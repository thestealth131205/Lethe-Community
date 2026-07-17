package com.lethe.mediaplayer.player

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lethe.mediaplayer.data.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automatischer Download für Favoriten/Playlists: Schaltet der Nutzer eine Favoriten- oder
 * Playlist-Ansicht auf "Automatischer Download", werden alle enthaltenen Titel als echte Datei
 * in einen App-eigenen Unterordner (Music/LetheMediaPlayer/<Name>) des öffentlichen Musik-
 * speichers geschrieben (MediaStore ab Android 10, sonst direkter Dateipfad). Wiedergabe nutzt
 * danach [localUriFor] statt der Stream-URL. Zustand (welche Playlist-Keys aktiv sind + welcher
 * Track wohin heruntergeladen wurde) liegt persistent in SharedPreferences (Gson-JSON).
 */
@Singleton
class AutoDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) {
    private val prefs = context.getSharedPreferences("auto_download_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _enabledKeys = MutableStateFlow(loadEnabledKeys())
    val enabledKeys: StateFlow<Set<String>> = _enabledKeys

    /** trackId -> lokale Content-/Datei-Uri (als String) */
    private val _localTracks = MutableStateFlow(loadLocalTracks())
    val localTracks: StateFlow<Map<String, String>> = _localTracks

    private val _downloadingKeys = MutableStateFlow<Set<String>>(emptySet())
    val downloadingKeys: StateFlow<Set<String>> = _downloadingKeys

    fun isEnabled(key: String): Boolean = _enabledKeys.value.contains(key)

    fun localUriFor(trackId: String): String? = _localTracks.value[trackId]

    /** Schaltet den automatischen Download für [key] (Favoriten oder eine Playlist) um. */
    fun setEnabled(key: String, enabled: Boolean, tracks: List<Track>) {
        val set = _enabledKeys.value.toMutableSet()
        if (enabled) set.add(key) else set.remove(key)
        _enabledKeys.value = set
        persistEnabledKeys(set)
        if (enabled) downloadAll(key, tracks) else removeAll(tracks)
    }

    /** Wird nach dem Nachladen einer Playlist erneut aufgerufen, um neue Titel nachzuziehen. */
    fun syncIfEnabled(key: String, tracks: List<Track>) {
        if (isEnabled(key)) downloadAll(key, tracks)
    }

    private fun downloadAll(key: String, tracks: List<Track>) {
        scope.launch {
            _downloadingKeys.value = _downloadingKeys.value + key
            try {
                val subfolder = key.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60)
                for (track in tracks) {
                    if (_localTracks.value.containsKey(track.id)) continue
                    runCatching { downloadTrack(subfolder, track) }
                }
            } finally {
                _downloadingKeys.value = _downloadingKeys.value - key
            }
        }
    }

    private fun downloadTrack(subfolder: String, track: Track) {
        val request = Request.Builder().url(track.audioUrl).build()
        client.newCall(request).execute().use { response ->
            val body = response.body ?: return
            val fileName = "${track.id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.mp3"
            val uri = if (Build.VERSION.SDK_INT >= 29) {
                saveViaMediaStore(subfolder, fileName, body.byteStream())
            } else {
                saveViaLegacyFile(subfolder, fileName, body.byteStream())
            } ?: return
            val map = _localTracks.value.toMutableMap()
            map[track.id] = uri.toString()
            _localTracks.value = map
            persistLocalTracks(map)
        }
    }

    private fun saveViaMediaStore(subfolder: String, fileName: String, input: java.io.InputStream): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/LetheMediaPlayer/$subfolder")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(uri)?.use { out -> input.copyTo(out) }
        values.clear()
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    /** Vor Android 10 (Scoped Storage): direkter Dateipfad im öffentlichen Musik-Verzeichnis. */
    @Suppress("DEPRECATION")
    private fun saveViaLegacyFile(subfolder: String, fileName: String, input: java.io.InputStream): Uri? {
        val musicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "LetheMediaPlayer/$subfolder"
        )
        if (!musicDir.exists() && !musicDir.mkdirs()) return null
        val file = File(musicDir, fileName)
        file.outputStream().use { out -> input.copyTo(out) }
        return Uri.fromFile(file)
    }

    private fun removeAll(tracks: List<Track>) {
        val resolver = context.contentResolver
        val map = _localTracks.value.toMutableMap()
        for (track in tracks) {
            val uriStr = map[track.id] ?: continue
            runCatching { resolver.delete(Uri.parse(uriStr), null, null) }
            runCatching { Uri.parse(uriStr).path?.let { File(it).delete() } }
            map.remove(track.id)
        }
        _localTracks.value = map
        persistLocalTracks(map)
    }

    private fun loadEnabledKeys(): Set<String> {
        val json = prefs.getString(KEY_ENABLED, null) ?: return emptySet()
        val type = object : TypeToken<Set<String>>() {}.type
        return runCatching { gson.fromJson<Set<String>>(json, type) }.getOrNull() ?: emptySet()
    }

    private fun persistEnabledKeys(set: Set<String>) {
        prefs.edit().putString(KEY_ENABLED, gson.toJson(set)).apply()
    }

    private fun loadLocalTracks(): Map<String, String> {
        val json = prefs.getString(KEY_LOCAL, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, String>>() {}.type
        return runCatching { gson.fromJson<Map<String, String>>(json, type) }.getOrNull() ?: emptyMap()
    }

    private fun persistLocalTracks(map: Map<String, String>) {
        prefs.edit().putString(KEY_LOCAL, gson.toJson(map)).apply()
    }

    companion object {
        private const val KEY_ENABLED = "enabled_keys"
        private const val KEY_LOCAL = "local_tracks"

        /** Deterministischer Playlist-Key für die Favoriten (analog zum Server, s. Backend). */
        const val FAVORITES_KEY = "favorites"
    }
}
