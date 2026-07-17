@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.lethe.mediaplayer.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SmartCache für bereits gehörte Songs (Lethe-Bibliothek + Audius): ExoPlayer schreibt
 * die Audio-Daten während der Wiedergabe transparent auf die Festplatte, sodass ein
 * erneutes Abspielen desselben Titels ihn nicht nochmal herunterlädt. LRU-Eviction hält
 * den Cache auf der in den Einstellungen konfigurierten Maximalgröße (Default 7% des
 * Gerätespeichers, max. 40 GB). Die Größe wird beim Erstellen des Caches (Prozessstart)
 * einmalig gelesen — Änderungen greifen ab dem nächsten Start.
 */
@Singleton
class MediaCache @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: PlaybackSettings
) {
    private val appContext = context

    val cache: SimpleCache by lazy {
        val cacheDir = File(appContext.filesDir, "media_smart_cache")
        val evictor: CacheEvictor = LeastRecentlyUsedCacheEvictor(settings.cacheMaxBytes.value)
        val databaseProvider = StandaloneDatabaseProvider(appContext)
        SimpleCache(cacheDir, evictor, databaseProvider)
    }

    /**
     * Verzeichnis für von außen importierte Audiodateien (z.B. "Öffnen mit" aus einem
     * Dateimanager, da der Lethe Medie Player sich als Musik-Player bei Android registriert).
     * Liegt bewusst NEBEN dem SimpleCache-Verzeichnis (nicht darin!) — SimpleCache verwaltet
     * sein Verzeichnis exklusiv über einen eigenen Index; fremde Dateien darin würden die
     * Cache-Datenbank beschädigen.
     */
    val importedAudioDir: File by lazy {
        File(appContext.filesDir, "media_smart_cache_imports").apply { mkdirs() }
    }

    /** Aktuell belegter Speicherplatz des SmartCaches in Bytes (Streaming-Cache + Importe). */
    val usedBytes: Long
        get() = cache.cacheSpace + (importedAudioDir.listFiles()?.sumOf { it.length() } ?: 0L)
}
