package com.securechat.app.data

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

/**
 * Generates a stable device fingerprint for new-device detection.
 * Combines Android ID with a persisted UUID (survives app reinstall on same device).
 * Hashed with SHA-256 for a consistent 64-char hex string.
 */
object DeviceIdentifier {

    private const val PREFS_NAME = "device_identity"
    private const val KEY_DEVICE_UUID = "device_uuid"

    fun getFingerprint(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedUuid = prefs.getString(KEY_DEVICE_UUID, null) ?: run {
            val newUuid = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_UUID, newUuid).apply()
            newUuid
        }

        val raw = "$androidId:$storedUuid"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
