package com.securechat.app.media

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.securechat.app.data.local.SparkFilterEngine
import com.securechat.app.data.local.SparkFilterId
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Playstore-Video-/GIF-Verarbeitung via FFmpegKit – identisches Verhalten wie vor der
 * Provider-Umstellung, nur hinter das [FfmpegProvider]-Interface gezogen (1:1-Extract-Refactor
 * aus `SparkProcessingTask`, `StickerCreatorSheet` und `StatusCreationScreen`).
 */
class FfmpegKitProvider @Inject constructor() : FfmpegProvider {

    companion object {
        private const val TAG = "LETHE_FFMPEG"
    }

    // ─── encodeSparkVideo ─────────────────────────────────────────────────────

    override suspend fun encodeSparkVideo(
        req: SparkEncodeRequest,
        onProgress: (Float) -> Unit
    ): FfmpegResult {
        val command = buildSparkCommand(req)
        Timber.tag(TAG).d("Spark FFmpeg-Command: $command")
        val success = executeFfmpegAsync(command, req.videoDurationMs, onProgress)
        return if (success && req.outputFile.exists() && req.outputFile.length() > 0L) {
            FfmpegResult.Success(req.outputFile)
        } else {
            FfmpegResult.Error("FFmpeg-Verarbeitung fehlgeschlagen.")
        }
    }

    /**
     * Baut den FFmpeg-Befehlsstring für die Spark-Finalisierung.
     *
     * Strategie:
     *   A) Nur Filter, kein Musik:  -i video -vf FILTER -c:a copy out.mp4
     *   B) Musik, Original stumm:   -i video -i musik -vf FILTER -map 0:v:0 -map 1:a:0 -shortest out.mp4
     *   C) Musik + Original-Mix:    -i video -i musik -filter_complex "amix" -map 0:v -map [aout] -vf FILTER out.mp4
     */
    private fun buildSparkCommand(req: SparkEncodeRequest): String {
        val baseFilter = SparkFilterEngine.ffmpegFilterFor(req.filterId)

        val fullFilter = if (req.isImage) {
            val scaleFilter = "scale=trunc(iw/2)*2:trunc(ih/2)*2,format=yuv420p"
            if (baseFilter.isNotBlank()) "$scaleFilter,$baseFilter" else scaleFilter
        } else baseFilter

        val vfPart = if (fullFilter.isNotBlank()) "-vf \"$fullFilter\"" else ""

        val trimStart = if (!req.isImage && req.trimStartMs > 0L) "-ss ${req.trimStartMs / 1000.0}" else ""
        val trimDuration = if (!req.isImage && req.trimEndMs > req.trimStartMs) {
            val durSec = (req.trimEndMs - req.trimStartMs) / 1000.0
            "-t $durSec"
        } else ""

        val musicSsFlag = if (req.musicOffsetSec > 0f) "-ss ${req.musicOffsetSec}" else ""
        val outputPath = req.outputFile.absolutePath
        val musicFile = req.musicFile

        return when {
            // ── Fall A: Kein Musik-Overlay ─────────────────────────────────────
            musicFile == null -> {
                if (req.isImage) {
                    buildString {
                        append("-loop 1 -i \"${req.inputVideoPath}\" ")
                        append("-t ${req.imageDurationSec} ")
                        if (vfPart.isNotBlank()) append("$vfPart ")
                        append("-c:v libx264 -preset fast -crf 23 ")
                        append("-an -movflags +faststart ")
                        append("\"$outputPath\"")
                    }
                } else {
                    val audioFlags = if (req.isMuted) "-an" else "-c:a copy"
                    "$trimStart $trimDuration -i \"${req.inputVideoPath}\" $vfPart $audioFlags -c:v libx264 -preset fast -crf 23 -movflags +faststart \"$outputPath\""
                }
            }

            // ── Fall B: Musik, Original-Audio stumm (oder Bild ohne Audio) ─────
            req.isMuted || req.isImage -> {
                buildString {
                    if (req.isImage) {
                        append("-loop 1 -i \"${req.inputVideoPath}\" ")
                        append("-t ${req.imageDurationSec} ")
                    } else {
                        if (trimStart.isNotBlank()) append("$trimStart ")
                        if (trimDuration.isNotBlank()) append("$trimDuration ")
                        append("-i \"${req.inputVideoPath}\" ")
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
                val ov = "%.2f".format(req.originalVolume)
                val mv = "%.2f".format(req.musicVolume)
                val filterComplex =
                    "[0:a]volume=${ov}[a0];" +
                    "[1:a]volume=${mv}[a1];" +
                    "[a0][a1]amix=inputs=2:duration=first:dropout_transition=2[aout]"

                buildString {
                    if (req.isImage) {
                        append("-loop 1 -i \"${req.inputVideoPath}\" ")
                        append("-t ${req.imageDurationSec} ")
                    } else {
                        if (trimStart.isNotBlank()) append("$trimStart ")
                        if (trimDuration.isNotBlank()) append("$trimDuration ")
                        append("-i \"${req.inputVideoPath}\" ")
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

    // ─── createGif ────────────────────────────────────────────────────────────

    override suspend fun createGif(req: GifRequest): FfmpegResult {
        val gifTarget = if (req.overlayBitmap != null) {
            File(req.outputFile.parentFile, "${req.outputFile.nameWithoutExtension}_base.gif")
        } else req.outputFile

        val step1Ok = when (val src = req.source) {
            is GifSource.Video -> {
                val startSec = src.startMs / 1000f
                val durSec = src.durationMs / 1000f
                val palFile = File(req.outputFile.parentFile, "${req.outputFile.nameWithoutExtension}_pal.png")
                val cW = src.cropRight - src.cropLeft
                val cH = src.cropBottom - src.cropTop
                val cropVf = "crop=iw*$cW:ih*$cH:iw*${src.cropLeft}:ih*${src.cropTop},"
                val pass1 = "-ss $startSec -t $durSec -i \"${src.inputFile.absolutePath}\" -vf \"${cropVf}fps=10,scale=${req.outputWidth}:${req.outputHeight}:flags=lanczos,palettegen\" -y \"${palFile.absolutePath}\""
                executeFfmpegSync(pass1)
                if (!palFile.exists()) {
                    false
                } else {
                    val pass2 = "-ss $startSec -t $durSec -i \"${src.inputFile.absolutePath}\" -i \"${palFile.absolutePath}\" " +
                            "-filter_complex \"${cropVf}fps=10,scale=${req.outputWidth}:${req.outputHeight}:flags=lanczos[x];[x][1:v]paletteuse\" -loop 0 -y \"${gifTarget.absolutePath}\""
                    val ok = executeFfmpegSync(pass2)
                    palFile.delete()
                    ok
                }
            }
            is GifSource.Image -> {
                val cW = src.cropRight - src.cropLeft
                val cH = src.cropBottom - src.cropTop
                val cropVf = "crop=iw*$cW:ih*$cH:iw*${src.cropLeft}:ih*${src.cropTop},"
                val imgCmd = "-i \"${src.inputFile.absolutePath}\" -vf \"${cropVf}scale=${req.outputWidth}:${req.outputHeight}:flags=lanczos\" -loop 0 -y \"${gifTarget.absolutePath}\""
                executeFfmpegSync(imgCmd)
            }
            is GifSource.ImageList -> {
                if (src.files.size < 2) {
                    return FfmpegResult.Error("Mindestens 2 Bilder erforderlich.")
                }
                val concatFile = File(req.outputFile.parentFile, "${req.outputFile.nameWithoutExtension}_concat.txt")
                val palFile = File(req.outputFile.parentFile, "${req.outputFile.nameWithoutExtension}_mpal.png")
                val delaySec = src.frameDelayMs / 1000.0
                val sb = StringBuilder("ffconcat version 1.0\n")
                src.files.forEach { f ->
                    sb.append("file '${f.absolutePath}'\n")
                    sb.append("duration $delaySec\n")
                }
                // letztes Bild nochmals ohne duration (FFmpeg-Anforderung für korrektes Looping)
                sb.append("file '${src.files.last().absolutePath}'\n")
                concatFile.writeText(sb.toString())

                val pass1 = "-f concat -safe 0 -i \"${concatFile.absolutePath}\" " +
                        "-vf \"scale=${req.outputWidth}:${req.outputHeight}:flags=lanczos,palettegen=stats_mode=diff\" -y \"${palFile.absolutePath}\""
                executeFfmpegSync(pass1)
                val ok = if (!palFile.exists() || palFile.length() == 0L) {
                    false
                } else {
                    val pass2 = "-f concat -safe 0 -i \"${concatFile.absolutePath}\" -i \"${palFile.absolutePath}\" " +
                            "-filter_complex \"scale=${req.outputWidth}:${req.outputHeight}:flags=lanczos[x];[x][1:v]paletteuse=dither=bayer\" -loop 0 -y \"${gifTarget.absolutePath}\""
                    executeFfmpegSync(pass2)
                }
                concatFile.delete(); palFile.delete()
                ok
            }
        }

        if (!step1Ok || !gifTarget.exists() || gifTarget.length() == 0L) {
            gifTarget.delete()
            return FfmpegResult.Error("GIF-Erstellung fehlgeschlagen.")
        }

        if (req.overlayBitmap != null) {
            val overlayFile = File(req.outputFile.parentFile, "${req.outputFile.nameWithoutExtension}_ovl.png")
            FileOutputStream(overlayFile).use { out -> req.overlayBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
            val ovCmd = "-i \"${gifTarget.absolutePath}\" -i \"${overlayFile.absolutePath}\" -filter_complex \"[0:v][1:v]overlay=0:0\" -loop 0 -y \"${req.outputFile.absolutePath}\""
            val ok2 = executeFfmpegSync(ovCmd)
            overlayFile.delete(); gifTarget.delete()
            if (!ok2 || !req.outputFile.exists()) {
                return FfmpegResult.Error("Overlay konnte nicht angewendet werden.")
            }
        }

        return FfmpegResult.Success(req.outputFile)
    }

    // ─── overlayOnVideo ───────────────────────────────────────────────────────

    override suspend fun overlayOnVideo(req: VideoOverlayRequest): FfmpegResult {
        val overlayFile = File(req.outputFile.parentFile, "${req.outputFile.nameWithoutExtension}_ovl.png")
        FileOutputStream(overlayFile).use { out -> req.overlayBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }

        val startSec = req.trimStartMs / 1000.0
        val durSec = (req.trimEndMs - req.trimStartMs) / 1000.0
        val trimArgs = if (req.trimEndMs > req.trimStartMs && req.trimEndMs < Long.MAX_VALUE) "-ss $startSec -t $durSec" else ""

        val cmd = "$trimArgs -i \"${req.inputFile.absolutePath}\" -i \"${overlayFile.absolutePath}\" " +
                "-filter_complex \"[0:v][1:v]overlay=0:0\" -c:a copy -y \"${req.outputFile.absolutePath}\""
        val ok = executeFfmpegSync(cmd)
        overlayFile.delete()

        return if (ok && req.outputFile.exists()) {
            FfmpegResult.Success(req.outputFile)
        } else {
            FfmpegResult.Error("FFmpeg overlay-Fehler.")
        }
    }

    // ─── FFmpeg-Ausführung ────────────────────────────────────────────────────

    /** Asynchrone Ausführung mit Fortschritts-Callback (für längere Spark-Encodes). */
    private suspend fun executeFfmpegAsync(
        command: String,
        videoDurationMs: Long,
        onProgress: (Float) -> Unit
    ): Boolean = suspendCancellableCoroutine { cont ->
        var progressSimulation = 0f

        val session = FFmpegKit.executeAsync(
            command,
            { completedSession ->
                val returnCode = completedSession.returnCode
                val success = ReturnCode.isSuccess(returnCode)
                if (!success) {
                    Timber.tag(TAG).e(
                        "FFmpeg fehlgeschlagen: Code=${returnCode.value}, " +
                        "Output=${completedSession.allLogsAsString?.takeLast(500)}"
                    )
                }
                onProgress(1f)
                if (cont.isActive) cont.resume(success)
            },
            { log -> Timber.tag(TAG).v("FFmpeg: ${log.message?.trimEnd()}") },
            { statistics ->
                val processedMs = statistics.time.toFloat()
                if (processedMs > 0) {
                    val p = if (videoDurationMs > 0L) {
                        (processedMs / videoDurationMs.toFloat()).coerceIn(0f, 0.98f)
                    } else {
                        progressSimulation = minOf(progressSimulation + 0.02f, 0.95f)
                        progressSimulation
                    }
                    onProgress(p)
                }
            }
        )

        cont.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }

    /** Synchrone Ausführung (für GIF-Passes/Overlays, wie bisher). */
    private fun executeFfmpegSync(command: String): Boolean {
        val session = FFmpegKit.execute(command)
        return ReturnCode.isSuccess(session.returnCode)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FfmpegProviderModule {
    @Binds
    @Singleton
    abstract fun bindFfmpegProvider(impl: FfmpegKitProvider): FfmpegProvider
}
