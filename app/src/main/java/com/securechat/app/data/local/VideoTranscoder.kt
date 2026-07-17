package com.securechat.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.TextureOverlay
import com.google.common.collect.ImmutableList
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Beschreibt eine über das Video gelegte Audio-Spur.
 * @param startOffsetMs Startpunkt innerhalb des Songs (weggeschnittener Anfang)
 * @param clipDurationMs Länge des verwendeten Ausschnitts
 * @param timelineStartMs Position auf der Video-Timeline, ab der der Song zu hören ist
 */
data class AudioOverlay(
    val url: String,
    val startOffsetMs: Long,
    val clipDurationMs: Long,
    val timelineStartMs: Long = 0L
)

/**
 * Farb-/Ton-Anpassungen des Video-Editors. Alle Werte zentrieren auf 0 (neutral);
 * gültiger Bereich −1f..+1f. Positive Werte verstärken, negative schwächen.
 * @param brightness  Helligkeit (additiver Lift)
 * @param contrast    Kontrast (Spreizung um die Bildmitte)
 * @param saturation  Farbsättigung (0 = neutral, −1 = grau, +1 = kräftiger)
 * @param temperature Farbtemperatur (+ = wärmer/mehr Rot, − = kühler/mehr Blau)
 * @param blackPoint  Schwarzpunkt (+ = Tiefen absaufen lassen, − = Tiefen anheben)
 * @param whitePoint  Weißpunkt (+ = Lichter aufhellen, − = Lichter absenken)
 * @param shadows     Schatten (Tiefen anheben/absenken, Weiß bleibt fixiert)
 * @param highlights  Spitzlichter (Helles verstärken/dämpfen)
 */
data class ColorAdjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    val blackPoint: Float = 0f,
    val whitePoint: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f
) {
    val isIdentity: Boolean
        get() = brightness == 0f && contrast == 0f && saturation == 0f &&
            temperature == 0f && blackPoint == 0f && whitePoint == 0f &&
            shadows == 0f && highlights == 0f
}

/**
 * Transkodiert Videos lokal auf dem Gerät zu H.264/AAC MP4, max 720p.
 * Verwendet Jetpack Media3 Transformer (Hardware-beschleunigt via MediaCodec).
 * Läuft auf dem Haupt-Thread, blockiert diesen aber nicht (async via Listener).
 *
 * Der Mutex stellt sicher, dass nie zwei Transkodierungsjobs gleichzeitig laufen –
 * Android-Geräte unterstützen oft nur einen aktiven H.264-Hardware-Encoder gleichzeitig,
 * was beim gleichzeitigen Teilen in mehrere Chats zu Hängern führen würde.
 */
object VideoTranscoder {

    private val transcodeMutex = Mutex()

    /**
     * Erzeugt eine stille WAV-Datei der Länge [durationMs] (44.1 kHz, Stereo, 16-bit PCM).
     * Wird einer Musik-Sequenz vorangestellt, damit der Song erst ab einer bestimmten
     * Timeline-Position im Video zu hören ist (Media3 1.4.1 hat kein Gap-API).
     */
    private fun createSilentWavFile(context: Context, durationMs: Long): File? {
        if (durationMs <= 0L) return null
        val sampleRate = 44100
        val channels = 2
        val bitsPerSample = 16
        val bytesPerFrame = channels * bitsPerSample / 8
        val byteRate = sampleRate * bytesPerFrame
        val numFrames = (sampleRate * durationMs / 1000L).toInt()
        val dataSize = numFrames * bytesPerFrame
        return try {
            val file = File(context.cacheDir, "silence_${durationMs}_${System.currentTimeMillis()}.wav")
            FileOutputStream(file).use { fos ->
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray(Charsets.US_ASCII))
                header.putInt(36 + dataSize)
                header.put("WAVE".toByteArray(Charsets.US_ASCII))
                header.put("fmt ".toByteArray(Charsets.US_ASCII))
                header.putInt(16)                               // fmt chunk size
                header.putShort(1)                              // PCM
                header.putShort(channels.toShort())
                header.putInt(sampleRate)
                header.putInt(byteRate)
                header.putShort(bytesPerFrame.toShort())        // block align
                header.putShort(bitsPerSample.toShort())
                header.put("data".toByteArray(Charsets.US_ASCII))
                header.putInt(dataSize)
                fos.write(header.array())
                val zeros = ByteArray(8192)
                var remaining = dataSize
                while (remaining > 0) {
                    val chunk = minOf(remaining, zeros.size)
                    fos.write(zeros, 0, chunk)
                    remaining -= chunk
                }
            }
            file
        } catch (_: Exception) {
            null
        }
    }

    /** Erstellt einen ChannelMixingAudioProcessor der alle Kanäle um [volume] skaliert (0f..1f). */
    @OptIn(UnstableApi::class)
    private fun createVolumeProcessor(volume: Float): ChannelMixingAudioProcessor =
        ChannelMixingAudioProcessor().also { proc ->
            for (ch in 1..8) {
                // Diagonalmatrix: Eingangskanal i → Ausgangskanal i mit Faktor volume
                val coeffs = FloatArray(ch * ch) { i -> if (i / ch == i % ch) volume else 0f }
                proc.putChannelMixingMatrix(ChannelMixingMatrix(ch, ch, coeffs))
            }
        }

    /**
     * Vollflächige, opake schwarze Bitmap – wird als Overlay über den Clip gelegt und
     * per zeitabhängigem Alpha ein-/ausgeblendet (Fade durch Schwarz). Groß genug, um
     * das Ausgabebild bei gegebener Zielhöhe zu bedecken (zentriert, Ränder abgeschnitten);
     * [outputHeight] * 2 deckt auch breite (bis 2:1) 4K-Frames vollständig ab.
     */
    private fun createBlackBitmap(outputHeight: Int): Bitmap {
        val size = maxOf(1920, outputHeight * 2)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
    }

    /**
     * Baut einen [OverlayEffect] der eine schwarze Fläche über den Clip legt und deren
     * Deckkraft zeitabhängig steuert: In den ersten [fadeInWindowMs] von 1→0 (Einblenden
     * aus Schwarz), in den letzten [fadeOutWindowMs] von 0→1 (Ausblenden nach Schwarz).
     * Ergibt zusammen mit dem Nachbarclip einen Fade-durch-Schwarz-Übergang.
     *
     * Annahme: [presentationTimeUs] ist clip-lokal (startet bei 0 je Clip nach dem Trim).
     */
    @OptIn(UnstableApi::class)
    private fun buildFadeOverlayEffect(
        blackBitmap: Bitmap,
        clipDurationMs: Long,
        fadeInWindowMs: Long,
        fadeOutWindowMs: Long
    ): androidx.media3.common.Effect {
        val clipDurUs = clipDurationMs * 1000.0
        val fadeInUs = fadeInWindowMs * 1000.0
        val fadeOutUs = fadeOutWindowMs * 1000.0
        val overlay = object : BitmapOverlay() {
            override fun getBitmap(presentationTimeUs: Long): Bitmap = blackBitmap
            override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
                val t = presentationTimeUs.toDouble()
                var alpha = 0.0
                if (fadeInUs > 0.0 && t < fadeInUs) {
                    alpha = maxOf(alpha, 1.0 - t / fadeInUs)
                }
                if (fadeOutUs > 0.0 && clipDurUs > 0.0 && t > clipDurUs - fadeOutUs) {
                    alpha = maxOf(alpha, (t - (clipDurUs - fadeOutUs)) / fadeOutUs)
                }
                val a = alpha.coerceIn(0.0, 1.0).toFloat()
                return OverlaySettings.Builder().setAlphaScale(a).build()
            }
        }
        return OverlayEffect(ImmutableList.of<TextureOverlay>(overlay))
    }

    /**
     * Hängt – falls ein Übergang an mindestens einer Clip-Seite anliegt – einen
     * Fade-Overlay-Effekt an die Effektliste an. [fadeInWindowMs]/[fadeOutWindowMs] sind
     * bereits die halben Übergangsdauern (Fade-durch-Schwarz: je Seite die Hälfte).
     */
    @OptIn(UnstableApi::class)
    private fun appendFadeOverlay(
        effects: MutableList<androidx.media3.common.Effect>,
        blackBitmap: Bitmap,
        clipDurationMs: Long,
        fadeInWindowMs: Long,
        fadeOutWindowMs: Long
    ) {
        if (clipDurationMs <= 0L) return
        if (fadeInWindowMs <= 0L && fadeOutWindowMs <= 0L) return
        effects += buildFadeOverlayEffect(blackBitmap, clipDurationMs, fadeInWindowMs, fadeOutWindowMs)
    }

    /**
     * Baut ein [EditedMediaItem] aus einem Standbild, das als Video-Clip fester Länge
     * ([durationMs]) gerendert wird. Media3 verlangt für Bilder eine Dauer UND eine
     * Bildrate; der MIME-Typ wird explizit gesetzt, damit der Image-Asset-Loader greift.
     */
    @OptIn(UnstableApi::class)
    private fun buildImageEditedItem(
        context: Context,
        uri: Uri,
        durationMs: Long,
        videoEffects: List<androidx.media3.common.Effect>
    ): EditedMediaItem {
        val mime = context.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") }
            ?: androidx.media3.common.MimeTypes.IMAGE_JPEG
        val item = MediaItem.Builder().setUri(uri).setMimeType(mime).build()
        return EditedMediaItem.Builder(item)
            .setDurationUs(durationMs.coerceAtLeast(1L) * 1000L)
            .setFrameRate(30)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()
    }

    /** RgbMatrix-Effekt mit konstanter (zeitunabhängiger) 4x4-Farbmatrix. */
    @OptIn(UnstableApi::class)
    private class ColorAdjustRgbMatrix(private val matrix: FloatArray) : RgbMatrix {
        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix
    }

    /**
     * Baut aus den Reglern [adj] einen RGB-Matrix-Effekt (affine Farb-/Ton-Korrektur).
     * Die Kette wird zu EINER 4x4-Matrix (out.rgb = M·in.rgb + O, Alpha bleibt) zusammengefasst:
     * Pegel (Schwarz-/Weißpunkt) → Kontrast → Helligkeit → Spitzlichter → Schatten →
     * Farbtemperatur → Sättigung. Gibt null zurück, wenn nichts anzupassen ist.
     */
    @OptIn(UnstableApi::class)
    fun colorAdjustEffect(adj: ColorAdjustments): androidx.media3.common.Effect? {
        if (adj.isIdentity) return null

        // Phase 1: kanal-uniforme affine Kette → Gain g, Offset o
        var g = 1f
        var o = 0f
        // Pegel (Schwarz-/Weißpunkt): out = (in - blackLevel) / (whiteLevel - blackLevel)
        val blackLevel = adj.blackPoint * 0.5f
        val whiteLevel = 1f - adj.whitePoint * 0.5f
        val span = (whiteLevel - blackLevel).let { if (kotlin.math.abs(it) < 0.1f) 0.1f else it }
        val scaleL = 1f / span
        g *= scaleL; o = o * scaleL + (-blackLevel * scaleL)
        // Kontrast um die Bildmitte (0.5)
        val cf = 1f + adj.contrast
        g *= cf; o = cf * o + 0.5f * (1f - cf)
        // Helligkeit (additiv)
        o += adj.brightness * 0.5f
        // Spitzlichter: Gain ab 0 → wirkt stärker auf Helles
        val gainH = 1f + adj.highlights * 0.5f
        g *= gainH; o *= gainH
        // Schatten: Tiefen anheben, Weiß bleibt fix (in=1 → 1)
        val gainS = 1f - adj.shadows * 0.4f
        val offS = adj.shadows * 0.4f
        g *= gainS; o = gainS * o + offS

        // Phase 2: Farbtemperatur (per-Kanal, warm = mehr Rot / weniger Blau)
        val sR = 1f + adj.temperature * 0.3f
        val sB = 1f - adj.temperature * 0.3f
        val gains = floatArrayOf(sR * g, g, sB * g)
        val offs = floatArrayOf(sR * o, o, sB * o)

        // Phase 3: Sättigung (Mischung Richtung Luma). S[i][j] = (i==j?sat:0) + w[j]*(1-sat)
        val sat = 1f + adj.saturation
        val w = floatArrayOf(0.2126f, 0.7152f, 0.0722f)
        fun sEl(i: Int, j: Int): Float = (if (i == j) sat else 0f) + w[j] * (1f - sat)
        // M[i][j] = S[i][j]*gains[j]; O[i] = Σ_k S[i][k]*offs[k]
        val m = Array(3) { i -> FloatArray(3) { j -> sEl(i, j) * gains[j] } }
        val outO = FloatArray(3) { i -> sEl(i, 0) * offs[0] + sEl(i, 1) * offs[1] + sEl(i, 2) * offs[2] }

        // Spalten-major 4x4 (GL): mat[col*4+row] = M[row][col], Translation in Spalte 4
        val mat = FloatArray(16)
        for (col in 0..2) for (row in 0..2) mat[col * 4 + row] = m[row][col]
        mat[12] = outO[0]; mat[13] = outO[1]; mat[14] = outO[2]
        mat[15] = 1f
        return ColorAdjustRgbMatrix(mat)
    }

    /**
     * Transkodiert [inputUri] → [outputFile].
     * @param onProgress Fortschritts-Callback 0f..1f während der Transkodierung
     * @return true = erfolgreich, false = fehlgeschlagen (Fallback auf Original empfohlen)
     */
    @OptIn(UnstableApi::class)
    suspend fun transcode(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        targetHeight: Int = 720,
        colorAdjustments: ColorAdjustments? = null,
        onProgress: (Float) -> Unit = {}
    ): Boolean = transcodeMutex.withLock { withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            val progressHolder = ProgressHolder()
            var transformer: Transformer? = null

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    handler.removeCallbacksAndMessages(null)
                    onProgress(1f)
                    if (cont.isActive) cont.resume(true)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    handler.removeCallbacksAndMessages(null)
                    if (cont.isActive) cont.resume(false)
                }
            }

            transformer = Transformer.Builder(context)
                .addListener(listener)
                .build()

            // Ziel-Höhe (Standard 720p; Editor kann HD/4K anfordern, nie über Quellauflösung)
            // + optionale Farb-/Ton-Anpassung (vor der Skalierung).
            val videoEffects: List<androidx.media3.common.Effect> = listOfNotNull(
                colorAdjustments?.let { colorAdjustEffect(it) },
                Presentation.createForHeight(targetHeight)
            )
            val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
                .setEffects(Effects(emptyList(), videoEffects))
                .build()

            // HDR-Videos (z. B. 4K-Aufnahmen von Pixel/Samsung) zu SDR tonemappen.
            // Ohne dies schlägt der Export auf Geräten fehl, deren H.264-Encoder kein
            // HDR unterstützt – dann landet das ungekürzte Original im Upload und wird
            // wegen Überschreitung des Größenlimits verworfen.
            val composition = Composition.Builder(EditedMediaItemSequence(editedMediaItem))
                .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                .build()

            transformer!!.start(composition, outputFile.absolutePath)

            // Fortschritt alle 300ms abfragen und weitergeben
            val pollRunnable = object : Runnable {
                override fun run() {
                    if (!cont.isActive) return
                    transformer?.let { t ->
                        val state = t.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(progressHolder.progress / 100f)
                        }
                    }
                    handler.postDelayed(this, 300)
                }
            }
            handler.postDelayed(pollRunnable, 300)

            cont.invokeOnCancellation {
                handler.removeCallbacksAndMessages(null)
                transformer?.cancel()
                outputFile.delete()
            }
        }
    } }

    /**
     * Transkodiert [inputUri] → [outputFile] mit optionalem Trim und Crop.
     * Trim: [trimStartMs] und [trimEndMs] (0 = kein Trim am jeweiligen Ende).
     * Crop: Entweder ein expliziter [cropRect] (normalisierte NDC-Werte
     * [left, right, bottom, top] im Bereich -1..1 – erlaubt Pan/Zoom-Ausschnitt),
     * oder als Fallback [cropAspect] (zentrierter Aspect-Ratio-Crop, null = kein Crop).
     * [videoWidthPx]/[videoHeightPx] werden für den zentrierten Aspect-Crop benötigt.
     */
    @OptIn(UnstableApi::class)
    suspend fun transcodeWithEdit(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        trimStartMs: Long = 0L,
        trimEndMs: Long = 0L,
        cropAspect: Float? = null,
        cropRect: FloatArray? = null,
        videoWidthPx: Int = 0,
        videoHeightPx: Int = 0,
        muteAudio: Boolean = false,
        videoAudioVolume: Float = 1f,
        audioOverlayUrls: List<AudioOverlay> = emptyList(),
        musicVolume: Float = 1f,
        targetHeight: Int = 720,
        colorAdjustments: ColorAdjustments? = null,
        onProgress: (Float) -> Unit = {}
    ): Boolean = transcodeMutex.withLock { withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            val progressHolder = ProgressHolder()
            var transformer: Transformer? = null

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    handler.removeCallbacksAndMessages(null)
                    onProgress(1f)
                    if (cont.isActive) cont.resume(true)
                }
                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    handler.removeCallbacksAndMessages(null)
                    if (cont.isActive) cont.resume(false)
                }
            }

            transformer = Transformer.Builder(context).addListener(listener).build()

            // Video-Effekte: optional Farb-/Ton-Anpassung + Crop + Ziel-Höhe
            val videoEffects = mutableListOf<androidx.media3.common.Effect>()
            colorAdjustments?.let { colorAdjustEffect(it) }?.let { videoEffects += it }
            if (cropRect != null && cropRect.size == 4) {
                // Expliziter Ausschnitt (Pan/Zoom): [left, right, bottom, top]
                videoEffects += Crop(cropRect[0], cropRect[1], cropRect[2], cropRect[3])
            } else if (cropAspect != null && videoWidthPx > 0 && videoHeightPx > 0) {
                val inputAspect = videoWidthPx.toFloat() / videoHeightPx
                val (cropL, cropR, cropB, cropT) = if (cropAspect < inputAspect) {
                    // Schmaler → Breite beschneiden
                    val frac = cropAspect / inputAspect
                    listOf(-frac, frac, -1f, 1f)
                } else {
                    // Breiter → Höhe beschneiden
                    val frac = inputAspect / cropAspect
                    listOf(-1f, 1f, -frac, frac)
                }
                videoEffects += Crop(cropL, cropR, cropB, cropT)
            }
            videoEffects += Presentation.createForHeight(targetHeight)

            // Trim-Konfiguration
            val clippingConfig = if (trimStartMs > 0L || trimEndMs > 0L) {
                MediaItem.ClippingConfiguration.Builder()
                    .apply {
                        if (trimStartMs > 0L) setStartPositionMs(trimStartMs)
                        if (trimEndMs > 0L) setEndPositionMs(trimEndMs)
                    }
                    .build()
            } else {
                MediaItem.ClippingConfiguration.UNSET
            }

            val mediaItem = MediaItem.Builder()
                .setUri(inputUri)
                .setClippingConfiguration(clippingConfig)
                .build()

            val videoAudioProcessors = if (!muteAudio && videoAudioVolume < 0.999f)
                listOf(createVolumeProcessor(videoAudioVolume)) else emptyList()
            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setEffects(Effects(videoAudioProcessors, videoEffects))
                .apply { if (muteAudio) setRemoveAudio(true) }
                .build()

            val sequences = mutableListOf(EditedMediaItemSequence(editedMediaItem))

            // Audiospuren hinzufügen (als zusätzliche Sequenzen → Media3 mischt automatisch)
            val musicAudioProcessors = if (musicVolume < 0.999f)
                listOf(createVolumeProcessor(musicVolume)) else emptyList()
            audioOverlayUrls.forEach { overlay ->
                val audioClipping = MediaItem.ClippingConfiguration.Builder()
                    .apply {
                        if (overlay.startOffsetMs > 0L) setStartPositionMs(overlay.startOffsetMs)
                        if (overlay.clipDurationMs > 0L) setEndPositionMs(overlay.startOffsetMs + overlay.clipDurationMs)
                    }
                    .build()
                val audioMediaItem = MediaItem.Builder()
                    .setUri(overlay.url)
                    .setClippingConfiguration(audioClipping)
                    .build()
                val audioEdited = EditedMediaItem.Builder(audioMediaItem)
                    .setRemoveVideo(true)
                    .apply { if (musicAudioProcessors.isNotEmpty()) setEffects(Effects(musicAudioProcessors, emptyList())) }
                    .build()
                // Timeline-Position: Stille voranstellen, damit der Song erst später einsetzt
                val silenceFile = createSilentWavFile(context, overlay.timelineStartMs)
                if (silenceFile != null) {
                    val silenceEdited = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(silenceFile)))
                        .setRemoveVideo(true)
                        .build()
                    sequences.add(EditedMediaItemSequence(listOf(silenceEdited, audioEdited)))
                } else {
                    sequences.add(EditedMediaItemSequence(audioEdited))
                }
            }

            val composition = Composition.Builder(sequences)
                .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                .build()

            transformer!!.start(composition, outputFile.absolutePath)

            val pollRunnable = object : Runnable {
                override fun run() {
                    if (!cont.isActive) return
                    transformer?.let { t ->
                        val state = t.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(progressHolder.progress / 100f)
                        }
                    }
                    handler.postDelayed(this, 300)
                }
            }
            handler.postDelayed(pollRunnable, 300)

            cont.invokeOnCancellation {
                handler.removeCallbacksAndMessages(null)
                transformer?.cancel()
                outputFile.delete()
            }
        }
    } }

    /**
     * Konkateniert mehrere Video-Clips zu einer MP4-Datei.
     * Der erste Clip erhält Trim/Crop; die Extra-Clips werden vollständig angehängt.
     *
     * Übergänge werden als Fade-durch-Schwarz umgesetzt: an jeder aktiven Clip-Grenze
     * blendet der abgehende Clip in [transitionFadeMs]/2 nach Schwarz aus und der
     * folgende Clip in [transitionFadeMs]/2 aus Schwarz ein (Gesamtdauer je Grenze
     * ≈ [transitionFadeMs]).
     *
     * @param clipDurationsMs Dauer JEDES Clips in Reihenfolge [primär(getrimmt), extra0, extra1, …].
     * @param transitionFadeMs Übergangsdauer je Grenze; Index b = Grenze zwischen Clip b und b+1
     *   (0 = kein Übergang). Größe = Anzahl Clips − 1. Leer = keine Übergänge (alte Verkettung).
     */
    @OptIn(UnstableApi::class)
    suspend fun transcodeConcat(
        context: Context,
        primaryUri: Uri,
        primaryIsImage: Boolean = false,
        primaryTrimStartMs: Long = 0L,
        primaryTrimEndMs: Long = 0L,
        cropRect: FloatArray? = null,
        videoWidthPx: Int = 0,
        videoHeightPx: Int = 0,
        muteAudio: Boolean = false,
        videoAudioVolume: Float = 1f,
        audioOverlayUrls: List<AudioOverlay> = emptyList(),
        musicVolume: Float = 1f,
        extraClipUris: List<Uri>,
        extraClipIsImage: List<Boolean> = emptyList(),
        clipDurationsMs: List<Long> = emptyList(),
        transitionFadeMs: List<Long> = emptyList(),
        targetHeight: Int = 720,
        colorAdjustments: ColorAdjustments? = null,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean = transcodeMutex.withLock { withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            val progressHolder = ProgressHolder()
            var transformer: Transformer? = null

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    handler.removeCallbacksAndMessages(null)
                    onProgress(1f)
                    if (cont.isActive) cont.resume(true)
                }
                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    handler.removeCallbacksAndMessages(null)
                    if (cont.isActive) cont.resume(false)
                }
            }

            transformer = Transformer.Builder(context).addListener(listener).build()

            // Übergänge: Fade durch Schwarz. Übergang an Grenze b wird zur Hälfte am Ende
            // von Clip b (Ausblenden) und zur Hälfte am Anfang von Clip b+1 (Einblenden) gelegt.
            val hasTransitions = transitionFadeMs.any { it > 0L }
            val blackBitmap: Bitmap? = if (hasTransitions) createBlackBitmap(targetHeight) else null
            val nClips = 1 + extraClipUris.size
            // Halbe Übergangsdauer je Grenze
            fun fadeInWindowFor(clipIdx: Int): Long =
                if (clipIdx >= 1) transitionFadeMs.getOrElse(clipIdx - 1) { 0L } / 2 else 0L
            fun fadeOutWindowFor(clipIdx: Int): Long =
                if (clipIdx < nClips - 1) transitionFadeMs.getOrElse(clipIdx) { 0L } / 2 else 0L

            // Farb-/Ton-Anpassung wird auf ALLE Clips der Spur gelegt (einheitlicher Look).
            val colorEffect = colorAdjustments?.let { colorAdjustEffect(it) }

            // Video-Effekte Primär-Clip: optional Farb-/Ton + Crop + Ziel-Höhe
            val primaryVideoEffects = mutableListOf<androidx.media3.common.Effect>()
            colorEffect?.let { primaryVideoEffects += it }
            if (cropRect != null && cropRect.size == 4) {
                primaryVideoEffects += Crop(cropRect[0], cropRect[1], cropRect[2], cropRect[3])
            }
            primaryVideoEffects += Presentation.createForHeight(targetHeight)
            if (blackBitmap != null) {
                appendFadeOverlay(
                    primaryVideoEffects, blackBitmap,
                    clipDurationMs = clipDurationsMs.getOrElse(0) { 0L },
                    fadeInWindowMs = fadeInWindowFor(0),
                    fadeOutWindowMs = fadeOutWindowFor(0)
                )
            }

            val primaryClipping = if (primaryTrimStartMs > 0L || primaryTrimEndMs > 0L) {
                MediaItem.ClippingConfiguration.Builder()
                    .apply {
                        if (primaryTrimStartMs > 0L) setStartPositionMs(primaryTrimStartMs)
                        if (primaryTrimEndMs > 0L) setEndPositionMs(primaryTrimEndMs)
                    }.build()
            } else MediaItem.ClippingConfiguration.UNSET

            val videoAudioProcessors = if (!muteAudio && videoAudioVolume < 0.999f)
                listOf(createVolumeProcessor(videoAudioVolume)) else emptyList()
            val primaryItem = if (primaryIsImage) {
                // Standbild-Primär: feste Dauer, kein Trim/Ton
                buildImageEditedItem(
                    context, primaryUri,
                    clipDurationsMs.getOrElse(0) { 4000L },
                    primaryVideoEffects
                )
            } else {
                EditedMediaItem.Builder(
                    MediaItem.Builder().setUri(primaryUri).setClippingConfiguration(primaryClipping).build()
                )
                    .setEffects(Effects(videoAudioProcessors, primaryVideoEffects))
                    .apply { if (muteAudio) setRemoveAudio(true) }
                    .build()
            }

            // Extra Clips (keine Trim, max 720p) + optionaler Fade-Übergang
            val allItems = mutableListOf(primaryItem)
            extraClipUris.forEachIndexed { i, uri ->
                val clipIdx = i + 1
                val isImg = extraClipIsImage.getOrElse(i) { false }
                val extraVideoEffects = mutableListOf<androidx.media3.common.Effect>()
                colorEffect?.let { extraVideoEffects += it }
                extraVideoEffects += Presentation.createForHeight(targetHeight)
                if (blackBitmap != null) {
                    appendFadeOverlay(
                        extraVideoEffects, blackBitmap,
                        clipDurationMs = clipDurationsMs.getOrElse(clipIdx) { 0L },
                        fadeInWindowMs = fadeInWindowFor(clipIdx),
                        fadeOutWindowMs = fadeOutWindowFor(clipIdx)
                    )
                }
                val extraItem = if (isImg) {
                    buildImageEditedItem(
                        context, uri,
                        clipDurationsMs.getOrElse(clipIdx) { 4000L },
                        extraVideoEffects
                    )
                } else {
                    EditedMediaItem.Builder(MediaItem.fromUri(uri))
                        .setEffects(Effects(videoAudioProcessors, extraVideoEffects))
                        .apply { if (muteAudio) setRemoveAudio(true) }
                        .build()
                }
                allItems.add(extraItem)
            }

            val sequences = mutableListOf(EditedMediaItemSequence(allItems))

            // Audiospuren (nur für Primär-Clip-Zeitraum)
            val musicAudioProcessors = if (musicVolume < 0.999f)
                listOf(createVolumeProcessor(musicVolume)) else emptyList()
            audioOverlayUrls.forEach { overlay ->
                val audioClipping = MediaItem.ClippingConfiguration.Builder()
                    .apply {
                        if (overlay.startOffsetMs > 0L) setStartPositionMs(overlay.startOffsetMs)
                        if (overlay.clipDurationMs > 0L) setEndPositionMs(overlay.startOffsetMs + overlay.clipDurationMs)
                    }.build()
                val audioEdited = EditedMediaItem.Builder(
                    MediaItem.Builder().setUri(overlay.url).setClippingConfiguration(audioClipping).build()
                ).setRemoveVideo(true)
                    .apply { if (musicAudioProcessors.isNotEmpty()) setEffects(Effects(musicAudioProcessors, emptyList())) }
                    .build()
                // Timeline-Position: Stille voranstellen, damit der Song erst später einsetzt
                val silenceFile = createSilentWavFile(context, overlay.timelineStartMs)
                if (silenceFile != null) {
                    val silenceEdited = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(silenceFile)))
                        .setRemoveVideo(true)
                        .build()
                    sequences.add(EditedMediaItemSequence(listOf(silenceEdited, audioEdited)))
                } else {
                    sequences.add(EditedMediaItemSequence(audioEdited))
                }
            }

            val composition = Composition.Builder(sequences)
                .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                .build()

            transformer!!.start(composition, outputFile.absolutePath)

            val pollRunnable = object : Runnable {
                override fun run() {
                    if (!cont.isActive) return
                    transformer?.let { t ->
                        val state = t.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(progressHolder.progress / 100f)
                        }
                    }
                    handler.postDelayed(this, 300)
                }
            }
            handler.postDelayed(pollRunnable, 300)

            cont.invokeOnCancellation {
                handler.removeCallbacksAndMessages(null)
                transformer?.cancel()
                outputFile.delete()
            }
        }
    } }
}
