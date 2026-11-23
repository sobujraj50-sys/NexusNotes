package com.nexusnotes.feature.notes

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.NoteLabelColors
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.domain.NoteText
import com.nexusnotes.domain.model.Note

const val NOTE_CARD_TAG = "note_card"

/** List card for one note: color stripe, title, preview, time and pin/favorite markers. */
@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val title = remember(note.title, note.body) { NoteText.displayTitle(note.title, note.body) }
    val preview = remember(note.title, note.body) {
        // Avoid repeating the first line when it is already used as the title.
        if (note.title.isBlank()) "" else NoteText.preview(note.body)
    }
    val timeLabel = remember(note.updatedAt, note.deletedAt) {
        val now = System.currentTimeMillis()
        val deletedAt = note.deletedAt
        if (deletedAt != null) {
            "Deleted " + relative(deletedAt, now)
        } else {
            "Edited " + relative(note.updatedAt, now)
        }
    }
    val labelColor = note.colorIndex?.let { NoteLabelColors.getOrNull(it) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag(NOTE_CARD_TAG),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(labelColor ?: Color.Transparent),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = spacing.lg, vertical = spacing.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (note.isPinned) {
                        Icon(
                            NexusIcons.Pin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = spacing.xs)
                                .size(16.dp)
                                .semantics { contentDescription = "Pinned" },
                        )
                    }
                    if (note.isFavorite) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .padding(start = spacing.xs)
                                .size(16.dp)
                                .semantics { contentDescription = "Favorite" },
                        )
                    }
                }
                if (preview.isNotEmpty()) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(spacing.sm))
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun relative(time: Long, now: Long): String =
    DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString()
