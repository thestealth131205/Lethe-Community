package com.securechat.app.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Globaler DataStore für Anmelde-Credentials (profilunabhängig).
 * Hält nur fake_number/password/remember_me damit Login ohne Profil funktioniert.
 */
private val Context.globalDataStore: DataStore<Preferences> by preferencesDataStore(name = "securechat_global_credentials")

/**
 * Enum für die Design-Modi der Anwendung.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Enum für das UI-Thema: flaches Material-Design oder transparentes Glossy-Morph-Design.
 */
enum class AppTheme { MATERIAL, GLOSSY_MORPH }

/**
 * Die zentrale Datenklasse für alle Benutzereinstellungen.
 * Beinhaltet Design, Sicherheit, Privatsphäre und Medienoptionen.
 * Alle Felder haben Standardwerte für den stabilen Erststart.
 */
data class UserPreferences(
    val fakeNumber: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,
    val biometricEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColor: Int = 0xFFA8A800.toInt(),  // Neon-Gelb abgedunkelt (66% Value)
    val accentColor: Int = 0xFF1A1A1A.toInt(),   // Fast-Schwarz als Kontrast
    val bubbleColor: Int = 0xFFC0DCF0.toInt(),   // Pastelblau für eigene Nachrichten
    val bubbleColorPartner: Int = 0xFFFFFFFF.toInt(), // Weiß für Partnernachrichten (hell) / bleibt dunkel je nach Theme
    val notificationsEnabled: Boolean = true,
    val language: String = "de",
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val fontSizeMultiplier: Float = 1.0f,
    val autoDownloadMedia: Boolean = false,
    val showOnlineStatus: Boolean = true,
    val readReceiptsEnabled: Boolean = true,
    val statusVisible: Boolean = true,
    val lastSyncTimestamp: Long = 0L,
    val isFirstStart: Boolean = true,
    val e2eeEnabled: Boolean = true,
    val datingRadiusKm: Float = 50f,
    val nearbyGenderFilter: String = "ALL",
    val nearbyAgeMin: Int = 18,
    val nearbyAgeMax: Int = 99,
    val nearbyFriendshipOnly: Boolean = false,
    val readReceiptAfterReply: Boolean = false,
    val chatSoundReceiveEnabled: Boolean = true,   // In-Chat Sound beim Empfangen
    val chatSoundSendEnabled: Boolean = true,      // In-Chat Sound beim Senden
    val barColor: Int = 0,                         // 0 = Theme-Standard (kein Custom-Override)
    val backgroundColor: Int = 0,                 // 0 = Theme-Standard (kein Custom-Override)
    val autoLoginEnabled: Boolean = false,         // Automatisch einloggen beim App-Start
    val notificationSound: String = "default",     // Ausgewählter Benachrichtigungston: "default" | "pocker"
    val appTheme: AppTheme = AppTheme.MATERIAL,    // UI-Thema: Material oder Glossy Morph
    val bubbleColor2: Int = 0xFFC0DCF0.toInt(),    // Zweite Verlaufsfarbe eigene Blase (Glossy Morph)
    val bubbleColorPartner2: Int = 0xFFFFFFFF.toInt(), // Zweite Verlaufsfarbe Partner-Blase (Glossy Morph)
    val focusBorderColor: Int = 0xFFC0DCF0.toInt(),  // Farbe 1 Textfeld-Fokusrahmen
    val focusBorderColor2: Int = 0xFFC0DCF0.toInt(), // Farbe 2 Textfeld-Fokusrahmen (Verlauf)
    val avatarSizeMultiplier: Float = 1.0f,          // Profilbild-Größe: 1.0=Standard, 1.03=Größer, 1.05=Groß
    val torMode: String = "OFF",                     // TorMode enum: "OFF" | "TOR_ONLY"
    val onionAddress: String = "",                   // .onion-Hostname des Backends (leer = nicht konfiguriert)
    val homeServerUrl: String = "",                  // Optionaler zweiter Server (z.B. Home-Server), Fallback bei VPS-Ausfall
    val autoBackupUrl: String = "",                  // Automatisch vom Server abgerufene Backup-URL (täglich aktualisiert)
    val lastBackupUrlFetchMs: Long = 0L,             // Zeitstempel des letzten Abrufs (Epoch ms)
    val decentralizedMode: Boolean = false,          // Dezentrales P2P-Mesh-Messaging (kein Server)
    val appLockBiometricEnabled: Boolean = false,    // App-Sperre per Fingerabdruck/Gesicht (PIN als Fallback)
    val hiddenNavItems: Set<String> = emptySet(),    // Ausgeblendete BottomBar-Einträge (z.B. "nearby", "sparks", "vip", "creator")
    val chatBackgroundUri: String = "",              // Globales Chat-Hintergrundbild: "" | "preset:N" | "content://..."
    val enterToSend: Boolean = false,                // Enter = Nachricht senden (statt neuer Absatz)
    val p2pInternetEnabled: Boolean = false,          // P2P über Internet (WebRTC DataChannel)
    val p2pDisabledContacts: Set<String> = emptySet(), // Kontakte (userId), für die P2P lokal deaktiviert wurde
    val contactsAppIntegration: Boolean = false,       // Lethe unter "Verbundene Apps" in der System-Kontakte-App zeigen
    val audioQuality: String = "AUTO",                 // Allgemeine Audio-Qualität in Anrufen: "AUTO" | "LOW" | "HIGH"
    val audioOutputChannel: String = "SYSTEM",         // Genutzter Audio-Ausgang: "SYSTEM" | "EARPIECE" | "SPEAKER" | "BLUETOOTH"
    val bluetoothHeadsetEnabled: Boolean = true,       // Bluetooth-Headset-Unterstützung (HFP) in Anrufen
)

/**
 * Repository zur Verwaltung von persistenten Benutzereinstellungen mittels Jetpack DataStore.
 * Einstellungen (Theme, Farben, etc.) werden profilspezifisch gespeichert.
 * Login-Credentials liegen im globalen DataStore.
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptedCredentialStore: EncryptedCredentialStore
) {
    /**
     * Profilspezifischer DataStore – Dateiname enthält die Fake-Nummer.
     * Wird beim ersten Zugriff erzeugt; der Name ändert sich nur nach App-Neustart.
     */
    private val profileDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = {
            File(context.filesDir, "datastore/${ProfileManager.dataStoreName(context)}.preferences_pb")
        }
    )

    // --- PREFERENCE KEYS ---
    private val FAKE_NUMBER = stringPreferencesKey("fake_number")
    private val PASSWORD = stringPreferencesKey("password")
    private val REMEMBER_ME = booleanPreferencesKey("remember_me")
    private val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    private val THEME_MODE = stringPreferencesKey("theme_mode")
    private val PRIMARY_COLOR = intPreferencesKey("primary_color")
    private val ACCENT_COLOR = intPreferencesKey("accent_color")
    private val BUBBLE_COLOR = intPreferencesKey("bubble_color")
    private val BUBBLE_COLOR_PARTNER = intPreferencesKey("bubble_color_partner")
    private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    private val LANGUAGE = stringPreferencesKey("app_language")
    private val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
    private val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    private val FONT_SIZE = floatPreferencesKey("font_size")
    private val AUTO_DOWNLOAD = booleanPreferencesKey("auto_download")
    private val SHOW_ONLINE = booleanPreferencesKey("show_online")
    private val READ_RECEIPTS = booleanPreferencesKey("read_receipts")
    private val STATUS_VISIBLE = booleanPreferencesKey("status_visible")
    private val SYNC_TIME = longPreferencesKey("sync_time")
    private val FIRST_START = booleanPreferencesKey("first_start")
    private val E2EE_ACTIVE = booleanPreferencesKey("e2ee_active")
    private val DATING_RADIUS = floatPreferencesKey("dating_radius_km")
    private val NEARBY_GENDER_FILTER = stringPreferencesKey("nearby_gender_filter")
    private val NEARBY_AGE_MIN = intPreferencesKey("nearby_age_min")
    private val NEARBY_AGE_MAX = intPreferencesKey("nearby_age_max")
    private val NEARBY_FRIENDSHIP_ONLY = booleanPreferencesKey("nearby_friendship_only")
    private val READ_RECEIPT_AFTER_REPLY = booleanPreferencesKey("read_receipt_after_reply")
    private val CHAT_SOUND_RECEIVE = booleanPreferencesKey("chat_sound_receive")
    private val CHAT_SOUND_SEND = booleanPreferencesKey("chat_sound_send")
    private val BAR_COLOR = intPreferencesKey("bar_color")
    private val BACKGROUND_COLOR = intPreferencesKey("background_color")
    private val AUTO_LOGIN = booleanPreferencesKey("auto_login_enabled")
    private val NOTIFICATION_SOUND = stringPreferencesKey("notification_sound")
    private val COMPLETED_ONBOARDING_STEPS = stringSetPreferencesKey("completed_onboarding_steps")
    private val APP_THEME = stringPreferencesKey("app_theme")
    private val BUBBLE_COLOR_2 = intPreferencesKey("bubble_color_2")
    private val BUBBLE_COLOR_PARTNER_2 = intPreferencesKey("bubble_color_partner_2")
    private val FOCUS_BORDER_COLOR = intPreferencesKey("focus_border_color")
    private val FOCUS_BORDER_COLOR_2 = intPreferencesKey("focus_border_color_2")
    private val AVATAR_SIZE = floatPreferencesKey("avatar_size_multiplier")
    private val TOR_MODE = stringPreferencesKey("tor_mode")
    private val ONION_ADDRESS = stringPreferencesKey("onion_address")
    private val HOME_SERVER_URL = stringPreferencesKey("home_server_url")
    private val AUTO_BACKUP_URL = stringPreferencesKey("auto_backup_url")
    private val LAST_BACKUP_URL_FETCH = longPreferencesKey("last_backup_url_fetch_ms")
    private val DECENTRALIZED_MODE = booleanPreferencesKey("decentralized_mode")
    private val NEARBY_QUESTIONS_SENT = intPreferencesKey("nearby_questions_sent_count")
    private val APP_LOCK_BIOMETRIC = booleanPreferencesKey("app_lock_biometric_enabled")
    private val HIDDEN_NAV_ITEMS = stringSetPreferencesKey("hidden_nav_items")
    private val CHAT_BACKGROUND_URI = stringPreferencesKey("chat_background_uri")
    private val ENTER_TO_SEND = booleanPreferencesKey("enter_to_send")
    private val P2P_INTERNET_ENABLED = booleanPreferencesKey("p2p_internet_enabled")
    private val P2P_DISABLED_CONTACTS = stringSetPreferencesKey("p2p_disabled_contacts")
    private val CONTACTS_APP_INTEGRATION = booleanPreferencesKey("contacts_app_integration")
    private val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
    private val AUDIO_OUTPUT_CHANNEL = stringPreferencesKey("audio_output_channel")
    private val BLUETOOTH_HEADSET_ENABLED = booleanPreferencesKey("bluetooth_headset_enabled")


    /**
     * Liefert den Flow der Benutzereinstellungen.
     * Credentials kommen aus dem globalen Store, Einstellungen aus dem profilspezifischen.
     */
    val userPreferencesFlow: Flow<UserPreferences> = profileDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e("UserPrefs", "Kritischer Fehler beim Lesen der DataStore-Datei.", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val themeString = preferences[THEME_MODE] ?: ThemeMode.SYSTEM.name
            UserPreferences(
                fakeNumber = preferences[FAKE_NUMBER] ?: "",
                password = encryptedCredentialStore.getPassword(),
                rememberMe = preferences[REMEMBER_ME] ?: false,
                biometricEnabled = preferences[BIOMETRIC_ENABLED] ?: false,
                themeMode = try { ThemeMode.valueOf(themeString) } catch (e: Exception) { ThemeMode.SYSTEM },
                primaryColor = preferences[PRIMARY_COLOR] ?: 0xFFA8A800.toInt(),
                accentColor = preferences[ACCENT_COLOR] ?: 0xFF1A1A1A.toInt(),
                bubbleColor = preferences[BUBBLE_COLOR] ?: 0xFFC0DCF0.toInt(),
                bubbleColorPartner = preferences[BUBBLE_COLOR_PARTNER] ?: 0xFFFFFFFF.toInt(),
                notificationsEnabled = preferences[NOTIFICATIONS_ENABLED] ?: true,
                language = preferences[LANGUAGE] ?: "de",
                vibrationEnabled = preferences[VIBRATION_ENABLED] ?: true,
                soundEnabled = preferences[SOUND_ENABLED] ?: true,
                fontSizeMultiplier = preferences[FONT_SIZE] ?: 1.0f,
                autoDownloadMedia = preferences[AUTO_DOWNLOAD] ?: false,
                showOnlineStatus = preferences[SHOW_ONLINE] ?: true,
                readReceiptsEnabled = preferences[READ_RECEIPTS] ?: true,
                statusVisible = preferences[STATUS_VISIBLE] ?: true,
                lastSyncTimestamp = preferences[SYNC_TIME] ?: 0L,
                isFirstStart = preferences[FIRST_START] ?: true,
                e2eeEnabled = preferences[E2EE_ACTIVE] ?: true,
                datingRadiusKm = preferences[DATING_RADIUS] ?: 50f,
                nearbyGenderFilter = preferences[NEARBY_GENDER_FILTER] ?: "ALL",
                nearbyAgeMin = preferences[NEARBY_AGE_MIN] ?: 18,
                nearbyAgeMax = preferences[NEARBY_AGE_MAX] ?: 99,
                nearbyFriendshipOnly = preferences[NEARBY_FRIENDSHIP_ONLY] ?: false,
                readReceiptAfterReply = preferences[READ_RECEIPT_AFTER_REPLY] ?: false,
                chatSoundReceiveEnabled = preferences[CHAT_SOUND_RECEIVE] ?: true,
                chatSoundSendEnabled = preferences[CHAT_SOUND_SEND] ?: true,
                barColor = preferences[BAR_COLOR] ?: 0,
                backgroundColor = preferences[BACKGROUND_COLOR] ?: 0,
                autoLoginEnabled = preferences[AUTO_LOGIN] ?: false,
                notificationSound = preferences[NOTIFICATION_SOUND] ?: "default",
                appTheme = try { AppTheme.valueOf(preferences[APP_THEME] ?: AppTheme.MATERIAL.name) } catch (e: Exception) { AppTheme.MATERIAL },
                bubbleColor2 = preferences[BUBBLE_COLOR_2] ?: (preferences[BUBBLE_COLOR] ?: 0xFFC0DCF0.toInt()),
                bubbleColorPartner2 = preferences[BUBBLE_COLOR_PARTNER_2] ?: (preferences[BUBBLE_COLOR_PARTNER] ?: 0xFFFFFFFF.toInt()),
                focusBorderColor = preferences[FOCUS_BORDER_COLOR] ?: 0xFFC0DCF0.toInt(),
                focusBorderColor2 = preferences[FOCUS_BORDER_COLOR_2] ?: (preferences[FOCUS_BORDER_COLOR] ?: 0xFFC0DCF0.toInt()),
                avatarSizeMultiplier = preferences[AVATAR_SIZE] ?: 1.0f,
                torMode = preferences[TOR_MODE] ?: "OFF",
                onionAddress = preferences[ONION_ADDRESS] ?: "",
                homeServerUrl = preferences[HOME_SERVER_URL] ?: "",
                autoBackupUrl = preferences[AUTO_BACKUP_URL] ?: "",
                lastBackupUrlFetchMs = preferences[LAST_BACKUP_URL_FETCH] ?: 0L,
                decentralizedMode = preferences[DECENTRALIZED_MODE] ?: false,
                appLockBiometricEnabled = preferences[APP_LOCK_BIOMETRIC] ?: false,
                hiddenNavItems = preferences[HIDDEN_NAV_ITEMS] ?: emptySet(),
                chatBackgroundUri = preferences[CHAT_BACKGROUND_URI] ?: "",
                enterToSend = preferences[ENTER_TO_SEND] ?: false,
                p2pInternetEnabled = preferences[P2P_INTERNET_ENABLED] ?: false,
                p2pDisabledContacts = preferences[P2P_DISABLED_CONTACTS] ?: emptySet(),
                contactsAppIntegration = preferences[CONTACTS_APP_INTEGRATION] ?: false,
                audioQuality = preferences[AUDIO_QUALITY] ?: "AUTO",
                audioOutputChannel = preferences[AUDIO_OUTPUT_CHANNEL] ?: "SYSTEM",
                bluetoothHeadsetEnabled = preferences[BLUETOOTH_HEADSET_ENABLED] ?: true,
            )
        }

    /**
     * Flow der Login-Credentials aus dem globalen (profilunabhängigen) Store.
     * Wird im ViewModel für den Login-Bildschirm genutzt.
     */
    val credentialsFlow: Flow<Triple<String, String, Boolean>> = context.globalDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            Triple(
                prefs[FAKE_NUMBER] ?: "",
                encryptedCredentialStore.getPassword(),
                prefs[REMEMBER_ME] ?: false
            )
        }

    // --- SETTER FUNKTIONEN ---

    suspend fun updateCredentials(num: String, pass: String, remember: Boolean) {
        // Passwort verschlüsselt im KeyStore speichern (nicht im Klartext-DataStore)
        encryptedCredentialStore.savePassword(if (remember) pass else "")
        // Fake-Number und Remember-Me im globalen DataStore (kein Passwort mehr)
        context.globalDataStore.edit {
            it[FAKE_NUMBER] = if (remember) num else ""
            it[REMEMBER_ME] = remember
        }
        // Auch im Profil-Store (kein Passwort mehr)
        profileDataStore.edit {
            it[FAKE_NUMBER] = num
            it[REMEMBER_ME] = remember
        }
    }

    suspend fun updateThemeMode(mode: ThemeMode) { profileDataStore.edit { it[THEME_MODE] = mode.name } }
    suspend fun updatePrimaryColor(color: Int) { profileDataStore.edit { it[PRIMARY_COLOR] = color } }
    suspend fun updateAccentColor(color: Int) { profileDataStore.edit { it[ACCENT_COLOR] = color } }
    suspend fun updateBubbleColor(color: Int) { profileDataStore.edit { it[BUBBLE_COLOR] = color } }
    suspend fun updateBubbleColorPartner(color: Int) { profileDataStore.edit { it[BUBBLE_COLOR_PARTNER] = color } }
    suspend fun setBiometricEnabled(enabled: Boolean) { profileDataStore.edit { it[BIOMETRIC_ENABLED] = enabled } }
    suspend fun setNotificationsEnabled(enabled: Boolean) { profileDataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled } }
    suspend fun setVibrationEnabled(enabled: Boolean) { profileDataStore.edit { it[VIBRATION_ENABLED] = enabled } }
    suspend fun setSoundEnabled(enabled: Boolean) { profileDataStore.edit { it[SOUND_ENABLED] = enabled } }
    suspend fun setFontSize(size: Float) { profileDataStore.edit { it[FONT_SIZE] = size } }
    suspend fun setAutoDownloadMedia(enabled: Boolean) { profileDataStore.edit { it[AUTO_DOWNLOAD] = enabled } }
    suspend fun setShowOnlineStatus(enabled: Boolean) { profileDataStore.edit { it[SHOW_ONLINE] = enabled } }
    suspend fun setReadReceipts(enabled: Boolean) { profileDataStore.edit { it[READ_RECEIPTS] = enabled } }
    suspend fun setStatusVisible(visible: Boolean) { profileDataStore.edit { it[STATUS_VISIBLE] = visible } }
    suspend fun setLastSyncTimestamp(time: Long) { profileDataStore.edit { it[SYNC_TIME] = time } }
    suspend fun setFirstStart(isFirst: Boolean) { profileDataStore.edit { it[FIRST_START] = isFirst } }
    suspend fun setE2eeEnabled(enabled: Boolean) { profileDataStore.edit { it[E2EE_ACTIVE] = enabled } }
    suspend fun setDatingRadius(km: Float) { profileDataStore.edit { it[DATING_RADIUS] = km } }
    suspend fun setNearbyGenderFilter(filter: String) { profileDataStore.edit { it[NEARBY_GENDER_FILTER] = filter } }
    suspend fun setNearbyAgeMin(age: Int) { profileDataStore.edit { it[NEARBY_AGE_MIN] = age } }
    suspend fun setNearbyAgeMax(age: Int) { profileDataStore.edit { it[NEARBY_AGE_MAX] = age } }
    suspend fun setNearbyFriendshipOnly(enabled: Boolean) { profileDataStore.edit { it[NEARBY_FRIENDSHIP_ONLY] = enabled } }
    suspend fun setReadReceiptAfterReply(enabled: Boolean) { profileDataStore.edit { it[READ_RECEIPT_AFTER_REPLY] = enabled } }
    suspend fun setChatSoundReceive(enabled: Boolean) { profileDataStore.edit { it[CHAT_SOUND_RECEIVE] = enabled } }
    suspend fun setChatSoundSend(enabled: Boolean) { profileDataStore.edit { it[CHAT_SOUND_SEND] = enabled } }
    suspend fun updateBarColor(color: Int) { profileDataStore.edit { it[BAR_COLOR] = color } }
    suspend fun updateBackgroundColor(color: Int) { profileDataStore.edit { it[BACKGROUND_COLOR] = color } }
    suspend fun setAutoLoginEnabled(enabled: Boolean) { profileDataStore.edit { it[AUTO_LOGIN] = enabled } }
    suspend fun setNotificationSound(sound: String) { profileDataStore.edit { it[NOTIFICATION_SOUND] = sound } }
    suspend fun updateAppTheme(theme: AppTheme) { profileDataStore.edit { it[APP_THEME] = theme.name } }
    suspend fun updateBubbleColor2(color: Int) { profileDataStore.edit { it[BUBBLE_COLOR_2] = color } }
    suspend fun updateBubbleColorPartner2(color: Int) { profileDataStore.edit { it[BUBBLE_COLOR_PARTNER_2] = color } }
    suspend fun updateFocusBorderColor(color: Int) { profileDataStore.edit { it[FOCUS_BORDER_COLOR] = color } }
    suspend fun updateFocusBorderColor2(color: Int) { profileDataStore.edit { it[FOCUS_BORDER_COLOR_2] = color } }
    suspend fun setAvatarSizeMultiplier(m: Float) { profileDataStore.edit { it[AVATAR_SIZE] = m } }
    suspend fun setTorMode(mode: String) { profileDataStore.edit { it[TOR_MODE] = mode } }
    suspend fun setOnionAddress(address: String) { profileDataStore.edit { it[ONION_ADDRESS] = address } }
    suspend fun setHomeServerUrl(url: String) { profileDataStore.edit { it[HOME_SERVER_URL] = url } }
    suspend fun setAutoBackupUrl(url: String) { profileDataStore.edit { it[AUTO_BACKUP_URL] = url } }
    suspend fun setLastBackupUrlFetchMs(ms: Long) { profileDataStore.edit { it[LAST_BACKUP_URL_FETCH] = ms } }
    suspend fun setDecentralizedMode(enabled: Boolean) { profileDataStore.edit { it[DECENTRALIZED_MODE] = enabled } }
    suspend fun setAppLockBiometricEnabled(enabled: Boolean) { profileDataStore.edit { it[APP_LOCK_BIOMETRIC] = enabled } }
    suspend fun setHiddenNavItems(items: Set<String>) { profileDataStore.edit { it[HIDDEN_NAV_ITEMS] = items } }
    suspend fun updateChatBackgroundUri(uri: String) { profileDataStore.edit { it[CHAT_BACKGROUND_URI] = uri } }
    suspend fun setEnterToSend(enabled: Boolean) { profileDataStore.edit { it[ENTER_TO_SEND] = enabled } }
    suspend fun setP2pInternetEnabled(enabled: Boolean) { profileDataStore.edit { it[P2P_INTERNET_ENABLED] = enabled } }
    suspend fun setContactsAppIntegration(enabled: Boolean) { profileDataStore.edit { it[CONTACTS_APP_INTEGRATION] = enabled } }
    suspend fun setAudioQuality(quality: String) { profileDataStore.edit { it[AUDIO_QUALITY] = quality } }
    suspend fun setAudioOutputChannel(channel: String) { profileDataStore.edit { it[AUDIO_OUTPUT_CHANNEL] = channel } }
    suspend fun setBluetoothHeadsetEnabled(enabled: Boolean) { profileDataStore.edit { it[BLUETOOTH_HEADSET_ENABLED] = enabled } }
    /** Aktiviert/deaktiviert P2P für einen einzelnen Kontakt (lokale Override-Liste). */
    suspend fun setP2pContactEnabled(userId: String, enabled: Boolean) {
        profileDataStore.edit { prefs ->
            val current = prefs[P2P_DISABLED_CONTACTS] ?: emptySet()
            prefs[P2P_DISABLED_CONTACTS] = if (enabled) current - userId else current + userId
        }
    }
    // --- CREDENTIALS BACKUP HELPERS ---
    fun getStoredPassword(): String = encryptedCredentialStore.getPassword()
    fun setStoredPassword(password: String) = encryptedCredentialStore.savePassword(password)
    fun getAppLockPinValue(): String = encryptedCredentialStore.getAppLockPin()
    fun setAppLockPinValue(pin: String) = encryptedCredentialStore.saveAppLockPin(pin)

    // --- APP LOCK PIN (via EncryptedCredentialStore) ---
    fun saveAppLockPin(pin: String) = encryptedCredentialStore.saveAppLockPin(pin)
    fun hasAppLockPin(): Boolean = encryptedCredentialStore.hasAppLockPin()
    fun verifyAppLockPin(pin: String): Boolean = encryptedCredentialStore.verifyAppLockPin(pin)
    fun clearAppLockPin() = encryptedCredentialStore.clearAppLockPin()

    // --- ONBOARDING ---

    val completedOnboardingStepsFlow: Flow<Set<String>> = profileDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[COMPLETED_ONBOARDING_STEPS] ?: emptySet() }

    suspend fun markOnboardingStepComplete(stepId: String) {
        profileDataStore.edit { prefs ->
            val current = prefs[COMPLETED_ONBOARDING_STEPS] ?: emptySet()
            prefs[COMPLETED_ONBOARDING_STEPS] = current + stepId
        }
    }

    suspend fun resetOnboarding() {
        profileDataStore.edit { it.remove(COMPLETED_ONBOARDING_STEPS) }
    }

    suspend fun clearAll() {
        encryptedCredentialStore.clearPassword()
        profileDataStore.edit { it.clear() }
    }

    /** Erhöht den Zähler für gesendete Nearby-Fragen und gibt den neuen Wert zurück. */
    suspend fun incrementNearbyQuestionsSent(): Int {
        var newCount = 0
        profileDataStore.edit { prefs ->
            val current = prefs[NEARBY_QUESTIONS_SENT] ?: 0
            newCount = current + 1
            prefs[NEARBY_QUESTIONS_SENT] = newCount
        }
        return newCount
    }
}