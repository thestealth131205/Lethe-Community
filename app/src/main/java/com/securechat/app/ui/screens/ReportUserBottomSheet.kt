package com.securechat.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.securechat.app.R
import com.securechat.app.ui.MainViewModel

/**
 * Wiederverwendbares Bottom Sheet zum Melden von Nutzern.
 *
 * @param reportedUserId  ID des zu meldenden Nutzers
 * @param contextSource   Kontext-Quelle: "CHAT" | "NEARBY" | "VIP_SPARK"
 * @param onDismiss       Callback wenn das Sheet geschlossen wird
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportUserBottomSheet(
    viewModel: MainViewModel,
    reportedUserId: String,
    contextSource: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val reasons = listOf(
        stringResource(R.string.report_reason_spam),
        stringResource(R.string.report_reason_harassment),
        stringResource(R.string.report_reason_inappropriate),
        stringResource(R.string.report_reason_fake),
        stringResource(R.string.report_reason_hate),
        stringResource(R.string.report_reason_other)
    )
    var selectedReason by remember { mutableStateOf(reasons[0]) }
    var details by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val successText = stringResource(R.string.report_success)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Flag, null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    stringResource(R.string.report_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.report_reason),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Column(modifier = Modifier.selectableGroup()) {
                reasons.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            OutlinedTextField(
                value = details,
                onValueChange = { details = it },
                placeholder = { Text(stringResource(R.string.report_details_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
                minLines = 2
            )

            Button(
                onClick = {
                    isSubmitting = true
                    viewModel.reportUser(
                        reportedUserId = reportedUserId,
                        reason = selectedReason,
                        details = details.takeIf { it.isNotBlank() },
                        reportContext = contextSource,
                        onSuccess = {
                            isSubmitting = false
                            Toast.makeText(context, successText, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    )
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError
                    )
                } else {
                    Text(stringResource(R.string.report_button), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
