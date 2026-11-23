package com.nexusnotes

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexusnotes.core.design.theme.NexusTheme
import com.nexusnotes.domain.model.AppSettings
import com.nexusnotes.domain.model.ThemeMode
import com.nexusnotes.core.database.CrashReportDialog
import com.nexusnotes.core.database.DatabaseErrorScreen
import com.nexusnotes.core.database.DatabaseState
import com.nexusnotes.navigation.NexusNotesRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as NexusNotesApp).container
        val settingsFlow = container.settingsRepository.settings

        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Keep status/navigation bar icons readable when the app theme differs from the system.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose {}
            }

            NexusTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                val dbState by produceState<DatabaseState>(DatabaseState.Opening) {
                    value = container.openDatabase().fold(
                        onSuccess = { DatabaseState.Ready },
                        onFailure = { DatabaseState.Failed(it) },
                    )
                }
                when (val state = dbState) {
                    DatabaseState.Opening -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
                    DatabaseState.Ready -> NexusNotesRoot()
                    is DatabaseState.Failed -> DatabaseErrorScreen(state.error)
                }
                CrashReportDialog()
            }
        }
    }

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
