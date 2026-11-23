package com.nexusnotes.core.database

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.nexusnotes.core.design.theme.spacing

sealed interface DatabaseState {
    data object Opening : DatabaseState
    data object Ready : DatabaseState
    data class Failed(val error: Throwable) : DatabaseState
}

/** Shown instead of crashing if the database cannot be opened or upgraded. */
@Composable
fun DatabaseErrorScreen(error: Throwable) {
    val spacing = MaterialTheme.spacing
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text("Notes could not be opened", style = MaterialTheme.typography.headlineSmall)
            Text(
                "The app stopped before changing anything else. A copy of your notes from before " +
                    "this update is kept on this phone (nexus-backup file), so nothing has been deleted. " +
                    "Please don't uninstall the app or clear its data. Send a screenshot of this " +
                    "screen so it can be fixed in the next update.",
                style = MaterialTheme.typography.bodyLarge,
            )
            SelectionContainer {
                Text(
                    text = (error::class.java.simpleName + ": " + (error.message ?: "")).take(1_500),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
