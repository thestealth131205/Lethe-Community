package com.securechat.app.data.local

import android.content.Context

/**
 * Verwaltet das aktuell aktive Nutzerprofil (Fake-Nummer).
 * Gespeichert in normalen SharedPreferences (kein DataStore),
 * damit der Datenbankname beim App-Start direkt verfügbar ist.
 */
object ProfileManager {

    private const val PREFS_NAME = "securechat_profile"
    private const val KEY_FAKE_NUMBER = "active_fake_number"
    private const val KEY_ORIGINAL_FAKE_NUMBER = "original_fake_number"

    /** Liefert die sanitized aktive Fake-Nummer für DB/DataStore-Namen. */
    fun getActiveProfile(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FAKE_NUMBER, "") ?: ""
    }

    /** Liefert die originale Fake-Nummer (mit Sonderzeichen, z.B. +49...) für API/Anzeige. */
    fun getOriginalFakeNumber(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ORIGINAL_FAKE_NUMBER, "") ?: ""
    }

    /** Setzt die aktive Fake-Nummer (wird beim Login aufgerufen). */
    fun setActiveProfile(context: Context, fakeNumber: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FAKE_NUMBER, sanitize(fakeNumber))
            .putString(KEY_ORIGINAL_FAKE_NUMBER, fakeNumber)
            .apply()
    }

    /**
     * Bereinigt die Fake-Nummer für den Einsatz als Datei-/DB-Name.
     * Nur Buchstaben, Ziffern und Unterstriche erlaubt.
     */
    fun sanitize(fakeNumber: String): String =
        fakeNumber.replace(Regex("[^A-Za-z0-9_]"), "_").take(40)

    /** Liefert den DB-Namen für das aktuelle Profil. */
    fun dbName(context: Context): String {
        val profile = getActiveProfile(context)
        return if (profile.isEmpty()) "secure_chat_database"
        else "secure_chat_$profile"
    }

    /** Liefert den DataStore-Namen für das aktuelle Profil. */
    fun dataStoreName(context: Context): String {
        val profile = getActiveProfile(context)
        return if (profile.isEmpty()) "secure_chat_settings"
        else "secure_chat_settings_$profile"
    }
}
