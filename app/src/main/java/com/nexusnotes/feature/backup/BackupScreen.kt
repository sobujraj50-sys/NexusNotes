package com.nexusnotes.feature.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nexusnotes.core.design.components.NexusListRow
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.components.PlannedFeatureCard
import com.nexusnotes.core.design.components.SectionHeader
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.navigation.NexusDestination

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val spacing = MaterialTheme.spacing
    NexusScreen(title = NexusDestination.Backup.title, onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            item {
                PlannedFeatureCard(
                    icon = NexusIcons.Backup,
                    title = "Local backups you control",
                    description = "Backups are written only to a location you pick, with integrity checks " +
                        "and schema versions. Restoring never overwrites data without asking first. " +
                        "Android cloud backup is turned off for this app.",
                    milestone = 8,
                )
            }
            item { SectionHeader("Backup", Modifier.padding(top = spacing.md)) }
            item { NexusListRow(NexusIcons.Backup, "Create backup", supporting = "Notes, prompts and attachments", milestone = 8) }
            item { NexusListRow(Icons.Filled.Refresh, "Restore from backup", supporting = "Validated before anything changes", milestone = 8) }
            item { NexusListRow(Icons.Filled.Lock, "Vault backup", supporting = "Stays encrypted; never exported as plain text", milestone = 8) }
            item { SectionHeader("Export", Modifier.padding(top = spacing.md)) }
            item { NexusListRow(Icons.Filled.Share, "Export notes", supporting = "TXT, Markdown, JSON or PDF", milestone = 8) }
            item { NexusListRow(NexusIcons.Sparkle, "Export / import prompts", supporting = "JSON", milestone = 8) }
        }
    }
}
