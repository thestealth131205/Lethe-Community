package com.securechat.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import com.securechat.app.data.local.ColorAdjustments
import com.securechat.app.data.local.SparkFilterId
import com.securechat.app.data.local.VideoTranscoder
import com.securechat.app.media.gif.GifEncoder
import com.securechat.app.media.gif.MedianCutQuantizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * FOSS/F-Droid-Video-/GIF-Verarbeitung via Media3 Transformer/Effects + reinem Kotlin-GIF-Encoder
 * ([GifEncoder]) – ersetzt FFmpegKit (proprietäres, vorgebautes `.aar`-Binär, playstore-only nach
 * Phase 1, siehe [com.securechat.app.media.FfmpegKitProvider]).
 *
 * Die Spark-Filter ([SparkFilterId]) wurden von FFmpeg-`-vf`-Filterstrings
 * ([com.securechat.app.data.local.SparkFilterEngine]) auf äquivalente [ColorAdjustments] abgebildet
 * und laufen über [VideoTranscoder.colorAdjustEffect] (bereits geteilte Media3-RgbMatrix-Logik,
 * ebenfalls im `foss`-Flavor nutzbar). Feinheiten wie Vignette (Cinematic), Filmkorn (Lo-Fi) oder
 * exakte Farbkurven (Retro/Warm/Cold) werden NICHT 1:1 nachgebildet (Media3 kennt weder
 * `vignette` noch `noise`/`curves`) – bewusste optische Annäherung statt exaktem FFmpeg-Ersatz.
 *
 * `createGif` nutzt keinen Media3-Baustein (Transformer kann keine animierten GIFs exportieren),
 * sondern extrahiert Frames (MediaMetadataRetriever bzw. BitmapFactory), quantisiert sie auf eine
 * gemeinsame Palette (Median-Cut) und schreibt sie über [GifEncoder].
 */
class Media3FfmpegProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : FfmpegProvider {

    private val transcodeMutex = Mutex()

    // ─── encodeSparkVideo ─────────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    override suspend fun encodeSparkVideo(
        req: SparkEncodeRequest,
        onProgress: (Float) -> Unit
    ): FfmpegResult {
        val success = transcodeMutex.withLock { withContext(Dispatchers.Main) {
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

                val videoEffects = listOfNotNull(
                    colorAdjustmentsForFilter(req.filterId)?.let { VideoTranscoder.colorAdjustEffect(it) },
                    cropEffectFor(req.cropRectNorm)
                )

                val musicFile = req.musicFile
                val targetDurationMs = when {
                    req.isImage -> req.imageDurationSec * 1000L
                    req.trimEndMs > req.trimStartMs -> req.trimEndMs - req.trimStartMs
                    req.videoDurationMs > 0L -> req.videoDurationMs
                    else -> 0L
                }

                val primaryItem: EditedMediaItem = if (req.isImage) {
                    buildImageEditedItem(
                        Uri.fromFile(File(req.inputVideoPath)),
                        req.imageDurationSec * 1000L,
                        videoEffects
                    )
                } else {
                    val clip = if (req.trimEndMs > req.trimStartMs) {
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(req.trimStartMs)
                            .setEndPositionMs(req.trimEndMs)
                            .build()
                    } else MediaItem.ClippingConfiguration.UNSET
                    val mediaItem = MediaItem.Builder()
                        .setUri(Uri.fromFile(File(req.inputVideoPath)))
                        .setClippingConfiguration(clip)
                        .build()
                    val audioProcessors = if (!req.isMuted && req.originalVolume < 0.999f)
                        listOf(createVolumeProcessor(req.originalVolume)) else emptyList()
                    EditedMediaItem.Builder(mediaItem)
                        .setEffects(Effects(audioProcessors, videoEffects))
                        .apply { if (req.isMuted) setRemoveAudio(true) }
                        .build()
                }

                val sequences = mutableListOf(EditedMediaItemSequence(primaryItem))

                if (musicFile != null) {
                    val offsetMs = (req.musicOffsetSec * 1000f).toLong().coerceAtLeast(0L)
                    val musicClipBuilder = MediaItem.ClippingConfiguration.Builder()
                    if (offsetMs > 0L) musicClipBuilder.setStartPositionMs(offsetMs)
                    if (targetDurationMs > 0L) musicClipBuilder.setEndPositionMs(offsetMs + targetDurationMs)
                    val musicItem = MediaItem.Builder()
                        .setUri(Uri.fromFile(musicFile))
                        .setClippingConfiguration(musicClipBuilder.build())
                        .build()
                    val musicProcessors = if (req.musicVolume < 0.999f)
                        listOf(createVolumeProcessor(req.musicVolume)) else emptyList()
                    val musicEdited = EditedMediaItem.Builder(musicItem)
                        .setRemoveVideo(true)
                        .apply { if (musicProcessors.isNotEmpty()) setEffects(Effects(musicProcessors, emptyList())) }
                        .build()
                    // isLooping=true: Musik-Sequenz wiederholt sich, bis die (nicht-loopende) Video-/Bild-
                    // Sequenz endet. Verhindert, dass bei Musik kürzer als die gewählte Bilddauer der Ton
                    // vorzeitig stoppt, während das Bild/Video weiterläuft (z.B. Ton nur 5s bei 12s-Spark).
                    sequences.add(EditedMediaItemSequence(listOf(musicEdited), /* isLooping= */ true))
                }

                val composition = Composition.Builder(sequences)
                    .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                    .build()

                transformer!!.start(composition, req.outputFile.absolutePath)

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
                    req.outputFile.delete()
                }
            }
        } }

        return if (success && req.outputFile.exists() && req.outputFile.length() > 0L) {
            FfmpegResult.Success(req.outputFile)
        } else {
            FfmpegResult.Error("Video-Verarbeitung fehlgeschlagen.")
        }
    }

    /**
     * Wandelt den normierten Crop-Ausschnitt (0f–1f, Top-Left-Ursprung) aus dem Spark-Editor in ein
     * Media3-[Crop]-Effect um. Media3 nutzt NDC-Koordinaten (-1f–1f, Y-Achse nach oben), daher die
     * Umrechnung `ndc = 2*frac - 1` (X) bzw. `ndc = 1 - 2*frac` (Y, invertiert wegen Top-Left→Y-up).
     */
    private fun cropEffectFor(cropRectNorm: FloatArray?): Crop? {
        if (cropRectNorm == null || cropRectNorm.size != 4) return null
        val (l, t, r, b) = cropRectNorm
        val left = 2f * l - 1f
        val right = 2f * r - 1f
        val top = 1f - 2f * t
        val bottom = 1f - 2f * b
        return Crop(left, right, bottom, top)
    }

    /** Bildet einen [SparkFilterId] auf äquivalente [ColorAdjustments] ab (Näherung, siehe Klassen-Doc). */
    private fun colorAdjustmentsForFilter(id: SparkFilterId): ColorAdjustments? = when (id) {
        SparkFilterId.NONE -> null
        SparkFilterId.VIVID -> ColorAdjustments(saturation = 0.6f, contrast = 0.15f, brightness = 0.04f)
        SparkFilterId.NOIR -> ColorAdjustments(saturation = -1f, contrast = 0.35f, brightness = -0.05f)
        SparkFilterId.CINEMATIC -> ColorAdjustments(contrast = 0.1f, saturation = -0.15f, temperature = -0.1f)
        SparkFilterId.LOFI -> ColorAdjustments(contrast = -0.1f, saturation = -0.3f, brightness = 0.05f)
        SparkFilterId.RETRO -> ColorAdjustments(saturation = -0.2f, contrast = 0.05f, temperature = 0.3f)
        SparkFilterId.WARM -> ColorAdjustments(saturation = 0.1f, brightness = 0.03f, temperature = 0.4f)
        SparkFilterId.COLD -> ColorAdjustments(saturation = -0.1f, contrast = 0.05f, temperature = -0.4f)
    }

    @OptIn(UnstableApi::class)
    private fun buildImageEditedItem(
        uri: Uri,
        durationMs: Long,
        videoEffects: List<androidx.media3.common.Effect>
    ): EditedMediaItem {
        val item = MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.IMAGE_JPEG).build()
        return EditedMediaItem.Builder(item)
            .setDurationUs(durationMs.coerceAtLeast(1L) * 1000L)
            .setFrameRate(30)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun createVolumeProcessor(volume: Float): ChannelMixingAudioProcessor =
        ChannelMixingAudioProcessor().also { proc ->
            for (ch in 1..8) {
                val coeffs = FloatArray(ch * ch) { i -> if (i / ch == i % ch) volume else 0f }
                proc.putChannelMixingMatrix(ChannelMixingMatrix(ch, ch, coeffs))
            }
        }

    // ─── overlayOnVideo ───────────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    override suspend fun overlayOnVideo(req: VideoOverlayRequest): FfmpegResult {
        val success = transcodeMutex.withLock { withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val handler = Handler(Looper.getMainLooper())
                var transformer: Transformer? = null

                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(true)
                    }
                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (cont.isActive) cont.resume(false)
                    }
                }
                transformer = Transformer.Builder(context).addListener(listener).build()

                val overlay = object : BitmapOverlay() {
                    override fun getBitmap(presentationTimeUs: Long): Bitmap = req.overlayBitmap
                }
                val overlayEffect = OverlayEffect(ImmutableList.of<TextureOverlay>(overlay))

                val clip = if (req.trimEndMs > req.trimStartMs && req.trimEndMs < Long.MAX_VALUE) {
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(req.trimStartMs)
                        .setEndPositionMs(req.trimEndMs)
                        .build()
                } else MediaItem.ClippingConfiguration.UNSET

                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.fromFile(req.inputFile))
                    .setClippingConfiguration(clip)
                    .build()
                val editedItem = EditedMediaItem.Builder(mediaItem)
                    .setEffects(Effects(emptyList(), listOf(overlayEffect)))
                    .build()

                val composition = Composition.Builder(EditedMediaItemSequence(editedItem))
                    .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                    .build()

                transformer!!.start(composition, req.outputFile.absolutePath)

                cont.invokeOnCancellation {
                    transformer?.cancel()
                    req.outputFile.delete()
                }
            }
        } }

        return if (success && req.outputFile.exists() && req.outputFile.length() > 0L) {
            FfmpegResult.Success(req.outputFile)
        } else {
            FfmpegResult.Error("Video-Overlay fehlgeschlagen.")
        }
    }

    // ─── createGif ────────────────────────────────────────────────────────────

    override suspend fun createGif(req: GifRequest): FfmpegResult = withContext(Dispatchers.Default) {
        try {
            val frames: List<Bitmap> = when (val src = req.source) {
                is GifSource.Video -> extractVideoFrames(src, req.outputWidth, req.outputHeight)
                is GifSource.Image -> listOf(
                    loadAndCropImage(src.inputFile, src.cropLeft, src.cropTop, src.cropRight, src.cropBottom, req.outputWidth, req.outputHeight)
                )
                is GifSource.ImageList -> {
                    if (src.files.size < 2) return@withContext FfmpegResult.Error("Mindestens 2 Bilder erforderlich.")
                    src.files.map { f -> loadAndScale(f, req.outputWidth, req.outputHeight) }
                }
            }
            if (frames.isEmpty()) return@withContext FfmpegResult.Error("Keine Frames extrahiert.")

            val composited = if (req.overlayBitmap != null) frames.map { applyOverlay(it, req.overlayBitmap) } else frames

            val delayMs = when (val src = req.source) {
                is GifSource.ImageList -> src.frameDelayMs
                else -> 100 // 10 fps, entspricht bisherigem FFmpeg-`fps=10`
            }

            // Gemeinsame Palette über alle Frames (jeder 4. Pixel, ausreichend für Histogramm).
            val histogram = HashMap<Int, Int>()
            val stride = 4
            for (bmp in composited) {
                val w = bmp.width; val h = bmp.height
                val pixels = IntArray(w * h)
                bmp.getPixels(pixels, 0, w, 0, 0, w, h)
                var i = 0
                while (i < pixels.size) {
                    val rgb = pixels[i] and 0xFFFFFF
                    histogram[rgb] = (histogram[rgb] ?: 0) + 1
                    i += stride
                }
            }
            val palette = MedianCutQuantizer.buildPalette(histogram, 256)

            FileOutputStream(req.outputFile).use { fos ->
                val encoder = GifEncoder(fos)
                encoder.writeHeader(req.outputWidth, req.outputHeight)
                for (bmp in composited) {
                    val w = bmp.width; val h = bmp.height
                    val pixels = IntArray(w * h)
                    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
                    val indices = ByteArray(pixels.size)
                    for (i in pixels.indices) {
                        indices[i] = MedianCutQuantizer.nearestIndex(pixels[i] and 0xFFFFFF, palette).toByte()
                    }
                    encoder.writeFrame(indices, palette, w, h, (delayMs / 10).coerceAtLeast(1))
                }
                encoder.writeTrailer()
            }

            if (req.outputFile.exists() && req.outputFile.length() > 0L) {
                FfmpegResult.Success(req.outputFile)
            } else {
                FfmpegResult.Error("GIF-Erstellung fehlgeschlagen.")
            }
        } catch (e: Exception) {
            FfmpegResult.Error(e.message ?: "GIF-Erstellung fehlgeschlagen.")
        }
    }

    private fun extractVideoFrames(src: GifSource.Video, outW: Int, outH: Int): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(src.inputFile.absolutePath)
            val frames = mutableListOf<Bitmap>()
            val frameIntervalUs = 100_000L // 10 fps
            val startUs = src.startMs * 1000L
            val endUs = startUs + src.durationMs * 1000L
            var t = startUs
            while (t < endUs) {
                val frame = retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    frames += cropAndScale(frame, src.cropLeft, src.cropTop, src.cropRight, src.cropBottom, outW, outH)
                }
                t += frameIntervalUs
            }
            frames
        } finally {
            retriever.release()
        }
    }

    private fun cropAndScale(bitmap: Bitmap, cl: Float, ct: Float, cr: Float, cb: Float, outW: Int, outH: Int): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        val x = (w * cl).toInt().coerceIn(0, w - 1)
        val y = (h * ct).toInt().coerceIn(0, h - 1)
        val cw = (w * (cr - cl)).toInt().coerceAtLeast(1).coerceAtMost(w - x)
        val ch = (h * (cb - ct)).toInt().coerceAtLeast(1).coerceAtMost(h - y)
        val cropped = Bitmap.createBitmap(bitmap, x, y, cw, ch)
        return Bitmap.createScaledBitmap(cropped, outW, outH, true)
    }

    private fun loadAndCropImage(file: File, cl: Float, ct: Float, cr: Float, cb: Float, outW: Int, outH: Int): Bitmap {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IllegalArgumentException("Bild konnte nicht geladen werden: ${file.name}")
        return cropAndScale(bitmap, cl, ct, cr, cb, outW, outH)
    }

    private fun loadAndScale(file: File, outW: Int, outH: Int): Bitmap {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IllegalArgumentException("Bild konnte nicht geladen werden: ${file.name}")
        return Bitmap.createScaledBitmap(bitmap, outW, outH, true)
    }

    private fun applyOverlay(frame: Bitmap, overlay: Bitmap): Bitmap {
        val mutable = frame.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(mutable).drawBitmap(overlay, 0f, 0f, null)
        return mutable
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FfmpegProviderModule {
    @Binds
    @Singleton
    abstract fun bindFfmpegProvider(impl: Media3FfmpegProvider): FfmpegProvider
}
