@file:Suppress("DEPRECATION")

package com.securechat.app.ui.screens

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.securechat.app.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

// ============================================================
// STL / OBJ Parser
// ============================================================

private data class Model3D(val vertices: FloatArray, val normals: FloatArray, val uvs: FloatArray, val triangleCount: Int, val hasUvs: Boolean = false)

private object ModelParser {

    /** Parst binary oder ASCII STL. */
    fun parseStle(file: File): Model3D {
        val bytes = file.readBytes()
        return if (isBinaryStl(bytes)) parseBinaryStl(bytes) else parseAsciiStl(bytes.decodeToString())
    }

    private fun emptyUvs(triangleCount: Int) = FloatArray(triangleCount * 6)

    private fun isBinaryStl(bytes: ByteArray): Boolean {
        if (bytes.size < 84) return false
        val headerStr = bytes.take(80).map { it.toInt().toChar() }.joinToString("")
        if (headerStr.trim().startsWith("solid", ignoreCase = true)) {
            // Could be ASCII or binary with "solid" header – check further
            val numTriangles = ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val expectedSize = 84 + numTriangles.toLong() * 50
            return bytes.size.toLong() == expectedSize
        }
        return true
    }

    private fun parseBinaryStl(bytes: ByteArray): Model3D {
        if (bytes.size < 84) return Model3D(FloatArray(0), FloatArray(0), FloatArray(0), 0)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(80)
        val numTri = buf.int.coerceIn(0, 2_000_000)
        val verts  = FloatArray(numTri * 9)
        val norms  = FloatArray(numTri * 9)
        var vi = 0; var ni = 0
        repeat(numTri) {
            if (buf.remaining() < 50) return@repeat
            val nx = buf.float; val ny = buf.float; val nz = buf.float
            repeat(3) {
                verts[vi++] = buf.float; verts[vi++] = buf.float; verts[vi++] = buf.float
                norms[ni++] = nx;        norms[ni++] = ny;        norms[ni++] = nz
            }
            buf.short // attribute
        }
        return normalizeModel(Model3D(verts, norms, FloatArray(0), numTri))
    }

    private fun parseAsciiStl(text: String): Model3D {
        val verts  = mutableListOf<Float>()
        val norms  = mutableListOf<Float>()
        var nx = 0f; var ny = 0f; var nz = 0f
        for (line in text.lines()) {
            val t = line.trim()
            when {
                t.startsWith("facet normal") -> {
                    val p = t.split("\\s+".toRegex())
                    if (p.size >= 5) { nx = p[2].toFloatOrNull() ?: 0f; ny = p[3].toFloatOrNull() ?: 0f; nz = p[4].toFloatOrNull() ?: 0f }
                }
                t.startsWith("vertex") -> {
                    val p = t.split("\\s+".toRegex())
                    if (p.size >= 4) {
                        verts += p[1].toFloatOrNull() ?: 0f; verts += p[2].toFloatOrNull() ?: 0f; verts += p[3].toFloatOrNull() ?: 0f
                        norms += nx; norms += ny; norms += nz
                    }
                }
            }
        }
        val numTri = verts.size / 9
        return normalizeModel(Model3D(verts.toFloatArray(), norms.toFloatArray(), FloatArray(0), numTri))
    }

    /** OBJ-Parser mit UV-Textur-Koordinaten (v, vt, f). */
    fun parseObj(file: File): Model3D {
        val positions = mutableListOf<Float>()
        val texCoords = mutableListOf<Float>()
        val faceVerts = mutableListOf<Float>()
        val faceNorms = mutableListOf<Float>()
        val faceUvs   = mutableListOf<Float>()
        BufferedReader(InputStreamReader(file.inputStream())).forEachLine { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("v ") -> {
                    val p = line.split("\\s+".toRegex())
                    if (p.size >= 4) { positions += p[1].toFloatOrNull() ?: 0f; positions += p[2].toFloatOrNull() ?: 0f; positions += p[3].toFloatOrNull() ?: 0f }
                }
                line.startsWith("vt ") -> {
                    val p = line.split("\\s+".toRegex())
                    if (p.size >= 3) { texCoords += p[1].toFloatOrNull() ?: 0f; texCoords += p[2].toFloatOrNull() ?: 0f }
                }
                line.startsWith("f ") -> {
                    val tokens = line.split("\\s+".toRegex()).drop(1)
                    data class FaceVertex(val vi: Int, val ti: Int)
                    val fvs = tokens.map { tok ->
                        val parts = tok.split('/')
                        FaceVertex(
                            vi = (parts.getOrNull(0)?.toIntOrNull() ?: 1) - 1,
                            ti = (parts.getOrNull(1)?.toIntOrNull() ?: 0) - 1
                        )
                    }
                    if (fvs.size >= 3) {
                        for (i in 1 until fvs.size - 1) {
                            for (fv in listOf(fvs[0], fvs[i], fvs[i + 1])) {
                                val idx = fv.vi.coerceIn(0, positions.size / 3 - 1) * 3
                                faceVerts += positions[idx]; faceVerts += positions[idx + 1]; faceVerts += positions[idx + 2]
                                if (texCoords.isNotEmpty() && fv.ti >= 0 && fv.ti * 2 + 1 < texCoords.size) {
                                    faceUvs += texCoords[fv.ti * 2]
                                    faceUvs += 1f - texCoords[fv.ti * 2 + 1] // Y-Flip für OpenGL
                                } else {
                                    faceUvs += 0f; faceUvs += 0f
                                }
                            }
                        }
                    }
                }
            }
        }
        val hasUvs = texCoords.isNotEmpty()
        // Normalen berechnen
        val numTri = faceVerts.size / 9
        for (i in 0 until numTri) {
            val b = i * 9
            val ax = faceVerts[b];   val ay = faceVerts[b+1]; val az = faceVerts[b+2]
            val bx = faceVerts[b+3]; val by = faceVerts[b+4]; val bz = faceVerts[b+5]
            val cx = faceVerts[b+6]; val cy = faceVerts[b+7]; val cz = faceVerts[b+8]
            val ux = bx-ax; val uy = by-ay; val uz = bz-az
            val vx = cx-ax; val vy = cy-ay; val vz = cz-az
            val nx = uy*vz - uz*vy; val ny = uz*vx - ux*vz; val nz = ux*vy - uy*vx
            val len = sqrt((nx*nx + ny*ny + nz*nz).toDouble()).toFloat().coerceAtLeast(1e-6f)
            repeat(3) { faceNorms += nx/len; faceNorms += ny/len; faceNorms += nz/len }
        }
        return normalizeModel(Model3D(faceVerts.toFloatArray(), faceNorms.toFloatArray(), faceUvs.toFloatArray(), numTri, hasUvs))
    }

    /** Zentriert + skaliert das Modell auf Einheitskugel. */
    private fun normalizeModel(m: Model3D): Model3D {
        if (m.triangleCount == 0) return m
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        var i = 0
        while (i < m.vertices.size) {
            val x = m.vertices[i]; val y = m.vertices[i+1]; val z = m.vertices[i+2]
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
            i += 3
        }
        val cx = (minX + maxX) / 2f; val cy = (minY + maxY) / 2f; val cz = (minZ + maxZ) / 2f
        val scale = 2f / maxOf(maxX - minX, maxY - minY, maxZ - minZ).coerceAtLeast(1e-6f)
        val v = m.vertices.copyOf()
        var j = 0
        while (j < v.size) { v[j] = (v[j] - cx) * scale; v[j+1] = (v[j+1] - cy) * scale; v[j+2] = (v[j+2] - cz) * scale; j += 3 }
        return m.copy(vertices = v)
    }
}

// ============================================================
// OpenGL ES 2.0 Renderer
// ============================================================

private const val VERTEX_SHADER = """
attribute vec3 aPosition;
attribute vec3 aNormal;
attribute vec2 aTexCoord;
uniform mat4 uMVP;
uniform mat4 uModel;
varying vec3 vNormal;
varying vec3 vFragPos;
varying vec2 vTexCoord;
void main() {
    gl_Position = uMVP * vec4(aPosition, 1.0);
    vFragPos = vec3(uModel * vec4(aPosition, 1.0));
    vNormal  = mat3(uModel) * aNormal;
    vTexCoord = aTexCoord;
}
"""

private const val FRAGMENT_SHADER = """
precision mediump float;
varying vec3 vNormal;
varying vec3 vFragPos;
varying vec2 vTexCoord;
uniform vec3 uLightDir;
uniform vec3 uObjectColor;
uniform sampler2D uTexture;
uniform int uHasTexture;
void main() {
    vec3 norm     = normalize(vNormal);
    float ambient = 0.3;
    float diff    = max(dot(norm, normalize(uLightDir)), 0.0);
    vec3 viewDir  = normalize(vec3(0.0, 0.0, 3.0) - vFragPos);
    vec3 halfDir  = normalize(normalize(uLightDir) + viewDir);
    float spec    = pow(max(dot(norm, halfDir), 0.0), 32.0) * 0.4;
    float light   = ambient + diff + spec;
    if (uHasTexture == 1) {
        vec4 texColor = texture2D(uTexture, vTexCoord);
        gl_FragColor  = vec4(texColor.rgb * light, texColor.a);
    } else {
        gl_FragColor  = vec4(light * uObjectColor, 1.0);
    }
}
"""

private class ModelRenderer(
    private val getModel: () -> Model3D?,
    private val getTextureBitmap: () -> android.graphics.Bitmap?,
    private val getRotX: () -> Float,
    private val getRotY: () -> Float,
    private val getZoom: () -> Float
) : GLSurfaceView.Renderer {

    private var program = 0
    private var vertBuf: FloatBuffer? = null
    private var normBuf: FloatBuffer? = null
    private var uvBuf:   FloatBuffer? = null
    private var triCount = 0
    private var textureId = 0
    private var textureLoaded = false
    private var lastTextureBitmap: android.graphics.Bitmap? = null

    private val mvpMatrix   = FloatArray(16)
    private val projMatrix  = FloatArray(16)
    private val viewMatrix  = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.07f, 0.07f, 0.12f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textureId = ids[0]
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        val ratio = w.toFloat() / h.coerceAtLeast(1).toFloat()
        Matrix.frustumM(projMatrix, 0, -ratio, ratio, -1f, 1f, 2f, 20f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val model = getModel() ?: return
        if (triCount != model.triangleCount) uploadModel(model)
        if (triCount == 0) return

        // Textur hochladen wenn neu verfügbar
        val bmp = getTextureBitmap()
        if (bmp != null && bmp !== lastTextureBitmap) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
            android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
            lastTextureBitmap = bmp
            textureLoaded = true
        }

        val vb = vertBuf ?: return
        val nb = normBuf ?: return

        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 3f * getZoom(), 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, getRotX(), 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, getRotY(), 0f, 1f, 0f)

        val vm = FloatArray(16); Matrix.multiplyMM(vm, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, vm, 0)

        GLES20.glUseProgram(program)
        val posLoc     = GLES20.glGetAttribLocation(program, "aPosition")
        val normLoc    = GLES20.glGetAttribLocation(program, "aNormal")
        val texLoc     = GLES20.glGetAttribLocation(program, "aTexCoord")
        val mvpLoc     = GLES20.glGetUniformLocation(program, "uMVP")
        val modLoc     = GLES20.glGetUniformLocation(program, "uModel")
        val lightLoc   = GLES20.glGetUniformLocation(program, "uLightDir")
        val colorLoc   = GLES20.glGetUniformLocation(program, "uObjectColor")
        val texSampler = GLES20.glGetUniformLocation(program, "uTexture")
        val hasTex     = GLES20.glGetUniformLocation(program, "uHasTexture")

        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(modLoc, 1, false, modelMatrix, 0)
        GLES20.glUniform3f(lightLoc, 1f, 2f, 3f)
        GLES20.glUniform3f(colorLoc, 0.66f, 0.66f, 0.0f)

        val useTexture = textureLoaded && model.hasUvs && uvBuf != null
        GLES20.glUniform1i(hasTex, if (useTexture) 1 else 0)
        if (useTexture) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glUniform1i(texSampler, 0)
        }

        vb.position(0)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, vb)

        nb.position(0)
        GLES20.glEnableVertexAttribArray(normLoc)
        GLES20.glVertexAttribPointer(normLoc, 3, GLES20.GL_FLOAT, false, 0, nb)

        if (useTexture && texLoc >= 0) {
            uvBuf!!.position(0)
            GLES20.glEnableVertexAttribArray(texLoc)
            GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, uvBuf!!)
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, triCount * 3)
        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(normLoc)
        if (useTexture && texLoc >= 0) GLES20.glDisableVertexAttribArray(texLoc)
    }

    private fun uploadModel(model: Model3D) {
        vertBuf = ByteBuffer.allocateDirect(model.vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().also { it.put(model.vertices); it.position(0) }
        normBuf = ByteBuffer.allocateDirect(model.normals.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().also { it.put(model.normals); it.position(0) }
        if (model.hasUvs && model.uvs.isNotEmpty()) {
            uvBuf = ByteBuffer.allocateDirect(model.uvs.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().also { it.put(model.uvs); it.position(0) }
        }
        triCount = model.triangleCount
    }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compileShader(GLES20.GL_VERTEX_SHADER, vs)
        val f = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
        return GLES20.glCreateProgram().also { GLES20.glAttachShader(it, v); GLES20.glAttachShader(it, f); GLES20.glLinkProgram(it) }
    }

    private fun compileShader(type: Int, src: String): Int {
        return GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }
    }
}

// ============================================================
// Touch-sensitives GLSurfaceView
// ============================================================

private class Interactive3DView(context: Context) : GLSurfaceView(context) {
    var rotX = 20f
    var rotY = -30f
    var zoom = 1f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom /= detector.scaleFactor
            zoom = zoom.coerceIn(0.3f, 4f)
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount == 1) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastTouchX = event.x; lastTouchY = event.y }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    rotY += dx * 0.5f
                    rotX += dy * 0.5f
                    rotX = rotX.coerceIn(-89f, 89f)
                    lastTouchX = event.x; lastTouchY = event.y
                    requestRender()
                }
            }
        }
        return true
    }
}

// ============================================================
// Composable Screen
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreen3DViewerScreen(
    fileUrl: String,
    filename: String,
    textureUrl: String = "",
    onNavigateBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()
    var model    by remember { mutableStateOf<Model3D?>(null) }
    var textureBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg  by remember { mutableStateOf<String?>(null) }

    // Modell asynchron herunterladen + parsen (gecacht via MediaCache)
    LaunchedEffect(fileUrl) {
        withContext(Dispatchers.IO) {
            try {
                val ext  = fileUrl.substringAfterLast('.', "stl").lowercase()
                val temp = viewModel.getMediaFile(fileUrl, "3d", ext)
                if (temp != null) {
                    model = when (ext) {
                        "obj" -> ModelParser.parseObj(temp)
                        else  -> ModelParser.parseStle(temp)
                    }
                } else {
                    errorMsg = "Download fehlgeschlagen"
                }
            } catch (e: Exception) {
                Timber.e(e, "3D-Viewer: Fehler beim Laden")
                errorMsg = e.message ?: "Fehler"
            } finally {
                isLoading = false
            }
        }
    }

    // Textur asynchron laden (gecacht via MediaCache)
    LaunchedEffect(textureUrl) {
        if (textureUrl.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val texFile = viewModel.getMediaFile(textureUrl, "images")
                    val bmp = texFile?.let { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }
                    textureBitmap = bmp
                } catch (e: Exception) {
                    Timber.w(e, "3D-Viewer: Textur konnte nicht geladen werden")
                }
            }
        }
    }

    // Rotation- + Zoom-State (für Renderer zugänglich via Lambda)
    var glView by remember { mutableStateOf<Interactive3DView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = filename,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        glView?.let { v ->
                            v.onPause()
                        }
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0D0D18)),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFFA8A800))
                        Spacer(Modifier.height(12.dp))
                        Text("3D-Modell wird geladen…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                }
                errorMsg != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Laden fehlgeschlagen", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(errorMsg ?: "", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
                else -> {
                    // 3D-Renderer
                    val currentModel = model
                    AndroidView(
                        factory = { ctx ->
                            Interactive3DView(ctx).also { view ->
                                glView = view
                                view.setEGLContextClientVersion(2)
                                val renderer = ModelRenderer(
                                    getModel         = { currentModel },
                                    getTextureBitmap = { textureBitmap },
                                    getRotX          = { view.rotX },
                                    getRotY          = { view.rotY },
                                    getZoom          = { view.zoom }
                                )
                                view.setRenderer(renderer)
                                view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    // Hinweis-Overlay (einblendet, verblasst nach 3 s)
                    var showHint by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) { kotlinx.coroutines.delay(3000); showHint = false }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showHint,
                        enter = androidx.compose.animation.fadeIn(),
                        exit  = androidx.compose.animation.fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        Surface(
                            modifier = Modifier.padding(bottom = 24.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.55f)
                        ) {
                            Text(
                                "Drehen: 1 Finger  •  Zoom: 2 Finger",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
