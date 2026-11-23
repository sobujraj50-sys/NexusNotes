package com.nexusnotes.feature.prompts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nexusnotes.core.design.components.MilestoneBadge
import com.nexusnotes.core.design.components.NexusListRow
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.components.PlannedFeatureCard
import com.nexusnotes.core.design.components.SectionHeader
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.domain.model.PromptCategory
import com.nexusnotes.navigation.NexusDestination

private const val EXAMPLE_TEMPLATE =
    "A cinematic shot of {{subject}} in {{style}} style, {{camera}}, {{lighting}}, aspect ratio {{aspect_ratio}}"

@Composable
fun PromptLibraryScreen(onNavigate: (String) -> Unit) {
    val spacing = MaterialTheme.spacing
    NexusScreen(
        title = NexusDestination.PromptLibrary.title,
        actions = {
            IconButton(onClick = { onNavigate(NexusDestination.Search.createRoute()) }) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigate(NexusDestination.PromptEditor.createRoute()) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New prompt") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = spacing.screen,
                end = spacing.screen,
                top = spacing.sm,
                bottom = spacing.fabClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            item(key = "intro") {
                PlannedFeatureCard(
                    icon = NexusIcons.Sparkle,
                    title = "Templates with variables",
                    description = "Save prompts with placeholders like {{subject}} and fill them in the " +
                        "Template Runner. The original template is never overwritten.",
                    milestone = 5,
                )
            }
            item(key = "categories_header") {
                SectionHeader("Categories", Modifier.padding(top = spacing.md))
            }
            items(PromptCategory.entries, key = { it.name }) { category ->
                // Informational until Milestone 5: no prompts exist, so rows are not clickable.
                NexusListRow(
                    icon = NexusIcons.Sparkle,
                    title = category.displayName,
                    supporting = category.description,
                )
            }
        }
    }
}

@Composable
fun PromptEditorScreen(
    promptId: String?,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    NexusScreen(
        title = if (promptId == null) "New prompt" else "Edit prompt",
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
            Text(
                "Saving prompts starts in Milestone 5, so these fields are read-only for now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MilestoneBadge(5)
            OutlinedTextField(
                value = "",
                onValueChange = {},
                enabled = false,
                placeholder = { Text("Prompt title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            NexusListRow(
                icon = NexusIcons.Folder,
                title = "Category",
                supporting = PromptCategory.GENERAL.displayName,
                milestone = 5,
            )
            OutlinedTextField(
                value = "",
                onValueChange = {},
                enabled = false,
                placeholder = { Text(EXAMPLE_TEMPLATE) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp),
            )
            FilledTonalButton(
                onClick = { onNavigate(NexusDestination.PromptRunner.createRoute(promptId)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("Open Template Runner", Modifier.padding(start = spacing.sm))
            }
        }
    }
}

@Composable
fun PromptRunnerScreen(onBack: () -> Unit) {
    val spacing = MaterialTheme.spacing
    NexusScreen(title = NexusDestination.PromptRunner.title, onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screen, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            PlannedFeatureCard(
                icon = Icons.Filled.PlayArrow,
                title = "Fill a template",
                description = "Each {{variable}} in the template gets its own input. The filled prompt is " +
                    "built as a new copy you can copy or save; unknown or empty variables are reported, " +
                    "never silently dropped.",
                milestone = 5,
            )
            SectionHeader("Example template")
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    EXAMPLE_TEMPLATE,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(spacing.lg),
                )
            }
        }
    }
}
