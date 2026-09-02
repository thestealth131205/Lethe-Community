package com.securechat.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.widget.RemoteViews
import coil.ImageLoader
import coil.request.ImageRequest
import com.securechat.app.MainActivity
import com.securechat.app.R
import com.securechat.app.data.local.ContactDao
import com.securechat.app.data.local.GroupDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Startbildschirm-Widget für einen einzelnen Kontakt oder eine Gruppe.
 * Zeigt das aktuelle Profil-/Gruppenbild + Namen, Tap öffnet den Chat direkt.
 *
 * Die Zuordnung Widget-ID → Kontakt/Gruppe wird in SharedPreferences ([PREFS_NAME])
 * gespeichert (befüllt von [WidgetConfigureActivity] bei der Widget-Einrichtung).
 */
@AndroidEntryPoint
class ContactWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var contactDao: ContactDao
    @Inject lateinit var groupDao: GroupDao

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateWidgetInternal(context, appWidgetManager, appWidgetId, contactDao, groupDao)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = widgetPrefs(context)
        val editor = prefs.edit()
        for (appWidgetId in appWidgetIds) editor.remove(keyFor(appWidgetId))
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "lethe_widgets"

        private fun widgetPrefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun keyFor(appWidgetId: Int) = "widget_$appWidgetId"

        /** Speichert, auf welchen Kontakt/welche Gruppe ein Widget zeigt (von der Konfigurations-UI aufgerufen). */
        fun saveWidgetTarget(context: Context, appWidgetId: Int, isGroup: Boolean, targetId: String) {
            widgetPrefs(context).edit()
                .putString(keyFor(appWidgetId), "${if (isGroup) "group" else "contact"}:$targetId")
                .apply()
        }

        /** Aktualisiert alle Widgets, die auf den angegebenen Kontakt/die Gruppe zeigen (z.B. nach Avatar-Änderung). */
        fun refreshWidgetsFor(context: Context, isGroup: Boolean, targetId: String) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ContactWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val prefix = "${if (isGroup) "group" else "contact"}:$targetId"
            val prefs = widgetPrefs(context)
            val matching = ids.filter { prefs.getString(keyFor(it), null) == prefix }
            if (matching.isEmpty()) return
            val intent = Intent(context, ContactWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, matching.toIntArray())
            }
            context.sendBroadcast(intent)
        }

        /** Baut die RemoteViews für ein einzelnes Widget neu auf und übergibt sie an den AppWidgetManager. */
        suspend fun updateWidgetInternal(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            contactDao: ContactDao,
            groupDao: GroupDao
        ) {
            val target = widgetPrefs(context).getString(keyFor(appWidgetId), null) ?: return
            val parts = target.split(":", limit = 2)
            if (parts.size != 2) return
            val isGroup = parts[0] == "group"
            val targetId = parts[1]

            val name: String
            val imageUrl: String?
            if (isGroup) {
                val group = groupDao.getGroupById(targetId) ?: return
                name = group.name
                imageUrl = group.groupImageUrl
            } else {
                val contact = contactDao.getContactById(targetId) ?: return
                name = contact.customAlias ?: contact.username ?: contact.fakeNumber
                imageUrl = contact.profileImageUrl
            }
            val absoluteUrl = imageUrl?.let { if (it.startsWith("http")) it else "https://letheapp.de$it" }

            val avatarBitmap: Bitmap? = if (absoluteUrl != null) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(absoluteUrl)
                        .allowHardware(false)
                        .size(256, 256)
                        .build()
                    (ImageLoader(context).execute(request).drawable as? BitmapDrawable)?.bitmap
                } catch (_: Exception) { null }
            } else null

            val views = RemoteViews(context.packageName, R.layout.widget_contact)
            views.setTextViewText(R.id.widget_contact_name, name)
            if (avatarBitmap != null) {
                views.setImageViewBitmap(R.id.widget_contact_avatar, circularBitmap(avatarBitmap))
            } else {
                views.setImageViewResource(R.id.widget_contact_avatar, R.drawable.widget_avatar_placeholder)
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("chat_id", targetId)
                if (isGroup) putExtra("navigate_to", "group_chat")
            }
            val pendingIntent = PendingIntent.getActivity(
                context, appWidgetId, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /** Schneidet ein Bitmap kreisrund zu (mittig quadratisch zugeschnitten). */
        private fun circularBitmap(bitmap: Bitmap): Bitmap {
            val size = minOf(bitmap.width, bitmap.height)
            val left = (bitmap.width - size) / 2
            val top = (bitmap.height - size) / 2
            val cropped = Bitmap.createBitmap(bitmap, left, top, size, size)

            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
            canvas.drawOval(rect, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(cropped, 0f, 0f, paint)
            return output
        }
    }
}
