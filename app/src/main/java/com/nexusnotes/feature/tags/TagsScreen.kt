package com.nexusnotes.feature.tags

import androidx.compose.runtime.Composable
import com.nexusnotes.core.design.components.CenteredEmptyState
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.navigation.NexusDestination

@Composable
fun TagsScreen(onBack: () -> Unit) {
    NexusScreen(title = NexusDestination.Tags.title, onBack = onBack) { padding ->
        CenteredEmptyState(
            padding = padding,
            icon = NexusIcons.Label,
            title = "No tags yet",
            message = "Give notes several tags each and filter or search by them.",
            milestone = 4,
        )
    }
}
