package com.securechat.app.data.webrtc

import org.webrtc.CapturerObserver
import org.webrtc.VideoFrame
import java.util.concurrent.atomic.AtomicReference

/**
 * [CapturerObserver]-Wrapper für digitalen Zoom des eigenen Kamera-Streams.
 *
 * Wird wie [BackgroundBlurCapturerObserver] zwischen [org.webrtc.VideoCapturer] und
 * [org.webrtc.VideoSource] geschaltet. Nutzt [VideoFrame.Buffer.cropAndScale] – eine native,
 * GPU/CPU-effiziente Operation ohne Bitmap-Umweg – um einen zentrierten Ausschnitt des Frames
 * wieder auf die volle Auflösung hochzuskalieren.
 *
 * Wichtig: der Zoom wirkt auf den Frame BEVOR er an die [org.webrtc.VideoSource] weitergereicht
 * wird – er verändert also nicht nur die lokale Vorschau, sondern auch das an den
 * Gesprächspartner gesendete Bild (z.B. um ein Detail in Nahaufnahme zu zeigen).
 */
class ZoomCapturerObserver(
    private val delegate: CapturerObserver
) : CapturerObserver {

    /** 1.0 = kein Zoom, bis zu [MAX_ZOOM]. Threadsicher von der UI änderbar. */
    val zoomFactor = AtomicReference(MIN_ZOOM)

    override fun onCapturerStarted(success: Boolean) = delegate.onCapturerStarted(success)
    override fun onCapturerStopped() = delegate.onCapturerStopped()

    override fun onFrameCaptured(frame: VideoFrame) {
        val zoom = zoomFactor.get()
        if (zoom <= MIN_ZOOM + 0.01f) {
            delegate.onFrameCaptured(frame)
            return
        }
        val buffer = frame.buffer
        val cropW = (buffer.width / zoom).toInt().coerceAtLeast(2)
        val cropH = (buffer.height / zoom).toInt().coerceAtLeast(2)
        val cropX = (buffer.width - cropW) / 2
        val cropY = (buffer.height - cropH) / 2
        val zoomedBuffer = buffer.cropAndScale(cropX, cropY, cropW, cropH, buffer.width, buffer.height)
        val zoomedFrame = VideoFrame(zoomedBuffer, frame.rotation, frame.timestampNs)
        delegate.onFrameCaptured(zoomedFrame)
        // release() gibt nur den zoomedBuffer frei (eigene Referenz) – der Original-[frame]
        // gehört weiterhin dem Aufrufer (Capturer) und wird von diesem selbst freigegeben.
        zoomedFrame.release()
    }

    companion object {
        const val MIN_ZOOM = 1.0f
        const val MAX_ZOOM = 3.0f
        const val ZOOM_STEP = 0.5f
    }
}
