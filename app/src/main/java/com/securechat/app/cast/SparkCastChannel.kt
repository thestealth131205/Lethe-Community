package com.securechat.app.cast

import org.json.JSONObject

/**
 * Custom Cast Channel für den SparksFeed.
 * Baut Scroll-Befehle (hoch/runter), Kommentare und Spark-Metadaten als JSON,
 * die über [CastDiscoveryManager.sendSparkMessage] an den Receiver gehen.
 * Namespace muss mit dem Receiver-HTML übereinstimmen.
 */
object SparkCastChannel {

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
