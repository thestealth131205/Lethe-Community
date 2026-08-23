package com.securechat.app.data.webrtc

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import timber.log.Timber

/**
 * Manages the full WebRTC lifecycle for 1-to-1 video calls.
 *
 *  - Initializes PeerConnectionFactory with hardware H.264 encoder/decoder.
 *  - Creates and holds local camera (max 720p) + microphone tracks.
 *  - Creates PeerConnection to STUN/TURN at letheapp.de.
 *  - Produces SDP offer/answer and ICE candidates via [SignalingCallback].
 *  - Routes audio to speakerphone (MODE_IN_COMMUNICATION).
 *  - Exposes [localVideoTrackFlow], [remoteVideoTrackFlow], [isMuted], [callState] as StateFlows.
 *  - On Activity.onPause → [onPause] disables video; audio continues.
 *  - Call [dispose] when the call is fully over to release all resources.
 */
class WebRtcClient(
    private val context: Context,
    private val partnerId: String,
    private val signalingCallback: SignalingCallback,
    /** "VIDEO" (default) oder "VOICE" – bestimmt ob Kamera benutzt wird. */
    val callType: String = "VIDEO",
    /** Serverseitig generierte TURN-Credentials (username, password). */
    private val turnUsername: String = "",
    private val turnPassword: String = "",
    /**
     * Gruppenanruf-Sekundärmodus: geteilter Video-Track vom primären WebRtcClient.
     * Wenn gesetzt, wird keine eigene Kamera geöffnet → verhindert Kamera-Konflikt.
     */
    private val sharedLocalVideoTrack: VideoTrack? = null,
    /**
     * Gruppenanruf-Sekundärmodus: geteilter Audio-Track vom primären WebRtcClient.
     * Wenn gesetzt, wird kein eigenes Mikrofon initialisiert → kein zweiter AudioManager-Eingriff.
     */
    private val sharedLocalAudioTrack: AudioTrack? = null,
    /** Selfie-Segmentierung für die Hintergrundunschärfe (Provider-Pattern, siehe [BackgroundBlurCapturerObserver]). */
    private val segmentationProvider: com.securechat.app.segmentation.SegmentationProvider,
    /** Allgemeine Audio-Qualität: "AUTO" | "LOW" | "HIGH" (steuert Mikrofon-Verbesserungen). */
    private val audioQuality: String = "AUTO",
    /** Gewünschter Audio-Ausgang: "SYSTEM" | "EARPIECE" | "SPEAKER" | "BLUETOOTH". */
    private val audioOutputChannel: String = "SYSTEM",
    /** false = Bluetooth-Headset (HFP) wird im Anruf nicht genutzt. */
    private val bluetoothHeadsetEnabled: Boolean = true,
    /**
     * true = Android Auto ist aktuell verbunden (siehe [com.securechat.app.AudioFocusManager]).
     * Der bei Wireless Android Auto weiterhin bestehende Bluetooth-Link dient dort nur dem
     * Verbindungsaufbau, nicht der Audioübertragung – bei "SYSTEM"-Kanal wird deshalb KEIN
     * manuelles SCO-Routing erzwungen, das System routet Anruf-Audio bereits selbst über
     * Android Auto (bessere Qualität als App-seitig erzwungenes klassisches Bluetooth-SCO).
     */
    private val androidAutoConnected: Boolean = false
) {

    /** true = Sekundärclient für Gruppenanruf – nutzt shared Tracks, kein eigenes Capture. */
    private val isSecondary: Boolean = sharedLocalVideoTrack != null || sharedLocalAudioTrack != null

    // ─────────────────────────────────────────────────────────────────────────
    // Public interface for signaling back to ViewModel
    // ─────────────────────────────────────────────────────────────────────────

    interface SignalingCallback {
        /** Called when a local SDP (offer or answer) is ready to be sent via WebSocket. */
        fun onLocalSdpReady(type: String, sdp: String)
        /** Called for each ICE candidate that should be sent to the remote peer. */
        fun onIceCandidateReady(sdpMid: String, sdpMLineIndex: Int, candidate: String)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Call state enum
    // ─────────────────────────────────────────────────────────────────────────

    enum class CallState { IDLE, CALLING, RINGING, CONNECTED, ENDED, ICE_FAILED }

    // ─────────────────────────────────────────────────────────────────────────
    // ICE / TURN configuration
    // ─────────────────────────────────────────────────────────────────────────

    /** ICE-Server-Liste mit serverseitig generierten TURN-Credentials. */
    private val iceServers: List<PeerConnection.IceServer> get() {
        val user = turnUsername
        val pass = turnPassword
        return buildList {
            // Eigener STUN (immer verfügbar, keine Credentials nötig)
            add(PeerConnection.IceServer.builder("stun:letheapp.de:3478").createIceServer())
            // TURN-Server nur wenn Credentials vorhanden
            if (user.isNotEmpty() && pass.isNotEmpty()) {
                add(PeerConnection.IceServer.builder("turn:letheapp.de:3478")
                    .setUsername(user).setPassword(pass).createIceServer())
                add(PeerConnection.IceServer.builder("turn:letheapp.de:3478?transport=tcp")
                    .setUsername(user).setPassword(pass).createIceServer())
                add(PeerConnection.IceServer.builder("turns:letheapp.de:5349")
                    .setUsername(user).setPassword(pass).createIceServer())
            }
            // Google STUN als Fallback
            add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
            add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal fields
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gemeinsamer EGL-Kontext für ALLE Clients – wird an die SurfaceViewRenderer der UI übergeben.
     * Stammt aus [WebRtcCore], damit Primär- und Sekundärclients denselben OpenGL-Kontext teilen
     * (verhindert das Einfrieren des Bildes beim Hinzufügen weiterer Teilnehmer).
     */
    val eglBase: EglBase = WebRtcCore.eglBase

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var videoCapturer: VideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null

    private var usingFrontCamera = true
    private var isCapturing = false

    /** Kapselt den CapturerObserver und verarbeitet Frames für den Hintergrundunschärfe-Effekt. */
    private var blurObserver: BackgroundBlurCapturerObserver? = null

    // ICE-Kandidaten die vor setRemoteDescription ankommen → puffern
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var remoteDescSet = false

    // Schutz vor doppeltem dispose() (z.B. durch Race zwischen cleanupCall und callState-Collector)
    @Volatile private var isDisposed = false

    // Renegotiation-Serialisierung: verhindert gleichzeitige Offers (SDP-Glare) wenn beim
    // Gruppenbeitritt mehrere weitergeleitete Tracks kurz nacheinander hinzugefügt werden.
    @Volatile private var isNegotiating = false
    private var pendingRenegotiation = false

    // true sobald die Verbindung mindestens einmal CONNECTED war – steuert die FAILED-Toleranz.
    @Volatile private var wasConnected = false

    // ─────────────────────────────────────────────────────────────────────────
    // Gruppen-Weiterleitung (Host-Relay / Software-SFU)
    // Streams von anderen Teilnehmern werden via VideoSink → VideoSource weitergeleitet.
    // ─────────────────────────────────────────────────────────────────────────

    /** Interne Daten eines weitergeleiteten Streams (Video-Proxy + Audio-Referenz). */
    private data class ForwardedStream(
        val fromId: String,
        /** Ursprünglicher Remote-VideoTrack – nur für removeSink() beim Aufräumen. */
        val sourceVideoTrack: VideoTrack?,
        /** Lokal erzeugter VideoTrack (aus forwardVideoSource) – muss disposed werden. */
        val localVideoTrack: VideoTrack?,
        /** Sink, der am sourceVideoTrack hängt und Frames in forwardVideoSource injiziert. */
        val videoSink: VideoSink?,
        /** Lokale VideoSource, die vom Sink gespeist wird – muss disposed werden. */
        val videoSource: VideoSource?,
        /** Remote-AudioTrack direkt referenziert (kein dispose – gehört dem anderen Client). */
        val audioTrack: AudioTrack?
    )

    /** Bereits aktive weitergeleitete Streams dieses Clients. */
    private val forwardedStreams = mutableListOf<ForwardedStream>()

    /** Puffer für Weiterleitungs-Tracks, die vor createPeerConnection() ankommen. */
    private val pendingForwardedTracks = mutableListOf<Triple<VideoTrack?, AudioTrack?, String>>()

    // 15-Sekunden-Toleranz bei ICE DISCONNECTED bevor der Anruf als gescheitert gilt.
    // Mobile Netzwerke (WLAN-Handoff, kurzer Signalverlust) können 5-10s brauchen um ICE zu re-etablieren.
    private val mainHandler = Handler(Looper.getMainLooper())
    private val iceDisconnectRunnable = Runnable {
        Timber.tag(TAG).w("ICE DISCONNECTED Timeout (15s) – Verbindung gilt als abgebrochen")
        _callState.value = CallState.ICE_FAILED
    }
    // Toleranz bei ICE FAILED NACH erfolgreichem Verbindungsaufbau: Eine Renegotiation
    // (z.B. Gruppenbeitritt) kann ICE kurzzeitig auf FAILED setzen und sich dann erholen.
    // Erst wenn nach 8s kein Reconnect erfolgt, gilt der Anruf endgültig als gescheitert.
    private val iceFailedRunnable = Runnable {
        Timber.tag(TAG).w("ICE FAILED Toleranz (8s) abgelaufen – Verbindung gilt als gescheitert")
        _callState.value = CallState.ICE_FAILED
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public StateFlows consumed by the UI
    // ─────────────────────────────────────────────────────────────────────────

    /** Gibt den aktuellen lokalen Video-Track zurück (für Sharing mit Sekundärclients). */
    val currentLocalVideoTrack: VideoTrack? get() = localVideoTrack

    /** Gibt den aktuellen lokalen Audio-Track zurück (für Sharing mit Sekundärclients). */
    val currentLocalAudioTrack: AudioTrack? get() = localAudioTrack

    /** Remote Video-Track des aktuellen Partners (für Weiterleitung an andere Gruppen-Teilnehmer). */
    val currentRemoteVideoTrack: VideoTrack? get() = _remoteVideoTrack.value

    /** Remote Audio-Track des aktuellen Partners (für Weiterleitung an andere Gruppen-Teilnehmer). */
    val currentRemoteAudioTrack: AudioTrack? get() = _remoteAudioTrack.value

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrackFlow: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrackFlow: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _remoteAudioTrack = MutableStateFlow<AudioTrack?>(null)
    val remoteAudioTrackFlow: StateFlow<AudioTrack?> = _remoteAudioTrack.asStateFlow()

    /** Weitergeleitete Video-Tracks von anderen Gruppen-Teilnehmern (userId → VideoTrack). Nur auf Callee-Seite befüllt. */
    private val _forwardedVideoTracks = MutableStateFlow<Map<String, VideoTrack>>(emptyMap())
    val forwardedVideoTracksFlow: StateFlow<Map<String, VideoTrack>> = _forwardedVideoTracks.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _isScreenSharing = MutableStateFlow(false)
    val isScreenSharing: StateFlow<Boolean> = _isScreenSharing.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    /** Aktiver Anruf-Recorder (null wenn nicht aufgezeichnet wird). */
    private var callRecorder: CallRecorder? = null

    private val _isSpeakerphoneOn = MutableStateFlow(callType == "VIDEO")
    val isSpeakerphoneOn: StateFlow<Boolean> = _isSpeakerphoneOn.asStateFlow()

    private val _isUsingFrontCamera = MutableStateFlow(true)
    val isUsingFrontCamera: StateFlow<Boolean> = _isUsingFrontCamera.asStateFlow()

    private val _virtualBackgroundMode = MutableStateFlow(VirtualBackgroundMode.NONE)
    val virtualBackgroundMode: StateFlow<VirtualBackgroundMode> = _virtualBackgroundMode.asStateFlow()
    /** Gewähltes Hintergrundbild (für [VirtualBackgroundMode.IMAGE]); überlebt Kamera-Wechsel. */
    private var virtualBackgroundImage: android.graphics.Bitmap? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Initialization
    // ─────────────────────────────────────────────────────────────────────────

    /** Must be called once before using any other method. Runs synchronously. */
    fun initialize() {
        // PeerConnectionFactory.initialize() wurde bereits beim App-Start in SecureChatApplication aufgerufen.
        // 1. Gemeinsame PeerConnectionFactory aus WebRtcCore nutzen (NICHT pro Client neu bauen).
        //    Dadurch stammen alle Tracks aller Clients aus EINER Factory → keine Cross-Factory-
        //    Korruption beim Teilen von Kamera/Mikrofon mit Sekundärclients im Gruppenanruf.
        factory = WebRtcCore.factory

        if (isSecondary) {
            // Sekundärclient (Gruppenanruf): geteilte Tracks übernehmen, KEIN eigenes Capture,
            // KEIN AudioManager-Eingriff (wäre doppelt und würde Haupt-Client stören).
            localVideoTrack = sharedLocalVideoTrack
            localAudioTrack = sharedLocalAudioTrack
            Timber.tag(TAG).d("WebRtcClient (secondary) initialized for partner=$partnerId, sharing tracks")
            return
        }

        // 2. Local media tracks (nur Primärclient)
        createLocalTracks()

        // 3. Audio routing: Videoanruf → Lautsprecher; Sprachanruf → Hörer (Earpiece)
        //    Ausnahme: Bluetooth-Headset bereits verbunden → BT nutzen statt Lautsprecher.
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        // Ist ein Bluetooth-Headset (HFP/SCO oder BLE) verfügbar? Nur wenn Nutzer BT erlaubt hat.
        val btScoConnected = bluetoothHeadsetEnabled && if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices.any { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoAvailableOffCall || audioManager.isBluetoothA2dpOn
        }

        // Vom Nutzer gewählter Ausgang bestimmt das Routing.
        // SYSTEM = automatisch (BT falls verbunden, sonst Lautsprecher bei Video / Hörer bei Sprache).
        // Ausnahme: Android Auto verbunden + Kanal SYSTEM → kein manuelles BT-Erzwingen (siehe
        // androidAutoConnected-Doku oben), das System übernimmt das Routing über Android Auto.
        val useBluetooth = audioOutputChannel == "BLUETOOTH" ||
                (audioOutputChannel == "SYSTEM" && btScoConnected && !androidAutoConnected)
        val useSpeaker = audioOutputChannel == "SPEAKER" ||
                (audioOutputChannel == "SYSTEM" && !btScoConnected && !androidAutoConnected && callType == "VIDEO")

        if (androidAutoConnected && audioOutputChannel == "SYSTEM") {
            Timber.tag(TAG).d("Audio → Android Auto (System-Routing, kein manueller Eingriff)")
        } else if (useBluetooth && btScoConnected) {
            // Auf Bluetooth-Headset routen, kein Lautsprecher
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val btDevice = audioManager.availableCommunicationDevices.firstOrNull { device ->
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                if (btDevice != null) audioManager.setCommunicationDevice(btDevice)
            } else {
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
            }
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            _isSpeakerphoneOn.value = false
            Timber.tag(TAG).d("Audio → Bluetooth (Kanal=$audioOutputChannel)")
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = useSpeaker
            _isSpeakerphoneOn.value = useSpeaker
            Timber.tag(TAG).d("Audio → ${if (useSpeaker) "Lautsprecher" else "Hörer"} (Kanal=$audioOutputChannel)")
        }

        Timber.tag(TAG).d("WebRtcClient initialized for partner=$partnerId callType=$callType quality=$audioQuality")
    }

    private fun createLocalTracks() {
        val f = factory ?: return

        // Kamera nur für Videoanrufe
        if (callType == "VIDEO") {
            videoCapturer = buildCameraCapturer()
            videoSource = f.createVideoSource(videoCapturer!!.isScreencast)
            surfaceHelper = SurfaceTextureHelper.create("VideoCaptureThread", eglBase.eglBaseContext)

            // BlurObserver zwischen Capturer und VideoSource schalten
            val blur = BackgroundBlurCapturerObserver(videoSource!!.capturerObserver, segmentationProvider)
            blurObserver = blur
            videoCapturer!!.initialize(surfaceHelper, context, blur)
            videoCapturer!!.startCapture(1280, 720, 30)  // max 720p @ 30 fps
            isCapturing = true

            localVideoTrack = f.createVideoTrack("LV0", videoSource).also {
                it.setEnabled(true)
                _localVideoTrack.value = it
            }
        }

        // Microphone – verbesserte Qualität: Hochpassfilter + Level-Control + erhöhte Bitrate.
        // audioQuality steuert die zusätzlichen (rechenintensiveren) Verbesserungen:
        //   HIGH/AUTO = alle Verbesserungen aktiv, LOW = nur Basis-Verarbeitung (spart CPU/Daten).
        val enhancedAudio = audioQuality != "LOW"
        val audioConstraints = MediaConstraints().apply {
            mandatory += MediaConstraints.KeyValuePair("echoCancellation",  "true")
            mandatory += MediaConstraints.KeyValuePair("noiseSuppression",  "true")
            mandatory += MediaConstraints.KeyValuePair("autoGainControl",   "true")
            if (enhancedAudio) {
                // Hochpassfilter entfernt Tieffrequenz-Rumpeln (Hintergrundgeräusche unter ~200 Hz)
                optional  += MediaConstraints.KeyValuePair("googHighpassFilter","true")
                // Verbesserte Rauschunterdrückung (Algorithmus der zweiten Generation)
                optional  += MediaConstraints.KeyValuePair("googNoiseSuppression2", "true")
                // Stabilisiert Lautstärke-Schwankungen (verhindert plötzlich laute/leise Passagen)
                optional  += MediaConstraints.KeyValuePair("googExperimentalAGC", "true")
            }
        }
        val audioSource = f.createAudioSource(audioConstraints)
        localAudioTrack = f.createAudioTrack("LA0", audioSource).also { it.setEnabled(true) }

        Timber.tag(TAG).d("Local tracks created")
    }

    private fun buildCameraCapturer(): CameraVideoCapturer {
        val enumerator = Camera2Enumerator(context)
        // Prefer front camera
        enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?.let { return enumerator.createCapturer(it, null)!! }
        // Fallback: any available camera
        enumerator.deviceNames.firstOrNull()
            ?.let { return enumerator.createCapturer(it, null)!! }
        throw IllegalStateException("No camera found on device")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PeerConnection setup
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates the PeerConnection.
     * @param isOffer true = caller (creates SDP offer), false = callee (waits for offer).
     */
    fun createPeerConnection(isOffer: Boolean) {
        val f = factory ?: run {
            Timber.tag(TAG).e("Factory null – call initialize() first")
            return
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            // MAXBUNDLE bündelt Audio+Video in ein einziges ICE-Komponent → ~4x weniger Candidate-Pairs
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            // REQUIRE multiplext RTP und RTCP auf demselben Port → nochmals weniger Ports/Pairs
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            // Kandidaten vorab sammeln bevor Offer/Answer fertig ist → schnellerer Verbindungsaufbau
            iceCandidatePoolSize = 4
        }

        peerConnection = f.createPeerConnection(rtcConfig, buildPcObserver()) ?: run {
            Timber.tag(TAG).e("createPeerConnection() returned null!")
            return
        }

        // Add local tracks using Unified Plan addTrack (Video nur bei Videoanrufen)
        if (callType == "VIDEO") {
            localVideoTrack?.let { peerConnection!!.addTrack(it, listOf("stream_local")) }
        }
        localAudioTrack?.let { peerConnection!!.addTrack(it, listOf("stream_local")) }

        // Gepufferte Weiterleitungs-Tracks aus anderen Gruppen-Teilnehmern einfügen
        // (wurden via addForwardedTracks() vor diesem Aufruf gesammelt)
        val pendingCopy = pendingForwardedTracks.toList()
        pendingForwardedTracks.clear()
        pendingCopy.forEach { (video, audio, fromId) ->
            doAddForwardedTracks(peerConnection!!, f, video, audio, fromId)
        }

        if (isOffer) {
            _callState.value = CallState.CALLING
            createOffer()
        } else {
            _callState.value = CallState.RINGING
        }
    }

    private fun buildPcObserver() = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {
            Timber.tag(TAG).d("Signaling → $state")
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            Timber.tag(TAG).d("ICE connection → $state")
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> {
                    // Reconnect nach DISCONNECTED/FAILED → beide Timer abbrechen
                    mainHandler.removeCallbacks(iceDisconnectRunnable)
                    mainHandler.removeCallbacks(iceFailedRunnable)
                    wasConnected = true
                    _callState.value = CallState.CONNECTED
                    // Audiobitrate nach erfolgreichem Verbindungsaufbau erhöhen
                    if (state == PeerConnection.IceConnectionState.CONNECTED) setHighQualityAudio()
                }

                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    // Vorübergehend – 15 Sekunden Toleranz bevor wir den Anruf beenden.
                    // Mobile Netzwerke brauchen bei Signalschwankungen oft 5–10s für ICE-Reconnect.
                    mainHandler.postDelayed(iceDisconnectRunnable, 15_000)
                    Timber.tag(TAG).d("ICE DISCONNECTED – warte 15s auf Reconnect")
                }

                PeerConnection.IceConnectionState.FAILED -> {
                    mainHandler.removeCallbacks(iceDisconnectRunnable)
                    if (wasConnected) {
                        // Kurzzeitiges FAILED während einer Renegotiation (Gruppenbeitritt)
                        // tolerieren – erst nach 8s ohne Reconnect endgültig abbrechen.
                        mainHandler.postDelayed(iceFailedRunnable, 8_000)
                        Timber.tag(TAG).d("ICE FAILED nach CONNECTED – 8s Toleranz (evtl. Renegotiation)")
                    } else {
                        // Erster Verbindungsaufbau gescheitert → sofort abbrechen.
                        _callState.value = CallState.ICE_FAILED
                    }
                }

                PeerConnection.IceConnectionState.CLOSED -> {
                    mainHandler.removeCallbacks(iceDisconnectRunnable)
                    mainHandler.removeCallbacks(iceFailedRunnable)
                    _callState.value = CallState.ICE_FAILED
                }

                else -> {}
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {}

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            Timber.tag(TAG).d("ICE gathering → $state")
        }

        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate ?: return
            Timber.tag(TAG).d("ICE candidate produced: ${candidate.sdpMid}")
            signalingCallback.onIceCandidateReady(
                candidate.sdpMid,
                candidate.sdpMLineIndex,
                candidate.sdp
            )
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

        // Unified Plan: remote tracks arrive via onTrack
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            val track = receiver?.track() ?: return
            // Weitergeleitete Tracks des Hosts haben Stream-IDs "stream_fwd_<userId>"
            val streamId = streams?.firstOrNull()?.id
            val fwdParticipantId = streamId
                ?.takeIf { it.startsWith("stream_fwd_") }
                ?.removePrefix("stream_fwd_")
            when (track.kind()) {
                MediaStreamTrack.VIDEO_TRACK_KIND -> {
                    if (fwdParticipantId != null) {
                        // Forwarded Track von einem anderen Gruppen-Teilnehmer
                        Timber.tag(TAG).d("Forwarded video track from participant $fwdParticipantId (via host $partnerId)")
                        val updated = _forwardedVideoTracks.value.toMutableMap()
                        updated[fwdParticipantId] = track as VideoTrack
                        _forwardedVideoTracks.value = updated
                    } else {
                        Timber.tag(TAG).d("Remote video track received from $partnerId")
                        _remoteVideoTrack.value = track as VideoTrack
                    }
                }
                MediaStreamTrack.AUDIO_TRACK_KIND -> {
                    Timber.tag(TAG).d("Remote audio track received from $partnerId")
                    _remoteAudioTrack.value = track as AudioTrack
                }
            }
        }

        // Plan B fallback (older servers) – nur Remote-Tracks setzen, nie den eigenen LocalTrack
        override fun onAddStream(stream: MediaStream?) {
            stream?.videoTracks?.firstOrNull()?.let { track ->
                if (track !== localVideoTrack) {
                    Timber.tag(TAG).d("Remote video track (addStream)")
                    _remoteVideoTrack.value = track
                } else {
                    Timber.tag(TAG).d("onAddStream: LocalTrack ignoriert (verhindert eigenes Bild im Remote-Bereich)")
                }
            }
        }

        override fun onRemoveStream(stream: MediaStream?) {
            _remoteVideoTrack.value = null
        }

        override fun onDataChannel(p0: org.webrtc.DataChannel?) {}

        override fun onRenegotiationNeeded() {
            // Nur bei aktiver Verbindung renegotiieren (nicht beim initialen Aufbau).
            // Wird getriggert wenn doAddForwardedTracks() neue Tracks nach CONNECTED einfügt.
            if (_callState.value != CallState.CONNECTED) return
            if (isNegotiating) {
                // Läuft bereits eine Renegotiation → nur merken und nach Abschluss nachholen.
                // Verhindert gleichzeitige Offers (SDP-Glare) beim Gruppenbeitritt.
                pendingRenegotiation = true
                Timber.tag(TAG).d("onRenegotiationNeeded → Renegotiation läuft bereits, gequeued")
                return
            }
            isNegotiating = true
            Timber.tag(TAG).d("onRenegotiationNeeded → sende neues Offer an $partnerId")
            createOffer()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SDP offer / answer
    // ─────────────────────────────────────────────────────────────────────────

    private fun createOffer() {
        peerConnection?.createOffer(sdpObserver { sdp ->
            peerConnection?.setLocalDescription(noopSdpObserver {
                signalingCallback.onLocalSdpReady(sdp.type.canonicalForm(), sdp.description)
                Timber.tag(TAG).d("SDP offer created and set locally")
            }, sdp)
        }, offerAnswerConstraints())
    }

    /** Called by ViewModel after receiving the remote offer SDP. */
    fun setRemoteSdp(type: String, sdp: String) {
        val sdpType = when (type.lowercase()) {
            "offer"   -> SessionDescription.Type.OFFER
            "answer"  -> SessionDescription.Type.ANSWER
            else      -> SessionDescription.Type.OFFER
        }
        peerConnection?.setRemoteDescription(noopSdpObserver {
            Timber.tag(TAG).d("Remote SDP set ($type)")
            remoteDescSet = true
            flushPendingCandidates()   // gepufferte Kandidaten jetzt einfügen
            if (sdpType == SessionDescription.Type.OFFER) {
                createAnswer()
            } else {
                // Answer empfangen → unsere (Re-)Negotiation ist abgeschlossen.
                onNegotiationComplete()
            }
        }, SessionDescription(sdpType, sdp))
    }

    /**
     * Schließt eine laufende Renegotiation ab und holt eine zwischenzeitlich angeforderte
     * Renegotiation nach (z.B. wenn während des Offers ein weiterer Teilnehmer beitrat).
     */
    private fun onNegotiationComplete() {
        isNegotiating = false
        if (pendingRenegotiation && _callState.value == CallState.CONNECTED) {
            pendingRenegotiation = false
            isNegotiating = true
            Timber.tag(TAG).d("Nachgeholte Renegotiation → neues Offer an $partnerId")
            createOffer()
        } else {
            pendingRenegotiation = false
        }
    }

    private fun createAnswer() {
        peerConnection?.createAnswer(sdpObserver { sdp ->
            peerConnection?.setLocalDescription(noopSdpObserver {
                signalingCallback.onLocalSdpReady(sdp.type.canonicalForm(), sdp.description)
                Timber.tag(TAG).d("SDP answer created and set locally")
            }, sdp)
        }, offerAnswerConstraints())
    }

    /** Called by ViewModel for each ICE candidate received from the remote peer.
     *  Puffert den Kandidaten falls PeerConnection noch nicht bereit oder Remote-SDP noch nicht gesetzt. */
    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        val pc = peerConnection
        if (pc != null && remoteDescSet) {
            pc.addIceCandidate(iceCandidate)
            Timber.tag(TAG).d("Remote ICE candidate added immediately")
        } else {
            pendingCandidates.add(iceCandidate)
            Timber.tag(TAG).d("Remote ICE candidate queued (pc=${pc != null}, remoteDesc=$remoteDescSet)")
        }
    }

    private fun flushPendingCandidates() {
        val pc = peerConnection ?: return
        Timber.tag(TAG).d("Flushing ${pendingCandidates.size} buffered ICE candidates")
        pendingCandidates.forEach { pc.addIceCandidate(it) }
        pendingCandidates.clear()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Controls
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Leitet den Video- und Audio-Stream eines anderen Gruppen-Teilnehmers durch diesen Client weiter.
     *
     * Wird vom Host aufgerufen wenn ein neuer Teilnehmer beitritt oder ein Remote-Track ankommt:
     *  - Video: VideoSink → VideoSource-Proxy (frame-level, keine Transkodierung)
     *  - Audio: Remote-AudioTrack wird direkt als Sender hinzugefügt
     *
     * Wenn [peerConnection] noch nicht existiert (vor createPeerConnection), werden die Tracks
     * gepuffert und beim nächsten createPeerConnection()-Aufruf eingefügt.
     */
    fun addForwardedTracks(videoTrack: VideoTrack?, audioTrack: AudioTrack?, fromId: String) {
        if (forwardedStreams.any { it.fromId == fromId }) return
        if (videoTrack == null && audioTrack == null) return
        val pc = peerConnection
        val f  = factory
        if (pc == null || f == null) {
            // Noch nicht bereit – puffern bis createPeerConnection aufgerufen wird
            if (pendingForwardedTracks.none { it.third == fromId }) {
                pendingForwardedTracks.add(Triple(videoTrack, audioTrack, fromId))
            }
            return
        }
        doAddForwardedTracks(pc, f, videoTrack, audioTrack, fromId)
    }

    private fun doAddForwardedTracks(
        pc: PeerConnection,
        f: PeerConnectionFactory,
        videoTrack: VideoTrack?,
        audioTrack: AudioTrack?,
        fromId: String
    ) {
        var localFwdTrack: VideoTrack? = null
        var videoSink: VideoSink?       = null
        var videoSource: VideoSource?   = null

        if (videoTrack != null) {
            // VideoSource-Proxy: Frames vom empfangenen Remote-Track → neue lokale VideoSource
            val fwdSource = f.createVideoSource(false)
            val fwdSink = VideoSink { frame ->
                frame.retain()
                fwdSource.capturerObserver.onFrameCaptured(frame)
                frame.release()
            }
            videoTrack.addSink(fwdSink)
            localFwdTrack = f.createVideoTrack("FV_$fromId", fwdSource).also { it.setEnabled(true) }
            pc.addTrack(localFwdTrack, listOf("stream_fwd_$fromId"))
            videoSink   = fwdSink
            videoSource = fwdSource
            Timber.tag(TAG).d("Weiterleitung Video von $fromId eingerichtet → $partnerId")
        }

        if (audioTrack != null) {
            // Remote-AudioTrack direkt als Sender hinzufügen (kein separates AudioSource nötig)
            pc.addTrack(audioTrack, listOf("stream_fwd_$fromId"))
            Timber.tag(TAG).d("Weiterleitung Audio von $fromId eingerichtet → $partnerId")
        }

        forwardedStreams.add(ForwardedStream(fromId, videoTrack, localFwdTrack, videoSink, videoSource, audioTrack))
    }

    /**
     * Setzt den virtuellen Hintergrund im eigenen Videostream (Kein/Unschärfe/Bild).
     * [image] wird nur für [VirtualBackgroundMode.IMAGE] verwendet und überlebt Kamera-Wechsel.
     */
    fun setVirtualBackground(newMode: VirtualBackgroundMode, image: android.graphics.Bitmap? = null) {
        if (newMode == VirtualBackgroundMode.IMAGE) {
            virtualBackgroundImage = image
        }
        val blur = blurObserver
        if (blur != null) {
            blur.backgroundImage.set(if (newMode == VirtualBackgroundMode.IMAGE) virtualBackgroundImage else null)
            blur.mode.set(newMode)
        }
        _virtualBackgroundMode.value = newMode
        Timber.tag(TAG).d("Virtual background → $newMode")
    }

    /**
     * Erhöht die Opus-Audiobitrate auf 128 kbps für bessere Sprachqualität.
     * Wird nach erfolgreichem Verbindungsaufbau aufgerufen.
     */
    private fun setHighQualityAudio() {
        val audioSender = peerConnection?.senders?.find { it.track()?.kind() == "audio" } ?: return
        val params = audioSender.parameters
        if (params.encodings.isNotEmpty()) {
            params.encodings[0].maxBitrateBps = 128_000
            audioSender.setParameters(params)
            Timber.tag(TAG).d("Audio-Bitrate auf 128 kbps gesetzt")
        }
    }

    fun toggleMute() {
        val newMuted = !_isMuted.value
        _isMuted.value = newMuted
        localAudioTrack?.setEnabled(!newMuted)
        Timber.tag(TAG).d("Muted → $newMuted")
    }

    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                usingFrontCamera = isFrontCamera
                _isUsingFrontCamera.value = isFrontCamera
                Timber.tag(TAG).d("Camera switched; front=$usingFrontCamera")
            }
            override fun onCameraSwitchError(errorDescription: String?) {
                Timber.tag(TAG).w("Camera switch failed: $errorDescription")
            }
        })
    }

    /** Startet Bildschirmfreigabe – ersetzt Kamera-Track durch Screen-Capture. */
    fun startScreenShare(data: Intent) {
        val f = factory ?: return

        // 1. Kamera-Capturer sofort stoppen und freigeben.
        //    stopCapture() ist bei Camera2 asynchron – wir dürfen den alten
        //    capturerObserver/SurfaceHelper danach NICHT mehr wiederverwenden.
        try { videoCapturer?.stopCapture() } catch (_: Exception) {}
        videoCapturer?.dispose()
        videoCapturer = null
        isCapturing = false

        // 2. Alte Objekte sichern – werden erst NACH dem Track-Switch freigegeben,
        //    damit kein Frame-Lücke im laufenden Stream entsteht.
        val oldTrack  = localVideoTrack
        val oldSource = videoSource
        val oldHelper = surfaceHelper

        // 3. Neue VideoSource mit isScreencast=true (Pflicht – sonst crasht der
        //    Encoder intern, weil er für Kamera-Charakteristik konfiguriert wurde).
        val screenSource = f.createVideoSource(/* isScreencast = */ true)
        videoSource = screenSource

        // 4. Eigener SurfaceTextureHelper auf eigenem Thread, damit kein Konflikt
        //    mit dem noch auslaufenden Camera2-Hintergrundthread entsteht.
        val screenHelper = SurfaceTextureHelper.create("ScreenCaptureThread", eglBase.eglBaseContext)
        surfaceHelper = screenHelper

        // 5. Neuer VideoTrack aus der Screen-Source.
        val screenTrack = f.createVideoTrack("LV0", screenSource).also { it.setEnabled(true) }
        localVideoTrack = screenTrack

        // 6. Track im laufenden PeerConnection-Sender ersetzen (kein Re-Negotiation nötig).
        peerConnection?.senders
            ?.find { it.track()?.kind() == "video" }
            ?.setTrack(screenTrack, false)
        _localVideoTrack.value = screenTrack

        // 7. Alte Ressourcen freigeben (nach dem Switch – keine Lücke im Stream).
        oldTrack?.dispose()
        oldSource?.dispose()
        oldHelper?.dispose()

        // 8. ScreenCapturer mit dem neuen Helper/Source initialisieren und starten.
        //    Bei Screen-Sharing keinen virtuellen Hintergrund anwenden (Observer deaktivieren).
        blurObserver?.mode?.set(VirtualBackgroundMode.NONE)
        _virtualBackgroundMode.value = VirtualBackgroundMode.NONE
        val screenCapturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {})
        videoCapturer = screenCapturer
        screenCapturer.initialize(screenHelper, context, screenSource.capturerObserver)
        screenCapturer.startCapture(1280, 720, 30)
        isCapturing = true
        _isScreenSharing.value = true
        Timber.tag(TAG).d("Screen sharing started")
    }

    /** Stoppt Bildschirmfreigabe und stellt die Kamera wieder her. */
    fun stopScreenShare() {
        val f = factory ?: return

        // 1. Screen-Capturer stoppen und freigeben.
        try { videoCapturer?.stopCapture() } catch (_: Exception) {}
        videoCapturer?.dispose()
        videoCapturer = null
        isCapturing = false

        // 2. Alte Objekte sichern.
        val oldTrack  = localVideoTrack
        val oldSource = videoSource
        val oldHelper = surfaceHelper

        // 3. Neue VideoSource mit isScreencast=false für Kamera.
        val cameraSource = f.createVideoSource(/* isScreencast = */ false)
        videoSource = cameraSource

        // 4. Neuer SurfaceTextureHelper für Camera-Thread.
        val cameraHelper = SurfaceTextureHelper.create("VideoCaptureThread", eglBase.eglBaseContext)
        surfaceHelper = cameraHelper

        // 5. Neuer VideoTrack aus Camera-Source.
        val cameraTrack = f.createVideoTrack("LV0", cameraSource).also { it.setEnabled(true) }
        localVideoTrack = cameraTrack

        // 6. Track im PeerConnection-Sender ersetzen.
        peerConnection?.senders
            ?.find { it.track()?.kind() == "video" }
            ?.setTrack(cameraTrack, false)
        _localVideoTrack.value = cameraTrack

        // 7. Alte Ressourcen freigeben.
        oldTrack?.dispose()
        oldSource?.dispose()
        oldHelper?.dispose()

        // 8. Kamera-Capturer mit neuem Helper/Source initialisieren und starten.
        //    BlurObserver für die neue Camera-Source neu verdrahten – zuvor gewählten
        //    virtuellen Hintergrund (Modus + Bild) wiederherstellen.
        val newBlur = BackgroundBlurCapturerObserver(cameraSource.capturerObserver, segmentationProvider)
        val restoreMode = _virtualBackgroundMode.value
        newBlur.backgroundImage.set(if (restoreMode == VirtualBackgroundMode.IMAGE) virtualBackgroundImage else null)
        newBlur.mode.set(restoreMode)
        blurObserver?.dispose()
        blurObserver = newBlur
        val cameraCapt = buildCameraCapturer()
        videoCapturer = cameraCapt
        cameraCapt.initialize(cameraHelper, context, newBlur)
        cameraCapt.startCapture(1280, 720, 30)
        isCapturing = true
        _isScreenSharing.value = false
        Timber.tag(TAG).d("Screen sharing stopped, camera resumed")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /** Activity.onPause → pause video, keep audio running. */
    fun onPause() {
        localVideoTrack?.setEnabled(false)
        videoCapturer?.stopCapture()
        isCapturing = false
        Timber.tag(TAG).d("Video paused")
    }

    /** Activity.onResume → resume video. Only starts capture if it was stopped via onPause(). */
    fun onResume() {
        if (!isCapturing) {
            videoCapturer?.startCapture(1280, 720, 30)
            isCapturing = true
        }
        localVideoTrack?.setEnabled(true)
        Timber.tag(TAG).d("Video resumed (wasCapturing=${!isCapturing})")
    }

    /** Tear down the PeerConnection but keep local tracks alive for display. */
    fun endCall() {
        _callState.value = CallState.ENDED
        peerConnection?.dispose()
        peerConnection = null
        _remoteVideoTrack.value = null
        _forwardedVideoTracks.value = emptyMap()
        Timber.tag(TAG).d("PeerConnection disposed (call ended)")
    }

    /** Full cleanup – call when the call screen leaves composition. */
    fun dispose() {
        if (isDisposed) {
            Timber.tag(TAG).w("dispose() bereits aufgerufen – ignoriert (partner=$partnerId)")
            return
        }
        isDisposed = true
        mainHandler.removeCallbacks(iceDisconnectRunnable)
        mainHandler.removeCallbacks(iceFailedRunnable)
        // Laufende Aufzeichnung sauber finalisieren bevor die Tracks freigegeben werden
        try { callRecorder?.stop() } catch (_: Exception) {}
        callRecorder = null
        _isRecording.value = false
        endCall()

        if (isSecondary) {
            // Sekundärclient: geteilte Tracks gehören dem Primärclient – NICHT freigeben.
            // Factory + EglBase gehören WebRtcCore (prozessweit) → NICHT disposen/releasen.
            // PeerConnection wurde bereits in endCall() oben freigegeben.
            localVideoTrack = null
            localAudioTrack = null
            factory = null
            Timber.tag(TAG).d("WebRtcClient (secondary) disposed for partner=$partnerId")
            return
        }

        // Weiterleitungs-Ressourcen aufräumen (VideoSink entfernen, lokale Tracks disposen)
        forwardedStreams.forEach { stream ->
            stream.videoSink?.let { sink -> stream.sourceVideoTrack?.removeSink(sink) }
            stream.localVideoTrack?.dispose()
            stream.videoSource?.dispose()
            // stream.audioTrack gehört dem anderen Client → kein dispose
        }
        forwardedStreams.clear()
        pendingForwardedTracks.clear()

        blurObserver?.dispose()
        blurObserver = null
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        surfaceHelper?.dispose()
        localVideoTrack?.dispose()
        videoSource?.dispose()
        localAudioTrack?.dispose()
        // Factory + EglBase gehören WebRtcCore (prozessweit) → NICHT disposen/releasen,
        // sonst friert/crasht ein gleichzeitig laufender Client (Gruppenanruf).
        factory = null

        // Restore normal audio mode
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            if (audioManager.isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
            }
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false

        Timber.tag(TAG).d("WebRtcClient fully disposed")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Setzt die Video-Qualität live über RtpParameters.
     * HD: scaleResolutionDownBy=1.0, maxBitrate=3 Mbit/s
     * SD: scaleResolutionDownBy=2.0, maxBitrate=350 kbit/s
     * minBitrate bleibt immer 200 kbit/s.
     */
    fun setVideoQuality(isHighDefinition: Boolean) {
        val videoSender = peerConnection?.senders?.find { it.track()?.kind() == "video" } ?: run {
            Timber.tag(TAG).w("setVideoQuality: kein Video-Sender gefunden"); return
        }
        val params = videoSender.parameters
        if (params.encodings.isEmpty()) {
            Timber.tag(TAG).w("setVideoQuality: keine Encodings in RtpParameters"); return
        }
        val encoding = params.encodings[0]
        encoding.minBitrateBps = 200_000
        if (isHighDefinition) {
            encoding.scaleResolutionDownBy = 1.0
            encoding.maxBitrateBps = 3_000_000
            Timber.tag(TAG).d("Video-Qualität: HD (720p, 3 Mbit/s)")
        } else {
            encoding.scaleResolutionDownBy = 2.0
            encoding.maxBitrateBps = 350_000
            Timber.tag(TAG).d("Video-Qualität: SD (360p, 350 kbit/s)")
        }
        val result = videoSender.setParameters(params)
        Timber.tag(TAG).d("setVideoQuality setParameters result: $result")
    }

    /** Schaltet zwischen Hörer (Earpiece) und Freisprecher um – nur sinnvoll für Sprachanrufe. */
    fun toggleSpeakerphone() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val newValue = !_isSpeakerphoneOn.value
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = newValue
        _isSpeakerphoneOn.value = newValue
        Timber.tag(TAG).d("Speakerphone → $newValue")
    }

    /**
     * Startet die clientseitige Aufzeichnung des laufenden Anrufs in [outputFile] (MP4).
     * Zeichnet den Remote-Videostream + gemischtes Audio (eigenes Mikrofon + Gegenseite) auf.
     * Gibt true zurück wenn die Aufnahme gestartet wurde.
     */
    fun startRecording(outputFile: java.io.File): Boolean {
        if (callRecorder != null) {
            Timber.tag(TAG).w("Aufnahme läuft bereits")
            return false
        }
        val recorder = CallRecorder(
            outputFile = outputFile,
            sharedEglContext = eglBase.eglBaseContext,
            localAudioTrack = localAudioTrack,
            remoteAudioTrack = _remoteAudioTrack.value,
            remoteVideoTrack = if (callType == "VOICE") null else _remoteVideoTrack.value
        )
        return if (recorder.start()) {
            callRecorder = recorder
            _isRecording.value = true
            Timber.tag(TAG).i("Anrufaufzeichnung gestartet")
            true
        } else {
            Timber.tag(TAG).e("Anrufaufzeichnung konnte nicht gestartet werden")
            false
        }
    }

    /** Stoppt die Aufzeichnung und gibt die fertige Datei zurück (oder null). */
    fun stopRecording(): java.io.File? {
        val recorder = callRecorder ?: return null
        callRecorder = null
        _isRecording.value = false
        return recorder.stop()
    }

    private fun offerAnswerConstraints() = MediaConstraints().apply {
        mandatory += MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
        // Bei Sprachanrufen Video explizit deaktivieren → kompaktere SDP, kein Codec-Aushandlung
        mandatory += MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (callType == "VOICE") "false" else "true")
    }

    /** Creates an SdpObserver that calls [onSuccess] only on createSuccess. */
    private inline fun sdpObserver(crossinline onSuccess: (SessionDescription) -> Unit) =
        object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let { onSuccess(it) }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Timber.tag(TAG).e("SDP create failure: $error")
            }
            override fun onSetFailure(error: String?) {
                Timber.tag(TAG).e("SDP set failure: $error")
            }
        }

    /** Creates an SdpObserver that calls [onSetSuccess] on setSuccess. */
    private inline fun noopSdpObserver(crossinline onSetSuccess: () -> Unit) =
        object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() { onSetSuccess() }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(error: String?) {
                Timber.tag(TAG).e("SDP set failure: $error")
            }
        }

    companion object {
        private const val TAG = "LETHE_VIDEO"
    }
}
