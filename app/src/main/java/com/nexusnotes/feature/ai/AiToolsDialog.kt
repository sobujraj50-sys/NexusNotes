package com.nexusnotes.feature.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexusnotes.domain.ai.AiAction
import com.nexusnotes.domain.ai.AiPrompts

/**
 * Optional AI tools. Builds a prompt from the note and shows the exact text. Only when the user
 * taps "Send to AI app" or "Copy" does it leave Nexus Notes — the app itself has no internet access.
 */
@Composable
fun AiToolsDialog(title: String, body: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var action by remember { mutableStateOf(AiAction.SUMMARIZE) }
    var language by remember { mutableStateOf("English") }
    val prompt = AiPrompts.build(action, title, body, language)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI tools") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Nexus Notes doesn't connect to the internet. This prepares a prompt you can send to an AI app " +
                        "you choose (for example Claude, Gemini or ChatGPT). Check the text below — it includes this note.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AiAction.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { a ->
                            FilterChip(selected = action == a, onClick = { action = a }, label = { Text(a.label) })
                        }
                    }
                }
                if (action.needsLanguage) {
                    OutlinedTextField(language, { language = it.take(40) }, singleLine = true, label = { Text("Into language") })
                }
                Card(Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Text(
                            prompt,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()).then(Modifier),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, prompt)
                runCatching { context.startActivity(Intent.createChooser(send, "Send to AI app")) }
                onDismiss()
            }) { Text("Send to AI app") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("AI prompt", prompt))
                    Toast.makeText(context, "Prompt copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}
