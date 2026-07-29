package com.securechat.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Zentrale FOSS-Ersatz-Helfer für Karten & Standort (ersetzt Google Maps + FusedLocation).
 * osmdroid (OpenStreetMap) für die Kartendarstellung, das native Android-[LocationManager]-API
 * für Standortabfragen – beides ohne Google Play Services, in beiden App-Flavors identisch.
 */

/**
 * Initialisiert osmdroid einmalig: setzt einen App-spezifischen User-Agent (tile.openstreetmap.org
 * blockiert den Default-UA) und legt den Tile-Cache in den privaten App-Speicher, damit keine
 * Storage-Berechtigung nötig ist. Muss vor dem ersten Anzeigen einer MapView aufgerufen werden.
 */
fun configureOsmdroid(context: Context) {
    val cfg = Configuration.getInstance()
    cfg.userAgentValue = "LetheApp/Android (contact@letheapp.de)"
    val base = File(context.filesDir, "osmdroid")
    if (!base.exists()) base.mkdirs()
    cfg.osmdroidBasePath = base
    cfg.osmdroidTileCache = File(base, "tiles")
}

/**
 * Holt einmalig eine aktuelle Position über das native [LocationManager]-API.
 * Fordert ein einzelnes Update vom besten aktiven Provider an; nach 10 s Timeout wird
 * ersatzweise die letzte bekannte Position geliefert (oder null). Ergebnis kommt immer
 * auf dem Main-Thread. Aufrufer müssen die Standort-Berechtigung vorher geprüft haben.
 */
@SuppressLint("MissingPermission")
fun getCurrentLocationOnce(context: Context, onResult: (Location?) -> Unit) {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (lm == null) { onResult(null); return }
    val provider = when {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }
    if (provider == null) {
        onResult(lastKnown(lm))
        return
    }
    val handler = Handler(Looper.getMainLooper())
    var delivered = false
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (delivered) return
            delivered = true
            lm.removeUpdates(this)
            handler.removeCallbacksAndMessages(null)
            onResult(location)
        }
        override fun onProviderDisabled(provider: String) {}
        override fun onProviderEnabled(provider: String) {}
        @Deprecated("Erforderlich für ältere API-Level, keine Logik nötig")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }
    try {
        lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
    } catch (e: Exception) {
        onResult(lastKnown(lm)); return
    }
    handler.postDelayed({
        if (delivered) return@postDelayed
        delivered = true
        lm.removeUpdates(listener)
        onResult(lastKnown(lm))
    }, 10_000L)
}

@SuppressLint("MissingPermission")
private fun lastKnown(lm: LocationManager): Location? = try {
    lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
} catch (e: Exception) { null }

/**
 * Rendert ein rundes Marker-Icon für die Karte: kreisförmiges Profilbild mit weißem Rand,
 * oder – falls kein Bild vorhanden – ein farbiger Kreis mit dem Anfangsbuchstaben des Namens.
 * Wird als osmdroid-[org.osmdroid.views.overlay.Marker]-Icon verwendet.
 */
fun avatarMarkerBitmap(avatar: Bitmap?, name: String, sizePx: Int = 120): Bitmap {
    val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val outerR = sizePx / 2f
    val border = sizePx * 0.08f
    val innerR = outerR - border

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Color.WHITE
    canvas.drawCircle(cx, cy, outerR, paint)

    if (avatar != null) {
        val d = (innerR * 2).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(avatar, d, d, true)
        val shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val m = Matrix()
        m.setTranslate(cx - innerR, cy - innerR)
        shader.setLocalMatrix(m)
        val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        avatarPaint.shader = shader
        canvas.drawCircle(cx, cy, innerR, avatarPaint)
    } else {
        paint.color = Color.parseColor("#1565C0")
        canvas.drawCircle(cx, cy, innerR, paint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        textPaint.color = Color.WHITE
        textPaint.textSize = innerR
        textPaint.textAlign = Paint.Align.CENTER
        val letter = name.trim().take(1).uppercase().ifEmpty { "?" }
        val fm = textPaint.fontMetrics
        canvas.drawText(letter, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint)
    }
    return out
}
