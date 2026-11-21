package com.nexusnotes.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import com.nexusnotes.core.design.components.CenteredEmptyState
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.navigation.NexusDestination

const val SEARCH_FIELD_TAG = "search_field"

/**
 * Search screen shell. The query field works (it is only a text input); there is
 * nothing indexed yet, so the result area states that plainly instead of faking results.
 */
@Composable
fun SearchScreen(onBack: () -> Unit) {
    val spacing = MaterialTheme.spacing
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    NexusScreen(title = NexusDestination.Search.title, onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search titles, content, tags, prompts") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screen, vertical = spacing.sm)
                    .focusRequester(focusRequester)
                    .testTag(SEARCH_FIELD_TAG),
            )
            CenteredEmptyState(
                padding = PaddingValues(),
                icon = Icons.Filled.Search,
                title = if (query.isBlank()) "Offline search" else "No results",
                message = "Full-text search runs entirely on this phone. Nothing is indexed yet — " +
                    "the search index is built in Milestone 4, and vault notes are never added to it.",
                milestone = 4,
            )
        }
    }
}
