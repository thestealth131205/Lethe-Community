package com.securechat.app

import android.content.Context

/**
 * Persistente Zähler der meistgehörten Musiktitel.
 *
 * Jeder Wiedergabestart erhöht den Zähler für den Track-Schlüssel (Play-URL bzw.
 * "audius:{id}"), sodass die meistgehörten Titel in Sortierungen oben stehen.
 * Gespeichert in SharedPreferences.
 */
object MusicPlayCountStore {

    private const val PREFS = "lethe_music_play_counts"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Erhöht den Wiedergabezähler für den angegebenen Schlüssel um 1. */
    fun increment(context: Context, key: String?) {
        if (key.isNullOrBlank()) return
        val p = prefs(context)
        p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
    }

    /** Aktueller Wiedergabezähler des Schlüssels (0, wenn noch nie gehört). */
    fun count(context: Context, key: String?): Int {
        if (key.isNullOrBlank()) return 0
        return prefs(context).getInt(key, 0)
    }
}
