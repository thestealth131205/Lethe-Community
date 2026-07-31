package com.securechat.app.facedetection

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Playstore-Gesichtserkennung via ML Kit Face Detection – identisches Verhalten wie vor
 * der Provider-Umstellung, nur hinter das [FaceDetectionProvider]-Interface gezogen.
 */
class MlKitFaceDetectionProvider @Inject constructor() : FaceDetectionProvider {

    companion object {
        private const val TAG = "LETHE_FACEDETECT"
    }

    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    override fun detectHeadYaw(bitmap: Bitmap, rotationDegrees: Int): Float? {
        return try {
            val image = InputImage.fromBitmap(bitmap, rotationDegrees)
            val faces = Tasks.await(detector.process(image))
            faces.firstOrNull()?.headEulerAngleY
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Gesichtserkennung fehlgeschlagen")
            null
        }
    }

    override fun close() {
        detector.close()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FaceDetectionProviderModule {
    @Binds
    @Singleton
    abstract fun bindFaceDetectionProvider(impl: MlKitFaceDetectionProvider): FaceDetectionProvider
}
