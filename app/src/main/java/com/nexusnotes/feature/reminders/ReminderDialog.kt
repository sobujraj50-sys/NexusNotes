package com.nexusnotes.feature.reminders

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nexusnotes.domain.reminder.Reminder
import com.nexusnotes.domain.reminder.ReminderRepeat
import com.nexusnotes.domain.reminder.ReminderSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Set or change a note's reminder. Asks for the notification permission (Android 13+) only here,
 * when the user actually sets a reminder.
 */
@Composable
fun ReminderDialog(
    current: Reminder?,
    exactAllowed: Boolean,
    onSave: (at: Long, repeat: ReminderRepeat) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val initial = current?.anchorAt?.takeIf { it > now } ?: current?.triggerAt
        ?: Instant.ofEpochMilli(now).atZone(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
    var at by remember { mutableLongStateOf(initial) }
    var repeat by remember { mutableStateOf(current?.repeat ?: ReminderRepeat.NONE) }
    var error by remember { mutableStateOf<String?>(null) }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onSave(at, repeat) else error = "Notifications are blocked, so the reminder can't be shown. Allow them in Android settings."
    }

    fun pickDate() {
        val d = Instant.ofEpochMilli(at).atZone(zone)
        DatePickerDialog(context, { _, y, m, day ->
            at = ReminderSchedule.at(LocalDate.of(y, m + 1, day), d.hour, d.minute, zone)
            error = null
        }, d.year, d.monthValue - 1, d.dayOfMonth).apply { datePicker.minDate = now - 1000 }.show()
    }

    fun pickTime() {
        val d = Instant.ofEpochMilli(at).atZone(zone)
        TimePickerDialog(context, { _, h, min ->
            at = ReminderSchedule.at(d.toLocalDate(), h, min, zone)
            error = null
        }, d.hour, d.minute, DateFormat.is24HourFormat(context)).show()
    }

    fun preset(daysAhead: Long, hour: Int, minute: Int = 0) {
        at = ReminderSchedule.at(LocalDate.now(zone).plusDays(daysAhead), hour, minute, zone)
        error = null
    }

    fun save() {
        if (repeat == ReminderRepeat.NONE && at <= System.currentTimeMillis()) {
            error = "Pick a time in the future."
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else onSave(at, repeat)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (current == null) "Remind me" else "Reminder") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {
                        at = System.currentTimeMillis() + 60 * 60_000L
                        error = null
                    }, label = { Text("In 1 hour") })
                    AssistChip(onClick = { preset(0, 20) }, label = { Text("Tonight 8 PM") })
                }
                AssistChip(onClick = { preset(1, 9) }, label = { Text("Tomorrow 9 AM") })
                Text(ReminderSchedule.label(at, ReminderRepeat.NONE, System.currentTimeMillis(), zone), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = ::pickDate) { Text("Date") }
                    OutlinedButton(onClick = ::pickTime) { Text("Time") }
                }
                ReminderRepeat.entries.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = repeat == r, role = Role.RadioButton, onClick = { repeat = r }).padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = repeat == r, onClick = null)
                        Spacer(Modifier.size(8.dp))
                        Text(r.label)
                    }
                }
                if (!exactAllowed) {
                    Text(
                        "“Alarms & reminders” is off for Nexus Notes, so Android may deliver reminders a few minutes late.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(android.net.Uri.parse("package:" + context.packageName)),
                                )
                            }
                        }) { Text("Allow exact reminders") }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = ::save) { Text("Save") } },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
