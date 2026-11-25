package com.nexusnotes.feature.settings

import android.os.Build
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusnotes.domain.model.ThemeMode
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
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val spacing = MaterialTheme.spacing
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val attachmentBytes by viewModel.attachmentBytes.collectAsStateWithLifecycle()
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
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
                    supporting = "Encrypted notes · PIN & fingerprint",
                    onClick = { onNavigate(NexusDestination.Vault.route) },
                )
            }
            item {
                NexusListRow(
                    NexusIcons.Backup,
                    "Backup & restore",
                    supporting = "Backup, restore and export",
                    onClick = { onNavigate(NexusDestination.Backup.route) },
                )
            }

            item {
                NexusListRow(
                    NexusIcons.Attach,
                    "Attachment storage",
                    supporting = com.nexusnotes.domain.attachment.AttachmentPolicy.formatSize(attachmentBytes) +
                        " used · private to this app · max " +
                        com.nexusnotes.domain.attachment.AttachmentPolicy.formatSize(com.nexusnotes.domain.attachment.AttachmentPolicy.MAX_FILE_BYTES) +
                        " per file",
                )
            }

            item { SectionHeader("Appearance", Modifier.padding(top = spacing.md)) }
            item {
                NexusListRow(
                    Icons.Filled.Settings,
                    "Theme",
                    supporting = settings.themeMode.label,
                    onClick = { showThemeDialog = true },
                )
            }
            item {
                SwitchRow(
                    title = "Dynamic color",
                    supporting = if (dynamicColorSupported) {
                        "Use colors from your wallpaper instead of the Nexus palette"
                    } else {
                        "Needs Android 12 or newer"
                    },
                    checked = settings.dynamicColor && dynamicColorSupported,
                    enabled = dynamicColorSupported,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }

            item { SectionHeader("Search", Modifier.padding(top = spacing.md)) }
            item {
                SwitchRow(
                    title = "Remember searches",
                    supporting = "Keep your last 10 searches on this phone. Off by default; turning it off deletes them.",
                    checked = settings.saveSearchHistory,
                    enabled = true,
                    onCheckedChange = viewModel::setSaveSearchHistory,
                )
            }

            item { SectionHeader("Productivity & AI", Modifier.padding(top = spacing.md)) }
            item {
                NexusListRow(
                    Icons.Filled.Notifications, "Reminders",
                    supporting = "All note reminders. Notification permission is asked only when you set one.",
                    onClick = { onNavigate(NexusDestination.Reminders.route) },
                )
            }
            item {
                NexusListRow(
                    NexusIcons.Note, "Templates",
                    supporting = "Start notes from checklists, journals, meeting notes or your own",
                    onClick = { onNavigate(NexusDestination.Templates.route) },
                )
            }
            item {
                SwitchRow(
                    title = "AI tools (optional)",
                    supporting = "Adds “AI tools…” to the note menu: builds a prompt you can send to an AI app you choose. " +
                        "Nexus Notes itself never connects to the internet. Off by default.",
                    checked = settings.aiToolsEnabled,
                    enabled = true,
                    onCheckedChange = viewModel::setAiToolsEnabled,
                )
            }

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

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme") },
            text = {
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = mode == settings.themeMode,
                                    onClick = {
                                        viewModel.setThemeMode(mode)
                                        showThemeDialog = false
                                    },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = mode == settings.themeMode, onClick = null)
                            Text(mode.label, Modifier.padding(start = spacing.md))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    supporting: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(supporting) },
            leadingContent = { Icon(Icons.Filled.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        )
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val promises = listOf(
        "No ads and no tracking SDKs",
        "No account and no subscription",
        "Works fully offline — no internet permission. Microphone, notifications and alarms are asked only when you use them",
        "Notes are stored only in this app's private storage on this phone",
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
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                "Milestone 9 of 10: notes, Markdown editor, folders, tags, offline search, the AI " +
                    "Prompt Library, attachments, the encrypted Private Vault, verified backup/restore " +
                    "and export, reminders, templates, a home-screen widget, quick-note shortcuts and " +
                    "optional AI tools.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
