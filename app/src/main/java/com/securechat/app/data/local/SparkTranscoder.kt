package com.securechat.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Konvertiert ein lokales Video für den Spark-Upload.
 *
 * Ablauf:
 *   Phase 1 ( 0 % →  80 %): Media3 Transformer → 720p H.264/AAC MP4 (Hardware-beschleunigt).
 *   Phase 2 (80 % →  90 %): Thumbnail aus Frame 0 via MediaMetadataRetriever (JPEG, Qualität 85).
 *   Phase 3 (90 % → 100 %): Umbenennung zu video.mp4.
 *
 * Die HLS-Segmentierung (MP4 → index.m3u8 + index000.ts, …) übernimmt der Server via FFmpeg,
 * da FFmpeg auf Android keine öffentlich zugängliche Maven-Abhängigkeit besitzt.
 *
 * Ausgabe-Verzeichnis: context.cacheDir/spark_<UUID>/
 *   video.mp4, thumbnail.jpg
 */
object SparkTranscoder {

    // ─────────────────────────────────────────────────────────────────────────
    // Öffentliche Datenklassen
    // ─────────────────────────────────────────────────────────────────────────

    data class HlsOutput(
        val outputDir: File,
        val videoFile: File,       // transkodiertes MP4 – Server erstellt HLS-Segmente daraus
        val thumbnailFile: File
    )

    sealed class TranscodeResult {
        data class Success(val output: HlsOutput) : TranscodeResult()
        data class Error(val message: String) : TranscodeResult()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Haupt-Einstiegspunkt
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Startet den vollständigen Transcode-Prozess als Suspend-Funktion.
     *
     * @param context    Android-Context (für cacheDir + Media3 benötigt).
     * @param inputUri   Uri des Quell-Videos (content:// oder file://).
     * @param onProgress Fortschritts-Callback (0f … 1f).
     */
    @OptIn(UnstableApi::class)
    suspend fun transcode(
        context: Context,
        inputUri: Uri,
        onProgress: (Float) -> Unit
    ): TranscodeResult {
        val uuid = UUID.randomUUID().toString()
        val outputDir = File(context.cacheDir, "spark_$uuid").also { it.mkdirs() }
        val tempMp4 = File(outputDir, "temp_transcoded.mp4")

        return try {
            // ── Phase 1: Media3 Transformer auf Main-Thread (0 % → 80 %) ─────
            val transcodeOk = transcodeToMp4(context, inputUri, tempMp4) { p ->
                onProgress(p * 0.80f)
            }
            if (!transcodeOk) {
                outputDir.deleteRecursively()
                return TranscodeResult.Error("Transkodierung fehlgeschlagen.")
            }
            onProgress(0.80f)

            // ── Phase 2: Thumbnail-Extraktion (IO, 80 % → 90 %) ─────────────
            val thumbFile = withContext(Dispatchers.IO) {
                extractThumbnail(tempMp4, outputDir)
            }
            onProgress(0.90f)

            // ── Phase 3: Umbenennen temp → video.mp4 (IO, 90 % → 100 %) ─────
            val videoFile = withContext(Dispatchers.IO) {
                val dest = File(outputDir, "video.mp4")
                Files.move(tempMp4.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
                dest
            }
            onProgress(1.0f)

            TranscodeResult.Success(HlsOutput(outputDir, videoFile, thumbFile))

        } catch (e: Exception) {
            Timber.tag("LETHE_SPARK").e("SparkTranscoder Fehler: ${e.message}")
            outputDir.deleteRecursively()
            TranscodeResult.Error("Fehler: ${e.message ?: "Unbekannt"}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 1: Media3 Transformer – 720p H.264 / AAC MP4
    // ─────────────────────────────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    private suspend fun transcodeToMp4(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.Main) {
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
                    Timber.tag("LETHE_SPARK").e("Media3-Fehler: ${exportException.message}")
                    if (cont.isActive) cont.resume(false)
                }
            }

            transformer = Transformer.Builder(context)
                .addListener(listener)
                .build()

            val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
                .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(720))))
                .build()

            transformer!!.start(editedItem, outputFile.absolutePath)

            // Fortschritt alle 300 ms abfragen
            val pollRunnable = object : Runnable {
                override fun run() {
                    if (!cont.isActive) return
                    transformer?.let { t ->
                        if (t.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
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
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 2: Thumbnail – MediaMetadataRetriever, Frame 0, JPEG 85 %
    // ─────────────────────────────────────────────────────────────────────────

    private fun extractThumbnail(inputFile: File, outputDir: File): File {
        val thumbFile = File(outputDir, "thumbnail.jpg")
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(inputFile.absolutePath)
            val bitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) {
                FileOutputStream(thumbFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                bitmap.recycle()
            } else {
                // Kein Frame verfügbar → leere Datei damit Upload nicht fehlschlägt
                thumbFile.createNewFile()
            }
        } catch (e: Exception) {
            Timber.tag("LETHE_SPARK").w("Thumbnail fehlgeschlagen: ${e.message}")
            if (!thumbFile.exists()) thumbFile.createNewFile()
        } finally {
            retriever.release()
        }
        return thumbFile
    }
}
