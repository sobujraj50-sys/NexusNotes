package com.nexusnotes.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexusnotes.BuildConfig
import com.nexusnotes.core.design.components.NexusListRow
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.components.SectionHeader
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.navigation.NexusDestination

@Composable
fun SettingsScreen(onNavigate: (String) -> Unit) {
    val spacing = MaterialTheme.spacing
    NexusScreen(title = NexusDestination.Settings.title) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            item { SectionHeader("Library") }
            item { NexusListRow(NexusIcons.Label, "Tags", onClick = { onNavigate(NexusDestination.Tags.route) }) }
            item { NexusListRow(NexusIcons.Archive, "Archive", onClick = { onNavigate(NexusDestination.Archive.route) }) }
            item { NexusListRow(Icons.Filled.Delete, "Trash", onClick = { onNavigate(NexusDestination.Trash.route) }) }

            item { SectionHeader("Security & data", Modifier.padding(top = spacing.md)) }
            item {
                NexusListRow(
                    Icons.Filled.Lock,
                    "Private vault",
                    supporting = "PIN, biometrics and encryption",
                    onClick = { onNavigate(NexusDestination.Vault.route) },
                )
            }
            item {
                NexusListRow(
                    NexusIcons.Backup,
                    "Backup & restore",
                    supporting = "Local backups and exports",
                    onClick = { onNavigate(NexusDestination.Backup.route) },
                )
            }

            item { SectionHeader("Appearance", Modifier.padding(top = spacing.md)) }
            item { NexusListRow(Icons.Filled.Settings, "Theme", supporting = "Follows system (dark or light)", milestone = 2) }
            item { NexusListRow(Icons.Filled.Build, "Dynamic color", supporting = "Off — uses the Nexus palette", milestone = 2) }

            item { SectionHeader("Productivity & AI", Modifier.padding(top = spacing.md)) }
            item { NexusListRow(Icons.Filled.Notifications, "Reminders", supporting = "Asks for notification permission only when used", milestone = 9) }
            item { NexusListRow(NexusIcons.Sparkle, "AI features", supporting = "Off. Optional, never required, nothing is sent without your consent", milestone = 9) }

            item { SectionHeader("App", Modifier.padding(top = spacing.md)) }
            item {
                NexusListRow(
                    Icons.Filled.Info,
                    "About Nexus Notes",
                    supporting = "Version ${BuildConfig.VERSION_NAME}",
                    onClick = { onNavigate(NexusDestination.About.route) },
                )
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val promises = listOf(
        "No ads and no tracking SDKs",
        "No account and no subscription",
        "Works fully offline — this version requests no permissions at all",
        "Nothing is uploaded without your explicit consent",
        "Android cloud backup is disabled for this app",
    )
    NexusScreen(title = NexusDestination.About.title, onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screen, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    NexusIcons.Note,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(spacing.md))
                Text("Nexus Notes", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Personal Pro Edition · ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(spacing.lg))
            SectionHeader("Privacy promises")
            promises.forEach { promise ->
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        promise,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = spacing.md),
                    )
                }
            }
            Spacer(Modifier.height(spacing.lg))
            SectionHeader("Build status")
            Text(
                "Milestone 1 of 10: app foundation, design system and navigation. Note storage begins in " +
                    "Milestone 2. Features marked with a milestone badge are not built yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
