package com.securechat.app.cast

import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.framework.CastSession
import org.json.JSONObject

/**
 * Custom Cast Channel für den SparksFeed.
 * Sendet Scroll-Befehle (hoch/runter) und Kommentare an den Receiver.
 * Namespace muss mit dem Receiver-HTML übereinstimmen.
 */
class SparkCastChannel : Cast.MessageReceivedCallback {

    companion object {
        const val NAMESPACE = "urn:x-cast:com.lethe.sparks"

        fun buildScrollMessage(direction: String): String =
            JSONObject().apply {
                put("type", "scroll")
                put("direction", direction) // "up" oder "down"
            }.toString()

        fun buildCommentMessage(authorName: String, text: String): String =
            JSONObject().apply {
                put("type", "comment")
                put("author", authorName)
                put("text", text)
            }.toString()

        fun buildSparkMessage(
            sparkId: String,
            videoUrl: String?,
            title: String?,
            creatorName: String?,
            imageUrls: List<String>? = null,
            musicUrl: String? = null,
            imageIndex: Int = 0,
            description: String? = null
        ): String = JSONObject().apply {
            put("type", "spark")
            put("sparkId", sparkId)
            put("videoUrl", videoUrl ?: "")
            put("title", title ?: "")
            put("creator", creatorName ?: "")
            if (!imageUrls.isNullOrEmpty()) put("imageUrls", org.json.JSONArray(imageUrls))
            if (!musicUrl.isNullOrEmpty()) put("musicUrl", musicUrl)
            if (imageIndex > 0) put("imageIndex", imageIndex)
            if (!description.isNullOrEmpty()) put("description", description)
        }.toString()
    }

    override fun onMessageReceived(device: CastDevice, namespace: String, message: String) {
        // Eingehende Nachrichten vom Receiver (z. B. D-Pad-Scroll-Bestätigungen)
    }
}

/**
 * Sendet eine Nachricht über den SparkCastChannel.
 * Gibt false zurück wenn keine aktive Session vorhanden.
 */
fun CastSession.sendSparkMessage(message: String): Boolean {
    return try {
        sendMessage(SparkCastChannel.NAMESPACE, message)
        true
    } catch (_: Exception) {
        false
    }
}
