package com.nexusnotes.feature.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexusnotes.core.design.components.MilestoneBadge
import com.nexusnotes.core.design.components.NexusListRow
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.components.SectionHeader
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.spacing

const val EDITOR_TITLE_TAG = "editor_title"

/**
 * Editor layout preview. Input is DISABLED in Milestone 1 on purpose: there is no
 * database yet, and the spec forbids keeping notes only in temporary UI state.
 * Milestone 2 replaces this with a ViewModel backed by Room with autosave.
 */
@Composable
fun NoteEditorScreen(noteId: String?, onBack: () -> Unit) {
    val spacing = MaterialTheme.spacing
    NexusScreen(
        title = if (noteId == null) "New note" else "Note",
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screen, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Editing is disabled until Milestone 2" },
            ) {
                Row(Modifier.padding(spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null)
                    Text(
                        "Writing and autosave start in Milestone 2. Nothing typed here could be " +
                            "stored yet, so the editor is read-only.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = spacing.md),
                    )
                }
            }

            OutlinedTextField(
                value = "",
                onValueChange = {},
                enabled = false,
                placeholder = { Text("Title") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EDITOR_TITLE_TAG),
            )

            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Formatting", Modifier.weight(1f))
                    MilestoneBadge(3)
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("B", "I", "U", "S", "H1", "H2", "•", "1.", "☐", "❝", "</>", "Link", "Table").forEach {
                        TextButton(onClick = {}, enabled = false) { Text(it) }
                    }
                }
            }

            OutlinedTextField(
                value = "",
                onValueChange = {},
                enabled = false,
                placeholder = { Text("Start writing…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp),
            )

            Text(
                "0 words · 0 characters",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader("Details")
            NexusListRow(icon = NexusIcons.Folder, title = "Folder", supporting = "Unfiled", milestone = 4)
            NexusListRow(icon = NexusIcons.Label, title = "Tags", supporting = "None", milestone = 4)
            NexusListRow(icon = NexusIcons.Note, title = "Attachments", supporting = "Images, audio, files", milestone = 6)
        }
    }
}
