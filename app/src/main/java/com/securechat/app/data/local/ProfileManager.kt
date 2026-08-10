package com.securechat.app.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Verwaltet das aktuell aktive Nutzerprofil (Fake-Nummer).
 * Gespeichert in normalen SharedPreferences (kein DataStore),
 * damit der Datenbankname beim App-Start direkt verfügbar ist.
 */
object ProfileManager {

    private const val PREFS_NAME = "securechat_profile"
    private const val KEY_FAKE_NUMBER = "active_fake_number"
    private const val KEY_ORIGINAL_FAKE_NUMBER = "original_fake_number"
    private const val KEY_SAVED_ACCOUNTS = "saved_accounts_json"
    private const val KEY_PENDING_RESTART = "pending_profile_switch_restart"

    private val gson = Gson()

    /** Für den Account-Switcher gespeicherte Zugangsdaten (Token liegt separat im TokenManager). */
    data class SavedAccount(
        val profileKey: String,
        val fakeNumber: String,
        val userId: String,
        val displayName: String,
        val profileImageUrl: String?,
        val lastUsed: Long
    )

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
        // WICHTIG: commit() statt apply() – der Aufrufer killt bei einem Profilwechsel
        // direkt danach den Prozess (Process.killProcess), damit Room/DataStore beim
        // Neustart mit dem richtigen profilspezifischen Namen geöffnet werden. apply()
        // würde asynchron schreiben und könnte das Rennen gegen den Kill verlieren.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FAKE_NUMBER, sanitize(fakeNumber))
            .putString(KEY_ORIGINAL_FAKE_NUMBER, fakeNumber)
            .commit()
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

    /**
     * Markiert einen bevorstehenden Profil-Wechsel-Neustart (vor Process.killProcess() aufzurufen).
     * MainActivity.onCreate() verwirft dadurch die vom System zwischengespeicherte SavedInstanceState
     * (NavController-Backstack inkl. Routen-Argumenten wie einer offenen Chat-contactId) beim nächsten
     * Start. Sonst überlebt diese Bundle-Restauration einen selbst ausgelösten killProcess() (die Task
     * bleibt in den Recents erhalten) und zeigt kurzzeitig/dauerhaft Inhalte des ALTEN Profils an (z.B.
     * eine Chat-Route mit einer contactId, die im neuen Profil zufällig auf einen anderen Kontakt trifft).
     * Ein manuelles Beenden über die Recents-Liste entfernt die Task dagegen komplett und verwirft die
     * Bundle von selbst – deckt sich mit der Beobachtung "erst nach komplettem Neustart wieder korrekt".
     */
    fun markPendingRestart(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING_RESTART, true)
            .commit()
    }

    /** Liest das Flag aus Schritt oben und löscht es sofort wieder (einmalig wirksam). */
    fun consumePendingRestart(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = prefs.getBoolean(KEY_PENDING_RESTART, false)
        if (pending) {
            prefs.edit().putBoolean(KEY_PENDING_RESTART, false).commit()
        }
        return pending
    }

    // ── Account-Switcher-Registry ──────────────────────────────────────────

    /** Liefert alle gespeicherten Accounts, neueste Nutzung zuerst. */
    fun getSavedAccounts(context: Context): List<SavedAccount> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SAVED_ACCOUNTS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SavedAccount>>() {}.type
            gson.fromJson<List<SavedAccount>>(json, type)
                ?.sortedByDescending { it.lastUsed }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Legt einen Account in der Switcher-Liste an oder aktualisiert ihn (z.B. Name/Bild geändert). */
    fun saveAccountToRegistry(context: Context, account: SavedAccount) {
        val updated = getSavedAccounts(context)
            .filterNot { it.profileKey == account.profileKey } + account
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVED_ACCOUNTS, gson.toJson(updated))
            .commit()
    }

    /** Entfernt einen Account aus der Switcher-Liste (z.B. bei explizitem Logout). */
    fun removeSavedAccount(context: Context, profileKey: String) {
        val updated = getSavedAccounts(context).filterNot { it.profileKey == profileKey }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVED_ACCOUNTS, gson.toJson(updated))
            .commit()
    }
}
