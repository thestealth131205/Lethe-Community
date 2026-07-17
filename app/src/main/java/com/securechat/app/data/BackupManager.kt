package com.securechat.app.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.securechat.app.data.crypto.CryptoManager
import com.securechat.app.data.local.ContactDao
import com.securechat.app.data.local.ContactEntity
import com.securechat.app.data.local.GroupDao
import com.securechat.app.data.local.GroupEntity
import com.securechat.app.data.local.GroupSenderKeyDao
import com.securechat.app.data.local.GroupSenderKeyEntity
import com.securechat.app.data.local.MessageDao
import com.securechat.app.data.local.MessageEntity
import com.securechat.app.data.local.PollDao
import com.securechat.app.data.local.ProfileManager
import com.securechat.app.data.local.ThemeMode
import com.securechat.app.data.local.AppTheme
import com.securechat.app.data.local.UserDao
import com.securechat.app.data.local.UserEntity
import com.securechat.app.data.local.UserPreferencesRepository
import com.securechat.app.data.network.TokenManager
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import timber.log.Timber
import java.io.*
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Erstellt und importiert vollständige passwortgeschützte Backups (.lethe).
 *
 * Format: AES-256-GCM(ZIP-Archiv)
 *   Datei-Layout: [16-Byte-Salt] [12-Byte-IV] [AES-256-GCM-Ciphertext+Tag]
 *   Schlüsselableitung: PBKDF2WithHmacSHA256 (200.000 Iterationen)
 *
 * ZIP-Inhalt:
 *   manifest.json    – Version, Zeitstempel, App-Infos
 *   user.json        – Eigener Account (UserEntity)
 *   contacts.json    – Alle Kontakte (ContactEntity[])
 *   messages.json    – Alle Nachrichten (MessageEntity[])
 *   groups.json      – Alle Gruppen (GroupEntity[])
 *   group_keys.json  – Gruppen-Sender-Keys (GroupSenderKeyEntity[])
 *   polls.json       – Alle Polls (PollEntity[])
 *   preferences.json – User-Einstellungen
 *   token.json       – Auth-Token + User-ID
 *   credentials.json – Gespeichertes Passwort + App-Lock-PIN
 *   keys.json        – ECDH Private/Public Key (aktuell + ggf. Backup-Blob vom Server)
 *   database.db      – Room-Datenbank (Binärkopie)
 *   media/...        – Alle gecachten Medien (Bilder, Videos, Audio)
 */
object BackupManager {

    private const val TAG = "BackupManager"
    private const val PBKDF2_ITERATIONS = 200_000
    private const val AES_KEY_SIZE = 256
    private const val GCM_IV_SIZE = 12
    private const val GCM_TAG_BITS = 128
    private const val SALT_SIZE = 16
    private const val BACKUP_VERSION = 2

    private val gson = Gson()

    enum class BackupDestination {
        LOCAL, GOOGLE_DRIVE, NEXTCLOUD
    }

    // ─── EXPORT ─────────────────────────────────────────────────────────────

    suspend fun createBackup(
        context: Context,
        password: String,
        outputUri: Uri,
        userDao: UserDao,
        contactDao: ContactDao,
        messageDao: MessageDao,
        groupDao: GroupDao,
        groupSenderKeyDao: GroupSenderKeyDao,
        pollDao: PollDao,
        tokenManager: TokenManager,
        userPreferencesRepository: UserPreferencesRepository,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        return try {
            onProgress(0.02f)

            // 1) Daten sammeln
            val user = userDao.getCurrentUser()
            val contacts = contactDao.getAllContacts().first()
            val messages = collectAllMessages(messageDao, contacts, user)
            val groups = groupDao.getAllGroups().first()
            val groupKeys = collectGroupKeys(groupSenderKeyDao, groups)
            val preferences = userPreferencesRepository.userPreferencesFlow.first()

            onProgress(0.1f)

            // 2) ZIP in Temp-Datei erstellen (statt Memory – wegen Medien)
            val tempZip = File(context.cacheDir, "backup_temp.zip")
            try {
                ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip))).use { zip ->
                    // Manifest
                    val manifest = mapOf(
                        "version" to BACKUP_VERSION,
                        "app" to "Lethe",
                        "timestamp" to System.currentTimeMillis(),
                        "userAgent" to "Android"
                    )
                    writeZipEntry(zip, "manifest.json", gson.toJson(manifest))
                    onProgress(0.12f)

                    // User
                    if (user != null) {
                        writeZipEntry(zip, "user.json", gson.toJson(user))
                    }
                    onProgress(0.14f)

                    // Contacts
                    writeZipEntry(zip, "contacts.json", gson.toJson(contacts))
                    onProgress(0.16f)

                    // Messages
                    writeZipEntry(zip, "messages.json", gson.toJson(messages))
                    onProgress(0.2f)

                    // Groups
                    writeZipEntry(zip, "groups.json", gson.toJson(groups))
                    onProgress(0.22f)

                    // Group Sender Keys
                    writeZipEntry(zip, "group_keys.json", gson.toJson(groupKeys))
                    onProgress(0.24f)

                    // Polls
                    writeZipEntry(zip, "polls.json", "[]")
                    onProgress(0.25f)

                    // Preferences
                    val prefsMap = mapOf(
                        "themeMode" to preferences.themeMode.name,
                        "primaryColor" to preferences.primaryColor,
                        "accentColor" to preferences.accentColor,
                        "bubbleColor" to preferences.bubbleColor,
                        "bubbleColorPartner" to preferences.bubbleColorPartner,
                        "notificationsEnabled" to preferences.notificationsEnabled,
                        "language" to preferences.language,
                        "vibrationEnabled" to preferences.vibrationEnabled,
                        "soundEnabled" to preferences.soundEnabled,
                        "fontSizeMultiplier" to preferences.fontSizeMultiplier,
                        "autoDownloadMedia" to preferences.autoDownloadMedia,
                        "showOnlineStatus" to preferences.showOnlineStatus,
                        "readReceiptsEnabled" to preferences.readReceiptsEnabled,
                        "statusVisible" to preferences.statusVisible,
                        "e2eeEnabled" to preferences.e2eeEnabled,
                        "datingRadiusKm" to preferences.datingRadiusKm,
                        "nearbyGenderFilter" to preferences.nearbyGenderFilter,
                        "nearbyAgeMin" to preferences.nearbyAgeMin,
                        "nearbyAgeMax" to preferences.nearbyAgeMax,
                        "nearbyFriendshipOnly" to preferences.nearbyFriendshipOnly,
                        "readReceiptAfterReply" to preferences.readReceiptAfterReply,
                        "chatSoundReceiveEnabled" to preferences.chatSoundReceiveEnabled,
                        "chatSoundSendEnabled" to preferences.chatSoundSendEnabled,
                        "barColor" to preferences.barColor,
                        "backgroundColor" to preferences.backgroundColor,
                        "autoLoginEnabled" to preferences.autoLoginEnabled,
                        "notificationSound" to preferences.notificationSound,
                        "appTheme" to preferences.appTheme.name,
                        "bubbleColor2" to preferences.bubbleColor2,
                        "bubbleColorPartner2" to preferences.bubbleColorPartner2,
                        "focusBorderColor" to preferences.focusBorderColor,
                        "focusBorderColor2" to preferences.focusBorderColor2,
                        "avatarSizeMultiplier" to preferences.avatarSizeMultiplier,
                        "torMode" to preferences.torMode,
                        "onionAddress" to preferences.onionAddress,
                        "homeServerUrl" to preferences.homeServerUrl,
                        "decentralizedMode" to preferences.decentralizedMode,
                        "appLockBiometricEnabled" to preferences.appLockBiometricEnabled,
                        "hiddenNavItems" to preferences.hiddenNavItems.toList(),
                        "chatBackgroundUri" to preferences.chatBackgroundUri,
                        "enterToSend" to preferences.enterToSend
                    )
                    writeZipEntry(zip, "preferences.json", gson.toJson(prefsMap))
                    onProgress(0.27f)

                    // Token
                    val tokenData = mapOf(
                        "authToken" to (tokenManager.getToken() ?: ""),
                        "userId" to (tokenManager.getUserId() ?: "")
                    )
                    writeZipEntry(zip, "token.json", gson.toJson(tokenData))
                    onProgress(0.28f)

                    // Credentials
                    val credData = mapOf(
                        "password" to userPreferencesRepository.getStoredPassword(),
                        "appLockPin" to userPreferencesRepository.getAppLockPinValue()
                    )
                    writeZipEntry(zip, "credentials.json", gson.toJson(credData))
                    onProgress(0.29f)

                    // Keys
                    val keysMap = mutableMapOf<String, String>()
                    if (user != null) {
                        keysMap["publicKey"] = user.publicKey
                        user.privateKey?.let { keysMap["privateKey"] = it }
                    }
                    CryptoManager.exportSoftPrivateKey()?.let { keysMap["softPrivateKey"] = it }
                    writeZipEntry(zip, "keys.json", gson.toJson(keysMap))
                    onProgress(0.3f)

                    // --- Room-Datenbank (Binärkopie) ---
                    addDatabaseToZip(context, zip)
                    onProgress(0.4f)

                    // --- Medien-Dateien (media_cache/) ---
                    addMediaToZip(context, zip) { mediaProgress ->
                        // mediaProgress: 0..1 → mapped auf 0.4..0.9
                        onProgress(0.4f + mediaProgress * 0.5f)
                    }
                    onProgress(0.9f)
                }

                // 3) ZIP-Datei verschlüsseln mit AES-256-GCM (Streaming)
                val tempEncrypted = File(context.cacheDir, "backup_temp.enc")
                try {
                    encryptFileWithPassword(password, tempZip, tempEncrypted)
                    onProgress(0.95f)

                    // 4) In die vom User gewählte URI schreiben
                    context.contentResolver.openOutputStream(outputUri)?.use { out ->
                        FileInputStream(tempEncrypted).use { input ->
                            input.copyTo(out, bufferSize = 8192)
                        }
                    } ?: return Result.failure(Exception("Kann Datei nicht öffnen"))

                    onProgress(1f)
                    val sizeMb = tempEncrypted.length() / (1024.0 * 1024.0)
                    Timber.tag(TAG).i("Backup erstellt: %.1f MB", sizeMb)
                    Result.success("Backup erfolgreich erstellt (%.1f MB)".format(sizeMb))
                } finally {
                    tempEncrypted.delete()
                }
            } finally {
                tempZip.delete()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Backup fehlgeschlagen")
            Result.failure(e)
        }
    }

    // ─── IMPORT ─────────────────────────────────────────────────────────────

    suspend fun restoreBackup(
        context: Context,
        password: String,
        inputUri: Uri,
        userDao: UserDao,
        contactDao: ContactDao,
        messageDao: MessageDao,
        groupDao: GroupDao,
        groupSenderKeyDao: GroupSenderKeyDao,
        pollDao: PollDao,
        tokenManager: TokenManager,
        userPreferencesRepository: UserPreferencesRepository,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        return try {
            onProgress(0.05f)

            // 1) Datei lesen und entschlüsseln
            val tempEncrypted = File(context.cacheDir, "restore_temp.enc")
            val tempZip = File(context.cacheDir, "restore_temp.zip")
            try {
                context.contentResolver.openInputStream(inputUri)?.use { input ->
                    FileOutputStream(tempEncrypted).use { out ->
                        input.copyTo(out, bufferSize = 8192)
                    }
                } ?: return Result.failure(Exception("Kann Datei nicht öffnen"))

                onProgress(0.1f)
                val decrypted = decryptFileWithPassword(password, tempEncrypted)
                    ?: return Result.failure(Exception("Falsches Passwort oder beschädigte Datei"))
                FileOutputStream(tempZip).use { it.write(decrypted) }

                onProgress(0.2f)

                // 2) ZIP entpacken
                val jsonEntries = mutableMapOf<String, String>()
                ZipInputStream(BufferedInputStream(FileInputStream(tempZip))).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val name = entry.name
                            when {
                                name == "database.db" -> {
                                    // Room-DB wiederherstellen
                                    restoreDatabaseFromZip(context, zip)
                                }
                                name == "database.db-wal" -> {
                                    // WAL-Datei wiederherstellen (neuere Backups)
                                    restoreWalFromZip(context, zip)
                                }
                                name.startsWith("media/") -> {
                                    // Mediendatei wiederherstellen
                                    restoreMediaFileFromZip(context, name, zip)
                                }
                                name.endsWith(".json") -> {
                                    jsonEntries[name] = zip.bufferedReader().readText()
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                onProgress(0.4f)

                // Manifest prüfen
                val manifestJson = jsonEntries["manifest.json"]
                    ?: return Result.failure(Exception("Ungültiges Backup: manifest.json fehlt"))
                val manifest: Map<String, Any> = gson.fromJson(manifestJson, object : TypeToken<Map<String, Any>>() {}.type)
                if (manifest["app"] != "Lethe") {
                    return Result.failure(Exception("Ungültiges Backup: keine Lethe-Datei"))
                }

                onProgress(0.45f)

                // 3) JSON-Daten wiederherstellen
                // User
                jsonEntries["user.json"]?.let { json ->
                    val user = gson.fromJson(json, UserEntity::class.java)
                    userDao.insertUser(user)
                }
                onProgress(0.5f)

                // Contacts
                jsonEntries["contacts.json"]?.let { json ->
                    val contacts: List<ContactEntity> = gson.fromJson(json, object : TypeToken<List<ContactEntity>>() {}.type)
                    contacts.forEach { contactDao.insertContact(it) }
                }
                onProgress(0.55f)

                // Messages
                jsonEntries["messages.json"]?.let { json ->
                    val messages: List<MessageEntity> = gson.fromJson(json, object : TypeToken<List<MessageEntity>>() {}.type)
                    messages.forEach { msg ->
                        messageDao.insertMessage(msg.copy(localId = 0))
                    }
                }
                onProgress(0.65f)

                // Groups
                jsonEntries["groups.json"]?.let { json ->
                    val groups: List<GroupEntity> = gson.fromJson(json, object : TypeToken<List<GroupEntity>>() {}.type)
                    groups.forEach { groupDao.insertGroup(it) }
                }
                onProgress(0.7f)

                // Group Sender Keys
                jsonEntries["group_keys.json"]?.let { json ->
                    val keys: List<GroupSenderKeyEntity> = gson.fromJson(json, object : TypeToken<List<GroupSenderKeyEntity>>() {}.type)
                    keys.forEach { groupSenderKeyDao.insertKey(it) }
                }
                onProgress(0.75f)

                // Token
                jsonEntries["token.json"]?.let { json ->
                    val data: Map<String, String> = gson.fromJson(json, object : TypeToken<Map<String, String>>() {}.type)
                    val token = data["authToken"] ?: ""
                    val userId = data["userId"] ?: ""
                    if (token.isNotBlank() && userId.isNotBlank()) {
                        tokenManager.saveToken(token, userId)
                    }
                }
                onProgress(0.8f)

                // Credentials
                jsonEntries["credentials.json"]?.let { json ->
                    val data: Map<String, String> = gson.fromJson(json, object : TypeToken<Map<String, String>>() {}.type)
                    data["password"]?.takeIf { it.isNotBlank() }?.let { userPreferencesRepository.setStoredPassword(it) }
                    data["appLockPin"]?.takeIf { it.isNotBlank() }?.let { userPreferencesRepository.setAppLockPinValue(it) }
                }
                onProgress(0.85f)

                // Keys
                jsonEntries["keys.json"]?.let { json ->
                    val keysMap: Map<String, String> = gson.fromJson(json, object : TypeToken<Map<String, String>>() {}.type)
                    val privKey = keysMap["privateKey"] ?: keysMap["softPrivateKey"]
                    val pubKey = keysMap["publicKey"]
                    if (privKey != null && pubKey != null) {
                        CryptoManager.ensureKeyPair(context, privKey, pubKey)
                        val user = userDao.getCurrentUser()
                        if (user != null) {
                            userDao.insertUser(user.copy(privateKey = privKey, publicKey = pubKey))
                        }
                    }
                }
                onProgress(0.9f)

                // Preferences
                jsonEntries["preferences.json"]?.let { json ->
                    restorePreferences(json, userPreferencesRepository)
                }
                onProgress(1f)

                Timber.tag(TAG).i("Backup erfolgreich wiederhergestellt")
                Result.success("Backup erfolgreich wiederhergestellt")
            } finally {
                tempEncrypted.delete()
                tempZip.delete()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Restore fehlgeschlagen")
            Result.failure(e)
        }
    }

    // ─── DATABASE BACKUP ────────────────────────────────────────────────────

    private fun addDatabaseToZip(context: Context, zip: ZipOutputStream) {
        val dbName = ProfileManager.dbName(context)
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) return

        // DB-Hauptdatei in ZIP schreiben
        // Kein externer WAL-Checkpoint: eine zweite SQLiteDatabase-Verbindung auf der Room-DB
        // kann den WAL-State von Room korrumpieren und führt zu Datenverlust.
        // Stattdessen DB + WAL zusammen sichern – SQLite replayed die WAL beim Öffnen automatisch.
        zip.putNextEntry(ZipEntry("database.db"))
        FileInputStream(dbFile).use { it.copyTo(zip, bufferSize = 8192) }
        zip.closeEntry()

        // WAL-Datei ebenfalls sichern, damit keine Daten verloren gehen
        val walFile = File(dbFile.absolutePath + "-wal")
        if (walFile.exists() && walFile.length() > 32) {
            zip.putNextEntry(ZipEntry("database.db-wal"))
            FileInputStream(walFile).use { it.copyTo(zip, bufferSize = 8192) }
            zip.closeEntry()
        }
    }

    private fun restoreDatabaseFromZip(context: Context, zipInput: ZipInputStream) {
        val dbName = ProfileManager.dbName(context)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // WAL und SHM löschen falls vorhanden
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()

        FileOutputStream(dbFile).use { out ->
            zipInput.copyTo(out, bufferSize = 8192)
        }
    }

    private fun restoreWalFromZip(context: Context, zipInput: ZipInputStream) {
        val dbName = ProfileManager.dbName(context)
        val walFile = File(context.getDatabasePath(dbName).absolutePath + "-wal")
        walFile.parentFile?.mkdirs()
        FileOutputStream(walFile).use { out ->
            zipInput.copyTo(out, bufferSize = 8192)
        }
    }

    // ─── MEDIA BACKUP ───────────────────────────────────────────────────────

    private fun addMediaToZip(context: Context, zip: ZipOutputStream, onProgress: (Float) -> Unit) {
        val mediaRoot = File(context.filesDir, "media_cache")
        if (!mediaRoot.exists() || !mediaRoot.isDirectory) {
            onProgress(1f)
            return
        }

        // Alle Dateien sammeln
        val allFiles = mediaRoot.walkTopDown().filter { it.isFile }.toList()
        if (allFiles.isEmpty()) {
            onProgress(1f)
            return
        }

        val totalSize = allFiles.sumOf { it.length() }.coerceAtLeast(1L)
        var processedSize = 0L

        for (file in allFiles) {
            val relativePath = file.relativeTo(context.filesDir).path // media_cache/...
            zip.putNextEntry(ZipEntry("media/$relativePath"))
            FileInputStream(file).use { it.copyTo(zip, bufferSize = 8192) }
            zip.closeEntry()

            processedSize += file.length()
            onProgress(processedSize.toFloat() / totalSize)
        }
    }

    private fun restoreMediaFileFromZip(context: Context, zipEntryName: String, zipInput: ZipInputStream) {
        // zipEntryName: "media/media_cache/chatId/images/file.jpg"
        val relativePath = zipEntryName.removePrefix("media/") // → "media_cache/chatId/images/file.jpg"
        val targetFile = File(context.filesDir, relativePath)
        targetFile.parentFile?.mkdirs()

        FileOutputStream(targetFile).use { out ->
            zipInput.copyTo(out, bufferSize = 8192)
        }
    }

    // ─── CLOUD UPLOAD ───────────────────────────────────────────────────────

    fun uploadToNextcloud(
        backupFile: File,
        serverUrl: String,
        username: String,
        password: String,
        remotePath: String = "/Lethe-Backups/"
    ): Result<String> {
        return try {
            val client = OkHttpClient.Builder()
                .authenticator { _, response ->
                    val credential = okhttp3.Credentials.basic(username, password)
                    response.request.newBuilder().header("Authorization", credential).build()
                }
                .build()

            // Ordner erstellen (MKCOL, Fehler ignorieren falls existiert)
            val mkcolUrl = serverUrl.trimEnd('/') + "/remote.php/dav/files/$username${remotePath}"
            val mkcolRequest = Request.Builder()
                .url(mkcolUrl)
                .method("MKCOL", null)
                .header("Authorization", okhttp3.Credentials.basic(username, password))
                .build()
            try { client.newCall(mkcolRequest).execute().close() } catch (_: Exception) {}

            // Datei hochladen (PUT)
            val uploadUrl = mkcolUrl + backupFile.name
            val request = Request.Builder()
                .url(uploadUrl)
                .put(backupFile.asRequestBody("application/octet-stream".toMediaType()))
                .header("Authorization", okhttp3.Credentials.basic(username, password))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful || response.code == 201 || response.code == 204) {
                Result.success("Backup auf Nextcloud hochgeladen")
            } else {
                Result.failure(Exception("Nextcloud-Upload fehlgeschlagen: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Nextcloud-Upload fehlgeschlagen")
            Result.failure(e)
        }
    }

    // ─── CRYPTO ─────────────────────────────────────────────────────────────

    private fun encryptFileWithPassword(password: String, inputFile: File, outputFile: File) {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val aesKey = deriveKey(password, salt)
        val iv = ByteArray(GCM_IV_SIZE).also { SecureRandom().nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))

        val plainData = inputFile.readBytes()
        val ciphertext = cipher.doFinal(plainData)

        FileOutputStream(outputFile).use { out ->
            out.write(salt)
            out.write(iv)
            out.write(ciphertext)
        }
    }

    private fun decryptFileWithPassword(password: String, encryptedFile: File): ByteArray? {
        return try {
            val encryptedData = encryptedFile.readBytes()
            if (encryptedData.size < SALT_SIZE + GCM_IV_SIZE + 16) return null
            val salt = encryptedData.copyOf(SALT_SIZE)
            val iv = encryptedData.copyOfRange(SALT_SIZE, SALT_SIZE + GCM_IV_SIZE)
            val ciphertext = encryptedData.copyOfRange(SALT_SIZE + GCM_IV_SIZE, encryptedData.size)

            val aesKey = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Timber.tag(TAG).w("Entschlüsselung fehlgeschlagen: ${e.message}")
            null
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_SIZE)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return key
    }

    // ─── HELPERS ────────────────────────────────────────────────────────────

    private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private suspend fun collectAllMessages(
        messageDao: MessageDao,
        contacts: List<ContactEntity>,
        user: UserEntity?
    ): List<MessageEntity> {
        if (user == null) return emptyList()
        val allMessages = mutableListOf<MessageEntity>()
        val chatIds = contacts.map { it.userId }.toMutableSet()
        chatIds.add(user.userId)
        for (chatId in chatIds) {
            try {
                val messages = messageDao.getMessagesForChat(chatId, Int.MAX_VALUE).first()
                allMessages.addAll(messages)
            } catch (_: Exception) {}
        }
        return allMessages
    }

    private suspend fun collectGroupKeys(
        groupSenderKeyDao: GroupSenderKeyDao,
        groups: List<GroupEntity>
    ): List<GroupSenderKeyEntity> {
        val allKeys = mutableListOf<GroupSenderKeyEntity>()
        for (group in groups) {
            try {
                allKeys.addAll(groupSenderKeyDao.getKeysForGroup(group.groupId))
            } catch (_: Exception) {}
        }
        return allKeys
    }

    private suspend fun restorePreferences(json: String, repo: UserPreferencesRepository) {
        try {
            val map: Map<String, Any> = gson.fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)

            (map["themeMode"] as? String)?.let {
                try { repo.updateThemeMode(ThemeMode.valueOf(it)) } catch (_: Exception) {}
            }
            (map["primaryColor"] as? Number)?.let { repo.updatePrimaryColor(it.toInt()) }
            (map["accentColor"] as? Number)?.let { repo.updateAccentColor(it.toInt()) }
            (map["bubbleColor"] as? Number)?.let { repo.updateBubbleColor(it.toInt()) }
            (map["bubbleColorPartner"] as? Number)?.let { repo.updateBubbleColorPartner(it.toInt()) }
            (map["notificationsEnabled"] as? Boolean)?.let { repo.setNotificationsEnabled(it) }
            (map["vibrationEnabled"] as? Boolean)?.let { repo.setVibrationEnabled(it) }
            (map["soundEnabled"] as? Boolean)?.let { repo.setSoundEnabled(it) }
            (map["fontSizeMultiplier"] as? Number)?.let { repo.setFontSize(it.toFloat()) }
            (map["readReceiptsEnabled"] as? Boolean)?.let { repo.setReadReceipts(it) }
            (map["showOnlineStatus"] as? Boolean)?.let { repo.setShowOnlineStatus(it) }
            (map["statusVisible"] as? Boolean)?.let { repo.setStatusVisible(it) }
            (map["enterToSend"] as? Boolean)?.let { repo.setEnterToSend(it) }
        } catch (e: Exception) {
            Timber.tag(TAG).w("Preferences-Wiederherstellung teilweise fehlgeschlagen: ${e.message}")
        }
    }
}
