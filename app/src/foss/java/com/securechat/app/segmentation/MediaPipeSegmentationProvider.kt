package com.securechat.app.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FOSS/F-Droid-Selfie-Segmentierung via MediaPipe Tasks Vision (Apache-2.0, Modell
 * `selfie_segmenter.tflite` als App-Asset gebündelt, kein proprietäres SDK/keine Cloud-
 * Abhängigkeit – Ersatz für ML Kit Segmentation im [MlKitSegmentationProvider]).
 */
class MediaPipeSegmentationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : SegmentationProvider {

    companion object {
        private const val TAG = "LETHE_SEGMENTATION"
        private const val MODEL_ASSET = "selfie_segmenter.tflite"
    }

    private val segmenter: ImageSegmenter? = try {
        val baseOptions = BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build()
        val options = ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setOutputCategoryMask(false)
            .setOutputConfidenceMasks(true)
            .build()
        ImageSegmenter.createFromOptions(context, options)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "MediaPipe ImageSegmenter konnte nicht initialisiert werden")
        null
    }

    override fun segment(bitmap: Bitmap, rotationDegrees: Int): SegmentationMask? {
        val seg = segmenter ?: return null
        return try {
            val input = if (rotationDegrees == 0) bitmap else rotate(bitmap, rotationDegrees)
            val mpImage = BitmapImageBuilder(input).build()
            val result = seg.segment(mpImage)
            val masks = result.confidenceMasks().orElse(null) ?: return null
            if (masks.isEmpty()) return null
            // Selfie-Segmenter-Modell: Kategorie 0 = Hintergrund, 1 = Person (falls nur eine
            // Maske geliefert wird, ist es bereits die Personen-Konfidenzmaske).
            val mask = if (masks.size > 1) masks[1] else masks[0]
            val floatBuffer = ByteBufferExtractor.extract(mask).asFloatBuffer()
            val floats = FloatArray(floatBuffer.remaining())
            floatBuffer.get(floats)
            SegmentationMask(floats, mask.width, mask.height)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Segmentierung fehlgeschlagen")
            null
        }
    }

    override fun close() {
        try { segmenter?.close() } catch (_: Exception) {}
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SegmentationProviderModule {
    @Binds
    @Singleton
    abstract fun bindSegmentationProvider(impl: MediaPipeSegmentationProvider): SegmentationProvider
}
