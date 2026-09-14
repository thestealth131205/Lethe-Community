package com.securechat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.securechat.app.R
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.components.PhoneInputField
import com.securechat.app.ui.components.buildE164

/**
 * Dialog zum Ändern der eigenen Handynummer (aus dem 3-Punkte-Menü unter Account).
 *
 * Ablauf:
 *   1. Neue Nummer eingeben → SMS-Code anfordern (POST /sms/send-phone-otp)
 *   2. Erhaltenen Code eingeben → bestätigen (POST /sms/verify-phone)
 *   3. Bei Erfolg: allen Kontakten wird eine Handshake-Erneuerungs-Anfrage
 *      (neuer Schlüssel) gesendet und "Nummer erfolgreich geändert" angezeigt.
 */
@Composable
fun ChangePhoneNumberDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsState()

    var countryCode by remember { mutableStateOf("+49") }
    var localNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val phoneE164 = buildE164(countryCode, localNumber)

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.change_phone_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = if (!otpSent) stringResource(R.string.change_phone_desc)
                    else stringResource(R.string.change_phone_code_sent, phoneE164),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(20.dp))

                if (!otpSent) {
                    PhoneInputField(
                        countryCode = countryCode,
                        localNumber = localNumber,
                        onCountryCodeChange = { countryCode = it },
                        onLocalNumberChange = { localNumber = it }
                    )
                } else {
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpCode = it },
                        label = { Text(stringResource(R.string.change_phone_code_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                errorText?.let { err ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { if (!isLoading) onDismiss() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.general_cancel))
                    }

                    Button(
                        onClick = {
                            errorText = null
                            if (!otpSent) {
                                if (phoneE164.length >= 7) {
                                    viewModel.sendPhoneChangeOtp(phoneE164) { ok, msg ->
                                        if (ok) otpSent = true else errorText = msg
                                    }
                                }
                            } else {
                                viewModel.confirmPhoneNumberChange(phoneE164, otpCode) { ok, msg ->
                                    if (ok) onDismiss() else errorText = msg
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading && (
                            (!otpSent && localNumber.isNotBlank()) ||
                            (otpSent && otpCode.length == 6)
                        ),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(
                                if (!otpSent) R.string.change_phone_send_code
                                else R.string.change_phone_confirm
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
