package com.securechat.app

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.securechat.app.data.network.ApiService
import com.securechat.app.data.network.TokenManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Hintergrund-Worker der periodisch ausstehende Kontaktanfragen vom Server abruft
 * und dem Benutzer als System-Benachrichtigung anzeigt.
 * Läuft auch wenn die App geschlossen ist (min. alle 15 Minuten via WorkManager).
 */
@HiltWorker
class ContactRequestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Nur ausführen wenn der Benutzer eingeloggt ist
        if (!tokenManager.hasToken()) return Result.success()

        return try {
            val response = apiService.getPendingContacts()
            if (response.isSuccessful) {
                val pending = response.body() ?: emptyList()
                pending.forEach { request ->
                    notificationHelper.showContactRequestNotification(
                        fromName = request.fromName,
                        fromNumber = request.fromFakeNumber,
                        contactEntryId = request.contactEntryId
                    )
                }
                Log.d("ContactRequestWorker", "${pending.size} ausstehende Anfragen verarbeitet.")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("ContactRequestWorker", "Fehler beim Prüfen der Anfragen", e)
            Result.retry()
        }
    }
}
