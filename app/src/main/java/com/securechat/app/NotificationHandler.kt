package com.securechat.app

import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.securechat.app.data.local.ContactDao
import com.securechat.app.data.network.WebSocketManager
import com.securechat.app.data.network.WebSocketMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Zentraler ForegroundService für WebSocket-Keepalive.
 *
 * - Ping-Loop: alle 30s wenn verbunden, alle 10s wenn getrennt (löst Reconnect aus)
 * - Läuft dank android:stopWithTask="false" weiter, wenn die App aus den Recents gewischt wird,
 *   sodass der WebSocket verbunden bleibt (kein "du könntest Nachrichten haben"-False-Positive)
 * - Startet WorkManager-Jobs für belt-and-suspenders-Hintergrundprüfung
 * - BootReceiver reiht nach Geräte-Neustart/App-Update einen Reconnect-Job ein, der diesen
 *   Service wieder startet
 */
@AndroidEntryPoint
class NotificationHandler : Service() {

    @Inject
    lateinit var webSocketManager: WebSocketManager

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var contactDao: ContactDao

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wsCollectorStarted = false

    companion object {
        const val PING_INTERVAL_CONNECTED    = 30_000L  // 30s wenn verbunden
        const val PING_INTERVAL_DISCONNECTED = 10_000L  // 10s wenn getrennt
        const val NOTIFICATION_ID_FOREGROUND = 42

        fun start(context: Context) {
            context.startForegroundService(Intent(context, NotificationHandler::class.java))
        }

        fun requestBatteryOptimization(context: Context) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                try {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) { /* Einige Geräte unterstützen diesen Intent nicht */ }
            }
        }
    }

    private val pingRunnable = object : Runnable {
        override fun run() {
            val connected = webSocketManager.isConnected()
            webSocketManager.sendPing()  // ping wenn verbunden, reconnect-trigger wenn tot
            if (connected) {
                Timber.tag("LETHE_BG").d("NotificationHandler: Ping – WS verbunden")
                handler.postDelayed(this, PING_INTERVAL_CONNECTED)
            } else {
                Timber.tag("LETHE_BG").w("NotificationHandler: WS nicht verbunden – Reconnect-Versuch")
                handler.postDelayed(this, PING_INTERVAL_DISCONNECTED)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID_FOREGROUND, buildForegroundNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID_FOREGROUND, buildForegroundNotification())
            }
        } catch (e: Exception) {
            Timber.tag("LETHE_BG").w("NotificationHandler: startForeground fehlgeschlagen – Service wird beendet: ${e.message}")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        acquireWakeLock()
        handler.post(pingRunnable)
        scheduleWorkManagerJobs()
        startWsNotificationCollector()
        Timber.tag("LETHE_BG").i("NotificationHandler: Service gestartet")
        return START_STICKY
    }

    /**
     * Verarbeitet WS-Nachrichten lifecycle-unabhängig zu Benachrichtigungen (Anrufe, Nearby,
     * Spiele-Rangliste, Spark, VIP-Diskussionen). Läuft dauerhaft solange der Service lebt, wird
     * aber übersprungen solange MainViewModel selbst aktiv ist und dieselben WS-Nachrichten
     * verarbeitet (verhindert doppelte Benachrichtigungen). So bekommen auch FOSS/F-Droid-Nutzer
     * (kein FCM-Fallback) diese Benachrichtigungen zuverlässig, selbst wenn der Task aus den
     * Recents entfernt und das ViewModel dadurch zerstört wurde.
     */
    private fun startWsNotificationCollector() {
        if (wsCollectorStarted) return
        wsCollectorStarted = true
        serviceScope.launch {
            webSocketManager.incomingMessages
                .catch { e -> Timber.tag("LETHE_BG").w("WS-Notification-Collector-Fehler: ${e.message}") }
                .collect { msg ->
                    if (FcmMessageBus.isViewModelActive) return@collect
                    try {
                        handleWsMessageForNotification(msg)
                    } catch (e: Exception) {
                        Timber.tag("LETHE_BG").w("Fehler bei WS-Benachrichtigung (${msg.type}): ${e.message}")
                    }
                }
        }
    }

    private suspend fun handleWsMessageForNotification(msg: WebSocketMessage) {
        val payload = msg.payload as? Map<*, *>
        when (msg.type) {
            "call_offer" -> {
                val senderId = msg.senderId ?: return
                val sdp = payload?.get("sdp") as? String ?: return
                val callType = payload?.get("call_type") as? String ?: "VIDEO"
                if (IncomingCallStore.pendingCall?.callerId == senderId) return
                val contact = contactDao.getContactById(senderId)
                val callerName = contact?.customAlias ?: contact?.username ?: contact?.fakeNumber ?: senderId
                val callerImage = contact?.profileImageUrl?.let { url ->
                    if (url.startsWith("http")) url else "https://letheapp.de$url"
                }
                val isGroupCall = payload?.get("is_group_call") as? Boolean ?: false
                val groupParticipants = (payload?.get("group_participants") as? List<*>)
                    ?.mapNotNull { item ->
                        val m = item as? Map<*, *> ?: return@mapNotNull null
                        val name = m["name"] as? String ?: return@mapNotNull null
                        val img = (m["image_url"] as? String)?.takeIf { it.isNotEmpty() }
                        name to img
                    } ?: emptyList()
                val groupName = (payload?.get("group_name") as? String)?.takeIf { it.isNotEmpty() }
                IncomingCallStore.pendingCall = IncomingCallStore.PendingCall(
                    callerId = senderId,
                    callerName = callerName,
                    callerImageUrl = callerImage,
                    sdpOffer = sdp,
                    callType = callType,
                    isGroupCall = isGroupCall,
                    groupParticipants = groupParticipants,
                    groupName = groupName
                )
                notificationHelper.showIncomingCallNotification(callerName, callType)
                val kg = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                if (kg?.isKeyguardLocked == true) {
                    IncomingCallActivity.startFromBackground(applicationContext)
                }
            }

            "nearby_like" -> {
                val likerName = payload?.get("username") as? String ?: "Jemand"
                notificationHelper.showNearbyLikeNotification(likerName)
            }

            "nearby_match" -> {
                val partnerName = payload?.get("partner_username") as? String ?: "Jemand"
                val matchId = payload?.get("match_id") as? String ?: return
                notificationHelper.showNearbyMatchNotification(partnerName, matchId)
            }

            "nearby_message" -> {
                val senderName = payload?.get("sender_name") as? String ?: "Nearby"
                val matchId = payload?.get("match_id") as? String ?: return
                val content = (payload?.get("content") as? String)?.takeIf { it.isNotBlank() }
                val mediaType = payload?.get("media_type") as? String
                val preview = content ?: if (mediaType != null) "[$mediaType]" else "[Bild]"
                notificationHelper.showNearbyMessageNotification(senderName, preview, matchId)
            }

            "nearby_new_question" -> {
                val questionId = payload?.get("question_id") as? String ?: return
                notificationHelper.showNearbyNewQuestionNotification(questionId)
            }

            "spark_new_comment" -> {
                val commenterName = payload?.get("commenter_name") as? String ?: "Jemand"
                val sparkId = payload?.get("spark_id") as? String ?: return
                notificationHelper.showSparkNewCommentNotification(commenterName, sparkId)
            }

            "spark_comment_liked" -> {
                val likerName = payload?.get("liker_name") as? String ?: "Jemand"
                val sparkId = payload?.get("spark_id") as? String ?: return
                val commentId = payload?.get("comment_id") as? String ?: return
                notificationHelper.showSparkCommentLikedNotification(likerName, sparkId, commentId)
            }

            "spark_comment_reply" -> {
                val replierName = payload?.get("replier_name") as? String ?: "Jemand"
                val sparkId = payload?.get("spark_id") as? String ?: return
                val commentId = payload?.get("comment_id") as? String ?: return
                notificationHelper.showSparkCommentReplyNotification(replierName, sparkId, commentId)
            }

            "jod_score" -> {
                val username = payload?.get("username") as? String ?: return
                val score = (payload?.get("score") as? String)?.toIntOrNull() ?: return
                notificationHelper.showJodScoreNotification(username, score)
            }

            "vip_discussion_reply" -> {
                val replierName = payload?.get("replier_name") as? String ?: "Jemand"
                val discussionTitle = payload?.get("discussion_title") as? String ?: ""
                val discussionId = payload?.get("discussion_id") as? String ?: return
                notificationHelper.showVipDiscussionReplyNotification(replierName, discussionTitle, discussionId)
            }

            "new_support_ticket" -> {
                val category = payload?.get("category") as? String ?: ""
                val title = payload?.get("title") as? String ?: "Neues Ticket"
                val ticketId = payload?.get("ticket_id") as? String ?: return
                notificationHelper.showNewSupportTicketNotification(category, title, ticketId)
            }

            "support_reply" -> {
                val ticketId = payload?.get("ticket_id") as? String ?: return
                val title = payload?.get("title") as? String ?: "Support-Ticket"
                val reply = payload?.get("reply") as? String ?: ""
                notificationHelper.showSupportReplyNotification(title, reply, ticketId)
            }

            "support_ticket_user_reply" -> {
                val ticketId = payload?.get("ticket_id") as? String ?: return
                val title = payload?.get("title") as? String ?: "Support-Ticket"
                val userName = payload?.get("user_name") as? String ?: "Ein Nutzer"
                val reply = payload?.get("reply") as? String ?: ""
                notificationHelper.showSupportTicketUserReplyNotification(userName, title, reply, ticketId)
            }

            "new_user_report" -> {
                val reportId = payload?.get("report_id") as? String ?: return
                val reason = payload?.get("reason") as? String ?: ""
                val reporterName = payload?.get("reporter_name") as? String ?: "Jemand"
                notificationHelper.showNewUserReportNotification(reporterName, reason, reportId)
            }

            "content_liked" -> {
                val contentId = payload?.get("content_id") as? String ?: return
                val likerName = payload?.get("liker_name") as? String ?: "Jemand"
                val isSpark = payload?.get("is_spark") as? Boolean ?: false
                notificationHelper.showContentLikedNotification(likerName, contentId, isSpark)
            }

            "discussion_liked" -> {
                val discussionId = payload?.get("discussion_id") as? String ?: return
                val discussionTitle = payload?.get("discussion_title") as? String ?: ""
                val likerName = payload?.get("liker_name") as? String ?: "Jemand"
                notificationHelper.showDiscussionLikedNotification(likerName, discussionTitle, discussionId)
            }

            "new_creator_application" -> {
                val applicantName = payload?.get("applicant_name") as? String ?: "Jemand"
                val applicationId = payload?.get("application_id") as? String ?: return
                notificationHelper.showNewCreatorApplicationNotification(applicantName, applicationId)
            }

            "creator_application_reviewed" -> {
                val status = payload?.get("status") as? String ?: return
                val adminNote = payload?.get("admin_note") as? String ?: ""
                val applicationId = payload?.get("application_id") as? String ?: "reviewed"
                notificationHelper.showCreatorApplicationReviewedNotification(status, adminNote, applicationId)
            }

            "creator_application_message" -> {
                val applicationId = payload?.get("application_id") as? String ?: return
                val reply = payload?.get("reply") as? String ?: ""
                notificationHelper.showCreatorApplicationMessageNotification(reply, applicationId)
            }

            "creator_application_user_reply" -> {
                val applicationId = payload?.get("application_id") as? String ?: return
                val userName = payload?.get("user_name") as? String ?: "Ein Nutzer"
                val reply = payload?.get("reply") as? String ?: ""
                notificationHelper.showCreatorApplicationUserReplyNotification(userName, reply, applicationId)
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Task aus den Recents gewischt. Dank android:stopWithTask="false" (Manifest) läuft dieser
        // Service weiter → WebSocket bleibt verbunden, es gibt KEINE False-Positive-Benachrichtigung.
        // Zur Sicherheit den Ping-Loop erneut anstoßen und einen WorkManager-Reconnect einreihen,
        // falls der Prozess zwischenzeitlich doch eingefroren wurde.
        Timber.tag("LETHE_BG").i("NotificationHandler: Task entfernt – Service läuft weiter (stopWithTask=false)")
        handler.removeCallbacks(pingRunnable)
        handler.post(pingRunnable)
        val restartRequest = androidx.work.OneTimeWorkRequestBuilder<WebSocketReconnectWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .build()
        androidx.work.WorkManager.getInstance(applicationContext)
            .enqueue(restartRequest)
        super.onTaskRemoved(rootIntent)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Lethe:NotificationHandler"
        ).apply {
            setReferenceCounted(false)
            acquire(60 * 60 * 1000L)  // max 60 Minuten; Handler-Loop hält Service aktiv
        }
    }

    private fun buildForegroundNotification() =
        NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID_FOREGROUND)
            .setContentTitle("Lethe")
            .setContentText("Verbindung aktiv")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()

    private fun scheduleWorkManagerJobs() {
        val wm = WorkManager.getInstance(this)

        val contactRequest = PeriodicWorkRequestBuilder<ContactRequestWorker>(15, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(
            "contact_request_check",
            ExistingPeriodicWorkPolicy.KEEP,
            contactRequest
        )

        val reconnectRequest = PeriodicWorkRequestBuilder<WebSocketReconnectWorker>(15, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(
            "websocket_reconnect",
            ExistingPeriodicWorkPolicy.KEEP,
            reconnectRequest
        )

        Timber.tag("LETHE_BG").d("NotificationHandler: WorkManager-Jobs geplant")
    }

    // Android 14+: dataSync-Services erhalten onTimeout() wenn das System-Timeout abläuft.
    // Ohne Override wird ForegroundServiceDidNotStopInTimeException geworfen.
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTimeout(startId: Int) {
        Timber.tag("LETHE_BG").w("NotificationHandler: System-Timeout – Service wird sauber beendet")
        handler.removeCallbacks(pingRunnable)
        wakeLock?.let { if (it.isHeld) it.release() }
        stopSelf(startId)
    }

    // Android 15+ (API 35): neue Signatur mit fgsType – ohne Override wird nur diese aufgerufen
    @RequiresApi(35)
    override fun onTimeout(startId: Int, fgsType: Int) {
        Timber.tag("LETHE_BG").w("NotificationHandler: System-Timeout (API 35+) – Service wird sauber beendet")
        handler.removeCallbacks(pingRunnable)
        wakeLock?.let { if (it.isHeld) it.release() }
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(pingRunnable)
        wakeLock?.let { if (it.isHeld) it.release() }
        serviceScope.cancel()
        Timber.tag("LETHE_BG").i("NotificationHandler: Service zerstört – wird durch START_STICKY neu gestartet")
        super.onDestroy()
    }
}
