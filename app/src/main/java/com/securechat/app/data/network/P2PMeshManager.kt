package com.securechat.app.data.network

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/** Repräsentiert einen gefundenen P2P-Peer im Mesh-Netzwerk. */
data class MeshPeer(
    val peerId: String,
    val displayName: String,
    val transport: MeshTransport,
    val rssi: Int = -1,
    val lastSeen: Long = System.currentTimeMillis()
)

enum class MeshTransport { BLUETOOTH_LE, WIFI_DIRECT, UNKNOWN }

/** Eingehende P2P-Nachricht. */
data class MeshMessage(
    val fromPeerId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * P2P-Mesh-Manager für dezentrales Messaging ohne zentralen Server.
 *
 * Nutzt:
 *  - Bluetooth LE (BLE): Peer-Entdeckung via Service-Advertisement
 *  - WiFi Direct: Transport nach Peer-Entdeckung
 *
 * Architektur angelehnt an libp2p:
 *  - Peer-IDs, Service-Discovery, Transport-Abstraktion
 */
@Singleton
class P2PMeshManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val LETHE_SERVICE_UUID: UUID = UUID.fromString("6c657468-6500-4000-8000-000000000001")
        private const val TAG = "P2PMeshManager"
    }

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<MeshPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<MeshPeer>> = _discoveredPeers.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<MeshMessage>(replay = 0, extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<MeshMessage> = _incomingMessages.asSharedFlow()

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private var leScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var advertiseCallback: AdvertiseCallback? = null

    private val wifiP2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }
    private var wifiP2pChannel: WifiP2pManager.Channel? = null

    val myPeerId: String by lazy {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: UUID.randomUUID().toString()
        "lethe-${androidId.take(16)}"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        if (_isActive.value) return
        Log.i(TAG, "P2P Mesh gestartet. Peer-ID: $myPeerId")
        _isActive.value = true
        _statusMessage.value = "Mesh aktiv – suche nach Peers…"
        startBleAdvertising()
        startBleScanning()
        initWifiDirect()
        scope.launch {
            while (isActive) {
                delay(30_000)
                pruneOldPeers()
            }
        }
    }

    fun stop() {
        if (!_isActive.value) return
        Log.i(TAG, "P2P Mesh gestoppt.")
        _isActive.value = false
        _statusMessage.value = null
        stopBleScanning()
        stopBleAdvertising()
        _discoveredPeers.value = emptyList()
    }

    private fun hasBleAdvertisePermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun hasBleScanPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun hasBleConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun startBleAdvertising() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) { Log.w(TAG, "Bluetooth deaktiviert."); return }
        if (!hasBleAdvertisePermission()) { Log.w(TAG, "BLUETOOTH_ADVERTISE fehlt."); return }

        val advertiser = adapter.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(LETHE_SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build()

        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.i(TAG, "BLE-Advertising aktiv.")
                _statusMessage.value = "BLE aktiv – für andere Lethe-Nutzer in der Nähe sichtbar"
            }
            override fun onStartFailure(errorCode: Int) {
                Log.w(TAG, "BLE-Advertising Fehler: $errorCode")
            }
        }
        advertiseCallback = cb
        advertiser.startAdvertising(settings, data, cb)
    }

    private fun stopBleAdvertising() {
        if (!hasBleAdvertisePermission()) return
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: return
        advertiseCallback?.let { advertiser.stopAdvertising(it) }
        advertiseCallback = null
    }

    private fun startBleScanning() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return
        if (!hasBleScanPermission()) { Log.w(TAG, "BLUETOOTH_SCAN fehlt."); return }

        val scanner = adapter.bluetoothLeScanner ?: return

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(LETHE_SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                onBlePeerFound(result)
            }
            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "BLE-Scan Fehler: $errorCode")
            }
        }
        scanCallback = cb
        leScanner = scanner
        scanner.startScan(listOf(filter), settings, cb)
        Log.i(TAG, "BLE-Scan gestartet.")
    }

    private fun stopBleScanning() {
        if (!hasBleScanPermission()) return
        scanCallback?.let { leScanner?.stopScan(it) }
        scanCallback = null
        leScanner = null
    }

    private fun onBlePeerFound(result: ScanResult) {
        if (!hasBleConnectPermission()) return
        val device = result.device
        val address = device.address ?: return
        val name = try { device.name?.takeIf { it.isNotBlank() } ?: "Lethe-Peer" } catch (e: Exception) { "Lethe-Peer" }
        val peerId = "lethe-ble-${address.replace(":", "").lowercase()}"

        val existing = _discoveredPeers.value
        if (existing.any { it.peerId == peerId }) {
            _discoveredPeers.value = existing.map {
                if (it.peerId == peerId) it.copy(lastSeen = System.currentTimeMillis(), rssi = result.rssi)
                else it
            }
        } else {
            val peer = MeshPeer(peerId = peerId, displayName = name, transport = MeshTransport.BLUETOOTH_LE, rssi = result.rssi)
            _discoveredPeers.value = existing + peer
            Log.i(TAG, "Neuer BLE-Peer: $peerId ($name, RSSI ${result.rssi})")
            _statusMessage.value = "${_discoveredPeers.value.size} Peer(s) in der Nähe"
        }
    }

    private fun initWifiDirect() {
        val manager = wifiP2pManager ?: return
        wifiP2pChannel = manager.initialize(context, context.mainLooper, null)
        Log.i(TAG, "WiFi Direct initialisiert.")
    }

    fun discoverWifiPeers() {
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) return

        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.i(TAG, "WiFi Direct Peer-Discovery gestartet.") }
            override fun onFailure(reason: Int) { Log.w(TAG, "WiFi Direct Discovery Fehler: $reason") }
        })
    }

    fun getPeers(): List<MeshPeer> = _discoveredPeers.value

    private fun pruneOldPeers() {
        val cutoff = System.currentTimeMillis() - 60_000
        _discoveredPeers.value = _discoveredPeers.value.filter { it.lastSeen > cutoff }
    }
}
