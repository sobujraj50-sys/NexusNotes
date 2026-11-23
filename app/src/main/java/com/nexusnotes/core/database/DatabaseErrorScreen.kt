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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

/** Shows the error from the last crash (if any) so it can be copied and reported. */
@Composable
fun CrashReportDialog() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var report by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(com.nexusnotes.core.common.CrashReporter.lastCrash(context))
    }
    val text = report ?: return
    fun dismiss() {
        com.nexusnotes.core.common.CrashReporter.clear(context)
        report = null
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { dismiss() },
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("The app closed unexpectedly") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Your notes are safe. Tap Copy and send this report so the problem can be fixed. " +
                        "It contains only the technical error, not your notes, and stays on this phone unless you share it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SelectionContainer {
                    Text(
                        text,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.md),
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                com.nexusnotes.core.common.ShareUtils.copy(context, "Nexus Notes crash report", text)
                dismiss()
            }) { Text("Copy") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { dismiss() }) { Text("Close") } },
    )
}
