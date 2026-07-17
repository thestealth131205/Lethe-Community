package com.securechat.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

/**
 * RootEncoder [BaseFilterRender] für Silhouetten-Hintergrundersatz (virtueller Greenscreen).
 *
 * ML Kit Selfie-Segmentierung liefert per [updateMask] eine Vordergrund-Konfidenzmaske.
 * Der Hintergrund im RTMP-Stream wird durch das per [setBackground] geladene Bild ersetzt.
 * Wenn kein Hintergrundbild gesetzt wurde, wird ein Grün (#00CC00) als Greenscreen genutzt.
 *
 * Thread-Sicherheit: Masken- und Hintergrundaktualisierungen laufen über AtomicReference
 * und werden auf dem GL-Thread in [drawFilter] hochgeladen.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class LiveSilhouetteFilter : BaseFilterRender() {

    // Quad: X, Y, Z, U, V
    private val QUAD = floatArrayOf(
        -1f, -1f, 0f, 0f, 0f,
         1f, -1f, 0f, 1f, 0f,
        -1f,  1f, 0f, 0f, 1f,
         1f,  1f, 0f, 1f, 1f,
    )

    private var prog     = -1
    private var aPos     = -1
    private var aUV      = -1
    private var uMVP     = -1
    private var uST      = -1
    private var uCamera  = -1
    private var uMask    = -1
    private var uBg      = -1
    private var uEnabled = -1
    private var uHasBg   = -1

    private val maskTexId = intArrayOf(-1)
    private val bgTexId   = intArrayOf(-1)
    private var maskInited = false
    private var bgInited   = false

    private val pendingMask = AtomicReference<Triple<ByteArray, Int, Int>?>(null)
    private val pendingBg   = AtomicReference<Bitmap?>(null)

    @Volatile var isEnabled    = false
    @Volatile var hasBackground = false

    private val FLOAT_SIZE = 4
    private val STRIDE     = 5 * FLOAT_SIZE

    init {
        // squareVertex ist ein protected FloatBuffer aus BaseRenderOffScreen
        squareVertex = ByteBuffer.allocateDirect(QUAD.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        squareVertex.put(QUAD).position(0)
    }

    override fun initGlFilter(context: Context) {
        val vsh = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uMVPMatrix;
            uniform mat4 uSTMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTexCoord = (uSTMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
            }
        """.trimIndent()

        val fsh = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uCamera;
            uniform sampler2D uMask;
            uniform sampler2D uBackground;
            uniform int uEnabled;
            uniform int uHasBg;
            void main() {
                vec4 cam = texture2D(uCamera, vTexCoord);
                if (uEnabled == 0) { gl_FragColor = cam; return; }
                float fg = texture2D(uMask, vTexCoord).r;
                vec4 bg = (uHasBg == 1)
                    ? texture2D(uBackground, vTexCoord)
                    : vec4(0.0, 0.8, 0.0, 1.0);
                gl_FragColor = mix(bg, cam, fg);
            }
        """.trimIndent()

        prog    = buildProg(vsh, fsh)
        aPos    = GLES20.glGetAttribLocation(prog,  "aPosition")
        aUV     = GLES20.glGetAttribLocation(prog,  "aTexCoord")
        uMVP    = GLES20.glGetUniformLocation(prog, "uMVPMatrix")
        uST     = GLES20.glGetUniformLocation(prog, "uSTMatrix")
        uCamera = GLES20.glGetUniformLocation(prog, "uCamera")
        uMask   = GLES20.glGetUniformLocation(prog, "uMask")
        uBg     = GLES20.glGetUniformLocation(prog, "uBackground")
        uEnabled = GLES20.glGetUniformLocation(prog, "uEnabled")
        uHasBg   = GLES20.glGetUniformLocation(prog, "uHasBg")
    }

    override fun drawFilter() {
        // Texturen beim ersten Draw initialisieren (GL-Kontext ist aktiv)
        if (!maskInited) { GLES20.glGenTextures(1, maskTexId, 0); initTex(maskTexId[0]); maskInited = true }
        if (!bgInited)   { GLES20.glGenTextures(1, bgTexId,   0); initTex(bgTexId[0]);   bgInited   = true }

        // Ausstehende Maske hochladen
        pendingMask.getAndSet(null)?.let { (bytes, w, h) ->
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTexId[0])
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
                w, h, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE,
                ByteBuffer.wrap(bytes)
            )
        }

        // Ausstehenden Hintergrund hochladen
        pendingBg.getAndSet(null)?.let { bmp ->
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTexId[0])
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            bmp.recycle()
            hasBackground = true
        }

        GLES20.glUseProgram(prog)

        // Camera-Textur (previousTexId von BaseFilterRender) → TEXTURE4
        GLES20.glActiveTexture(GLES20.GL_TEXTURE4)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId)
        GLES20.glUniform1i(uCamera, 4)

        // Maske → TEXTURE1
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTexId[0])
        GLES20.glUniform1i(uMask, 1)

        // Hintergrund → TEXTURE2
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTexId[0])
        GLES20.glUniform1i(uBg, 2)

        GLES20.glUniform1i(uEnabled, if (isEnabled) 1 else 0)
        GLES20.glUniform1i(uHasBg,   if (hasBackground) 1 else 0)

        GLES20.glUniformMatrix4fv(uMVP, 1, false, MVPMatrix, 0)
        GLES20.glUniformMatrix4fv(uST,  1, false, STMatrix,  0)

        squareVertex.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, STRIDE, squareVertex)
        GLES20.glEnableVertexAttribArray(aPos)

        squareVertex.position(3)
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, STRIDE, squareVertex)
        GLES20.glEnableVertexAttribArray(aUV)
    }

    override fun release() {
        if (prog != -1)       GLES20.glDeleteProgram(prog)
        if (maskInited) GLES20.glDeleteTextures(1, maskTexId, 0)
        if (bgInited)   GLES20.glDeleteTextures(1, bgTexId,   0)
    }

    /** Von ML-Kit-Callback aufrufen (beliebiger Thread). */
    fun updateMask(floats: FloatArray, w: Int, h: Int) {
        val bytes = ByteArray(w * h) { i -> (floats[i].coerceIn(0f, 1f) * 255).toInt().toByte() }
        pendingMask.set(Triple(bytes, w, h))
    }

    /**
     * Neuen Hintergrund setzen (UI-Thread).
     * Die Bitmap wird auf dem GL-Thread recycelt – nach dem Aufruf NICHT mehr verwenden.
     */
    fun setBackground(bitmap: Bitmap) {
        val w = nextPow2(bitmap.width)
        val h = nextPow2(bitmap.height)
        val scaled = if (w == bitmap.width && h == bitmap.height)
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        else
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        pendingBg.set(scaled)
    }

    /** Setzt den Hintergrund zurück (zeigt Greenscreen statt Bild). */
    fun clearBackground() { hasBackground = false }

    // ── GL-Hilfsmethoden ─────────────────────────────────────────────────────

    private fun initTex(id: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun buildProg(vsh: String, fsh: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            return s
        }
        val v = compile(GLES20.GL_VERTEX_SHADER, vsh)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fsh)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v); GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        GLES20.glDeleteShader(v); GLES20.glDeleteShader(f)
        return p
    }

    private fun nextPow2(n: Int): Int { var x = 1; while (x < n) x = x shl 1; return x }
}
