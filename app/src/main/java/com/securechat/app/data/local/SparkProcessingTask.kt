package com.securechat.app.data.local

import android.content.Context
import android.net.Uri
import com.securechat.app.media.FfmpegProvider
import com.securechat.app.media.FfmpegResult
import com.securechat.app.media.SparkEncodeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// SparkProcessingTask
//
// Finalisiert ein Spark-Video mit optionalem Dual-Audio-Muxing und Video-Filter.
//
// Pipeline:
//   1. (Optional) Download der Musik-MP3 von Pixabay.
//   2. FFmpeg-Command aufbauen (Filter + Audio).
//   3. FFmpeg synchron/asynchron ausführen → optimiertes MP4.
//   4. Rückgabe des Output-Pfades für den Upload via uploadSparkHls().
//
// FFmpeg Audio-Modi:
//   - isMuted=true  → Original-Audio entfernen, nur Musik (-an + Musik-Stream).
//   - isMuted=false → amix: Original + Musik mischen.
//   - musicUrl=null → Nur Filter, Audio unverändert.
// ─────────────────────────────────────────────────────────────────────────────

object SparkProcessingTask {

    /**
     * Fortschritts-Phasen für den Callback (0f–1f):
     *   0.00 – 0.25 : MP3-Download
     *   0.25 – 0.90 : FFmpeg-Verarbeitung
     *   0.90 – 1.00 : Cleanup/Rename
     */

    // ─── Ergebnis-Sealed-Class ────────────────────────────────────────────────

    sealed class ProcessResult {
        /** Verarbeitung erfolgreich. [outputUri] zeigt auf die fertige MP4-Datei. */
        data class Success(val outputUri: Uri) : ProcessResult()
        /** Verarbeitung fehlgeschlagen. [message] enthält die Fehlerbeschreibung. */
        data class Error(val message: String) : ProcessResult()
    }

    // ─── Haupt-Einstiegspunkt ─────────────────────────────────────────────────

    /**
     * Führt die vollständige Spark-Verarbeitung aus.
     *
     * @param context          Android Context (für cacheDir und FileProvider).
     * @param inputVideoPath   Absoluter Pfad zum trankodierten MP4 (Ausgabe von SparkTranscoder).
     * @param musicDownloadUrl URL der Pixabay MP3 (null = kein Musik-Overlay).
     * @param isMuted          True = Original-Audio stummschalten (nur Musik). False = Mix.
     * @param filterId         Gewählter [SparkFilterId] (NONE = kein Video-Filter).
     * @param musicVolume      Lautstärke der Musik-Spur (0f–1f, Standard 0.6).
     * @param originalVolume   Lautstärke der Originalspur (0f–1f, Standard 1.0).
     * @param onProgress       Fortschritts-Callback (0f–1f) auf beliebigem Thread.
     * @return                 [ProcessResult.Success] mit Uri oder [ProcessResult.Error].
     */
    suspend fun process(
        context: Context,
        ffmpegProvider: FfmpegProvider,
        inputVideoPath: String,
        musicDownloadUrl: String? = null,
        isMuted: Boolean = false,
        filterId: SparkFilterId = SparkFilterId.NONE,
        musicVolume: Float = 0.6f,
        originalVolume: Float = 1.0f,
        videoDurationMs: Long = 0L,
        isImage: Boolean = false,
        imageDurationSec: Int = 5,
        trimStartMs: Long = 0L,
        trimEndMs: Long = 0L,
        musicOffsetSec: Float = 0f,
        onProgress: (Float) -> Unit = {}
    ): ProcessResult = withContext(Dispatchers.IO) {

        val sessionId = UUID.randomUUID().toString().take(8)
        val workDir = File(context.cacheDir, "spark_process_$sessionId").also { it.mkdirs() }
        Timber.tag("LETHE_SPARK").d("SparkProcessingTask gestartet. sessionId=$sessionId")

        try {
            // ── Phase 1: MP3 bereitstellen (0% → 25%) ─────────────────────────
            // Wenn die URL lokal ist (kein http/https), Datei direkt verwenden.
            val musicFile: File? = when {
                musicDownloadUrl == null -> null
                musicDownloadUrl.startsWith("http://") || musicDownloadUrl.startsWith("https://") -> {
                    // Fernzugriff: MP3 herunterladen
                    onProgress(0.05f)
                    downloadMp3(musicDownloadUrl, workDir) { dlProgress ->
                        onProgress(0.05f + dlProgress * 0.20f)
                    }
                }
                else -> {
                    // Lokaler Pfad: Datei direkt verwenden (kein Download nötig)
                    onProgress(0.20f)
                    File(musicDownloadUrl).takeIf { it.exists() }.also { f ->
                        if (f == null) Timber.tag("LETHE_SPARK").e("Lokale MP3 nicht gefunden: $musicDownloadUrl")
                    }
                }
            }
            onProgress(0.25f)

            // ── Phase 2+3: Video-Verarbeitung via FfmpegProvider (25% → 90%) ───
            val outputFile = File(workDir, "spark_final_$sessionId.mp4")
            val req = SparkEncodeRequest(
                inputVideoPath = inputVideoPath,
                musicFile = musicFile,
                isMuted = isMuted,
                filterId = filterId,
                musicVolume = musicVolume,
                originalVolume = originalVolume,
                videoDurationMs = videoDurationMs,
                isImage = isImage,
                imageDurationSec = imageDurationSec,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                musicOffsetSec = musicOffsetSec,
                outputFile = outputFile
            )
            onProgress(0.30f)
            val encodeResult = ffmpegProvider.encodeSparkVideo(req) { sessionProgress ->
                onProgress(0.30f + sessionProgress * 0.60f) // 30%–90%
            }

            if (encodeResult is FfmpegResult.Error) {
                workDir.deleteRecursively()
                return@withContext ProcessResult.Error(
                    "FFmpeg-Verarbeitung fehlgeschlagen. Bitte versuche es erneut."
                )
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                workDir.deleteRecursively()
                return@withContext ProcessResult.Error(
                    "Ausgabedatei nicht gefunden nach FFmpeg-Ausführung."
                )
            }

            onProgress(1.0f)
            Timber.tag("LETHE_SPARK").d("SparkProcessingTask erfolgreich: ${outputFile.absolutePath}")
            ProcessResult.Success(Uri.fromFile(outputFile))

        } catch (e: Exception) {
            Timber.tag("LETHE_SPARK").e("SparkProcessingTask Ausnahme: ${e.message}")
            workDir.deleteRecursively()
            ProcessResult.Error("Fehler: ${e.message ?: "Unbekannt"}")
        }
    }

    // ─── Privat: MP3-Download ─────────────────────────────────────────────────

    /**
     * Lädt eine MP3-Datei von einer URL herunter.
     * Schreibt sie in [workDir]/music_TIMESTAMP.mp3.
     *
     * @param url        Vollständige HTTP/HTTPS URL der MP3.
     * @param workDir    Zielverzeichnis im Cache.
     * @param onProgress Download-Fortschritts-Callback (0f–1f).
     * @return           Die heruntergeladene [File] oder null bei Fehler.
     */
    private fun downloadMp3(
        url: String,
        workDir: File,
        onProgress: (Float) -> Unit
    ): File? {
        return try {
            val client = OkHttpClient.Builder().build()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Timber.tag("LETHE_SPARK").e("MP3-Download fehlgeschlagen: ${response.code}")
                return null
            }

            val body = response.body ?: return null
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: 1L
            val outputFile = File(workDir, "music_${System.currentTimeMillis()}.mp3")

            FileOutputStream(outputFile).use { out ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalRead = 0L
                val inputStream = body.byteStream()
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    onProgress(totalRead.toFloat() / totalBytes.toFloat())
                }
            }

            Timber.tag("LETHE_SPARK").d("MP3 heruntergeladen: ${outputFile.absolutePath}")
            outputFile

        } catch (e: Exception) {
            Timber.tag("LETHE_SPARK").e("MP3-Download Ausnahme: ${e.message}")
            null
        }
    }
}
