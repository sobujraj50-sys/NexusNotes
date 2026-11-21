package com.nexusnotes.feature.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nexusnotes.core.design.components.EmptyState
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.components.PlannedFeatureCard
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.navigation.NexusDestination

@Composable
fun VaultScreen(onBack: () -> Unit) {
    val spacing = MaterialTheme.spacing
    NexusScreen(title = NexusDestination.Vault.title, onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screen),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            EmptyState(
                icon = Icons.Filled.Lock,
                title = "No vault yet",
                message = "The vault is not set up and stores nothing. It is not encrypted storage until " +
                    "Milestone 7 ships and passes its encryption tests.",
                milestone = 7,
            )
            PlannedFeatureCard(
                icon = Icons.Filled.Lock,
                title = "How it will work",
                description = "Vault notes are encrypted with authenticated encryption using a key derived " +
                    "from your PIN and protected by Android Keystore. Fingerprint/face unlock is a " +
                    "convenience on top of the PIN, not a replacement for encryption. Vault notes never " +
                    "appear in search, previews, notifications or Android backups.",
                milestone = 7,
            )
            PlannedFeatureCard(
                icon = Icons.Filled.Warning,
                title = "Recovery limits",
                description = "There will be no PIN reset and no back door. If you forget the PIN, or the " +
                    "app's keys are lost (for example after a factory reset), vault content cannot be " +
                    "recovered unless you made an encrypted vault backup and still know its password.",
                milestone = 7,
            )
        }
    }
}
