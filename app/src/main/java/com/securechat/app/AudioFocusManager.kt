package com.securechat.app

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import timber.log.Timber

/**
 * Verwaltet Audio-Fokus und Bluetooth-Audio-Routing zentral für die gesamte App.
 *
 * Verwendung:
 * - [requestFocus]: Beim Öffnen des SparksFeedScreens oder beim Starten eines Calls aufrufen.
 * - [abandonFocus]: Beim Verlassen des SparksFeedScreens oder nach Ende eines Calls aufrufen.
 * - [register]: In MainActivity.onCreate() aufrufen.
 * - [unregister]: In MainActivity.onDestroy() aufrufen.
 *
 * Ausnahme: Der interne "listentogether"-Player beeinflusst diesen Manager NICHT –
 * er verwaltet seinen eigenen Audio-Fokus separat als AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK.
 */
class AudioFocusManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // AudioFocusRequest-Objekt (API 26+), wird beim Abandon benötigt
    private var focusRequest: AudioFocusRequest? = null

    // Legacy-Listener für API < 26 (wird für abandonAudioFocus benötigt)
    private val legacyFocusListener = AudioManager.OnAudioFocusChangeListener { }

    // Gibt an ob wir aktuell Audio-Fokus halten
    private var hasFocus = false

    /**
     * true solange echte Sprachkommunikation läuft (Sprach-/Videoanruf oder
     * Sprachnachricht-Aufnahme). Nur dann darf ein verbundenes Bluetooth-Headset in den
     * SCO-/Headset-Modus (Mikrofon, schmalbandig) geschaltet werden. Bei reiner Wiedergabe
     * (z. B. Musik im Chat) bleibt die hochwertige A2DP-Ausgabe aktiv.
     */
    @Volatile
    private var communicationActive = false

    // Handler für einen verzögerten, erneuten Normal-Modus-Reset (siehe forceResetToNormalMode).
    private val mainHandler = Handler(Looper.getMainLooper())

    // Generationszähler: macht einen bereits geplanten verzögerten Reset ungültig, sobald ein
    // neuer Anruf startet oder ein neuer Reset ausgelöst wird.
    @Volatile
    private var resetGeneration = 0

    /**
     * true solange Android Auto verbunden ist (Projektion auf ein Kopfgerät oder natives
     * Android Automotive). Bei Wireless Android Auto bleibt zusätzlich ein klassischer
     * Bluetooth-Link (HFP/A2DP) bestehen – dieser dient dort aber NUR dem Verbindungsaufbau,
     * die eigentliche Audio-/Anruf-Übertragung läuft über den Android-Auto-Kanal (WLAN oder
     * dediziertes AA-Protokoll). Solange Android Auto verbunden ist, darf [routeAudioToBluetooth]
     * daher NICHT manuell auf SCO/Kommunikationsgerät umschalten – das System routet Audio in
     * diesem Fall bereits selbst korrekt über Android Auto; ein manueller Eingriff würde den
     * gesamten Audio-Ausgang (Anrufe, Sprachnachrichten, Musik, Videoton) auf klassisches,
     * qualitativ schlechteres Bluetooth-SCO zwingen.
     */
    @Volatile
    var isAndroidAutoConnected: Boolean = false
        private set

    private val carConnection = CarConnection(context)
    private val carConnectionObserver = Observer<Int> { connectionType ->
        isAndroidAutoConnected = connectionType == CarConnection.CONNECTION_TYPE_PROJECTION ||
            connectionType == CarConnection.CONNECTION_TYPE_NATIVE
        Timber.tag("LETHE_AUDIO").d(
            "Android-Auto-Verbindungsstatus: $connectionType (verbunden=$isAndroidAutoConnected)"
        )
    }

    /**
     * Schaltet den Kommunikationsmodus um. Wird beim Start/Ende eines Anrufs bzw. einer
     * Sprachnachricht-Aufnahme aufgerufen. Steuert, ob ein (neu) verbundenes Headset auf
     * SCO geroutet wird.
     */
    fun setCommunicationActive(active: Boolean) {
        communicationActive = active
        // Startet gerade wieder Sprachkommunikation, einen noch ausstehenden verzögerten
        // Normal-Reset entwerten, damit er den frischen Kommunikationsmodus nicht zerstört.
        if (active) resetGeneration++
        Timber.tag("LETHE_AUDIO").d("communicationActive=$active")
    }

    /**
     * Betritt den Sprachkommunikations-Modus (Anruf-Annahme oder Sprachnachricht-Aufnahme).
     * Setzt das Flag und aktiviert – falls bereits ein Bluetooth-Headset verbunden ist –
     * SCO, damit das Headset-Mikrofon genutzt wird. So funktioniert die Headset-Erkennung
     * gezielt nur für Sprachkommunikation und nicht für reine Wiedergabe (Musik).
     */
    fun enterVoiceCommunication() {
        setCommunicationActive(true)
        if (isBluetoothHeadsetConnected()) {
            Timber.tag("LETHE_AUDIO").d("Sprachkommunikation gestartet – Headset verbunden, SCO aktivieren")
            routeAudioToBluetooth(withMicrophone = true)
        }
    }

    /** Verlässt den Sprachkommunikations-Modus und stellt Standard-Routing wieder her. */
    fun exitVoiceCommunication() {
        setCommunicationActive(false)
        restoreDefaultAudioRouting()
    }

    /** true solange echte Sprachkommunikation (Anruf/Sprachnachricht-Aufnahme) läuft. */
    fun isCommunicationActive(): Boolean = communicationActive

    /**
     * Hartes Zurücksetzen in den Normal-Audiomodus. Setzt MODE_NORMAL, schaltet Speakerphone ab
     * und löst SCO / das Kommunikationsgerät. Anders als [restoreDefaultAudioRouting] wird der
     * Modus bedingungslos zurückgesetzt (KEIN MODE_IN_COMMUNICATION-Schutz), da der Aufrufer bereits
     * sicherstellt, dass keine Sprachkommunikation mehr läuft.
     *
     * Sicherheitsnetz gegen einen "hängenden" Telefonmodus: Wenn ein Anruf durch Falschbedienung
     * oder Absturz nie sauber beendet wurde, bleibt das System sonst in MODE_IN_COMMUNICATION und
     * routet Sounds über die Ohrmuschel statt den Lautsprecher.
     */
    fun forceResetToNormalMode() {
        communicationActive = false
        val generation = ++resetGeneration
        applyNormalModeReset()
        // Bluetooth-SCO wird ab Android 12 ASYNCHRON freigegeben (clearCommunicationDevice
        // kehrt sofort zurück, das Routing wird erst danach neu bewertet). Wurde der Anruf mit
        // einem Bluetooth-Headset geführt, überschreibt die Audio-Policy nach der SCO-Freigabe
        // den gerade gesetzten MODE_NORMAL wieder mit MODE_IN_COMMUNICATION → der Ton bleibt
        // prozessweit an der Ohrmuschel hängen (nur der Telefon-Lautstärkeregler erscheint,
        // Force-Stop half bisher). Deshalb den Reset nach der asynchronen SCO-Freigabe erneut
        // hart durchsetzen – aber nur, wenn inzwischen kein neuer Anruf begonnen hat.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mainHandler.postDelayed({
                if (generation == resetGeneration && !communicationActive) {
                    applyNormalModeReset()
                    Timber.tag("LETHE_AUDIO").d("forceResetToNormalMode: verzögerter Re-Assert nach SCO-Freigabe")
                }
            }, 400)
        }
    }

    /** Setzt SCO/Kommunikationsgerät, Speakerphone und Audiomodus synchron auf Normal zurück. */
    private fun applyNormalModeReset() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                if (audioManager.isBluetoothScoOn) {
                    @Suppress("DEPRECATION")
                    audioManager.stopBluetoothSco()
                    @Suppress("DEPRECATION")
                    audioManager.isBluetoothScoOn = false
                }
            }
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            if (audioManager.mode != AudioManager.MODE_NORMAL) {
                audioManager.mode = AudioManager.MODE_NORMAL
                Timber.tag("LETHE_AUDIO").d("applyNormalModeReset: Audiomodus hart auf NORMAL zurückgesetzt")
            }
        } catch (e: Exception) {
            Timber.tag("LETHE_AUDIO").w(e, "applyNormalModeReset fehlgeschlagen")
        }
    }

    /** true wenn aktuell ein Bluetooth-Headset (mit Mikrofon/SCO) verbunden ist. */
    private fun isBluetoothHeadsetConnected(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices.any { device ->
                device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                device.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoAvailableOffCall
        }
    } catch (_: Exception) {
        false
    }

    // BroadcastReceiver für Bluetooth-Verbindungsänderungen
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val action = intent.action ?: return
            val state = intent.getIntExtra(
                BluetoothProfile.EXTRA_STATE,
                BluetoothProfile.STATE_DISCONNECTED
            )
            when (action) {
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    // A2DP: reine Wiedergabe-Kopfhörer (kein Mikrofon)
                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Timber.tag("LETHE_AUDIO").d("Bluetooth A2DP verbunden – Audio-Ausgabe auf BT routen")
                            routeAudioToBluetooth(withMicrophone = false)
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Timber.tag("LETHE_AUDIO").d("Bluetooth A2DP getrennt – zurück auf Standard-Ausgabe")
                            restoreDefaultAudioRouting()
                        }
                    }
                }
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    // Headset: mit Mikrofon (für Calls/Sprachnachrichten relevant)
                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            if (communicationActive) {
                                // Aktiver Anruf/Sprachnachricht: SCO für bidirektionales Audio.
                                Timber.tag("LETHE_AUDIO").d("Headset verbunden während Kommunikation – SCO aktivieren")
                                routeAudioToBluetooth(withMicrophone = true)
                            } else {
                                // Keine Sprachkommunikation aktiv (z. B. Musik-Wiedergabe):
                                // NICHT in den SCO-/Headset-Modus schalten – das würde die
                                // Audioqualität verschlechtern. Hochwertige A2DP-Ausgabe nutzen.
                                Timber.tag("LETHE_AUDIO").d("Headset verbunden ohne Kommunikation – A2DP-Ausgabe (kein SCO)")
                                routeAudioToBluetooth(withMicrophone = false)
                            }
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Timber.tag("LETHE_AUDIO").d("Bluetooth Headset getrennt – SCO deaktivieren")
                            restoreDefaultAudioRouting()
                        }
                    }
                }
            }
        }
    }

    private var receiverRegistered = false

    /**
     * BroadcastReceiver registrieren. Einmalig in MainActivity.onCreate() aufrufen.
     */
    fun register() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        context.registerReceiver(bluetoothReceiver, filter)
        receiverRegistered = true
        // observeForever statt LiveData.observe(), da AudioFocusManager kein LifecycleOwner ist –
        // Gegenstück ist removeObserver() in unregister().
        carConnection.type.observeForever(carConnectionObserver)
        Timber.tag("LETHE_AUDIO").d("AudioFocusManager registriert")
    }

    /**
     * BroadcastReceiver deregistrieren. Einmalig in MainActivity.onDestroy() aufrufen.
     */
    fun unregister() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {}
        carConnection.type.removeObserver(carConnectionObserver)
        receiverRegistered = false
        Timber.tag("LETHE_AUDIO").d("AudioFocusManager deregistriert")
    }

    /**
     * Audio-Fokus anfordern (AUDIOFOCUS_GAIN).
     * Pausiert Musik anderer Apps (z.B. Spotify). Der interne listentogether-Player
     * wird hiervon NICHT beeinflusst, da er AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK verwendet.
     *
     * Aufrufen: beim Betreten des SparksFeedScreens und beim Starten eines Video-/Sprach-Calls.
     */
    fun requestFocus() {
        if (hasFocus) return
        Timber.tag("LETHE_AUDIO").d("AudioFocus anfordern (AUDIOFOCUS_GAIN_TRANSIENT)")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    Timber.tag("LETHE_AUDIO").d("AudioFocus-Änderung: $focusChange")
                }
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                legacyFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
        hasFocus = true
    }

    /**
     * Audio-Fokus freigeben.
     * Aufrufen: beim Verlassen des SparksFeedScreens und nach Ende eines Video-/Sprach-Calls.
     */
    fun abandonFocus() {
        if (!hasFocus) return
        Timber.tag("LETHE_AUDIO").d("AudioFocus freigeben")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(legacyFocusListener)
        }
        hasFocus = false
    }

    // ── Bluetooth-Routing ─────────────────────────────────────────────────────

    /**
     * Audio auf Bluetooth-Gerät routen.
     * @param withMicrophone true = Headset mit Mikrofon (SCO aktivieren), false = nur A2DP-Ausgabe.
     */
    private fun routeAudioToBluetooth(withMicrophone: Boolean) {
        if (isAndroidAutoConnected) {
            // Android Auto aktiv: kein manuelles SCO-/A2DP-Erzwingen. Der Bluetooth-Link dient
            // bei Wireless Android Auto nur dem Verbindungsaufbau – das System routet Anrufe,
            // Sprachnachrichten, Musik und Videoton bereits selbst korrekt über Android Auto.
            Timber.tag("LETHE_AUDIO").d("Android Auto verbunden – Bluetooth-Routing NICHT erzwungen")
            return
        }
        if (withMicrophone) {
            // Headset mit Mikrofon: SCO für bidirektionales Audio (Calls)
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31+: explizit das SCO-Kommunikationsgerät setzen
                val btDevice = audioManager.availableCommunicationDevices.firstOrNull { device ->
                    device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                if (btDevice != null) {
                    audioManager.setCommunicationDevice(btDevice)
                    Timber.tag("LETHE_AUDIO").d("SCO-Gerät gesetzt: ${btDevice.productName}")
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
            }
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        } else {
            // Reine Ausgabe (A2DP, kein Mikrofon):
            // Wenn gerade ein Anruf läuft (MODE_IN_COMMUNICATION), den Modus NICHT auf NORMAL
            // zurücksetzen – das würde den Anruf-Audio unterbrechen.
            // Stattdessen Speakerphone deaktivieren: Android routet dann automatisch auf A2DP.
            if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
                Timber.tag("LETHE_AUDIO").d("A2DP verbunden während Anruf – Speakerphone aus, A2DP übernimmt Ausgabe")
            } else {
                audioManager.mode = AudioManager.MODE_NORMAL
            }
        }
    }

    /**
     * Standard-Audio-Routing wiederherstellen (nach BT-Trennung).
     */
    private fun restoreDefaultAudioRouting() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            if (audioManager.isBluetoothScoOn) {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
            }
        }
        // Modus nur zurücksetzen wenn KEIN aktiver Anruf läuft.
        // Während eines Anrufs bleibt MODE_IN_COMMUNICATION, damit WebRTC Audio weiterläuft.
        if (audioManager.mode != AudioManager.MODE_IN_COMMUNICATION) {
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }
}
