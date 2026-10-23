/*
 * Pixel Notes — password fields for protecting sign-ins in backups.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.backup.BackupCrypto

enum class BackupPasswordMode {
    /** Making a backup: choose a password and type it twice. */
    CREATE,

    /** Restoring a backup whose sign-ins are encrypted. */
    ENTER,
}

/** True when [password] (and [confirm], when creating) can be used. */
fun isBackupPasswordValid(
    mode: BackupPasswordMode,
    password: String,
    confirm: String,
): Boolean =
    when (mode) {
        BackupPasswordMode.CREATE -> password.length >= BackupCrypto.MIN_PASSWORD_LENGTH && password == confirm
        BackupPasswordMode.ENTER -> password.isNotEmpty()
    }

@Composable
fun BackupPasswordFields(
    mode: BackupPasswordMode,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirm: String,
    onConfirmChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
    val keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text =
                stringResource(
                    if (mode == BackupPasswordMode.CREATE) {
                        R.string.backup_password_create_hint
                    } else {
                        R.string.backup_password_enter_hint
                    },
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val tooShort =
            mode == BackupPasswordMode.CREATE &&
                password.isNotEmpty() &&
                password.length < BackupCrypto.MIN_PASSWORD_LENGTH
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.backup_password_label)) },
            singleLine = true,
            visualTransformation = transformation,
            keyboardOptions = keyboard,
            isError = tooShort,
            supportingText =
                if (tooShort) {
                    { Text(stringResource(R.string.backup_password_too_short)) }
                } else {
                    null
                },
            modifier = Modifier.fillMaxWidth(),
        )
        if (mode == BackupPasswordMode.CREATE) {
            val mismatch = confirm.isNotEmpty() && confirm != password
            OutlinedTextField(
                value = confirm,
                onValueChange = onConfirmChange,
                label = { Text(stringResource(R.string.backup_password_confirm_label)) },
                singleLine = true,
                visualTransformation = transformation,
                keyboardOptions = keyboard,
                isError = mismatch,
                supportingText =
                    if (mismatch) {
                        { Text(stringResource(R.string.backup_password_mismatch)) }
                    } else {
                        null
                    },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = visible, onCheckedChange = { visible = it })
            Text(
                text = stringResource(R.string.backup_password_show),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
