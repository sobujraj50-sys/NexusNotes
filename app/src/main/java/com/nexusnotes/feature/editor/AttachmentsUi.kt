package com.nexusnotes.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexusnotes.core.design.components.ConfirmDialog
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.core.storage.AttachmentIntents
import com.nexusnotes.core.storage.AudioPlayer
import com.nexusnotes.core.storage.AudioRecorder
import com.nexusnotes.core.storage.BitmapLoader
import com.nexusnotes.domain.attachment.AttachmentKind
import com.nexusnotes.domain.attachment.AttachmentPolicy
import com.nexusnotes.domain.model.Attachment
import kotlinx.coroutines.delay
import java.io.File

const val ATTACHMENT_TILE_TAG = "attachment_tile"

/** Horizontal strip of a note's attachments. */
@Composable
fun AttachmentStrip(
    attachments: List<Attachment>,
    missing: Set<String>,
    isAttaching: Boolean,
    readOnly: Boolean,
    fileFor: (Attachment) -> File,
    onRemove: (Attachment) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val context = LocalContext.current
    val player = remember { AudioPlayer() }
    var playingId by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<Attachment?>(null) }
    var confirmRemove by remember { mutableStateOf<Attachment?>(null) }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    if (attachments.isEmpty() && !isAttaching) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = spacing.lg, vertical = spacing.xs),
    ) {
        items(attachments, key = { it.id }) { a ->
            val isMissing = a.id in missing
            AttachmentTile(
                attachment = a,
                isMissing = isMissing,
                isPlaying = playingId == a.id,
                file = fileFor(a),
                onOpen = {
                    when {
                        isMissing -> confirmRemove = a
                        a.kind == AttachmentKind.IMAGE -> viewing = a
                        a.kind == AttachmentKind.AUDIO -> {
                            if (playingId == a.id) {
                                player.stop()
                                playingId = null
                            } else {
                                runCatching {
                                    player.play(fileFor(a)) { playingId = null }
                                    playingId = a.id
                                }.onFailure { AttachmentIntents.open(context, fileFor(a), a.mimeType) }
                            }
                        }
                        else -> AttachmentIntents.open(context, fileFor(a), a.mimeType)
                    }
                },
                onShare = { AttachmentIntents.share(context, fileFor(a), a.mimeType, a.displayName) },
                onOpenExternally = { AttachmentIntents.open(context, fileFor(a), a.mimeType) },
                onRemove = if (readOnly) null else ({ confirmRemove = a }),
            )
        }
        if (isAttaching) {
            item(key = "attaching") {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
            }
        }
    }

    viewing?.let { a ->
        ImageViewer(
            attachment = a,
            file = fileFor(a),
            onShare = { AttachmentIntents.share(context, fileFor(a), a.mimeType, a.displayName) },
            onDismiss = { viewing = null },
        )
    }
    confirmRemove?.let { a ->
        ConfirmDialog(
            title = if (a.id in missing) "File missing" else "Remove attachment?",
            message = if (a.id in missing) {
                "“${a.displayName}” is no longer on this phone (it may have been cleared by a cleaner app). Remove it from the note?"
            } else {
                "“${a.displayName}” will be deleted from this note and from the app's storage."
            },
            confirmLabel = "Remove",
            onConfirm = {
                if (playingId == a.id) player.stop()
                onRemove(a)
                confirmRemove = null
            },
            onDismiss = { confirmRemove = null },
        )
    }
}

@Composable
private fun AttachmentTile(
    attachment: Attachment,
    isMissing: Boolean,
    isPlaying: Boolean,
    file: File,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onOpenExternally: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    val spacing = MaterialTheme.spacing
    Box {
        Surface(
            onClick = onOpen,
            shape = MaterialTheme.shapes.medium,
            color = if (isMissing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.testTag(ATTACHMENT_TILE_TAG),
        ) {
            when {
                isMissing -> TileLabel(Icons.Filled.Warning, "Missing", attachment.displayName)
                attachment.kind == AttachmentKind.IMAGE -> {
                    val bitmap by produceState<ImageBitmap?>(null, file) {
                        value = BitmapLoader.load(file, AttachmentPolicy.THUMBNAIL_EDGE)
                    }
                    Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                        val b = bitmap
                        if (b != null) {
                            Image(b, contentDescription = attachment.displayName, contentScale = ContentScale.Crop, modifier = Modifier.size(88.dp))
                        } else {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
                attachment.kind == AttachmentKind.AUDIO -> TileLabel(
                    if (isPlaying) Icons.Filled.Close else Icons.Filled.PlayArrow,
                    if (isPlaying) "Stop" else (attachment.durationMs?.let { AttachmentPolicy.formatDuration(it) } ?: "Audio"),
                    attachment.displayName,
                )
                else -> TileLabel(NexusIcons.Note, AttachmentPolicy.formatSize(attachment.sizeBytes), attachment.displayName)
            }
        }
        // Small menu button on every tile.
        IconButton(
            onClick = { menu = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .padding(2.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Options for ${attachment.displayName}",
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (!isMissing) {
                DropdownMenuItem(text = { Text("Open with…") }, onClick = { menu = false; onOpenExternally() })
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Filled.Share, null) },
                    onClick = { menu = false; onShare() },
                )
            }
            if (onRemove != null) {
                DropdownMenuItem(
                    text = { Text("Remove") },
                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                    onClick = { menu = false; onRemove() },
                )
            }
            DropdownMenuItem(
                text = { Text(attachment.displayName + " · " + AttachmentPolicy.formatSize(attachment.sizeBytes), maxLines = 2) },
                enabled = false,
                onClick = {},
            )
        }
    }
}

@Composable
private fun TileLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, top: String, bottom: String) {
    Column(
        Modifier
            .width(120.dp)
            .height(88.dp)
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(top, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
        Text(bottom, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Full-screen image with pinch-to-zoom. */
@Composable
private fun ImageViewer(attachment: Attachment, file: File, onShare: () -> Unit, onDismiss: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, file) { value = BitmapLoader.load(file, AttachmentPolicy.VIEWER_EDGE) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val b = bitmap
            if (b == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                Image(
                    b,
                    contentDescription = attachment.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(state)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            }
            Row(Modifier.align(Alignment.TopEnd).safeDrawingPadding()) {
                IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Share image", tint = Color.White) }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White) }
            }
        }
    }
}

/** Records audio with a live timer. [onSaved] receives the duration; [onCancel] discards. */
@Composable
fun RecordAudioDialog(
    output: File,
    onSaved: (durationMs: Long) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context) }
    var started by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var finished by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        runCatching { recorder.start(output) }
            .onSuccess { started = true }
            .onFailure { error = "The microphone could not be started: ${it.message ?: "unknown error"}" }
        onDispose {
            if (!finished) {
                recorder.cancel()
                output.delete()
            }
        }
    }
    LaunchedEffect(started) {
        while (started && !finished) {
            delay(250)
            elapsed += 250
            if (elapsed >= AttachmentPolicy.MAX_RECORDING_MS) break
        }
    }
    fun stopAndSave() {
        val duration = recorder.stop()
        finished = true
        if (duration != null && output.length() > 0) onSaved(duration) else {
            output.delete()
            onCancel()
        }
    }
    BackHandler { onCancel() }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { Icon(NexusIcons.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(if (error == null) "Recording…" else "Can't record") },
        text = {
            Text(
                error ?: (AttachmentPolicy.formatDuration(elapsed) + "\nAudio stays on this phone."),
                style = if (error == null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            if (error == null) TextButton(onClick = { stopAndSave() }) { Text("Stop & save") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(if (error == null) "Discard" else "Close") } },
    )
}
