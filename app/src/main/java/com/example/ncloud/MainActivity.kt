package com.example.ncloud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.example.ncloud.ui.AppBg
import com.example.ncloud.ui.AppPanel
import com.example.ncloud.ui.AppPurple
import com.example.ncloud.ui.AppText
import com.example.ncloud.ui.NcloudApp
import com.example.ncloud.ui.theme.NcloudTheme
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NcloudTheme {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        background = AppBg,
                        surface = AppPanel,
                        primary = AppPurple,
                        onPrimary = Color.White,
                        onSurface = AppText
                    )
                ) {
                    NcloudApp()
                }
            }
        }
    }
}