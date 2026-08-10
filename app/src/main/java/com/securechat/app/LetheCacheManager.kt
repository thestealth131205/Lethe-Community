package com.securechat.app

import android.content.Context
import com.securechat.app.data.local.ProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Smart Cache Manager für Lethe.
 *
 * Verwaltet den persistenten Medien-Cache (filesDir/media_cache/<profil>/) des
 * aktuell aktiven Accounts sowie temporäre Spiel-Dateien (cacheDir/temp/games/)
 * mit drei Strategien:
 *  1. Sofortiges Löschen von Spiel-Temp-Dateien
 *  2. Zeit-basierte Eviction: Dateien älter als N Tage entfernen
 *  3. Größen-basierte Eviction: Cache auf maximal X MB begrenzen
 */
class LetheCacheManager(private val context: Context) {

    // Persistenter Medien-Cache des aktiven Profils (wird von MediaCache befüllt)
    private val mediaCacheRoot: File
        get() {
            val profile = ProfileManager.getActiveProfile(context).ifBlank { "default" }
            return File(context.filesDir, "media_cache/$profile").also { it.mkdirs() }
        }

    // Temporäre Spiel-Dateien im System-Cache (werden vom OS automatisch gelöscht)
    private val tempGamesDir: File
        get() = File(context.cacheDir, "temp/games").also { it.mkdirs() }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Löscht sofort alle temporären Spiel-Dateien (z. B. nach Sketch-n-Check-Sendung). */
    suspend fun clearGameTempFiles(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            tempGamesDir.deleteRecursively()
            tempGamesDir.mkdirs()
            Timber.tag("LETHE_CACHE").d("Spiel-Temp-Dateien gelöscht")
            true
        } catch (e: Exception) {
            Timber.tag("LETHE_CACHE").e(e, "clearGameTempFiles fehlgeschlagen")
            false
        }
    }

    /**
     * Entfernt gecachte Mediendateien, die älter als [daysOld] Tage sind.
     * @return Anzahl der gelöschten Dateien
     */
    suspend fun cleanOldMedia(daysOld: Int = 7): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysOld.toLong())
        var deleted = 0

        if (mediaCacheRoot.exists()) {
            mediaCacheRoot.walk()
                .filter { it.isFile && it.lastModified() < cutoff }
                .forEach { file ->
                    if (file.delete()) {
                        deleted++
                        Timber.tag("LETHE_CACHE").v("Veraltet gelöscht: ${file.name}")
                    }
                }
        }
        Timber.tag("LETHE_CACHE").d("cleanOldMedia(${daysOld}d): $deleted Dateien entfernt")
        deleted
    }

    /**
     * Begrenzt den Medien-Cache auf [maxMb] MB (LRU – älteste Dateien zuerst).
     */
    suspend fun enforceMaxCacheSize(maxMb: Int = 500): Boolean = withContext(Dispatchers.IO) {
        val maxBytes = maxMb * 1024L * 1024L

        val allFiles = if (mediaCacheRoot.exists()) {
            mediaCacheRoot.walk().filter { it.isFile }.sortedBy { it.lastModified() }.toList()
        } else emptyList()

        var totalSize = allFiles.sumOf { it.length() }

        if (totalSize > maxBytes) {
            for (file in allFiles) {
                if (totalSize <= maxBytes) break
                val size = file.length()
                if (file.delete()) {
                    totalSize -= size
                    Timber.tag("LETHE_CACHE").v("Größenlimit: gelöscht ${file.name}")
                }
            }
            Timber.tag("LETHE_CACHE")
                .d("enforceMaxCacheSize(${maxMb}MB): Cache auf %.1f MB reduziert".format(totalSize / 1024.0 / 1024.0))
        }
        true
    }

    /** Gibt die aktuelle Gesamtgröße des Medien-Cache in MB zurück. */
    suspend fun getCacheSizeInMb(): Double = withContext(Dispatchers.IO) {
        if (!mediaCacheRoot.exists()) return@withContext 0.0
        mediaCacheRoot.walk().filter { it.isFile }.sumOf { it.length() } / (1024.0 * 1024.0)
    }
}
