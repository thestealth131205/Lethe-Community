package com.securechat.app.data.local

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume

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

            // ── Phase 2: FFmpeg-Command bauen ─────────────────────────────────
            val outputFile = File(workDir, "spark_final_$sessionId.mp4")
            val command = buildFfmpegCommand(
                inputVideoPath = inputVideoPath,
                musicFile = musicFile,
                isMuted = isMuted,
                filterId = filterId,
                musicVolume = musicVolume,
                originalVolume = originalVolume,
                isImage = isImage,
                imageDurationSec = imageDurationSec,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                musicOffsetSec = musicOffsetSec,
                outputPath = outputFile.absolutePath
            )
            Timber.tag("LETHE_SPARK").d("FFmpeg-Command: $command")

            // ── Phase 3: FFmpeg ausführen (25% → 90%) ─────────────────────────
            onProgress(0.30f)
            val ffmpegSuccess = executeFfmpeg(command, videoDurationMs) { sessionProgress ->
                onProgress(0.30f + sessionProgress * 0.60f) // 30%–90%
            }

            if (!ffmpegSuccess) {
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

    // ─── Privat: FFmpeg-Command-Builder ──────────────────────────────────────

    /**
     * Erstellt den vollständigen FFmpeg-Befehlsstring.
     *
     * Strategie:
     *   A) Nur Filter, kein Musik:  -i video -vf FILTER -c:a copy out.mp4
     *   B) Musik, Original stumm:   -i video -i musik -vf FILTER -map 0:v:0 -map 1:a:0 -shortest out.mp4
     *   C) Musik + Original-Mix:    -i video -i musik -filter_complex "[0:a]volume=V1[a0];[1:a]volume=V2[a1];[a0][a1]amix=inputs=2:duration=first[aout]" -map 0:v -map [aout] -vf FILTER out.mp4
     *
     * @param inputVideoPath  Absoluter Eingabe-Videopfad.
     * @param musicFile       Heruntergeladene MP3-Datei (null = kein Musik-Overlay).
     * @param isMuted         Original-Audiospur stummschalten.
     * @param filterId        Gewählter [SparkFilterId].
     * @param musicVolume     Lautstärke der Musik-Spur (amix).
     * @param originalVolume  Lautstärke der Originalspur (amix).
     * @param outputPath      Absoluter Ausgabepfad.
     * @return                FFmpegKit-kompatibler Befehlsstring.
     */
    private fun buildFfmpegCommand(
        inputVideoPath: String,
        musicFile: File?,
        isMuted: Boolean,
        filterId: SparkFilterId,
        musicVolume: Float,
        originalVolume: Float,
        isImage: Boolean = false,
        imageDurationSec: Int = 5,
        trimStartMs: Long = 0L,
        trimEndMs: Long = 0L,
        musicOffsetSec: Float = 0f,
        outputPath: String
    ): String {
        val baseFilter = SparkFilterEngine.ffmpegFilterFor(filterId)

        // Für Bilder: zusätzlicher Skalier-Filter um ungerade Dimensionen zu vermeiden
        val fullFilter = if (isImage) {
            val scaleFilter = "scale=trunc(iw/2)*2:trunc(ih/2)*2,format=yuv420p"
            if (baseFilter.isNotBlank()) "$scaleFilter,$baseFilter" else scaleFilter
        } else baseFilter

        val vfPart = if (fullFilter.isNotBlank()) "-vf \"$fullFilter\"" else ""

        // Trim-Optionen (nur für Video, wenn Trim gesetzt)
        val trimStart = if (!isImage && trimStartMs > 0L) "-ss ${trimStartMs / 1000.0}" else ""
        val trimDuration = if (!isImage && trimEndMs > trimStartMs) {
            val durSec = (trimEndMs - trimStartMs) / 1000.0
            "-t $durSec"
        } else ""

        // Musik-Offset (vor Musik-Input)
        val musicSsFlag = if (musicOffsetSec > 0f) "-ss ${musicOffsetSec}" else ""

        return when {

            // ── Fall A: Kein Musik-Overlay ─────────────────────────────────────
            musicFile == null -> {
                if (isImage) {
                    // Bild → Video
                    buildString {
                        append("-loop 1 -i \"$inputVideoPath\" ")
                        append("-t $imageDurationSec ")
                        if (vfPart.isNotBlank()) append("$vfPart ")
                        append("-c:v libx264 -preset fast -crf 23 ")
                        append("-an -movflags +faststart ")
                        append("\"$outputPath\"")
                    }
                } else {
                    val audioFlags = if (isMuted) "-an" else "-c:a copy"
                    "$trimStart $trimDuration -i \"$inputVideoPath\" $vfPart $audioFlags -c:v libx264 -preset fast -crf 23 -movflags +faststart \"$outputPath\""
                }
            }

            // ── Fall B: Musik, Original-Audio stumm (oder Bild ohne Audio) ─────
            isMuted || isImage -> {
                buildString {
                    if (isImage) {
                        append("-loop 1 -i \"$inputVideoPath\" ")
                        append("-t $imageDurationSec ")
                    } else {
                        if (trimStart.isNotBlank()) append("$trimStart ")
                        if (trimDuration.isNotBlank()) append("$trimDuration ")
                        append("-i \"$inputVideoPath\" ")
                    }
                    if (musicSsFlag.isNotBlank()) append("$musicSsFlag ")
                    append("-i \"${musicFile.absolutePath}\" ")
                    if (vfPart.isNotBlank()) append("$vfPart ")
                    append("-map 0:v:0 -map 1:a:0 ")
                    append("-c:v libx264 -preset fast -crf 23 ")
                    append("-c:a aac -b:a 192k ")
                    append("-shortest ")
                    append("-movflags +faststart ")
                    append("\"$outputPath\"")
                }
            }

            // ── Fall C: Musik + Original-Audio mischen (amix) ─────────────────
            else -> {
                val ov = "%.2f".format(originalVolume)
                val mv = "%.2f".format(musicVolume)
                val filterComplex =
                    "[0:a]volume=${ov}[a0];" +
                    "[1:a]volume=${mv}[a1];" +
                    "[a0][a1]amix=inputs=2:duration=first:dropout_transition=2[aout]"

                buildString {
                    if (isImage) {
                        append("-loop 1 -i \"$inputVideoPath\" ")
                        append("-t $imageDurationSec ")
                    } else {
                        if (trimStart.isNotBlank()) append("$trimStart ")
                        if (trimDuration.isNotBlank()) append("$trimDuration ")
                        append("-i \"$inputVideoPath\" ")
                    }
                    if (musicSsFlag.isNotBlank()) append("$musicSsFlag ")
                    append("-i \"${musicFile.absolutePath}\" ")
                    append("-filter_complex \"$filterComplex\" ")
                    if (vfPart.isNotBlank()) append("$vfPart ")
                    append("-map 0:v -map [aout] ")
                    append("-c:v libx264 -preset fast -crf 23 ")
                    append("-c:a aac -b:a 192k ")
                    append("-movflags +faststart ")
                    append("\"$outputPath\"")
                }
            }
        }
    }

    // ─── Privat: FFmpeg ausführen ─────────────────────────────────────────────

    /**
     * Führt einen FFmpeg-Befehl via FFmpegKit aus.
     * Gibt true zurück, wenn der Return-Code 0 (Erfolg) ist.
     *
     * @param command    Vollständiger FFmpeg-Befehlsstring.
     * @param onProgress Fortschritts-Callback; FFmpegKit meldet Fortschritt via
     *                   Statistik-Callbacks nicht linear, daher Pseudo-Fortschritt.
     * @return           True = Erfolg, False = Fehler.
     */
    private suspend fun executeFfmpeg(
        command: String,
        videoDurationMs: Long = 0L,
        onProgress: (Float) -> Unit
    ): Boolean = suspendCancellableCoroutine { cont ->

        // Wenn die Video-Gesamtdauer bekannt ist: exakte Fortschrittsberechnung.
        // Sonst: Pseudo-Sättigungs-Fortschritt (0.02f pro Statistik-Callback).
        var progressSimulation = 0f

        val session = FFmpegKit.executeAsync(
            command,
            { completedSession ->
                // Completion-Callback (auf FFmpegKit-Thread)
                val returnCode = completedSession.returnCode
                val success = ReturnCode.isSuccess(returnCode)
                if (!success) {
                    Timber.tag("LETHE_SPARK").e(
                        "FFmpeg fehlgeschlagen: Code=${returnCode.value}, " +
                        "Output=${completedSession.allLogsAsString?.takeLast(500)}"
                    )
                }
                onProgress(1f)
                if (cont.isActive) cont.resume(success)
            },
            { log ->
                // Log-Callback (auf FFmpegKit-Thread) – nur Timber-Logging
                Timber.tag("LETHE_SPARK").v("FFmpeg: ${log.message?.trimEnd()}")
            },
            { statistics ->
                // Statistik-Callback: exakter Fortschritt wenn Dauer bekannt, sonst Pseudo
                val processedMs = statistics.time.toFloat()
                if (processedMs > 0) {
                    val p = if (videoDurationMs > 0L) {
                        // Echte Prozentberechnung: verarbeitete ms / Gesamtdauer
                        (processedMs / videoDurationMs.toFloat()).coerceIn(0f, 0.98f)
                    } else {
                        // Fallback: Pseudo-Sättigung
                        progressSimulation = minOf(progressSimulation + 0.02f, 0.95f)
                        progressSimulation
                    }
                    onProgress(p)
                }
            }
        )

        cont.invokeOnCancellation {
            FFmpegKit.cancel(session.sessionId)
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
