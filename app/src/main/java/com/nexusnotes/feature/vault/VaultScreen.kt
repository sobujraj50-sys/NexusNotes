package com.nexusnotes.feature.vault

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.core.security.BiometricUnlock
import com.nexusnotes.core.security.SecureWindow
import com.nexusnotes.core.security.findActivity
import com.nexusnotes.domain.vault.VaultNote
import com.nexusnotes.domain.vault.VaultPolicy
import com.nexusnotes.navigation.NexusDestination
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.crypto.Cipher

const val VAULT_UNLOCK_BUTTON_TAG = "vault_unlock"

@Composable
fun VaultScreen(
    onBack: () -> Unit,
    onOpenItem: (String?) -> Unit,
    viewModel: VaultViewModel = viewModel(factory = VaultViewModel.Factory),
) {
    SecureWindow()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val biometricAvailable = remember { BiometricUnlock.availability(context) == BiometricUnlock.Availability.AVAILABLE }

    fun runBiometric(cipher: Cipher, title: String, onOk: (Cipher) -> Unit) {
        val activity = context.findActivity() ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        BiometricUnlock.authenticate(
            activity, cipher, title, "Nexus Notes vault",
            onSuccess = onOk,
            onError = { msg -> if (msg != null) viewModel.biometricMessage(msg) },
        )
    }

    fun biometricUnlock() {
        scope.launch {
            val cipher = viewModel.biometricUnlockCipher() ?: return@launch
            runBiometric(cipher, "Unlock vault") { viewModel.completeBiometricUnlock(it) }
        }
    }

    NexusScreen(
        title = NexusDestination.Vault.title,
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            if (state.phase == VaultPhase.UNLOCKED) {
                IconButton(onClick = viewModel::lock) { Icon(Icons.Filled.Lock, contentDescription = "Lock vault") }
                androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Vault settings") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Change PIN") }, onClick = { menuOpen = false; dialog = "pin" })
                    if (biometricAvailable) {
                        DropdownMenuItem(
                            text = { Text(if (state.biometricEnabled) "Turn off fingerprint unlock" else "Turn on fingerprint unlock") },
                            leadingIcon = { Icon(NexusIcons.Fingerprint, null) },
                            onClick = {
                                menuOpen = false
                                if (state.biometricEnabled) {
                                    viewModel.disableBiometric()
                                } else {
                                    viewModel.enableBiometricCipher()?.let { c ->
                                        runBiometric(c, "Turn on fingerprint unlock") { viewModel.completeBiometricEnable(it) }
                                    }
                                }
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Auto-lock: " + VaultPolicy.autoLockLabel(state.autoLockMs).lowercase()) },
                        onClick = { menuOpen = false; dialog = "autolock" },
                    )
                    DropdownMenuItem(text = { Text("How the vault protects notes") }, onClick = { menuOpen = false; dialog = "info" })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete vault…", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; dialog = "reset" },
                    )
                }
                }
            }
        },
        floatingActionButton = {
            if (state.phase == VaultPhase.UNLOCKED) {
                ExtendedFloatingActionButton(
                    onClick = { onOpenItem(null) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("New vault note") },
                )
            }
        },
    ) { padding ->
        when (state.phase) {
            VaultPhase.LOADING -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            VaultPhase.SETUP -> SetupContent(padding, state, onCreate = viewModel::create, onClearError = viewModel::clearPinError)
            VaultPhase.LOCKED -> LockedContent(
                padding = padding,
                state = state,
                showBiometric = biometricAvailable && state.biometricEnabled,
                onUnlock = viewModel::unlock,
                onBiometric = ::biometricUnlock,
                onClearError = viewModel::clearPinError,
                onForgot = { dialog = "reset" },
            )
            VaultPhase.KEYS_LOST -> KeysLostContent(padding, onReset = { dialog = "reset" })
            VaultPhase.UNLOCKED -> UnlockedContent(padding, state, viewModel::setQuery, onOpen = { onOpenItem(it.id) })
        }
    }

    when (dialog) {
        "pin" -> ChangePinDialog(onChange = { o, n, c -> viewModel.changePin(o, n, c) }, onDismiss = { dialog = null })
        "autolock" -> AutoLockDialog(state.autoLockMs, onPick = { viewModel.setAutoLock(it); dialog = null }, onDismiss = { dialog = null })
        "info" -> SecurityInfoDialog(onDismiss = { dialog = null })
        "reset" -> ResetVaultDialog(onConfirm = { viewModel.reset(); dialog = null }, onDismiss = { dialog = null })
    }
}

@Composable
private fun SetupContent(padding: PaddingValues, state: VaultUiState, onCreate: (String, String) -> Unit, onClearError: () -> Unit) {
    val spacing = MaterialTheme.spacing
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var understood by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Icon(Icons.Filled.Lock, null, Modifier.size(48.dp).align(Alignment.CenterHorizontally), tint = MaterialTheme.colorScheme.primary)
        Text("Set up your private vault", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        InfoCard(
            "Vault notes are encrypted on this phone with AES-256-GCM. The key is protected by your PIN " +
                "and by Android Keystore. Vault notes never appear in search, Home, previews or notifications.",
        )
        InfoCard(
            "There is no PIN reset and no back door. If you forget the PIN, or this phone's keys are lost " +
                "(factory reset, some system updates), vault notes cannot be recovered by anyone — including us.",
            warning = true,
        )
        PinField(pin, { pin = it; onClearError() }, "New PIN (6+ digits, or a passphrase)", imeAction = ImeAction.Next)
        PinField(confirm, { confirm = it; onClearError() }, "Repeat PIN", error = state.pinError, onDone = { if (understood) onCreate(pin, confirm) })
        Row(
            Modifier.fillMaxWidth().clickable { understood = !understood },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = understood, onCheckedChange = { understood = it })
            Text("I understand a forgotten PIN means vault notes are lost forever.", style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = { onCreate(pin, confirm) },
            enabled = understood && pin.isNotEmpty() && confirm.isNotEmpty() && !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Create vault")
        }
        Text(
            "Tip: a longer PIN or a passphrase is much harder to guess.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LockedContent(
    padding: PaddingValues,
    state: VaultUiState,
    showBiometric: Boolean,
    onUnlock: (String) -> Unit,
    onBiometric: () -> Unit,
    onClearError: () -> Unit,
    onForgot: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    var pin by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val waiting = state.lockedUntil > now
    LaunchedEffect(state.lockedUntil) {
        while (state.lockedUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    LaunchedEffect(state.pinError) { if (state.pinError != null) pin = "" }
    // Offer the fingerprint right away, once per screen visit.
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showBiometric) {
        if (showBiometric && !autoPrompted && !waiting) {
            autoPrompted = true
            onBiometric()
        }
    }
    Column(
        Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(spacing.xl))
        Icon(Icons.Filled.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Vault is locked", style = MaterialTheme.typography.headlineSmall)
        PinField(
            pin,
            { pin = it; onClearError() },
            "Vault PIN",
            error = if (waiting) "Too many wrong attempts. Try again in ${VaultPolicy.formatWait(state.lockedUntil - now)}." else state.pinError,
            onDone = { onUnlock(pin) },
            enabled = !state.busy && !waiting,
        )
        Button(
            onClick = { onUnlock(pin) },
            enabled = pin.isNotEmpty() && !state.busy && !waiting,
            modifier = Modifier.fillMaxWidth().testTag(VAULT_UNLOCK_BUTTON_TAG),
        ) {
            if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Unlock")
        }
        if (showBiometric) {
            OutlinedButton(onClick = onBiometric, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Icon(NexusIcons.Fingerprint, null)
                Spacer(Modifier.size(8.dp))
                Text("Use fingerprint")
            }
        }
        Text(
            "Forgot the PIN? It can't be reset or recovered. You can only delete the vault and start a new one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onForgot) { Text("Forgot PIN — delete vault…", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun KeysLostContent(padding: PaddingValues, onReset: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Icon(Icons.Filled.Warning, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Text("The vault can't be opened", style = MaterialTheme.typography.headlineSmall)
        InfoCard(
            "This phone no longer has the security key that protects the vault (this can happen after a factory " +
                "reset, restoring the app on another phone, or rarely a system update). The encrypted notes are " +
                "still stored but cannot be decrypted by anyone. Your normal notes are not affected.",
            warning = true,
        )
        Text("You can delete the unreadable vault and create a new one.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onReset) { Text("Delete vault and start over…") }
    }
}

@Composable
private fun UnlockedContent(padding: PaddingValues, state: VaultUiState, onQuery: (String) -> Unit, onOpen: (VaultNote) -> Unit) {
    val spacing = MaterialTheme.spacing
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                placeholder = { Text("Search vault notes") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.damaged > 0) {
            item { InfoCard("${state.damaged} vault item(s) are damaged and could not be decrypted.", warning = true) }
        }
        if (state.notes.isEmpty()) {
            item {
                Text(
                    if (state.query.isBlank()) "No vault notes yet. Tap “New vault note”, or open any note and choose “Move to Vault”." else "No matches.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = spacing.lg),
                )
            }
        }
        items(state.notes, key = { it.id }) { note ->
            Card(onClick = { onOpen(note) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(spacing.md)) {
                    Text(note.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (note.body.isNotBlank()) {
                        Text(note.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(note.updatedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(text: String, warning: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun ChangePinDialog(onChange: suspend (String, String, String) -> String?, onDismiss: () -> Unit) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Change PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(old, { old = it; error = null }, "Current PIN", imeAction = ImeAction.Next)
                PinField(new, { new = it; error = null }, "New PIN", imeAction = ImeAction.Next)
                PinField(confirm, { confirm = it; error = null }, "Repeat new PIN", error = error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && old.isNotEmpty() && new.isNotEmpty(),
                onClick = {
                    busy = true
                    scope.launch {
                        error = onChange(old, new, confirm)
                        busy = false
                        if (error == null) onDismiss()
                    }
                },
            ) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Change") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
private fun AutoLockDialog(current: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock the vault when I leave the app") },
        text = {
            Column {
                VaultPolicy.AUTO_LOCK_OPTIONS.forEach { ms ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = ms == current, role = Role.RadioButton, onClick = { onPick(ms) }).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = ms == current, onClick = null)
                        Spacer(Modifier.size(12.dp))
                        Text(VaultPolicy.autoLockLabel(ms))
                    }
                }
                Text(
                    "The vault also locks when the app is closed or the phone restarts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun SecurityInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How the vault protects notes") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("• Notes, titles and attachments are encrypted with AES-256-GCM before they are saved.")
                Text("• The vault key is protected by your PIN (PBKDF2, ${VaultPolicy.PBKDF2_ITERATIONS / 1000}k rounds) and by an Android Keystore key that can't be copied off this phone.")
                Text("• Your PIN itself is never stored. After ${VaultPolicy.FREE_ATTEMPTS} wrong tries you must wait, longer each time.")
                Text("• Fingerprint unlock is a shortcut that unwraps the same key; it is turned off automatically if fingerprints change.")
                Text("• Screenshots are blocked and the app preview in Recents is blank while a vault screen is open.")
                Text("• Vault notes are not in search, Home, Trash, or any Android backup.")
                Text("Limits: someone who knows your PIN, or who controls your unlocked phone, can read the vault. Files you export with “Save a copy” are not encrypted.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun ResetVaultDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete the whole vault?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("All vault notes and files are destroyed permanently. This cannot be undone. Type DELETE to confirm.")
                OutlinedTextField(typed, { typed = it }, singleLine = true, placeholder = { Text("DELETE") })
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed.trim() == "DELETE") {
                Text("Delete vault", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
