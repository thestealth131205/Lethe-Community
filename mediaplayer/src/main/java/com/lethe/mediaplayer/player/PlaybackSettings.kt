package com.lethe.mediaplayer.player

import android.content.Context
import android.os.StatFs
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lethe.mediaplayer.data.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistente Audio-Einstellungen (Crossfade an/aus + Dauer 1–10 s, SmartCache-Größe,
 * zuletzt gehörter Titel + Position). Wird sowohl vom Service (Wiedergabe) als auch von
 * der UI (Bottom-Sheet/App-Infos) gelesen.
 */
@Singleton
class PlaybackSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("lethe_player_settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _crossfadeEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled

    private val _crossfadeSeconds = MutableStateFlow(prefs.getInt(KEY_SECONDS, 4).coerceIn(1, 10))
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds

    private val _cacheMaxBytes = MutableStateFlow(
        if (prefs.contains(KEY_CACHE_MAX_BYTES)) {
            prefs.getLong(KEY_CACHE_MAX_BYTES, defaultCacheMaxBytes()).coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)
        } else {
            defaultCacheMaxBytes()
        }
    )
    val cacheMaxBytes: StateFlow<Long> = _cacheMaxBytes

    fun setCrossfadeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _crossfadeEnabled.value = enabled
    }

    fun setCrossfadeSeconds(sec: Int) {
        val v = sec.coerceIn(1, 10)
        prefs.edit().putInt(KEY_SECONDS, v).apply()
        _crossfadeSeconds.value = v
    }

    fun setCacheMaxBytes(bytes: Long) {
        val v = bytes.coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)
        prefs.edit().putLong(KEY_CACHE_MAX_BYTES, v).apply()
        _cacheMaxBytes.value = v
    }

    /** Merkt sich die zuletzt gespielte Warteschlange + aktuellen Index + Wiedergabeposition,
     * damit die App bzw. Android Auto nach dem Beenden/Neustarten genau dort weiterhören kann
     * (inkl. funktionierendem Vor/Zurück, da die ganze Liste erhalten bleibt). */
    fun saveLastQueue(tracks: List<Track>, index: Int, positionMs: Long) {
        if (tracks.isEmpty()) return
        prefs.edit()
            .putString(KEY_LAST_QUEUE, gson.toJson(tracks))
            .putInt(KEY_LAST_INDEX, index.coerceIn(0, tracks.size - 1))
            .putLong(KEY_LAST_POSITION, positionMs)
            .apply()
    }

    /** Zuletzt gespielte Warteschlange + Index + Position, falls vorhanden. */
    fun getLastQueue(): Triple<List<Track>, Int, Long>? {
        val json = prefs.getString(KEY_LAST_QUEUE, null) ?: return null
        val type = object : TypeToken<List<Track>>() {}.type
        val tracks = runCatching { gson.fromJson<List<Track>>(json, type) }.getOrNull()
            ?.takeIf { it.isNotEmpty() } ?: return null
        val index = prefs.getInt(KEY_LAST_INDEX, 0).coerceIn(0, tracks.size - 1)
        return Triple(tracks, index, prefs.getLong(KEY_LAST_POSITION, 0L))
    }

    /** 7% des Gerätespeichers, gedeckelt auf 40 GB. */
    private fun defaultCacheMaxBytes(): Long {
        val total = runCatching { StatFs(context.filesDir.path).totalBytes }.getOrDefault(MAX_CACHE_BYTES)
        return (total * 0.07).toLong().coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)
    }

    companion object {
        private const val KEY_ENABLED = "crossfade_enabled"
        private const val KEY_SECONDS = "crossfade_seconds"
        private const val KEY_CACHE_MAX_BYTES = "cache_max_bytes"
        private const val KEY_LAST_QUEUE = "last_queue_json"
        private const val KEY_LAST_INDEX = "last_queue_index"
        private const val KEY_LAST_POSITION = "last_track_position_ms"
        const val MIN_CACHE_BYTES = 100L * 1024 * 1024
        const val MAX_CACHE_BYTES = 40L * 1024 * 1024 * 1024
    }
}
