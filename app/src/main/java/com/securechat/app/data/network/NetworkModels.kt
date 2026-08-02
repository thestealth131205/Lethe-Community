package com.securechat.app.data.network

import com.google.gson.annotations.SerializedName

// --- APP SETTINGS ---

data class AppSettingsResponse(
    @SerializedName("giphy_api_key") val giphyApiKey: String? = null
)

// --- GIPHY ---

data class GiphyFixedImage(
    val url: String,
    val width: String = "",
    val height: String = ""
)

data class GiphyImages(
    @SerializedName("fixed_height") val fixedHeight: GiphyFixedImage,
    @SerializedName("fixed_height_still") val fixedHeightStill: GiphyFixedImage? = null
)

data class GiphyGif(
    val id: String,
    val title: String,
    val images: GiphyImages
) {
    /** Animierte GIF-URL für Anzeige und Versenden */
    val displayUrl: String get() = images.fixedHeight.url
}

data class GiphyResponse(
    val `data`: List<GiphyGif>,
    val pagination: GiphyPagination? = null
)

data class GiphyPagination(
    val count: Int = 0,
    val total_count: Int = 0,
    val offset: Int = 0
)

// --- STICKER ---

data class UserStickerResponse(
    @SerializedName("id") val id: String,
    @SerializedName("url") val url: String,
    @SerializedName("created_at") val createdAt: String = ""
)

data class StickerInfoResponse(
    @SerializedName("creator_username") val creatorUsername: String? = null
)

// --- FCM ---

data class FcmTokenRequest(
    @SerializedName("fcm_token") val fcmToken: String? = null,
    // Push-Transport des Geräts: "fcm" (Play-Store-Build) oder "ws" (FOSS/F-Droid-Build).
    // Null → Server lässt push_kind unverändert (Abwärtskompatibilität).
    @SerializedName("push_kind") val pushKind: String? = null
)

// --- APP UPDATE ---

data class AppVersionResponse(
    val version: String,
    @SerializedName("apk_url") val apkUrl: String,
    val changelog: String? = null
)

data class NetworkInfoResponse(
    @SerializedName("clearnet_url") val clearnetUrl: String,
    @SerializedName("backup_url") val backupUrl: String? = null,
    @SerializedName("onion_address") val onionAddress: String? = null,
    @SerializedName("tor_supported") val torSupported: Boolean = false
)

// --- TURN CREDENTIALS ---

data class TurnCredentialsResponse(
    val username: String,
    val password: String
)

// --- AUTH & USER ---

// Registrierung: Client schickt name, fake_number (= E.164 Telefonnummer), password + optionale ECDH-Daten
data class UserCreateRequest(
    @SerializedName("fake_number") val fakeNumber: String,
    @SerializedName("name") val name: String,
    @SerializedName("password") val password: String,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("ecdh_public_key") val ecdhPublicKey: String? = null,
    @SerializedName("sms_code") val smsCode: String? = null,
    @SerializedName("is_child_account") val isChildAccount: Boolean? = null,
    @SerializedName("birth_date") val birthDate: String? = null,
    @SerializedName("invite_token") val inviteToken: String? = null
)

data class FamilyInviteExistingRequest(
    @SerializedName("phone_number_or_fake") val phoneNumberOrFake: String,
    @SerializedName("username") val username: String
)

data class RegistrationOtpRequest(
    @SerializedName("phone_number") val phoneNumber: String
)

data class UserLoginRequest(
    @SerializedName("fake_number") val fakeNumber: String,
    @SerializedName("password") val password: String,
    @SerializedName("device_fingerprint") val deviceFingerprint: String = ""
)

// Login per Telefonnummer (E.164) + Passwort
data class PhoneLoginRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("password") val password: String,
    @SerializedName("device_fingerprint") val deviceFingerprint: String = ""
)

// ECDH Public Key hochladen nach Schlüsselgenerierung (+ Signing Key für Anti-MITM)
// notify_contacts=true: Server sendet key_rotated WS-Event an alle Kontakte (Gerätewechsel)
data class EcdhKeyUpdateRequest(
    @SerializedName("ecdh_public_key") val ecdhPublicKey: String,
    @SerializedName("signing_public_key") val signingPublicKey: String? = null,
    @SerializedName("notify_contacts") val notifyContacts: Boolean = false
)

// Key-Backup hochladen (PBKDF2+AES-256-GCM verschlüsselter Private Key)
data class KeyBackupUploadRequest(
    @SerializedName("encrypted_blob") val encryptedBlob: String
)

// Key-Backup abrufen (null wenn noch kein Backup existiert)
data class KeyBackupResponse(
    @SerializedName("encrypted_blob") val encryptedBlob: String?
)

// --- UMK (User Master Key) – Multi-Device E2EE ---

data class UmkUploadRequest(
    @SerializedName("encrypted_umk") val encryptedUmk: String
)

data class UmkResponse(
    @SerializedName("encrypted_umk") val encryptedUmk: String?,
    @SerializedName("has_umk") val hasUmk: Boolean = false
)

data class DeviceEnrollRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("device_pub_key") val devicePubKey: String,
    @SerializedName("wrapped_umk") val wrappedUmk: String
)

data class DeviceEnrollResponse(
    @SerializedName("success") val success: Boolean = true,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("message") val message: String = ""
)

data class DeviceKeyResponse(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("wrapped_umk") val wrappedUmk: String,
    @SerializedName("device_pub_key") val devicePubKey: String
)

data class DeviceListItem(
    @SerializedName("id") val id: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("last_used") val lastUsed: String? = null
)

data class DeviceListResponse(
    @SerializedName("devices") val devices: List<DeviceListItem>
)

data class PartnerUmkResponse(
    @SerializedName("partner_id") val partnerId: String,
    @SerializedName("encrypted_umk") val encryptedUmk: String?,
    @SerializedName("has_umk") val hasUmk: Boolean = false
)

// Re-Encryption: v2 → v3 Migration
data class ReencryptItem(
    @SerializedName("message_id") val messageId: String,
    @SerializedName("content_blob") val contentBlob: String
)

data class ReencryptRequest(
    @SerializedName("messages") val messages: List<ReencryptItem>
)

data class ReencryptResponse(
    @SerializedName("updated") val updated: Int,
    @SerializedName("skipped") val skipped: Int
)

// SMS Passwort-Reset anfordern
data class ForgotPasswordRequest(
    @SerializedName("phone_number") val phoneNumber: String
)

// SMS-Code für Passwort-Reset verifizieren
data class VerifyResetCodeRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("code") val code: String
)

// Neues Passwort mit Reset-Token setzen
data class ResetPasswordRequest(
    @SerializedName("reset_token") val resetToken: String,
    @SerializedName("new_password") val newPassword: String
)

// Temporaeres Passwort per SMS anfordern (Altersverifikation)
data class SendPasswordViaSmsRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("age") val age: Int
)

// Lethe Algorithmus v1: Spark-Interaktions-Event
data class SparkInteractionRequest(
    @SerializedName("interaction_type") val interactionType: String,  // VIEW, SKIP, LOOP, LIKE, COMMENT
    @SerializedName("duration_seconds") val durationSeconds: Float? = null,
    @SerializedName("viewer_gender") val viewerGender: String? = null,
    @SerializedName("viewer_age") val viewerAge: Int? = null
)

data class SparkEditRequest(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String
)

// OTP an Telefonnummer senden (Telefon-Verifikation)
data class PhoneOtpRequest(
    @SerializedName("phone_number") val phoneNumber: String
)

// OTP-Code bestätigen → setzt is_phone_verified=True
data class PhoneOtpConfirmRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("code") val code: String
)

data class PhoneVerifiedResponse(
    val success: Boolean,
    val message: String
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("user_id") val userId: String
)

// --- NEUE GERÄT-ERKENNUNG ---

// Server-Antwort beim Login (entweder normaler Token ODER neues Gerät)
data class LoginResponse(
    // Normaler Login
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("token_type") val tokenType: String? = null,
    @SerializedName("user_id") val userId: String? = null,
    // Neues Gerät
    @SerializedName("new_device") val newDevice: Boolean = false,
    @SerializedName("session_token") val sessionToken: String? = null
)

// Anfrage zur Verifikation eines neuen Geräts (SMS-Code eingeben)
data class DeviceVerifyRequest(
    @SerializedName("session_token") val sessionToken: String,
    @SerializedName("sms_code") val smsCode: String,
    @SerializedName("device_fingerprint") val deviceFingerprint: String
)

// Antwort nach erfolgreicher Gerät-Verifikation (JWT + Key-Backup)
data class DeviceVerifyResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("key_backup") val keyBackup: String?
)

// State für neues Gerät – wird im ViewModel gehalten bis SMS-Code eingegeben
data class NewDeviceState(
    val sessionToken: String,
    val fakeNumber: String,
    val password: String  // temporär gehalten um Key-Backup zu entschlüsseln
)

data class UserStatusResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("is_online") val isOnline: Boolean,
    @SerializedName("last_active") val lastActive: String
)

data class RegisterResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("fake_number") val fakeNumber: String,
    val name: String,
    @SerializedName("public_key") val publicKey: String
)

data class UserResponse(
    val id: String,
    @SerializedName("fake_number") val fakeNumber: String,
    val name: String,
    @SerializedName("public_key") val publicKey: String,
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    @SerializedName("is_moderator") val isModerator: Boolean = false,
    @SerializedName("is_creator") val isCreator: Boolean = false,
    @SerializedName("is_bot") val isBot: Boolean = false,
    @SerializedName("profile_image_url") val profileImageUrl: String?,
    val info: String? = null,
    val links: String? = null,
    val instagram: String? = null,
    val tiktok: String? = null,
    val youtube: String? = null,
    val styx: Int = 0,
    @SerializedName("age_verified") val ageVerified: Boolean = false,       // mind. 16 verifiziert
    @SerializedName("is_18_verified") val is18Verified: Boolean = false,    // 18+ (direkt oder per Zeitablauf)
    @SerializedName("approximate_age") val approximateAge: Int? = null,
    @SerializedName("status_permitted") val statusPermitted: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("ecdh_public_key") val ecdhPublicKey: String? = null,
    @SerializedName("is_phone_verified") val isPhoneVerified: Boolean = false,
    @SerializedName("lethe_id") val letheId: String? = null,
    @SerializedName("admin_added") val adminAdded: Boolean = false,
    @SerializedName("is_verified") val isVerified: Boolean = false
)

// Bot-Suche (öffentlich, kein Token nötig)
data class BotPublicResponse(
    val id: String,
    val name: String,
    @SerializedName("fake_number") val fakeNumber: String,
    @SerializedName("is_bot") val isBot: Boolean = true,
    @SerializedName("profile_image_url") val profileImageUrl: String? = null,
    val description: String? = null
)

data class StatusPermittedRequest(
    @SerializedName("permitted_ids") val permittedIds: List<String>?
)

// --- STYX-COINS ---

/**
 * Schritt 1: Nur Signatur-Daten zur Server-Verifikation senden.
 * Noch keine Coins – erst nach Server-OK.
 */
data class VerifyPurchaseRequest(
    @SerializedName("signed_data") val signedData: String,
    @SerializedName("signature")   val signature: String,
    @SerializedName("product_id")  val productId: String
)

/**
 * Schritt 2: Kauf verbuchen. Enthält zusätzlich signed_data + signature
 * damit das Backend Defence-in-Depth nochmals prüfen kann.
 */
data class CoinPurchaseRequest(
    @SerializedName("purchase_token") val purchaseToken: String,
    @SerializedName("product_id")     val productId: String,
    @SerializedName("signed_data")    val signedData: String,
    @SerializedName("signature")      val signature: String
)

data class CoinPurchaseResponse(
    @SerializedName("new_balance") val newBalance: Int,
    val added: Int
)

// --- KONTAKTE ---

data class AddContactRequest(
    @SerializedName("target_fake_number") val targetFakeNumber: String? = null,
    @SerializedName("requester_id") val requesterId: String,
    @SerializedName("target_lethe_id") val targetLetheId: String? = null
)

// Akzeptierter Kontakt mit allen E2EE-Daten (vom GET /contacts Endpoint)
data class ContactListItem(
    @SerializedName("contact_id") val contactId: String,
    @SerializedName("partner_id") val partnerId: String,
    @SerializedName("partner_fake_number") val partnerFakeNumber: String,
    @SerializedName("partner_name") val partnerName: String,
    @SerializedName("partner_public_key") val partnerPublicKey: String,
    @SerializedName("partner_image") val partnerImage: String?,
    @SerializedName("partner_web_public_key") val partnerWebPublicKey: String? = null,
    @SerializedName("partner_is_bot") val partnerIsBot: Boolean = false,
    @SerializedName("is_anonymous") val isAnonymous: Boolean = false,
    @SerializedName("partner_lethe_id") val partnerLetheId: String? = null,
    @SerializedName("partner_instagram") val partnerInstagram: String? = null,
    @SerializedName("partner_tiktok") val partnerTiktok: String? = null,
    @SerializedName("partner_youtube") val partnerYoutube: String? = null,
    @SerializedName("partner_info") val partnerInfo: String? = null,
    @SerializedName("partner_is_verified") val partnerIsVerified: Boolean = false,
    @SerializedName("partner_p2p_enabled") val partnerP2pEnabled: Boolean = false
)

// Request für PUT /users/me/p2p (P2P-Internet ein-/ausschalten)
data class P2pSettingRequest(
    @SerializedName("enabled") val enabled: Boolean
)

data class BotStartResponse(
    @SerializedName("bot_id") val botId: String,
    @SerializedName("bot_name") val botName: String,
    @SerializedName("bot_fake_number") val botFakeNumber: String,
    @SerializedName("bot_public_key") val botPublicKey: String,
    @SerializedName("bot_profile_image_url") val botProfileImageUrl: String? = null,
    @SerializedName("is_bot") val isBot: Boolean = true
)

data class BlockUserRequest(
    @SerializedName("blocker_id") val blockerId: String,
    @SerializedName("blocked_id") val blockedId: String
)

data class UnblockRequest(
    @SerializedName("blocked_id") val blockedId: String
)

data class BlockedUserResponse(
    val id: String,
    @SerializedName("blocked_id") val blockedId: String,
    @SerializedName("blocked_name") val blockedName: String?,
    @SerializedName("blocked_fake_number") val blockedFakeNumber: String?,
    @SerializedName("blocked_image_url") val blockedImageUrl: String?,
    @SerializedName("created_at") val createdAt: String?
)

data class ReportUserRequest(
    @SerializedName("reported_user_id") val reportedUserId: String,
    val reason: String,
    val details: String? = null,
    val context: String? = null
)

data class ChangePasswordRequest(
    @SerializedName("current_password") val currentPassword: String,
    @SerializedName("new_password") val newPassword: String
)

data class UpdateProfileRequest(
    val name: String,
    val info: String? = null,
    val links: String? = null,
    val instagram: String? = null,
    val tiktok: String? = null,
    val youtube: String? = null
)

data class PrivacySettingsRequest(
    @SerializedName("show_online_status") val showOnlineStatus: Boolean,
    @SerializedName("show_read_receipts") val showReadReceipts: Boolean,
    @SerializedName("status_visible") val statusVisible: Boolean? = null
)

// --- STATUS VIEWER ---

data class StatusViewerResponse(
    @SerializedName("viewer_id") val viewerId: String,
    @SerializedName("viewer_name") val viewerName: String,
    @SerializedName("viewer_image") val viewerImage: String?,
    @SerializedName("viewed_at") val viewedAt: String?,
    @SerializedName("liked") val liked: Boolean = false
)

// --- STATUS ---

data class StatusResponse(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("user_name") val userName: String,
    @SerializedName("user_image") val userImage: String?,
    @SerializedName("media_url") val mediaUrl: String,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("duration_hours") val durationHours: Int,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("music_url") val musicUrl: String? = null,
    @SerializedName("music_title") val musicTitle: String? = null,
    @SerializedName("music_artist") val musicArtist: String? = null,
    @SerializedName("music_duration_sec") val musicDurationSec: Int? = null,
    @SerializedName("music_offset_sec") val musicOffsetSec: Float? = null,
    @SerializedName("link_url") val linkUrl: String? = null,
    @SerializedName("link_label") val linkLabel: String? = null
)

// --- ADMIN ---

data class AdminLogsResponse(
    val lines: List<String>
)

data class ServerSettingsResponse(
    @SerializedName("only_fcm") val onlyFcm: Boolean = false
)

data class ServerSettingsUpdate(
    @SerializedName("only_fcm") val onlyFcm: Boolean
)

data class AdminStatusResponse(
    @SerializedName("server") val server: String,
    @SerializedName("turn") val turn: String
)

/** Status eines Backend-Dienstes (für Admin-Statusanzeige). */
enum class ServiceStatus { ONLINE, OFFLINE, ERROR, UNKNOWN }

data class InstanceInfo(
    val id: String,
    val port: Int,
    val status: ServiceStatus,
    val activeConnections: Int,
    val instanceType: String = "primary"
)

data class InstancesResponse(
    val instances: List<InstanceInfoResponse>
)

data class InstanceInfoResponse(
    @SerializedName("id") val id: String,
    @SerializedName("port") val port: Int,
    @SerializedName("online") val online: Boolean,
    @SerializedName("active_connections") val activeConnections: Int,
    @SerializedName("type") val type: String = "primary"
)

data class FailoverOutputResponse(
    @SerializedName("output") val output: String
)

data class ServerCpuInfo(
    @SerializedName("percent") val percent: Double,
    @SerializedName("cores") val cores: Int
)

data class ServerRamInfo(
    @SerializedName("total_mb") val totalMb: Long,
    @SerializedName("used_mb") val usedMb: Long,
    @SerializedName("available_mb") val availableMb: Long,
    @SerializedName("percent") val percent: Double
)

data class ServerDiskInfo(
    @SerializedName("total_gb") val totalGb: Double,
    @SerializedName("used_gb") val usedGb: Double,
    @SerializedName("free_gb") val freeGb: Double,
    @SerializedName("percent") val percent: Double
)

data class AdminServerInfoResponse(
    @SerializedName("cpu") val cpu: ServerCpuInfo,
    @SerializedName("ram") val ram: ServerRamInfo,
    @SerializedName("disk") val disk: ServerDiskInfo,
    @SerializedName("uptime_seconds") val uptimeSeconds: Long
)

data class AdminStatsResponse(
    @SerializedName("total_users") val totalUsers: Int,
    @SerializedName("active_users") val activeUsers: Int
)

data class SmsGatewayStatusResponse(
    @SerializedName("service") val service: String,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("gateway_reachable") val gatewayReachable: Boolean,
    @SerializedName("queue_size") val queueSize: Int,
    @SerializedName("timestamp") val timestamp: String
)

data class ContactResponseAction(
    @SerializedName("contact_entry_id") val contactEntryId: String,
    val action: String
)

data class HandshakeRenewRequest(
    @SerializedName("partner_id") val partnerId: String
)

data class HandshakeRenewRespond(
    @SerializedName("partner_id") val partnerId: String,
    val action: String
)

// --- WebSocket ---

data class MessageAckPayload(
    @SerializedName("client_message_id") val clientMessageId: String,
    @SerializedName("message_id") val messageId: String
)

data class WebSocketMessage(
    val type: String,
    @SerializedName("sender_id") val senderId: String?,
    @SerializedName("receiver_id") val receiverId: String?,
    @SerializedName("message_id") val messageId: String?,
    val payload: Any?
)

data class ChatMessagePayload(
    @SerializedName("content_blob") val contentBlob: String,
    @SerializedName("media_url") val mediaUrl: String?,
    @SerializedName("media_type") val mediaType: String?
)

data class ContactRequestPayload(
    @SerializedName("from_fake_number") val fromFakeNumber: String,
    @SerializedName("from_name") val fromName: String,
    @SerializedName("contact_entry_id") val contactEntryId: String
)

data class ContactAcceptedPayload(
    @SerializedName("partner_id") val partnerId: String,
    @SerializedName("partner_fake_number") val partnerFakeNumber: String,
    @SerializedName("partner_name") val partnerName: String,
    @SerializedName("partner_public_key") val partnerPublicKey: String,
    @SerializedName("partner_image") val partnerImage: String?
)

// --- VERKNÜPFTE GERÄTE (Linked Devices / Web-Client) ---

data class LinkedDevice(
    val id: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("last_active") val lastActive: String?,
    @SerializedName("web_public_key") val webPublicKey: String? = null
)

data class QrInitResponse(
    val token: String,
    @SerializedName("expires_in") val expiresIn: Int = 300
)

data class ScanQrRequest(
    val token: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("web_public_key") val webPublicKey: String? = null,
    @SerializedName("signature") val signature: String? = null,
    @SerializedName("signing_public_key") val signingPublicKey: String? = null
)

// --- NEARBY ---

data class NearbyProfileResponse(
    val id: String,
    val username: String,
    val age: Int,
    val gender: String,
    val height: Int? = null,
    val description: String?,
    @SerializedName("nearby_image_url") val nearbyImageUrl: String?,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("user_id") val userId: String,
    @SerializedName("distance_km") var distanceKm: Double? = null,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("looking_for") val lookingFor: String? = null,
    @SerializedName("has_children") val hasChildren: String? = null,
    @SerializedName("wants_children") val wantsChildren: String? = null,
    @SerializedName("gallery_photo_1") val galleryPhoto1: String? = null,
    @SerializedName("gallery_photo_2") val galleryPhoto2: String? = null,
    @SerializedName("gallery_photo_3") val galleryPhoto3: String? = null
)

data class NearbyProfileCreate(
    @SerializedName("user_id") val userId: String,
    val username: String,
    val description: String?,
    val age: Int,
    val gender: String,
    @SerializedName("looking_for") val lookingFor: String,
    val latitude: Double,
    val longitude: Double,
    val height: Int? = null,
    @SerializedName("is_active") val isActive: Boolean? = null,
    @SerializedName("has_children") val hasChildren: String? = null,
    @SerializedName("wants_children") val wantsChildren: String? = null
)

data class NearbyLikeRequest(
    @SerializedName("target_user_id") val targetUserId: String
)

data class NearbyLikeByUsernameRequest(
    @SerializedName("username") val username: String
)

data class NearbyLikeIncoming(
    val id: String,
    @SerializedName("liker_id") val likerId: String,
    val username: String,
    @SerializedName("nearby_image_url") val nearbyImageUrl: String?,
    val age: Int,
    val gender: String,
    @SerializedName("created_at") val createdAt: String
)

data class NearbyMatch(
    val id: String,
    @SerializedName("partner_id") val partnerId: String,
    @SerializedName("partner_username") val partnerUsername: String,
    @SerializedName("partner_image_url") val partnerImageUrl: String?,
    @SerializedName("last_message") val lastMessage: String?,
    @SerializedName("last_message_at") val lastMessageAt: String?,
    @SerializedName("via_username") val viaUsername: Boolean = false,
    @SerializedName("created_at") val createdAt: String
)

data class NearbyMessage(
    val id: String,
    @SerializedName("match_id") val matchId: String,
    @SerializedName("sender_id") val senderId: String,
    val content: String?,
    @SerializedName("media_url") val mediaUrl: String?,
    @SerializedName("preview_url") val previewUrl: String? = null,
    @SerializedName("media_type") val mediaType: String = "text",
    @SerializedName("view_mode") val viewMode: String = "permanent",
    @SerializedName("view_count") val viewCount: Int = 0,
    @SerializedName("created_at") val createdAt: String
)

data class NearbyRestoreRequest(
    @SerializedName("target_user_id") val targetUserId: String
)

data class NearbyProfileVisitor(
    @SerializedName("visitor_id") val visitorId: String,
    val username: String,
    @SerializedName("nearby_image_url") val nearbyImageUrl: String?,
    @SerializedName("visited_at") val visitedAt: String
)

data class NearbyVisitorsSubscriptionStatus(
    val subscribed: Boolean,
    val month: String
)

data class NearbySendMessageRequest(
    val content: String?,
    @SerializedName("media_url") val mediaUrl: String? = null,
    @SerializedName("preview_url") val previewUrl: String? = null,
    @SerializedName("media_type") val mediaType: String = "text",
    @SerializedName("view_mode") val viewMode: String = "permanent"
)

data class NearbyLocationUpdate(
    val latitude: Double,
    val longitude: Double
)

data class NearbyAnonQuestion(
    val id: String,
    @SerializedName("profile_user_id") val profileUserId: String,
    val question: String,
    val answer: String?,
    @SerializedName("answer_image_url") val answerImageUrl: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("answered_at") val answeredAt: String?,
    @SerializedName("like_count") val likeCount: Int = 0,
    @SerializedName("liked_by_me") val likedByMe: Boolean = false,
    val reactions: Map<String, Int> = emptyMap(),
    @SerializedName("my_reaction") val myReaction: String? = null
)

data class NearbyReactionRequest(val emoji: String)

data class NearbyAnonQuestionCreate(
    val question: String
)

data class NearbyAnonAnswerRequest(
    val answer: String
)

// --- POLLS ---

data class PollCreate(
    val question: String,
    val options: List<String>,
    @SerializedName("receiver_id") val receiverId: String,
    @SerializedName("allow_multiple_choice") val allowMultipleChoice: Boolean = false
)

data class PollOptionResult(
    val index: Int,
    val text: String,
    @SerializedName("vote_count") val voteCount: Int,
    val voters: List<String>
)

data class PollResponse(
    val id: String,
    val question: String,
    val options: List<String>,
    val results: List<PollOptionResult>,
    @SerializedName("created_by") val createdBy: String,
    @SerializedName("receiver_id") val receiverId: String?,
    @SerializedName("allow_multiple_choice") val allowMultipleChoice: Boolean = false,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("user_vote") val userVote: Int?,
    @SerializedName("user_votes") val userVotes: List<Int> = emptyList()
)

data class PollVoteRequest(
    @SerializedName("option_index") val optionIndex: Int
)

// --- NACHRICHTEN STATUS-SYNC ---

data class DeliveryStatusUpdate(
    @SerializedName("message_id") val messageId: String,
    @SerializedName("status") val status: Int  // 2=DELIVERED, 3=READ
)

// --- NACHRICHTEN HISTORIE ---

data class MessageItemResponse(
    val id: String,
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("receiver_id") val receiverId: String,
    @SerializedName("content_blob") val contentBlob: String?,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("media_url") val mediaUrl: String?,
    val timestamp: String?,
    @SerializedName("is_delivered") val isDelivered: Boolean = false,
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("is_delivered_as_notification") val isDeliveredAsNotification: Boolean = false,
    @SerializedName("client_message_id") val clientMessageId: String? = null,
    @SerializedName("reply_to_content") val replyToContent: String? = null,
    @SerializedName("reply_to_sender_id") val replyToSenderId: String? = null,
    @SerializedName("reply_to_media_type") val replyToMediaType: String? = null,
    @SerializedName("reaction") val reaction: String? = null,
    @SerializedName("is_edited") val isEdited: Boolean = false,
    @SerializedName("is_encrypted") val isEncrypted: Boolean = false,
    // Multi-Device E2EE (EPIC 5)
    @SerializedName("web_content_blob") val webContentBlob: String? = null,
    @SerializedName("sender_web_pub") val senderWebPub: String? = null,
    // Multi-Device Fan-Out: UUID des sendenden Linked-Device + Selbst-verschlüsselte Kopie
    @SerializedName("sender_device_id") val senderDeviceId: String? = null,
    @SerializedName("self_content_blob") val selfContentBlob: String? = null
)

// --- SYNC (leichtgewichtiger Nachrichten-Abgleich ohne Blobs) ---

data class SyncMessageItem(
    val id: String,
    val timestamp: String?,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("is_delivered") val isDelivered: Boolean = false,
    @SerializedName("is_read") val isRead: Boolean = false,
    val reaction: String? = null,
    @SerializedName("is_edited") val isEdited: Boolean = false
)

// --- GRUPPEN ---

data class GroupUpdateRequest(
    val name: String,
    val description: String? = null
)

data class AddMembersRequest(
    @SerializedName("member_ids") val memberIds: List<String>
)

data class GroupResponse(
    val id: String,
    val name: String,
    @SerializedName("created_by") val createdBy: String,
    @SerializedName("member_count") val memberCount: Int,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("group_image_url") val groupImageUrl: String? = null,
    val description: String? = null
)

data class GroupCreateRequest(
    val name: String,
    @SerializedName("member_ids") val memberIds: List<String>
)

// --- GRUPPEN E2EE (SENDER-KEY-VERFAHREN) ---

/** Ein verschlüsseltes Sender-Key-Paket für genau einen Empfänger. */
data class SenderKeyBundle(
    @SerializedName("recipient_id") val recipientId: String,
    @SerializedName("encrypted_key_bundle") val encryptedKeyBundle: String,
    val version: Int = 1
)

/** Request-Body: eigene Sender-Key-Pakete für alle Mitglieder hochladen. */
data class DistributeKeysRequest(
    val bundles: List<SenderKeyBundle>,
    val version: Int = 1
)

/** Ein Sender-Key-Paket, das ein anderes Mitglied für uns hochgeladen hat. */
data class MyKeyBundleEntry(
    @SerializedName("owner_id") val ownerId: String,
    @SerializedName("encrypted_key_bundle") val encryptedKeyBundle: String,
    val version: Int = 1
)

/** Response-Body: alle für mich verfügbaren Sender-Key-Pakete. */
data class MyKeyBundleResponse(
    val bundles: List<MyKeyBundleEntry>
)

/** Public Key eines Gruppenmitglieds für die Client-seitige Bundle-Verschlüsselung. */
data class GroupMemberPublicKey(
    @SerializedName("user_id") val userId: String,
    @SerializedName("public_key") val publicKey: String
)

// --- TERMIN-SYSTEM ---

data class RsvpAttendee(
    @SerializedName("user_id") val userId: String,
    val name: String? = null,
    @SerializedName("profile_image_url") val profileImageUrl: String? = null
)

data class GroupMemberInfo(
    @SerializedName("user_id") val userId: String,
    val name: String? = null,
    @SerializedName("fake_number") val fakeNumber: String? = null,
    @SerializedName("profile_image_url") val profileImageUrl: String? = null,
    val role: String = "member"  // 'member' | 'moderator' | 'admin'
)

data class SetMemberRoleRequest(
    val role: String
)

data class AppointmentOptionResponse(
    val id: String,
    @SerializedName("date_time") val dateTime: String,
    @SerializedName("vote_count") val voteCount: Int = 0,
    @SerializedName("voter_ids") val voterIds: List<String> = emptyList(),
    @SerializedName("voter_names") val voterNames: List<String> = emptyList(),
    @SerializedName("voter_images") val voterImages: List<String?> = emptyList(),
    @SerializedName("my_vote") val myVote: Boolean = false
)

data class AppointmentVoteRequest(
    @SerializedName("option_ids") val optionIds: List<String>
)

data class MoveToConfirmationRequest(
    @SerializedName("option_id") val optionId: String
)

data class GroupMediaViewEntry(
    @SerializedName("user_id") val userId: String,
    val name: String?,
    @SerializedName("fake_number") val fakeNumber: String?,
    @SerializedName("profile_image_url") val profileImageUrl: String?,
    @SerializedName("viewed_at") val viewedAt: String?
)

data class GroupMessageReadEntry(
    @SerializedName("user_id") val userId: String,
    val name: String?,
    @SerializedName("fake_number") val fakeNumber: String?,
    @SerializedName("profile_image_url") val profileImageUrl: String?,
    @SerializedName("read_at") val readAt: String?
)

data class GroupAppointmentResponse(
    val id: String,
    @SerializedName("group_id") val groupId: String,
    @SerializedName("created_by") val createdBy: String,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    @SerializedName("proposed_dates") val proposedDates: List<String>? = null,
    @SerializedName("final_date") val finalDate: String? = null,
    val status: String = "finalized",  // 'suggesting' | 'confirming' | 'finalized'
    @SerializedName("created_at") val createdAt: String? = null,
    val options: List<AppointmentOptionResponse> = emptyList(),
    val going: List<RsvpAttendee> = emptyList(),
    val declined: List<RsvpAttendee> = emptyList(),
    @SerializedName("my_status") val myStatus: String? = null
)

data class GroupAppointmentCreateRequest(
    val title: String,
    val description: String? = null,
    val location: String? = null,
    @SerializedName("proposed_dates") val proposedDates: List<String>? = null,
    val options: List<String>? = null  // Neue Voting-Options
)

data class AppointmentFinalizeRequest(
    @SerializedName("final_date") val finalDate: String
)

data class RsvpRequest(
    val status: String  // "going" | "declined"
)

// --- KONTAKTANFRAGEN (AUSSTEHEND) ---

data class PendingContactRequest(
    @SerializedName("contact_entry_id") val contactEntryId: String,
    @SerializedName("from_user_id") val fromUserId: String,
    @SerializedName("from_fake_number") val fromFakeNumber: String,
    @SerializedName("from_name") val fromName: String,
    @SerializedName("created_at") val createdAt: String
)

// --- 3D-DATEIEN ---

data class ThreeDUploadResponse(
    @SerializedName("file_url") val fileUrl: String,
    @SerializedName("preview_url") val previewUrl: String,
    @SerializedName("filename") val filename: String,
    @SerializedName("file_size") val fileSize: Long
)

data class ThreeDFileMeta(
    @SerializedName("file_url") val fileUrl: String,
    @SerializedName("preview_url") val previewUrl: String,
    @SerializedName("filename") val filename: String,
    @SerializedName("file_size") val fileSize: Long,
    @SerializedName("texture_url") val textureUrl: String = "",
    @SerializedName("price") val price: Int = 0
)


// --- EINLADUNGEN ---

data class SetAdminPanelPasswordRequest(
    @SerializedName("password") val password: String
)

data class VerifyAdminPanelPasswordRequest(
    @SerializedName("password") val password: String
)

data class AdminPanelPasswordStatusResponse(
    @SerializedName("is_set") val isSet: Boolean
)

data class InviteGenerateResponse(
    @SerializedName("token") val token: String,
    @SerializedName("invite_url") val inviteUrl: String,
    @SerializedName("creator_name") val creatorName: String? = null,
    @SerializedName("creator_fake_number") val creatorFakeNumber: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class InviteInfoResponse(
    @SerializedName("token") val token: String,
    @SerializedName("creator_fake_number") val creatorFakeNumber: String?,
    @SerializedName("creator_name") val creatorName: String?,
    @SerializedName("is_used") val isUsed: Boolean,
    @SerializedName("created_at") val createdAt: String? = null
)

data class InviteRedeemResponse(
    @SerializedName("status") val status: String,
    @SerializedName("creator_user_id") val creatorUserId: String? = null,
    @SerializedName("creator_fake_number") val creatorFakeNumber: String? = null,
    @SerializedName("creator_name") val creatorName: String? = null
)

// --- PHONE LOOKUP ---

data class PhoneLookupMatch(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("fake_number") val fakeNumber: String,
    val name: String,
    @SerializedName("profile_image_url") val profileImageUrl: String? = null,
    @SerializedName("is_already_contact") val isAlreadyContact: Boolean = false
)

data class PhoneLookupResponse(
    val matches: List<PhoneLookupMatch>
)

// --- CREATOR ---

data class CreatorProfileResponse(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val username: String? = null,
    val bio: String? = null,
    @SerializedName("banner_url") val bannerUrl: String? = null,
    @SerializedName("profile_image_url") val profileImageUrl: String? = null,
    val diamonds: Int = 0,
    @SerializedName("styx_received") val styxReceived: Int = 0,
    @SerializedName("subscription_price") val subscriptionPrice: Int = 100,
    val name: String? = null,
    @SerializedName("fake_number") val fakeNumber: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    // EPIC 1: Livestream-Felder
    @SerializedName("stream_key") val streamKey: String? = null,
    @SerializedName("is_live") val isLive: Boolean = false,
    val likes: Int = 0,
    val abos: Int = 0
)

data class CreatorContentResponse(
    val id: String,
    @SerializedName("creator_id") val creatorId: String,
    @SerializedName("creator_user_id") val creatorUserId: String? = null,
    val title: String? = null,
    val description: String? = null,
    @SerializedName("media_url") val mediaUrl: String? = null,
    @SerializedName("media_type") val mediaType: String = "image",
    @SerializedName("styx_cost") val styxCost: Int = 0,
    @SerializedName("is_public") val isPublic: Boolean = true,
    @SerializedName("content_html") val contentHtml: String? = null,
    val category: String? = null,
    @SerializedName("preview_image_url") val previewImageUrl: String? = null,
    @SerializedName("view_count") val viewCount: Int = 0,
    val slug: String? = null,
    @SerializedName("creator_name") val creatorName: String? = null,
    @SerializedName("creator_username") val creatorUsername: String? = null,
    @SerializedName("creator_fake_number") val creatorFakeNumber: String? = null,
    @SerializedName("creator_profile_image_url") val creatorProfileImageUrl: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    // EPIC 2: True wenn dieser Feed-Eintrag ein aktiver Livestream ist
    @SerializedName("is_live") val isLive: Boolean = false,
    val likes: Int = 0,
    @SerializedName("is_liked_by_me") val isLikedByMe: Boolean = false,
    @SerializedName("is_saved_by_me") val isSavedByMe: Boolean = false,
    val tags: String? = null,
    @SerializedName("creator_banner_url") val creatorBannerUrl: String? = null,
    @SerializedName("is_18plus") val is18plus: Boolean = false,
    @SerializedName("comments_count") val commentsCount: Int = 0,
    // Musik-Metadaten
    @SerializedName("music_title") val musicTitle: String? = null,
    @SerializedName("music_artist") val musicArtist: String? = null,
    @SerializedName("music_cover_url") val musicCoverUrl: String? = null,
    @SerializedName("sound_origin_spark_id") val soundOriginSparkId: String? = null,
    @SerializedName("original_sound") val originalSound: String? = null,
    // Bild-Slideshow (null oder Liste von URLs)
    @SerializedName("spark_image_urls") val sparkImageUrls: List<String>? = null,
    // Referenz zur Music-Tabelle
    @SerializedName("music_id") val musicId: String? = null,
    // Direkte Abspiel-URL des Songs (für image_spark Audiowiedergabe)
    @SerializedName("music_url") val musicUrl: String? = null
)

data class MusicResponse(
    val id: String,
    val artist: String? = null,
    @SerializedName("song_title") val songTitle: String? = null,
    @SerializedName("media_url") val mediaUrl: String,
    @SerializedName("cover_url") val coverUrl: String? = null,
    val length: Int = 0,
    @SerializedName("uploader_id") val uploaderId: String? = null,
    val uses: Int = 0,
    val source: String = "local",
    @SerializedName("audius_id") val audiusId: String? = null,
    val lyrics: String? = null,
    val year: String? = null,
    val producer: String? = null,
    @SerializedName("preview_offset_sec") val previewOffsetSec: Int = 0
)

data class MusicUpdateRequest(
    val artist: String? = null,
    @SerializedName("song_title") val songTitle: String? = null,
    val year: String? = null,
    val lyrics: String? = null,
    val producer: String? = null,
    @SerializedName("preview_offset_sec") val previewOffsetSec: Int? = null
)

data class SaveApiTrackRequest(
    @SerializedName("audius_id") val audiusId: String,
    val title: String? = null,
    val artist: String? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("stream_url") val streamUrl: String,
    val duration: Int = 0
)

// --- Persoenliche Musikbibliothek ---

data class UserMusicSaveRequest(
    @SerializedName("music_url") val musicUrl: String,
    @SerializedName("music_title") val musicTitle: String? = null,
    val artist: String? = null,
    @SerializedName("play_time") val playTime: Int? = null
)

data class UserMusicResponse(
    val id: String,
    @SerializedName("music_url") val musicUrl: String,
    @SerializedName("music_title") val musicTitle: String? = null,
    val artist: String? = null,
    @SerializedName("play_time") val playTime: Int? = null,
    val favorit: Boolean = false,
    @SerializedName("playlist_id") val playlistId: String? = null,
    @SerializedName("playlist_name") val playlistName: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class UserMusicFavoriteRequest(
    val favorit: Boolean
)

data class UserMusicPlaylistRequest(
    @SerializedName("playlist_id") val playlistId: String? = null,
    @SerializedName("playlist_name") val playlistName: String
)

data class PlaylistResponse(
    @SerializedName("playlist_id") val playlistId: String,
    @SerializedName("playlist_name") val playlistName: String,
    @SerializedName("track_count") val trackCount: Int = 0,
    @SerializedName("total_duration_seconds") val totalDurationSeconds: Int? = null
)

data class PublicCreatorProfileResponse(
    @SerializedName("creator_id") val creatorId: String,
    @SerializedName("user_id") val userId: String? = null,
    val username: String? = null,
    val name: String? = null,
    @SerializedName("profile_image_url") val profileImageUrl: String? = null,
    @SerializedName("subscriber_count") val subscriberCount: Int = 0,
    @SerializedName("my_subscriptions_count") val mySubscriptionsCount: Int = 0,
    @SerializedName("total_views") val totalViews: Int = 0,
    @SerializedName("total_likes") val totalLikes: Int = 0,
    @SerializedName("is_live") val isLive: Boolean = false,
    val monetary: Boolean = true
)

data class VipSearchResponse(
    val posts: List<CreatorContentResponse> = emptyList(),
    val sparks: List<CreatorContentResponse> = emptyList(),
    val profiles: List<NearbyProfileResponse> = emptyList()
)

data class CreatorContentCreateRequest(
    val title: String,
    val category: String?,
    val description: String?,
    @SerializedName("content_html") val contentHtml: String?,
    @SerializedName("styx_cost") val styxCost: Int,
    @SerializedName("preview_image_url") val previewImageUrl: String?,
    @SerializedName("media_url") val mediaUrl: String?,
    @SerializedName("media_type") val mediaType: String,
    val slug: String
)

data class CreatorSettingsRequest(
    @SerializedName("subscription_price") val subscriptionPrice: Int?,
    val bio: String?,
    val username: String? = null
)

data class PurchaseResponse(
    val success: Boolean,
    @SerializedName("new_balance") val newBalance: Int,
    val message: String?
)

data class SubscriptionCheckResponse(
    @SerializedName("is_subscribed") val isSubscribed: Boolean
)

// --- ANIMATION STATE ---

data class AnimationStateResponse(
    val animation: String
)

data class AnimationStateRequest(
    val animation: String
)


// --- LIVESTREAM (EPIC 2+3+5) ---

data class LiveStreamKeyResponse(
    @SerializedName("rtmp_url") val rtmpUrl: String,
    @SerializedName("stream_key") val streamKey: String,
    @SerializedName("hls_url") val hlsUrl: String,
    @SerializedName("is_live") val isLive: Boolean
)

/** Öffentliche Live-Info eines Creators für den Zuschauer-Screen. */
data class CreatorLiveInfoResponse(
    @SerializedName("hls_url") val hlsUrl: String?,
    @SerializedName("is_live") val isLive: Boolean = false
)

/** Eine einzelne Nachricht im Live-Chat. Wird nur im RAM gehalten, nicht persistiert. */
data class LiveChatMessage(
    val userId: String,
    val userName: String,
    val text: String,
    val isSystem: Boolean = false,
    val profileImageUrl: String? = null,
    val timestamp: String = ""
)

// --- ALTERSVERIFIKATION ---

data class AgeVerificationResponse(
    val success: Boolean,
    val message: String,
    @SerializedName("approximate_age") val approximateAge: Int? = null
)

// --- SPARK-KOMMENTARE ---

data class SparkCommentResponse(
    @SerializedName("id") val id: String,
    @SerializedName("spark_id") val sparkId: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("text") val text: String,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("likes_count") val likesCount: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("user_name") val userName: String? = null,
    @SerializedName("user_fake_number") val userFakeNumber: String? = null,
    @SerializedName("user_profile_image_url") val userProfileImageUrl: String? = null,
    @SerializedName("is_liked_by_me") val isLikedByMe: Boolean = false
)

data class SparkCommentCreateRequest(
    @SerializedName("text") val text: String,
    @SerializedName("image_url") val imageUrl: String? = null
)

// --- SPARK-STATISTIKEN (Creator-Dashboard) ---

data class SparkStatsItem(
    val id: String,
    val title: String?,
    @SerializedName("view_count") val viewCount: Int = 0,
    @SerializedName("like_count") val likeCount: Int = 0,
    @SerializedName("loop_count") val loopCount: Int = 0,
    @SerializedName("skip_count") val skipCount: Int = 0,
    @SerializedName("avg_duration_seconds") val avgDurationSeconds: Float? = null
)

data class SparkStatsResponse(
    val sparks: List<SparkStatsItem>,
    @SerializedName("total_views") val totalViews: Int = 0,
    @SerializedName("total_likes") val totalLikes: Int = 0,
    @SerializedName("total_subscribers") val totalSubscribers: Int = 0,
    @SerializedName("avg_watch_time") val avgWatchTime: Float? = null
)

/** Öffentliche Vorschau eines Content-/Spark-Beitrags (kein Auth erforderlich). */
data class PublicContentPreview(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    @SerializedName("preview_image_url") val previewImageUrl: String? = null,
    @SerializedName("media_type") val mediaType: String = "spark",
    @SerializedName("creator_id") val creatorId: String = "",
    @SerializedName("creator_name") val creatorName: String? = null,
    @SerializedName("creator_username") val creatorUsername: String? = null,
)

data class ListenTogetherTrackInfo(
    val url: String,
    val filename: String,
)

data class ListenTogetherTracksResponse(
    val tracks: List<ListenTogetherTrackInfo>,
)

data class SavedPlaylistTrack(
    val url: String,
    val title: String,
    val artist: String,
    val cover: String? = null,
    val duration: Long = 0L,
)

data class SavePlaylistRequest(
    val tracks: List<SavedPlaylistTrack>,
)

data class GetPlaylistResponse(
    val tracks: List<SavedPlaylistTrack>,
)

data class ListenTogetherSessionResponse(
    val active: Boolean,
    @com.google.gson.annotations.SerializedName("current_track_url")   val currentTrackUrl:   String? = null,
    @com.google.gson.annotations.SerializedName("current_track_title") val currentTrackTitle: String? = null,
    @com.google.gson.annotations.SerializedName("position_ms")         val positionMs:        Long    = 0L,
    @com.google.gson.annotations.SerializedName("is_playing")          val isPlaying:         Boolean = false,
    @com.google.gson.annotations.SerializedName("updated_at")          val updatedAt:         String? = null,
)


// --- GAMING ---

data class SaveGameSessionRequest(
    val level: Int,
    val coins: Int,
    @SerializedName("duration_seconds") val durationSeconds: Int,
    val result: String,                                          // "win" | "lose"
    @SerializedName("game_type") val gameType: String,          // "jump_run" | "activity"
    @SerializedName("opponent_id") val opponentId: String? = null,
    @SerializedName("opponent_username") val opponentUsername: String? = null
)

data class GameSessionResponse(
    val ok: Boolean,
    @SerializedName("total_coins") val totalCoins: Int,
    @SerializedName("highest_level") val highestLevel: Int
)

data class GameRankingEntry(
    val username: String,
    @SerializedName("total_coins") val totalCoins: Int,
    @SerializedName("total_play_duration") val totalPlayDuration: Int = 0,
    val rank: Int
)

data class GameMonthWinner(
    val rank: Int,
    val username: String,
    @SerializedName("total_coins") val totalCoins: Int
)

data class GameRankingResponse(
    val entries: List<GameRankingEntry>,
    @SerializedName("reset_at") val resetAt: String,
    @SerializedName("last_month_top3") val lastMonthTop3: List<GameMonthWinner>
)

data class MyGamingStatsResponse(
    @SerializedName("highest_level") val highestLevel: Int,
    @SerializedName("total_coins") val totalCoins: Int,
    @SerializedName("total_play_duration") val totalPlayDuration: Int,
    @SerializedName("games_played") val gamesPlayed: Int
)

// --- JOD LEADERBOARD ---

data class JodLeaderboardEntry(
    val rank: Int,
    val username: String,
    @SerializedName("best_score") val bestScore: Int
)

data class JodLeaderboardResponse(
    @SerializedName("current_month") val currentMonth: List<JodLeaderboardEntry>,
    @SerializedName("prev_month") val prevMonth: List<JodLeaderboardEntry>
)

data class JodScoreResponse(
    val ok: Boolean,
    @SerializedName("new_best") val newBest: Boolean,
    @SerializedName("best_score") val bestScore: Int
)

data class JodStatusResponse(
    @SerializedName("continuous_play") val continuousPlay: Int,
    @SerializedName("hole_available") val holeAvailable: Boolean
)

data class JodContinuousPlayResponse(
    val ok: Boolean,
    @SerializedName("continuous_play") val continuousPlay: Int
)

// --- PINBALL LEADERBOARD ---

data class PinballLeaderboardEntry(
    val rank: Int,
    val username: String,
    @SerializedName("best_score") val bestScore: Int
)

data class PinballLeaderboardResponse(
    @SerializedName("current_month") val currentMonth: List<PinballLeaderboardEntry>,
    @SerializedName("prev_month") val prevMonth: List<PinballLeaderboardEntry>
)

data class PinballScoreResponse(
    val ok: Boolean,
    @SerializedName("new_best") val newBest: Boolean,
    @SerializedName("best_score") val bestScore: Int
)

data class TttRewardResponse(
    val ok: Boolean,
    @SerializedName("new_coins") val newCoins: Int
)

data class TttRewardRequest(
    val result: String,                                          // "win" | "lose"
    @SerializedName("opponent_id") val opponentId: String? = null,
    @SerializedName("opponent_username") val opponentUsername: String? = null
)

data class SknChRewardRequest(
    val score: Int,
    val result: String,                                          // "win" | "lose"
    @SerializedName("opponent_username") val opponentUsername: String? = null
)

data class GamingHistoryEntry(
    val id: String,
    @SerializedName("game_type") val gameType: String,
    val result: String,
    @SerializedName("coins_earned") val coinsEarned: Int,
    @SerializedName("opponent_username") val opponentUsername: String?,
    val level: Int?,
    @SerializedName("duration_seconds") val durationSeconds: Int?,
    val score: Int?,
    @SerializedName("period_year") val periodYear: Int,
    @SerializedName("period_month") val periodMonth: Int,
    @SerializedName("created_at") val createdAt: String?
)

data class CoinsExchangeResponse(
    val ok: Boolean,
    @SerializedName("new_coins") val newCoins: Int,
    @SerializedName("new_styx") val newStyx: Int
)

data class GameProfileStats(
    @SerializedName("jod_high_score") val jodHighScore: Int,
    @SerializedName("most_played_game") val mostPlayedGame: String?,
    @SerializedName("most_played_game_count") val mostPlayedGameCount: Int,
    @SerializedName("most_played_game_high_score") val mostPlayedGameHighScore: Int
)

// --- SKETCH N CHECK ---

data class SknChPlayerEntry(
    @SerializedName("userId") val userId: String,
    val username: String
)

data class SknChGameEntry(
    @SerializedName("game_id") val gameId: String,
    @SerializedName("creator_id") val creatorId: String = "",
    @SerializedName("creator_username") val creatorUsername: String,
    @SerializedName("countdown_seconds") val countdownSeconds: Int,
    @SerializedName("players_required") val playersRequired: Int,
    @SerializedName("players_joined") val playersJoined: Int,
    val players: List<SknChPlayerEntry> = emptyList(),
    val status: String = "waiting",
    @SerializedName("spectators_count") val spectatorsCount: Int = 0
)

data class SknChCreateGameRequest(
    @SerializedName("countdown_seconds") val countdownSeconds: Int,
    @SerializedName("players_required") val playersRequired: Int
)

data class SknChUpdateSettingsRequest(
    @SerializedName("countdown_seconds") val countdownSeconds: Int,
    @SerializedName("players_required") val playersRequired: Int
)



data class AdminSupportTicket(
    val id: String,
    @SerializedName("user_id") val userId: String?,
    @SerializedName("user_fake_number") val userFakeNumber: String?,
    @SerializedName("user_name") val userName: String?,
    @SerializedName("user_avatar") val userAvatar: String?,
    @SerializedName("phone_number") val phoneNumber: String?,
    val category: String,
    val title: String,
    val description: String,
    @SerializedName("image_urls") val imageUrls: List<String> = emptyList(),
    val status: String,
    @SerializedName("admin_reply") val adminReply: String?,
    @SerializedName("replied_at") val repliedAt: String?,
    @SerializedName("created_at") val createdAt: String?
)

data class AdminSupportReplyRequest(
    val reply: String,
    val status: String = "closed"
)

data class AdminUserReport(
    val id: String,
    val reason: String,
    val details: String?,
    val context: String?,
    val status: String,
    val timestamp: String?,
    @SerializedName("reporter_id") val reporterId: String,
    @SerializedName("reporter_fake_number") val reporterFakeNumber: String?,
    @SerializedName("reporter_name") val reporterName: String?,
    @SerializedName("reporter_avatar") val reporterAvatar: String?,
    @SerializedName("reported_user_id") val reportedUserId: String,
    @SerializedName("reported_fake_number") val reportedFakeNumber: String?,
    @SerializedName("reported_name") val reportedName: String?,
    @SerializedName("reported_avatar") val reportedAvatar: String?
)

data class UserSupportTicket(
    val id: String,
    val category: String,
    val title: String,
    val description: String,
    @SerializedName("image_urls") val imageUrls: List<String> = emptyList(),
    val status: String,
    @SerializedName("admin_reply") val adminReply: String?,
    @SerializedName("replied_at") val repliedAt: String?,
    @SerializedName("created_at") val createdAt: String?
)

// --- CREATOR ARTICLES ---

data class CreateArticleRequest(
    val title: String,
    val description: String,
    @SerializedName("price_styx") val price_styx: Double,
    val category: String?,
    val license: String?,
    @SerializedName("is_music") val is_music: Boolean,
    @SerializedName("is_3d_model") val is_3d_model: Boolean,
    @SerializedName("is_18plus") val is_18plus: Boolean
)

data class ArticleResponse(
    val id: Int,
    val title: String,
    @SerializedName("price_styx") val price_styx: Double,
    val status: String,
    @SerializedName("is_18plus") val is_18plus: Boolean
)

// ─────────────────────────────────────────────────────────────────────────────
// LETHE FAMILY – DTOs
// ─────────────────────────────────────────────────────────────────────────────

/** Berechtigungen eines Kind-Accounts (Server → App). */
data class ChildPermissionsResponse(
    @SerializedName("relation_id") val relationId: String,
    @SerializedName("child_id") val childId: String,
    @SerializedName("can_video_call") val canVideoCall: Boolean = true,
    @SerializedName("can_audio_call") val canAudioCall: Boolean = true,
    @SerializedName("can_send_media") val canSendMedia: Boolean = true,
    @SerializedName("can_view_sparks") val canViewSparks: Boolean = true,
    @SerializedName("can_use_nearby") val canUseNearby: Boolean = false,
    @SerializedName("can_play_games") val canPlayGames: Boolean = true,
    @SerializedName("allowed_game_minutes_per_day") val allowedGameMinutesPerDay: Int = 0,
    @SerializedName("game_time_start") val gameTimeStart: String? = null,
    @SerializedName("game_time_end") val gameTimeEnd: String? = null,
    @SerializedName("contact_whitelist") val contactWhitelist: String? = null,
    @SerializedName("contact_blocked_ids") val contactBlockedIds: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("is_vip") val isVip: Boolean = false
)

/** Ein Kontakt des Kind-Accounts (für Eltern-Dashboard). */
data class ChildContactEntry(
    @SerializedName("partner_id") val partnerId: String,
    @SerializedName("partner_name") val partnerName: String,
    @SerializedName("partner_fake_number") val partnerFakeNumber: String,
    @SerializedName("partner_image") val partnerImage: String? = null
)

/** Partielle Aktualisierung der Berechtigungen durch den Parent (App → Server). */
data class ChildPermissionsUpdateRequest(
    @SerializedName("can_video_call") val canVideoCall: Boolean? = null,
    @SerializedName("can_audio_call") val canAudioCall: Boolean? = null,
    @SerializedName("can_send_media") val canSendMedia: Boolean? = null,
    @SerializedName("can_view_sparks") val canViewSparks: Boolean? = null,
    @SerializedName("can_use_nearby") val canUseNearby: Boolean? = null,
    @SerializedName("can_play_games") val canPlayGames: Boolean? = null,
    @SerializedName("allowed_game_minutes_per_day") val allowedGameMinutesPerDay: Int? = null,
    @SerializedName("game_time_start") val gameTimeStart: String? = null,
    @SerializedName("game_time_end") val gameTimeEnd: String? = null,
    @SerializedName("contact_whitelist") val contactWhitelist: List<String>? = null,
    @SerializedName("contact_blocked_ids") val contactBlockedIds: List<String>? = null,
    @SerializedName("is_vip") val isVip: Boolean? = null
)

/** Einladung zum Lethe-Family-Verbund erstellen (Parent → Server). */
data class FamilyInviteResponse(
    @SerializedName("relation_id") val relationId: String,
    @SerializedName("invite_token") val inviteToken: String
)

/** Einladung akzeptieren (Kind-Account → Server). */
data class FamilyAcceptInviteRequest(
    @SerializedName("invite_token") val inviteToken: String
)

/** Kompakter Eintrag über ein verknüpftes Kind (in FamilyStatusResponse.children). */
data class FamilyChildEntry(
    @SerializedName("child_id") val childId: String,
    @SerializedName("child_name") val childName: String,
    @SerializedName("child_fake_number") val childFakeNumber: String,
    @SerializedName("child_image") val childImage: String?,
    @SerializedName("relation_id") val relationId: String
)

/** Kompakter Eintrag über den übergeordneten Parent (in FamilyStatusResponse.parent). */
data class FamilyParentEntry(
    @SerializedName("parent_id") val parentId: String,
    @SerializedName("parent_name") val parentName: String,
    @SerializedName("parent_fake_number") val parentFakeNumber: String,
    @SerializedName("parent_image") val parentImage: String?,
    @SerializedName("relation_id") val relationId: String
)

/** Ausstehende Einladung (noch kein Kind hat akzeptiert). */
data class FamilyPendingInvite(
    @SerializedName("relation_id") val relationId: String,
    @SerializedName("invite_token") val inviteToken: String,
    @SerializedName("created_at") val createdAt: String?
)

/** Vollständiger Family-Status: GET /family/my-status. */
data class FamilyStatusResponse(
    @SerializedName("is_child_account") val isChildAccount: Boolean = false,
    @SerializedName("is_parent") val isParent: Boolean = false,
    val parent: FamilyParentEntry? = null,
    val children: List<FamilyChildEntry> = emptyList(),
    @SerializedName("pending_invites") val pendingInvites: List<FamilyPendingInvite> = emptyList()
)

/** PIN setzen (Parent → Server). */
data class FamilySetPinRequest(
    val pin: String
)

/** PIN verifizieren (Kind-App → Server). */
data class FamilyPinVerifyRequest(
    val pin: String
)

data class ArticleFileResponse(
    val id: Int,
    @SerializedName("file_path") val file_path: String,
    @SerializedName("file_type") val file_type: String
)

// --- CREATOR BEWERBUNG ---

data class CreatorApplicationRequest(
    @SerializedName("bio") val bio: String,
    @SerializedName("age") val age: Int,
    @SerializedName("email") val email: String,
    @SerializedName("references") val references: String?,
    @SerializedName("content_types") val contentTypes: List<String>
)

data class CreatorApplicationResponse(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("bio") val bio: String,
    @SerializedName("age") val age: Int,
    @SerializedName("email") val email: String,
    @SerializedName("references") val references: String? = null,
    @SerializedName("content_types") val contentTypes: String,
    @SerializedName("status") val status: String,
    @SerializedName("admin_note") val adminNote: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("user_name") val userName: String? = null,
    @SerializedName("user_fake_number") val userFakeNumber: String? = null,
    @SerializedName("user_profile_image") val userProfileImage: String? = null
)

data class AdminCreatorApplicationAction(
    @SerializedName("action") val action: String,
    @SerializedName("admin_note") val adminNote: String? = null
)

// --- LETHE TEAM ANNOUNCE ---

data class AdminAnnounceRequest(
    @SerializedName("target_type") val targetType: String,
    @SerializedName("target_user_id") val targetUserId: String? = null,
    @SerializedName("content") val content: String,
    @SerializedName("is_notification") val isNotification: Boolean = false,
    @SerializedName("via_sms") val viaSms: Boolean = false
)

data class AdminAnnounceResponse(
    @SerializedName("sent_to") val sentTo: Int,
    @SerializedName("target_type") val targetType: String
)

// --- ADMIN USER CREATE / EDIT ---

data class AdminCreateUserRequest(
    @SerializedName("fake_number") val fakeNumber: String,
    @SerializedName("name") val name: String,
    @SerializedName("password") val password: String,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    @SerializedName("is_moderator") val isModerator: Boolean = false,
    @SerializedName("is_creator") val isCreator: Boolean = false
)

data class AdminUpdateUserRequest(
    @SerializedName("name") val name: String? = null,
    @SerializedName("fake_number") val fakeNumber: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("password") val password: String? = null,
    @SerializedName("is_admin") val isAdmin: Boolean? = null,
    @SerializedName("is_moderator") val isModerator: Boolean? = null,
    @SerializedName("is_creator") val isCreator: Boolean? = null,
    @SerializedName("is_blocked") val isBlocked: Boolean? = null,
    @SerializedName("age_verified") val ageVerified: Boolean? = null,
    @SerializedName("info") val info: String? = null,
    @SerializedName("styx") val styx: Int? = null,
    @SerializedName("instagram") val instagram: String? = null,
    @SerializedName("tiktok") val tiktok: String? = null,
    @SerializedName("youtube") val youtube: String? = null,
    @SerializedName("show_online_status") val showOnlineStatus: Boolean? = null,
    @SerializedName("show_read_receipts") val showReadReceipts: Boolean? = null,
    @SerializedName("status_visible") val statusVisible: Boolean? = null,
    @SerializedName("is_verified") val isVerified: Boolean? = null
)

data class AdminUserDetails(
    val id: String,
    val fakeNumber: String,
    val name: String,
    val phoneNumber: String,
    val isAdmin: Boolean,
    val isModerator: Boolean,
    val isCreator: Boolean,
    val isBlocked: Boolean,
    val ageVerified: Boolean,
    val isPhoneVerified: Boolean,
    val isVerified: Boolean,
    val info: String,
    val styx: Int,
    val instagram: String,
    val tiktok: String,
    val youtube: String,
    val showOnlineStatus: Boolean,
    val showReadReceipts: Boolean,
    val statusVisible: Boolean,
    val profileImageUrl: String,
    val letheId: String,
    val createdAt: String?
)

// ── Zeitgeplante Nachrichten ─────────────────────────────────────────────────

data class ScheduleMessageRequest(
    @SerializedName("receiver_id") val receiverId: String,
    @SerializedName("content_blob") val contentBlob: String,
    @SerializedName("scheduled_at") val scheduledAt: String,
    @SerializedName("self_content_blob") val selfContentBlob: String? = null,
    @SerializedName("web_content_blob") val webContentBlob: String? = null,
    @SerializedName("sender_web_pub") val senderWebPub: String? = null,
    @SerializedName("sender_device_id") val senderDeviceId: String? = null,
    @SerializedName("client_message_id") val clientMessageId: String? = null,
    @SerializedName("media_type") val mediaType: String = "text",
    @SerializedName("reply_to_content") val replyToContent: String? = null,
    @SerializedName("reply_to_sender_id") val replyToSenderId: String? = null,
    @SerializedName("reply_to_media_type") val replyToMediaType: String? = null,
    @SerializedName("reply_to_message_id") val replyToMessageId: String? = null,
)

data class ScheduleMessageResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("id") val id: String,
    @SerializedName("scheduled_at") val scheduledAt: String,
)

data class ScheduledMessageItem(
    @SerializedName("id") val id: String,
    @SerializedName("receiver_id") val receiverId: String,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("scheduled_at") val scheduledAt: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("client_message_id") val clientMessageId: String?,
)

// --- STRIPE ---

data class StripePaymentIntentResponse(
    @SerializedName("client_secret") val clientSecret: String,
    @SerializedName("publishable_key") val publishableKey: String
)

// --- CREATOR PAYOUT ---

data class CreatorPayoutRequest(
    val iban: String,
    val name: String,
    val email: String
)

data class CreatorPayoutResponse(
    val success: Boolean,
    val message: String,
    @SerializedName("diamonds_deducted") val diamondsDeducted: Int = 0,
    @SerializedName("euros_payout") val eurosPayout: Double = 0.0
)

// --- DIAMOND RATE ---

data class DiamondRateResponse(
    @SerializedName("diamond_to_euro_rate") val diamondToEuroRate: Double
)

data class DiamondRateRequest(
    @SerializedName("diamond_to_euro_rate") val diamondToEuroRate: Double
)

data class StyxToDiamondsRequest(
    val amount: Int
)

data class StyxToDiamondsResponse(
    val success: Boolean,
    val message: String,
    @SerializedName("styx_converted") val styxConverted: Int = 0,
    @SerializedName("diamonds_added") val diamondsAdded: Int = 0,
    @SerializedName("new_styx_received") val newStyxReceived: Int = 0,
    @SerializedName("new_diamonds") val newDiamonds: Int = 0
)

data class StyxTransferRequest(
    val amount: Int,
    val direction: String
)

data class StyxTransferResponse(
    val success: Boolean,
    val message: String,
    @SerializedName("new_user_styx") val newUserStyx: Int = 0,
    @SerializedName("new_creator_styx") val newCreatorStyx: Int = 0
)

// --- VIP DISKUSSIONEN ---

data class VipDiscussionResponse(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("content") val content: String,
    @SerializedName("category") val category: String?,
    @SerializedName("author_id") val authorId: String,
    @SerializedName("author_name") val authorName: String?,
    @SerializedName("author_fake_number") val authorFakeNumber: String?,
    @SerializedName("author_profile_image_url") val authorProfileImageUrl: String?,
    @SerializedName("replies_count") val repliesCount: Int = 0,
    @SerializedName("likes_count") val likesCount: Int = 0,
    @SerializedName("is_liked_by_me") val isLikedByMe: Boolean = false,
    @SerializedName("created_at") val createdAt: String?
)

data class VipDiscussionCreate(
    @SerializedName("title") val title: String,
    @SerializedName("content") val content: String,
    @SerializedName("category") val category: String?
)

data class VipDiscussionReplyResponse(
    @SerializedName("id") val id: String,
    @SerializedName("discussion_id") val discussionId: String,
    @SerializedName("author_id") val authorId: String,
    @SerializedName("author_name") val authorName: String?,
    @SerializedName("author_fake_number") val authorFakeNumber: String?,
    @SerializedName("author_profile_image_url") val authorProfileImageUrl: String?,
    @SerializedName("content") val content: String,
    @SerializedName("likes_count") val likesCount: Int = 0,
    @SerializedName("is_liked_by_me") val isLikedByMe: Boolean = false,
    @SerializedName("created_at") val createdAt: String?
)

data class VipDiscussionReplyCreate(
    @SerializedName("content") val content: String
)

// --- VIP KATEGORIE-SYSTEM (Honeycomb) ---

data class VipCategoryResponse(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("parent_id") val parentId: String?,
    @SerializedName("icon_key") val iconKey: String?,
    @SerializedName("is_root") val isRoot: Boolean = true,
    @SerializedName("display_order") val displayOrder: Int = 0,
    @SerializedName("thread_count") val threadCount: Int = 0,
    @SerializedName("children_count") val childrenCount: Int = 0,
    @SerializedName("total_thread_count") val totalThreadCount: Int = 0
)

data class VipThreadResponse(
    @SerializedName("id") val id: String,
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("title") val title: String,
    @SerializedName("author_id") val authorId: String,
    @SerializedName("author_name") val authorName: String?,
    @SerializedName("author_fake_number") val authorFakeNumber: String?,
    @SerializedName("author_profile_image_url") val authorProfileImageUrl: String?,
    @SerializedName("heat_level") val heatLevel: Float = 0f,
    @SerializedName("mentioned_thread_ids") val mentionedThreadIds: String?,
    @SerializedName("location_lat") val locationLat: Double?,
    @SerializedName("location_lng") val locationLng: Double?,
    @SerializedName("replies_count") val repliesCount: Int = 0,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("last_content_snippet") val lastContentSnippet: String? = null,
    @SerializedName("last_reply_at") val lastReplyAt: String? = null,
    @SerializedName("first_image_url") val firstImageUrl: String? = null
)

data class VipThreadCreate(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("title") val title: String,
    @SerializedName("location_lat") val locationLat: Double? = null,
    @SerializedName("location_lng") val locationLng: Double? = null
)

data class VipThreadMessageResponse(
    @SerializedName("id") val id: String,
    @SerializedName("thread_id") val threadId: String,
    @SerializedName("author_id") val authorId: String,
    @SerializedName("author_name") val authorName: String?,
    @SerializedName("author_fake_number") val authorFakeNumber: String?,
    @SerializedName("author_profile_image_url") val authorProfileImageUrl: String?,
    @SerializedName("author_posts_count") val authorPostsCount: Int = 0,
    @SerializedName("author_threads_count") val authorThreadsCount: Int = 0,
    @SerializedName("author_likes_count") val authorLikesCount: Int = 0,
    @SerializedName("author_is_verified") val authorIsVerified: Boolean = false,
    @SerializedName("content") val content: String,
    @SerializedName("media_url") val mediaUrl: String?,
    @SerializedName("media_type") val mediaType: String = "text",
    @SerializedName("likes_count") val likesCount: Int = 0,
    @SerializedName("is_liked_by_me") val isLikedByMe: Boolean = false,
    @SerializedName("created_at") val createdAt: String?
)

data class VipThreadUpdate(
    @SerializedName("title") val title: String
)

data class VipThreadMessageCreate(
    @SerializedName("content") val content: String,
    @SerializedName("media_url") val mediaUrl: String? = null,
    @SerializedName("media_type") val mediaType: String = "text"
)

data class VipUserStatsResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("posts_count") val postsCount: Int = 0,
    @SerializedName("threads_count") val threadsCount: Int = 0,
    @SerializedName("likes_count") val likesCount: Int = 0
)

// --- SERVERSEITIGE ANRUF-AUFZEICHNUNG ---

data class RecordingStartRequest(
    @SerializedName("partner_id") val partnerId: String,
    @SerializedName("call_type") val callType: String = "video",
    @SerializedName("allow_partner_download") val allowPartnerDownload: Boolean = false
)

data class RecordingStartResponse(
    @SerializedName("recording_id") val recordingId: String,
    @SerializedName("self_path") val selfPath: String,
    @SerializedName("partner_path") val partnerPath: String,
    @SerializedName("whip_base") val whipBase: String
)

data class RecordingStopRequest(
    @SerializedName("recording_id") val recordingId: String,
    @SerializedName("duration_seconds") val durationSeconds: Int = 0
)

data class CallRecordingResponse(
    val id: String,
    @SerializedName("partner_id") val partnerId: String,
    @SerializedName("call_type") val callType: String,
    val status: String,
    @SerializedName("duration_seconds") val durationSeconds: Int,
    @SerializedName("started_at") val startedAt: String,
    @SerializedName("has_file") val hasFile: Boolean,
    @SerializedName("has_self_file") val hasSelfFile: Boolean = false,
    @SerializedName("has_partner_file") val hasPartnerFile: Boolean = false,
    @SerializedName("has_combined_file") val hasCombinedFile: Boolean = false
)
