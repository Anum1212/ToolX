package com.toolx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.toolx.app.ui.navigation.ToolXNavHost
import com.toolx.app.ui.theme.ToolXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ToolXTheme {
                ToolXNavHost()
            }
        }
    }
}
