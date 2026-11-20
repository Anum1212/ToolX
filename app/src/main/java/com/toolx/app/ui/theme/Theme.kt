package com.toolx.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Violet10 = Color(0xFF1B0563)
private val Violet20 = Color(0xFF2F1590)
private val Violet30 = Color(0xFF4628B8)
private val Violet40 = Color(0xFF5B3FD9)
private val Violet80 = Color(0xFFC8BFFF)
private val Violet90 = Color(0xFFE5DEFF)

private val Teal10 = Color(0xFF002020)
private val Teal20 = Color(0xFF003737)
private val Teal30 = Color(0xFF004F4F)
private val Teal40 = Color(0xFF00696A)
private val Teal80 = Color(0xFF4CDADB)
private val Teal90 = Color(0xFF6FF7F7)

private val Coral10 = Color(0xFF3A0B00)
private val Coral20 = Color(0xFF5E1700)
private val Coral30 = Color(0xFF842500)
private val Coral40 = Color(0xFFA93800)
private val Coral80 = Color(0xFFFFB59C)
private val Coral90 = Color(0xFFFFDBCF)

private val LightColors = lightColorScheme(
    primary = Violet40,
    onPrimary = Color.White,
    primaryContainer = Violet90,
    onPrimaryContainer = Violet10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Teal10,
    tertiary = Coral40,
    onTertiary = Color.White,
    tertiaryContainer = Coral90,
    onTertiaryContainer = Coral10,
    background = Color(0xFFFCF8FF),
    surface = Color(0xFFFCF8FF),
)

private val DarkColors = darkColorScheme(
    primary = Violet80,
    onPrimary = Violet20,
    primaryContainer = Violet30,
    onPrimaryContainer = Violet90,
    secondary = Teal80,
    onSecondary = Teal20,
    secondaryContainer = Teal30,
    onSecondaryContainer = Teal90,
    tertiary = Coral80,
    onTertiary = Coral20,
    tertiaryContainer = Coral30,
    onTertiaryContainer = Coral90,
    background = Color(0xFF131218),
    surface = Color(0xFF131218),
)

/**
 * App theme. Follows the system light/dark setting. Set [dynamicColor] to true to
 * use the wallpaper-based Material You palette on Android 12+ instead of the brand colors.
 */
@Composable
fun ToolXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
