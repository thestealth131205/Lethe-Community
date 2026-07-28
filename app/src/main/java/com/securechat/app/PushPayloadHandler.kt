package com.securechat.app

import android.content.Context
import com.securechat.app.data.crypto.CryptoManager
import com.securechat.app.data.local.ContactDao
import com.securechat.app.data.local.GroupDao
import com.securechat.app.data.local.GroupSenderKeyDao
import com.securechat.app.data.local.GroupSenderKeyEntity
import com.securechat.app.data.local.MessageDao
import com.securechat.app.data.network.ApiService
import com.securechat.app.data.network.TokenManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transport-unabhängige Verarbeitung von Push-Payloads.
 *
 * Dieselbe `when(type)`-Logik wird von zwei Transporten aufgerufen:
 * - `LetheFcmService` (Play-Store-Build, FCM-Data-Messages)
 * - dem persistenten WebSocket-Foreground-Service (FOSS/F-Droid-Build, kein FCM)
 *
 * So bleibt die Anzeige-/Entschlüsselungs-Logik an EINER Stelle und beide Build-Flavors
 * teilen sich denselben Code. Erwartet die identische `data`-Map wie eine FCM-Data-Message
 * (Schlüssel `type`, `sender_id`, …).
 */
@Singleton
class PushPayloadHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val notificationHelper: NotificationHelper,
    private val tokenManager: TokenManager,
    private val contactDao: ContactDao,
    private val groupDao: GroupDao,
    private val groupSenderKeyDao: GroupSenderKeyDao,
    private val messageDao: MessageDao,
) {

    /**
     * Verarbeitet eine Push-Payload. `data` muss den Schlüssel `type` enthalten,
     * ansonsten wird nichts getan.
     */
    fun handle(data: Map<String, String>) {
        val type = data["type"] ?: return
        Timber.tag("LETHE_PUSH").d("Push verarbeitet: type=$type")

        when (type) {
            "new_message" -> {
                val senderId    = data["sender_id"] ?: return
                val senderName  = data["sender_name"] ?: "Unbekannt"
                val contentBlob = data["content_blob"] ?: ""
                val mediaType   = data["media_type"] ?: "text"
                val rawMediaUrl = data["media_url"] ?: ""
                val notifId     = (senderId.hashCode() and 0x7FFFFFFF) + 3000
                val messageId    = data["message_id"]
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
                        Timber.tag("LETHE_PUSH").d("Push-Notification übersprungen (WS hat bereits gezeigt): $messageId")
                    }
                }
            }

            "group_message" -> {
                val groupId     = data["group_id"] ?: return
                val senderId    = data["sender_id"] ?: return
                val senderName  = data["sender_name"] ?: "Unbekannt"
                val groupName   = data["group_name"]?.takeIf { it.isNotBlank() } ?: senderName
                val mediaType   = data["media_type"] ?: "text"
                val messageId   = data["message_id"]
                val contentBlob = data["content_blob"] ?: ""
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
                    withContext(Dispatchers.Main) {
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
                val callerId   = data["caller_id"] ?: return
                val callerName = data["caller_name"] ?: "Unbekannt"
                val callType   = data["call_type"] ?: "VIDEO"
                val sdpOffer   = data["sdp_offer"] ?: ""
                val isGroupCall = data["is_group_call"] == "1"
                val groupName   = data["group_name"]?.takeIf { it.isNotBlank() }
                val groupParticipants: List<Pair<String, String?>> = data["group_participants"]
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
                    IncomingCallActivity.startFromBackground(context)
                }
                notificationHelper.showIncomingCallNotification(callerName, callType)
            }

            "call_cancelled" -> {
                // Gegenseite hat aufgelegt/abgebrochen. Kommt auch an wenn der WS getrennt war
                // (Doze) und der eingehende Anruf nur per FCM-call_offer gezeigt wurde.
                val callerId = data["caller_id"]
                // Gespeicherten Anruf nur verwerfen wenn er von diesem Anrufer stammt
                // (sonst nicht den Store eines anderen, parallelen Anrufers löschen).
                if (callerId == null || IncomingCallStore.pendingCall?.callerId == callerId) {
                    IncomingCallStore.clear()
                }
                notificationHelper.cancelCallNotification()
                // Sperrbildschirm-Anruf-UI schliessen
                context.sendBroadcast(
                    android.content.Intent(IncomingCallActivity.ACTION_CALL_CANCELLED)
                        .setPackage(context.packageName)
                )
                // ViewModel informieren, falls der Anruf bereits angenommen/aktiv ist
                if (callerId != null) FcmMessageBus.notifyCallCancelled(callerId)
            }

            "contact_request" -> {
                val fromName       = data["from_name"] ?: "Unbekannt"
                val fromNumber     = data["from_number"] ?: ""
                val contactEntryId = data["contact_entry_id"] ?: fromNumber
                notificationHelper.showContactRequestNotification(fromName, fromNumber, contactEntryId)
            }

            "nearby_message" -> {
                val senderName = data["sender_name"] ?: "Nearby"
                val text       = data["text"] ?: "[Bild]"
                val matchId    = data["match_id"] ?: return
                notificationHelper.showNearbyMessageNotification(senderName, text, matchId)
            }

            "nearby_like" -> {
                val likerName = data["liker_name"] ?: "Jemand"
                notificationHelper.showNearbyLikeNotification(likerName)
            }

            "nearby_match" -> {
                val partnerName = data["partner_name"] ?: "Jemand"
                val matchId     = data["match_id"] ?: return
                notificationHelper.showNearbyMatchNotification(partnerName, matchId)
            }

            "creator_content" -> {
                val creatorName = data["creator_name"] ?: "Ein Creator"
                val title       = data["title"] ?: ""
                val contentId   = data["content_id"] ?: return
                notificationHelper.showCreatorContentNotification(creatorName, title, contentId)
            }

            "creator_livestream" -> {
                val creatorName = data["creator_name"] ?: "Ein Creator"
                val creatorId   = data["creator_id"] ?: return
                notificationHelper.showCreatorLivestreamNotification(creatorName, creatorId)
            }

            "creator_spark" -> {
                val creatorName = data["creator_name"] ?: "Ein Creator"
                val sparkId     = data["spark_id"] ?: return
                notificationHelper.showCreatorSparkNotification(creatorName, sparkId)
            }

            "new_support_ticket" -> {
                val category = data["category"] ?: ""
                val title    = data["title"] ?: "Neues Ticket"
                val ticketId = data["ticket_id"] ?: return
                notificationHelper.showNewSupportTicketNotification(category, title, ticketId)
            }

            "new_creator_application" -> {
                val applicantName = data["applicant_name"] ?: "Jemand"
                val applicationId = data["application_id"] ?: return
                notificationHelper.showNewCreatorApplicationNotification(applicantName, applicationId)
            }

            "nearby_new_question" -> {
                val questionId = data["question_id"] ?: return
                notificationHelper.showNearbyNewQuestionNotification(questionId)
            }

            "spark_new_comment" -> {
                val commenterName = data["commenter_name"] ?: "Jemand"
                val sparkId       = data["spark_id"] ?: return
                notificationHelper.showSparkNewCommentNotification(commenterName, sparkId)
            }

            "spark_comment_liked" -> {
                val likerName = data["liker_name"] ?: "Jemand"
                val sparkId   = data["spark_id"] ?: return
                val commentId = data["comment_id"] ?: return
                notificationHelper.showSparkCommentLikedNotification(likerName, sparkId, commentId)
            }

            "spark_comment_reply" -> {
                val replierName = data["replier_name"] ?: "Jemand"
                val sparkId     = data["spark_id"] ?: return
                val commentId   = data["comment_id"] ?: return
                notificationHelper.showSparkCommentReplyNotification(replierName, sparkId, commentId)
            }

            "child_family_invite" -> {
                val parentName  = data["parent_name"] ?: "Jemand"
                val inviteToken = data["invite_token"] ?: return
                // Token für spätere Verarbeitung in SharedPreferences speichern
                context.getSharedPreferences("lethe_family", Context.MODE_PRIVATE)
                    .edit().putString("pending_child_invite_token", inviteToken).apply()
                notificationHelper.showChildFamilyInviteNotification(parentName, inviteToken)
            }

            "reaction" -> {
                val reactorName = data["reactor_name"] ?: "Jemand"
                val emoji       = data["emoji"] ?: "❤️"
                val reactorId   = data["reactor_id"] ?: return
                notificationHelper.showReactionNotification(reactorName, emoji, reactorId)
            }

            "listen_together" -> {
                val fromName   = data["from_name"] ?: "Jemand"
                val trackTitle = data["track_title"] ?: ""
                notificationHelper.showListenTogetherNotification(fromName, trackTitle)
            }

            "new_status" -> {
                val senderName = data["sender_name"] ?: "Jemand"
                notificationHelper.showNewStatusNotification(senderName)
            }

            "status_liked" -> {
                val likerUsername = data["liker_username"] ?: "Jemand"
                notificationHelper.showStatusLikedNotification(likerUsername)
            }

            "nearby_reaction" -> {
                val emoji = data["emoji"] ?: "❤️"
                notificationHelper.showNearbyQuestionReactionNotification(emoji)
            }

            "game_invite" -> {
                val fromName = data["from_name"] ?: "Jemand"
                notificationHelper.showGameInviteNotification(fromName)
            }

            "nearby_100_questions" -> {
                notificationHelper.showNearby100QuestionsNotification()
            }

            "jod_score" -> {
                val competitorUsername = data["username"] ?: return
                val competitorScore    = data["score"]?.toIntOrNull() ?: return
                notificationHelper.showJodScoreNotification(competitorUsername, competitorScore)
            }
        }
    }
}
