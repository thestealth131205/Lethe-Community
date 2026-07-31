package com.securechat.app.facedetection

import android.graphics.Bitmap

/**
 * Transport-unabhängige Gesichtserkennung für die Liveness-Prüfung bei der
 * Altersverifikation (Kopf-Gier-Winkel: geradeaus/links/rechts, siehe AgeVerificationScreen).
 *
 * Der `playstore`-Flavor nutzt ML Kit Face Detection
 * ([com.securechat.app.facedetection.MlKitFaceDetectionProvider], bisheriges Verhalten
 * unverändert). Der `foss`/F-Droid-Flavor nutzt OpenCV Haar-Cascades (Apache-2.0, Modelle
 * als App-Assets gebündelt, kein proprietäres SDK,
 * [com.securechat.app.facedetection.OpenCvFaceDetectionProvider]).
 *
 * Beide Implementierungen sind synchron/blockierend – muss auf einem Hintergrund-Thread
 * aufgerufen werden (wie bisher der ML-Kit-Aufruf via CameraX-`cameraExecutor`).
 */
interface FaceDetectionProvider {
    /**
     * Liefert den Kopf-Gier-Winkel (Yaw, Grad; negativ = nach links, positiv = nach rechts
     * gedreht) des größten erkannten Gesichts, oder null falls kein Gesicht erkannt wurde.
     */
    fun detectHeadYaw(bitmap: Bitmap, rotationDegrees: Int): Float?

    /** Gibt native Ressourcen frei – beim Verlassen des Screens aufrufen. */
    fun close()
}
