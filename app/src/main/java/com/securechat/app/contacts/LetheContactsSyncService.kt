package com.securechat.app.contacts

import android.app.Service
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle
import android.os.IBinder

/**
 * Sync-Adapter für den Lethe-Kontakte-Account. Das eigentliche Schreiben der
 * RawContacts erfolgt direkt aus der App (LetheContactsIntegration), nicht über
 * periodisches Framework-Sync. onPerformSync ist daher ein No-Op – der Adapter
 * existiert nur, damit das System den Account an den Kontakte-Provider bindet und
 * die CONTACTS_STRUCTURE-Metadaten (res/xml/contacts.xml) findet.
 */
class LetheContactsSyncAdapter(context: Context, autoInitialize: Boolean) :
    AbstractThreadedSyncAdapter(context, autoInitialize) {

    override fun onPerformSync(
        account: android.accounts.Account?,
        extras: Bundle?,
        authority: String?,
        provider: ContentProviderClient?,
        syncResult: SyncResult?
    ) {
        // Bewusst leer – Datenpflege läuft über LetheContactsIntegration.
    }
}

class LetheContactsSyncService : Service() {

    override fun onBind(intent: Intent?): IBinder = syncAdapter.syncAdapterBinder

    companion object {
        private val lock = Any()

        @Volatile
        private var instance: LetheContactsSyncAdapter? = null
    }

    private val syncAdapter: LetheContactsSyncAdapter
        get() = instance ?: synchronized(lock) {
            instance ?: LetheContactsSyncAdapter(applicationContext, true).also { instance = it }
        }
}
