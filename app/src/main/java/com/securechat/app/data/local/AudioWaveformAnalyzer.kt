package com.securechat.app.data.local

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Analysiert Audio-Dateien und liefert Amplitude + Frequenz-Proxy (Zero-Crossing-Rate)
 * für die Waveform-Visualisierung.
 *
 * Amplitude  → Balkenhöhe im Waveform-Player
 * Frequenz   → Balkenfarbe (hell = hohe Frequenz, dunkel = tiefe Frequenz)
 */
data class WaveformData(
    /** Amplituden-Werte, normiert auf 0..1 (RMS pro Segment) */
    val amplitudes: FloatArray = FloatArray(80),
    /** Frequenz-Proxy 0..1, basierend auf Zero-Crossing-Rate (1 = hoch) */
    val frequencies: FloatArray = FloatArray(80)
) {
    val segmentCount: Int get() = amplitudes.size
}

object AudioWaveformAnalyzer {

    /**
     * Analysiert eine Audio-Datei und gibt [WaveformData] zurück.
     * Läuft auf IO-Dispatcher.
     *
     * @param sourcePath  Absoluter Dateipfad oder HTTP-URL
     * @param segmentCount Anzahl der Segmente (Balken im Player, default 80)
     */
    suspend fun analyze(
        sourcePath: String,
        segmentCount: Int = 80
    ): WaveformData = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = MediaExtractor()
            extractor.setDataSource(sourcePath)

            // Audio-Track suchen
            val audioTrackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@runCatching WaveformData(FloatArray(segmentCount), FloatArray(segmentCount))

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val mimeType = format.getString(MediaFormat.KEY_MIME) ?: return@runCatching WaveformData()

            // Decoder einrichten
            val decoder = MediaCodec.createDecoderByType(mimeType)
            decoder.configure(format, null, null, 0)
            decoder.start()

            // Max 1,5 M Samples (~17 Sekunden Stereo 44.1 kHz) – OOM-Schutz
            val maxSamples = 1_500_000
            val pcmSamples = ArrayList<Short>(minOf(512 * 1024, maxSamples))
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            // Decode-Loop
            while (!outputDone) {
                // Input befüllen
                if (!inputDone) {
                    val inputIdx = decoder.dequeueInputBuffer(8000L)
                    if (inputIdx >= 0) {
                        val inputBuf = decoder.getInputBuffer(inputIdx) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputIdx, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // Output lesen
                val outputIdx = decoder.dequeueOutputBuffer(bufferInfo, 8000L)
                when {
                    outputIdx >= 0 -> {
                        val outBuf = decoder.getOutputBuffer(outputIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            // PCM-16-Daten als Shorts lesen
                            val shortBuf = outBuf.order(ByteOrder.nativeOrder()).asShortBuffer()
                            val chunk = ShortArray(shortBuf.remaining())
                            shortBuf.get(chunk)
                            val remaining = maxSamples - pcmSamples.size
                            if (remaining > 0) {
                                val limit = minOf(chunk.size, remaining)
                                pcmSamples.ensureCapacity(pcmSamples.size + limit)
                                for (i in 0 until limit) pcmSamples.add(chunk[i])
                            }
                        }
                        decoder.releaseOutputBuffer(outputIdx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 ||
                            pcmSamples.size >= maxSamples) {
                            outputDone = true
                        }
                    }
                    outputIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> { /* warten */ }
                    else -> { /* Format-Änderung o.ä. – ignorieren */ }
                }
            }

            decoder.stop()
            decoder.release()
            extractor.release()

            computeWaveform(pcmSamples, segmentCount)
        }.getOrElse { WaveformData(FloatArray(segmentCount), FloatArray(segmentCount)) }
    }

    /** Berechnet Waveform aus bereits decodierten PCM-Shorts. */
    fun computeWaveformFromSamples(samples: List<Short>, segmentCount: Int = 80): WaveformData =
        computeWaveform(samples, segmentCount)

    /**
     * Berechnet Waveform aus normalisierten Amplitudenwerten (0..1),
     * wie sie während der Aufnahme via getMaxAmplitude() gesammelt werden.
     * Frequenz-Proxy wird aus der zeitlichen Variation der Amplitude abgeleitet.
     *
     * @param normalizedAmplitudes Amplitudenwerte 0..1 (aus getMaxAmplitude/32767)
     * @param segmentCount         Anzahl der Ausgabe-Segmente
     */
    fun computeWaveformFromRecordingAmplitudes(
        normalizedAmplitudes: List<Float>,
        segmentCount: Int = 80
    ): WaveformData {
        if (normalizedAmplitudes.isEmpty()) return WaveformData(FloatArray(segmentCount), FloatArray(segmentCount))

        val total = normalizedAmplitudes.size
        val perSeg = maxOf(1, total / segmentCount)
        val amplitudes = FloatArray(segmentCount)
        val frequencies = FloatArray(segmentCount)

        for (seg in 0 until segmentCount) {
            val start = seg * perSeg
            val end = minOf(start + perSeg, total)
            if (start >= total) break

            // Durchschnittliche Amplitude des Segments
            var sum = 0f
            for (i in start until end) sum += normalizedAmplitudes[i]
            amplitudes[seg] = sum / (end - start)

            // Frequenz-Proxy: Varianz der Amplitude im Segment
            // Hohe Varianz → schnelle Schwingungen → hohe Frequenz → hellgrau
            val mean = amplitudes[seg]
            var variance = 0f
            for (i in start until end) {
                val diff = normalizedAmplitudes[i] - mean
                variance += diff * diff
            }
            variance /= (end - start)
            // Varianz normiert: max sinnvoller Wert ca. 0.04 (±0.2 Swing)
            frequencies[seg] = (variance / 0.04f).coerceIn(0f, 1f)
        }

        return WaveformData(amplitudes, frequencies)
    }

    private fun computeWaveform(samples: List<Short>, segmentCount: Int): WaveformData {
        if (samples.isEmpty()) return WaveformData(FloatArray(segmentCount), FloatArray(segmentCount))

        val total = samples.size
        val perSeg = maxOf(1, total / segmentCount)
        val amplitudes = FloatArray(segmentCount)
        val frequencies = FloatArray(segmentCount)

        var globalPeak = 0f
        for (seg in 0 until segmentCount) {
            val start = seg * perSeg
            val end = minOf(start + perSeg, total)
            if (start >= total) break

            // RMS = Effektivwert (Lautstärke-Wahrnehmung)
            var sumSq = 0.0
            var zeroCrossings = 0
            var prevSign = 0 // -1, 0, +1
            for (i in start until end) {
                val v = samples[i].toInt()
                sumSq += v.toLong() * v.toLong()
                val curSign = when {
                    v > 0 -> 1
                    v < 0 -> -1
                    else -> 0
                }
                if (prevSign != 0 && curSign != 0 && prevSign != curSign) zeroCrossings++
                if (curSign != 0) prevSign = curSign
            }

            val rms = sqrt(sumSq / (end - start)).toFloat()
            amplitudes[seg] = rms
            if (rms > globalPeak) globalPeak = rms

            // ZCR normiert: ~0 = tiefe Frequenz, ~1 = hohe Frequenz
            // Maximale ZCR bei 44100 Hz Mono ≈ 22050 Crossings/s
            // Pro Segment (perSeg samples): maxZCR ≈ perSeg/2
            val maxZcr = perSeg / 2f
            frequencies[seg] = (zeroCrossings / maxZcr).coerceIn(0f, 1f)
        }

        // Amplituden auf 0..1 normieren (globaler Peak)
        if (globalPeak > 0f) {
            for (i in amplitudes.indices) amplitudes[i] = (amplitudes[i] / globalPeak).coerceIn(0f, 1f)
        }

        return WaveformData(amplitudes, frequencies)
    }

    // -------------------------------------------------------------------------
    // Normalisierung: Gain ±6 dB
    // -------------------------------------------------------------------------

    /**
     * Normalisiert eine M4A-Datei so, dass der Pegel bei -3 dBFS liegt.
     * Gain ist auf ±6 dB begrenzt (Faktor 0.5 bis 2.0).
     *
     * @return Normalisierte Ausgabedatei (oder inputFile bei Fehler)
     */
    suspend fun normalizeAudio(inputFile: File): File = withContext(Dispatchers.IO) {
        // Bei Dateien > 4 MB Normalisierung überspringen (OOM-Schutz für lange Aufnahmen)
        if (inputFile.length() > 4L * 1024 * 1024) return@withContext inputFile
        val outputFile = File(inputFile.parent, "norm_${inputFile.name}")
        runCatching {
            val (pcm, sampleRate, channels) = decodeM4AToPcm(inputFile.absolutePath)
            if (pcm.isEmpty()) return@runCatching inputFile

            // Peak berechnen
            val peak = pcm.maxOfOrNull { abs(it.toInt()) }?.toFloat() ?: return@runCatching inputFile
            if (peak < 100f) return@runCatching inputFile // Fast-stille Datei – überspringen

            // Ziel: -3 dBFS = 0.708 linear → 32767 * 0.708 ≈ 23199
            val targetPeak = 32767f * 0.708f
            val rawGain = targetPeak / peak

            // Auf ±6 dB begrenzen: 10^(±6/20) = 0.501 .. 1.995
            val minGain = 10f.pow(-6f / 20f)  // ≈ 0.501
            val maxGain = 10f.pow(6f / 20f)   // ≈ 1.995
            val gain = rawGain.coerceIn(minGain, maxGain)

            if (gain < 0.95f || gain > 1.05f) {
                // Gain anwenden
                val normalized = ShortArray(pcm.size) { i ->
                    (pcm[i].toInt() * gain).toInt().coerceIn(-32768, 32767).toShort()
                }
                encodePcmToM4A(normalized, sampleRate, channels, outputFile)
                outputFile
            } else {
                inputFile // Gain nahe 1 → keine Änderung nötig
            }
        }.getOrElse { inputFile }
    }

    // -------------------------------------------------------------------------
    // Interne Decode / Encode Helfer
    // -------------------------------------------------------------------------

    private data class PcmResult(val samples: ShortArray, val sampleRate: Int, val channels: Int)

    private fun decodeM4AToPcm(path: String): PcmResult {
        val extractor = MediaExtractor()
        extractor.setDataSource(path)

        val trackIdx = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return PcmResult(ShortArray(0), 44100, 1)

        extractor.selectTrack(trackIdx)
        val format = extractor.getTrackFormat(trackIdx)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return PcmResult(ShortArray(0), 44100, 1)
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val result = ArrayList<Short>()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val idx = decoder.dequeueInputBuffer(5000L)
                if (idx >= 0) {
                    val buf = decoder.getInputBuffer(idx) ?: continue
                    val n = extractor.readSampleData(buf, 0)
                    if (n < 0) {
                        decoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(idx, 0, n, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = decoder.dequeueOutputBuffer(info, 5000L)
            if (outIdx >= 0) {
                val buf = decoder.getOutputBuffer(outIdx)
                if (buf != null && info.size > 0) {
                    val sb = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                    val chunk = ShortArray(sb.remaining())
                    sb.get(chunk)
                    for (s in chunk) result.add(s)
                }
                decoder.releaseOutputBuffer(outIdx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }

        decoder.stop()
        decoder.release()
        extractor.release()
        return PcmResult(result.toShortArray(), sampleRate, channels)
    }

    private fun encodePcmToM4A(
        pcm: ShortArray,
        sampleRate: Int,
        channels: Int,
        outputFile: File
    ) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels)
        format.setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var audioTrackIdx = -1
        var muxerStarted = false

        // PCM → ByteBuffer konvertieren
        val pcmBytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.nativeOrder())
        for (s in pcm) pcmBytes.putShort(s)
        pcmBytes.flip()

        val info = MediaCodec.BufferInfo()
        var inputOffset = 0
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val idx = encoder.dequeueInputBuffer(5000L)
                if (idx >= 0) {
                    val buf = encoder.getInputBuffer(idx) ?: continue
                    buf.clear()
                    val chunkSize = minOf(buf.capacity(), pcmBytes.remaining())
                    if (chunkSize <= 0) {
                        encoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val slice = pcmBytes.slice()
                        slice.limit(chunkSize)
                        buf.put(slice)
                        pcmBytes.position(pcmBytes.position() + chunkSize)
                        val pts = (inputOffset / (sampleRate.toFloat() * channels * 2) * 1_000_000).toLong()
                        encoder.queueInputBuffer(idx, 0, chunkSize, pts, 0)
                        inputOffset += chunkSize
                    }
                }
            }

            val outIdx = encoder.dequeueOutputBuffer(info, 5000L)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    audioTrackIdx = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                outIdx >= 0 -> {
                    val outBuf = encoder.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0 && muxerStarted &&
                        info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        muxer.writeSampleData(audioTrackIdx, outBuf, info)
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }

        encoder.stop()
        encoder.release()
        if (muxerStarted) muxer.stop()
        muxer.release()
    }
}
