package com.polo.soundlogger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import com.polo.soundlogger.service.NightScheduler
import com.polo.soundlogger.ui.SoundLoggerApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Replanifie les sessions automatiques à chaque ouverture de l'app (et après reboot).
        NightScheduler.scheduleNext(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface { SoundLoggerApp() }
            }
        }
    }
}
