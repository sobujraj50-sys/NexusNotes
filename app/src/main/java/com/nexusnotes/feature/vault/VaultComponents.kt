package com.nexusnotes.feature.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nexusnotes.core.design.icons.NexusIcons
import kotlinx.coroutines.launch

const val VAULT_PIN_FIELD_TAG = "vault_pin_field"

/**
 * PIN / passphrase input. Starts with the number pad; "Aa" switches to a full keyboard so a
 * stronger passphrase can be used. No autocorrect or suggestions (keyboards shouldn't learn it).
 */
@Composable
fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
    enabled: Boolean = true,
) {
    var visible by remember { mutableStateOf(false) }
    var textKeyboard by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 64 && '\n' !in it) onValueChange(it) },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else {
            null
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (textKeyboard) KeyboardType.Password else KeyboardType.NumberPassword,
            imeAction = imeAction,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }, onNext = { onDone() }),
        trailingIcon = {
            androidx.compose.foundation.layout.Row {
                TextButton(onClick = { textKeyboard = !textKeyboard }) { Text(if (textKeyboard) "123" else "Aa") }
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) NexusIcons.VisibilityOff else NexusIcons.Visibility,
                        contentDescription = if (visible) "Hide" else "Show",
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth().testTag(VAULT_PIN_FIELD_TAG),
    )
}

/**
 * Small "enter your vault PIN" dialog, used outside the vault screen (e.g. Move to Vault).
 * [onUnlock] returns null on success or an error message.
 */
@Composable
fun VaultUnlockDialog(
    title: String,
    message: String,
    onUnlock: suspend (String) -> String?,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun submit() {
        if (busy || pin.isEmpty()) return
        busy = true
        scope.launch {
            error = onUnlock(pin)
            busy = false
            if (error != null) pin = ""
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(androidx.compose.material.icons.Icons.Filled.Lock, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                PinField(
                    value = pin,
                    onValueChange = { pin = it; error = null },
                    label = "Vault PIN",
                    error = error,
                    onDone = ::submit,
                    enabled = !busy,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, enabled = !busy && pin.isNotEmpty()) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Unlock")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
