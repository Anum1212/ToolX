package com.toolx.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.toolx.app.tools.ToolCategory

/** The palette in use, for category colours and anything Material's scheme doesn't cover. */
val LocalAppTheme = staticCompositionLocalOf { Themes.Light }

/** Builds a full Material 3 scheme from a palette; every "on" colour is checked for contrast. */
fun AppTheme.toColorScheme(): ColorScheme {
    val p = readable(primary, background, 3f)
    val container = tint(p, surface, dark)
    val step = { t: Float -> lerp(background, surfaceHigh, t) }
    val error = readable(accents[ToolCategory.VIDEO.ordinal], background, 3f)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p,
        onPrimary = onColor(p),
        primaryContainer = container,
        onPrimaryContainer = text,
        inversePrimary = p,
        // Selected chips, slider tracks, cards: a soft tint of the primary colour (no stray cyan).
        secondary = p,
        onSecondary = onColor(p),
        secondaryContainer = container,
        onSecondaryContainer = text,
        tertiary = readable(accents[ToolCategory.CALC.ordinal], background, 3f),
        onTertiary = onColor(readable(accents[ToolCategory.CALC.ordinal], background, 3f)),
        tertiaryContainer = tint(accents[ToolCategory.CALC.ordinal], surface, dark),
        onTertiaryContainer = text,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = muted,
        surfaceTint = p,
        inverseSurface = text,
        inverseOnSurface = background,
        error = error,
        onError = onColor(error),
        errorContainer = tint(error, surface, dark),
        onErrorContainer = text,
        outline = outline,
        outlineVariant = lerp(outline, background, 0.4f),
        scrim = Color.Black,
        surfaceBright = step(0.6f),
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = lerp(surface, surfaceHigh, 0.4f),
        surfaceContainerHigh = lerp(surface, surfaceHigh, 0.7f),
        surfaceContainerHighest = surfaceHigh,
    )
}

/**
 * App theme. [themeId] is a palette id, [SYSTEM_THEME] (follows the phone's light/dark mode)
 * or [DYNAMIC_THEME] (Material You wallpaper colours on Android 12+; category colours then
 * come from the default palettes).
 */
@Composable
fun ToolXTheme(themeId: String, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val palette = Themes.resolve(themeId, systemDark)
    val context = LocalContext.current
    val scheme = remember(themeId, systemDark) {
        if (themeId == DYNAMIC_THEME && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            palette.toColorScheme()
        }
    }
    CompositionLocalProvider(LocalAppTheme provides palette) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}

/** A category's colour, made readable on the current background. */
@Composable
fun categoryColor(category: ToolCategory): Color {
    val t = LocalAppTheme.current
    return readable(t.accent(category), MaterialTheme.colorScheme.surfaceContainerLow, 3f)
}

/** Soft container tint for a category (tile backgrounds, selected chips). */
@Composable
fun categoryContainer(category: ToolCategory): Color {
    val t = LocalAppTheme.current
    return tint(t.accent(category), MaterialTheme.colorScheme.surfaceContainerLow, t.dark)
}

/**
 * Re-themes a tool screen in its category's colour: buttons, chips, sliders and highlights
 * all take that colour, so each category looks like its own space.
 */
@Composable
fun CategoryTheme(category: ToolCategory, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val t = LocalAppTheme.current
    val accent = readable(t.accent(category), base.background, 3f)
    val container = tint(accent, base.surfaceContainerLow, t.dark)
    val scheme = base.copy(
        primary = accent,
        onPrimary = onColor(accent),
        primaryContainer = container,
        onPrimaryContainer = base.onSurface,
        secondary = accent,
        onSecondary = onColor(accent),
        secondaryContainer = container,
        onSecondaryContainer = base.onSurface,
        surfaceTint = accent,
    )
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
}
