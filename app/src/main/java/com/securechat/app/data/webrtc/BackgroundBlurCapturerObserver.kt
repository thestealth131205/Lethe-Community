package com.securechat.app.data.webrtc

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import com.securechat.app.segmentation.SegmentationProvider
import org.webrtc.CapturerObserver
import org.webrtc.JavaI420Buffer
import org.webrtc.VideoFrame
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Auswählbarer virtueller Hintergrund für den lokalen Video-Stream (analog Nextcloud Talk). */
enum class VirtualBackgroundMode { NONE, BLUR, IMAGE }

/**
 * Ein [CapturerObserver]-Wrapper der bei aktivierter Privatsphäre-Funktion den Hintergrund
 * im lokalen Videostream via Selfie-Segmentierung ([SegmentationProvider]) ersetzt – entweder
 * unscharf ([VirtualBackgroundMode.BLUR]) oder durch ein gewähltes Bild
 * ([VirtualBackgroundMode.IMAGE]).
 *
 * Das Compositing folgt dem Ansatz von Nextcloud Talk (Canvas-2D-Pfad): die Segmentierungsmaske
 * wird mit weichen Kanten (Feathering) als Alphakanal auf die Person angewendet
 * (`PorterDuff.DST_IN` ≙ `source-in`), anschließend wird der gewählte Hintergrund darunter
 * gezeichnet (`SRC_OVER` ≙ `destination-over`). Das ersetzt den früheren Per-Pixel-Misch-Loop
 * und liefert weiche Übergänge statt harter Kanten.
 *
 * Verwendung: Wird zwischen VideoCapturer und VideoSource geschaltet.
 * Wenn [mode] = [VirtualBackgroundMode.NONE], werden Frames 1:1 weitergeleitet (kein Overhead).
 */
class BackgroundBlurCapturerObserver(
    private val delegate: CapturerObserver,
    private val segmentationProvider: SegmentationProvider
) : CapturerObserver {

    /** Aktueller Hintergrund-Modus, jederzeit threadsicher umschaltbar. */
    val mode = AtomicReference(VirtualBackgroundMode.NONE)

    /** Hintergrundbild für [VirtualBackgroundMode.IMAGE] (deckt den Frame ab, seitenverhältnis-erhaltend). */
    val backgroundImage = AtomicReference<Bitmap?>(null)

    // Dedizierter Single-Thread-Executor für CPU-intensive Blur-Verarbeitung
    private val blurExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "blur-processor").also { it.isDaemon = true }
    }
    // Verhindert Frame-Stau: wenn noch ein Frame verarbeitet wird, wird der neue übersprungen
    private val isProcessingFrame = AtomicBoolean(false)

    // Letztes Segmentierungsmaske + deren Dimensionen (AtomicReference für threadsichere Updates)
    private val lastMask      = AtomicReference<FloatArray?>(null)
    @Volatile private var lastMaskW = 1
    @Volatile private var lastMaskH = 1

    private var frameCount = 0

    // ─────────────────────────────────────────────────────────────────────────
    // CapturerObserver delegation
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCapturerStarted(success: Boolean) = delegate.onCapturerStarted(success)
    override fun onCapturerStopped() = delegate.onCapturerStopped()

    override fun onFrameCaptured(frame: VideoFrame) {
        val activeMode = mode.get()
        if (activeMode == VirtualBackgroundMode.NONE) {
            delegate.onFrameCaptured(frame)
            return
        }

        // Wenn der Executor noch mit dem vorigen Frame beschäftigt ist, Original weiterleiten
        if (!isProcessingFrame.compareAndSet(false, true)) {
            delegate.onFrameCaptured(frame)
            return
        }

        frame.retain()
        val submitted = try {
            blurExecutor.execute {
                try {
                    val i420 = frame.buffer.toI420()
                    if (i420 == null) {
                        delegate.onFrameCaptured(frame)
                        return@execute
                    }

                    val origW = i420.width
                    val origH = i420.height

                    // Für Performance: Verarbeitung auf halber Auflösung
                    val procW = (origW / 2).coerceAtLeast(64)
                    val procH = (origH / 2).coerceAtLeast(48)

                    // I420 → NV21 Bitmap (bei halber Auflösung)
                    val bitmap = i420ToBitmap(i420, procW, procH)
                    i420.release()

                    // Segmentierung alle 5 Frames neu berechnen (Performance)
                    frameCount++
                    if (frameCount % 5 == 0) {
                        // Kopie erstellen: die Segmentierung darf das Original-Bitmap nicht
                        // recyceln während es weiter unten für den Blur-Mix verwendet wird.
                        val segBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                        try {
                            val mask = segmentationProvider.segment(segBitmap, 0)
                            if (mask != null) {
                                lastMask.set(mask.buffer)
                                lastMaskW = mask.width
                                lastMaskH = mask.height
                            }
                        } catch (e: Exception) {
                            Timber.tag("LETHE_BLUR").w("Segmentation failed: ${e.message}")
                        } finally {
                            segBitmap.recycle()
                        }
                    }

                    val mask = lastMask.get()
                    val processedBitmap: Bitmap
                    if (mask != null) {
                        processedBitmap = applyVirtualBackground(
                            bitmap, mask, lastMaskW, lastMaskH, activeMode, backgroundImage.get()
                        )
                        bitmap.recycle()
                    } else {
                        processedBitmap = bitmap
                        // wird weiter unten via processedBitmap.recycle() freigegeben
                    }

                    // Blur-Bitmap zurück auf Originalauflösung skalieren und in I420 konvertieren
                    val finalBitmap = if (processedBitmap.width != origW || processedBitmap.height != origH) {
                        val scaled = Bitmap.createScaledBitmap(processedBitmap, origW, origH, false)
                        processedBitmap.recycle()
                        scaled
                    } else {
                        processedBitmap
                    }

                    val newI420 = bitmapToI420(finalBitmap, origW, origH)
                    finalBitmap.recycle()

                    val processedFrame = VideoFrame(newI420, frame.rotation, frame.timestampNs)
                    delegate.onFrameCaptured(processedFrame)
                    processedFrame.release()
                    newI420.release()
                } catch (e: Throwable) {
                    Timber.tag("LETHE_BLUR").e(e, "Frame processing failed – forwarding original")
                    delegate.onFrameCaptured(frame)
                } finally {
                    isProcessingFrame.set(false)
                    frame.release()
                }
            }
            true
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            // Executor wurde bereits durch dispose() heruntergefahren – Original weiterleiten
            false
        }
        if (!submitted) {
            isProcessingFrame.set(false)
            delegate.onFrameCaptured(frame)
            frame.release()
        }
    }

    /**
     * Gibt den Executor frei. Muss beim Abbau des WebRTC-Clients aufgerufen werden.
     * Der [segmentationProvider] ist ein App-weiter Singleton und wird hier NICHT
     * geschlossen (wird ggf. vom nächsten Call weiterverwendet).
     */
    fun dispose() {
        blurExecutor.shutdownNow()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Image conversion helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** I420Buffer → JPEG → Bitmap (mit Ziel-Auflösung targetW×targetH). */
    private fun i420ToBitmap(i420: VideoFrame.I420Buffer, targetW: Int, targetH: Int): Bitmap {
        val w = i420.width
        val h = i420.height
        val chromaH = (h + 1) / 2
        val chromaW = (w + 1) / 2

        val nv21 = ByteArray(w * h * 3 / 2)

        // Y-Ebene (zeilenweise, Stride beachten)
        val yBuf = i420.dataY
        yBuf.rewind()
        if (i420.strideY == w) {
            yBuf.get(nv21, 0, w * h)
        } else {
            for (row in 0 until h) {
                yBuf.position(row * i420.strideY)
                yBuf.get(nv21, row * w, w)
            }
        }

        // VU interleaved (NV21-Format)
        val uBuf = i420.dataU
        val vBuf = i420.dataV
        uBuf.rewind()
        vBuf.rewind()
        var uvOff = w * h
        for (row in 0 until chromaH) {
            for (col in 0 until chromaW) {
                val uIdx = row * i420.strideU + col
                val vIdx = row * i420.strideV + col
                if (uvOff + 1 < nv21.size) {
                    nv21[uvOff++] = vBuf[vIdx]
                    nv21[uvOff++] = uBuf[uIdx]
                }
            }
        }

        val yuvImage = YuvImage(nv21, android.graphics.ImageFormat.NV21, w, h, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, w, h), 80, out)
        val full = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
            ?: return Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888) // Fallback bei Dekodierungsfehler

        return if (full.width != targetW || full.height != targetH) {
            val scaled = Bitmap.createScaledBitmap(full, targetW, targetH, false)
            full.recycle()
            scaled
        } else full
    }

    /**
     * Ersetzt den Hintergrund anhand der Segmentierungsmaske – Compositing wie im Canvas-2D-Pfad
     * von Nextcloud Talk:
     *  1. Maske → weiche Alpha-Maske (Feathering durch bilineares Hochskalieren aus der niedrig
     *     aufgelösten Segmentierungsmaske).
     *  2. Person freistellen: Original mit Maske als Alpha (`PorterDuff.DST_IN` ≙ `source-in`).
     *  3. Hintergrund darunter zeichnen (`SRC_OVER` ≙ `destination-over`): entweder das
     *     unscharf gerechnete Original ([VirtualBackgroundMode.BLUR]) oder das gewählte Bild
     *     seitenverhältnis-erhaltend deckend ([VirtualBackgroundMode.IMAGE]).
     */
    private fun applyVirtualBackground(
        original: Bitmap,
        mask: FloatArray,
        maskW: Int,
        maskH: Int,
        activeMode: VirtualBackgroundMode,
        bgImage: Bitmap?
    ): Bitmap {
        val w = original.width
        val h = original.height

        // ── 1. Weiche Alpha-Maske aus der Segmentierungsmaske ────────────────────
        val mW = maskW.coerceAtLeast(1)
        val mH = maskH.coerceAtLeast(1)
        val maskPixels = IntArray(mW * mH)
        for (idx in 0 until mW * mH) {
            val confidence = if (idx < mask.size) mask[idx] else 0f
            val a = (confidence.coerceIn(0f, 1f) * 255f).toInt()
            // Alpha = Personen-Konfidenz; RGB egal (wird nur als Alpha via DST_IN genutzt)
            maskPixels[idx] = (a shl 24)
        }
        val maskSmall = Bitmap.createBitmap(mW, mH, Bitmap.Config.ARGB_8888)
        maskSmall.setPixels(maskPixels, 0, mW, 0, 0, mW, mH)
        // bilineares Hochskalieren erzeugt weiche Kanten (Feathering)
        val maskFull = Bitmap.createScaledBitmap(maskSmall, w, h, true)
        maskSmall.recycle()

        // ── 2. Person freistellen (Original × Masken-Alpha) ──────────────────────
        val person = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val personCanvas = Canvas(person)
        personCanvas.drawBitmap(original, 0f, 0f, null)
        val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        personCanvas.drawBitmap(maskFull, 0f, 0f, cutPaint)
        maskFull.recycle()

        // ── 3. Hintergrund + Person zusammensetzen ───────────────────────────────
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val outCanvas = Canvas(result)

        if (activeMode == VirtualBackgroundMode.IMAGE && bgImage != null && !bgImage.isRecycled) {
            drawCoverImage(outCanvas, bgImage, w, h)
        } else {
            // BLUR (auch Fallback, falls IMAGE ohne gültiges Bild): günstige Weichzeichnung
            val blurFactor = 8
            val smallW = (w / blurFactor).coerceAtLeast(1)
            val smallH = (h / blurFactor).coerceAtLeast(1)
            val small   = Bitmap.createScaledBitmap(original, smallW, smallH, true)
            val blurred = Bitmap.createScaledBitmap(small, w, h, true)
            small.recycle()
            outCanvas.drawBitmap(blurred, 0f, 0f, null)
            blurred.recycle()
        }

        outCanvas.drawBitmap(person, 0f, 0f, null)
        person.recycle()
        return result
    }

    /** Zeichnet [image] deckend (center-crop, seitenverhältnis-erhaltend) auf die Zielfläche w×h. */
    private fun drawCoverImage(canvas: Canvas, image: Bitmap, w: Int, h: Int) {
        val iw = image.width.toFloat()
        val ih = image.height.toFloat()
        if (iw <= 0f || ih <= 0f) return
        val scale = maxOf(w / iw, h / ih)
        val drawW = iw * scale
        val drawH = ih * scale
        val left = (w - drawW) / 2f
        val top  = (h - drawH) / 2f
        canvas.drawBitmap(image, null, RectF(left, top, left + drawW, top + drawH),
            Paint(Paint.FILTER_BITMAP_FLAG))
    }

    /** Bitmap → JavaI420Buffer für WebRTC VideoFrame. */
    private fun bitmapToI420(bitmap: Bitmap, w: Int, h: Int): JavaI420Buffer {
        val scaled = if (bitmap.width != w || bitmap.height != h) {
            Bitmap.createScaledBitmap(bitmap, w, h, false)
        } else bitmap

        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== bitmap) scaled.recycle()

        val i420 = JavaI420Buffer.allocate(w, h)
        val yBuf = i420.dataY
        val uBuf = i420.dataU
        val vBuf = i420.dataV
        yBuf.rewind()
        uBuf.rewind()
        vBuf.rewind()

        for (j in 0 until h) {
            for (i2 in 0 until w) {
                val pixel = pixels[j * w + i2]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr  8) and 0xFF
                val b =  pixel         and 0xFF

                // BT.601 RGB → YUV
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yBuf.put(y.coerceIn(0, 255).toByte())

                if (j % 2 == 0 && i2 % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    uBuf.put(u.coerceIn(0, 255).toByte())
                    vBuf.put(v.coerceIn(0, 255).toByte())
                }
            }
        }
        return i420
    }
}
