package com.polo.soundlogger.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/** Écrans principaux (spec §4). */
enum class Screen(val label: String) {
    HOME("Nuit"), REPORT("Rapport"), HISTORY("Historique"), SETTINGS("Réglages")
}

@Composable
fun SoundLoggerApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route?.let { runCatching { Screen.valueOf(it) }.getOrNull() } ?: Screen.HOME

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { s ->
                    NavigationBarItem(
                        selected = s == current,
                        onClick = { nav.navigate(s.name) { launchSingleTop = true } },
                        icon = {
                            Icon(when (s) {
                                Screen.HOME -> Icons.Default.Home
                                Screen.REPORT -> Icons.Default.BarChart
                                Screen.HISTORY -> Icons.Default.History
                                Screen.SETTINGS -> Icons.Default.Settings
                            }, contentDescription = s.label)
                        },
                        label = { Text(s.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Screen.HOME.name,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.HOME.name) { HomeScreen() }
            composable(Screen.REPORT.name) { NightReportScreen() }
            composable(Screen.HISTORY.name) { HistoryScreen() }
            composable(Screen.SETTINGS.name) { SettingsScreen() }
        }
    }
}
