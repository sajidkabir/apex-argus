package com.apexpredator.argus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.apexpredator.argus.ui.AppNav
import com.apexpredator.argus.ui.theme.ApexArgusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ApexArgusTheme {
                AppNav()
            }
        }
    }
}
