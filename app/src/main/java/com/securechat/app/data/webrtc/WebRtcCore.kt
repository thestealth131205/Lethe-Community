package com.securechat.app.data.webrtc

import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory

/**
 * Prozessweite, gemeinsam genutzte WebRTC-Kernressourcen.
 *
 * Alle [WebRtcClient]-Instanzen – Primärclient UND Sekundärclients bei Gruppenanrufen –
 * teilen sich EINE [EglBase] und EINE [PeerConnectionFactory].
 *
 * Hintergrund: Jeder Client baute zuvor eine eigene EglBase + Factory. Beim Hinzufügen
 * eines weiteren Teilnehmers entstand dadurch ein zweiter OpenGL-Kontext, und geteilte
 * Tracks (Kamera/Mikrofon) wurden über Factory-Grenzen hinweg in fremde PeerConnections
 * gehängt. Das korrumpierte den nativen Zustand → das laufende Bild fror ein und der
 * Ton verstummte.
 *
 * Diese Ressourcen leben für die gesamte Prozesslaufzeit (kein Dispose / kein
 * Ref-Counting), analog zum bereits prozessweiten [PeerConnectionFactory.initialize],
 * das in SecureChatApplication aufgerufen wird.
 */
object WebRtcCore {

    /** Gemeinsamer EGL-Kontext für Capture, Encoder/Decoder und UI-Renderer. */
    val eglBase: EglBase by lazy { EglBase.create() }

    /** Gemeinsame Factory mit Hardware-H.264-Encoder/Decoder. */
    val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setOptions(PeerConnectionFactory.Options().apply { disableNetworkMonitor = true })
            .createPeerConnectionFactory()
    }
}
