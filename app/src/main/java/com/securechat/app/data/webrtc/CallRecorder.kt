package com.securechat.app.data.webrtc

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import org.webrtc.AudioTrack
import org.webrtc.AudioTrackSink
import org.webrtc.EglBase
import org.webrtc.GlRectDrawer
import org.webrtc.VideoFrame
import org.webrtc.VideoFrameDrawer
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Zeichnet einen laufenden 1:1-WebRTC-Anruf clientseitig in eine MP4-Datei auf.
 *
 * Lethe arbeitet rein peer-to-peer (E2EE, TURN relayt nur verschlüsseltes SRTP) – eine
 * serverseitige Aufzeichnung würde einen Mediaserver (SFU) erfordern, der die Ende-zu-Ende-
 * Verschlüsselung aufbrechen müsste. Stattdessen greift dieser Recorder die bereits
 * entschlüsselten Tracks lokal ab; die fertige Datei wird anschließend zum Server hochgeladen.
 *
 *  - **Video**: Frames des Remote-VideoTracks werden über OpenGL (gemeinsamer EGL-Kontext)
 *    in die Eingabe-Surface eines MediaCodec-H.264-Encoders gerendert.
 *  - **Audio**: Lokaler Mikrofon-Track und Remote-AudioTrack werden über [AudioTrackSink]
 *    abgegriffen, zu Mono 48 kHz gemischt (Summe mit Clipping) und als AAC kodiert.
 *
 * Der gemeinsame Factory-/ADM-Zustand bleibt unangetastet → laufende Anrufe (auch Gruppen)
 * werden nicht beeinflusst.
 */
class CallRecorder(
    private val outputFile: File,
    private val sharedEglContext: EglBase.Context,
    private val localAudioTrack: AudioTrack?,
    private val remoteAudioTrack: AudioTrack?,
    private val remoteVideoTrack: VideoTrack?
) {
    private val recordVideo = remoteVideoTrack != null

    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var muxer: MediaMuxer? = null

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    @Volatile private var muxerStarted = false
    private val muxerLock = Any()

    private val running = AtomicBoolean(false)
    private var startNs = 0L

    // EGL/GL läuft ausschließlich auf diesem Thread.
    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null
    @Volatile private var eglBase: EglBase? = null
    private var glDrawer: GlRectDrawer? = null
    private var frameDrawer: VideoFrameDrawer? = null
    @Volatile private var glReady = false

    // Audio
    private val localBuf = ArrayDeque<Short>()           // gepufferte lokale Mono-Samples
    private val pcmQueue = LinkedBlockingQueue<PcmItem>() // gemischte Frames → Audio-Encoder
    private var audioFeeder: Thread? = null
    private var videoDrainThread: Thread? = null

    private class PcmItem(val data: ByteArray, val ptsUs: Long, val eos: Boolean)

    private val localSink = AudioTrackSink { audioData, bitsPerSample, _, channels, frames, _ ->
        if (!running.get() || bitsPerSample != 16) return@AudioTrackSink
        val mono = toMono(audioData, channels, frames)
        synchronized(localBuf) {
            for (s in mono) localBuf.addLast(s)
            // Lokalen Puffer auf ~2 s begrenzen (verhindert unbegrenztes Wachstum bei Drift)
            while (localBuf.size > OUT_SAMPLE_RATE * 2) localBuf.removeFirst()
        }
    }

    // Remote-Track taktet die Mischung: pro Remote-Frame wird die gleiche Menge lokaler
    // Samples entnommen und addiert.
    private val remoteSink = AudioTrackSink { audioData, bitsPerSample, _, channels, frames, _ ->
        if (!running.get() || bitsPerSample != 16) return@AudioTrackSink
        val remoteMono = toMono(audioData, channels, frames)
        val mixed = ByteArray(remoteMono.size * 2)
        val bb = ByteBuffer.wrap(mixed).order(ByteOrder.LITTLE_ENDIAN)
        synchronized(localBuf) {
            for (r in remoteMono) {
                val l = if (localBuf.isNotEmpty()) localBuf.removeFirst().toInt() else 0
                var s = r.toInt() + l
                if (s > 32767) s = 32767
                if (s < -32768) s = -32768
                bb.putShort(s.toShort())
            }
        }
        val ptsUs = (System.nanoTime() - startNs) / 1000
        pcmQueue.offer(PcmItem(mixed, ptsUs, false))
    }

    private val videoSink = VideoSink { frame ->
        if (!running.get() || !glReady) return@VideoSink
        frame.retain()
        val h = renderHandler
        if (h == null) { frame.release(); return@VideoSink }
        h.post {
            try { renderFrame(frame) } catch (e: Exception) {
                Timber.tag(TAG).w(e, "renderFrame fehlgeschlagen")
            } finally { frame.release() }
        }
    }

    /** Startet die Aufnahme. Gibt true bei Erfolg zurück. */
    fun start(): Boolean {
        try {
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Audio-Encoder (AAC-LC, Mono 48 kHz)
            val aFmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, OUT_SAMPLE_RATE, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32_768)
            }
            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(aFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            if (recordVideo) {
                val vFmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_W, VIDEO_H).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, 3_000_000)
                    setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                }
                videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                    configure(vFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                }
                inputSurface = videoEncoder!!.createInputSurface()
                videoEncoder!!.start()
            }

            running.set(true)
            startNs = System.nanoTime()

            // GL-Render-Thread aufsetzen
            if (recordVideo) {
                val rt = HandlerThread("CallRecorderGL").also { it.start() }
                renderThread = rt
                val h = Handler(rt.looper)
                renderHandler = h
                h.post { initGl() }
                videoDrainThread = Thread({ videoDrainLoop() }, "CallRecorderVideoDrain").also { it.start() }
            }

            // Audio-Feeder-Thread
            audioFeeder = Thread({ audioFeederLoop() }, "CallRecorderAudio").also { it.start() }

            // Sinks anhängen
            localAudioTrack?.addSink(localSink)
            remoteAudioTrack?.addSink(remoteSink)
            remoteVideoTrack?.addSink(videoSink)

            Timber.tag(TAG).i("Aufnahme gestartet → ${outputFile.name} (video=$recordVideo)")
            return true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Start der Aufnahme fehlgeschlagen")
            cleanup()
            return false
        }
    }

    /** Stoppt die Aufnahme und finalisiert die Datei. Blockiert bis die Encoder geleert sind. */
    fun stop(): File? {
        if (!running.getAndSet(false)) return null
        try {
            remoteVideoTrack?.removeSink(videoSink)
            remoteAudioTrack?.removeSink(remoteSink)
            localAudioTrack?.removeSink(localSink)

            // Audio sauber beenden
            pcmQueue.offer(PcmItem(ByteArray(0), (System.nanoTime() - startNs) / 1000, true))
            audioFeeder?.join(3000)

            // Video sauber beenden
            if (recordVideo) {
                val latch = Object()
                renderHandler?.post {
                    try { videoEncoder?.signalEndOfInputStream() } catch (_: Exception) {}
                    synchronized(latch) { (latch as Object).notifyAll() }
                }
                synchronized(latch) { try { (latch as Object).wait(1500) } catch (_: Exception) {} }
                videoDrainThread?.join(3000)
            }
            return outputFile.takeIf { it.exists() && it.length() > 0 }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Stop der Aufnahme fehlgeschlagen")
            return null
        } finally {
            cleanup()
        }
    }

    // ── GL / Video ──────────────────────────────────────────────────────────

    private fun initGl() {
        try {
            val egl = EglBase.create(sharedEglContext, EglBase.CONFIG_RECORDABLE)
            egl.createSurface(inputSurface!!)
            egl.makeCurrent()
            eglBase = egl
            glDrawer = GlRectDrawer()
            frameDrawer = VideoFrameDrawer()
            glReady = true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "EGL-Initialisierung fehlgeschlagen")
        }
    }

    private fun renderFrame(frame: VideoFrame) {
        val egl = eglBase ?: return
        val drawer = glDrawer ?: return
        val fd = frameDrawer ?: return
        egl.makeCurrent()
        android.opengl.GLES20.glClearColor(0f, 0f, 0f, 1f)
        android.opengl.GLES20.glClear(android.opengl.GLES20.GL_COLOR_BUFFER_BIT)
        // Seitenverhältnis-erhaltend in 1280x720 einpassen (Letterbox)
        val fw = frame.rotatedWidth
        val fh = frame.rotatedHeight
        if (fw <= 0 || fh <= 0) return
        val scale = minOf(VIDEO_W.toFloat() / fw, VIDEO_H.toFloat() / fh)
        val vw = (fw * scale).toInt()
        val vh = (fh * scale).toInt()
        val vx = (VIDEO_W - vw) / 2
        val vy = (VIDEO_H - vh) / 2
        fd.drawFrame(frame, drawer, null, vx, vy, vw, vh)
        egl.swapBuffers(maxOf(0L, System.nanoTime() - startNs))
    }

    private fun videoDrainLoop() {
        val enc = videoEncoder ?: return
        val info = MediaCodec.BufferInfo()
        while (true) {
            val idx = try { enc.dequeueOutputBuffer(info, 10_000) } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Video dequeue Fehler"); break
            }
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!running.get() && pcmQueue.isEmpty()) { /* warte auf EOS-Flag im Buffer */ }
                }
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> synchronized(muxerLock) {
                    videoTrackIndex = muxer!!.addTrack(enc.outputFormat)
                    maybeStartMuxer()
                }
                idx >= 0 -> {
                    val buf = enc.getOutputBuffer(idx)
                    if (buf != null) {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && awaitMuxerStarted()) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            synchronized(muxerLock) {
                                if (muxerStarted) muxer!!.writeSampleData(videoTrackIndex, buf, info)
                            }
                        }
                    }
                    enc.releaseOutputBuffer(idx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
    }

    // ── Audio ───────────────────────────────────────────────────────────────

    private fun audioFeederLoop() {
        val enc = audioEncoder ?: return
        val info = MediaCodec.BufferInfo()
        var sawEos = false
        while (!sawEos) {
            val item = try { pcmQueue.take() } catch (e: InterruptedException) { break }
            var offset = 0
            do {
                val inIdx = try { enc.dequeueInputBuffer(10_000) } catch (e: Exception) { -1 }
                if (inIdx < 0) {
                    drainAudio(enc, info); continue
                }
                val inBuf = enc.getInputBuffer(inIdx) ?: continue
                inBuf.clear()
                if (item.eos) {
                    enc.queueInputBuffer(inIdx, 0, 0, item.ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    sawEos = true
                    break
                }
                val chunk = minOf(inBuf.remaining(), item.data.size - offset)
                inBuf.put(item.data, offset, chunk)
                enc.queueInputBuffer(inIdx, 0, chunk, item.ptsUs, 0)
                offset += chunk
            } while (offset < item.data.size)
            drainAudio(enc, info)
        }
        // Restliche Ausgabe leeren
        drainAudio(enc, info, untilEos = true)
    }

    private fun drainAudio(enc: MediaCodec, info: MediaCodec.BufferInfo, untilEos: Boolean = false) {
        while (true) {
            val idx = try { enc.dequeueOutputBuffer(info, if (untilEos) 10_000 else 0) } catch (e: Exception) { break }
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!untilEos) break
                }
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> synchronized(muxerLock) {
                    audioTrackIndex = muxer!!.addTrack(enc.outputFormat)
                    maybeStartMuxer()
                }
                idx >= 0 -> {
                    val buf = enc.getOutputBuffer(idx)
                    if (buf != null) {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && awaitMuxerStarted()) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            synchronized(muxerLock) {
                                if (muxerStarted) muxer!!.writeSampleData(audioTrackIndex, buf, info)
                            }
                        }
                    }
                    enc.releaseOutputBuffer(idx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
    }

    // ── Hilfen ──────────────────────────────────────────────────────────────

    /** Startet den Muxer sobald alle erwarteten Spuren registriert sind. Aufruf unter [muxerLock]. */
    private fun maybeStartMuxer() {
        if (muxerStarted) return
        if (audioTrackIndex < 0) return
        if (recordVideo && videoTrackIndex < 0) return
        muxer!!.start()
        muxerStarted = true
        Timber.tag(TAG).d("Muxer gestartet (audio=$audioTrackIndex video=$videoTrackIndex)")
    }

    /** Wartet kurz bis der Muxer läuft (max. 2 s). Verhindert Schreiben vor muxer.start(). */
    private fun awaitMuxerStarted(): Boolean {
        var waited = 0
        while (!muxerStarted && waited < 2000) {
            Thread.sleep(2); waited += 2
        }
        return muxerStarted
    }

    private fun toMono(buf: ByteBuffer, channels: Int, frames: Int): ShortArray {
        buf.order(ByteOrder.LITTLE_ENDIAN)
        val sb = buf.asShortBuffer()
        val out = ShortArray(frames)
        if (channels <= 1) {
            for (i in 0 until frames) out[i] = if (sb.hasRemaining()) sb.get() else 0
        } else {
            for (i in 0 until frames) {
                var sum = 0
                for (c in 0 until channels) if (sb.hasRemaining()) sum += sb.get().toInt()
                out[i] = (sum / channels).toShort()
            }
        }
        return out
    }

    private fun cleanup() {
        try { videoEncoder?.stop() } catch (_: Exception) {}
        try { videoEncoder?.release() } catch (_: Exception) {}
        try { audioEncoder?.stop() } catch (_: Exception) {}
        try { audioEncoder?.release() } catch (_: Exception) {}
        try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
        try { muxer?.release() } catch (_: Exception) {}
        try { eglBase?.release() } catch (_: Exception) {}
        try { glDrawer?.release() } catch (_: Exception) {}
        try { frameDrawer?.release() } catch (_: Exception) {}
        try { inputSurface?.release() } catch (_: Exception) {}
        try { renderThread?.quitSafely() } catch (_: Exception) {}
        videoEncoder = null; audioEncoder = null; muxer = null
        eglBase = null; glDrawer = null; frameDrawer = null; inputSurface = null
        renderThread = null; renderHandler = null
        glReady = false; muxerStarted = false
    }

    companion object {
        private const val TAG = "LETHE_REC"
        private const val OUT_SAMPLE_RATE = 48_000
        private const val VIDEO_W = 1280
        private const val VIDEO_H = 720
    }
}
