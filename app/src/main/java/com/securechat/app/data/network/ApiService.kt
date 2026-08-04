package com.securechat.app.data.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Die zentrale Schnittstelle für die Kommunikation mit dem FastAPI-Backend.
 * Alle Endpunkte wurden an die aktuelle server/main.py angepasst.
 */
interface ApiService {

    // --- APP-UPDATE ---

    @GET("app/version")
    suspend fun getAppVersion(): Response<AppVersionResponse>

    @GET("network-info")
    suspend fun getNetworkInfo(): NetworkInfoResponse

    // --- APP SETTINGS ---

    @GET("settings/app")
    suspend fun getAppSettings(): Response<AppSettingsResponse>

    // --- AUTHENTIFIZIERUNG & NUTZERVERWALTUNG ---

    @POST("register")
    suspend fun register(@Body request: UserCreateRequest): Response<RegisterResponse>

    @POST("login")
    suspend fun login(@Body request: UserLoginRequest): Response<LoginResponse>

    // Login per Telefonnummer
    @POST("login/phone")
    suspend fun loginWithPhone(@Body request: PhoneLoginRequest): Response<LoginResponse>

    // Neues Gerät via SMS-Code verifizieren
    @POST("login/verify-device")
    suspend fun verifyDevice(@Body request: DeviceVerifyRequest): Response<DeviceVerifyResponse>

    // ECDH Public Key hochladen
    @PUT("users/me/ecdh-key")
    suspend fun updateEcdhKey(@Body request: EcdhKeyUpdateRequest): Response<Map<String, String>>

    // Key-Backup hochladen (verschlüsselter Private Key)
    @PUT("users/me/key-backup")
    suspend fun uploadKeyBackup(@Body request: KeyBackupUploadRequest): Response<Map<String, String>>

    // Key-Backup abrufen
    @GET("users/me/key-backup")
    suspend fun getKeyBackup(): Response<KeyBackupResponse>

    // --- UMK (User Master Key) – Multi-Device E2EE ---

    // UMK hochladen (mit Passwort verschlüsselt)
    @PUT("keys/umk")
    suspend fun uploadUmk(@Body request: UmkUploadRequest): Response<UmkResponse>

    // UMK abrufen
    @GET("keys/umk")
    suspend fun getUmk(): Response<UmkResponse>

    // Gerät registrieren (Device Enrollment)
    @POST("keys/enroll-device")
    suspend fun enrollDevice(@Body request: DeviceEnrollRequest): Response<DeviceEnrollResponse>

    // Gewrappten UMK für ein Gerät abrufen
    @GET("keys/wrapped-umk/{device_id}")
    suspend fun getWrappedUmk(@Path("device_id") deviceId: String): Response<DeviceKeyResponse>

    // Geräteliste abrufen
    @GET("keys/devices")
    suspend fun getDevices(): Response<DeviceListResponse>

    // Gerät entfernen
    @DELETE("keys/devices/{device_id}")
    suspend fun removeDevice(@Path("device_id") deviceId: String): Response<Map<String, String>>

    // Partner-UMK abrufen (verschlüsselt mit eigenem UMK oder per ECDH)
    @GET("keys/partner-umk/{partner_id}")
    suspend fun getPartnerUmk(@Path("partner_id") partnerId: String): Response<PartnerUmkResponse>

    // Re-Encryption: alte v2-Nachrichten mit neuem v3-Key re-encrypten
    @POST("keys/reencrypt")
    suspend fun reencryptMessages(@Body request: ReencryptRequest): Response<ReencryptResponse>

    // Nutzer per Telefonnummer suchen
    @GET("users/by-phone/{phone}")
    suspend fun getUserByPhone(@Path("phone") phone: String): Response<UserResponse>

    @GET("users/me")
    suspend fun getMe(): Response<UserResponse>

    @GET("users/me/turn-credentials")
    suspend fun getTurnCredentials(): Response<TurnCredentialsResponse>

    @GET("users/{user_id}")
    suspend fun getUser(@Path("user_id") userId: String): Response<UserResponse>

    @GET("users/{user_id}/status")
    suspend fun getUserStatus(@Path("user_id") userId: String): Response<UserStatusResponse>

    @POST("refresh")
    suspend fun refreshToken(): Response<TokenResponse>

    @POST("users/me/password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<TokenResponse>

    @GET("users/me/admin-panel-password/status")
    suspend fun getAdminPanelPasswordStatus(): Response<AdminPanelPasswordStatusResponse>

    @POST("users/me/admin-panel-password")
    suspend fun setAdminPanelPassword(@Body request: SetAdminPanelPasswordRequest): Response<Map<String, Boolean>>

    @POST("users/me/admin-panel-password/verify")
    suspend fun verifyAdminPanelPassword(@Body request: VerifyAdminPanelPasswordRequest): Response<Map<String, Boolean>>

    @POST("users/me/fcm-token")
    suspend fun updateFcmToken(@Body request: FcmTokenRequest): Response<Unit>

    @POST("user/buy-verification")
    suspend fun buyVerification(): Response<Map<String, Any>>

    @DELETE("users/me")
    suspend fun deleteAccount(): Response<Map<String, String>>

    @PUT("users/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<UserResponse>

    @PUT("users/me/privacy")
    suspend fun updatePrivacySettings(@Body request: PrivacySettingsRequest): Response<Map<String, String>>

    @PUT("users/me/privacy/status-permitted")
    suspend fun updateStatusPermitted(@Body request: StatusPermittedRequest): Response<Map<String, String>>

    @PUT("users/me/p2p")
    suspend fun updateP2pSetting(@Body request: P2pSettingRequest): Response<Map<String, Any>>

    @PUT("users/me/chat-backup")
    suspend fun updateChatBackupSetting(@Body request: ChatBackupToggleRequest): Response<Map<String, Any>>

    @DELETE("users/me/chat-backup")
    suspend fun purgeChatBackup(): Response<Map<String, Any>>

    @POST("users/me/online")
    suspend fun setOnline(): Response<Map<String, String>>

    @POST("users/me/offline")
    suspend fun setOffline(): Response<Map<String, String>>

    @PUT("users/me/busy")
    suspend fun setBusy(@Body body: Map<String, Boolean>): Response<Unit>

    // --- STYX-COINS ---

    /** Schritt 1: RSA-Signatur serverseitig prüfen (vor Gutschrift). */
    @POST("coins/verify-purchase")
    suspend fun verifyPurchase(@Body request: VerifyPurchaseRequest): Response<Unit>

    /** Schritt 2: Coins gutschreiben (nur nach erfolgreicher Verifikation aufrufen). */
    @POST("coins/purchase")
    suspend fun grantCoins(@Body request: CoinPurchaseRequest): Response<CoinPurchaseResponse>

    // --- STATUS ---

    @Multipart
    @POST("status/upload")
    suspend fun createStatus(
        @Part("duration_hours") durationHours: okhttp3.RequestBody,
        @Part("media_type") mediaType: okhttp3.RequestBody,
        @Part file: MultipartBody.Part,
        @Part("as_lethe_team") asLetheTeam: okhttp3.RequestBody,
        @Part("music_url") musicUrl: okhttp3.RequestBody? = null,
        @Part("music_title") musicTitle: okhttp3.RequestBody? = null,
        @Part("music_artist") musicArtist: okhttp3.RequestBody? = null,
        @Part("music_duration_sec") musicDurationSec: okhttp3.RequestBody? = null,
        @Part("music_offset_sec") musicOffsetSec: okhttp3.RequestBody? = null,
        @Part("link_url") linkUrl: okhttp3.RequestBody? = null,
        @Part("link_label") linkLabel: okhttp3.RequestBody? = null
    ): Response<Map<String, String>>

    @GET("status/mine")
    suspend fun getMyStatuses(): Response<List<StatusResponse>>

    @GET("status/contacts")
    suspend fun getContactStatuses(): Response<List<StatusResponse>>

    @retrofit2.http.DELETE("status/{status_id}")
    suspend fun deleteStatus(@Path("status_id") statusId: String): Response<Map<String, String>>

    @POST("status/{status_id}/view")
    suspend fun viewStatus(@Path("status_id") statusId: String): Response<Map<String, String>>

    @GET("status/{status_id}/viewers")
    suspend fun getStatusViewers(@Path("status_id") statusId: String): Response<List<StatusViewerResponse>>

    @GET("status/{status_id}/liked")
    suspend fun isStatusLiked(@Path("status_id") statusId: String): Response<Map<String, Boolean>>

    @POST("status/{status_id}/like")
    suspend fun likeStatus(@Path("status_id") statusId: String): Response<Map<String, Boolean>>

    // --- ADMIN ---

    @GET("admin/status")
    suspend fun getAdminStatus(): Response<AdminStatusResponse>

    @GET("admin/server-info")
    suspend fun getAdminServerInfo(): Response<AdminServerInfoResponse>

    @GET("admin/logs")
    suspend fun getAdminLogs(@Query("lines") lines: Int = 200): Response<AdminLogsResponse>

    @POST("admin/restart")
    suspend fun restartServer(): Response<Map<String, String>>

    @POST("admin/restart-turn")
    suspend fun restartTurnServer(): Response<Map<String, String>>

    @GET("admin/sms-gateway/status")
    suspend fun getSmsGatewayStatus(): Response<SmsGatewayStatusResponse>

    @POST("admin/sms-gateway/restart")
    suspend fun restartSmsGateway(): Response<Map<String, String>>

    @POST("admin/heal-db")
    suspend fun healDatabase(): Response<Map<String, String>>

    @GET("admin/settings")
    suspend fun getServerSettings(): Response<ServerSettingsResponse>

    @POST("admin/settings")
    suspend fun updateServerSettings(@Body update: ServerSettingsUpdate): Response<Map<String, String>>

    @POST("admin/lumis/broadcast")
    suspend fun sendAdminLumisBroadcast(@Body body: Map<String, String>): Response<Map<String, String>>

    // Admin: Nutzerverwaltung
    @GET("admin/users/search")
    suspend fun adminSearchUsers(@Query("q") query: String): Response<List<Map<String, Any>>>

    @POST("admin/users/{userId}/block")
    suspend fun adminBlockUser(@Path("userId") userId: String): Response<Map<String, String>>

    @POST("admin/users/{userId}/unblock")
    suspend fun adminUnblockUser(@Path("userId") userId: String): Response<Map<String, String>>

    @DELETE("admin/users/{userId}")
    suspend fun adminDeleteUser(@Path("userId") userId: String): Response<Map<String, String>>

    @POST("admin/users/{userId}/verify-age")
    suspend fun adminVerifyAge(@Path("userId") userId: String): Response<Map<String, String>>

    @POST("admin/users/{userId}/make-creator")
    suspend fun adminMakeCreator(@Path("userId") userId: String): Response<Map<String, String>>

    @POST("admin/users/{userId}/make-moderator")
    suspend fun adminMakeModerator(@Path("userId") userId: String): Response<Map<String, String>>

    @POST("admin/users/{userId}/remove-moderator")
    suspend fun adminRemoveModerator(@Path("userId") userId: String): Response<Map<String, String>>

    @POST("admin/users/create")
    suspend fun adminCreateUser(@Body body: AdminCreateUserRequest): Response<Map<String, Any>>

    @GET("admin/users/{userId}/details")
    suspend fun adminGetUserDetails(@Path("userId") userId: String): Response<Map<String, Any>>

    @PUT("admin/users/{userId}")
    suspend fun adminUpdateUser(
        @Path("userId") userId: String,
        @Body body: AdminUpdateUserRequest
    ): Response<Map<String, String>>

    @DELETE("creator/sparks/{sparkId}/comments/{commentId}")
    suspend fun deleteSparkComment(
        @Path("sparkId") sparkId: String,
        @Path("commentId") commentId: String
    ): Response<Map<String, String>>

    @POST("admin/reset/users")
    suspend fun adminResetUsers(): Response<Map<String, String>>

    @POST("admin/reset/all")
    suspend fun adminResetAll(): Response<Map<String, String>>

    @GET("admin/instances")
    suspend fun getInstances(@Header("Authorization") token: String): Response<InstancesResponse>

    @POST("admin/instances/{id}/start")
    suspend fun startInstance(@Header("Authorization") token: String, @Path("id") id: Int): Response<Unit>

    @POST("admin/instances/{id}/stop")
    suspend fun stopInstance(@Header("Authorization") token: String, @Path("id") id: Int): Response<Unit>

    @POST("admin/backup-instances/{id}/start")
    suspend fun startBackupInstance(@Header("Authorization") token: String, @Path("id") id: String): Response<Unit>

    @POST("admin/backup-instances/{id}/stop")
    suspend fun stopBackupInstance(@Header("Authorization") token: String, @Path("id") id: String): Response<Unit>

    @GET("admin/failover/status")
    suspend fun failoverStatus(@Header("Authorization") token: String): Response<FailoverOutputResponse>

    @POST("admin/failover/promote")
    suspend fun failoverPromote(@Header("Authorization") token: String): Response<FailoverOutputResponse>

    @POST("admin/failover/recover")
    suspend fun failoverRecover(@Header("Authorization") token: String): Response<FailoverOutputResponse>

    @Multipart
    @POST("upload/media")
    suspend fun uploadMedia(
        @Part("media_type") mediaType: okhttp3.RequestBody,
        @Part file: MultipartBody.Part
    ): Response<Map<String, String>>

    @Multipart
    @POST("chats/{chatId}/listen-together/upload")
    suspend fun uploadListenTogetherTrack(
        @Path("chatId") chatId: String,
        @Part file: MultipartBody.Part
    ): Response<Map<String, String>>

    @GET("chats/{chatId}/listen-together/tracks")
    suspend fun getListenTogetherTracks(
        @Path("chatId") chatId: String
    ): Response<ListenTogetherTracksResponse>

    @POST("chats/{chatId}/listen-together/playlist")
    suspend fun saveListenTogetherPlaylist(
        @Path("chatId") chatId: String,
        @Body request: SavePlaylistRequest
    ): Response<Map<String, Boolean>>

    @GET("chats/{chatId}/listen-together/playlist")
    suspend fun getListenTogetherPlaylist(
        @Path("chatId") chatId: String
    ): Response<GetPlaylistResponse>

    @GET("listen-together/session/{partnerId}")
    suspend fun getListenTogetherSession(
        @Path("partnerId") partnerId: String
    ): Response<ListenTogetherSessionResponse>

    @Multipart
    @POST("upload/3dprint")
    suspend fun upload3dFile(
        @Part file: MultipartBody.Part
    ): Response<ThreeDUploadResponse>

    @POST("messages/3d-file/purchase")
    suspend fun purchase3DFile(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<PurchaseResponse>

    @Multipart
    @POST("upload/profile-image/{user_id}")
    suspend fun uploadProfileImage(
        @Path("user_id") userId: String,
        @Part image: MultipartBody.Part
    ): Response<Map<String, String>>

    // --- KONTAKTVERWALTUNG ---

    @GET("messages/delivery-status-sync")
    suspend fun getDeliveryStatusSync(): Response<List<DeliveryStatusUpdate>>

    @POST("messages/{message_id}/mark-notified")
    suspend fun markMessageNotified(@Path("message_id") messageId: String): Response<Unit>

    @DELETE("messages/{message_id}")
    suspend fun deleteMessage(@Path("message_id") messageId: String): Response<Unit>

    @PUT("messages/{message_id}")
    suspend fun editMessage(
        @Path("message_id") messageId: String,
        @Body body: Map<String, String>
    ): Response<Map<String, String>>

    @GET("messages/{contact_id}")
    suspend fun getMessages(
        @Path("contact_id") contactId: String,
        @Query("limit") limit: Int = 300,
        @Query("before_id") beforeId: String? = null,
        @Query("after_id") afterId: String? = null,
        @Query("after_timestamp") afterTimestamp: String? = null
    ): Response<List<MessageItemResponse>>

    @GET("messages/{contact_id}/sync")
    suspend fun syncMessages(
        @Path("contact_id") contactId: String,
        @Query("after_id") afterId: String? = null,
        @Query("after_timestamp") afterTimestamp: String? = null,
        @Query("limit") limit: Int = 1000
    ): Response<List<SyncMessageItem>>

    @GET("contacts")
    suspend fun getContacts(): Response<List<ContactListItem>>

    @POST("contacts/add")
    suspend fun addContact(@Body request: AddContactRequest): Response<Map<String, String>>

    @POST("contacts/respond")
    suspend fun respondToContactRequest(@Body request: ContactResponseAction): Response<Map<String, String>>

    @GET("contacts/pending")
    suspend fun getPendingContacts(): Response<List<PendingContactRequest>>

    @DELETE("contacts/by-partner/{partner_id}")
    suspend fun deleteContactByPartner(@Path("partner_id") partnerId: String): Response<Map<String, String>>

    @POST("contacts/renew-handshake")
    suspend fun requestHandshakeRenew(@Body request: HandshakeRenewRequest): Response<Map<String, String>>

    @POST("contacts/renew-handshake/respond")
    suspend fun respondHandshakeRenew(@Body request: HandshakeRenewRespond): Response<Map<String, String>>

    @POST("blocks/add")
    suspend fun blockUser(@Body request: BlockUserRequest): Response<Map<String, String>>

    @GET("blocks")
    suspend fun getBlockedUsers(): Response<List<BlockedUserResponse>>

    @POST("blocks/unblock")
    suspend fun unblockUser(@Body request: UnblockRequest): Response<Map<String, String>>

    // --- REPORT & SUPPORT ---

    @POST("report")
    suspend fun reportUser(@Body request: ReportUserRequest): Response<Map<String, String>>

    @Multipart
    @POST("support")
    suspend fun submitSupportTicket(
        @Part("category") category: okhttp3.RequestBody,
        @Part("title") title: okhttp3.RequestBody,
        @Part("description") description: okhttp3.RequestBody,
        @Part files: List<MultipartBody.Part>
    ): Response<Map<String, String>>

    @GET("support/my-tickets")
    suspend fun getMyTickets(): Response<List<UserSupportTicket>>

    @Multipart
    @POST("support/otp-issue")
    suspend fun createOtpSupportTicket(
        @Part("phone_number") phoneNumber: okhttp3.RequestBody,
        @Part("message") message: okhttp3.RequestBody
    ): Response<Map<String, String>>

    // --- VERKNÜPFTE GERÄTE ---

    @POST("linked-devices/scan")
    suspend fun scanQrCode(@Body request: ScanQrRequest): Response<Map<String, String>>

    @GET("linked-devices")
    suspend fun getLinkedDevices(): Response<List<LinkedDevice>>

    @DELETE("linked-devices/{device_id}")
    suspend fun removeLinkedDevice(@Path("device_id") deviceId: String): Response<Map<String, String>>

    // --- NEARBY-SYSTEM ---

    @GET("nearby/profile/me")
    suspend fun getMyNearbyProfile(): Response<NearbyProfileResponse>

    @GET("nearby/profiles/{profile_user_id}")
    suspend fun getNearbyProfileById(@Path("profile_user_id") userId: String): Response<NearbyProfileResponse>

    @GET("nearby/discover/{user_id}")
    suspend fun getNearbyUsers(
        @Path("user_id") userId: String,
        @Query("radius_km") radiusKm: Double,
        @Query("gender_filter") genderFilter: String = "ALL",
        @Query("age_min") ageMin: Int = 0,
        @Query("age_max") ageMax: Int = 150,
        @Query("friendship_only") friendshipOnly: Boolean = false
    ): Response<List<NearbyProfileResponse>>

    @POST("nearby/like")
    suspend fun sendLike(@Body request: NearbyLikeRequest): Response<Map<String, String>>

    @POST("nearby/like-by-username")
    suspend fun sendLikeByUsername(@Body request: NearbyLikeByUsernameRequest): Response<Map<String, String>>

    @GET("nearby/likes/incoming")
    suspend fun getIncomingLikes(): Response<List<NearbyLikeIncoming>>

    @POST("nearby/likes/{like_id}/accept")
    suspend fun acceptLike(@Path("like_id") likeId: String): Response<Map<String, String>>

    @POST("nearby/likes/{like_id}/reject")
    suspend fun rejectLike(@Path("like_id") likeId: String): Response<Unit>

    @POST("nearby/restore-profile")
    suspend fun restoreNearbyProfileWithCoins(@Body request: NearbyRestoreRequest): Response<Map<String, Any>>

    @GET("nearby/matches")
    suspend fun getNearbyMatches(): Response<List<NearbyMatch>>

    @GET("nearby/messages/{match_id}")
    suspend fun getNearbyMessages(@Path("match_id") matchId: String): Response<List<NearbyMessage>>

    @POST("nearby/messages/{match_id}")
    suspend fun sendNearbyMessage(
        @Path("match_id") matchId: String,
        @Body request: NearbySendMessageRequest
    ): Response<NearbyMessage>

    @POST("nearby/messages/{msg_id}/view")
    suspend fun markNearbyMessageViewed(@Path("msg_id") msgId: String): Response<Unit>

    @POST("nearby/matches/{match_id}/invite-to-lethe")
    suspend fun inviteToLethe(@Path("match_id") matchId: String): Response<Unit>

    @POST("nearby/profiles/{profile_user_id}/questions")
    suspend fun askAnonQuestion(
        @Path("profile_user_id") profileUserId: String,
        @Body request: NearbyAnonQuestionCreate
    ): Response<Map<String, String>>

    @GET("nearby/profiles/{profile_user_id}/questions")
    suspend fun getProfileQuestions(
        @Path("profile_user_id") profileUserId: String
    ): Response<List<NearbyAnonQuestion>>

    @GET("nearby/my-questions")
    suspend fun getMyNearbyQuestions(): Response<List<NearbyAnonQuestion>>

    @POST("nearby/questions/{question_id}/answer")
    suspend fun answerNearbyQuestion(
        @Path("question_id") questionId: String,
        @Body request: NearbyAnonAnswerRequest
    ): Response<Map<String, String>>

    @POST("nearby/questions/{question_id}/like")
    suspend fun likeAnonQuestion(
        @Path("question_id") questionId: String
    ): Response<Map<String, Any>>

    @POST("nearby/questions/{question_id}/react")
    suspend fun reactToNearbyQuestion(
        @Path("question_id") questionId: String,
        @Body request: NearbyReactionRequest
    ): Response<Map<String, Any>>

    @Multipart
    @POST("nearby/questions/{question_id}/answer-image")
    suspend fun uploadAnswerImage(
        @Path("question_id") questionId: String,
        @Part image: MultipartBody.Part
    ): Response<Map<String, String>>

    @POST("nearby/profiles/{userId}/visit")
    suspend fun trackNearbyProfileVisit(@Path("userId") userId: String): Response<Map<String, String>>

    @GET("nearby/profile-visitors")
    suspend fun getNearbyProfileVisitors(): Response<List<NearbyProfileVisitor>>

    @POST("nearby/visitors/subscribe")
    suspend fun subscribeToNearbyVisitors(): Response<Map<String, Any>>

    @GET("nearby/visitors/subscription-status")
    suspend fun getNearbyVisitorsSubscriptionStatus(): Response<NearbyVisitorsSubscriptionStatus>

    @POST("nearby/profile")
    suspend fun updateNearbyProfile(@Body request: NearbyProfileCreate): Response<Map<String, String>>

    @Multipart
    @POST("upload/nearby-image/{user_id}")
    suspend fun uploadNearbyImage(
        @Path("user_id") userId: String,
        @Part image: MultipartBody.Part
    ): Response<Map<String, String>>

    @Multipart
    @POST("upload/nearby-gallery/{user_id}/{slot}")
    suspend fun uploadNearbyGalleryPhoto(
        @Path("user_id") userId: String,
        @Path("slot") slot: Int,
        @Part file: MultipartBody.Part
    ): Response<Map<String, String>>

    @DELETE("upload/nearby-gallery/{user_id}/{slot}")
    suspend fun deleteNearbyGalleryPhoto(
        @Path("user_id") userId: String,
        @Path("slot") slot: Int
    ): Response<Map<String, String>>

    @Multipart
    @POST("upload/nearby-chat-image")
    suspend fun uploadNearbyChatImage(
        @Part image: MultipartBody.Part
    ): Response<Map<String, String>>

    @Multipart
    @POST("upload/group-image/{group_id}")
    suspend fun uploadGroupImage(
        @Path("group_id") groupId: String,
        @Part image: MultipartBody.Part
    ): Response<Map<String, String>>

    // --- GRUPPEN ---

    @GET("groups")
    suspend fun getGroups(): Response<List<GroupResponse>>

    @POST("groups")
    suspend fun createGroup(@Body request: GroupCreateRequest): Response<Map<String, String>>

    @GET("groups/{group_id}/messages")
    suspend fun getGroupMessages(
        @Path("group_id") groupId: String,
        @Query("limit") limit: Int = 50,
        @Query("before_id") beforeId: String? = null
    ): Response<List<MessageItemResponse>>

    @PATCH("groups/{group_id}")
    suspend fun updateGroup(
        @Path("group_id") groupId: String,
        @Body req: GroupUpdateRequest
    ): Response<Map<String, String>>

    @POST("groups/{group_id}/members")
    suspend fun addGroupMembers(
        @Path("group_id") groupId: String,
        @Body req: AddMembersRequest
    ): Response<Map<String, String>>

    @DELETE("groups/{group_id}/members/{user_id}")
    suspend fun removeGroupMember(
        @Path("group_id") groupId: String,
        @Path("user_id") userId: String
    ): Response<Map<String, String>>

    @GET("groups/{group_id}/members")
    suspend fun getGroupMembers(
        @Path("group_id") groupId: String
    ): Response<List<GroupMemberInfo>>

    @PUT("groups/{group_id}/members/{user_id}/role")
    suspend fun setGroupMemberRole(
        @Path("group_id") groupId: String,
        @Path("user_id") userId: String,
        @Body request: SetMemberRoleRequest
    ): Response<GroupMemberInfo>

    // --- GRUPPEN E2EE (SENDER-KEY-VERWALTUNG) ---

    /**
     * Lädt die eigenen Sender-Key-Pakete (verschlüsselt mit 1:1-ECDH-Shared-Secret)
     * für alle anderen Gruppenmitglieder hoch.
     */
    @POST("groups/{group_id}/keys/distribute")
    suspend fun distributeGroupSenderKeys(
        @Path("group_id") groupId: String,
        @Body request: DistributeKeysRequest
    ): Response<Map<String, String>>

    /**
     * Holt alle Sender-Key-Pakete ab, die andere Mitglieder speziell für uns verschlüsselt haben.
     * Wir entschlüsseln jedes Bundle mit unserem 1:1-ECDH-Shared-Secret mit dem jeweiligen Owner.
     */
    @GET("groups/{group_id}/keys/my_bundle")
    suspend fun getMyGroupKeyBundle(
        @Path("group_id") groupId: String
    ): Response<MyKeyBundleResponse>

    /**
     * Holt die ECDH-Public-Keys aller anderen Mitglieder.
     * Wird benötigt, um Shared Secrets abzuleiten und Sender-Key-Bundles zu verschlüsseln.
     */
    @GET("groups/{group_id}/members/keys")
    suspend fun getGroupMemberPublicKeys(
        @Path("group_id") groupId: String
    ): Response<List<GroupMemberPublicKey>>

    /**
     * Meldet dem Server, dass dieser Client die E2EE-Einrichtung für die Gruppe abgeschlossen hat.
     * Der Server aktiviert die Gruppe sobald alle Mitglieder bereit sind.
     */
    @POST("groups/{group_id}/ready")
    suspend fun markGroupReady(
        @Path("group_id") groupId: String
    ): Response<Map<String, Any>>

    // --- TERMIN-SYSTEM ---

    @POST("groups/{group_id}/appointments")
    suspend fun createGroupAppointment(
        @Path("group_id") groupId: String,
        @Body request: GroupAppointmentCreateRequest
    ): Response<GroupAppointmentResponse>

    @GET("groups/{group_id}/appointments")
    suspend fun getGroupAppointments(
        @Path("group_id") groupId: String
    ): Response<List<GroupAppointmentResponse>>

    @GET("groups/appointments/{appointment_id}")
    suspend fun getGroupAppointment(
        @Path("appointment_id") appointmentId: String
    ): Response<GroupAppointmentResponse>

    @PUT("groups/appointments/{appointment_id}/finalize")
    suspend fun finalizeAppointment(
        @Path("appointment_id") appointmentId: String,
        @Body request: AppointmentFinalizeRequest
    ): Response<GroupAppointmentResponse>

    @POST("groups/appointments/{appointment_id}/rsvp")
    suspend fun rsvpAppointment(
        @Path("appointment_id") appointmentId: String,
        @Body request: RsvpRequest
    ): Response<GroupAppointmentResponse>

    @POST("groups/appointments/{appointment_id}/vote")
    suspend fun voteForAppointmentOptions(
        @Path("appointment_id") appointmentId: String,
        @Body request: AppointmentVoteRequest
    ): Response<GroupAppointmentResponse>

    @POST("groups/appointments/{appointment_id}/move-to-confirmation")
    suspend fun moveToConfirmation(
        @Path("appointment_id") appointmentId: String,
        @Body request: MoveToConfirmationRequest
    ): Response<GroupAppointmentResponse>

    @POST("groups/appointments/{appointment_id}/final-approve")
    suspend fun finalApproveAppointment(
        @Path("appointment_id") appointmentId: String
    ): Response<GroupAppointmentResponse>

    // --- GRUPPEN MEDIEN-AUFRUFE ---

    @POST("groups/{group_id}/messages/{message_id}/view")
    suspend fun recordGroupMediaView(
        @Path("group_id") groupId: String,
        @Path("message_id") messageId: String
    ): Response<Unit>

    @GET("groups/{group_id}/messages/{message_id}/views")
    suspend fun getGroupMediaViews(
        @Path("group_id") groupId: String,
        @Path("message_id") messageId: String
    ): Response<List<GroupMediaViewEntry>>

    @GET("groups/{group_id}/messages/{message_id}/reads")
    suspend fun getGroupMessageReads(
        @Path("group_id") groupId: String,
        @Path("message_id") messageId: String
    ): Response<List<GroupMessageReadEntry>>

    // --- POLLS ---

    @POST("polls")
    suspend fun createPoll(@Body request: PollCreate): Response<PollResponse>

    @GET("polls/{poll_id}")
    suspend fun getPoll(@Path("poll_id") pollId: String): Response<PollResponse>

    @POST("polls/{poll_id}/vote")
    suspend fun voteOnPoll(
        @Path("poll_id") pollId: String,
        @Body request: PollVoteRequest
    ): Response<Map<String, String>>

    // --- CREATOR ---

    @GET("creator/profile")
    suspend fun getCreatorProfile(): Response<CreatorProfileResponse>

    @Multipart
    @POST("creator/profile")
    suspend fun updateCreatorProfile(
        @Part("bio") bio: okhttp3.RequestBody? = null
    ): Response<CreatorProfileResponse>

    @Multipart
    @POST("creator/upload/banner")
    suspend fun uploadCreatorBanner(
        @Part file: MultipartBody.Part
    ): Response<Map<String, String>>

    @Multipart
    @POST("creator/upload/profile-image")
    suspend fun uploadCreatorProfileImage(
        @Part file: MultipartBody.Part
    ): Response<Map<String, String>>

    @GET("creator/content")
    suspend fun getCreatorContent(): Response<List<CreatorContentResponse>>

    @GET("creator/sparks")
    suspend fun getCreatorSparks(): Response<List<CreatorContentResponse>>

    // Content CRUD
    @POST("creator/content")
    suspend fun createCreatorContent(@Body request: CreatorContentCreateRequest): Response<CreatorContentResponse>

    @GET("creator/content/{id}")
    suspend fun getCreatorContentById(@Path("id") id: String): Response<CreatorContentResponse>

    @PUT("creator/content/{id}")
    suspend fun updateCreatorContent(@Path("id") id: String, @Body request: CreatorContentCreateRequest): Response<CreatorContentResponse>

    @DELETE("creator/content/{id}")
    suspend fun deleteCreatorContent(@Path("id") id: String): Response<Unit>

    // Upload
    @Multipart
    @POST("creator/upload/content-image")
    suspend fun uploadContentImage(@Part file: MultipartBody.Part): Response<Map<String, String>>

    @Multipart
    @POST("creator/upload/content-video")
    suspend fun uploadContentVideo(@Part file: MultipartBody.Part): Response<Map<String, String>>

    /**
     * HLS-Upload für Sparks: thumbnail.jpg + index.m3u8 + seg*.ts als Multipart-Liste.
     * Alle Parts laufen unter dem Feldnamen "files". Der Server unterscheidet sie
     * anhand des Dateinamens (thumbnail.jpg → Vorschaubild, index.m3u8 → Playlist,
     * seg*.ts → MPEG-TS-Segmente).
     */
    @Multipart
    @POST("creator/upload/spark-hls")
    suspend fun uploadSparkHls(
        @Part files: List<MultipartBody.Part>,
        @Part("title") title: RequestBody,
        @Part("category") category: RequestBody?,
        @Part("description") description: RequestBody?,
        @Part("music_title") musicTitle: RequestBody?,
        @Part("music_artist") musicArtist: RequestBody?,
        @Part("music_cover_url") musicCoverUrl: RequestBody?,
        @Part("sound_origin_spark_id") soundOriginSparkId: RequestBody?,
        @Part("music_id") musicId: RequestBody?
    ): Response<CreatorContentResponse>

    @Multipart
    @POST("creator/upload/spark-images")
    suspend fun uploadSparkImages(
        @Part files: List<MultipartBody.Part>,
        @Part("title") title: RequestBody,
        @Part("description") description: RequestBody?,
        @Part("category") category: RequestBody?,
        @Part("music_title") musicTitle: RequestBody?,
        @Part("music_artist") musicArtist: RequestBody?,
        @Part("music_cover_url") musicCoverUrl: RequestBody?,
        @Part("sound_origin_spark_id") soundOriginSparkId: RequestBody?,
        @Part("music_id") musicId: RequestBody?,
        @Part audioFile: MultipartBody.Part? = null
    ): Response<CreatorContentResponse>

    @Multipart
    @POST("music/upload")
    suspend fun uploadMusicFile(
        @Part file: MultipartBody.Part,
        @Part("artist") artist: RequestBody? = null,
        @Part("song_title") songTitle: RequestBody? = null,
        @Part("year") year: RequestBody? = null,
        @Part("lyrics") lyrics: RequestBody? = null,
        @Part("producer") producer: RequestBody? = null,
        @Part("preview_offset_sec") previewOffsetSec: RequestBody? = null
    ): Response<MusicResponse>

    @PUT("music/{musicId}")
    suspend fun updateMusicTrack(
        @Path("musicId") musicId: String,
        @Body request: MusicUpdateRequest
    ): Response<MusicResponse>

    @DELETE("music/{musicId}")
    suspend fun deleteMusicTrack(
        @Path("musicId") musicId: String
    ): Response<Map<String, String>>

    @POST("music/save-api-track")
    suspend fun saveApiMusicTrack(
        @Body request: SaveApiTrackRequest
    ): Response<MusicResponse>

    @GET("music/{musicId}")
    suspend fun getMusic(
        @Path("musicId") musicId: String
    ): Response<MusicResponse>

    @GET("music/by-url")
    suspend fun getMusicByUrl(
        @Query("url") url: String
    ): Response<MusicResponse>

    @GET("music/library")
    suspend fun getLetheLibrary(
        @Query("q") query: String? = null
    ): Response<List<LetheMusicTrack>>

    // Persoenliche Musikbibliothek + Playlists
    @POST("mymusic")
    suspend fun saveUserMusic(@Body request: UserMusicSaveRequest): Response<UserMusicResponse>

    @GET("mymusic")
    suspend fun getUserMusic(
        @Query("favorites_only") favoritesOnly: Boolean = false,
        @Query("playlist_id") playlistId: String? = null
    ): Response<List<UserMusicResponse>>

    @GET("mymusic/playlists")
    suspend fun getUserPlaylists(): Response<List<PlaylistResponse>>

    @POST("mymusic/{musicId}/favorite")
    suspend fun setUserMusicFavorite(
        @Path("musicId") musicId: String,
        @Body request: UserMusicFavoriteRequest
    ): Response<UserMusicResponse>

    @POST("mymusic/{musicId}/playlist")
    suspend fun addUserMusicToPlaylist(
        @Path("musicId") musicId: String,
        @Body request: UserMusicPlaylistRequest
    ): Response<UserMusicResponse>

    @DELETE("mymusic/{musicId}")
    suspend fun deleteUserMusic(@Path("musicId") musicId: String): Response<Unit>

    @GET("sparks/sound/{sparkId}")
    suspend fun getSparksBySound(
        @Path("sparkId") sparkId: String
    ): Response<List<CreatorContentResponse>>

    // Settings
    @PUT("creator/settings")
    suspend fun updateCreatorSettings(@Body request: CreatorSettingsRequest): Response<CreatorProfileResponse>

    // Purchase
    @POST("creator/content/{id}/purchase")
    suspend fun purchaseContent(@Path("id") id: String): Response<PurchaseResponse>

    @POST("creator/content/{id}/like")
    suspend fun likeContent(@Path("id") id: String): Response<Map<String, Any>>

    @POST("creator/subscription/{creatorId}")
    suspend fun subscribeToCreator(@Path("creatorId") creatorId: String): Response<PurchaseResponse>

    @GET("creator/subscription/check/{creatorId}")
    suspend fun checkSubscription(@Path("creatorId") creatorId: String): Response<SubscriptionCheckResponse>

    // Livestream (EPIC 2)
    @GET("creator/stream-key")
    suspend fun getStreamKey(): Response<LiveStreamKeyResponse>

    @GET("vip/creator/{creatorId}/live")
    suspend fun getCreatorLiveInfo(@Path("creatorId") creatorId: String): Response<CreatorLiveInfoResponse>

    @POST("creator/stream/start")
    suspend fun startStream(): Response<Map<String, String>>

    @POST("creator/stream/end")
    suspend fun endStream(): Response<Map<String, String>>

    // VIP Diskussionen
    @GET("vip/discussions")
    suspend fun getVipDiscussions(
        @Query("category") category: String? = null,
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 30
    ): Response<List<VipDiscussionResponse>>

    @POST("vip/discussions")
    suspend fun createVipDiscussion(@Body data: VipDiscussionCreate): Response<VipDiscussionResponse>

    @GET("vip/discussions/{id}/replies")
    suspend fun getDiscussionReplies(@retrofit2.http.Path("id") id: String): Response<List<VipDiscussionReplyResponse>>

    @POST("vip/discussions/{id}/replies")
    suspend fun createDiscussionReply(
        @retrofit2.http.Path("id") id: String,
        @Body data: VipDiscussionReplyCreate
    ): Response<VipDiscussionReplyResponse>

    @POST("vip/discussions/{id}/like")
    suspend fun likeVipDiscussion(@retrofit2.http.Path("id") id: String): Response<Map<String, Any>>

    // VIP Feed
    @GET("vip/feed")
    suspend fun getVipFeed(
        @Query("category") category: String? = null,
        @Query("type") type: String? = null,
        @Query("creator_id") creatorId: String? = null,
        @Query("similar_to") similarTo: String? = null
    ): Response<List<CreatorContentResponse>>

    // Alle Inhalte eines Creators für die Profil-Ansicht (keine Seen-Filterung)
    @GET("vip/creator/{creatorId}/content")
    suspend fun getCreatorPublicContent(
        @Path("creatorId") creatorId: String
    ): Response<List<CreatorContentResponse>>

    // Öffentliches Creator-Profil mit Stats (Abonnenten, Views, Likes, meine Abos)
    @GET("vip/creator/{creatorId}/profile")
    suspend fun getPublicCreatorProfile(
        @Path("creatorId") creatorId: String
    ): Response<PublicCreatorProfileResponse>

    @GET("vip/search")
    suspend fun searchVip(@Query("query") query: String): Response<VipSearchResponse>

    // VIP Kategorie-System (Honeycomb)
    @GET("vip/categories")
    suspend fun getVipCategories(
        @Query("parent_id") parentId: String? = null
    ): Response<List<VipCategoryResponse>>

    @GET("vip/categories/{categoryId}/threads")
    suspend fun getCategoryThreads(
        @retrofit2.http.Path("categoryId") categoryId: String,
        @Query("radar") radar: Boolean = false,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null
    ): Response<List<VipThreadResponse>>

    @POST("vip/threads")
    suspend fun createVipThread(@Body data: VipThreadCreate): Response<VipThreadResponse>

    @GET("vip/threads/{threadId}/messages")
    suspend fun getThreadMessages(
        @retrofit2.http.Path("threadId") threadId: String
    ): Response<List<VipThreadMessageResponse>>

    @POST("vip/threads/{threadId}/messages")
    suspend fun createThreadMessage(
        @retrofit2.http.Path("threadId") threadId: String,
        @Body data: VipThreadMessageCreate
    ): Response<VipThreadMessageResponse>

    @PUT("vip/threads/{threadId}")
    suspend fun updateVipThread(
        @retrofit2.http.Path("threadId") threadId: String,
        @Body data: VipThreadUpdate
    ): Response<VipThreadResponse>

    @DELETE("vip/threads/{threadId}")
    suspend fun deleteVipThread(
        @retrofit2.http.Path("threadId") threadId: String
    ): Response<Map<String, Any>>

    @POST("vip/threads/{threadId}/messages/{messageId}/like")
    suspend fun likeVipThreadMessage(
        @retrofit2.http.Path("threadId") threadId: String,
        @retrofit2.http.Path("messageId") messageId: String
    ): Response<Map<String, Any>>

    @GET("vip/user-stats/{userId}")
    suspend fun getVipUserStats(
        @retrofit2.http.Path("userId") userId: String
    ): Response<VipUserStatsResponse>

    // Lethe Algorithmus v1: Spark-Interaktion senden
    @POST("api/sparks/{sparkId}/interaction")
    suspend fun trackSparkInteraction(
        @Path("sparkId") sparkId: String,
        @Body request: SparkInteractionRequest
    ): Response<Map<String, String>>

    // Spark-Metadaten bearbeiten (nur Creator)
    @retrofit2.http.PATCH("api/sparks/{sparkId}")
    suspend fun editSpark(
        @Path("sparkId") sparkId: String,
        @Body request: SparkEditRequest
    ): Response<Map<String, String>>

    // --- EINLADUNGEN ---

    @POST("invite/generate")
    suspend fun generateInvite(): Response<InviteGenerateResponse>

    @GET("invite/{token}")
    suspend fun getInviteInfo(@Path("token") token: String): Response<InviteInfoResponse>

    @POST("invite/{token}/redeem")
    suspend fun redeemInvite(@Path("token") token: String): Response<InviteRedeemResponse>

    @POST("contacts/phone-lookup")
    suspend fun lookupPhoneContacts(@Body body: Map<String, List<String>>): Response<PhoneLookupResponse>

    // --- ANIMATION STATE ---

    @GET("admin/animation-state")
    suspend fun getAnimationState(): Response<AnimationStateResponse>

    @POST("admin/animation-state")
    suspend fun setAnimationState(@Body request: AnimationStateRequest): Response<Map<String, String>>

    // --- SMS PASSWORT-RESET ---

    @POST("sms/send-registration-otp")
    suspend fun sendRegistrationOtp(@Body request: RegistrationOtpRequest): Response<Map<String, String>>

    @POST("sms/send-verification")
    suspend fun sendPasswordResetSms(@Body request: ForgotPasswordRequest): Response<Map<String, String>>

    @POST("sms/reset-password")
    suspend fun resetPasswordWithToken(@Body request: ResetPasswordRequest): Response<Map<String, String>>

    @POST("sms/verify-reset-code")
    suspend fun verifyResetCode(@Body request: VerifyResetCodeRequest): Response<Map<String, String>>

    @POST("sms/send-temp-password")
    suspend fun sendTempPasswordViaSms(@Body request: SendPasswordViaSmsRequest): Response<Map<String, String>>

    // OTP an Telefon senden (Telefon-Verifikation für neue/alte User)
    @POST("sms/send-phone-otp")
    suspend fun sendPhoneOtp(@Body request: PhoneOtpRequest): Response<Map<String, String>>

    // OTP bestätigen → setzt is_phone_verified=True
    @POST("sms/verify-phone")
    suspend fun confirmPhoneOtp(@Body request: PhoneOtpConfirmRequest): Response<PhoneVerifiedResponse>

    // --- ALTERSVERIFIKATION ---
    @Multipart
    @POST("api/verify-age")
    suspend fun verifyAge(
        @Part file: MultipartBody.Part,
        @Part("birthdate") birthdate: okhttp3.RequestBody
    ): Response<AgeVerificationResponse>

    // --- SPARK-KOMMENTARE ---

    @GET("creator/sparks/{sparkId}/comments")
    suspend fun getSparkComments(
        @Path("sparkId") sparkId: String
    ): Response<List<SparkCommentResponse>>

    @POST("creator/sparks/{sparkId}/comments")
    suspend fun postSparkComment(
        @Path("sparkId") sparkId: String,
        @Body request: SparkCommentCreateRequest
    ): Response<SparkCommentResponse>

    @POST("creator/sparks/comments/{commentId}/like")
    suspend fun likeSparkComment(
        @Path("commentId") commentId: String
    ): Response<Map<String, Any>>

    @GET("creator/sparks/stats")
    suspend fun getSparkStats(): Response<SparkStatsResponse>

    @POST("creator/sparks/{sparkId}/save")
    suspend fun saveSpark(@Path("sparkId") sparkId: String): Response<Map<String, Any>>

    @GET("creator/sparks/saved")
    suspend fun getSavedSparks(): Response<List<CreatorContentResponse>>

    @GET("creator/sparks/liked")
    suspend fun getLikedSparks(): Response<List<CreatorContentResponse>>

    /** Öffentliche Vorschau eines Beitrags per Content-UUID (kein Auth erforderlich). */
    @GET("public/content/{id}")
    suspend fun getPublicContentPreview(@Path("id") id: String): Response<PublicContentPreview>

    // --- BOTS ---

    /** Öffentliche Bot-Suche (kein Auth nötig). */
    @GET("bots/list")
    suspend fun searchBots(@Query("q") query: String): Response<List<BotPublicResponse>>

    /** Startet einen Bot-Chat (auto-accepted, kein Handshake). */
    @POST("bots/{botFakeNumber}/start")
    suspend fun startBotChat(@Path("botFakeNumber") botFakeNumber: String): Response<BotStartResponse>

    // --- GAMING ---

    @POST("gaming/session")
    suspend fun saveGameSession(@Body request: SaveGameSessionRequest): Response<GameSessionResponse>

    @GET("gaming/ranking")
    suspend fun getGamingRanking(): Response<GameRankingResponse>

    @POST("gaming/jod/score")
    suspend fun submitJodScore(@Query("score") score: Int): Response<JodScoreResponse>

    @GET("gaming/jod/leaderboard")
    suspend fun getJodLeaderboard(): Response<JodLeaderboardResponse>

    @GET("gaming/jod/status")
    suspend fun getJodStatus(): Response<JodStatusResponse>

    @POST("gaming/jod/continuous_play")
    suspend fun updateJodContinuousPlay(@Query("action") action: String): Response<JodContinuousPlayResponse>

    @POST("gaming/jod/wormhole_used")
    suspend fun jodWormholeUsed(): Response<JodContinuousPlayResponse>

    @POST("gaming/pinball/score")
    suspend fun submitPinballScore(@Query("score") score: Int): Response<PinballScoreResponse>

    @GET("gaming/pinball/leaderboard")
    suspend fun getPinballLeaderboard(): Response<PinballLeaderboardResponse>

    @GET("gaming/me")
    suspend fun getMyGamingStats(): Response<MyGamingStatsResponse>

    @POST("gaming/ttt/reward")
    suspend fun claimTttReward(@Body request: TttRewardRequest): Response<TttRewardResponse>

    @POST("gaming/sknch/reward")
    suspend fun claimSknChReward(@Body request: SknChRewardRequest): Response<TttRewardResponse>

    @GET("gaming/history")
    suspend fun getGamingHistory(): Response<List<GamingHistoryEntry>>

    @GET("gaming/profile-stats/{userId}")
    suspend fun getGameProfileStats(@Path("userId") userId: String): Response<GameProfileStats>

    @POST("gaming/coins/exchange")
    suspend fun exchangeCoinsForStyx(): Response<CoinsExchangeResponse>

    // --- SKETCH N CHECK ---

    @GET("sknch/games")
    suspend fun getSknChGames(): Response<List<SknChGameEntry>>

    @POST("sknch/games")
    suspend fun createSknChGame(@Body request: SknChCreateGameRequest): Response<SknChGameEntry>

    @PUT("sknch/games/{gameId}")
    suspend fun updateSknChGame(
        @Path("gameId") gameId: String,
        @Body request: SknChUpdateSettingsRequest
    ): Response<SknChGameEntry>

    @POST("sknch/games/{gameId}/join")
    suspend fun joinSknChGame(@Path("gameId") gameId: String): Response<SknChGameEntry>

    @DELETE("sknch/games/{gameId}")
    suspend fun deleteSknChGame(@Path("gameId") gameId: String): Response<Map<String, Any>>

    // --- ADMIN STATS ---

    // --- CREATOR ARTICLES ---

    @POST("creator/articles")
    suspend fun createArticle(@Body body: CreateArticleRequest): Response<ArticleResponse>

    @GET("creator/articles")
    suspend fun getMyArticles(): Response<List<ArticleResponse>>

    @Multipart
    @POST("creator/articles/{id}/files")
    suspend fun uploadArticleFile(
        @Path("id") articleId: Int,
        @Part("file_type") fileType: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<ArticleFileResponse>

    @GET("creator/articles/{id}")
    suspend fun getArticleDetail(@Path("id") articleId: Int): Response<Map<String, Any>>

    @DELETE("creator/articles/{id}")
    suspend fun deleteArticle(@Path("id") articleId: Int): Response<Map<String, String>>

    @GET("admin/stats")
    suspend fun getAdminStats(@Header("Authorization") token: String): Response<AdminStatsResponse>

    @GET("admin/support")
    suspend fun adminGetSupportTickets(
        @Header("Authorization") token: String,
        @Query("status") status: String = "all"
    ): Response<List<AdminSupportTicket>>

    @POST("admin/support/{ticketId}/reply")
    suspend fun adminReplySupportTicket(
        @Header("Authorization") token: String,
        @Path("ticketId") ticketId: String,
        @Body body: AdminSupportReplyRequest
    ): Response<Map<String, String>>

    @PATCH("admin/support/{ticketId}/status")
    suspend fun adminUpdateSupportTicketStatus(
        @Header("Authorization") token: String,
        @Path("ticketId") ticketId: String,
        @Body body: Map<String, String>
    ): Response<Map<String, String>>

    @GET("admin/reports")
    suspend fun adminGetUserReports(
        @Header("Authorization") token: String,
        @Query("status") status: String = "all"
    ): Response<List<AdminUserReport>>

    @PATCH("admin/reports/{reportId}/status")
    suspend fun adminUpdateReportStatus(
        @Header("Authorization") token: String,
        @Path("reportId") reportId: String,
        @Body body: Map<String, String>
    ): Response<Map<String, String>>

    // ─────────────────────────────────────────────────────────────────────────
    // LETHE FAMILY – Elternkontrolle
    // ─────────────────────────────────────────────────────────────────────────

    /** Vollständiger Family-Status: bin ich Parent/Child? Wer sind meine Verbindungen? */
    @GET("family/my-status")
    suspend fun getFamilyStatus(): Response<FamilyStatusResponse>

    /** Parent erstellt eine neue Einladung (invite_token für das Kind). */
    @POST("family/invite")
    suspend fun createFamilyInvite(): Response<FamilyInviteResponse>

    /** Kind-Account akzeptiert eine Family-Einladung anhand des Tokens. */
    @POST("family/accept")
    suspend fun acceptFamilyInvite(@Body body: FamilyAcceptInviteRequest): Response<Map<String, String>>

    /** Parent liest die Berechtigungen eines Kind-Accounts. */
    @GET("family/children/{childId}/permissions")
    suspend fun getChildPermissions(
        @Path("childId") childId: String
    ): Response<ChildPermissionsResponse>

    /** Parent aktualisiert die Berechtigungen eines Kind-Accounts (PATCH = partielle Updates). */
    @PATCH("family/children/{childId}/permissions")
    suspend fun updateChildPermissions(
        @Path("childId") childId: String,
        @Body body: ChildPermissionsUpdateRequest
    ): Response<ChildPermissionsResponse>

    /** Parent setzt eine 4-stellige Kontroll-PIN für den Kind-Account. */
    @POST("family/children/{childId}/pin")
    suspend fun setFamilyPin(
        @Path("childId") childId: String,
        @Body body: FamilySetPinRequest
    ): Response<Map<String, String>>

    /** Kind-App prüft die Kontroll-PIN (für gesperrte Bereiche). */
    @POST("family/verify-pin")
    suspend fun verifyFamilyPin(@Body body: FamilyPinVerifyRequest): Response<Map<String, Boolean>>

    /** Parent entfernt eine Verbindung / Kind verlässt den Family-Verbund. */
    @DELETE("family/relations/{relationId}")
    suspend fun deleteFamilyRelation(
        @Path("relationId") relationId: String
    ): Response<Map<String, String>>

    /** Kind-Account ruft seine eigenen aktiven Berechtigungen ab (für App-Startup-Sync). */
    @GET("family/my-permissions")
    suspend fun getMyChildPermissions(): Response<ChildPermissionsResponse>

    /** Parent ruft die Kontaktliste eines Kind-Accounts ab. */
    @GET("family/children/{childId}/contacts")
    suspend fun getChildContacts(
        @Path("childId") childId: String
    ): Response<List<ChildContactEntry>>

    /** Parent lädt ein bestehendes Konto ein, Kind-Account zu werden (sendet FCM). */
    @POST("family/invite-existing")
    suspend fun inviteExistingChild(@Body body: FamilyInviteExistingRequest): Response<Map<String, String>>

    // --- STICKER ---

    /** Lädt einen Nutzer-Sticker (GIF) auf den Server hoch. */
    @Multipart
    @POST("stickers/upload")
    suspend fun uploadSticker(
        @Part file: MultipartBody.Part
    ): Response<UserStickerResponse>

    /** Gibt alle Sticker des aktuellen Nutzers zurück. */
    @GET("stickers/my")
    suspend fun getMyStickers(): Response<List<UserStickerResponse>>

    /** Löscht einen Nutzer-Sticker. */
    @DELETE("stickers/{sticker_id}")
    suspend fun deleteSticker(
        @Path("sticker_id") stickerId: String
    ): Response<Map<String, String>>

    /** Gibt Creator-Info für eine Sticker-URL zurück (kein Token nötig). */
    @GET("stickers/info")
    suspend fun getStickerInfo(
        @Query("url") url: String
    ): Response<StickerInfoResponse>

    // ─────────────────────────────────────────────────────────────────────────
    // CREATOR BEWERBUNG
    // ─────────────────────────────────────────────────────────────────────────

    /** Creator-Bewerbung einreichen (kostet 1000 Styx, Alter muss verifiziert sein). */
    @POST("creator/apply")
    suspend fun submitCreatorApplication(
        @Body body: CreatorApplicationRequest
    ): Response<CreatorApplicationResponse>

    // ─────────────────────────────────────────────────────────────────────────
    // ADMIN: CREATOR-BEWERBUNGEN
    // ─────────────────────────────────────────────────────────────────────────

    /** Alle Creator-Bewerbungen auflisten (Admin). */
    @GET("admin/creator-applications")
    suspend fun adminGetCreatorApplications(
        @Header("Authorization") token: String,
        @Query("status") status: String = "all"
    ): Response<List<CreatorApplicationResponse>>

    /** Bewerbung genehmigen (Admin). */
    @PATCH("admin/creator-applications/{applicationId}")
    suspend fun adminReviewCreatorApplication(
        @Header("Authorization") token: String,
        @Path("applicationId") applicationId: String,
        @Query("action") action: String,
        @Query("admin_note") adminNote: String? = null
    ): Response<Map<String, String>>

    // ─────────────────────────────────────────────────────────────────────────
    // ADMIN: LETHE TEAM ANKÜNDIGUNGEN
    // ─────────────────────────────────────────────────────────────────────────

    /** Sendet eine Ankündigung vom Lethe-Team-Account. */
    @POST("admin/announce")
    suspend fun adminAnnounce(
        @Header("Authorization") token: String,
        @Body request: AdminAnnounceRequest
    ): Response<AdminAnnounceResponse>

    // ─────────────────────────────────────────────────────────────────────────
    // ZEITGEPLANTE NACHRICHTEN
    // ─────────────────────────────────────────────────────────────────────────

    @POST("messages/scheduled")
    suspend fun scheduleMessage(
        @Header("Authorization") token: String,
        @Body request: ScheduleMessageRequest
    ): Response<ScheduleMessageResponse>

    @GET("messages/scheduled")
    suspend fun getScheduledMessages(
        @Header("Authorization") token: String
    ): Response<List<ScheduledMessageItem>>

    @DELETE("messages/scheduled/{id}")
    suspend fun cancelScheduledMessage(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<Map<String, String>>

    // --- STRIPE ---

    /** Erstellt einen Stripe PaymentIntent für den Styx-Beutel (6,50 €). */
    @POST("stripe/styx/payment-intent")
    suspend fun createStripePaymentIntent(): Response<StripePaymentIntentResponse>

    /** Bestätigt einen erfolgreichen Stripe-Kauf und schreibt Styx gut. */
    @POST("stripe/styx/confirm")
    suspend fun confirmStripePayment(@Body body: Map<String, String>): Response<Map<String, Any>>

    // --- CREATOR AUSZAHLUNG ---

    /** Beantragt eine Auszahlung der Diamanten per SEPA-Überweisung. */
    @POST("creator/payout")
    suspend fun requestCreatorPayout(@Body body: CreatorPayoutRequest): Response<CreatorPayoutResponse>

    // --- ADMIN: DIAMANTEN-KURS ---

    /** Liest den aktuellen Diamant→Euro-Umrechnungsfaktor. */
    @GET("admin/diamond-rate")
    suspend fun getAdminDiamondRate(@Header("Authorization") token: String): Response<DiamondRateResponse>

    /** Setzt den Diamant→Euro-Umrechnungsfaktor (nur Admin). */
    @PUT("admin/diamond-rate")
    suspend fun setAdminDiamondRate(
        @Header("Authorization") token: String,
        @Body body: DiamondRateRequest
    ): Response<DiamondRateResponse>

    /** Öffentlicher Endpunkt: Creator liest den aktuellen Kurs. */
    @GET("creator/diamond-rate")
    suspend fun getCreatorDiamondRate(): Response<DiamondRateResponse>

    /** Tauscht empfangene Creator-Styx in Diamanten um. */
    @POST("creator/styx-to-diamonds")
    suspend fun convertStyxToDiamonds(@Body body: StyxToDiamondsRequest): Response<StyxToDiamondsResponse>

    /** Überträgt Styx zwischen User-Konto und Creator-Konto. */
    @POST("creator/styx-transfer")
    suspend fun transferStyx(@Body body: StyxTransferRequest): Response<StyxTransferResponse>

    // --- SERVERSEITIGE ANRUF-AUFZEICHNUNG ---

    @POST("calls/recording/start")
    suspend fun startCallRecording(@Body req: RecordingStartRequest): Response<RecordingStartResponse>

    @POST("calls/recording/stop")
    suspend fun stopCallRecording(@Body req: RecordingStopRequest): Response<Map<String, String>>

    @GET("calls/recordings")
    suspend fun listCallRecordings(): Response<List<CallRecordingResponse>>

    @Streaming
    @GET("calls/recording/{id}/download")
    suspend fun downloadCallRecording(
        @Path("id") recordingId: String,
        @Query("which") which: String = "self"
    ): Response<okhttp3.ResponseBody>

}