package com.securechat.app.contacts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import com.securechat.app.MainActivity

/**
 * Unsichtbare Brücken-Activity: wird vom System aufgerufen, wenn der Nutzer in der
 * Kontakte-App eine Lethe-Aktionszeile ("Verbundene Apps") antippt. Liest aus der
 * angetippten Data-Row die userId (DATA1) + den MIME-Typ und startet MainActivity
 * mit den passenden Routing-Extras.
 */
class ContactActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeAndFinish()
    }

    private fun routeAndFinish() {
        val dataUri = intent?.data
        val mime = intent?.type
        if (dataUri == null || mime == null) { finish(); return }

        var userId: String? = null
        try {
            contentResolver.query(
                dataUri,
                arrayOf(ContactsContract.Data.DATA1),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) userId = c.getString(0)
            }
        } catch (e: Exception) {
            Log.w("ContactAction", "Data-Row konnte nicht gelesen werden", e)
        }

        if (userId.isNullOrBlank()) { finish(); return }

        val navigateTo = when (mime) {
            LetheContactsIntegration.MIME_VOICE -> "contact_voice_call"
            LetheContactsIntegration.MIME_VIDEO -> "contact_video_call"
            else -> null // chat → Default-Branch in MainActivity öffnet chat/{userId}
        }

        val launch = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("chat_id", userId)
            if (navigateTo != null) putExtra("navigate_to", navigateTo)
        }
        startActivity(launch)
        finish()
    }
}
