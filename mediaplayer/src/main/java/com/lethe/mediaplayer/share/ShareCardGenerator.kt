package com.lethe.mediaplayer.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lethe.mediaplayer.data.Track
import java.io.File
import java.io.FileOutputStream

/**
 * Erstellt ein 9:16-Sharepic (PNG) für einen Track: Cover als verdunkelter Hintergrund,
 * zentriertes rundes Cover mit Schatten, Titel/Interpret und dekorative Player-Optik
 * (Fortschrittsbalken + Play-Button). Wird über den Teilen-Button in der Now-Playing-
 * Ansicht ausgelöst; das Bild landet im Cache-Verzeichnis und wird per FileProvider geteilt.
 */
object ShareCardGenerator {

    private const val WIDTH = 1080
    private const val HEIGHT = 1920

    suspend fun generate(context: Context, track: Track, artwork: Any? = null): Uri? {
        val cover = loadArtworkBitmap(context, artwork) ?: loadCoverBitmap(context, track.coverUrl)
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas, cover)
        drawCoverArt(canvas, cover)
        drawTexts(canvas, track.title, track.artist)
        drawPlayerControls(canvas)
        drawWatermark(canvas)

        return runCatching { saveToCache(context, bitmap, track.id) }.getOrNull()
    }

    // Cover aus der laufenden MediaSession (eingebettete ID3-Artwork als ByteArray oder Uri).
    private suspend fun loadArtworkBitmap(context: Context, artwork: Any?): Bitmap? = when (artwork) {
        is ByteArray -> runCatching {
            BitmapFactory.decodeByteArray(artwork, 0, artwork.size)
        }.getOrNull()
        is Uri -> loadCoverBitmap(context, artwork.toString())
        else -> null
    }

    private suspend fun loadCoverBitmap(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            (result as? SuccessResult)?.drawable?.toBitmap()
        }.getOrNull()
    }

    private fun drawBackground(canvas: Canvas, cover: Bitmap?) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (cover != null) {
            val scale = maxOf(WIDTH.toFloat() / cover.width, HEIGHT.toFloat() / cover.height)
            val dx = (WIDTH - cover.width * scale) / 2f
            val dy = (HEIGHT - cover.height * scale) / 2f
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(dx, dy)
            }
            canvas.drawBitmap(cover, matrix, paint)
        } else {
            canvas.drawColor(Color.parseColor("#1A1033"))
        }
        // Dunkler Verlauf oben transparent -> unten fast schwarz, damit Text lesbar bleibt.
        val gradient = LinearGradient(
            0f, 0f, 0f, HEIGHT.toFloat(),
            intArrayOf(Color.argb(120, 0, 0, 0), Color.argb(210, 10, 5, 25), Color.argb(250, 5, 2, 15)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
    }

    private fun drawCoverArt(canvas: Canvas, cover: Bitmap?) {
        val size = 760f
        val left = (WIDTH - size) / 2f
        val top = 260f
        val rect = RectF(left, top, left + size, top + size)
        val radius = 48f

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 0, 0, 0)
            setShadowLayer(50f, 0f, 24f, Color.argb(160, 0, 0, 0))
        }
        canvas.drawRoundRect(rect, radius, radius, shadowPaint)

        val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        if (cover != null) {
            val scale = maxOf(size / cover.width, size / cover.height)
            val dx = left - (cover.width * scale - size) / 2f
            val dy = top - (cover.height * scale - size) / 2f
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(dx, dy)
            }
            canvas.drawBitmap(cover, matrix, Paint(Paint.ANTI_ALIAS_FLAG))
        } else {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3D2C6B") }
            canvas.drawRect(rect, p)
        }
        canvas.restore()
    }

    private fun drawTexts(canvas: Canvas, title: String, artist: String) {
        val centerX = WIDTH / 2f
        var y = 260f + 760f + 130f

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 64f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val ellipsizedTitle = TextUtils.ellipsize(title, titlePaint, WIDTH - 140f, TextUtils.TruncateAt.END)
        canvas.drawText(ellipsizedTitle.toString(), centerX, y, titlePaint)

        y += 70f
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 255, 255, 255)
            textSize = 42f
            textAlign = Paint.Align.CENTER
        }
        val ellipsizedArtist = TextUtils.ellipsize(artist, artistPaint, WIDTH - 140f, TextUtils.TruncateAt.END)
        canvas.drawText(ellipsizedArtist.toString(), centerX, y, artistPaint)
    }

    private fun drawPlayerControls(canvas: Canvas) {
        val centerX = WIDTH / 2f
        val barY = 1500f
        val barWidth = 760f
        val barLeft = centerX - barWidth / 2f

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
        canvas.drawRoundRect(RectF(barLeft, barY, barLeft + barWidth, barY + 8f), 4f, 4f, trackPaint)
        val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7C4DFF") }
        canvas.drawRoundRect(RectF(barLeft, barY, barLeft + barWidth * 0.35f, barY + 8f), 4f, 4f, progressPaint)

        val playCenterY = barY + 140f
        val radius = 70f
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(centerX, playCenterY, radius, circlePaint)
        val trianglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1033") }
        val triSize = 34f
        val path = Path().apply {
            moveTo(centerX - triSize / 2f, playCenterY - triSize)
            lineTo(centerX - triSize / 2f, playCenterY + triSize)
            lineTo(centerX + triSize, playCenterY)
            close()
        }
        canvas.drawPath(path, trianglePaint)
    }

    private fun drawWatermark(canvas: Canvas) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
            textSize = 40f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Lethe Media Player", WIDTH / 2f, HEIGHT - 90f, paint)
    }

    private fun saveToCache(context: Context, bitmap: Bitmap, trackId: String): Uri {
        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val safeId = trackId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "share_track_$safeId.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
