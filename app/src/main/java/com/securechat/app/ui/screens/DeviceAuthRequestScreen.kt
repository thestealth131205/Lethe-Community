package com.securechat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.securechat.app.R
import com.securechat.app.data.network.DeviceAuthRequestInfo
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.utils.BiometricHelper

/**
 * Vollbild-Freigabe-Screen: wird geöffnet wenn ein anderes Gerät (Media Player, Web Chat,
 * weiteres Smartphone) sich per Lethe Messenger an diesem Konto anmelden möchte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceAuthRequestScreen(
    requestId: String,
    viewModel: MainViewModel,
    activity: FragmentActivity,
    onNavigateBack: () -> Unit
) {
    var info by remember { mutableStateOf<DeviceAuthRequestInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    val biometricHelper = remember(activity) { BiometricHelper(activity) }
    val canUseBiometric = remember { biometricHelper.canAuthenticate() }

    LaunchedEffect(requestId) {
        info = viewModel.fetchDeviceAuthRequestInfo(requestId)
        loading = false
        if (info == null) loadError = "Anfrage nicht gefunden oder abgelaufen."
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.device_auth_title)) })
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                done -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Erledigt", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) { Text("Zurück") }
                    }
                }
                loadError != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(loadError!!, color = MaterialTheme.colorScheme.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) { Text("Zurück") }
                    }
                }
                info != null -> {
                    val i = info!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = i.deviceName ?: i.appName,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.device_auth_text),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(i.ipAddress ?: "Unbekannte IP")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(i.approxLocation ?: "Unbekannter Standort")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        if (canUseBiometric) {
                            Button(
                                onClick = {
                                    submitError = null
                                    biometricHelper.showPrompt(
                                        activity = activity,
                                        onSuccess = {
                                            submitting = true
                                            viewModel.approveDeviceAuthRequest(requestId, null, true) { ok, err ->
                                                submitting = false
                                                if (ok) done = true else submitError = err
                                            }
                                        },
                                        onError = { submitError = it }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !submitting,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.device_auth_biometric_button))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("oder", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.device_auth_password_hint)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        submitError?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                submitError = null
                                submitting = true
                                viewModel.approveDeviceAuthRequest(requestId, password, false) { ok, err ->
                                    submitting = false
                                    if (ok) done = true else submitError = err
                                }
                            },
                            enabled = !submitting && password.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                        ) {
                            if (submitting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                            } else {
                                Text("Bestätigen", color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedButton(
                            onClick = {
                                submitting = true
                                viewModel.denyDeviceAuthRequest(requestId) { _ ->
                                    submitting = false
                                    done = true
                                }
                            },
                            enabled = !submitting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.device_auth_deny_button), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
