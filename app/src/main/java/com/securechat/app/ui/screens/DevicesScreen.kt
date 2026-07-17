package com.securechat.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.compose.ui.res.stringResource
import com.securechat.app.R
import com.securechat.app.data.network.DeviceListItem
import com.securechat.app.data.network.LinkedDevice
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.theme.topBarTitleColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val devices by viewModel.linkedDevices.collectAsState()
    val enrolledDevices by viewModel.enrolledDevices.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val context = LocalContext.current

    // QR-Scanner Launcher (ZXing)
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { qrContent ->
            viewModel.linkDeviceWithQrContent(qrContent)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadLinkedDevices()
        viewModel.loadEnrolledDevices()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.devices_title),
                        color = topBarTitleColor(),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.general_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Info-Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Computer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            stringResource(R.string.devices_info_title),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.devices_info_subtitle),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Gerät hinzufügen Button
            Button(
                onClick = {
                    val options = ScanOptions().apply {
                        setPrompt(context.getString(R.string.devices_qr_prompt))
                        setBeepEnabled(false)
                        setOrientationLocked(false)
                        setCameraId(0)
                    }
                    scanLauncher.launch(options)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.devices_add_button), fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(16.dp))

            // Status Message
            AnimatedVisibility(
                visible = statusMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                statusMessage?.let { msg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (msg.startsWith("✓"))
                                Color(0xFF1B5E20).copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                msg,
                                modifier = Modifier.weight(1f),
                                color = if (msg.startsWith("✓"))
                                    Color(0xFF81C784)
                                else
                                    MaterialTheme.colorScheme.onErrorContainer
                            )
                            TextButton(onClick = { viewModel.clearStatus() }) {
                                Text(stringResource(R.string.general_ok))
                            }
                        }
                    }
                }
            }

            // Geräteliste
            if (devices.isEmpty() && enrolledDevices.isEmpty()) {
                DevicesEmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Web-Linked Devices (QR-Code)
                    if (devices.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.devices_active_count, devices.size),
                                modifier = Modifier.padding(vertical = 4.dp),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(devices, key = { "linked_${it.id}" }) { device ->
                            LinkedDeviceItem(
                                device = device,
                                onRemove = { viewModel.removeLinkedDevice(device.id) }
                            )
                        }
                    }

                    // UMK-Enrolled Devices (Multi-Device E2EE)
                    if (enrolledDevices.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Multi-Device E2EE (${enrolledDevices.size})",
                                modifier = Modifier.padding(vertical = 4.dp),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(enrolledDevices, key = { "enrolled_${it.deviceId}" }) { device ->
                            EnrolledDeviceItem(
                                device = device,
                                onRemove = { viewModel.removeEnrolledDevice(device.deviceId) }
                            )
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun LinkedDeviceItem(device: LinkedDevice, onRemove: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.devices_disconnect_dialog)) },
            text = { Text(stringResource(R.string.devices_disconnect_text, device.deviceName)) },
            confirmButton = {
                Button(
                    onClick = { showConfirm = false; onRemove() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.devices_disconnect_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.devices_disconnect_cancel)) }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Device icon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val icon = when {
                    device.deviceName.contains("Android", ignoreCase = true) -> Icons.Default.PhoneAndroid
                    device.deviceName.contains("iPhone", ignoreCase = true) -> Icons.Default.PhoneIphone
                    else -> Icons.Default.Computer
                }
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.deviceName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                val linkedStr = stringResource(R.string.devices_linked_text)
                val lastStr = stringResource(R.string.devices_last_active)
                Text(
                    buildString {
                        append("$linkedStr ${formatDeviceDate(device.createdAt)}")
                        device.lastActive?.let { append(" · $lastStr ${formatDeviceDate(it)}") }
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { showConfirm = true }) {
                Icon(
                    Icons.Default.LinkOff,
                    contentDescription = stringResource(R.string.devices_disconnect_button),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun EnrolledDeviceItem(device: DeviceListItem, onRemove: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.devices_disconnect_dialog)) },
            text = { Text(stringResource(R.string.devices_disconnect_text, device.deviceName)) },
            confirmButton = {
                Button(
                    onClick = { showConfirm = false; onRemove() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.devices_disconnect_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.devices_disconnect_cancel)) }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val icon = when {
                    device.deviceName.contains("Android", ignoreCase = true) -> Icons.Default.PhoneAndroid
                    device.deviceName.contains("iPhone", ignoreCase = true) -> Icons.Default.PhoneIphone
                    device.deviceName.contains("Web", ignoreCase = true) -> Icons.Default.Computer
                    else -> Icons.Default.Devices
                }
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.deviceName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append("Registriert: ${formatDeviceDate(device.createdAt)}")
                        device.lastUsed?.let { append(" · Zuletzt: ${formatDeviceDate(it)}") }
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { showConfirm = true }) {
                Icon(
                    Icons.Default.LinkOff,
                    contentDescription = stringResource(R.string.devices_disconnect_button),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun DevicesEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.DevicesOther,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        )
        Text(
            stringResource(R.string.devices_empty_title),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.devices_empty_subtitle),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

private fun formatDeviceDate(iso: String?): String {
    if (iso == null) return "–"
    return try {
        val instant = Instant.parse(if (iso.endsWith("Z")) iso else "${iso}Z")
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        iso.take(10)
    }
}
