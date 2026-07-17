package com.securechat.app.data.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verwaltet das Authentifizierungs-Token für API-Requests.
 * Token werden verschlüsselt gespeichert (AES256-GCM via EncryptedSharedPreferences).
 * Fallback auf normale SharedPreferences falls das Gerät EncryptedSharedPreferences
 * nicht unterstützt (z.B. Samsung Knox-Konflikte, beschädigter KeyStore).
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        createEncryptedPrefsOrFallback()
    }

    private fun createEncryptedPrefsOrFallback(): SharedPreferences {
        return try {
            // security-crypto:1.0.0 API: MasterKeys (Plural) + String-Alias statt MasterKey-Objekt
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "secure_chat_auth_enc",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Auf bestimmten Samsung-Geräten schlägt der AndroidKeyStore fehl.
            // Fallback auf normale (nicht verschlüsselte) SharedPreferences.
            Log.e("TokenManager", "EncryptedSharedPreferences nicht verfügbar, nutze Fallback: ${e.message}")
            // Veraltete verschlüsselte Datei löschen um Folgefehler zu vermeiden
            try {
                context.deleteSharedPreferences("secure_chat_auth_enc")
            } catch (ignored: Exception) {}
            context.getSharedPreferences("secure_chat_auth_fallback", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
    }

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpiredEvent: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    /** Wird aus dem TokenAuthenticator aufgerufen wenn Token-Refresh mit 401 scheitert. */
    fun signalSessionExpired() {
        _sessionExpired.tryEmit(Unit)
    }

    fun saveToken(token: String, userId: String) {
        prefs.edit().apply {
            putString(KEY_AUTH_TOKEN, token)
            putString(KEY_USER_ID, userId)
            apply()
        }
    }

    fun getToken(): String? = prefs.getString(KEY_AUTH_TOKEN, null)

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun hasToken(): Boolean = !getToken().isNullOrEmpty()

    fun clearToken() {
        prefs.edit().apply {
            remove(KEY_AUTH_TOKEN)
            remove(KEY_USER_ID)
            apply()
        }
    }
}
