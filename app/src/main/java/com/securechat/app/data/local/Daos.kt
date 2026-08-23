package com.securechat.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    // Es gibt meist nur einen eingeloggten User (uns selbst)
    @Query("SELECT * FROM user_me LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("DELETE FROM user_me")
    suspend fun clearUser()
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE userId = :userId")
    suspend fun getContactById(userId: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Update
    suspend fun updateContact(contact: ContactEntity)

    @Delete
    suspend fun delete(contact: ContactEntity)

    @Query("DELETE FROM contacts")
    suspend fun clearAll()

    @Query("UPDATE contacts SET customAlias = :alias WHERE userId = :userId")
    suspend fun updateAlias(userId: String, alias: String?)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM (SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit) ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String, limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(): MessageEntity?

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    fun getLastMessageForChat(chatId: String): Flow<MessageEntity?>

    @Query("SELECT chatId FROM messages GROUP BY chatId ORDER BY MAX(timestamp) DESC")
    fun getChatIdsSortedByRecent(): Flow<List<String>>

    /** Chat-IDs sortiert nach Anzahl ausgetauschter Nachrichten (am häufigsten zuerst). Für Weiterleiten-Screen. */
    @Query("SELECT chatId FROM messages GROUP BY chatId ORDER BY COUNT(*) DESC")
    fun getChatIdsSortedByFrequency(): Flow<List<String>>

    @Query("UPDATE messages SET isRead = 1 WHERE senderId = :senderId AND receiverId = :receiverId")
    suspend fun markAllRead(senderId: String, receiverId: String)

    @Query("UPDATE messages SET isSent = 1 WHERE messageId = :messageId")
    suspend fun markDelivered(messageId: String)

    // --- Delivery Status (neu) ---

    @Query("UPDATE messages SET messageId = :serverId, deliveryStatus = 1 WHERE clientMessageId = :clientId")
    suspend fun ackMessage(clientId: String, serverId: String)

    @Query("UPDATE messages SET deliveryStatus = 2 WHERE messageId = :messageId")
    suspend fun markDeliveredNew(messageId: String)

    @Query("UPDATE messages SET deliveryStatus = :status WHERE messageId = :messageId")
    suspend fun updateDeliveryStatusByMessageId(messageId: String, status: Int)

    @Query("UPDATE messages SET deliveryStatus = 3 WHERE chatId = :chatId AND senderId = :myId")
    suspend fun markAllReadNew(chatId: String, myId: String)

    @Query("SELECT * FROM messages WHERE clientMessageId = :clientId LIMIT 1")
    suspend fun getMessageByClientId(clientId: String): MessageEntity?

    @Query("UPDATE messages SET mediaUrl = :mediaUrl, deliveryStatus = 0 WHERE clientMessageId = :clientId")
    suspend fun updateMessageUrl(clientId: String, mediaUrl: String)

    @Query("UPDATE messages SET mediaUrl = :mediaUrl, mediaType = :mediaType, deliveryStatus = 0 WHERE clientMessageId = :clientId")
    suspend fun updateMessageUrlAndMediaType(clientId: String, mediaUrl: String, mediaType: String)

    @Query("UPDATE messages SET content = :content, mediaUrl = :mediaUrl WHERE clientMessageId = :clientId")
    suspend fun updateMessageUrlAndContent(clientId: String, mediaUrl: String, content: String)

    @Query("UPDATE messages SET isSent = 1, deliveryStatus = 3 WHERE clientMessageId = :clientId")
    suspend fun markSelfNoteDelivered(clientId: String)

    @Query("DELETE FROM messages WHERE clientMessageId = :clientId")
    suspend fun deleteMessageByClientId(clientId: String)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun clearMessagesForChat(chatId: String)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesForChat(chatId: String, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE localId = :localId LIMIT 1")
    suspend fun getMessageByLocalId(localId: Long): MessageEntity?

    @Query("DELETE FROM messages WHERE localId IN (:ids)")
    suspend fun deleteMessagesByIds(ids: List<Long>)

    @Query("UPDATE messages SET isImportant = :value WHERE localId = :localId")
    suspend fun setImportant(localId: Long, value: Boolean)

    @Query("UPDATE messages SET content = :newContent, isEdited = 1 WHERE localId = :localId")
    suspend fun editMessage(localId: Long, newContent: String)

    @Query("UPDATE messages SET content = :newContent, isEdited = 1 WHERE messageId = :messageId")
    suspend fun editMessageByServerId(messageId: String, newContent: String)

    /** Löscht eine Nachricht anhand der Server-ID (z.B. wenn der Sender sie für alle löscht). */
    @Query("DELETE FROM messages WHERE messageId = :messageId")
    suspend fun deleteMessageByServerId(messageId: String)

    @Query("SELECT DISTINCT chatId FROM messages GROUP BY chatId ORDER BY MAX(timestamp) DESC LIMIT :limit")
    suspend fun getRecentChatIds(limit: Int): List<String>

    @Query("SELECT COUNT(*) FROM messages WHERE senderId = :senderId AND receiverId = :myId AND isRead = 0")
    suspend fun getUnreadCountFromSender(senderId: String, myId: String): Int

    /** Flow-basierte Ungelesen-Zählung pro Kontakt (für LiveUI-Updates in ContactlistScreen). */
    @Query("SELECT COUNT(*) FROM messages WHERE senderId = :senderId AND receiverId = :myId AND isRead = 0")
    fun getUnreadCountForContact(senderId: String, myId: String): Flow<Int>

    /** Flow-basierte Ungelesen-Zählung pro Gruppe (chatId = groupId, receiverId = groupId). */
    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :groupId AND senderId != :myId AND isRead = 0")
    fun getUnreadCountForGroup(groupId: String, myId: String): Flow<Int>

    /** Markiert alle ungelesenen Nachrichten einer Gruppe als gelesen. */
    @Query("UPDATE messages SET isRead = 1 WHERE chatId = :groupId AND senderId != :myId")
    suspend fun markGroupMessagesRead(groupId: String, myId: String)

    /** Gesamtzahl aller ungelesenen Nachrichten inkl. Gruppen (für BottomNav-Badge).
     *  Für 1:1-Chats: receiverId = myId. Für Gruppen: chatId = receiverId (beide = groupId). */
    @Query("SELECT COUNT(*) FROM messages WHERE senderId != :myId AND isRead = 0 AND (receiverId = :myId OR chatId = receiverId)")
    fun getTotalUnreadCount(myId: String): Flow<Int>

    /** Alle ungelesenen Nachrichten eines bestimmten Absenders (für Read-Receipt-After-Reply). */
    @Query("SELECT * FROM messages WHERE senderId = :senderId AND receiverId = :myId AND deliveryStatus < 3")
    suspend fun getUnreadMessagesFrom(senderId: String, myId: String): List<MessageEntity>

    /** Alle ungelesenen Nachrichten einer Gruppe (chatId = groupId, eigene ausgeschlossen). */
    @Query("SELECT * FROM messages WHERE chatId = :groupId AND senderId != :myId AND deliveryStatus < 3")
    suspend fun getUnreadGroupMessages(groupId: String, myId: String): List<MessageEntity>

    @Query("UPDATE messages SET deliveryStatus = :status WHERE localId = :localId")
    suspend fun updateDeliveryStatus(localId: Long, status: Int)

    /** Markiert eine Sprachnachricht als vollständig abgespielt (persistiert über App-Neustarts). */
    @Query("UPDATE messages SET isAudioPlayed = 1 WHERE mediaUrl = :url")
    suspend fun markAudioPlayed(url: String)

    /** Markiert eine Sprachnachricht per Server-ID als abgespielt (z.B. wenn Empfänger-Bestätigung vom Server kommt). */
    @Query("UPDATE messages SET isAudioPlayed = 1 WHERE messageId = :messageId")
    suspend fun markAudioPlayedByMessageId(messageId: String)

    /** Gibt alle mediaUrls zurück, die als abgespielt markiert sind. */
    @Query("SELECT mediaUrl FROM messages WHERE isAudioPlayed = 1 AND mediaUrl IS NOT NULL")
    fun getPlayedAudioUrls(): Flow<List<String>>

    /** Markiert eine Nachricht als P2P-zugestellt (kein Server-Roundtrip). */
    @Query("UPDATE messages SET isP2pDelivered = 1 WHERE localId = :localId")
    suspend fun markP2pDelivered(localId: Long)

    /** Markiert eine Nachricht als "Benachrichtigung wurde bereits gezeigt" (Dedup). */
    @Query("UPDATE messages SET isDeliveredAsNotification = 1 WHERE messageId = :messageId")
    suspend fun markDeliveredAsNotification(messageId: String)

    /** Prüft ob für eine Nachricht bereits eine Benachrichtigung gezeigt wurde. */
    @Query("SELECT COUNT(*) FROM messages WHERE messageId = :messageId AND isDeliveredAsNotification = 1")
    suspend fun isDeliveredAsNotification(messageId: String): Int

    /** Opt-in Chat-Backup: markiert eine Nachricht als bereits als Klartext gesichert (Dedup). */
    @Query("UPDATE messages SET backedUp = 1 WHERE messageId = :messageId")
    suspend fun markBackedUp(messageId: String)

    /** Prüft ob eine Nachricht bereits per Chat-Backup gesichert wurde (Dedup-Guard). */
    @Query("SELECT COUNT(*) FROM messages WHERE messageId = :messageId AND backedUp = 1")
    suspend fun isBackedUp(messageId: String): Int

    /** Opt-in Chat-Backup Stufe 3 (Backfill): alle lokal bereits im Klartext vorliegenden
     * Text-Nachrichten, die noch nicht gesichert wurden. */
    @Query("SELECT * FROM messages WHERE backedUp = 0 AND mediaType = 'text' AND messageId IS NOT NULL AND content IS NOT NULL AND content != ''")
    suspend fun getUnbackedUpTextMessages(): List<MessageEntity>

    /** Opt-in Chat-Backup Stufe 3 (Purge bei Deaktivierung): setzt alle Dedup-Flags lokal zurück,
     * damit ein späteres Re-Aktivieren wieder sauber backfillt. */
    @Query("UPDATE messages SET backedUp = 0 WHERE backedUp = 1")
    suspend fun resetAllBackedUp()

    /** Setzt oder entfernt eine Emoji-Reaktion auf eine Nachricht (per Server-ID). */
    @Query("UPDATE messages SET reaction = :emoji WHERE messageId = :messageId")
    suspend fun setReactionByServerId(messageId: String, emoji: String?): Int

    /** Setzt oder entfernt eine Emoji-Reaktion auf eine Nachricht (per lokaler ID). */
    @Query("UPDATE messages SET reaction = :emoji WHERE localId = :localId")
    suspend fun setReactionByLocalId(localId: Long, emoji: String?)

    /** Alle ausstehenden eigenen Text-Nachrichten (deliveryStatus=0), die erneut gesendet werden sollen. */
    @Query("SELECT * FROM messages WHERE deliveryStatus = 0 AND senderId = :myId AND mediaType = 'text' AND clientMessageId IS NOT NULL")
    suspend fun getPendingOutgoingTextMessages(myId: String): List<MessageEntity>

    /** Neueste Server-Message-ID eines Chats (für inkrementellen Sync via after_id). */
    @Query("SELECT messageId FROM messages WHERE chatId = :chatId AND messageId IS NOT NULL ORDER BY timestamp DESC LIMIT 1")
    suspend fun getNewestServerMessageId(chatId: String): String?

    /** Älteste Server-Message-ID eines Chats (für before_id Paginierung). */
    @Query("SELECT messageId FROM messages WHERE chatId = :chatId AND messageId IS NOT NULL ORDER BY timestamp ASC LIMIT 1")
    suspend fun getOldestServerMessageId(chatId: String): String?

    /** Alle Server-Message-IDs eines Chats (für Sync-Abgleich mit leichtgewichtigem Sync-Endpoint). */
    @Query("SELECT messageId FROM messages WHERE chatId = :chatId AND messageId IS NOT NULL")
    suspend fun getAllServerMessageIds(chatId: String): List<String>

    /** Anzahl lokal gespeicherter Nachrichten für einen Chat. */
    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId")
    suspend fun getLocalMessageCount(chatId: String): Int

    /** Ältester Timestamp eines Chats (für after_timestamp Fallback). */
    @Query("SELECT MIN(timestamp) FROM messages WHERE chatId = :chatId")
    suspend fun getOldestTimestamp(chatId: String): Long?

    /** Nachrichten nach Server-IDs laden (für gezieltes Nachladen fehlender Nachrichten). */
    @Query("SELECT messageId FROM messages WHERE messageId IN (:ids)")
    suspend fun getExistingMessageIds(ids: List<String>): List<String>

    /** Anruf-Verlauf: alle Anruf-System-Nachrichten, neueste zuerst. */
    @Query("SELECT * FROM messages WHERE mediaType IN ('call_initiated', 'call_accepted', 'call_rejected', 'call_ended', 'call_missed') ORDER BY timestamp DESC LIMIT 200")
    fun getCallMessages(): kotlinx.coroutines.flow.Flow<List<MessageEntity>>

    /** Alle Nachrichten mit Medien (Bild, Video, Audio, 3D) chronologisch (älteste zuerst) –
     *  für den globalen Hintergrund-Prefetch über ALLE Chats (1:1 + Gruppen). */
    @Query("SELECT * FROM messages WHERE mediaUrl IS NOT NULL AND mediaUrl != '' AND mediaType IN ('image', 'video', 'audio', 'audio_music', '3dprint') ORDER BY timestamp ASC")
    suspend fun getAllMediaMessages(): List<MessageEntity>

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}

@Dao
interface PollDao {
    @Query("SELECT * FROM polls WHERE pollId = :pollId")
    suspend fun getPollById(pollId: String): PollEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoll(poll: PollEntity)

    @Query("UPDATE polls SET resultsJson = :resultsJson WHERE pollId = :pollId")
    suspend fun updateResults(pollId: String, resultsJson: String)

    @Query("UPDATE polls SET userVote = :optionIndex WHERE pollId = :pollId")
    suspend fun setUserVote(pollId: String, optionIndex: Int)

    @Query("DELETE FROM polls WHERE createdBy = :partnerId OR receiverId = :partnerId")
    suspend fun clearPollsForUser(partnerId: String)

    @Query("DELETE FROM polls")
    suspend fun clearAll()
}

@Dao
interface StatusDao {
    @Query("SELECT * FROM statuses WHERE expiresAt > :now ORDER BY createdAt DESC")
    fun getActiveStatuses(now: Long): Flow<List<StatusEntity>>

    @Query("SELECT * FROM statuses ORDER BY createdAt DESC")
    fun getAllStatuses(): Flow<List<StatusEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatus(status: StatusEntity)

    @Query("SELECT COUNT(*) FROM statuses WHERE userId = :userId AND expiresAt > :now")
    suspend fun hasActiveStatus(userId: String, now: Long): Int

    @Query("DELETE FROM statuses WHERE statusId = :statusId")
    suspend fun deleteStatus(statusId: String)

    @Query("DELETE FROM statuses WHERE expiresAt <= :now")
    suspend fun deleteExpired(now: Long)

    /** Löscht veraltete Kontakt-Statuse die nicht mehr vom Server geliefert werden. */
    @Query("DELETE FROM statuses WHERE statusId NOT IN (:freshIds) AND userId != :myUserId")
    suspend fun deleteContactStatusesNotIn(freshIds: Set<String>, myUserId: String)

    /** Löscht alle Kontakt-Statuse (wenn Server eine leere Liste zurückgibt). */
    @Query("DELETE FROM statuses WHERE userId != :myUserId")
    suspend fun deleteAllContactStatuses(myUserId: String)

    /** Löscht alle Statuse eines bestimmten Nutzers (beim Kontakt löschen). */
    @Query("DELETE FROM statuses WHERE userId = :userId")
    suspend fun deleteStatusesForUser(userId: String)

    @Query("DELETE FROM statuses")
    suspend fun clearAll()
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM `groups`")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Query("SELECT * FROM `groups` WHERE groupId = :groupId LIMIT 1")
    suspend fun getGroupById(groupId: String): GroupEntity?

    @Query("DELETE FROM `groups` WHERE groupId = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("DELETE FROM `groups`")
    suspend fun clearAll()
}

/**
 * DAO für den lokalen Gruppen-Sender-Key-Cache (Gruppen-E2EE).
 * Alle Operationen sind suspend – ausschließlich auf IO-Dispatcher aufrufen.
 */
@Dao
interface GroupSenderKeyDao {

    /** Einzelnen Key abrufen (eigener oder der eines Mitglieds). */
    @Query("SELECT * FROM group_sender_keys WHERE groupId = :groupId AND ownerId = :ownerId LIMIT 1")
    suspend fun getKey(groupId: String, ownerId: String): GroupSenderKeyEntity?

    /** Alle Keys für eine Gruppe abrufen (für Debugging/Übersicht). */
    @Query("SELECT * FROM group_sender_keys WHERE groupId = :groupId")
    suspend fun getKeysForGroup(groupId: String): List<GroupSenderKeyEntity>

    /** Alle Keys aller Gruppen abrufen (für Web-Key-Sync an den Browser). */
    @Query("SELECT * FROM group_sender_keys")
    suspend fun getAllKeys(): List<GroupSenderKeyEntity>

    /** Key einfügen oder ersetzen (bei Key-Rotation). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(key: GroupSenderKeyEntity)

    /** Einzelnen Key löschen. */
    @Query("DELETE FROM group_sender_keys WHERE groupId = :groupId AND ownerId = :ownerId")
    suspend fun deleteKey(groupId: String, ownerId: String)

    /** Alle Keys einer Gruppe löschen (bei Key-Rotation nach Mitglieds-Austritt). */
    @Query("DELETE FROM group_sender_keys WHERE groupId = :groupId")
    suspend fun deleteKeysForGroup(groupId: String)

    /** Alle Keys aller Gruppen löschen (bei Logout). */
    @Query("DELETE FROM group_sender_keys")
    suspend fun clearAll()
}

@Dao
interface MusicMetadataDao {
    @Query("SELECT * FROM music_metadata WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): MusicMetadataEntity?

    @Query("SELECT * FROM music_metadata WHERE url IN (:urls)")
    suspend fun getByUrls(urls: List<String>): List<MusicMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MusicMetadataEntity)

    @Query("DELETE FROM music_metadata WHERE cachedAt < :threshold")
    suspend fun pruneOldEntries(threshold: Long)
}