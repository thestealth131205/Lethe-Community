package com.securechat.app.segmentation

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Playstore-Selfie-Segmentierung via ML Kit Selfie Segmentation – identisches Verhalten
 * wie vor der Provider-Umstellung, nur hinter das [SegmentationProvider]-Interface gezogen.
 */
class MlKitSegmentationProvider @Inject constructor() : SegmentationProvider {

    companion object {
        private const val TAG = "LETHE_SEGMENTATION"
    }

    private val segmenter: Segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .enableRawSizeMask()
            .build()
    )

    override fun segment(bitmap: Bitmap, rotationDegrees: Int): SegmentationMask? {
        return try {
            val image = InputImage.fromBitmap(bitmap, rotationDegrees)
            val result = Tasks.await(segmenter.process(image))
            val buf = result.buffer
            buf.rewind()
            val floats = FloatArray(result.width * result.height)
            buf.asFloatBuffer().get(floats)
            SegmentationMask(floats, result.width, result.height)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Segmentierung fehlgeschlagen")
            null
        }
    }

    override fun close() {
        segmenter.close()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SegmentationProviderModule {
    @Binds
    @Singleton
    abstract fun bindSegmentationProvider(impl: MlKitSegmentationProvider): SegmentationProvider
}
