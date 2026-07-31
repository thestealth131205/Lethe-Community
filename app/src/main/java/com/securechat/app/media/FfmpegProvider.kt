package com.securechat.app.media

import android.graphics.Bitmap
import com.securechat.app.data.local.SparkFilterId
import java.io.File

/**
 * Transport-unabhängige Video-/GIF-Verarbeitung, genutzt für Spark-Video-Finalisierung
 * ([com.securechat.app.data.local.SparkProcessingTask]), GIF-Sticker-Erstellung
 * ([com.securechat.app.ui.screens.StickerCreatorSheet]) und Status-Video-Overlays
 * ([com.securechat.app.ui.screens.StatusCreationScreen]).
 *
 * Der `playstore`-Flavor nutzt FFmpegKit ([com.securechat.app.media.FfmpegKitProvider],
 * bisheriges Verhalten unverändert). Der `foss`/F-Droid-Flavor nutzt Media3 Transformer/Effects
 * + einen reinen Java-GIF-Encoder ohne vorgebautes Binär-Blob
 * ([com.securechat.app.media.Media3FfmpegProvider]).
 *
 * Alle Methoden sind suspend und dürfen von einem Hintergrund-Thread aufgerufen werden
 * (wie bisher die direkten FFmpegKit-Aufrufe via Dispatchers.IO).
 */
interface FfmpegProvider {

    /**
     * Finalisiert ein Spark-Video: optionaler Video-Filter ([SparkFilterId]), optionaler Trim,
     * Bild→Video-Loop (für Bild-Sparks) und optionales Musik-Overlay (Original stummschalten
     * oder mit `amix` mischen).
     */
    suspend fun encodeSparkVideo(req: SparkEncodeRequest, onProgress: (Float) -> Unit): FfmpegResult

    /** Erstellt ein animiertes GIF aus einer Video- oder Bilderlisten-Quelle, optional mit Overlay-Bitmap. */
    suspend fun createGif(req: GifRequest): FfmpegResult

    /** Schneidet ein Video (optional) zu und legt ein Overlay-Bitmap darüber; Audio bleibt unverändert. */
    suspend fun overlayOnVideo(req: VideoOverlayRequest): FfmpegResult
}

/** Ergebnis einer [FfmpegProvider]-Operation. */
sealed class FfmpegResult {
    /** Verarbeitung erfolgreich. [outputFile] zeigt auf die fertige Datei. */
    data class Success(val outputFile: File) : FfmpegResult()
    /** Verarbeitung fehlgeschlagen. [message] enthält die Fehlerbeschreibung. */
    data class Error(val message: String) : FfmpegResult()
}

/**
 * Anfrage für [FfmpegProvider.encodeSparkVideo].
 *
 * @param musicFile Bereits heruntergeladene/lokale MP3-Datei (null = kein Musik-Overlay).
 *                   Der Download selbst erfolgt außerhalb des Providers.
 */
data class SparkEncodeRequest(
    val inputVideoPath: String,
    val musicFile: File?,
    val isMuted: Boolean = false,
    val filterId: SparkFilterId = SparkFilterId.NONE,
    val musicVolume: Float = 0.6f,
    val originalVolume: Float = 1.0f,
    val videoDurationMs: Long = 0L,
    val isImage: Boolean = false,
    val imageDurationSec: Int = 5,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val musicOffsetSec: Float = 0f,
    val outputFile: File
)

/** Quelle für [GifRequest]: ein (ggf. gecroppter) Video-Ausschnitt, ein Einzelbild oder eine Bilderliste. */
sealed class GifSource {
    data class Video(
        val inputFile: File,
        val startMs: Long,
        val durationMs: Long,
        val cropLeft: Float = 0f,
        val cropTop: Float = 0f,
        val cropRight: Float = 1f,
        val cropBottom: Float = 1f
    ) : GifSource()

    /** Einzelbild → statisches 1-Frame-GIF (kein Palette-Pass nötig). */
    data class Image(
        val inputFile: File,
        val cropLeft: Float = 0f,
        val cropTop: Float = 0f,
        val cropRight: Float = 1f,
        val cropBottom: Float = 1f
    ) : GifSource()

    data class ImageList(
        val files: List<File>,
        val frameDelayMs: Int
    ) : GifSource()
}

/** Anfrage für [FfmpegProvider.createGif]. */
data class GifRequest(
    val source: GifSource,
    val outputWidth: Int = 320,
    val outputHeight: Int = 240,
    val overlayBitmap: Bitmap? = null,
    val outputFile: File
)

/** Anfrage für [FfmpegProvider.overlayOnVideo]. */
data class VideoOverlayRequest(
    val inputFile: File,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = Long.MAX_VALUE,
    val overlayBitmap: Bitmap,
    val outputFile: File
)
