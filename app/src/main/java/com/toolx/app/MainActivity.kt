package com.toolx.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import com.toolx.app.core.AppSettings
import com.toolx.app.ui.navigation.ToolXNavHost
import com.toolx.app.ui.theme.Themes
import com.toolx.app.ui.theme.ToolXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        enableEdgeToEdge()
        setContent {
            val themeId = AppSettings.themeId
            val dark = Themes.resolve(themeId, isSystemInDarkTheme()).dark
            // Status/navigation bar icons must follow the app theme, not the phone's setting
            // (a dark app theme on a light phone would otherwise get dark icons on dark).
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            ToolXTheme(themeId) {
                ToolXNavHost()
            }
        }
    }
}
