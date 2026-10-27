package com.apexpredator.argus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.apexpredator.argus.data.AlertScheduler
import com.apexpredator.argus.ui.AppNav
import com.apexpredator.argus.ui.theme.ApexArgusTheme
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Required by the OSM tile usage policy: a valid user agent.
        Configuration.getInstance().userAgentValue = "ApexArgus/2.0"
        // Balcony alerts (v2.3): the periodic check is kept, not
        // recreated, on every launch, and no-ops until the user
        // allows notifications and leaves a type switched on.
        AlertScheduler.schedule(this)
        enableEdgeToEdge()
        setContent {
            ApexArgusTheme {
                AppNav()
            }
        }
    }
}
