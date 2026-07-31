package com.securechat.app.segmentation

import android.graphics.Bitmap

/**
 * Ergebnis einer Selfie-Segmentierung: pro Pixel eine Vordergrund(Personen)-Konfidenz
 * (0f..1f) im (meist herunterskalierten) Masken-Raster [width]×[height].
 */
data class SegmentationMask(val buffer: FloatArray, val width: Int, val height: Int)

/**
 * Transport-unabhängige Selfie-Segmentierung (Person vs. Hintergrund), genutzt für
 * Sticker-Freistellung ([com.securechat.app.ui.screens] StickerCreatorSheet),
 * Video-Call-Hintergrundunschärfe ([com.securechat.app.data.webrtc.BackgroundBlurCapturerObserver])
 * und Live-Stream-Greenscreen ([com.securechat.app.data.local.LiveSilhouetteFilter] via
 * CreatorLiveScreen).
 *
 * Der `playstore`-Flavor nutzt ML Kit Selfie Segmentation
 * ([com.securechat.app.segmentation.MlKitSegmentationProvider], bisheriges Verhalten
 * unverändert). Der `foss`/F-Droid-Flavor nutzt MediaPipe Tasks Vision ImageSegmenter
 * (Apache-2.0, Modell als App-Asset gebündelt, kein proprietäres SDK,
 * [com.securechat.app.segmentation.MediaPipeSegmentationProvider]).
 *
 * Beide Implementierungen sind synchron/blockierend – muss auf einem Hintergrund-Thread
 * aufgerufen werden (wie bisher der ML-Kit-Aufruf via dedizierten Executor).
 */
interface SegmentationProvider {
    /** Liefert die Segmentierungsmaske oder null bei Fehler. [rotationDegrees] wie bei ML Kit InputImage. */
    fun segment(bitmap: Bitmap, rotationDegrees: Int): SegmentationMask?

    /** Gibt native Ressourcen frei – beim Verlassen des Screens/Streams aufrufen. */
    fun close()
}
