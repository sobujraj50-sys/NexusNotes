package com.nexusnotes.feature.prompts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusnotes.core.common.ShareUtils
import com.nexusnotes.core.design.components.CenteredEmptyState
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.components.SectionHeader
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.navigation.NexusDestination

const val RUNNER_RESULT_TAG = "runner_result"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptRunnerScreen(
    onBack: () -> Unit,
    viewModel: PromptRunnerViewModel = viewModel(factory = PromptRunnerViewModel.Factory),
) {
    val spacing = MaterialTheme.spacing
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prompt = state.prompt
    val result = state.result

    NexusScreen(
        title = prompt?.title?.ifBlank { null } ?: NexusDestination.PromptRunner.title,
        onBack = onBack,
        actions = {
            if (state.variables.isNotEmpty()) {
                IconButton(onClick = viewModel::resetToDefaults) { Icon(Icons.Filled.Refresh, contentDescription = "Reset to default values") }
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@NexusScreen
        }
        if (prompt == null || result == null) {
            CenteredEmptyState(
                padding = padding,
                icon = NexusIcons.Sparkle,
                title = "Prompt not found",
                message = "Open the Template Runner from a saved prompt.",
            )
            return@NexusScreen
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screen, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            if (state.variables.isEmpty()) {
                Text(
                    "This prompt has no {{variables}}; it is ready to copy as it is.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SectionHeader("Fill in")
                state.variables.forEach { name ->
                    OutlinedTextField(
                        value = state.values[name].orEmpty(),
                        onValueChange = { viewModel.setValue(name, it) },
                        label = { Text(name.replace('_', ' ')) },
                        isError = name in result.missing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SectionHeader("Result")
            if (result.missing.isNotEmpty()) {
                Row {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        "Not filled yet: " + result.missing.joinToString { "{{$it}}" },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = spacing.sm),
                    )
                }
            }
            val filledStyle = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            val missingStyle = SpanStyle(color = MaterialTheme.colorScheme.error, textDecoration = TextDecoration.Underline)
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().testTag(RUNNER_RESULT_TAG),
            ) {
                SelectionContainer {
                    Text(
                        buildAnnotatedString {
                            append(result.text)
                            result.filledRanges.forEach { addStyle(filledStyle, it.first, it.last + 1) }
                            result.unfilledRanges.forEach { addStyle(missingStyle, it.first, it.last + 1) }
                        },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(spacing.lg),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { ShareUtils.copy(context, prompt.title.ifBlank { "Prompt" }, result.text) }, modifier = Modifier.weight(1f)) {
                    Icon(NexusIcons.Copy, contentDescription = null)
                    Text("Copy", Modifier.padding(start = spacing.sm))
                }
                OutlinedButton(onClick = { ShareUtils.shareText(context, prompt.title.ifBlank { "Prompt" }, result.text) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text("Share", Modifier.padding(start = spacing.sm))
                }
            }
            Text(
                "The saved template is not changed. Values typed here are not stored; set defaults in the prompt editor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
