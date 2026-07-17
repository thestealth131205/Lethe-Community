package com.securechat.app.contacts

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.securechat.app.data.local.ContactEntity

/**
 * Schreibt für jeden Lethe-Kontakt einen RawContact unter dem Lethe-System-Account
 * (com.Lethe.app) in die Geräte-Kontakte. Über die Telefonnummer (fakeNumber)
 * aggregiert Android diesen RawContact automatisch mit dem bestehenden Telefonbuch-
 * Kontakt – dadurch erscheint Lethe unter "Verbundene Apps" mit den Aktionen
 * Nachricht / Sprachanruf / Videoanruf (analog WhatsApp).
 *
 * Das Tap-Routing der Aktionszeilen läuft über ContactActionActivity, die anhand
 * des MIME-Typs + der in DATA1 gespeicherten userId in die App navigiert.
 */
object LetheContactsIntegration {

    private const val TAG = "LetheContacts"

    const val MIME_CHAT = "vnd.android.cursor.item/vnd.com.Lethe.app.chat"
    const val MIME_VOICE = "vnd.android.cursor.item/vnd.com.Lethe.app.voicecall"
    const val MIME_VIDEO = "vnd.android.cursor.item/vnd.com.Lethe.app.videocall"

    private val LETHE_ACCOUNT = Account(LETHE_ACCOUNT_NAME, LETHE_ACCOUNT_TYPE)

    fun hasWritePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** Legt den Lethe-System-Account an, falls noch nicht vorhanden. */
    fun ensureAccount(context: Context): Boolean = try {
        val am = AccountManager.get(context)
        val exists = am.getAccountsByType(LETHE_ACCOUNT_TYPE).isNotEmpty()
        if (!exists) {
            am.addAccountExplicitly(LETHE_ACCOUNT, null, null)
            // Kontakte-Sync für den Account grundsätzlich erlauben (kein periodisches Sync).
            ContactsContract.AUTHORITY.let {
                android.content.ContentResolver.setIsSyncable(LETHE_ACCOUNT, it, 1)
                android.content.ContentResolver.setSyncAutomatically(LETHE_ACCOUNT, it, false)
            }
        }
        true
    } catch (e: Exception) {
        Log.w(TAG, "ensureAccount fehlgeschlagen", e)
        false
    }

    /** Entfernt den Lethe-Account inkl. aller daran gehängten RawContacts. */
    fun removeIntegration(context: Context) {
        try {
            val am = AccountManager.get(context)
            am.getAccountsByType(LETHE_ACCOUNT_TYPE).forEach { acc ->
                @Suppress("DEPRECATION")
                am.removeAccount(acc, null, null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "removeIntegration fehlgeschlagen", e)
        }
    }

    /**
     * Synchronisiert die Lethe-RawContacts: löscht alle bestehenden und legt für jeden
     * übergebenen Kontakt mit nutzbarer Telefonnummer neue an. Muss auf einem
     * Hintergrund-Thread aufgerufen werden (ContentProvider-Batch-Operationen).
     */
    fun sync(context: Context, contacts: List<ContactEntity>) {
        if (!hasWritePermission(context)) {
            Log.d(TAG, "WRITE_CONTACTS fehlt – Sync übersprungen")
            return
        }
        if (!ensureAccount(context)) return

        val resolver = context.contentResolver
        val syncUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, LETHE_ACCOUNT_NAME)
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, LETHE_ACCOUNT_TYPE)
            .build()

        // 1. Alle bisherigen Lethe-RawContacts hart löschen (CALLER_IS_SYNCADAPTER).
        try {
            resolver.delete(
                syncUri,
                "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND ${ContactsContract.RawContacts.ACCOUNT_NAME} = ?",
                arrayOf(LETHE_ACCOUNT_TYPE, LETHE_ACCOUNT_NAME)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Löschen alter RawContacts fehlgeschlagen", e)
        }

        // 2. Neu anlegen – nur Kontakte mit echter Telefonnummer (aggregierbar).
        val eligible = contacts.filter { !it.isAnonymous && isPhoneNumber(it.fakeNumber) }
        if (eligible.isEmpty()) return

        val dataSyncUri = ContactsContract.Data.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .build()

        // In Blöcken anwenden, damit die Provider-Transaktion nicht zu groß wird.
        eligible.chunked(40).forEach { chunk ->
            val ops = ArrayList<ContentProviderOperation>()
            chunk.forEach { c ->
                val displayName = c.customAlias?.takeIf { it.isNotBlank() }
                    ?: c.username?.takeIf { it.isNotBlank() }
                    ?: c.fakeNumber
                val rawIndex = ops.size

                ops.add(
                    ContentProviderOperation.newInsert(syncUri)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, LETHE_ACCOUNT_TYPE)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, LETHE_ACCOUNT_NAME)
                        .build()
                )

                // Anzeigename (für Aggregation + Darstellung)
                ops.add(
                    ContentProviderOperation.newInsert(dataSyncUri)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                        )
                        .withValue(
                            ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                            displayName
                        )
                        .build()
                )

                // Telefonnummer – treibt die Aggregation mit dem Telefonbuch-Kontakt.
                ops.add(
                    ContentProviderOperation.newInsert(dataSyncUri)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, c.fakeNumber)
                        .withValue(
                            ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
                        )
                        .build()
                )

                // Aktionszeilen: DATA1 = userId (Routing), DATA2 = Aktionstext, DATA3 = Untertitel.
                ops.add(actionRow(dataSyncUri, rawIndex, MIME_CHAT, c.userId, "Lethe Nachricht senden", displayName))
                ops.add(actionRow(dataSyncUri, rawIndex, MIME_VOICE, c.userId, "Lethe Sprachanruf", displayName))
                ops.add(actionRow(dataSyncUri, rawIndex, MIME_VIDEO, c.userId, "Lethe Videoanruf", displayName))
            }
            try {
                resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            } catch (e: Exception) {
                Log.w(TAG, "applyBatch fehlgeschlagen", e)
            }
        }
        Log.d(TAG, "Kontakte-Integration synchronisiert: ${eligible.size} Einträge")
    }

    private fun actionRow(
        dataUri: android.net.Uri,
        rawIndex: Int,
        mime: String,
        userId: String,
        summary: String,
        detail: String
    ): ContentProviderOperation =
        ContentProviderOperation.newInsert(dataUri)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
            .withValue(ContactsContract.Data.MIMETYPE, mime)
            .withValue(ContactsContract.Data.DATA1, userId)
            .withValue(ContactsContract.Data.DATA2, summary)
            .withValue(ContactsContract.Data.DATA3, detail)
            .build()

    /** Grobe Plausibilitätsprüfung: enthält genügend Ziffern, um aggregierbar zu sein. */
    private fun isPhoneNumber(value: String): Boolean {
        val digits = value.count { it.isDigit() }
        return digits in 6..18 && value.all { it.isDigit() || it in "+ ()-/" }
    }
}
