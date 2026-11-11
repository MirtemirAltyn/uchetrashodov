package com.moifinansy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.moifinansy.core.ui.theme.MoiFinansyTheme
import com.moifinansy.ui.navigation.MoiFinansyNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoiFinansyTheme {
                MoiFinansyNavHost()
            }
        }
    }
}
