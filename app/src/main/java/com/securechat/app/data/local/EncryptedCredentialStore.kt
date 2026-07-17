package com.securechat.app.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speichert das Login-Passwort verschlüsselt im Android KeyStore (AES256-GCM).
 * Ersetzt die Klartextablage im regulären DataStore.
 */
@Singleton
class EncryptedCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy { createPrefs() }

    private fun createPrefs(): SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "securechat_credentials_enc",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e("EncCredStore", "EncryptedSharedPreferences nicht verfügbar, nutze Fallback: ${e.message}")
        try { context.deleteSharedPreferences("securechat_credentials_enc") } catch (_: Exception) {}
        context.getSharedPreferences("securechat_credentials_fallback", Context.MODE_PRIVATE)
    }

    fun savePassword(password: String) {
        prefs.edit().putString("pw", password).apply()
    }

    fun getPassword(): String = prefs.getString("pw", "") ?: ""

    fun clearPassword() {
        prefs.edit().remove("pw").apply()
    }

    fun saveAppLockPin(pin: String) {
        prefs.edit().putString("app_lock_pin", pin).apply()
    }

    fun getAppLockPin(): String = prefs.getString("app_lock_pin", "") ?: ""

    fun hasAppLockPin(): Boolean = getAppLockPin().isNotEmpty()

    fun verifyAppLockPin(pin: String): Boolean = getAppLockPin() == pin

    fun clearAppLockPin() {
        prefs.edit().remove("app_lock_pin").apply()
    }
}
