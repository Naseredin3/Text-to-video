package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.TorchAmber

@Composable
fun PermanentApiKeyBanner(
    isConfigured: Boolean,
    maskedKey: String,
    onOpenDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    Surface(
        onClick = onOpenDialog,
        shape = RoundedCornerShape(14.dp),
        color = if (isConfigured) {
            EmeraldReady.copy(alpha = 0.14f)
        } else {
            TorchAmber.copy(alpha = 0.18f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isConfigured) EmeraldReady.copy(alpha = 0.6f) else TorchAmber
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("permanent_api_key_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Key,
                    contentDescription = null,
                    tint = if (isConfigured) EmeraldReady else TorchAmber,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = if (isConfigured) {
                            strings.apiKeyBannerConfiguredTitle(maskedKey)
                        } else {
                            strings.apiKeyBannerUnconfiguredTitle
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isConfigured) {
                            strings.apiKeyBannerConfiguredSub
                        } else {
                            strings.apiKeyBannerUnconfiguredSub
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = if (isConfigured) strings.editAction else strings.savePermanentAction,
                style = MaterialTheme.typography.labelMedium,
                color = if (isConfigured) EmeraldReady else TorchAmber,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PermanentApiKeyDialog(
    isConfigured: Boolean,
    maskedKey: String,
    initialBackupKey: String,
    onDismiss: () -> Unit,
    onSaveKey: (primary: String, backup: String) -> Unit,
    onClearKey: () -> Unit
) {
    val strings = LocalAppStrings.current
    var apiKeyInput by rememberSaveable { mutableStateOf("") }
    var backupKeyInput by rememberSaveable { mutableStateOf(initialBackupKey) }
    var showKeyText by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = strings.apiKeyDialogTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.apiKeyDialogDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isConfigured) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EmeraldReady.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, EmeraldReady.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strings.apiKeyBannerConfiguredTitle(maskedKey),
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldReady,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text(strings.apiKeyInputLabel) },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    visualTransformation = if (showKeyText) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showKeyText = !showKeyText }) {
                            Icon(
                                imageVector = if (showKeyText) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = "Toggle key visibility"
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("permanent_api_key_input")
                )

                OutlinedTextField(
                    value = backupKeyInput,
                    onValueChange = { backupKeyInput = it },
                    label = { Text(strings.backupApiKeyInputLabel) },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    visualTransformation = if (showKeyText) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_api_key_input")
                )

                if (isConfigured) {
                    TextButton(
                        onClick = onClearKey,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(strings.clearSavedKeyBtn)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveKey(apiKeyInput, backupKeyInput) },
                enabled = apiKeyInput.isNotBlank(),
                modifier = Modifier.testTag("save_permanent_api_key_button")
            ) {
                Text(strings.confirmSavePermanentBtn)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_api_key_dialog_button")
            ) {
                Text(strings.cancelBtn)
            }
        }
    )
}
