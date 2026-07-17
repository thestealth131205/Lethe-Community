package com.securechat.app

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.securechat.app.data.crypto.CryptoManager
import com.securechat.app.data.local.ContactDao
import com.securechat.app.data.local.GroupDao
import com.securechat.app.data.local.GroupSenderKeyDao
import com.securechat.app.data.local.GroupSenderKeyEntity
import com.securechat.app.data.local.MessageDao
import com.securechat.app.data.network.ApiService
import com.securechat.app.data.network.FcmTokenRequest
import com.securechat.app.data.network.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Firebase Cloud Messaging Service.
 * Empfängt Push-Notifications wenn die App im Hintergrund oder nicht aktiv ist.
 *
 * Nachrichten-Typen:
 * - "new_message"      → Chat-Nachricht anzeigen (wenn WS offline war)
 * - "call_offer"       → Eingehenden Anruf anzeigen (Heads-up, Klingelton)
 * - "contact_request"  → Kontaktanfrage anzeigen
 */
@AndroidEntryPoint
class LetheFcmService : FirebaseMessagingService() {

    @Inject lateinit var apiService: ApiService
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var contactDao: ContactDao
    @Inject lateinit var groupDao: GroupDao
    @Inject lateinit var groupSenderKeyDao: GroupSenderKeyDao
    @Inject lateinit var messageDao: MessageDao

    /**
     * Wird aufgerufen wenn sich der FCM-Token erneuert (z.B. nach Neuinstallation).
     * Registriert den neuen Token sofort am Server.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.tag("LETHE_FCM").d("FCM-Token erneuert: ${token.take(20)}…")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                apiService.updateFcmToken(FcmTokenRequest(fcmToken = token))
                Timber.tag("LETHE_FCM").d("Neuer FCM-Token erfolgreich am Server registriert.")
            } catch (e: Exception) {
                Timber.tag("LETHE_FCM").e(e, "Token-Update fehlgeschlagen")
            }
        }
    }

    /**
     * Empfängt FCM-Datennachrichten.
     * Wird immer aufgerufen – auch wenn die App im Hintergrund ist (data-only messages).
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val type = message.data["type"] ?: return
        Timber.tag("LETHE_FCM").d("FCM empfangen: type=$type")

        when (type) {
            "new_message" -> {
                val senderId    = message.data["sender_id"] ?: return
                val senderName  = message.data["sender_name"] ?: "Unbekannt"
                val contentBlob = message.data["content_blob"] ?: ""
                val mediaType   = message.data["media_type"] ?: "text"
                val rawMediaUrl = message.data["media_url"] ?: ""
                val notifId     = (senderId.hashCode() and 0x7FFFFFFF) + 3000
                val messageId    = message.data["message_id"]
                val myUserId    = tokenManager.getUserId()
                val isOwnMessage = myUserId != null && senderId == myUserId
                // ViewModel benachrichtigen damit es die Nachricht per REST nachlädt
                // (WS hat sie evtl. nicht geliefert – z.B. bei Multi-Instance-Setup)
                FcmMessageBus.notifyNewMessage(senderId)
                CoroutineScope(Dispatchers.IO).launch {
                    // Nachricht entschlüsseln wenn möglich (E2EE: content_blob im v2:-Format)
                    val messagePreview = if (mediaType == "text" && contentBlob.startsWith("v2:")) {
                        try {
                            val contact = contactDao.getContactById(senderId)
                            if (contact != null && contact.publicKey.length > 100) {
                                CryptoManager.deriveSharedSecret(senderId, contact.publicKey)
                            }
                            val decrypted = CryptoManager.decrypt(senderId, contentBlob)
                            if (decrypted.startsWith("[🔐")) "Neue Nachricht" else decrypted
                        } catch (_: Exception) {
                            "Neue Nachricht"
                        }
                    } else if (mediaType == "text" && contentBlob.isNotBlank()) {
                        contentBlob.take(120)
                    } else when (mediaType) {
                        "image"    -> "📷 Bild"
                        "video"    -> "🎥 Video"
                        "audio"    -> "🎤 Sprachnachricht"
                        "document" -> "📎 Dokument"
                        "poll"     -> "📊 Umfrage"
                        else       -> "Neue Nachricht"
                    }
                    // media_url in absolute URL umwandeln (Server kann relative Pfade liefern)
                    val absMediaUrl = rawMediaUrl.takeIf { it.isNotBlank() }
                        ?.let { if (it.startsWith("http")) it else "https://letheapp.de$it" }
                    // Dedup: WS-Handler hat die Nachricht eventuell bereits als Notification gezeigt
                    // (z.B. bei aktiver Verbindung mit Display an). In dem Fall kein Duplikat erzeugen.
                    // Eigene Nachrichten (Mirror) nie als Notification anzeigen.
                    val alreadyShownByWs = messageId != null &&
                        (messageDao.isDeliveredAsNotification(messageId) == 1 ||
                         FcmMessageBus.wasNotificationShownByFcm(messageId))
                    if (!alreadyShownByWs) {
                        // FCM als einzige Benachrichtigungs-Quelle markieren (verhindert Duplikat vom WS-Handler)
                        if (messageId != null) FcmMessageBus.markNotificationShown(messageId)
                        // Profilbild des Kontakts laden, damit Android Auto / Wear OS den Avatar
                        // auch bei 1:1-Chats anzeigt (zuvor null → kein Avatar).
                        val senderProfileImg = contactDao.getContactById(senderId)?.profileImageUrl?.let {
                            if (it.startsWith("http")) it else "https://letheapp.de$it"
                        }
                        notificationHelper.showMessageNotification(
                            senderId = senderId,
                            senderName = senderName,
                            senderImageUrl = senderProfileImg,
                            messagePreview = messagePreview,
                            notificationId = notifId,
                            silent = isOwnMessage,
                            mediaType = mediaType,
                            mediaHttpUrl = absMediaUrl
                        )
                    } else {
                        Timber.tag("LETHE_FCM").d("FCM-Notification übersprungen (WS hat bereits gezeigt): $messageId")
                    }
                }
            }

            "group_message" -> {
                val groupId     = message.data["group_id"] ?: return
                val senderId    = message.data["sender_id"] ?: return
                val senderName  = message.data["sender_name"] ?: "Unbekannt"
                val groupName   = message.data["group_name"]?.takeIf { it.isNotBlank() } ?: senderName
                val mediaType   = message.data["media_type"] ?: "text"
                val messageId   = message.data["message_id"]
                val contentBlob = message.data["content_blob"] ?: ""
                val notifId     = (groupId.hashCode() and 0x7FFFFFFF) + 5000
                // ViewModel benachrichtigen damit Gruppennachrichten nachgeladen werden
                FcmMessageBus.notifyNewMessage(groupId)
                CoroutineScope(Dispatchers.IO).launch {
                    val messagePreview = when {
                        mediaType == "audio"    -> "$senderName: 🎤 Sprachnachricht"
                        mediaType == "image"    -> "$senderName: 📷 Bild"
                        mediaType == "video"    -> "$senderName: 🎥 Video"
                        mediaType == "document" -> "$senderName: 📎 Dokument"
                        mediaType == "poll"     -> "$senderName: 📊 Umfrage"
                        mediaType == "text" && contentBlob.startsWith("v2:") -> {
                            // E2EE-Gruppen-Blob entschlüsseln mit lokalem Sender-Key
                            suspend fun tryDecryptLocal(): String? = try {
                                val keyEntity = groupSenderKeyDao.getKey(groupId, senderId)
                                if (keyEntity != null) {
                                    val rawKey = android.util.Base64.decode(keyEntity.keyBase64, android.util.Base64.NO_WRAP)
                                    val result = CryptoManager.decryptGroupMessage(rawKey, contentBlob)
                                    if (result.startsWith("[🔐") || result == contentBlob) null else result
                                } else null
                            } catch (_: Exception) { null }

                            var decrypted = tryDecryptLocal()

                            if (decrypted == null) {
                                // Fallback: Keys vom Server nachladen (wie WS-Handler in MainViewModel)
                                try {
                                    val response = apiService.getMyGroupKeyBundle(groupId)
                                    if (response.isSuccessful) {
                                        val myId = tokenManager.getUserId()
                                        response.body()?.bundles?.forEach { entry ->
                                            if (myId != null && entry.ownerId == myId) return@forEach
                                            val existing = groupSenderKeyDao.getKey(groupId, entry.ownerId)
                                            if (existing != null && existing.version >= entry.version) return@forEach
                                            if (!CryptoManager.hasSharedSecret(entry.ownerId)) {
                                                val contact = contactDao.getContactById(entry.ownerId)
                                                if (contact != null && contact.publicKey.length > 100) {
                                                    CryptoManager.deriveSharedSecret(entry.ownerId, contact.publicKey)
                                                }
                                            }
                                            val rawKey = CryptoManager.decryptSenderKeyBundle(entry.ownerId, entry.encryptedKeyBundle)
                                            if (rawKey != null) {
                                                val keyBase64 = android.util.Base64.encodeToString(rawKey, android.util.Base64.NO_WRAP)
                                                groupSenderKeyDao.insertKey(GroupSenderKeyEntity(
                                                    groupId = groupId,
                                                    ownerId = entry.ownerId,
                                                    keyBase64 = keyBase64,
                                                    version = entry.version,
                                                    storedAt = System.currentTimeMillis()
                                                ))
                                            }
                                        }
                                    }
                                } catch (_: Exception) { }
                                decrypted = tryDecryptLocal()
                            }

                            if (decrypted != null) "$senderName: ${decrypted.take(120)}"
                            else "$senderName: Neue Nachricht"
                        }
                        mediaType == "text" && contentBlob.isNotBlank() ->
                            "$senderName: ${contentBlob.take(120)}"
                        else -> "$senderName: Neue Nachricht"
                    }
                    if (messageId != null) FcmMessageBus.markNotificationShown(messageId)
                    val senderProfileImg = contactDao.getContactById(senderId)?.profileImageUrl?.let {
                        if (it.startsWith("http")) it else "https://letheapp.de$it"
                    }
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        notificationHelper.showMessageNotification(
                            senderId = groupId,
                            senderName = groupName,
                            senderImageUrl = groupDao.getGroupById(groupId)?.groupImageUrl?.let { if (it.startsWith("http")) it else "https://letheapp.de$it" },
                            messagePreview = messagePreview,
                            notificationId = notifId,
                            silent = false,
                            mediaType = mediaType,
                            isGroup = true,
                            senderDisplayName = senderName,
                            senderProfileUrl = senderProfileImg
                        )
                    }
                }
            }

            "call_offer" -> {
                val callerId   = message.data["caller_id"] ?: return
                val callerName = message.data["caller_name"] ?: "Unbekannt"
                val callType   = message.data["call_type"] ?: "VIDEO"
                val sdpOffer   = message.data["sdp_offer"] ?: ""
                val isGroupCall = message.data["is_group_call"] == "1"
                val groupName   = message.data["group_name"]?.takeIf { it.isNotBlank() }
                val groupParticipants: List<Pair<String, String?>> = message.data["group_participants"]
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.map { it to null }
                    ?: emptyList()

                // Nur speichern wenn noch kein Anruf von diesem Anrufer aussteht
                if (IncomingCallStore.pendingCall?.callerId != callerId) {
                    IncomingCallStore.pendingCall = IncomingCallStore.PendingCall(
                        callerId      = callerId,
                        callerName    = callerName,
                        callerImageUrl = null,
                        sdpOffer      = sdpOffer,
                        callType      = callType,
                        isGroupCall   = isGroupCall,
                        groupParticipants = groupParticipants,
                        groupName     = groupName
                    )
                    // IncomingCallActivity auf dem Sperrbildschirm starten
                    IncomingCallActivity.startFromBackground(this)
                }
                notificationHelper.showIncomingCallNotification(callerName, callType)
            }

            "call_cancelled" -> {
                // Gegenseite hat aufgelegt/abgebrochen. Kommt auch an wenn der WS getrennt war
                // (Doze) und der eingehende Anruf nur per FCM-call_offer gezeigt wurde.
                val callerId = message.data["caller_id"]
                // Gespeicherten Anruf nur verwerfen wenn er von diesem Anrufer stammt
                // (sonst nicht den Store eines anderen, parallelen Anrufers löschen).
                if (callerId == null || IncomingCallStore.pendingCall?.callerId == callerId) {
                    IncomingCallStore.clear()
                }
                notificationHelper.cancelCallNotification()
                // Sperrbildschirm-Anruf-UI schliessen
                sendBroadcast(
                    android.content.Intent(IncomingCallActivity.ACTION_CALL_CANCELLED)
                        .setPackage(packageName)
                )
                // ViewModel informieren, falls der Anruf bereits angenommen/aktiv ist
                if (callerId != null) FcmMessageBus.notifyCallCancelled(callerId)
            }

            "contact_request" -> {
                val fromName       = message.data["from_name"] ?: "Unbekannt"
                val fromNumber     = message.data["from_number"] ?: ""
                val contactEntryId = message.data["contact_entry_id"] ?: fromNumber
                notificationHelper.showContactRequestNotification(fromName, fromNumber, contactEntryId)
            }

            "nearby_message" -> {
                val senderName = message.data["sender_name"] ?: "Nearby"
                val text       = message.data["text"] ?: "[Bild]"
                val matchId    = message.data["match_id"] ?: return
                notificationHelper.showNearbyMessageNotification(senderName, text, matchId)
            }

            "nearby_like" -> {
                val likerName = message.data["liker_name"] ?: "Jemand"
                notificationHelper.showNearbyLikeNotification(likerName)
            }

            "nearby_match" -> {
                val partnerName = message.data["partner_name"] ?: "Jemand"
                val matchId     = message.data["match_id"] ?: return
                notificationHelper.showNearbyMatchNotification(partnerName, matchId)
            }

            "creator_content" -> {
                val creatorName = message.data["creator_name"] ?: "Ein Creator"
                val title       = message.data["title"] ?: ""
                val contentId   = message.data["content_id"] ?: return
                notificationHelper.showCreatorContentNotification(creatorName, title, contentId)
            }

            "creator_livestream" -> {
                val creatorName = message.data["creator_name"] ?: "Ein Creator"
                val creatorId   = message.data["creator_id"] ?: return
                notificationHelper.showCreatorLivestreamNotification(creatorName, creatorId)
            }

            "creator_spark" -> {
                val creatorName = message.data["creator_name"] ?: "Ein Creator"
                val sparkId     = message.data["spark_id"] ?: return
                notificationHelper.showCreatorSparkNotification(creatorName, sparkId)
            }

            "new_support_ticket" -> {
                val category = message.data["category"] ?: ""
                val title    = message.data["title"] ?: "Neues Ticket"
                val ticketId = message.data["ticket_id"] ?: return
                notificationHelper.showNewSupportTicketNotification(category, title, ticketId)
            }

            "new_creator_application" -> {
                val applicantName = message.data["applicant_name"] ?: "Jemand"
                val applicationId = message.data["application_id"] ?: return
                notificationHelper.showNewCreatorApplicationNotification(applicantName, applicationId)
            }

            "nearby_new_question" -> {
                val questionId = message.data["question_id"] ?: return
                notificationHelper.showNearbyNewQuestionNotification(questionId)
            }

            "spark_new_comment" -> {
                val commenterName = message.data["commenter_name"] ?: "Jemand"
                val sparkId       = message.data["spark_id"] ?: return
                notificationHelper.showSparkNewCommentNotification(commenterName, sparkId)
            }

            "spark_comment_liked" -> {
                val likerName = message.data["liker_name"] ?: "Jemand"
                val sparkId   = message.data["spark_id"] ?: return
                val commentId = message.data["comment_id"] ?: return
                notificationHelper.showSparkCommentLikedNotification(likerName, sparkId, commentId)
            }

            "spark_comment_reply" -> {
                val replierName = message.data["replier_name"] ?: "Jemand"
                val sparkId     = message.data["spark_id"] ?: return
                val commentId   = message.data["comment_id"] ?: return
                notificationHelper.showSparkCommentReplyNotification(replierName, sparkId, commentId)
            }

            "child_family_invite" -> {
                val parentName  = message.data["parent_name"] ?: "Jemand"
                val inviteToken = message.data["invite_token"] ?: return
                // Token für spätere Verarbeitung in SharedPreferences speichern
                applicationContext.getSharedPreferences("lethe_family", android.content.Context.MODE_PRIVATE)
                    .edit().putString("pending_child_invite_token", inviteToken).apply()
                notificationHelper.showChildFamilyInviteNotification(parentName, inviteToken)
            }

            "reaction" -> {
                val reactorName = message.data["reactor_name"] ?: message.notification?.title ?: "Jemand"
                val emoji       = message.data["emoji"] ?: "❤️"
                val reactorId   = message.data["reactor_id"] ?: return
                notificationHelper.showReactionNotification(reactorName, emoji, reactorId)
            }

            "listen_together" -> {
                val fromName   = message.data["from_name"] ?: "Jemand"
                val trackTitle = message.data["track_title"] ?: ""
                notificationHelper.showListenTogetherNotification(fromName, trackTitle)
            }

            "new_status" -> {
                val senderName = message.data["sender_name"] ?: "Jemand"
                notificationHelper.showNewStatusNotification(senderName)
            }

            "status_liked" -> {
                val likerUsername = message.data["liker_username"] ?: "Jemand"
                notificationHelper.showStatusLikedNotification(likerUsername)
            }

            "nearby_reaction" -> {
                val emoji = message.data["emoji"] ?: "❤️"
                notificationHelper.showNearbyQuestionReactionNotification(emoji)
            }

            "game_invite" -> {
                val fromName = message.data["from_name"] ?: "Jemand"
                notificationHelper.showGameInviteNotification(fromName)
            }

            "nearby_100_questions" -> {
                notificationHelper.showNearby100QuestionsNotification()
            }

            "jod_score" -> {
                val competitorUsername = message.data["username"] ?: return
                val competitorScore    = message.data["score"]?.toIntOrNull() ?: return
                notificationHelper.showJodScoreNotification(competitorUsername, competitorScore)
            }
        }
    }
}
