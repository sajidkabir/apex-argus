package com.apexpredator.argus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.apexpredator.argus.ui.AppNav
import com.apexpredator.argus.ui.theme.ApexArgusTheme
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Required by the OSM tile usage policy: a valid user agent.
        Configuration.getInstance().userAgentValue = "ApexArgus/1.1.0"
        enableEdgeToEdge()
        setContent {
            ApexArgusTheme {
                AppNav()
            }
        }
    }
}
