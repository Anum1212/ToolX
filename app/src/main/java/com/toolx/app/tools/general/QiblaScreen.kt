package com.toolx.app.tools.general

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.toolx.app.tools.Tool
import com.toolx.app.ui.common.BigResult
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.NumberField
import com.toolx.app.ui.common.ResultCard
import com.toolx.app.ui.common.ToolScaffold
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private fun hasLocationPermission(context: Context) =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/**
 * A fresh fix if one arrives within 30 s, otherwise the newest last-known one. GPS, fused and
 * network are asked at once and the first answer wins: fused alone may skip GPS (it did on the
 * emulator), and GPS is the one that works with no network.
 */
@SuppressLint("MissingPermission")
private suspend fun findLocation(context: Context): Location? {
    val lm = context.getSystemService(LocationManager::class.java) ?: return null
    val enabled = lm.getProviders(true)
    val last = enabled.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    if (last != null && System.currentTimeMillis() - last.time < 10 * 60_000) return last
    val providers = listOfNotNull(
        LocationManager.GPS_PROVIDER,
        if (Build.VERSION.SDK_INT >= 31) LocationManager.FUSED_PROVIDER else null,
        LocationManager.NETWORK_PROVIDER,
    ).filter { it in enabled }
    if (providers.isEmpty()) return last
    val result = CompletableDeferred<Location>()
    // Every method is implemented: on Android 10 they aren't interface defaults yet.
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) { result.complete(location) }
        @Deprecated("Deprecated in API 29") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }
    return try {
        providers.forEach { runCatching { lm.requestLocationUpdates(it, 1000L, 0f, listener, Looper.getMainLooper()) } }
        withTimeoutOrNull(30_000) { result.await() } ?: last
    } finally {
        lm.removeUpdates(listener)
    }
}

private enum class FixState { IDLE, SEARCHING, FAILED, OFF }

@Composable
internal fun QiblaScreen(tool: Tool, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("qibla", Context.MODE_PRIVATE) }
    // The last place is remembered on the phone only, so the screen works straight away next time.
    var lat by rememberSaveable { mutableStateOf(prefs.getString("lat", null)?.toDoubleOrNull()) }
    var lon by rememberSaveable { mutableStateOf(prefs.getString("lon", null)?.toDoubleOrNull()) }
    var accuracy by rememberSaveable { mutableStateOf<Float?>(null) }
    var state by remember { mutableStateOf(FixState.IDLE) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var latText by rememberSaveable { mutableStateOf(lat?.let { "%.5f".format(it) } ?: "") }
    var lonText by rememberSaveable { mutableStateOf(lon?.let { "%.5f".format(it) } ?: "") }
    var permissionAsks by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun setPlace(newLat: Double, newLon: Double, acc: Float?) {
        lat = newLat; lon = newLon; accuracy = acc
        latText = "%.5f".format(newLat); lonText = "%.5f".format(newLon)
        prefs.edit().putString("lat", newLat.toString()).putString("lon", newLon.toString()).apply()
    }

    fun locate() {
        val lm = context.getSystemService(LocationManager::class.java)
        if (lm?.isLocationEnabled == false) { state = FixState.OFF; return }
        state = FixState.SEARCHING
        scope.launch {
            val loc = findLocation(context)
            if (loc != null) { setPlace(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else null); state = FixState.IDLE }
            else state = FixState.FAILED
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        permissionAsks++
        if (granted.values.any { it }) locate()
    }
    LaunchedEffect(Unit) { if (hasLocationPermission(context)) locate() }

    // Heading from the rotation-vector sensor (magnetic), corrected to true north for this place.
    var azimuth by remember { mutableFloatStateOf(0f) }
    val matrix = remember { FloatArray(9) }
    val orientation = remember { FloatArray(3) }
    val hasSensor = rememberSensor(Sensor.TYPE_ROTATION_VECTOR) { v ->
        SensorManager.getRotationMatrixFromVector(matrix, v)
        SensorManager.getOrientation(matrix, orientation)
        val target = ((Math.toDegrees(orientation[0].toDouble()) + 360) % 360).toFloat()
        var delta = target - azimuth
        if (delta > 180) delta -= 360
        if (delta < -180) delta += 360
        azimuth = (azimuth + delta * 0.2f + 360) % 360
    }

    ToolScaffold(tool.title, onBack) {
        val la = lat; val lo = lon
        if (la == null || lo == null) {
            Note("Qibla needs to know where you are. Your location is used on the phone only and is never sent anywhere.")
        } else {
            val qibla = Qibla.bearing(la, lo)
            val declination = remember(la, lo) {
                GeomagneticField(la.toFloat(), lo.toFloat(), 0f, System.currentTimeMillis()).declination
            }
            val heading = ((azimuth + declination) % 360 + 360) % 360
            val turn = Qibla.turn(heading.toDouble(), qibla)
            val facing = hasSensor && abs(turn) < 3
            BigResult(
                if (!hasSensor) "${qibla.roundToInt()}° from north"
                else if (facing) "Facing the Qibla ✓"
                else if (turn > 0) "Turn right ${turn.roundToInt()}°" else "Turn left ${(-turn).roundToInt()}°",
                "Qibla is ${"%.1f".format(qibla)}° from true north",
            )
            if (hasSensor) QiblaDial(heading, qibla.toFloat(), facing)
            else Note("This phone has no compass sensor. Use the angle above with a separate compass.", error = true)
            ResultCard(
                listOf(
                    "Distance to the Kaaba" to "%,.0f km".format(Qibla.distanceKm(la, lo)),
                    "Your position" to "%.4f, %.4f".format(la, lo) + (accuracy?.let { " (±${it.roundToInt()} m)" } ?: ""),
                    "Magnetic declination" to "%.1f°".format(declination),
                ),
            )
            if (hasSensor) Note("Hold the phone flat, away from metal and magnets. If the direction seems off, wave the phone in a figure-8 to recalibrate the compass.")
        }

        when (state) {
            FixState.SEARCHING -> Note("Finding your location… Outdoors or near a window is quickest.")
            FixState.FAILED -> Note("Couldn't get a location. Try outdoors, or enter your coordinates.", error = true)
            FixState.OFF -> Note("Location is turned off. Turn it on in the quick settings, then try again.", error = true)
            FixState.IDLE -> Unit
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (hasLocationPermission(context)) locate()
                    else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
                enabled = state != FixState.SEARCHING,
            ) { Text(if (lat == null) "Use my location" else "Update location") }
            OutlinedButton(onClick = { manual = !manual }) { Text("Enter coordinates") }
        }
        if (permissionAsks > 0 && !hasLocationPermission(context)) {
            Note("Location permission was denied. You can allow it in the app's settings, or enter your coordinates.", error = true)
        }
        if (manual) {
            NumberField(latText, { latText = it }, "Latitude (−90 to 90)")
            NumberField(lonText, { lonText = it }, "Longitude (−180 to 180)")
            val mLat = latText.replace(',', '.').toDoubleOrNull()
            val mLon = lonText.replace(',', '.').toDoubleOrNull()
            val ok = mLat != null && mLon != null && mLat in -90.0..90.0 && mLon in -180.0..180.0
            Button(onClick = { setPlace(mLat!!, mLon!!, null); manual = false }, enabled = ok) { Text("Use these") }
        }
    }
}

/** Dial that turns with the phone: N in red, the Kaaba marker at the Qibla bearing, fixed pointer at the top. */
@Composable
private fun QiblaDial(heading: Float, qibla: Float, facing: Boolean) {
    val line = MaterialTheme.colorScheme.onSurface
    val north = MaterialTheme.colorScheme.error
    val accent = if (facing) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(16.dp)) {
        val r = min(size.width, size.height) / 2
        val c = Offset(size.width / 2, size.height / 2)
        fun at(deg: Float, radius: Float): Offset {
            val rad = Math.toRadians(deg.toDouble())
            return Offset(c.x + radius * sin(rad).toFloat(), c.y - radius * cos(rad).toFloat())
        }
        drawCircle(line, r, c, style = Stroke(3f))
        rotate(-heading, c) {
            for (deg in 0 until 360 step 10) {
                drawLine(line, at(deg.toFloat(), if (deg % 90 == 0) r * 0.86f else r * 0.93f), at(deg.toFloat(), r), strokeWidth = if (deg % 90 == 0) 4f else 1.5f)
            }
            drawCircle(north, r * 0.05f, at(0f, r * 0.76f))
            // Needle from the centre to the Kaaba, ending in a small cube.
            drawLine(accent, c, at(qibla, r * 0.7f), strokeWidth = 10f)
            val k = at(qibla, r * 0.78f)
            val s = r * 0.13f
            rotate(qibla, k) {
                drawRect(Color(0xFF212121), Offset(k.x - s / 2, k.y - s / 2), Size(s, s))
                drawRect(Color(0xFFC9A227), Offset(k.x - s / 2, k.y - s * 0.22f), Size(s, s * 0.12f))
            }
        }
        // Fixed pointer: where the top of the phone faces.
        drawLine(accent, Offset(c.x, c.y - r * 1.04f), Offset(c.x, c.y - r * 0.88f), strokeWidth = 8f)
        drawCircle(accent, 10f, c)
    }
}
