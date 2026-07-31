package com.securechat.app.facedetection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfRect
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FOSS/F-Droid-Gesichtserkennung via OpenCV Haar-Cascades (Apache-2.0, Kaskaden-XMLs als
 * App-Assets gebündelt, kein proprietäres SDK/keine Cloud-Abhängigkeit – Ersatz für ML Kit
 * Face Detection im [MlKitFaceDetectionProvider]).
 *
 * OpenCV liefert (anders als ML Kit) keinen kontinuierlichen 3D-Kopf-Gier-Winkel, sondern
 * nur Frontal-/Profilerkennung. Für die Liveness-Prüfung in AgeVerificationScreen
 * (Zustände geradeaus/links/rechts, Schwellenwerte ±15°/±30°) reicht das:
 * Frontalgesicht → 0°, Profil-Kaskade (Blick nach links im Bild) → -45°, Profil-Kaskade auf
 * horizontal gespiegeltem Bild (Blick nach rechts im Bild) → +45°.
 */
class OpenCvFaceDetectionProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : FaceDetectionProvider {

    companion object {
        private const val TAG = "LETHE_FACEDETECT"
        private const val ASSET_FRONTAL = "haarcascade_frontalface_default.xml"
        private const val ASSET_PROFILE = "haarcascade_profileface.xml"
    }

    private val ready: Boolean = try { OpenCVLoader.initLocal() } catch (e: Throwable) {
        Timber.tag(TAG).e(e, "OpenCV konnte nicht initialisiert werden")
        false
    }

    private val frontalDetector: CascadeClassifier? = if (ready) loadCascade(ASSET_FRONTAL) else null
    private val profileDetector: CascadeClassifier? = if (ready) loadCascade(ASSET_PROFILE) else null

    private fun loadCascade(assetName: String): CascadeClassifier? {
        return try {
            val outFile = File(context.cacheDir, assetName)
            if (!outFile.exists()) {
                context.assets.open(assetName).use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
            }
            val classifier = CascadeClassifier(outFile.absolutePath)
            if (classifier.empty()) null else classifier
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Haar-Kaskade $assetName konnte nicht geladen werden")
            null
        }
    }

    override fun detectHeadYaw(bitmap: Bitmap, rotationDegrees: Int): Float? {
        if (!ready) return null
        return try {
            val rotated = if (rotationDegrees == 0) bitmap else rotate(bitmap, rotationDegrees)
            val rgba = Mat()
            Utils.bitmapToMat(rotated, rgba)
            val gray = Mat()
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.equalizeHist(gray, gray)

            frontalDetector?.let { fd ->
                val faces = MatOfRect()
                fd.detectMultiScale(gray, faces)
                if (faces.toArray().isNotEmpty()) return 0f
            }
            profileDetector?.let { pd ->
                val faces = MatOfRect()
                pd.detectMultiScale(gray, faces)
                if (faces.toArray().isNotEmpty()) return -45f

                val flipped = Mat()
                Core.flip(gray, flipped, 1)
                val flippedFaces = MatOfRect()
                pd.detectMultiScale(flipped, flippedFaces)
                if (flippedFaces.toArray().isNotEmpty()) return 45f
            }
            null
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Gesichtserkennung fehlgeschlagen")
            null
        }
    }

    override fun close() {
        // CascadeClassifier hält keine expliziten nativen Ressourcen, die freigegeben werden müssen.
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FaceDetectionProviderModule {
    @Binds
    @Singleton
    abstract fun bindFaceDetectionProvider(impl: OpenCvFaceDetectionProvider): FaceDetectionProvider
}
