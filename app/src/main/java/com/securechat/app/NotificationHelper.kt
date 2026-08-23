package com.securechat.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.securechat.app.R
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import coil.ImageLoader
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repräsentiert eine historische Nachricht für MessagingStyle-Benachrichtigungen.
 * isFromSender = true → Nachricht kommt vom Kontakt; false → Nachricht von mir
 */
data class NotificationMessage(
    val text: String,
    val timestamp: Long,
    val isFromSender: Boolean
)

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Kategorie: Kontakte (Kontaktanfragen, Kontaktaktivitäten)
        const val CHANNEL_ID_CONTACTS = "lethe_contacts"

        // Kategorie: Nachrichten (Chat-Nachrichten)
        const val CHANNEL_ID_MESSAGES = "lethe_messages"

        // Kategorie: Gruppen (Gruppenbenachrichtigungen)
        const val CHANNEL_ID_GROUPS = "lethe_groups"

        // Kategorie: Dating (Matches, Anfragen)
        const val CHANNEL_ID_DATING = "lethe_dating"

        // Kategorie: Updates (Systembenachrichtigungen)
        const val CHANNEL_ID_UPDATES = "lethe_updates"

        // Eingehende Anrufe (eigener Kanal mit Klingelton-Priorität)
        const val CHANNEL_ID_CALLS = "lethe_calls"

        // Kategorie: Nearby (Likes, Matches, Nachrichten)
        const val CHANNEL_ID_NEARBY = "lethe_nearby"

        // Kategorie: Creator Content (neue Beiträge, Artikel)
        const val CHANNEL_ID_CONTENT = "lethe_content"

        // Kategorie: Livestream (Creator geht live)
        const val CHANNEL_ID_LIVESTREAM = "lethe_livestream"

        // Kategorie: Sparks (neue Spark-Videos)
        const val CHANNEL_ID_SPARKS = "lethe_sparks"

        // Kategorie: VIP-Forum (neue Threads / Beiträge in abonnierten Kategorien)
        const val CHANNEL_ID_VIP_FORUM = "lethe_vip_forum"

        // ForegroundService-Kanal (stumm, kein Sound, kein Badge)
        const val CHANNEL_ID_FOREGROUND = "lethe_foreground"

        // Jump or Die – Score-Benachrichtigungen
        const val CHANNEL_ID_JOD = "jump_or_die"

        // Legacy-Konstanten für Rückwärtskompatibilität
        const val CHANNEL_ID = CHANNEL_ID_CONTACTS
        const val CHANNEL_NAME = "Kontakte"

        const val NOTIFICATION_ID_BASE = 2000
        const val NOTIFICATION_ID_MESSAGE_BASE = 3000
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val EXTRA_SENDER_ID = "extra_sender_id"
        const val EXTRA_SENDER_NAME = "extra_sender_name"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_IS_GROUP = "extra_is_group"

        // Lokal gespiegeltes Server-Setting "only_fcm". Wird vom MainViewModel beschrieben
        // und von Hintergrund-Workern gelesen, um das WS/HTTP-Fallback-Benachrichtigungs-
        // system bei aktivem Only-FCM-Modus komplett zu deaktivieren.
        const val ONLY_FCM_PREFS = "lethe_notif_settings"
        const val ONLY_FCM_KEY = "only_fcm"

        /** true, wenn Benachrichtigungen ausschließlich über FCM laufen sollen. */
        fun isOnlyFcmMode(context: Context): Boolean =
            context.getSharedPreferences(ONLY_FCM_PREFS, Context.MODE_PRIVATE)
                .getBoolean(ONLY_FCM_KEY, false)
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Kontakte (Kontaktanfragen etc.)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_CONTACTS, "Kontakte", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Kontaktanfragen und Kontaktaktivitäten"
            }
        )

        // Nachrichten (Chat)
        val msgSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val msgAudioAttr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_MESSAGES, "Nachrichten", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Eingehende Chat-Nachrichten"
                enableVibration(true)
                setSound(msgSound, msgAudioAttr)
            }
        )

        // Gruppen
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_GROUPS, "Gruppen", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Gruppenbenachrichtigungen und Gruppeneinladungen"
            }
        )

        // Dating
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_DATING, "Dating", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Dating-Matches und Anfragen"
            }
        )

        // Updates
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_UPDATES, "Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "App-Updates und Systemmeldungen"
            }
        )

        // Eingehende Anrufe – eigener Kanal mit Klingelton + voller Priorität
        val callRingtoneUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val callAudioAttr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_CALLS, "Anrufe", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Eingehende Sprach- und Videoanrufe"
                enableVibration(true)
                setSound(callRingtoneUri, callAudioAttr)
                enableLights(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )

        // Nearby – Likes, Matches, Nachrichten
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_NEARBY, "Nearby", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Likes, Matches und Nachrichten in Nearby"
                enableVibration(true)
            }
        )

        // Creator Content – neue Beiträge & Artikel
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_CONTENT, "Creator Content", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Neue Beiträge und Artikel von Creatorn, denen du folgst"
            }
        )

        // Livestream – Creator geht live
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_LIVESTREAM, "Livestream", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Ein Creator, dem du folgst, startet einen Livestream"
                enableVibration(true)
            }
        )

        // Sparks – neue Spark-Videos
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_SPARKS, "Sparks", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Neue Sparks von Creatorn, denen du folgst"
            }
        )

        // VIP-Forum – neue Threads und Beiträge in abonnierten Kategorien
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_VIP_FORUM, "VIP-Forum", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Neue Threads und Beiträge in abonnierten Forum-Kategorien"
            }
        )

        // ForegroundService – vollständig unsichtbar (kein Eintrag in der Benachrichtigungsleiste)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_FOREGROUND, "Lethe läuft", NotificationManager.IMPORTANCE_NONE).apply {
                description = "Hintergrundservice (versteckt)"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
        )

        // Jump or Die – Score-Benachrichtigungen
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_JOD, "Jump or Die", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Score-Benachrichtigungen aus dem Spiel Jump or Die"
                enableVibration(true)
            }
        )
    }

    fun showContactRequestNotification(fromName: String, fromNumber: String, contactEntryId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "contacts")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, contactEntryId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CONTACTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Lethe – Neue Kontaktanfrage")
            .setContentText("$fromName ($fromNumber) möchte Kontakt aufnehmen.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_BASE + contactEntryId.hashCode(), notification)
    }

    /**
     * Zeigt eine Benachrichtigung für einen neuen Thread oder Beitrag in einer abonnierten
     * VIP-Forum-Kategorie. Als Bild wird das Lethe-App-Icon verwendet.
     */
    fun showVipForumNotification(categoryName: String, threadTitle: String, categoryId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "vip")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, ("vip_$categoryId").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_VIP_FORUM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Neues Thema in \u201E$categoryName\u201C")
            .setContentText(threadTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(threadTitle))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(("vip_$categoryId${threadTitle.hashCode()}").hashCode(), notification)
    }

    /** Zeigt eine Benachrichtigung wenn jemand auf die eigene VIP-Diskussion antwortet. */
    fun showVipDiscussionReplyNotification(replierName: String, discussionTitle: String, discussionId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "vip")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, discussionId.hashCode() + 8700, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_VIP_FORUM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Neue Antwort: $discussionTitle")
            .setContentText("$replierName hat geantwortet")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(discussionId.hashCode() + 8700, notification)
    }

    /**
     * Zeigt eine Benachrichtigung für eine eingehende Nachricht.
     * Nutzt MessagingStyle für automatisches Wearable- und Android-Auto-Mirroring.
     * [recentMessages] enthält die letzten Nachrichten des Gesprächs (max. 5), älteste zuerst.
     * [channelId] erlaubt das Routing in die passende Kategorie (Nachrichten vs. Gruppen).
     * [mediaType] bestimmt den Nachrichtentyp ("text", "audio", "image", "video", …).
     *   Bei "audio" wird der Hinweistext für Android Auto optimiert und die RemoteInput-Beschriftung
     *   auf Spracheingabe hingewiesen; bei "image" wird das Bild-URI an die MessagingStyle-Nachricht
     *   angehängt wenn [mediaUri] ein erreichbarer content://-URI ist.
     * [mediaHttpUrl] optionale http-URL eines Bildes – wird intern zu einem content://-URI
     *   heruntergeladen wenn [mediaUri] null ist (für FCM und WS-Benachrichtigungen).
     */
    suspend fun showMessageNotification(
        senderId: String,
        senderName: String,
        senderImageUrl: String?,
        myImageUrl: String? = null,
        messagePreview: String,
        notificationId: Int,
        recentMessages: List<NotificationMessage> = emptyList(),
        channelId: String = CHANNEL_ID_MESSAGES,
        badgeCount: Int = 1,
        silent: Boolean = false,
        mediaType: String = "text",
        mediaUri: android.net.Uri? = null,
        mediaHttpUrl: String? = null,
        isGroup: Boolean = false,
        senderDisplayName: String? = null,
        senderProfileUrl: String? = null
    ) {
        // Kontaktbild und eigenes Profilbild asynchron laden
        suspend fun loadBitmap(url: String?): Bitmap? = withContext(Dispatchers.IO) {
            if (url == null) return@withContext null
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(128, 128)
                    .build()
                (loader.execute(request).drawable as? BitmapDrawable)?.bitmap
            } catch (_: Exception) { null }
        }

        // Bild-URL → cache-Datei → content://-URI (für Android Auto / MessagingStyle.Message.setData)
        suspend fun downloadToContentUri(httpUrl: String): android.net.Uri? = withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(httpUrl)
                    .allowHardware(false)
                    .size(320, 320)
                    .build()
                val bitmap = (loader.execute(request).drawable as? BitmapDrawable)?.bitmap
                    ?: return@withContext null
                val cacheDir = java.io.File(context.cacheDir, "notification_images").also { it.mkdirs() }
                val hash = httpUrl.hashCode().let { if (it < 0) "m${-it}" else "$it" }
                val file = java.io.File(cacheDir, "img_$hash.jpg")
                file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                // Lese-Berechtigung für System-UI und Android Auto erteilen
                listOf("com.android.systemui", "com.google.android.gms",
                       "com.google.android.projection.gearhead").forEach { pkg ->
                    try { context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    catch (_: Exception) {}
                }
                uri
            } catch (_: Exception) { null }
        }

        // Avatar-Bitmap → cache-Datei → content://-URI.
        // Android Auto (Gearhead) läuft in einem eigenen Prozess und rendert die Profilbilder
        // der Person-Objekte nur zuverlässig, wenn sie als content://-URI statt als Inline-Bitmap
        // übergeben werden. Inline-Bitmaps gehen beim Prozesswechsel häufig verloren → kein Avatar.
        suspend fun avatarToContentUri(bitmap: Bitmap?, key: String): android.net.Uri? = withContext(Dispatchers.IO) {
            if (bitmap == null) return@withContext null
            try {
                val cacheDir = java.io.File(context.cacheDir, "notification_avatars").also { it.mkdirs() }
                val safeKey = key.hashCode().let { if (it < 0) "m${-it}" else "$it" }
                val file = java.io.File(cacheDir, "avatar_$safeKey.png")
                file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 90, out) }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                listOf("com.android.systemui", "com.google.android.gms",
                       "com.google.android.projection.gearhead").forEach { pkg ->
                    try { context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    catch (_: Exception) {}
                }
                uri
            } catch (_: Exception) { null }
        }

        val largeIcon = loadBitmap(senderImageUrl)
        val senderIcon = if (isGroup && senderProfileUrl != null) loadBitmap(senderProfileUrl) else largeIcon
        val myIcon = loadBitmap(myImageUrl)
        // content://-URIs der Avatare für die Person-Icons (Android-Auto-tauglich)
        val senderIconUri = avatarToContentUri(senderIcon, "sender_$senderId")
        val myIconUri = avatarToContentUri(myIcon, "me")
        // Effektiven media-URI auflösen: expliziter content://-URI hat Vorrang,
        // andernfalls wird mediaHttpUrl heruntergeladen
        val effectiveMediaUri: android.net.Uri? = when {
            mediaUri != null -> mediaUri
            mediaType == "image" && !mediaHttpUrl.isNullOrBlank() -> downloadToContentUri(mediaHttpUrl)
            else -> null
        }

        // Person-Objekte für MessagingStyle (Wear OS, Android Auto, etc.)
        // Icon bevorzugt als content://-URI (Android Auto), Bitmap nur als Fallback.
        val mePerson = Person.Builder()
            .setName("Du")
            .setKey("me")
            .apply {
                when {
                    myIconUri != null -> setIcon(IconCompat.createWithContentUri(myIconUri))
                    myIcon != null -> setIcon(IconCompat.createWithBitmap(myIcon))
                }
            }
            .build()

        val senderPerson = Person.Builder()
            .setName(senderDisplayName ?: senderName)
            .setKey(senderId)
            .apply {
                when {
                    senderIconUri != null -> setIcon(IconCompat.createWithContentUri(senderIconUri))
                    senderIcon != null -> setIcon(IconCompat.createWithBitmap(senderIcon))
                }
            }
            .build()

        // MessagingStyle: wird von Wear OS / Android Auto automatisch als Chat dargestellt
        val messagingStyle = NotificationCompat.MessagingStyle(mePerson)
            .setConversationTitle(senderName)
            .setGroupConversation(isGroup)

        // Historische Nachrichten einfügen (älteste zuerst)
        recentMessages.forEach { msg ->
            messagingStyle.addMessage(
                NotificationCompat.MessagingStyle.Message(
                    msg.text,
                    msg.timestamp,
                    if (msg.isFromSender) senderPerson else null // null = "ich"
                )
            )
        }
        // Aktuelle Nachricht hinzufügen
        // Bei Bild-Nachrichten: Bild-URI an die Message anhängen damit Android Auto ein Thumbnail
        // darstellen kann. Funktioniert nur mit content://-URIs (FileProvider oder MediaStore).
        // Bei Gruppen enthält messagePreview bereits den Sendernamen als Prefix ("Hannah: 😄").
        // MessagingStyle zeigt den Namen via senderPerson selbst an → Prefix entfernen damit
        // Android Auto den Namen nicht doppelt vorliest.
        val messagingText = if (isGroup) {
            val prefix = "${senderPerson.name}: "
            if (messagePreview.startsWith(prefix)) messagePreview.removePrefix(prefix)
            else messagePreview
        } else messagePreview
        val currentMessage = NotificationCompat.MessagingStyle.Message(
            messagingText,
            System.currentTimeMillis(),
            senderPerson
        ).also { msg ->
            if (mediaType == "image" && effectiveMediaUri != null) {
                try { msg.setData("image/jpeg", effectiveMediaUri) } catch (_: Exception) { /* URI nicht erreichbar */ }
            }
        }
        messagingStyle.addMessage(currentMessage)

        // Intent: Tippen öffnet den Chat
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_id", senderId)
            if (isGroup) putExtra("navigate_to", "group_chat")
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, notificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // RemoteInput für direkte Antwort (Inline Reply + Android Auto Spracheingabe).
        // Bei Sprachnachrichten wird der Label-Text geändert um dem Nutzer in Android Auto
        // zu signalisieren, dass eine Sprach-Antwort möglich ist.
        // setAllowFreeFormInput(true) ist der Standard, hier explizit für AA-Klarheit gesetzt.
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel(if (mediaType == "audio") "Per Sprache antworten …" else "Antworten…")
            .setAllowFreeFormInput(true)
            .build()

        val replyIntent = Intent(context, MessageReplyReceiver::class.java).apply {
            putExtra(EXTRA_SENDER_ID, senderId)
            putExtra(EXTRA_SENDER_NAME, senderName)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_IS_GROUP, isGroup)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context, notificationId + 1, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Antworten",
            replyPendingIntent
        ).addRemoteInput(remoteInput)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()

        // "Als gelesen markieren" Aktion
        val markReadIntent = Intent(context, MarkAsReadReceiver::class.java).apply {
            putExtra(EXTRA_SENDER_ID, senderId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_IS_GROUP, isGroup)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context, notificationId + 2, markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "Als gelesen markieren",
            markReadPendingIntent
        ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        // Dismiss-Intent (Wegwischen der Benachrichtigung): löst dieselbe Logik wie
        // "Als gelesen markieren" aus, damit Server/App die Nachricht danach nicht erneut anzeigen.
        val deleteIntent = Intent(context, MarkAsReadReceiver::class.java).apply {
            putExtra(EXTRA_SENDER_ID, senderId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_IS_GROUP, isGroup)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context, notificationId + 3, deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Neon-Gelb wie in der App (Theme DefaultPrimary) als Hintergrund-/Akzentfarbe
        val neonYellow = android.graphics.Color.parseColor("#A8A800")

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText(messagePreview)
            .setStyle(messagingStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .addAction(replyAction)
            .addAction(markReadAction)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setShortcutId(senderId)
            .setAutoCancel(true)
            .setColor(neonYellow)
            .setColorized(true)
            .setNumber(badgeCount)

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }
        if (silent) {
            builder.setSilent(true)
        }
        // Sprachnachricht: Ticker-Text für Android-Auto-TTS und Accessibility-Services optimieren.
        // Android Auto liest den Ticker-Text vor wenn kein anderer Beschreibungstext verfügbar ist.
        if (mediaType == "audio") {
            builder.setTicker("$senderName hat eine Sprachnachricht gesendet")
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, builder.build())
    }

    /** Zeigt eine Benachrichtigung für einen eingehenden Anruf (Video oder Sprache).
     *  Nutzt IncomingCallActivity als FullScreen-Intent (Sperrbildschirm-Anruf-UI).
     *  Aktions-Buttons "Annehmen" und "Ablehnen" ermöglichen Interaktion ohne Entsperren. */
    fun showIncomingCallNotification(callerName: String, callType: String = "VIDEO") {
        // Bildschirm sofort aufwecken (funktioniert auch wenn Gerät im Standby/Doze ist).
        // SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP ist deprecated, aber die einzige
        // zuverlässige Methode, den Bildschirm aus einem Service heraus aktiv einzuschalten.
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "lethe:incoming_call_wake"
        )
        wl.acquire(15_000L) // 15s; IncomingCallActivity übernimmt danach mit FLAG_KEEP_SCREEN_ON

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // FullScreen-Intent → IncomingCallActivity (Sperrbildschirm-Anruf-UI)
        val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 9000, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Aktion "Annehmen" → MainActivity mit navigate_to=accept_call
        val acceptIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "accept_call")
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            context, 9010, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Aktion "Ablehnen" → MainActivity mit navigate_to=decline_call
        val declineIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "decline_call")
        }
        val declinePendingIntent = PendingIntent.getActivity(
            context, 9011, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canUseFullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                manager.canUseFullScreenIntent()

        val title = if (callType == "VOICE") "Eingehender Sprachanruf" else "Eingehender Videoanruf"
        val text  = if (callType == "VOICE") "$callerName ruft an …" else "$callerName möchte einen Videoanruf …"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_CALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(false)
            .setOngoing(true)  // Nicht swipe-away-bar während der Klingelphase
            .addAction(android.R.drawable.ic_menu_call, "Annehmen", acceptPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Ablehnen", declinePendingIntent)

        if (canUseFullScreen) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        } else {
            builder.setContentIntent(fullScreenPendingIntent)
        }

        manager.notify(9001, builder.build())
    }

    /**
     * Zeigt eine generische Benachrichtigung für eine neue Nachricht auf einem NICHT aktiven,
     * gespeicherten Account (Multi-Account-Überwachung). Enthält bewusst weder Kontaktname
     * noch Nachrichtentext, um keine Inhalte des inaktiven Accounts preiszugeben. Tippen
     * wechselt direkt in den betroffenen Account (App-Neustart über switchAccount()).
     */
    fun showAccountSwitchNotification(accountDisplayName: String, profileKey: String) {
        val text = context.getString(R.string.multi_account_notification_body, accountDisplayName)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "switch_account")
            putExtra("switch_account_profile_key", profileKey)
        }
        val notificationId = (profileKey.hashCode() and 0x7FFFFFFF) + 7000
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Lethe")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    /** Zeigt eine Benachrichtigung für eine eingehende „Listen Together"-Einladung. */
    fun showListenTogetherNotification(fromName: String, trackTitle: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "listen_together")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 9100, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$fromName lädt dich ein")
            .setContentText("Listen Together: $trackTitle")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(9101, notification)
    }

    /** Zeigt eine Benachrichtigung für eine eingehende Spielanfrage. */
    fun showGameInviteNotification(fromName: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "games")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 9200, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Spielanfrage von $fromName")
            .setContentText("$fromName möchte ein Spiel mit dir spielen!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(9201, notification)
    }

    /** Zeigt eine Jump-or-Die Score-Benachrichtigung mit triggernd formuliertem Text. */
    fun showJodScoreNotification(competitorUsername: String, competitorScore: Int) {
        val texts = listOf(
            "\"$competitorUsername\" hat mit seinem Score $competitorScore fast das Zuhause erreicht – lass nicht zu, dass er dich vom Treppchen kickt!",
            "\"$competitorUsername\" rast mit $competitorScore Punkten Richtung Zuhause. Zeig ihm, wer hier das Sagen hat!",
            "Score-Alarm! \"$competitorUsername\" ist mit $competitorScore Punkten kurz vor dem Ziel. Bist du schneller?",
            "\"$competitorUsername\" hat $competitorScore Punkte – dein Platz auf dem Podium ist in Gefahr!"
        )
        val text = texts[(competitorScore + competitorUsername.length) % texts.size]
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "games")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 9500, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val largeIcon = try {
            android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.jod_notif_icon)
        } catch (_: Exception) { null }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_JOD)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Jump or Die")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        if (largeIcon != null) builder.setLargeIcon(largeIcon)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(9501 + (competitorScore % 10), builder.build())
    }

    fun showSknChGameOpenedNotification(creatorUsername: String, playersRequired: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "games")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 9300, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sketch 'n' Check – Offenes Spiel")
            .setContentText("$creatorUsername hat ein Sketch 'n' Check für $playersRequired Spieler eröffnet und wartet.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(9301, notification)
    }

    /** Bricht die laufende Anruf-Benachrichtigung ab (z.B. nach Annehmen/Ablehnen). */
    fun cancelCallNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(9001)
    }

    /** Bricht die Nachrichtenbenachrichtigung für einen bestimmten Absender ab (z.B. wenn Chat geöffnet wird). */
    fun cancelNotificationForSender(senderId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Identische Formel wie beim Anzeigen (FCM + WS): senderId.hashCode() maskieren,
        // sonst wird die Notification bei Absendern mit negativem hashCode nicht entfernt.
        manager.cancel((senderId.hashCode() and 0x7FFFFFFF) + NOTIFICATION_ID_MESSAGE_BASE)
    }

    /** Zeigt eine Benachrichtigung für eine eingehende Nearby-Chat-Nachricht. */
    fun showNearbyMessageNotification(senderName: String, messagePreview: String, matchId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "nearby_chat")
            putExtra("match_id", matchId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, matchId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_NEARBY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Lethe Nearby – $senderName")
            .setContentText(messagePreview)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(android.graphics.Color.parseColor("#A8A800"))
            .setColorized(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_BASE + matchId.hashCode(), notification)
    }

    /** Zeigt eine Benachrichtigung für ein erhaltenes Nearby-Like. */
    fun showNearbyLikeNotification(likerName: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "nearby")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 7100, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_NEARBY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Lethe Nearby – Neues Like")
            .setContentText("$likerName hat dein Profil geliked!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(android.graphics.Color.parseColor("#A8A800"))
            .setColorized(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(7101, notification)
    }

    /** Zeigt eine Benachrichtigung wenn jemand auf eine Nearby-Antwort reagiert hat. */
    fun showNearbyQuestionReactionNotification(emoji: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "nearby")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 7150, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_NEARBY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Nearby – Neue Reaktion")
            .setContentText("Jemand hat $emoji auf deine Antwort reagiert!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(android.graphics.Color.parseColor("#A8A800"))
            .setColorized(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(7151, notification)
    }

    /** Zeigt eine Benachrichtigung für ein neues Nearby-Match. */
    fun showNearbyMatchNotification(partnerName: String, matchId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "nearby_chat")
            putExtra("match_id", matchId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, matchId.hashCode() + 7200, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_NEARBY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Lethe Nearby – Match!")
            .setContentText("Du und $partnerName habt euch gegenseitig geliked!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(android.graphics.Color.parseColor("#A8A800"))
            .setColorized(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_BASE + matchId.hashCode() + 7200, notification)
    }

    /** Zeigt eine Benachrichtigung wenn ein abonnierter Creator einen neuen Beitrag veröffentlicht. */
    fun showCreatorContentNotification(creatorName: String, title: String, contentId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "creator_content")
            putExtra("content_id", contentId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, contentId.hashCode() + 8000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CONTENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$creatorName hat etwas Neues gepostet")
            .setContentText(title.ifBlank { "Neuer Beitrag" })
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(contentId.hashCode() + 8000, notification)
    }

    /** Zeigt eine Benachrichtigung wenn ein abonnierter Creator live geht. */
    fun showCreatorLivestreamNotification(creatorName: String, creatorId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "livestream")
            putExtra("creator_id", creatorId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, creatorId.hashCode() + 8100, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_LIVESTREAM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$creatorName ist jetzt live!")
            .setContentText("Tippe um den Livestream anzusehen")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(creatorId.hashCode() + 8100, notification)
    }

    /** Zeigt eine Benachrichtigung wenn ein abonnierter Creator einen neuen Spark postet. */
    fun showCreatorSparkNotification(creatorName: String, sparkId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "sparks")
            putExtra("spark_id", sparkId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, sparkId.hashCode() + 8200, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SPARKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$creatorName hat einen neuen Spark")
            .setContentText("Tippe um den Spark anzusehen")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(sparkId.hashCode() + 8200, notification)
    }

    /** Zeigt eine Benachrichtigung für ein neues Support-Ticket (nur für Admins/Moderatoren). */
    fun showNewSupportTicketNotification(category: String, title: String, ticketId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "backend_support")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, ticketId.hashCode() + 8300, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Neues Support-Ticket")
            .setContentText("[$category] $title")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(8300, notification)
    }

    /** Zeigt eine Benachrichtigung wenn der Support auf ein Ticket des Nutzers geantwortet hat. */
    fun showSupportReplyNotification(title: String, reply: String, ticketId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "support")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, ticketId.hashCode() + 8302, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Antwort zu deinem Ticket: $title")
            .setContentText(reply)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reply))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ticketId.hashCode() + 8302, notification)
    }

    /** Zeigt eine Benachrichtigung wenn ein Nutzer auf ein Support-Ticket geantwortet hat (nur für Admins/Moderatoren). */
    fun showSupportTicketUserReplyNotification(userName: String, title: String, reply: String, ticketId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "backend_support")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, ticketId.hashCode() + 8303, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Neue Antwort im Ticket: $title")
            .setContentText("$userName: $reply")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$userName: $reply"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ticketId.hashCode() + 8303, notification)
    }

    /** Zeigt eine Benachrichtigung für eine neue Nutzer-Meldung (nur für Admins/Moderatoren). */
    fun showNewUserReportNotification(reporterName: String, reason: String, reportId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "backend_support")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, reportId.hashCode() + 8304, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Neue Meldung von $reporterName")
            .setContentText(reason.ifBlank { "Kein Grund angegeben" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(reportId.hashCode() + 8304, notification)
    }

    /** Zeigt eine Benachrichtigung wenn ein Spark oder Beitrag geliked wurde. */
    fun showContentLikedNotification(likerName: String, contentId: String, isSpark: Boolean) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (isSpark) {
                putExtra("navigate_to", "spark_view")
            } else {
                putExtra("navigate_to", "content_view")
            }
            putExtra("chat_id", contentId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, contentId.hashCode() + 8305, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CONTENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (isSpark) "Dein Spark wurde geliked" else "Dein Beitrag wurde geliked")
            .setContentText("$likerName gefällt das")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(contentId.hashCode() + 8305, notification)
    }

    /** Zeigt eine Benachrichtigung wenn eine VIP-Diskussion geliked wurde. */
    fun showDiscussionLikedNotification(likerName: String, discussionTitle: String, discussionId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "vip")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, discussionId.hashCode() + 8306, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_VIP_FORUM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Deine Diskussion wurde geliked")
            .setContentText("$likerName gefällt \"$discussionTitle\"")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(discussionId.hashCode() + 8306, notification)
    }

    /** Zeigt eine Benachrichtigung für eine neue Creator-Bewerbung (nur für Admins/Moderatoren). */
    fun showNewCreatorApplicationNotification(applicantName: String, applicationId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "backend_applications")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, applicationId.hashCode() + 8301, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Neue Creator-Bewerbung")
            .setContentText("Von $applicantName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(8301, notification)
    }

    /** Zeigt eine Benachrichtigung wenn eine Creator-Bewerbung genehmigt oder abgelehnt wurde. */
    fun showCreatorApplicationReviewedNotification(status: String, adminNote: String, applicationId: String) {
        val approved = status == "approved"
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "creator_apply")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, applicationId.hashCode() + 8307, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (approved) "Herzlichen Glückwunsch, du bist jetzt Creator!" else adminNote.ifBlank { "Deine Bewerbung wurde leider abgelehnt." }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (approved) "Bewerbung genehmigt" else "Bewerbung abgelehnt")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(applicationId.hashCode() + 8307, notification)
    }

    /** Zeigt eine Benachrichtigung wenn das Team eine Rückfrage zur Creator-Bewerbung gestellt hat. */
    fun showCreatorApplicationMessageNotification(reply: String, applicationId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "creator_apply")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, applicationId.hashCode() + 8308, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Nachricht zu deiner Creator-Bewerbung")
            .setContentText(reply)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reply))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(applicationId.hashCode() + 8308, notification)
    }

    /** Zeigt eine Benachrichtigung wenn ein Bewerber auf eine Rückfrage geantwortet hat (nur für Admins/Moderatoren). */
    fun showCreatorApplicationUserReplyNotification(userName: String, reply: String, applicationId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "backend_applications")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, applicationId.hashCode() + 8309, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = "$userName: $reply"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Antwort auf Creator-Bewerbung")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(applicationId.hashCode() + 8309, notification)
    }

    /** Zeigt eine Benachrichtigung für eine neue anonyme Frage im Nearby-Bereich. */
    fun showNearbyNewQuestionNotification(questionId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "nearby_questions")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, questionId.hashCode() + 8400, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_NEARBY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Neue anonyme Frage")
            .setContentText("Jemand hat dir eine anonyme Frage gestellt")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(android.graphics.Color.parseColor("#A8A800"))
            .setColorized(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(questionId.hashCode() + 8400, notification)
    }

    /** Zeigt eine Benachrichtigung wenn jemand den eigenen Spark kommentiert. */
    fun showSparkNewCommentNotification(commenterName: String, sparkId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "spark_view")
            putExtra("chat_id", sparkId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, sparkId.hashCode() + 8500, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SPARKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Neuer Kommentar auf deinen Spark")
            .setContentText("$commenterName hat kommentiert")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(sparkId.hashCode() + 8500, notification)
    }

    /** Zeigt eine Benachrichtigung wenn der eigene Spark-Kommentar geliked wurde. */
    fun showSparkCommentLikedNotification(likerName: String, sparkId: String, commentId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "spark_view")
            putExtra("chat_id", sparkId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, commentId.hashCode() + 8600, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SPARKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Dein Kommentar wurde geliked")
            .setContentText("$likerName mag deinen Kommentar")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(commentId.hashCode() + 8600, notification)
    }

    /** Zeigt eine Benachrichtigung wenn ein Parent-Account diesen Nutzer als Kind-Account einlädt. */
    fun showChildFamilyInviteNotification(parentName: String, inviteToken: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "family")
            putExtra("child_invite_token", inviteToken)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, inviteToken.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CONTACTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Familien-Einladung")
            .setContentText("$parentName möchte dich als Kind-Account einladen. Tippe zum Annehmen.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(inviteToken.hashCode() + 9500, notification)
    }

    fun showSparkCommentReplyNotification(replierName: String, sparkId: String, commentId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "spark_view")
            putExtra("chat_id", sparkId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, commentId.hashCode() + 8700, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SPARKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Antwort auf deinen Kommentar")
            .setContentText("$replierName hat deinen Kommentar beantwortet")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(commentId.hashCode() + 8700, notification)
    }

    /** Zeigt eine Glückwunsch-Benachrichtigung wenn der Nutzer 100 anonyme Fragen gestellt hat. */
    fun showNearby100QuestionsNotification() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "nearby")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 8900, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🎉 Lethe Team – Glückwunsch!")
            .setContentText("Du hast 100 Fragen gestellt und hoffentlich auch Antworten erhalten 🙌✨ Danke, dass du Lethe Nearby so aktiv nutzt! 💛")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "🎉 Glückwunsch! Du hast 100 anonyme Fragen in Lethe Nearby gestellt und hoffentlich auch spannende Antworten erhalten! 🙌✨\n\nDanke, dass du die Community so aktiv bereicherst. Das Lethe Team freut sich über dein Engagement! 💛\n\n👉 https://letheapp.de"
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(android.graphics.Color.parseColor("#A8A800"))
            .setColorized(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(8900, notification)
    }

    fun showNewStatusNotification(senderName: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "statuses")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, senderName.hashCode() + 8700, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CONTACTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText("hat einen neuen Status veröffentlicht.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(senderName.hashCode() + 8700, notification)
    }

    fun showStatusLikedNotification(likerUsername: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "status")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 8800, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CONTACTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Lethe – Status geliked")
            .setContentText("$likerUsername hat deinen Status geliked!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(likerUsername.hashCode() + 8800, notification)
    }

    /** Zeigt eine Benachrichtigung wenn jemand auf eine eigene Nachricht reagiert hat. */
    fun showReactionNotification(reactorName: String, emoji: String, reactorId: String, target: String = "deine Nachricht") {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_id", reactorId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, reactorId.hashCode() + 9100, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reactorName)
            .setContentText("hat auf $target mit $emoji reagiert.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(reactorId.hashCode() + 9100, notification)
    }

    /** Einmalige Hinweis-Benachrichtigung: Nearby-Profil aktivieren. */
    fun showNearbyProfileActivationPromptNotification() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "nearby_profile_setup")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 9300, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_NEARBY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Werde in der Nähe entdeckt!")
            .setContentText("Aktiviere dein Nearby-Profil und lerne neue Menschen in deiner Umgebung kennen.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Aktiviere dein Nearby-Profil und lerne neue Menschen in deiner Umgebung kennen."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(9300, notification)
    }
}
