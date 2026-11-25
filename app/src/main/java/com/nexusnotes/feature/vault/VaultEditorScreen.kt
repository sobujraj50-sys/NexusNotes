package com.nexusnotes.feature.vault

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusnotes.core.design.components.ConfirmDialog
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.core.security.SecureWindow
import com.nexusnotes.core.storage.BitmapLoader
import com.nexusnotes.domain.attachment.AttachmentPolicy
import com.nexusnotes.domain.vault.VaultAttachment
import com.nexusnotes.feature.editor.MarkdownPreview

@Composable
fun VaultEditorScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    viewModel: VaultEditorViewModel = viewModel(factory = VaultEditorViewModel.Factory),
) {
    SecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<VaultAttachment?>(null) }
    var exporting by remember { mutableStateOf<VaultAttachment?>(null) }
    var warnExport by remember { mutableStateOf<VaultAttachment?>(null) }
    var removing by remember { mutableStateOf<VaultAttachment?>(null) }

    // Leave exactly once, whatever triggered it (back, delete, lock, failed load).
    var closed by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (!closed) {
            closed = true
            viewModel.flush()
            onBack()
        }
    }
    // Locked (auto-lock, or the app came back after being killed): leave the note.
    LaunchedEffect(unlocked) { if (!unlocked) close() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                VaultEditorEvent.Close -> close()
                is VaultEditorEvent.OpenNote -> if (!closed) { closed = true; onOpenNote(e.noteId) }
                is VaultEditorEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_STOP) viewModel.flush() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    BackHandler { close() }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::attach) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::attach) }
    val saveCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val a = exporting
        exporting = null
        if (uri != null && a != null) viewModel.export(a, uri)
    }

    NexusScreen(
        title = "",
        onBack = close,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            Text(state.saveLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { preview = !preview }) {
                Icon(NexusIcons.Preview, contentDescription = if (preview) "Edit" else "Preview")
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Move out of vault") },
                        leadingIcon = { Icon(Icons.Filled.Lock, null) },
                        enabled = state.isStored && !state.busy,
                        onClick = { menu = false; confirm = "out" },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete forever") },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        enabled = !state.busy,
                        onClick = { menu = false; confirm = "delete" },
                    )
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@NexusScreen
        }
        val spacing = MaterialTheme.spacing
        val fieldColors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        )
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            TextField(
                value = state.title,
                onValueChange = viewModel::onTitle,
                placeholder = { Text("Title") },
                textStyle = MaterialTheme.typography.headlineSmall,
                singleLine = true,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.padding(horizontal = spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Encrypted", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                AssistChip(
                    onClick = { viewModel.beforePicker(); pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    label = { Text("Photo") },
                    leadingIcon = { Icon(NexusIcons.Image, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    enabled = !state.attaching,
                )
                AssistChip(
                    onClick = { viewModel.beforePicker(); pickFile.launch(arrayOf("*/*")) },
                    label = { Text("File") },
                    leadingIcon = { Icon(NexusIcons.Attach, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    enabled = !state.attaching,
                )
            }
            if (state.attachments.isNotEmpty() || state.attaching) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    items(state.attachments, key = { it.id }) { a ->
                        VaultTile(
                            attachment = a,
                            missing = a.id in state.missing,
                            load = viewModel::readBytes,
                            onOpen = { if (a.id in state.missing) removing = a else if (a.isImage) viewing = a else warnExport = a },
                            onSaveCopy = { warnExport = a },
                            onRemove = { removing = a },
                        )
                    }
                    if (state.attaching) {
                        item { Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) } }
                    }
                }
            }
            if (preview) {
                MarkdownPreview(
                    markdown = state.body,
                    onToggleTask = null,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(spacing.lg),
                )
            } else {
                TextField(
                    value = state.body,
                    onValueChange = viewModel::onBody,
                    placeholder = { Text("Private note… (Markdown supported)") },
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    viewing?.let { a -> VaultImageViewer(a, viewModel::readBytes, onDismiss = { viewing = null }) }
    warnExport?.let { a ->
        ConfirmDialog(
            title = "Save an unencrypted copy?",
            message = "“${a.displayName}” will be decrypted and saved where you choose. That copy is NOT protected by the vault.",
            confirmLabel = "Choose location",
            onConfirm = {
                warnExport = null
                exporting = a
                viewModel.beforePicker()
                saveCopy.launch(a.displayName)
            },
            onDismiss = { warnExport = null },
        )
    }
    removing?.let { a ->
        ConfirmDialog(
            title = "Remove file?",
            message = "“${a.displayName}” will be deleted from the vault.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.removeAttachment(a); removing = null },
            onDismiss = { removing = null },
        )
    }
    when (confirm) {
        "out" -> ConfirmDialog(
            title = "Move out of the vault?",
            message = "The note becomes a normal, unencrypted note: it will be searchable and shown on Home.",
            confirmLabel = "Move out",
            onConfirm = { confirm = null; viewModel.moveOut() },
            onDismiss = { confirm = null },
        )
        "delete" -> ConfirmDialog(
            title = "Delete forever?",
            message = "This vault note and its files are destroyed. Vault notes don't go to Trash.",
            confirmLabel = "Delete",
            onConfirm = { confirm = null; viewModel.delete() },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun VaultTile(
    attachment: VaultAttachment,
    missing: Boolean,
    load: suspend (VaultAttachment) -> ByteArray?,
    onOpen: () -> Unit,
    onSaveCopy: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = onOpen,
            shape = MaterialTheme.shapes.medium,
            color = if (missing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            if (!missing && attachment.isImage) {
                val bitmap by produceState<ImageBitmap?>(null, attachment.id) {
                    value = load(attachment)?.let { bytes -> BitmapLoader.decode(bytes, AttachmentPolicy.THUMBNAIL_EDGE).also { bytes.fill(0) } }
                }
                Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                    val b = bitmap
                    if (b != null) Image(b, attachment.displayName, contentScale = ContentScale.Crop, modifier = Modifier.size(88.dp))
                    else CircularProgressIndicator(Modifier.size(24.dp))
                }
            } else {
                Column(Modifier.width(120.dp).height(88.dp).padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (missing) Icons.Filled.Warning else NexusIcons.Note, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            if (missing) " Missing" else " " + AttachmentPolicy.formatSize(attachment.sizeBytes),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Text(attachment.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        IconButton(
            onClick = { menu = true },
            modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), androidx.compose.foundation.shape.CircleShape),
        ) { Icon(Icons.Filled.MoreVert, "Options for ${attachment.displayName}", Modifier.size(16.dp)) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (!missing) DropdownMenuItem(text = { Text("Save a copy…") }, onClick = { menu = false; onSaveCopy() })
            DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; onRemove() })
            DropdownMenuItem(
                text = { Text(attachment.displayName + " · " + AttachmentPolicy.formatSize(attachment.sizeBytes)) },
                enabled = false,
                onClick = {},
            )
        }
    }
}

/** Decrypts into memory only; nothing is written to disk. */
@Composable
private fun VaultImageViewer(a: VaultAttachment, load: suspend (VaultAttachment) -> ByteArray?, onDismiss: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, a.id) {
        value = load(a)?.let { bytes -> BitmapLoader.decode(bytes, AttachmentPolicy.VIEWER_EDGE).also { bytes.fill(0) } }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val b = bitmap
            if (b == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                Image(
                    b, a.displayName, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().transformable(transform).graphicsLayer {
                        scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                    },
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding()) {
                Icon(Icons.Filled.Close, "Close", tint = Color.White)
            }
        }
    }
}
