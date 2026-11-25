package com.nexusnotes.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusnotes.core.design.components.NexusListRow
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.components.SectionHeader
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.spacing
import android.net.Uri
import com.nexusnotes.domain.backup.AutoBackupPolicy
import com.nexusnotes.domain.backup.ExportFormat
import com.nexusnotes.domain.backup.Manifest
import com.nexusnotes.domain.backup.RestoreMode
import com.nexusnotes.feature.vault.PinField
import com.nexusnotes.feature.vault.VaultUnlockDialog
import com.nexusnotes.navigation.NexusDestination
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory),
) {
    val spacing = MaterialTheme.spacing
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val vaultSetUp by viewModel.vaultSetUp.collectAsStateWithLifecycle()
    val vaultUnlocked by viewModel.vaultUnlocked.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(viewModel::setAutoBackupFolder) }

    var showBackupOptions by rememberSaveable { mutableStateOf(false) }
    var pendingVaultPassword by remember { mutableStateOf<String?>(null) }
    var pendingExport by rememberSaveable { mutableStateOf<ExportFormat?>(null) }
    val stamp = { SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val pw = pendingVaultPassword
        pendingVaultPassword = null
        if (uri != null) viewModel.createBackup(uri, pw)
    }
    val openForRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::inspect) }
    val openForVerify = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::verify) }
    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val f = pendingExport
        pendingExport = null
        if (uri != null && f != null) viewModel.export(f, uri, "export.zip")
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        pendingExport = null
        if (uri != null) viewModel.export(ExportFormat.JSON, uri, "export.json")
    }
    val exportPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        pendingExport = null
        if (uri != null) viewModel.export(ExportFormat.PDF, uri, "export.pdf")
    }
    val backupTypes = arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")

    fun startExport(format: ExportFormat) {
        viewModel.beforePicker()
        pendingExport = format
        val name = "NexusNotes-export-${stamp()}"
        when (format) {
            ExportFormat.MARKDOWN -> exportZip.launch("$name-markdown.zip")
            ExportFormat.TEXT -> exportZip.launch("$name-text.zip")
            ExportFormat.JSON -> exportJson.launch("$name.json")
            ExportFormat.PDF -> exportPdf.launch("$name.pdf")
        }
    }

    NexusScreen(title = NexusDestination.Backup.title, onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm),
                ) {
                    Text(
                        "Backups are one .zip file saved where you choose — this phone, an SD card, or a cloud drive app. " +
                            "Nothing is uploaded by Nexus Notes. Android's automatic backup is off for this app, so " +
                            "making your own backup is the only way to move notes to a new phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            state.busy?.let { label ->
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            val enabled = state.busy == null

            item { SectionHeader("Backup") }
            item {
                NexusListRow(
                    NexusIcons.Backup, "Create backup",
                    supporting = "Notes, folders, tags, prompts, attachments — optionally the vault (stays encrypted)",
                    onClick = if (enabled) ({ showBackupOptions = true }) else null,
                )
            }
            item {
                NexusListRow(
                    Icons.Filled.CheckCircle, "Check a backup file",
                    supporting = "Verifies every part without restoring",
                    onClick = if (enabled) ({ viewModel.beforePicker(); openForVerify.launch(backupTypes) }) else null,
                )
            }

            item { SectionHeader("Automatic backup", Modifier.padding(top = spacing.md)) }
            item {
                val folder = appSettings.autoBackupFolder
                Column(Modifier.fillMaxWidth().padding(vertical = spacing.xs), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Back up automatically", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (folder == null) "Choose a folder once; backups then run in the background." else "Into: " + Uri.decode(folder).substringAfterLast(':').ifBlank { "chosen folder" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = appSettings.autoBackupEnabled,
                            enabled = enabled,
                            onCheckedChange = { on ->
                                if (on && folder == null) { viewModel.beforePicker(); pickFolder.launch(null) } else {
                                    viewModel.setAutoBackupOptions(on, appSettings.autoBackupIntervalDays, appSettings.autoBackupKeep)
                                }
                            },
                        )
                    }
                    if (appSettings.autoBackupEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AutoBackupPolicy.INTERVAL_DAYS_OPTIONS.forEach { d ->
                                FilterChip(
                                    selected = appSettings.autoBackupIntervalDays == d,
                                    onClick = { viewModel.setAutoBackupOptions(true, d, appSettings.autoBackupKeep) },
                                    label = { Text(if (d == 1) "Daily" else "Weekly") },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AutoBackupPolicy.KEEP_OPTIONS.forEach { k ->
                                FilterChip(
                                    selected = appSettings.autoBackupKeep == k,
                                    onClick = { viewModel.setAutoBackupOptions(true, appSettings.autoBackupIntervalDays, k) },
                                    label = { Text("Keep $k") },
                                )
                            }
                        }
                        Row {
                            TextButton(onClick = { viewModel.runAutoBackupNow() }, enabled = enabled) { Text("Back up now") }
                            TextButton(onClick = { viewModel.beforePicker(); pickFolder.launch(null) }, enabled = enabled) { Text("Change folder") }
                        }
                        Text(
                            "Vault notes are not in automatic backups. Use Create backup with a password for those.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { SectionHeader("Restore", Modifier.padding(top = spacing.md)) }
            item {
                NexusListRow(
                    Icons.Filled.Refresh, "Restore from backup…",
                    supporting = "You'll see what's inside and choose Merge or Replace before anything changes",
                    onClick = if (enabled) ({ viewModel.beforePicker(); openForRestore.launch(backupTypes) }) else null,
                )
            }
            if (state.hasSafetyCopy) {
                item {
                    NexusListRow(
                        Icons.Filled.Warning, "Undo last restore",
                        supporting = "Bring back the data that was on this phone before the last “Replace” restore",
                        onClick = if (enabled) ({ viewModel.inspectSafetyCopy() }) else null,
                    )
                }
            }

            item { SectionHeader("Export (readable copies, can't be restored)", Modifier.padding(top = spacing.md)) }
            ExportFormat.entries.forEach { f ->
                item(key = "export_" + f.name) {
                    NexusListRow(
                        Icons.Filled.Share, f.label,
                        supporting = "All notes except Trash and the vault",
                        onClick = if (enabled) ({ startExport(f) }) else null,
                    )
                }
            }

            item { SectionHeader("Maintenance", Modifier.padding(top = spacing.md)) }
            item {
                NexusListRow(
                    Icons.Filled.CheckCircle, "Check database health",
                    supporting = "Integrity check of notes, links and attachment files. Changes nothing.",
                    onClick = if (enabled) ({ viewModel.checkHealth() }) else null,
                )
            }

            item { SectionHeader("History", Modifier.padding(top = spacing.md)) }
            if (history.isEmpty()) {
                item { Text("No backups yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(history, key = { it.id }) { h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (h.success) Icons.Filled.CheckCircle else Icons.Filled.Warning, null,
                        tint = if (h.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(
                            h.kind.lowercase().replaceFirstChar { it.uppercase() } + " · " +
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(h.createdAt)),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(h.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showBackupOptions) {
        BackupOptionsDialog(
            vaultSetUp = vaultSetUp == true,
            vaultUnlocked = vaultUnlocked,
            passwordProblem = viewModel::backupPasswordProblem,
            unlockVault = viewModel::unlockVault,
            onConfirm = { password ->
                showBackupOptions = false
                pendingVaultPassword = password
                viewModel.beforePicker()
                saveBackup.launch("NexusNotes-backup-${stamp()}.zip")
            },
            onDismiss = { showBackupOptions = false },
        )
    }

    state.inspected?.let { m ->
        RestoreDialog(
            manifest = m,
            lines = viewModel.describe(m),
            safetyCopy = state.inspectedIsSafetyCopy,
            vaultSetUp = vaultSetUp == true,
            vaultUnlocked = vaultUnlocked,
            unlockVault = viewModel::unlockVault,
            onRestore = viewModel::restore,
            onDismiss = viewModel::cancelRestore,
        )
    }

    state.report?.let { r ->
        AlertDialog(
            onDismissRequest = viewModel::dismissReport,
            icon = { Icon(if (r.success) Icons.Filled.CheckCircle else Icons.Filled.Warning, null) },
            title = { Text(r.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissReport) { Text("OK") } },
        )
    }
}

@Composable
private fun BackupOptionsDialog(
    vaultSetUp: Boolean,
    vaultUnlocked: Boolean,
    passwordProblem: (String, String) -> String?,
    unlockVault: suspend (String) -> String?,
    onConfirm: (vaultPassword: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var includeVault by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var askUnlock by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 24.dp),
        title = { Text("Create backup") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Includes all notes (also Archive and Trash), folders, tags, prompts with history, and attachments.")
                if (vaultSetUp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, null, Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Include vault notes", Modifier.weight(1f))
                        Switch(checked = includeVault, onCheckedChange = { includeVault = it; error = null })
                    }
                    if (includeVault) {
                        if (!vaultUnlocked) {
                            Text("Unlock the vault first so its key can be added to the backup.", color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { askUnlock = true }) { Text("Unlock vault") }
                        } else {
                            Text(
                                "Vault notes stay encrypted in the backup. Choose a backup password — you will need it to " +
                                    "restore them on any phone. It cannot be recovered if forgotten.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            PinField(password, { password = it; error = null }, "Backup password (10+ characters)", imeAction = ImeAction.Next, startWithText = true)
                            PinField(confirm, { confirm = it; error = null }, "Repeat backup password", error = error, startWithText = true)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !includeVault || vaultUnlocked,
                onClick = {
                    val problem = if (includeVault) passwordProblem(password, confirm) else null
                    when {
                        problem != null -> error = problem
                        includeVault -> onConfirm(password)
                        else -> onConfirm(null)
                    }
                },
            ) { Text("Choose where to save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (askUnlock) {
        VaultUnlockDialog(
            title = "Unlock vault",
            message = "Enter your vault PIN to include the vault in this backup.",
            onUnlock = { pin -> unlockVault(pin).also { if (it == null) askUnlock = false } },
            onDismiss = { askUnlock = false },
        )
    }
}

@Composable
private fun RestoreDialog(
    manifest: Manifest,
    lines: List<String>,
    safetyCopy: Boolean,
    vaultSetUp: Boolean,
    vaultUnlocked: Boolean,
    unlockVault: suspend (String) -> String?,
    onRestore: (RestoreMode, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(if (safetyCopy) RestoreMode.REPLACE else RestoreMode.MERGE) }
    var typed by remember { mutableStateOf("") }
    var restoreVault by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var askUnlock by remember { mutableStateOf(false) }
    val canVault = manifest.includesVault && vaultSetUp
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 24.dp),
        title = { Text(if (safetyCopy) "Undo last restore?" else "Restore this backup?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (!safetyCopy) {
                    ModeOption(
                        selected = mode == RestoreMode.MERGE,
                        title = "Merge (recommended)",
                        text = "Keeps everything on this phone and adds what's missing. Duplicates are skipped; " +
                            "changed notes come back as “(restored)” copies. Nothing is overwritten.",
                        onClick = { mode = RestoreMode.MERGE },
                    )
                    ModeOption(
                        selected = mode == RestoreMode.REPLACE,
                        title = "Replace everything",
                        text = "Deletes current notes, folders, tags, prompts and attachments, then loads the backup. " +
                            "A safety copy is made first. The vault is never deleted.",
                        onClick = { mode = RestoreMode.REPLACE },
                    )
                } else {
                    Text("Your current notes will be replaced by the copy saved just before the last restore.")
                }
                if (mode == RestoreMode.REPLACE) {
                    OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text("Type REPLACE to confirm") })
                }
                if (manifest.includesVault) {
                    when {
                        !vaultSetUp -> Text(
                            "This backup has encrypted vault notes. To restore them, set up the vault on this phone first (any PIN), then restore again.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        !vaultUnlocked -> {
                            Text("Unlock the vault to also restore its notes.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { askUnlock = true }) { Text("Unlock vault") }
                        }
                        else -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Also restore vault notes", Modifier.weight(1f))
                                Switch(checked = restoreVault, onCheckedChange = { restoreVault = it })
                            }
                            if (restoreVault) PinField(password, { password = it }, "Backup password", startWithText = true)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = (mode == RestoreMode.MERGE || typed.trim() == "REPLACE") && (!restoreVault || password.isNotEmpty()),
                onClick = { onRestore(mode, if (canVault && vaultUnlocked && restoreVault) password else null) },
            ) { Text("Restore") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (askUnlock) {
        VaultUnlockDialog(
            title = "Unlock vault",
            message = "Enter this phone's vault PIN.",
            onUnlock = { pin -> unlockVault(pin).also { if (it == null) askUnlock = false } },
            onDismiss = { askUnlock = false },
        )
    }
}

@Composable
private fun ModeOption(selected: Boolean, title: String, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.size(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
