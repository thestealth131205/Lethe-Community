package com.securechat.app.data.webrtc

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * Manages WebRTC DataChannel connections for internet P2P chat.
 *
 * - No media tracks (audio/video) — text messages via DataChannel only.
 * - STUN/TURN via letheapp.de Coturn server (same as WebRtcClient for calls).
 * - Signaling relayed through the existing Lethe WebSocket server using the new
 *   message types:  webrtc_dc_offer / webrtc_dc_answer / webrtc_dc_ice
 *   The server never sees plaintext — it only routes SDP and ICE packets.
 * - Supports multiple simultaneous peer connections (one per chat partner).
 *
 * Lifecycle:
 *   initialize() → connectToPeer() / handleRemoteOffer() → sendMessage() → closeConnection()
 *   closeAll() on ViewModel.onCleared()
 */
class WebRTCDataChannelManager(
    private val context: Context,
    private val signalingCallback: SignalingCallback
) {

    // ─────────────────────────────────────────────────────────────────────────
    // Public interfaces & types
    // ─────────────────────────────────────────────────────────────────────────

    /** Called by the manager when signaling data must be sent to the remote peer. */
    interface SignalingCallback {
        /** An SDP offer or answer is ready — send it via WebSocket to [peerId]. */
        fun onLocalSdpReady(peerId: String, type: String, sdp: String)
        /** An ICE candidate is ready — send it via WebSocket to [peerId]. */
        fun onIceCandidateReady(peerId: String, sdpMid: String, sdpMLineIndex: Int, candidate: String)
    }

    enum class P2PState { IDLE, CONNECTING, CONNECTED, DISCONNECTED, FAILED }

    data class P2PMessage(
        val fromPeerId: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        /** Non-null only for binary (file) transfers; [content] is empty in that case. */
        val binaryData: ByteArray? = null
    ) {
        val isBinary: Boolean get() = binaryData != null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ICE / TURN configuration — mirrors WebRtcClient
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildIceServers(
        turnUsername: String = "",
        turnPassword: String = ""
    ): List<PeerConnection.IceServer> = buildList {
        // Own STUN (no credentials required)
        add(PeerConnection.IceServer.builder("stun:letheapp.de:3478").createIceServer())
        // Own TURN — only when credentials are available
        if (turnUsername.isNotEmpty() && turnPassword.isNotEmpty()) {
            add(
                PeerConnection.IceServer.builder("turn:letheapp.de:3478")
                    .setUsername(turnUsername).setPassword(turnPassword).createIceServer()
            )
            add(
                PeerConnection.IceServer.builder("turn:letheapp.de:3478?transport=tcp")
                    .setUsername(turnUsername).setPassword(turnPassword).createIceServer()
            )
            add(
                PeerConnection.IceServer.builder("turns:letheapp.de:5349")
                    .setUsername(turnUsername).setPassword(turnPassword).createIceServer()
            )
        }
        // Google STUN fallback
        add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal per-peer state
    // ─────────────────────────────────────────────────────────────────────────

    private data class PeerEntry(
        val peerId: String,
        val pc: PeerConnection,
        var dataChannel: DataChannel? = null,
        var remoteDescSet: Boolean = false,
        val pendingCandidates: MutableList<IceCandidate> = mutableListOf()
    )

    private val peers = mutableMapOf<String, PeerEntry>()
    private var factory: PeerConnectionFactory? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Public state flows
    // ─────────────────────────────────────────────────────────────────────────

    /** Map of peerId → current connection state. */
    private val _connectionStates = MutableStateFlow<Map<String, P2PState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, P2PState>> = _connectionStates.asStateFlow()

    /** Stream of messages received from any connected peer. */
    private val _incomingMessages = MutableSharedFlow<P2PMessage>(replay = 0, extraBufferCapacity = 128)
    val incomingMessages: SharedFlow<P2PMessage> = _incomingMessages.asSharedFlow()

    // ─────────────────────────────────────────────────────────────────────────
    // Initialization
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Must be called once before using any other method.
     * PeerConnectionFactory.initialize() is expected to have run at app start
     * (in SecureChatApplication).
     */
    fun initialize() {
        if (factory != null) return
        factory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options().apply { disableNetworkMonitor = true })
            .createPeerConnectionFactory()
        Timber.tag(TAG).d("WebRTCDataChannelManager initialized")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Caller side: initiate connection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Initiates a P2P DataChannel connection to [peerId] as the offerer (caller).
     * [turnUsername] / [turnPassword] come from the server TURN-credentials endpoint.
     * If the factory hasn't been initialized yet, initialize() is called implicitly.
     */
    fun connectToPeer(peerId: String, turnUsername: String = "", turnPassword: String = "") {
        if (factory == null) initialize()
        if (peers.containsKey(peerId)) {
            Timber.tag(TAG).w("connectToPeer: already have entry for $peerId — ignored")
            return
        }
        val pc = createPeerConnection(peerId, turnUsername, turnPassword) ?: return

        // Offerer creates the DataChannel; callee receives it via onDataChannel callback.
        val dcInit = DataChannel.Init().apply {
            ordered = true   // reliable, ordered delivery (SCTP default)
        }
        val dc = pc.createDataChannel(DC_LABEL, dcInit)
        dc.registerObserver(buildDataChannelObserver(peerId, dc))

        peers[peerId] = PeerEntry(peerId = peerId, pc = pc, dataChannel = dc)
        updateState(peerId, P2PState.CONNECTING)

        // Create SDP offer (no audio/video — DataChannel only)
        pc.createOffer(buildSdpCreateObserver(peerId, "offer"), MediaConstraints())
        Timber.tag(TAG).d("connectToPeer: SDP offer initiated for $peerId")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Callee side: handle incoming offer
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called by the ViewModel when a `webrtc_dc_offer` WebSocket message arrives.
     * Creates a PeerConnection, sets the remote description, and sends back an answer.
     */
    fun handleRemoteOffer(
        peerId: String,
        sdp: String,
        turnUsername: String = "",
        turnPassword: String = ""
    ) {
        if (factory == null) initialize()
        // Remove stale entry if any (e.g. reconnect attempt)
        closePeerInternal(peerId)

        val pc = createPeerConnection(peerId, turnUsername, turnPassword) ?: return
        val entry = PeerEntry(peerId = peerId, pc = pc)
        peers[peerId] = entry
        updateState(peerId, P2PState.CONNECTING)

        pc.setRemoteDescription(
            buildSdpSetObserver(peerId) {
                entry.remoteDescSet = true
                flushPendingCandidates(entry)
                // Create answer after remote description is confirmed set
                pc.createAnswer(buildSdpCreateObserver(peerId, "answer"), MediaConstraints())
            },
            SessionDescription(SessionDescription.Type.OFFER, sdp)
        )
        Timber.tag(TAG).d("handleRemoteOffer from $peerId")
    }

    /**
     * Called by the ViewModel when a `webrtc_dc_answer` WebSocket message arrives.
     */
    fun handleRemoteAnswer(peerId: String, sdp: String) {
        val entry = peers[peerId] ?: run {
            Timber.tag(TAG).w("handleRemoteAnswer: no entry for $peerId")
            return
        }
        entry.pc.setRemoteDescription(
            buildSdpSetObserver(peerId) {
                entry.remoteDescSet = true
                flushPendingCandidates(entry)
                Timber.tag(TAG).d("handleRemoteAnswer: remote description set for $peerId")
            },
            SessionDescription(SessionDescription.Type.ANSWER, sdp)
        )
    }

    /**
     * Called by the ViewModel when a `webrtc_dc_ice` WebSocket message arrives.
     * Buffers the candidate if the remote description isn't set yet.
     */
    fun addRemoteIceCandidate(peerId: String, sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val entry = peers[peerId] ?: run {
            Timber.tag(TAG).w("addRemoteIceCandidate: no entry for $peerId — ignored")
            return
        }
        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        if (entry.remoteDescSet) {
            entry.pc.addIceCandidate(iceCandidate)
            Timber.tag(TAG).d("addRemoteIceCandidate: added immediately for $peerId")
        } else {
            entry.pendingCandidates.add(iceCandidate)
            Timber.tag(TAG).d("addRemoteIceCandidate: buffered for $peerId (${entry.pendingCandidates.size} total)")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Messaging
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends [message] to [peerId] via the open DataChannel.
     * Returns true if the message was successfully sent, false otherwise.
     */
    fun sendMessage(peerId: String, message: String): Boolean {
        val dc = peers[peerId]?.dataChannel
        if (dc == null || dc.state() != DataChannel.State.OPEN) {
            Timber.tag(TAG).w("sendMessage: DataChannel not open for $peerId (state=${dc?.state()})")
            return false
        }
        val buffer = DataChannel.Buffer(
            ByteBuffer.wrap(message.toByteArray(Charsets.UTF_8)),
            false   // false = text mode (not binary)
        )
        val sent = dc.send(buffer)
        Timber.tag(TAG).d("sendMessage to $peerId: sent=$sent, len=${message.length}")
        return sent
    }

    /**
     * Sends raw binary [data] to [peerId] via the open DataChannel.
     * Use this for file/blob transfers — the receiver gets it via [onMessage] with [DataChannel.Buffer.binary] = true.
     * Returns true if successfully enqueued, false if the DataChannel isn't open.
     */
    fun sendBinaryData(peerId: String, data: ByteArray): Boolean {
        val dc = peers[peerId]?.dataChannel
        if (dc == null || dc.state() != DataChannel.State.OPEN) {
            Timber.tag(TAG).w("sendBinaryData: DataChannel not open for $peerId (state=${dc?.state()})")
            return false
        }
        val buffer = DataChannel.Buffer(
            ByteBuffer.wrap(data),
            true   // true = binary mode
        )
        val sent = dc.send(buffer)
        Timber.tag(TAG).d("sendBinaryData to $peerId: sent=$sent, bytes=${data.size}")
        return sent
    }

    /** Returns the current [P2PState] for [peerId], or [P2PState.IDLE] if unknown. */
    fun getState(peerId: String): P2PState =
        _connectionStates.value[peerId] ?: P2PState.IDLE

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /** Closes the connection to [peerId] and releases resources. */
    fun closeConnection(peerId: String) {
        closePeerInternal(peerId)
        updateState(peerId, P2PState.DISCONNECTED)
        Timber.tag(TAG).d("closeConnection: $peerId closed")
    }

    /** Closes all active connections and disposes the factory. */
    fun closeAll() {
        peers.keys.toList().forEach { closePeerInternal(it) }
        peers.clear()
        factory?.dispose()
        factory = null
        Timber.tag(TAG).d("closeAll: all P2P DataChannel connections released")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun closePeerInternal(peerId: String) {
        val entry = peers.remove(peerId) ?: return
        try { entry.dataChannel?.close() } catch (_: Exception) {}
        try { entry.dataChannel?.dispose() } catch (_: Exception) {}
        try { entry.pc.dispose() } catch (_: Exception) {}
    }

    private fun createPeerConnection(
        peerId: String,
        turnUsername: String,
        turnPassword: String
    ): PeerConnection? {
        val f = factory ?: return null
        val rtcConfig = PeerConnection.RTCConfiguration(buildIceServers(turnUsername, turnPassword)).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            iceCandidatePoolSize = 4
        }
        val pc = f.createPeerConnection(rtcConfig, buildPcObserver(peerId))
        if (pc == null) Timber.tag(TAG).e("createPeerConnection returned null for $peerId")
        return pc
    }

    private fun buildPcObserver(peerId: String) = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {
            Timber.tag(TAG).d("[$peerId] signaling → $state")
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            Timber.tag(TAG).d("[$peerId] ICE connection → $state")
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED ->
                    updateState(peerId, P2PState.CONNECTED)

                PeerConnection.IceConnectionState.DISCONNECTED ->
                    updateState(peerId, P2PState.DISCONNECTED)

                PeerConnection.IceConnectionState.FAILED,
                PeerConnection.IceConnectionState.CLOSED ->
                    updateState(peerId, P2PState.FAILED)

                else -> {}
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {}

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            Timber.tag(TAG).d("[$peerId] ICE gathering → $state")
        }

        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate ?: return
            signalingCallback.onIceCandidateReady(
                peerId,
                candidate.sdpMid,
                candidate.sdpMLineIndex,
                candidate.sdp
            )
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}

        /**
         * Callee receives the DataChannel created by the offerer here.
         * We store it in the PeerEntry and register our observer.
         */
        override fun onDataChannel(dc: DataChannel?) {
            dc ?: return
            Timber.tag(TAG).d("[$peerId] onDataChannel: label=${dc.label()}")
            val entry = peers[peerId] ?: return
            // Replace any stale DataChannel reference
            try { entry.dataChannel?.dispose() } catch (_: Exception) {}
            peers[peerId] = entry.copy(dataChannel = dc)
            dc.registerObserver(buildDataChannelObserver(peerId, dc))
        }
    }

    private fun buildDataChannelObserver(peerId: String, dc: DataChannel) =
        object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                val state = dc.state()
                Timber.tag(TAG).d("[$peerId] DataChannel state → $state")
                when (state) {
                    DataChannel.State.OPEN ->
                        updateState(peerId, P2PState.CONNECTED)
                    DataChannel.State.CLOSING,
                    DataChannel.State.CLOSED -> {
                        if (_connectionStates.value[peerId] == P2PState.CONNECTED)
                            updateState(peerId, P2PState.DISCONNECTED)
                    }
                    else -> {}
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                buffer ?: return
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                if (buffer.binary) {
                    Timber.tag(TAG).d("[$peerId] DataChannel binary message received, bytes=${bytes.size}")
                    _incomingMessages.tryEmit(P2PMessage(fromPeerId = peerId, content = "", binaryData = bytes))
                } else {
                    val text = String(bytes, Charsets.UTF_8)
                    Timber.tag(TAG).d("[$peerId] DataChannel text message received, len=${text.length}")
                    _incomingMessages.tryEmit(P2PMessage(fromPeerId = peerId, content = text))
                }
            }
        }

    /**
     * SdpObserver used with createOffer / createAnswer.
     * On success, sets local description and then fires [signalingCallback].
     */
    private fun buildSdpCreateObserver(peerId: String, type: String) = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {
            sdp ?: return
            val entry = peers[peerId] ?: return
            entry.pc.setLocalDescription(
                buildSdpSetObserver(peerId) {
                    signalingCallback.onLocalSdpReady(peerId, type, sdp.description)
                    Timber.tag(TAG).d("[$peerId] SDP $type set locally and signaled")
                },
                sdp
            )
        }

        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {
            Timber.tag(TAG).e("[$peerId] SDP create failure ($type): $error")
            updateState(peerId, P2PState.FAILED)
        }
        override fun onSetFailure(error: String?) {
            Timber.tag(TAG).e("[$peerId] SDP set failure ($type): $error")
        }
    }

    /** SdpObserver for setLocalDescription / setRemoteDescription calls. */
    private inline fun buildSdpSetObserver(
        peerId: String,
        crossinline onSuccess: () -> Unit
    ) = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() { onSuccess() }
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {
            Timber.tag(TAG).e("[$peerId] SDP set failure: $error")
        }
    }

    private fun flushPendingCandidates(entry: PeerEntry) {
        Timber.tag(TAG).d("[${entry.peerId}] flushing ${entry.pendingCandidates.size} buffered ICE candidates")
        entry.pendingCandidates.forEach { entry.pc.addIceCandidate(it) }
        entry.pendingCandidates.clear()
    }

    private fun updateState(peerId: String, state: P2PState) {
        _connectionStates.value = _connectionStates.value.toMutableMap().also { it[peerId] = state }
    }

    companion object {
        private const val TAG = "LETHE_P2P_DC"
        const val DC_LABEL = "lethe_p2p_chat"
    }
}
