@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.lethe.mediaplayer.player

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import androidx.media3.exoplayer.ExoPlayer
import com.lethe.mediaplayer.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wählt die Audio-Ausgabe des Lethe Medie Players live nach fester Priorität:
 *
 * 1. **Android Auto** (Projektion oder natives Automotive) – kein manueller Geräte-Zwang,
 *    das System routet Wiedergabe-Ton bereits selbst über den Android-Auto-Kanal. Ein
 *    parallel bestehender klassischer Bluetooth-A2DP-Link (bei Wireless Android Auto nur
 *    zum Verbindungsaufbau da) würde sonst fälschlich bevorzugt und die Wiedergabe auf
 *    schlechteres Bluetooth-Routing zwingen.
 * 2. **Bluetooth (A2DP)** – wird explizit als bevorzugtes Ausgabegerät gesetzt, sobald
 *    verbunden. Der tatsächlich genutzte Codec (SBC/AAC/aptX/aptX HD/LDAC) wird nur
 *    diagnostisch geloggt, da die Codec-Aushandlung selbst vom Bluetooth-Stack des
 *    Betriebssystems übernommen wird und nicht von der App erzwungen werden kann.
 * 3. **Geräte-Lautsprecher** – Fallback, wenn weder Android Auto noch Bluetooth aktiv sind.
 *
 * Reagiert live auf Änderungen während des Betriebs (nicht nur beim Start der Wiedergabe).
 */
@Singleton
class AudioRoutingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var player: ExoPlayer? = null
    private var registered = false

    private var isAndroidAutoConnected = false
    private var bluetoothA2dpProxy: BluetoothA2dp? = null

    private val carConnection = CarConnection(context)
    private val carConnectionObserver = Observer<Int> { connectionType ->
        isAndroidAutoConnected = connectionType == CarConnection.CONNECTION_TYPE_PROJECTION ||
            connectionType == CarConnection.CONNECTION_TYPE_NATIVE
        AppLogger.d(TAG,"Android-Auto-Status: $connectionType (verbunden=$isAndroidAutoConnected)")
        applyRouting()
    }

    // Feuert bei jeder Änderung der verfügbaren Ausgabegeräte (Bluetooth verbunden/getrennt,
    // Kopfhörer ein-/ausgesteckt, ...) – funktioniert ohne zusätzliche Bluetooth-Berechtigung.
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            probeBluetoothCodec()
            applyRouting()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            applyRouting()
        }
    }

    private val bluetoothProfileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            bluetoothA2dpProxy = proxy as? BluetoothA2dp
            probeBluetoothCodec()
        }

        override fun onServiceDisconnected(profile: Int) {
            bluetoothA2dpProxy = null
        }
    }

    /** Vom [PlaybackService] beim Erstellen des Players aufgerufen. */
    fun attach(exoPlayer: ExoPlayer) {
        player = exoPlayer
        if (!registered) {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
            carConnection.type.observeForever(carConnectionObserver)
            if (hasBluetoothConnectPermission()) {
                runCatching {
                    BluetoothAdapter.getDefaultAdapter()
                        ?.getProfileProxy(context, bluetoothProfileListener, BluetoothProfile.A2DP)
                }
            }
            registered = true
        }
        applyRouting()
    }

    /** Vom [PlaybackService] in onDestroy() aufgerufen. */
    fun detach() {
        if (registered) {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            carConnection.type.removeObserver(carConnectionObserver)
            bluetoothA2dpProxy?.let { proxy ->
                runCatching { BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(BluetoothProfile.A2DP, proxy) }
            }
            registered = false
        }
        player = null
    }

    private fun applyRouting() {
        val exo = player ?: return
        exo.setPreferredAudioDevice(resolvePreferredDevice())
    }

    /** Wendet das aktuell ermittelte bevorzugte Ausgabegerät auch auf einen weiteren Player an
     * (z.B. den Crossfade-Standby-Player kurz bevor er zu spielen beginnt, damit er von Anfang
     * an auf demselben Ausgabegerät wie der aktive Player läuft). */
    fun applyRoutingTo(exoPlayer: ExoPlayer) {
        exoPlayer.setPreferredAudioDevice(resolvePreferredDevice())
    }

    private fun resolvePreferredDevice(): AudioDeviceInfo? {
        if (isAndroidAutoConnected) {
            AppLogger.d(TAG,"Routing: Android Auto (System-Default)")
            return null
        }

        val bluetoothDevice = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        if (bluetoothDevice != null) {
            AppLogger.d(TAG,"Routing: Bluetooth (${bluetoothDevice.productName})")
            return bluetoothDevice
        }

        val speaker = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        AppLogger.d(TAG,"Routing: Geräte-Lautsprecher (Fallback)")
        return speaker
    }

    /** Rein diagnostisch: loggt den Namen des verbundenen Bluetooth-Geräts. */
    private fun probeBluetoothCodec() {
        if (!hasBluetoothConnectPermission()) return
        val proxy = bluetoothA2dpProxy ?: return
        runCatching {
            val device = proxy.connectedDevices.firstOrNull() ?: return
            AppLogger.d(TAG,"Bluetooth verbunden: ${device.name}")
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "LETHE_MP_AUDIO"
    }
}
