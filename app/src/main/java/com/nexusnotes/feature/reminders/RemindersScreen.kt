package com.nexusnotes.feature.reminders

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexusnotes.appContainer
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.core.reminders.ReminderAlarms
import com.nexusnotes.data.entity.ReminderWithNote
import com.nexusnotes.data.repository.ReminderRepository
import com.nexusnotes.domain.reminder.ReminderRepeat
import com.nexusnotes.domain.reminder.ReminderSchedule
import com.nexusnotes.navigation.NexusDestination
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

class RemindersViewModel(private val reminders: ReminderRepository) : ViewModel() {
    val all: StateFlow<List<ReminderWithNote>> = reminders.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val exactAllowed: Boolean get() = reminders.exactAlarmsAllowed

    companion object {
        val Factory = viewModelFactory { initializer { RemindersViewModel(appContainer().reminderRepository) } }
    }
}

@Composable
fun RemindersScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    viewModel: RemindersViewModel = viewModel(factory = RemindersViewModel.Factory),
) {
    val list by viewModel.all.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val spacing = MaterialTheme.spacing
    val notificationsOk = ReminderAlarms.hasNotificationPermission(context)
    NexusScreen(title = NexusDestination.Reminders.title, onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = spacing.xl)) {
            if (!notificationsOk || !viewModel.exactAllowed) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screen, vertical = spacing.sm),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            if (!notificationsOk) Text("Notifications are off for Nexus Notes, so reminders can't appear.")
                            if (!viewModel.exactAllowed) Text("“Alarms & reminders” is off: reminders may arrive a few minutes late.")
                            TextButton(onClick = {
                                val intent = if (!notificationsOk) {
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:" + context.packageName))
                                } else {
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:" + context.packageName))
                                }
                                runCatching { context.startActivity(intent) }
                            }) { Text("Open Android settings") }
                        }
                    }
                }
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        "No reminders. Open a note › ⋮ › Reminder… to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(spacing.screen),
                    )
                }
            }
            items(list, key = { it.noteId }) { r ->
                val repeat = ReminderRepeat.entries.firstOrNull { it.name == r.repeat } ?: ReminderRepeat.NONE
                ListItem(
                    headlineContent = { Text(r.title.ifBlank { "Untitled" }) },
                    supportingContent = {
                        Text(
                            (if (r.deletedAt != null) "In Trash — won't ring · " else "") +
                                ReminderSchedule.label(r.triggerAt, repeat, System.currentTimeMillis(), ZoneId.systemDefault()),
                        )
                    },
                    leadingContent = { Icon(if (r.deletedAt != null) Icons.Filled.Warning else Icons.Filled.Notifications, null) },
                    modifier = Modifier.clickable { onOpenNote(r.noteId) },
                )
            }
        }
    }
}
