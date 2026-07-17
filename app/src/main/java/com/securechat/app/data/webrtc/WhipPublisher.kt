package com.securechat.app.data.webrtc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Publiziert die lokalen Kamera- und Mikrofon-Tracks via WHIP-Protokoll an einen MediaMTX-Server.
 *
 * WHIP (WebRTC-HTTP Ingestion Protocol) ist ein minimales HTTP-basiertes Signaling-Protokoll:
 *  1. Client erstellt SDP-Offer und wartet auf vollstaendige ICE-Kandidaten (non-trickle).
 *  2. POST {whip_url} mit Body = SDP-Offer (application/sdp).
 *  3. Server antwortet mit SDP-Answer → setRemoteDescription → Verbindung steht.
 *
 * Ressourcen: Teilt [WebRtcCore.factory] und [WebRtcCore.eglBase] mit laufenden Anrufen.
 * Es werden ausschliesslich die EIGENEN lokalen Kamera-/Mikrofon-Tracks publiziert –
 * jeder Teilnehmer publisht seinen eigenen Stream (kein Weiterleiten fremder Remote-
 * Tracks). Die laufende Call-PeerConnection bleibt unangetastet.
 */
class WhipPublisher {

    private var peerConnection: PeerConnection? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** true wenn WHIP-Publish aktiv ist. */
    @Volatile var isPublishing = false
        private set

    /**
     * Startet das WHIP-Publishing.
     *
     * @param whipUrl  Vollstaendige WHIP-Endpunkt-URL, z. B.
     *                 "https://letheapp.de/rec/call_<id>_<userId>/whip"
     * @param videoTrack  Lokaler VideoTrack (null bei Audio-Only-Calls)
     * @param audioTrack  Lokaler AudioTrack
     * @return true bei Erfolg, false bei Fehler
     */
    suspend fun start(
        whipUrl: String,
        videoTrack: VideoTrack?,
        audioTrack: AudioTrack?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val offerSdp = buildOfferSdp(videoTrack, audioTrack) ?: return@withContext false
            val answerSdp = postWhip(whipUrl, offerSdp) ?: return@withContext false
            setAnswer(answerSdp)
            isPublishing = true
            Timber.tag("WHIP").i("Publishing gestartet: $whipUrl")
            true
        } catch (e: Exception) {
            Timber.tag("WHIP").e(e, "start() fehlgeschlagen")
            stop()
            false
        }
    }

    /** Stoppt das Publishing und schliesst die PeerConnection. */
    fun stop() {
        isPublishing = false
        try { peerConnection?.close() } catch (_: Exception) {}
        peerConnection = null
        Timber.tag("WHIP").i("Publishing gestoppt")
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Erstellt eine PeerConnection mit sendonly-Transceivers, generiert ein SDP-Offer
     * und wartet bis ICE-Gathering abgeschlossen ist (non-trickle).
     */
    private suspend fun buildOfferSdp(videoTrack: VideoTrack?, audioTrack: AudioTrack?): String? {
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }

        val iceComplete = kotlinx.coroutines.CompletableDeferred<String>()

        val observer = object : PeerConnection.Observer {
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) {
                    val sdp = peerConnection?.localDescription?.description
                    if (sdp != null) iceComplete.complete(sdp)
                    else iceComplete.completeExceptionally(Exception("localDescription null nach ICE COMPLETE"))
                }
            }
            override fun onIceCandidate(p0: org.webrtc.IceCandidate?) {}
            override fun onIceCandidatesRemoved(p0: Array<out org.webrtc.IceCandidate>?) {}
            override fun onAddStream(p0: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(p0: org.webrtc.MediaStream?) {}
            override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onAddTrack(p0: org.webrtc.RtpReceiver?, p1: Array<out org.webrtc.MediaStream>?) {}
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {}
        }

        peerConnection = WebRtcCore.factory.createPeerConnection(rtcConfig, observer)
            ?: return null

        // Sendonly-Transceiver hinzufuegen
        if (videoTrack != null) {
            peerConnection!!.addTransceiver(
                videoTrack,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
            )
        }
        if (audioTrack != null) {
            peerConnection!!.addTransceiver(
                audioTrack,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
            )
        }

        // SDP-Offer erzeugen
        val offerCreated = suspendCancellableCoroutine<SessionDescription?> { cont ->
            peerConnection!!.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) { cont.resume(sdp) }
                override fun onCreateFailure(e: String?) { cont.resume(null) }
                override fun onSetSuccess() {}
                override fun onSetFailure(e: String?) {}
            }, MediaConstraints())
        } ?: return null

        // WICHTIG: MediaMTX zeichnet in fMP4 auf und kann KEIN VP8/VP9 (Video) bzw.
        // G722/PCMU/PCMA (Audio) in MP4 speichern – es ueberspringt solche Tracks.
        // Daher Video auf H264 und Audio auf Opus reduzieren, damit MP4-kompatible
        // Spuren ausgehandelt werden.
        val mungedOffer = SessionDescription(
            offerCreated.type,
            mungeSdpForRecording(offerCreated.description)
        )

        // LocalDescription setzen (startet ICE-Gathering)
        suspendCancellableCoroutine<Unit> { cont ->
            peerConnection!!.setLocalDescription(object : SdpObserver {
                override fun onSetSuccess() { cont.resume(Unit) }
                override fun onSetFailure(e: String?) { cont.resume(Unit) }
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, mungedOffer)
        }

        // Auf vollstaendige ICE-Kandidaten warten (max 15 s)
        return withTimeoutOrNull(15_000L) { iceComplete.await() }
    }

    /**
     * Reduziert das Offer fuer die MediaMTX-fMP4-Aufzeichnung:
     *  - m=video auf H264 (inkl. zugehoeriger RTX-Payloads) – VP8/VP9/AV1 koennen nicht
     *    in MP4 gespeichert werden.
     *  - m=audio auf Opus – statische Codecs wie G722/PCMU/PCMA werden vom fMP4-Recorder
     *    uebersprungen (kein Ton in der Aufnahme).
     *
     * WICHTIG: Das Filtern der Attribut-Zeilen (rtpmap/fmtp/rtcp-fb) erfolgt
     * sektions-bewusst – sonst wuerde das Entfernen verworfener Video-Payloads auch die
     * Opus-rtpmap-Zeile (dynamischer Payload) treffen und Audio auf einen statischen
     * Codec degradieren. Bietet das Geraet einen Codec nicht an, bleibt die jeweilige
     * Sektion unveraendert.
     */
    private fun mungeSdpForRecording(sdp: String): String {
        val lines = sdp.split("\r\n")
        val rtpmapRe = Regex("^a=rtpmap:(\\d+) ([A-Za-z0-9\\-]+)/.*")
        val aptRe = Regex("^a=fmtp:(\\d+) apt=(\\d+)")
        val attrRe = Regex("^a=(rtpmap|fmtp|rtcp-fb):(\\d+)")

        // 1) Pro Sektion (Index der m=-Zeile) die zu behaltenden Payloads bestimmen.
        data class Section(val mIdx: Int, val kind: String)
        val sections = mutableListOf<Section>()
        lines.forEachIndexed { i, l ->
            when {
                l.startsWith("m=video ") -> sections.add(Section(i, "video"))
                l.startsWith("m=audio ") -> sections.add(Section(i, "audio"))
                l.startsWith("m=") -> sections.add(Section(i, "other"))
            }
        }
        if (sections.isEmpty()) return sdp

        // Payload-Zugehoerigkeit jeder Zeile zur Sektion ableiten.
        fun sectionOf(idx: Int): Section? {
            var cur: Section? = null
            for (s in sections) { if (s.mIdx <= idx) cur = s else break }
            return cur
        }

        // keepPts je Sektions-m-Index berechnen (nur fuer video/audio gefiltert).
        val keepBySection = HashMap<Int, MutableSet<String>>()
        for (s in sections) {
            if (s.kind == "other") continue
            // Ende der Sektion = naechste m=-Zeile
            val nextM = sections.firstOrNull { it.mIdx > s.mIdx }?.mIdx ?: lines.size
            val target = if (s.kind == "video") "H264" else "OPUS"
            val primary = mutableSetOf<String>()
            for (i in s.mIdx until nextM) {
                val m = rtpmapRe.find(lines[i]) ?: continue
                if (m.groupValues[2].equals(target, ignoreCase = true)) primary.add(m.groupValues[1])
            }
            if (primary.isEmpty()) continue  // Codec nicht angeboten → Sektion unveraendert lassen
            val keep = primary.toMutableSet()
            // RTX-Payloads behalten, deren apt auf einen behaltenen Payload zeigt (relevant fuer Video)
            for (i in s.mIdx until nextM) {
                val m = aptRe.find(lines[i]) ?: continue
                if (m.groupValues[2] in primary) keep.add(m.groupValues[1])
            }
            keepBySection[s.mIdx] = keep
        }
        if (keepBySection.isEmpty()) return sdp

        // 2) Zeilen neu aufbauen: m=-Zeilen kuerzen, verworfene Payload-Attribute entfernen.
        val out = ArrayList<String>(lines.size)
        for ((idx, line) in lines.withIndex()) {
            val sec = sectionOf(idx)
            val keep = sec?.let { keepBySection[it.mIdx] }
            if (keep == null) { out.add(line); continue }  // Sektion nicht gefiltert

            if (idx == sec.mIdx) {
                // m=-Zeile: Payload-Liste auf behaltene Payloads kuerzen (Reihenfolge erhalten)
                val parts = line.split(" ")
                if (parts.size > 3) {
                    val keptOrdered = parts.drop(3).filter { it in keep }
                    out.add((parts.take(3) + keptOrdered).joinToString(" "))
                } else out.add(line)
                continue
            }
            // Attribut-Zeile innerhalb dieser Sektion: nur behalten, wenn Payload erhalten bleibt
            val m = attrRe.find(line)
            if (m == null) out.add(line)
            else if (m.groupValues[2] in keep) out.add(line)
        }
        return out.joinToString("\r\n")
    }

    /** Sendet den SDP-Offer per HTTP-POST ans WHIP-Endpoint und liefert den SDP-Answer. */
    private suspend fun postWhip(url: String, offerSdp: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(offerSdp.toRequestBody("application/sdp".toMediaType()))
            .header("Content-Type", "application/sdp")
            .build()
        try {
            val response = http.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.tag("WHIP").w("WHIP POST ${response.code}: $url")
                return@withContext null
            }
            response.body?.string()
        } catch (e: Exception) {
            Timber.tag("WHIP").e(e, "WHIP POST fehlgeschlagen")
            null
        }
    }

    /** Setzt den SDP-Answer als RemoteDescription. */
    private suspend fun setAnswer(answerSdp: String) {
        val answer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        suspendCancellableCoroutine<Unit> { cont ->
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() { cont.resume(Unit) }
                override fun onSetFailure(e: String?) { cont.resume(Unit) }
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, answer) ?: cont.resume(Unit)
        }
    }
}
