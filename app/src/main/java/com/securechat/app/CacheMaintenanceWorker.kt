package com.securechat.app

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Periodischer Hintergrund-Worker (alle 6 Stunden via WorkManager).
 *
 * Begrenzt den persistenten Medien-Cache (MediaCache/LetheCacheManager) auch dann,
 * wenn die App über längere Zeit nicht geschlossen wird – bisher lief die Bereinigung
 * nur einmalig in MainActivity.onStop().
 */
@HiltWorker
class CacheMaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val cacheManager = LetheCacheManager(applicationContext)
            val deleted = cacheManager.cleanOldMedia(daysOld = 7)
            cacheManager.enforceMaxCacheSize(maxMb = CACHE_LIMIT_MB)
            Timber.tag("LETHE_BG").d("CacheMaintenanceWorker: $deleted veraltete Dateien entfernt")
            Result.success()
        } catch (e: Exception) {
            Timber.tag("LETHE_BG").e(e, "CacheMaintenanceWorker fehlgeschlagen")
            Result.success()
        }
    }

    companion object {
        const val CACHE_LIMIT_MB = 300
    }
}
