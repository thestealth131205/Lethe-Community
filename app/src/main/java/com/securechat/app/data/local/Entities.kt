package com.securechat.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Speichert den aktuell eingeloggten Nutzer (uns selbst).
 * Das Feld 'authToken' ist essentiell für alle API-Anrufe nach dem Login.
 */
@Entity(tableName = "user_me")
data class UserEntity(
    @PrimaryKey val userId: String,
    val fakeNumber: String,
    val name: String = "",
    val authToken: String,
    val publicKey: String,
    val isAdmin: Boolean = false,
    val isModerator: Boolean = false,  // Globaler Moderator
    val isCreator: Boolean = false,  // Zugang zum Creator-Dashboard
    val styx: Int = 0,               // Styx-Coin-Guthaben
    val ageVerified: Boolean = false,   // mind. 16 verifiziert (Nearby-Zugang)
    val is18Verified: Boolean = false,  // 18+ verifiziert (direkt oder per Zeitablauf nach 16er-Verif.)
    val privateKey: String? = null, // Nur lokal auf dem Gerät vorhanden
    val profileImageUrl: String? = null,
    val info: String? = null,       // "Über mich"-Text
    val links: String? = null,      // Profil-Link
    val statusPermitted: String? = null,  // JSON: erlaubte Kontakt-IDs für Status-Sichtbarkeit
    val isPhoneVerified: Boolean = false, // False = Telefonnummer noch nicht per OTP bestätigt
    val letheId: String? = null,          // Eindeutige LetheID (LIDXx12340426)
    val instagram: String? = null,
    val tiktok: String? = null,
    val youtube: String? = null,
    val isVerified: Boolean = false   // Bezahlter Verifizierungshaken
)

/**
 * Speichert Kontakte, die bereits bestätigt wurden.
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val userId: String, // Die UUID vom Server
    val fakeNumber: String,
    val username: String? = null,   // Optionaler Anzeigename
    val publicKey: String,          // Erforderlich für Ende-zu-Ende Verschlüsselung
    val profileImageUrl: String? = null,
    val status: String = "accepted", // "pending", "accepted", "rejected"
    val isBot: Boolean = false,       // True für Bot-Konten (is_bot=true auf dem Server)
    val customAlias: String? = null,  // Lokaler Spitzname (überschreibt username in der Anzeige)
    val isAnonymous: Boolean = false, // True = anonymer LetheID-Kontakt (kein Name/Bild/Nummer sichtbar)
    val instagram: String? = null,
    val tiktok: String? = null,
    val youtube: String? = null,
    val info: String? = null,
    val letheId: String? = null,      // Eindeutige LetheID des Kontakts (für anonymen Chat-Flow)
    val isVerified: Boolean = false   // Bezahlter Verifizierungshaken
)

/**
 * Gecachte Status-Beiträge von Kontakten.
 */
@Entity(tableName = "statuses")
data class StatusEntity(
    @PrimaryKey val statusId: String,
    val userId: String,
    val userName: String,
    val userImage: String? = null,
    val mediaUrl: String,
    val mediaType: String,           // "image" oder "video"
    val durationHours: Int,
    val expiresAt: Long,             // Unix-Timestamp in Millisekunden
    val createdAt: Long,
    val musicUrl: String? = null,    // Direkte Abspiel-URL des Songs (für Bild-Status)
    val musicTitle: String? = null,  // Titel des Songs
    val musicArtist: String? = null, // Künstler des Songs
    val musicDurationSec: Int? = null, // Gewählte Musikclip-Dauer in Sekunden (= Anzeige-Dauer)
    val musicOffsetSec: Float? = null, // Start-Offset im Song in Sekunden
    val linkUrl: String? = null,       // Externer Link (z.B. APK-Download), beim Tippen extern geöffnet
    val linkLabel: String? = null      // Optionaler Anzeigetext für den Link
)

/**
 * Cached Poll-Daten für die Offline-Anzeige.
 * results wird als JSON-String gespeichert (Liste von PollOptionResult).
 */
@Entity(tableName = "polls")
data class PollEntity(
    @PrimaryKey val pollId: String,
    val question: String,
    val optionsJson: String,    // JSON: ["Option A", "Option B", ...]
    val resultsJson: String,    // JSON: [{index, text, vote_count, voters:[...]}, ...]
    val createdBy: String,
    val receiverId: String?,
    val userVote: Int = -1,     // -1 = noch nicht abgestimmt
    val allowMultipleChoice: Boolean = false,
    val userVotesJson: String = "[]",  // JSON: [0, 2, ...] bei Mehrfachauswahl
    val createdAt: Long
)

/**
 * Speichert lokale Chat-Nachrichten.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val messageId: String? = null,        // Optionale Server-ID
    val chatId: String,                   // Partner-ID (userId des Kontakts)
    val senderId: String,                 // ID des Absenders
    val receiverId: String,
    val content: String?,                 // Entschlüsselter Klartext
    val mediaUrl: String? = null,
    val mediaType: String = "text",       // "text", "image", "audio", "video", "poll"
    val timestamp: Long,
    val isRead: Boolean = false,          // Legacy – durch deliveryStatus ersetzt
    val isSent: Boolean = false,          // Legacy – durch deliveryStatus ersetzt
    val replyToContent: String? = null,
    val replyToSenderId: String? = null,
    val replyToMediaType: String? = null, // Medientyp der zitierten Nachricht ("image", "video", …)
    val clientMessageId: String? = null,  // Client-seitige UUID für Dedup
    val deliveryStatus: Int = 0,          // 0=PENDING, 1=SENT, 2=DELIVERED, 3=READ
    val isImportant: Boolean = false,     // Vom Nutzer als wichtig markiert (lokal)
    val isEdited: Boolean = false,        // Vom Sender nachträglich bearbeitet
    val isAudioPlayed: Boolean = false,   // Sprachnachricht wurde vollständig abgespielt
    val isDeliveredAsNotification: Boolean = false,  // Benachrichtigung für diese Nachricht wurde gezeigt (Dedup)
    val reaction: String? = null,          // Emoji-Reaktion auf die Nachricht (null = keine)
    val isEncrypted: Boolean = false,      // False = Altlast/Klartext, True = E2EE AES-256-GCM
    val senderDeviceId: String? = null,    // UUID des sendenden Linked-Device (null = primäres Android)
    val replyToMessageId: String? = null,  // Server-ID der zitierten Nachricht (für Scroll-to-Quote)
    val isP2pDelivered: Boolean = false    // Nachricht wurde direkt per WebRTC DataChannel (P2P) übertragen
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val groupId: String,
    val name: String,
    val createdBy: String,
    val memberCount: Int = 0,
    val createdAt: Long,
    val groupImageUrl: String? = null,
    val description: String? = null
)

/**
 * Lokaler Cache für Gruppen-Sender-Keys (Sender-Key-Verfahren für Gruppen-E2EE).
 *
 * Primärschlüssel: (groupId, ownerId).
 *   - ownerId == eigene userId → eigener generierter Key
 *   - ownerId == andere userId → vom Server heruntergeladenes und entschlüsseltes Key-Bundle
 *
 * keyBase64: Base64-kodierte rohe AES-256-Schlüsselbytes (immer 32 Byte nach der Entschlüsselung).
 * Diese Tabelle speichert ausschließlich Klartext-Keys — niemals verschlüsselte Bundles.
 */
@Entity(
    tableName = "group_sender_keys",
    primaryKeys = ["groupId", "ownerId"]
)
data class GroupSenderKeyEntity(
    val groupId: String,
    val ownerId: String,       // Wessen Key (eigener oder den eines Mitglieds)
    val keyBase64: String,     // Base64(rawAES256KeyBytes, 32 Byte)
    val version: Int = 1,
    val storedAt: Long = System.currentTimeMillis()
)

/**
 * Lokaler Cache für MP3-Metadaten (ID3-Tags + Cover-Pfad).
 * Verhindert wiederholtes Netzwerkladen beim Betreten des Chats.
 */
@Entity(tableName = "music_metadata")
data class MusicMetadataEntity(
    @PrimaryKey val url: String,
    val title: String? = null,
    val artist: String? = null,
    val coverPath: String? = null,  // Pfad zur lokal gecachten Cover-JPEG-Datei
    val durationMs: Long = 0L,
    val cachedAt: Long = 0L
)