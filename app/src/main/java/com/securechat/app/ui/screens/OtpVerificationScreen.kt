package com.securechat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.app.R
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.components.PhoneInputField
import com.securechat.app.ui.components.buildE164

/**
 * OTP-Verifizierungs-Screen.
 *
 * Wird angezeigt, wenn is_phone_verified == false (alte Accounts ohne Telefon-Verifikation
 * oder neue Nutzer nach der Registrierung).
 *
 * Flow:
 *   1. Nutzer gibt seine Telefonnummer ein
 *   2. App sendet POST /sms/send-phone-otp → SMS mit 6-stelligem Code
 *   3. Nutzer gibt Code ein → POST /sms/verify-phone
 *   4. Bei Erfolg: ECDH-Schlüssel generieren + hochladen, dann onVerified() aufrufen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpVerificationScreen(
    viewModel: MainViewModel,
    onVerified: () -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var countryCode by remember { mutableStateOf("+49") }
    var localNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }

    val phoneE164 = buildE164(countryCode, localNumber)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PhoneAndroid,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.otp_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.otp_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(28.dp))

        if (!otpSent) {
            // Schritt 1: Telefonnummer eingeben
            Text(
                text = stringResource(R.string.otp_step1_title),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(8.dp))
            PhoneInputField(
                countryCode = countryCode,
                localNumber = localNumber,
                onCountryCodeChange = { countryCode = it },
                onLocalNumberChange = { localNumber = it }
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (phoneE164.length >= 7) {
                        viewModel.sendPhoneOtp(phoneE164)
                        otpSent = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && localNumber.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.otp_send_button))
            }
        } else {
            // Schritt 2: OTP-Code eingeben
            Text(
                text = stringResource(R.string.otp_step2_title),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.otp_step2_subtitle, phoneE164),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = otpCode,
                onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpCode = it },
                label = { Text(stringResource(R.string.otp_code_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.confirmPhoneOtp(phoneE164, otpCode, onVerified)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && otpCode.length == 6
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.otp_confirm_button))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    otpSent = false
                    otpCode = ""
                }
            ) {
                Text(stringResource(R.string.otp_back_button))
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = statusMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
