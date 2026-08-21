package com.securechat.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Cast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.app.ui.utils.BiometricHelper
import androidx.core.content.FileProvider
import com.securechat.app.data.local.AppTheme
import com.securechat.app.data.local.UserPreferences
import com.securechat.app.data.local.ThemeMode
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.screens.*
import com.securechat.app.ui.theme.SecureChatTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val cacheManager by lazy { LetheCacheManager(applicationContext) }
    private val biometricHelper by lazy { BiometricHelper(applicationContext) }

    /** Zentrales Audio-Fokus- und Bluetooth-Routing-Management. */
    val audioFocusManager by lazy { AudioFocusManager(applicationContext) }

    // Legacy Notification-Permission (wird durch multiplePermissionLauncher ersetzt, bleibt für Compat)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — keine weiteren Aktionen nötig */ }

    // Vollständiges Permission-Handling für alle benötigten Berechtigungen
    private val multiplePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Ergebnisse werden ignoriert — App funktioniert mit Teilerlaub */ }

    // MediaProjection-Launcher für Bildschirmfreigabe im Videoanruf
    private val screenShareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val data = result.data!!
            // Vor dem Start der Bildschirmfreigabe: CallForegroundService mit
            // FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION aktualisieren (Android 10+ Pflicht).
            val svcIntent = Intent(this, CallForegroundService::class.java).apply {
                action = CallForegroundService.ACTION_SCREEN_SHARE
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(svcIntent)
            } else {
                startService(svcIntent)
            }
            // Kurze Verzögerung damit onStartCommand + startForeground abgeschlossen sind,
            // bevor ScreenCapturerAndroid MediaProjection.getMediaProjection() aufruft.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                viewModel.onStartScreenShare(data)
            }, 300)
        }
    }

    // Flag: verhindert Doppel-Registrierung des Netzwerk-Callbacks (z. B. bei Activity-Neustart)
    private var networkCallbackRegistered = false

    // Netzwerk-Callback: bei Netzwerkwechsel (WLAN↔Mobilfunk) WebSocket neu verbinden
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Neue Netzwerkverbindung verfügbar → WebSocket sauber trennen und neu verbinden
            // onAvailable läuft auf ConnectivityThread → Hilt/ViewModel braucht Main Thread
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                viewModel.reconnectWebSocket()
            }
        }
        override fun onLost(network: Network) {
            // Netzwerk weg → Status aktualisieren (Reconnect erfolgt bei onAvailable)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Nach einem Profil-/Account-Wechsel (Process.killProcess() in MainViewModel.login()/
        // switchAccount()) bleibt die Task in den Recents erhalten – Android würde sonst die
        // SavedInstanceState-Bundle (NavController-Backstack inkl. offener Chat-Route mit
        // contactId des ALTEN Profils) restaurieren und kurzzeitig/dauerhaft Inhalte des
        // falschen Accounts zeigen. Bundle in diesem Fall verwerfen für einen sauberen Neustart.
        val restoredState = if (com.securechat.app.data.local.ProfileManager.consumePendingRestart(this)) null else savedInstanceState
        super.onCreate(restoredState)
        // Pflicht für targetSdk >= 35 (Android 15+) — erzwingt Edge-to-Edge Handling
        enableEdgeToEdge()

        // APK bereinigen: nur löschen wenn die App bereits auf die Zielversion aktualisiert wurde.
        // APK liegt in filesDir/updates/ (kein cacheDir – der wird vom System gecleart).
        // Löschen NUR wenn pendingVersion gesetzt ist UND currentVersion >= pendingVersion.
        // Falls pendingVersion fehlt: APK nicht löschen (unbekannter Zustand, Installer könnte
        // die Datei noch benötigen).
        run {
            val prefs = getSharedPreferences("lethe_update", android.content.Context.MODE_PRIVATE)
            val pendingVersion = prefs.getString("pending_apk_version", null)
            val apkFile = File(File(filesDir, "updates"), "lethe-update.apk")
            // Altes APK aus cacheDir entfernen (Migration von vor dem filesDir-Wechsel)
            File(cacheDir, "lethe-update.apk").takeIf { it.exists() }?.delete()
            if (apkFile.exists() && pendingVersion != null) {
                val currentVersion = com.securechat.app.BuildConfig.VERSION_NAME
                if (!isOlderVersion(currentVersion, pendingVersion)) {
                    apkFile.delete()
                    prefs.edit().remove("pending_apk_version").apply()
                }
            }
        }

        // Alle benötigten Runtime-Permissions beim Start anfordern
        val permissionsToRequest = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            // Galerie-Export (Movies/Lethe, Pictures/Lethe): vor Android 10 ist
            // WRITE_EXTERNAL_STORAGE zur Laufzeit nötig; ab API 29 schreibt MediaStore ohne Recht.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

        if (permissionsToRequest.isNotEmpty()) {
            multiplePermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }

        // Akkuoptimierung deaktivieren: Lethe als Messenger braucht uneingeschränkten
        // Hintergrundbetrieb, damit WebSocket und Benachrichtigungen nicht eingefroren werden.
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        }

        // Netzwerkänderungen überwachen (WLAN ↔ Mobilfunk → WebSocket neu verbinden)
        // try-catch: TooManyRequestsException möglich wenn Activity nach AOD/Sperrbildschirm
        // neu erstellt wird bevor onDestroy() des alten Callbacks ausgeführt wurde.
        if (!networkCallbackRegistered) {
            try {
                val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                val networkRequest = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(networkRequest, networkCallback)
                networkCallbackRegistered = true
            } catch (e: Exception) {
                android.util.Log.w("LETHE_NETWORK", "registerNetworkCallback fehlgeschlagen: ${e.message}")
            }
        }

        // NotificationHandler ForegroundService starten (Keepalive + Liveness-Check)
        try {
            NotificationHandler.start(this)
        } catch (_: Exception) { /* Service-Start kann in manchen Situationen fehlschlagen */ }

        // Anruf-Aktionen vom Sperrbildschirm (App-Neustart via Intent)
        val intentNavigateTo = intent.getStringExtra("navigate_to")
        when (intentNavigateTo) {
            "accept_call"  -> viewModel.acceptCallFromStore()
            "decline_call" -> viewModel.declineCallFromStore()
            "switch_account" -> {
                intent.getStringExtra("switch_account_profile_key")?.let { viewModel.switchAccount(it) }
            }
            else -> {
                // Kind-Familien-Einladungstoken aus Notification speichern
                val childInviteToken = intent.getStringExtra("child_invite_token")
                if (childInviteToken != null) {
                    viewModel.storePendingChildInviteToken(childInviteToken)
                }
                // Normaler Benachrichtigungs-Deep-Link (chat_id / navigate_to)
                val intentChatId = if (intentNavigateTo == "nearby_chat")
                    intent.getStringExtra("match_id")
                else
                    intent.getStringExtra("chat_id")
                viewModel.setPendingDeepLink(
                    chatId = intentChatId,
                    navigateTo = intentNavigateTo
                )
            }
        }

        // Share-Intent aus anderen Apps verarbeiten
        handleIncomingIntent(intent)

        // Foreground-Status initial setzen (onResume ruft checkForUpdate auf)
        viewModel.setAppForeground(true)

        // Ausstehende globale Lumis-Broadcasts prüfen (falls App neu gestartet wurde)
        viewModel.checkPendingGlobalLumis()

        // Cast-Geräte-Discovery (Google-frei, mDNS) läuft über den CastDiscoveryManager,
        // der in SecureChatApplication gestartet wird – hier ist nichts zu tun.

        // Bluetooth-Routing-Receiver registrieren (A2DP + Headset Verbindungsänderungen)
        audioFocusManager.register()

        setContent {
            // System-Dialoge erst NACH dem ersten sichtbaren Frame anzeigen.
            // LaunchedEffect(Unit) läuft zwar nach der ersten Komposition, aber noch VOR dem
            // ersten gerenderten Frame. delay(1500) stellt sicher, dass die App vollständig
            // im Vordergrund ist bevor startActivity() aufgerufen wird – sonst landet die
            // Activity im Task-Switcher, bevor der User sie sieht.
            // SharedPreferences-Flag: Dialoge werden pro Installation nur EINMAL gezeigt,
            // damit der User bei jedem Start normal zur App kommt.
            val context = androidx.compose.ui.platform.LocalContext.current
            var showVollaDialog by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                val prefs = context.getSharedPreferences("lethe_startup", android.content.Context.MODE_PRIVATE)
                if (!prefs.getBoolean("system_perms_requested", false)) {
                    delay(1500) // App vollständig im Vordergrund abwarten
                    prefs.edit().putBoolean("system_perms_requested", true).apply()
                    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                    if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                        try {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) { }
                    }
                    // USE_FULL_SCREEN_INTENT prüfen (Android 14+): leitet zu App-Einstellungen
                    // wenn das Recht fehlt (Samsung OneUI/MIUI entziehen es oft still).
                    checkAndRequestFullScreenPermission(context)
                    // Volla OS: einmaliger Hinweis auf manuelle Benachrichtigungs-Ausnahmeregeln
                    val isVollaOs = Build.MANUFACTURER.equals("Volla", ignoreCase = true)
                    if (isVollaOs && !prefs.getBoolean("volla_notification_hint_shown", false)) {
                        prefs.edit().putBoolean("volla_notification_hint_shown", true).apply()
                        showVollaDialog = true
                    }
                }
            }
            if (showVollaDialog) {
                AlertDialog(
                    onDismissRequest = { showVollaDialog = false },
                    title = { Text("Benachrichtigungen einrichten") },
                    text = {
                        Text(
                            "Auf Volla OS werden Benachrichtigungen von Kontakten standardmäßig eingeschränkt.\n\n" +
                            "Damit du keine Nachrichten verpasst, füge bitte für jeden Kontakt eine Benachrichtigungs-Ausnahmeregel hinzu:\n\n" +
                            "Einstellungen → Apps → Lethe → Benachrichtigungen → Ausnahmen hinzufügen"
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showVollaDialog = false }) {
                            Text("Verstanden")
                        }
                    }
                )
            }

            // Beobachte die Preferences direkt aus dem ViewModel
            val preferences by viewModel.userPrefs.collectAsState()
            val scope = rememberCoroutineScope()

            // Berechne ob Dark Mode basierend auf ThemeMode
            val systemInDarkTheme = isSystemInDarkTheme()
            val isDarkTheme = when (preferences.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemInDarkTheme
            }

            val customBarColor = if (preferences.barColor != 0) Color(preferences.barColor) else null
            val customBackgroundColor = if (preferences.backgroundColor != 0) Color(preferences.backgroundColor) else null

            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            // Hintergrundfarbe nur außerhalb des ChatScreens anwenden
            val isChatRoute = currentRoute?.startsWith("chat/") == true
            val effectiveBackgroundColor = if (isChatRoute) null else customBackgroundColor

            SecureChatTheme(
                darkTheme = isDarkTheme,
                primaryColor = Color(preferences.primaryColor),
                accentColor = Color(preferences.accentColor),
                barColor = customBarColor,
                backgroundColor = effectiveBackgroundColor,
                appTheme = preferences.appTheme
            ) {
                // Globaler Cast-Geräte-Picker (Google-frei) – liegt über der ganzen App
                GlobalCastDevicePicker(viewModel.castDiscoveryManager)

                val currentUser by viewModel.currentUser.collectAsState()
                val pendingShare by viewModel.pendingShare.collectAsState()
                val pendingDeepLink by viewModel.pendingDeepLink.collectAsState()
                val isAppLocked by viewModel.isAppLocked.collectAsState()
                var showAppLockPinInput by remember { mutableStateOf(false) }
                var appLockPinEntry by remember { mutableStateOf("") }
                var appLockPinError by remember { mutableStateOf(false) }

                // Share-Intent: automatisch zum ShareTargetScreen navigieren
                // Die login→contacts-Navigation erfolgt durch LaunchedEffect(currentUser).
                // Hier nur warten bis login verlassen wurde, dann share_target öffnen.
                LaunchedEffect(pendingShare, currentUser) {
                    val share = pendingShare ?: return@LaunchedEffect
                    if (currentUser == null) return@LaunchedEffect
                    // Warten bis LaunchedEffect(currentUser) von login wegnavigiert hat
                    var waited = 0
                    while (navController.currentBackStackEntry?.destination?.route == "login" && waited < 20) {
                        kotlinx.coroutines.delay(50)
                        waited++
                    }
                    val targetChatId = share.targetChatId
                    if (targetChatId != null) {
                        // Direct Share: Kontakt wurde im System-Share-Sheet bereits gewählt –
                        // Kontakt-Picker überspringen und direkt in den Chat navigieren.
                        // Nur zu contacts navigieren wenn noch auf dem login-Screen – sonst
                        // würde popUpTo("login") crashen weil login nicht mehr im Back-Stack ist.
                        if (navController.currentBackStackEntry?.destination?.route == "login") {
                            navController.navigate("contacts") {
                                popUpTo("login") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                        // Prüfen ob das Ziel eine Gruppe ist
                        val isGroup = viewModel.isGroupChat(targetChatId)
                        when {
                            share.text != null -> {
                                // Text-Share: Textfeld im Chat vorbefüllen
                                viewModel.setPendingChatText(share.text)
                                viewModel.clearPendingShare()
                                navController.navigate("chat/$targetChatId")
                            }
                            share.uri != null && share.mimeType?.startsWith("image/") == true -> {
                                // Direct Share: Image Editor anzeigen statt direkt senden.
                                // Begleit-Text (EXTRA_TEXT, z.B. Song-Empfehlung aus dem Media Player)
                                // als Bildunterschrift vorbelegen – sonst geht der Text verloren.
                                val shareCaption = share.caption
                                viewModel.clearPendingShare()
                                val encodedUri = android.net.Uri.encode(share.uri.toString())
                                val captionArg = if (!shareCaption.isNullOrBlank())
                                    "&caption=${android.net.Uri.encode(shareCaption)}" else ""
                                navController.navigate("image_editor?uri=$encodedUri&chatId=$targetChatId&partnerId=$targetChatId$captionArg")
                            }
                            share.uri != null -> {
                                // Andere Medien (Audio, Video, Bild, Dokument): direkt senden
                                val mediaType = when {
                                    share.mimeType?.startsWith("image/") == true -> "image"
                                    share.mimeType?.startsWith("video/") == true -> "video"
                                    share.mimeType?.startsWith("audio/") == true -> "audio"
                                    else -> "document"
                                }
                                if (mediaType == "document") viewModel.sendDocumentMessage(targetChatId, share.uri, isGroup = isGroup)
                                else if (isGroup) viewModel.sendGroupMediaMessage(targetChatId, share.uri, mediaType)
                                else viewModel.sendMediaMessage(targetChatId, share.uri, mediaType)
                                viewModel.clearPendingShare()
                                navController.navigate("chat/$targetChatId")
                            }
                            share.uris != null -> {
                                // Mehrere Medien: alle direkt senden
                                val mediaType = when {
                                    share.mimeType?.startsWith("image/") == true -> "image"
                                    share.mimeType?.startsWith("video/") == true -> "video"
                                    share.mimeType?.startsWith("audio/") == true -> "audio"
                                    else -> "document"
                                }
                                share.uris.forEach { uri ->
                                    if (mediaType == "document") viewModel.sendDocumentMessage(targetChatId, uri, isGroup = isGroup)
                                    else if (isGroup) viewModel.sendGroupMediaMessage(targetChatId, uri, mediaType)
                                    else viewModel.sendMediaMessage(targetChatId, uri, mediaType)
                                }
                                viewModel.clearPendingShare()
                                navController.navigate("chat/$targetChatId")
                            }
                            else -> viewModel.clearPendingShare()
                        }
                        return@LaunchedEffect
                    }
                    // Kein vorausgewählter Kontakt → Kontakt-Picker anzeigen
                    if (navController.currentBackStackEntry?.destination?.route != "share_target") {
                        navController.navigate("share_target") {
                            launchSingleTop = true
                        }
                    }
                }

                // Avatar-Klick → Status-Tab + Kontakt-Status öffnen
                val pendingStatusNavContact by viewModel.pendingStatusNavContact.collectAsState()
                LaunchedEffect(pendingStatusNavContact) {
                    val contactId = pendingStatusNavContact ?: return@LaunchedEffect
                    // Navigiere zum Status-Tab; pendingStatusNavContact bleibt gesetzt
                    // damit StatusScreen es ausliest und den richtigen Status öffnet
                    navController.navigate("statuses") {
                        launchSingleTop = true
                        popUpTo("contacts") { inclusive = false }
                    }
                }

                // Benachrichtigungs-Deep-Link: chat_id → Chat öffnen; navigate_to="contacts" → Kontaktliste
                LaunchedEffect(currentUser, pendingDeepLink) {
                    val (chatId, navigateTo) = pendingDeepLink ?: return@LaunchedEffect
                    if (currentUser == null) return@LaunchedEffect
                    viewModel.clearPendingDeepLink()
                    // "incoming_call" wird vom LaunchedEffect(incomingCall) navigiert –
                    // NICHT hier navigieren, sonst wird der Backstack mit popUpTo geleert
                    // und der Anruf-Screen verschwindet sofort wieder (Flash-Bug).
                    if (navigateTo == "incoming_call") return@LaunchedEffect
                    // Nearby-Chat: direkt zur Nearby-Chat-Route navigieren (matchId in chatId)
                    if (navigateTo == "backend_support") {
                        navController.navigate("contacts") {
                            popUpTo("login") { inclusive = true }
                            launchSingleTop = true
                        }
                        navController.navigate("settings/backend?initialTab=3")
                        return@LaunchedEffect
                    }
                    if (navigateTo == "support") {
                        navController.navigate("contacts") {
                            popUpTo("login") { inclusive = true }
                            launchSingleTop = true
                        }
                        navController.navigate("support")
                        return@LaunchedEffect
                    }
                    if (navigateTo == "backend_applications") {
                        navController.navigate("contacts") {
                            popUpTo("login") { inclusive = true }
                            launchSingleTop = true
                        }
                        navController.navigate("settings/backend?initialTab=4")
                        return@LaunchedEffect
                    }
                    if (navigateTo == "creator_apply") {
                        navController.navigate("contacts") {
                            popUpTo("login") { inclusive = true }
                            launchSingleTop = true
                        }
                        navController.navigate("creator_apply?startTab=1")
                        return@LaunchedEffect
                    }
                    if (navigateTo == "nearby_questions" || navigateTo == "nearby_profile_setup") {
                        navController.navigate("contacts") {
                            popUpTo("login") { inclusive = true }
                            launchSingleTop = true
                        }
                        navController.navigate("nearby_profile")
                        return@LaunchedEffect
                    }
                    if (navigateTo == "nearby_chat") {
                        val matchId = chatId
                        if (matchId != null) {
                            navController.navigate("contacts") {
                                popUpTo("login") { inclusive = true }
                                launchSingleTop = true
                            }
                            navController.navigate("nearby_chat/$matchId")
                        }
                        return@LaunchedEffect
                    }
                    if (navigateTo == "statuses") {
                        navController.navigate("contacts") {
                            popUpTo("login") { inclusive = true }
                            launchSingleTop = true
                        }
                        viewModel.markStatusTabSeen()
                        navController.navigate("statuses") {
                            launchSingleTop = true
                            restoreState = true
                        }
                        return@LaunchedEffect
                    }
                    if (navigateTo == "spark_view") {
                        navController.navigate("contacts") {
                            popUpTo("login") { inclusive = true }
                            launchSingleTop = true
                        }
                        navController.navigate("sparks_feed") {
                            launchSingleTop = true
                            restoreState = true
                        }
                        return@LaunchedEffect
                    }
                    if (navigateTo == "content_view") {
                        if (chatId != null) {
                            navController.navigate("contacts") {
                                popUpTo("login") { inclusive = true }
                                launchSingleTop = true
                            }
                            navController.navigate("content_view/$chatId") {
                                launchSingleTop = true
                            }
                        }
                        return@LaunchedEffect
                    }
                    if (navigateTo == "group_chat") {
                        if (chatId != null) {
                            navController.navigate("contacts") {
                                popUpTo("login") { inclusive = true }
                                launchSingleTop = true
                            }
                            navController.navigate("chat/$chatId?isGroup=true")
                        }
                        return@LaunchedEffect
                    }
                    // Aktion aus der Kontakte-App ("Verbundene Apps"): Lethe-Anruf starten.
                    // Die Navigation zum Call-Screen übernimmt der activeCallPartnerId-Effekt.
                    if (navigateTo == "contact_voice_call" || navigateTo == "contact_video_call") {
                        if (chatId != null) {
                            navController.navigate("contacts") {
                                popUpTo("login") { inclusive = true }
                                launchSingleTop = true
                            }
                            if (navigateTo == "contact_voice_call")
                                viewModel.startVoiceCall(chatId)
                            else
                                viewModel.startVideoCall(chatId)
                        }
                        return@LaunchedEffect
                    }
                    navController.navigate("contacts") {
                        popUpTo("login") { inclusive = true }
                        launchSingleTop = true
                    }
                    if (chatId != null) {
                        navController.navigate("chat/$chatId")
                    }
                }

                // Auto-Navigate nach Login oder Profil-Wechsel-Neustart (nur ohne pending Deep-Link)
                LaunchedEffect(currentUser) {
                    if (currentUser != null && pendingDeepLink == null &&
                        navController.currentBackStackEntry?.destination?.route == "login") {
                        navController.navigate("contacts") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                    if (currentUser != null) {
                        viewModel.checkAndShowNearbyProfilePrompt()
                    }
                }

                // Session abgelaufen (Token ungültig nach Server-Wechsel o.ä.) → Login-Screen
                LaunchedEffect(Unit) {
                    viewModel.sessionExpiredEvent.collect {
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
                // ── Ausgehender Anruf: startVideoCall()/startVoiceCall() → navigiere zur richtigen Route ──
                val activeCallPartnerId by viewModel.activeCallPartnerId.collectAsState()
                val activeCallType      by viewModel.activeCallType.collectAsState()
                LaunchedEffect(activeCallPartnerId) {
                    if (activeCallPartnerId != null && currentUser != null) {
                        val route = navController.currentBackStackEntry?.destination?.route
                        val targetRoute = if (activeCallType == "VOICE")
                            "voice_call/$activeCallPartnerId"
                        else
                            "video_call/$activeCallPartnerId"
                        if (route?.startsWith("video_call") == false && route?.startsWith("voice_call") == false) {
                            navController.navigate(targetRoute) {
                                launchSingleTop = true
                            }
                        }
                    }
                }

                // ── Eingehender Videoanruf → automatisch zum Klingel-Screen navigieren ──
                val incomingCall by viewModel.incomingCall.collectAsState()
                LaunchedEffect(incomingCall, currentUser) {
                    if (incomingCall != null && currentUser != null) {
                        // Bildschirm aufwecken und über Sperrbildschirm anzeigen
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            setShowWhenLocked(true)
                            setTurnScreenOn(true)
                        } else {
                            @Suppress("DEPRECATION")
                            window.addFlags(
                                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            )
                        }
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        // Warte bis NavController bereit ist (Cold-Start-Fix)
                        var waited = 0
                        while (navController.currentBackStackEntry?.destination?.route == null && waited < 20) {
                            delay(50); waited++
                        }
                        val route = navController.currentBackStackEntry?.destination?.route
                        if (route != "incoming_call") {
                            navController.navigate("incoming_call") {
                                launchSingleTop = true
                            }
                        }
                    } else if (incomingCall == null) {
                        // Flags zurücksetzen wenn Anruf endet
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            setShowWhenLocked(false)
                            setTurnScreenOn(false)
                        } else {
                            @Suppress("DEPRECATION")
                            window.clearFlags(
                                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            )
                        }
                        // Anruf wurde per Benachrichtigung abgelehnt / ist abgelaufen →
                        // incoming_call-Screen nur schließen wenn er noch aktiv ist.
                        // Nicht schließen wenn wir bereits zu video_call navigiert haben.
                        if (navController.currentBackStackEntry?.destination?.route == "incoming_call") {
                            navController.popBackStack()
                        }
                    }
                }

                // VideoCapabilityMonitor: lokalen Status laufend ans ViewModel melden
                val videoCapabilityMonitor = remember {
                    com.securechat.app.ui.screens.VideoCapabilityMonitor(this@MainActivity)
                }
                val isLocalVideoCapable by videoCapabilityMonitor.isVideoCapable.collectAsState(false)
                LaunchedEffect(isLocalVideoCapable) {
                    viewModel.updateLocalVideoCapable(isLocalVideoCapable)
                }
                DisposableEffect(Unit) {
                    onDispose { videoCapabilityMonitor.cleanup() }
                }

                // Update-State beobachten
                val updateInfo by viewModel.updateInfo.collectAsState()
                val downloadProgress by viewModel.downloadProgress.collectAsState()
                val uploadProgress by viewModel.uploadProgress.collectAsState()
                val installReady by viewModel.installReady.collectAsState()
                val totalUnreadCount by viewModel.totalUnreadCount.collectAsState()
                val chatBadgeCount by viewModel.chatBadgeCount.collectAsState()
                val hasUnreadNearby by viewModel.hasUnreadNearbyMessages.collectAsState()
                val nearbyBadgeCount by viewModel.nearbyBadgeCount.collectAsState()
                val hasUnseenStatuses by viewModel.hasUnseenStatuses.collectAsState()
                val myChildPermissions by viewModel.myChildPermissions.collectAsState()

                // Globales Chat-Hintergrundbild – Galerie-Picker
                val globalChatBgPickerLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent()
                ) { uri: android.net.Uri? ->
                    if (uri != null) {
                        // Persistente Leseberechtigung sichern
                        try {
                            contentResolver.takePersistableUriPermission(
                                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (_: Exception) {}
                        viewModel.updateChatBackgroundUri(uri.toString())
                    }
                }

                // Falls Kind auf gesperrter Route landet → zu Chats umleiten
                LaunchedEffect(myChildPermissions, currentRoute) {
                    val perms = myChildPermissions ?: return@LaunchedEffect
                    if (!perms.canUseNearby && currentRoute == "dating") {
                        navController.navigate("contacts") { popUpTo("contacts") { inclusive = true } }
                    }
                    if (!perms.canViewSparks && currentRoute == "sparks_feed") {
                        navController.navigate("contacts") { popUpTo("contacts") { inclusive = true } }
                    }
                }

                val isCompact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
                val canUseNearby = myChildPermissions?.canUseNearby != false
                val canViewSparks = myChildPermissions?.canViewSparks != false
                val hiddenNavItems = preferences.hiddenNavItems
                val showNearby = canUseNearby && "nearby" !in hiddenNavItems
                val showSparks = canViewSparks && "sparks" !in hiddenNavItems
                val showCreator = (currentUser?.isCreator == true || currentUser?.isAdmin == true) && "creator" !in hiddenNavItems
                val showVip = "vip" !in hiddenNavItems
                val bottomBarRoutes = buildList {
                    add("contacts")
                    if (showNearby) add("dating")
                    add("statuses")
                    if (showVip) add("vip")
                    if (showCreator) add("creator_dashboard")
                }

                Row(modifier = Modifier.fillMaxSize()) {
                if (!isCompact && currentRoute in bottomBarRoutes) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        NavigationRailItem(
                            icon = {
                                BadgedBox(badge = {
                                    if (chatBadgeCount > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) { Text(if (chatBadgeCount > 99) "99+" else "$chatBadgeCount", fontSize = 9.sp) }
                                    }
                                }) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Chats") }
                            },
                            label = { Text("Chats") },
                            selected = currentRoute == "contacts",
                            onClick = {
                                if (currentRoute == "contacts") viewModel.resetContactListTab()
                                navController.navigate("contacts") { popUpTo("contacts") { saveState = true }; launchSingleTop = true; restoreState = true }
                            }
                        )
                        NavigationRailItem(
                            icon = {
                                BadgedBox(badge = {
                                    if (hasUnseenStatuses) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            modifier = androidx.compose.ui.Modifier.size(8.dp)
                                        ) {}
                                    }
                                }) { Icon(Icons.Default.AccessTime, contentDescription = "Status") }
                            },
                            label = { Text("Status") },
                            selected = currentRoute == "statuses",
                            onClick = {
                                viewModel.markStatusTabSeen()
                                navController.navigate("statuses") { popUpTo("contacts") { saveState = true }; launchSingleTop = true; restoreState = true }
                            }
                        )
                        if (showNearby) NavigationRailItem(
                            icon = {
                                BadgedBox(badge = {
                                    if (nearbyBadgeCount > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) { Text(if (nearbyBadgeCount > 99) "99+" else "$nearbyBadgeCount", fontSize = 9.sp) }
                                    }
                                }) { Icon(Icons.Default.LocationOn, contentDescription = "Nearby") }
                            },
                            label = { Text("Nearby") },
                            selected = currentRoute == "dating",
                            onClick = { viewModel.clearNearbyBadge(); navController.navigate("dating") { popUpTo("contacts") { saveState = true }; launchSingleTop = true; restoreState = true } }
                        )
                        if (showCreator) NavigationRailItem(
                            icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Creator") },
                            label = { Text("Creator") },
                            selected = currentRoute == "creator_dashboard",
                            onClick = { navController.navigate("creator_dashboard") { popUpTo("contacts") { saveState = true }; launchSingleTop = true; restoreState = true } }
                        )
                        if (showSparks) NavigationRailItem(
                            icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Sparks") },
                            label = { Text("Sparks") },
                            selected = currentRoute == "sparks_feed",
                            onClick = { navController.navigate("sparks_feed") { popUpTo("contacts") { saveState = true }; launchSingleTop = true; restoreState = true } }
                        )
                        if (showVip) NavigationRailItem(
                            icon = { Icon(Icons.Default.Star, contentDescription = "VIP") },
                            label = { Text("VIP") },
                            selected = currentRoute == "vip",
                            onClick = { navController.navigate("vip") { popUpTo("contacts") { saveState = true }; launchSingleTop = true; restoreState = true } }
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f)) {

                Scaffold(
                    contentWindowInsets = WindowInsets.systemBars,
                    bottomBar = {
                        if (isCompact && currentRoute in bottomBarRoutes) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                NavigationBarItem(
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (chatBadgeCount > 0) {
                                                    Badge(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                                    ) {
                                                        Text(
                                                            text = if (chatBadgeCount > 99) "99+" else "$chatBadgeCount",
                                                            fontSize = 9.sp
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Chats")
                                        }
                                    },
                                    label = { Text("Chats") },
                                    selected = currentRoute == "contacts",
                                    onClick = {
                                        if (currentRoute == "contacts") viewModel.resetContactListTab()
                                        navController.navigate("contacts") {
                                            popUpTo("contacts") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                                NavigationBarItem(
                                    icon = {
                                        BadgedBox(badge = {
                                            if (hasUnseenStatuses) {
                                                Badge(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    modifier = androidx.compose.ui.Modifier.size(8.dp)
                                                ) {}
                                            }
                                        }) { Icon(Icons.Default.AccessTime, contentDescription = "Status") }
                                    },
                                    label = { Text("Status") },
                                    selected = currentRoute == "statuses",
                                    onClick = {
                                        viewModel.markStatusTabSeen()
                                        navController.navigate("statuses") {
                                            popUpTo("contacts") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                                if (showNearby) NavigationBarItem(
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (nearbyBadgeCount > 0) {
                                                    Badge(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                                    ) {
                                                        Text(
                                                            text = if (nearbyBadgeCount > 99) "99+" else "$nearbyBadgeCount",
                                                            fontSize = 9.sp
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.LocationOn, contentDescription = "Nearby")
                                        }
                                    },
                                    label = { Text("Nearby") },
                                    selected = currentRoute == "dating",
                                    onClick = {
                                        viewModel.clearNearbyBadge()
                                        navController.navigate("dating") {
                                            popUpTo("contacts") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                                // Creator-Tab: sichtbar für Creator und Admins (wenn nicht ausgeblendet)
                                if (showCreator) {
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Creator") },
                                        label = { Text("Creator") },
                                        selected = currentRoute == "creator_dashboard",
                                        onClick = {
                                            navController.navigate("creator_dashboard") {
                                                popUpTo("contacts") { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    )
                                }
                                if (showSparks) NavigationBarItem(
                                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Sparks") },
                                    label = { Text("Sparks") },
                                    selected = currentRoute == "sparks_feed",
                                    onClick = {
                                        navController.navigate("sparks_feed") {
                                            popUpTo("contacts") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                                if (showVip) NavigationBarItem(
                                    icon = { Icon(Icons.Default.Star, contentDescription = "VIP") },
                                    label = { Text("VIP") },
                                    selected = currentRoute == "vip",
                                    onClick = {
                                        navController.navigate("vip") {
                                            popUpTo("contacts") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    val startDest = remember {
                        val prefs = context.getSharedPreferences("lethe_startup", android.content.Context.MODE_PRIVATE)
                        if (prefs.getBoolean("onboarding_shown", false)) "login" else "onboarding"
                    }
                    NavHost(
                        navController = navController,
                        startDestination = startDest,
                        modifier = Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                    ) {
                        composable("onboarding") {
                            com.securechat.app.ui.screens.OnboardingScreen(
                                onFinished = {
                                    context.getSharedPreferences("lethe_startup", android.content.Context.MODE_PRIVATE)
                                        .edit().putBoolean("onboarding_shown", true).apply()
                                    navController.navigate("login") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("login") {
                            LoginScreen(
                                viewModel = viewModel,
                                onLoginSuccess = {
                                    navController.navigate("contacts") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("share_target") {
                            ShareTargetScreen(
                                viewModel = viewModel,
                                onNavigateToChat = { chatId ->
                                    navController.navigate("chat/$chatId") {
                                        popUpTo("share_target") { inclusive = true }
                                    }
                                },
                                onDismiss = {
                                    navController.popBackStack()
                                },
                                onNavigateToImageEditor = { uri, partnerId, caption ->
                                    val encodedUri = android.net.Uri.encode(uri.toString())
                                    val captionArg = if (!caption.isNullOrBlank())
                                        "&caption=${android.net.Uri.encode(caption)}" else ""
                                    navController.navigate("image_editor?uri=$encodedUri&chatId=$partnerId&partnerId=$partnerId$captionArg")
                                },
                                onNavigateToStatusCreate = {
                                    navController.navigate("status_create") {
                                        popUpTo("share_target") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("freunde_werben") {
                            FriendeWerbenScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("contact_import") {
                            ContactImportScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("contacts") {
                            ContactlistScreen(
                                viewModel = viewModel,
                                onNavigateToChat = { chatId ->
                                    navController.navigate("chat/$chatId")
                                },
                                onNavigateToGroupChat = { groupId ->
                                    navController.navigate("chat/$groupId?isGroup=true")
                                },
                                onNavigateToSettings = { section ->
                                    navController.navigate("settings/$section")
                                },
                                onNavigateToStatusView = { statusId ->
                                    navController.navigate("status_view/$statusId")
                                },
                                onNavigateToDevices = {
                                    navController.navigate("devices")
                                },
                                onNavigateToCoins = {
                                    navController.navigate("coins")
                                },
                                onNavigateToAgeVerification = {
                                    navController.navigate("age_verification")
                                },
                                onNavigateToAppSettings = {
                                    navController.navigate("settings/app_settings")
                                },
                                onNavigateToNotifications = {
                                    navController.navigate("settings/notifications")
                                },
                                onNavigateToContactImport = {
                                    navController.navigate("contact_import")
                                },
                                onNavigateToBlockedUsers = {
                                    navController.navigate("blocked_users")
                                },
                                onNavigateToSupport = {
                                    navController.navigate("support")
                                },
                                onNavigateToPayForCreator = {
                                    navController.navigate("pay_for_creator")
                                },
                                onNavigateToSknChLobby = {
                                    navController.navigate("sketch_n_check_lobby")
                                },
                                onNavigateToGames = {
                                    navController.navigate("games")
                                },
                                onNavigateToJumpOrDieGame = {
                                    navController.navigate("jump_or_die_solo")
                                },
                                onNavigateToPinballGame = {
                                    navController.navigate("pinball_solo")
                                },
                                onNavigateToFamily = {
                                    navController.navigate("family")
                                },
                                onAccountDeleted = {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("games") {
                            GamesScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToTiltNDrop = { navController.navigate("tilt_n_drop") },
                                onNavigateToJumpOrDie = { navController.navigate("jump_or_die") },
                                onNavigateToPinball = { navController.navigate("pinball") }
                            )
                        }

                        composable("games_singleplayer") {
                            GamesScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToTiltNDrop = { navController.navigate("tilt_n_drop") },
                                onNavigateToJumpOrDie = { navController.navigate("jump_or_die") },
                                onNavigateToPinball = { navController.navigate("pinball") },
                                initialLeaderboardMode = 1
                            )
                        }

                        composable("jump_or_die") {
                            JumpOrDieScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("pinball") {
                            PinballScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("pinball_solo") {
                            PinballScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.navigate("games_singleplayer") {
                                        popUpTo("pinball_solo") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("jump_or_die_solo") {
                            JumpOrDieScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.navigate("games_singleplayer") {
                                        popUpTo("jump_or_die_solo") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("tilt_n_drop") {
                            val tiltParams by viewModel.tiltNDropParams.collectAsState()
                            val params = tiltParams
                            TiltNDropScreen(
                                viewModel   = viewModel,
                                partnerId   = params?.partnerId   ?: "",
                                partnerName = params?.partnerName ?: "",
                                isHost      = params?.isHost      ?: true,
                                onNavigateBack = {
                                    viewModel.clearTiltNDropParams()
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("sketch_n_check_lobby") {
                            LobbySknChScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("pay_for_creator") {
                            PayForCreatorScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToCreatorApply = { navController.navigate("creator_apply") }
                            )
                        }

                        composable("blocked_users") {
                            BlockedUsersScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("support") {
                            SupportScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("age_verification") {
                            AgeVerificationScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("coins") {
                            CoinsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("devices") {
                            DevicesScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("dating") {
                            NearbyListScreen(
                                viewModel = viewModel,
                                onNavigateToProfile = {
                                    navController.navigate("nearby_profile")
                                },
                                onNavigateToNearbyChat = { matchId ->
                                    navController.navigate("nearby_chat/$matchId")
                                },
                                onNavigateToNearbyDetail = {
                                    navController.navigate("nearby_detail")
                                },
                                fontSizeMultiplier = preferences.fontSizeMultiplier
                            )
                        }

                        composable("creator_dashboard") {
                            CreatorsDashboard(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                navController = navController,
                                onNavigateToPayout = { navController.navigate("creator_stripe_payout") }
                            )
                        }

                        composable("creator_stripe_payout") {
                            CreatorStripePayoutScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("creator_add_article") {
                            CreatorAddArticleScreen(
                                viewModel = viewModel,
                                navController = navController
                            )
                        }

                        composable("spark_stats") {
                            SparkStatsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("content_editor") {
                            ContentEditorScreen(
                                viewModel = viewModel,
                                navController = navController,
                                initialTab = 0
                            )
                        }

                        composable("content_editor_spark") {
                            ContentEditorScreen(
                                viewModel = viewModel,
                                navController = navController,
                                initialTab = 1
                            )
                        }

                        // 3D-Viewer Screen
                        composable(
                            route = "fullscreen_3d/{encodedUrl}/{filename}?textureUrl={textureUrl}",
                            arguments = listOf(
                                androidx.navigation.navArgument("encodedUrl")  { type = androidx.navigation.NavType.StringType },
                                androidx.navigation.navArgument("filename")    { type = androidx.navigation.NavType.StringType },
                                androidx.navigation.navArgument("textureUrl")  { type = androidx.navigation.NavType.StringType; defaultValue = "" }
                            )
                        ) { backStack ->
                            val encodedUrl    = backStack.arguments?.getString("encodedUrl") ?: ""
                            val filename      = backStack.arguments?.getString("filename")   ?: "3D-Datei"
                            val encodedTex    = backStack.arguments?.getString("textureUrl") ?: ""
                            val fileUrl       = java.net.URLDecoder.decode(encodedUrl, "UTF-8")
                            val textureUrl    = if (encodedTex.isNotEmpty()) java.net.URLDecoder.decode(encodedTex, "UTF-8") else ""
                            FullScreen3DViewerScreen(
                                fileUrl        = fileUrl,
                                filename       = filename,
                                textureUrl     = textureUrl,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("content_editor/{contentId}") { backStack ->
                            ContentEditorScreen(
                                viewModel = viewModel,
                                navController = navController,
                                contentId = backStack.arguments?.getString("contentId")
                            )
                        }

                        // Spark-Editor: Filter, Musik und FFmpeg-Verarbeitung vor dem Upload (Einzelbild/Video)
                        composable(
                            route = "spark_editor?videoUri={videoUri}",
                            arguments = listOf(
                                navArgument("videoUri") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) { entry ->
                            val rawUri = entry.arguments?.getString("videoUri") ?: ""
                            if (rawUri.isNotBlank()) {
                                val mediaUri = android.net.Uri.parse(android.net.Uri.decode(rawUri))
                                val pendingSound = viewModel.pendingSoundData.collectAsState().value
                                LaunchedEffect(Unit) { viewModel.clearPendingSoundData() }
                                SparkAddEditorScreen(
                                    viewModel = viewModel,
                                    mediaUri = mediaUri,
                                    preSelectedSoundOriginId = pendingSound?.soundOriginSparkId,
                                    preSelectedMusicTitle = pendingSound?.musicTitle,
                                    preSelectedMusicArtist = pendingSound?.musicArtist,
                                    preSelectedMusicCoverUrl = pendingSound?.musicCoverUrl,
                                    onFinish = { outputUri, title, description, category ->
                                        navController.previousBackStackEntry?.savedStateHandle?.let { sh ->
                                            sh.set("processed_spark_uri", outputUri.toString())
                                            sh.set("processed_spark_title", title)
                                            sh.set("processed_spark_description", description)
                                            sh.set("processed_spark_category", category)
                                            sh.set("processed_spark_sound_origin_id", pendingSound?.soundOriginSparkId)
                                            sh.set("processed_spark_music_title", pendingSound?.musicTitle)
                                            sh.set("processed_spark_music_artist", pendingSound?.musicArtist)
                                            sh.set("processed_spark_music_cover_url", pendingSound?.musicCoverUrl)
                                        }
                                        navController.popBackStack()
                                    },
                                    onCancel = { navController.popBackStack() }
                                )
                            } else {
                                LaunchedEffect(Unit) { navController.popBackStack() }
                            }
                        }

                        // Spark-Editor Multi-Image: Bild-Slideshow-Spark erstellen
                        composable(
                            route = "spark_editor_multi?uris={uris}",
                            arguments = listOf(
                                navArgument("uris") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) { entry ->
                            val rawUris = entry.arguments?.getString("uris") ?: ""
                            if (rawUris.isNotBlank()) {
                                val uriList = rawUris.split("|")
                                    .mapNotNull { enc ->
                                        try { android.net.Uri.parse(android.net.Uri.decode(enc)) } catch (_: Exception) { null }
                                    }
                                if (uriList.isNotEmpty()) {
                                    val firstUri = uriList.first()
                                    val extra = uriList.drop(1)
                                    val pendingSound = viewModel.pendingSoundData.collectAsState().value
                                    LaunchedEffect(Unit) { viewModel.clearPendingSoundData() }
                                    SparkAddEditorScreen(
                                        viewModel = viewModel,
                                        mediaUri = firstUri,
                                        extraImageUris = extra,
                                        preSelectedSoundOriginId = pendingSound?.soundOriginSparkId,
                                        preSelectedMusicTitle = pendingSound?.musicTitle,
                                        preSelectedMusicArtist = pendingSound?.musicArtist,
                                        preSelectedMusicCoverUrl = pendingSound?.musicCoverUrl,
                                        onFinish = { outputUri, title, description, category ->
                                            navController.previousBackStackEntry?.savedStateHandle?.let { sh ->
                                                sh.set("processed_spark_uri", outputUri.toString())
                                                sh.set("processed_spark_title", title)
                                                sh.set("processed_spark_description", description)
                                                sh.set("processed_spark_category", category)
                                            }
                                            navController.popBackStack()
                                        },
                                        onUploadImageSpark = { uris, title, desc, category, mTitle, mArtist, mCover, originId, localMusicUri, audiusTrackId, audiusStreamUrl, audiusDuration, onProgress ->
                                            when {
                                                localMusicUri != null -> {
                                                    // MP3 direkt beim Spark-Upload in den Spark-Ordner hochladen
                                                    viewModel.uploadSparkImages(
                                                        uris = uris,
                                                        title = title,
                                                        description = desc,
                                                        category = category,
                                                        musicTitle = mTitle,
                                                        musicArtist = mArtist,
                                                        musicCoverUrl = mCover,
                                                        soundOriginSparkId = originId,
                                                        musicId = null,
                                                        audioUri = localMusicUri,
                                                        onProgress = onProgress,
                                                        onResult = { spark, _ ->
                                                            onProgress(1f)
                                                            if (spark != null) viewModel.loadVipFeed(type = "spark")
                                                            navController.popBackStack()
                                                        }
                                                    )
                                                }
                                                audiusTrackId != null && audiusStreamUrl != null -> {
                                                    onProgress(0.05f)
                                                    viewModel.saveApiMusicTrack(
                                                        audiusId = audiusTrackId,
                                                        title = mTitle,
                                                        artist = mArtist,
                                                        coverUrl = mCover,
                                                        streamUrl = audiusStreamUrl,
                                                        duration = audiusDuration
                                                    ) { music, _ ->
                                                        onProgress(0.3f)
                                                        viewModel.uploadSparkImages(
                                                            uris = uris,
                                                            title = title,
                                                            description = desc,
                                                            category = category,
                                                            musicTitle = mTitle,
                                                            musicArtist = mArtist,
                                                            musicCoverUrl = mCover,
                                                            soundOriginSparkId = originId,
                                                            musicId = music?.id,
                                                            onProgress = { p -> onProgress(0.3f + p * 0.7f) },
                                                            onResult = { spark, _ ->
                                                                onProgress(1f)
                                                                if (spark != null) viewModel.loadVipFeed(type = "spark")
                                                                navController.popBackStack()
                                                            }
                                                        )
                                                    }
                                                }
                                                else -> viewModel.uploadSparkImages(
                                                    uris = uris,
                                                    title = title,
                                                    description = desc,
                                                    category = category,
                                                    musicTitle = mTitle,
                                                    musicArtist = mArtist,
                                                    musicCoverUrl = mCover,
                                                    soundOriginSparkId = originId,
                                                    musicId = null,
                                                    onProgress = onProgress,
                                                    onResult = { spark, _ ->
                                                        onProgress(1f)
                                                        if (spark != null) viewModel.loadVipFeed(type = "spark")
                                                        navController.popBackStack()
                                                    }
                                                )
                                            }
                                        },
                                        onCancel = { navController.popBackStack() }
                                    )
                                } else {
                                    LaunchedEffect(Unit) { navController.popBackStack() }
                                }
                            } else {
                                LaunchedEffect(Unit) { navController.popBackStack() }
                            }
                        }

                        // SparkSoundScreen: Alle Sparks mit demselben Sound
                        composable(
                            route = "sparks_sound/{sparkId}",
                            arguments = listOf(
                                navArgument("sparkId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val sparkId = backStackEntry.arguments?.getString("sparkId") ?: ""
                            SparkSoundScreen(
                                sparkId = sparkId,
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToSpark = { sid ->
                                    viewModel.setSparkFeedOverride(null)
                                    navController.navigate("sparks_feed") { launchSingleTop = true }
                                    viewModel.setPendingSparkId(sid)
                                },
                                onUseSound = { oid, mTitle, mArtist, mCover ->
                                    viewModel.setPendingSoundData(
                                        com.securechat.app.ui.MainViewModel.PendingSoundData(
                                            soundOriginSparkId = oid,
                                            musicTitle = mTitle,
                                            musicArtist = mArtist,
                                            musicCoverUrl = mCover,
                                        )
                                    )
                                    navController.navigate("sparks_feed") { launchSingleTop = true }
                                }
                            )
                        }

                        composable("content_view/{contentId}") { backStack ->
                            val cId = backStack.arguments?.getString("contentId") ?: return@composable
                            ContentViewScreen(
                                viewModel = viewModel,
                                contentId = cId,
                                navController = navController,
                                fontSizeMultiplier = preferences.fontSizeMultiplier
                            )
                        }

                        composable("live_room/{creatorId}") { backStack ->
                            val cId = backStack.arguments?.getString("creatorId") ?: return@composable
                            LiveRoomScreen(
                                creatorId = cId,
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("livestream_settings/{creatorId}") { backStack ->
                            val cId = backStack.arguments?.getString("creatorId") ?: return@composable
                            LiveStreamConfigScreen(
                                viewModel = viewModel,
                                onStartStream = { config ->
                                    viewModel.pendingLiveStreamConfig = config
                                    navController.navigate("creator_live/$cId")
                                },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("creator_live/{creatorId}") { backStack ->
                            val cId = backStack.arguments?.getString("creatorId") ?: return@composable
                            CreatorLiveScreen(
                                creatorId = cId,
                                viewModel = viewModel,
                                config = viewModel.pendingLiveStreamConfig,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("document_viewer") {
                            DocumentViewerScreen(
                                viewModel = viewModel,
                                navController = navController
                            )
                        }

                        composable("vip") {
                            VipScreen(
                                viewModel = viewModel,
                                navController = navController,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("sparks_feed") { entry ->
                            val processedSparkUri by remember(entry) {
                                entry.savedStateHandle.getStateFlow("processed_spark_uri", null as String?)
                            }.collectAsState()
                            val processedSparkTitle by remember(entry) {
                                entry.savedStateHandle.getStateFlow("processed_spark_title", null as String?)
                            }.collectAsState()
                            val processedSparkDesc by remember(entry) {
                                entry.savedStateHandle.getStateFlow("processed_spark_description", null as String?)
                            }.collectAsState()
                            val processedSparkCategory by remember(entry) {
                                entry.savedStateHandle.getStateFlow("processed_spark_category", null as String?)
                            }.collectAsState()
                            val processedSoundOriginId by remember(entry) {
                                entry.savedStateHandle.getStateFlow("processed_spark_sound_origin_id", null as String?)
                            }.collectAsState()
                            val processedMusicTitle by remember(entry) {
                                entry.savedStateHandle.getStateFlow("processed_spark_music_title", null as String?)
                            }.collectAsState()
                            val processedMusicArtist by remember(entry) {
                                entry.savedStateHandle.getStateFlow("processed_spark_music_artist", null as String?)
                            }.collectAsState()
                            val processedMusicCoverUrl by remember(entry) {
                                entry.savedStateHandle.getStateFlow("processed_spark_music_cover_url", null as String?)
                            }.collectAsState()
                            LaunchedEffect(processedSparkUri) {
                                processedSparkUri?.let { uriString ->
                                    entry.savedStateHandle.remove<String>("processed_spark_uri")
                                    entry.savedStateHandle.remove<String>("processed_spark_title")
                                    entry.savedStateHandle.remove<String>("processed_spark_description")
                                    entry.savedStateHandle.remove<String>("processed_spark_category")
                                    val soundOrigin = processedSoundOriginId
                                    val mTitle = processedMusicTitle
                                    val mArtist = processedMusicArtist
                                    val mCover = processedMusicCoverUrl
                                    entry.savedStateHandle.remove<String>("processed_spark_sound_origin_id")
                                    entry.savedStateHandle.remove<String>("processed_spark_music_title")
                                    entry.savedStateHandle.remove<String>("processed_spark_music_artist")
                                    entry.savedStateHandle.remove<String>("processed_spark_music_cover_url")
                                    val uri = android.net.Uri.parse(uriString)
                                    viewModel.uploadSparkHls(
                                        uri = uri,
                                        title = processedSparkTitle ?: "",
                                        category = processedSparkCategory,
                                        description = processedSparkDesc,
                                        soundOriginSparkId = soundOrigin,
                                        musicTitle = mTitle,
                                        musicArtist = mArtist,
                                        musicCoverUrl = mCover
                                    ) { spark, _ ->
                                        if (spark != null) viewModel.loadVipFeed(type = "spark")
                                    }
                                }
                            }
                            SparksFeedScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToLiveRoom = { creatorId -> navController.navigate("live_room/$creatorId") },
                                onNavigateToContent = { contentId -> navController.navigate("content_view/$contentId") },
                                onNavigateToSparkEditor = { encodedUri ->
                                    navController.navigate("spark_editor?videoUri=$encodedUri")
                                },
                                onNavigateToSparkEditorMulti = { encodedUris ->
                                    navController.navigate("spark_editor_multi?uris=$encodedUris")
                                },
                                onNavigateToSoundScreen = { sparkId ->
                                    navController.navigate("sparks_sound/$sparkId")
                                },
                                onNavigateToProfile = { navController.navigate("settings/account") },
                                onNavigateToSparksProfile = { creatorId ->
                                    navController.navigate("sparks_profile/$creatorId")
                                },
                                fontSizeMultiplier = preferences.fontSizeMultiplier
                            )
                        }

                        composable(
                            route = "sparks_profile/{creatorId}",
                            arguments = listOf(
                                navArgument("creatorId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val creatorId = backStackEntry.arguments?.getString("creatorId") ?: ""
                            SparksProfileScreen(
                                creatorId = creatorId,
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToSpark = { _ ->
                                    // Zurück zum bestehenden SparksFeedScreen statt neuer Instanz:
                                    // verhindert dass der Pager von Seite 0 (Video) startet und
                                    // dann erst zu dem angeklickten Spark scrollt.
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("statuses") {
                            StatusScreen(
                                viewModel = viewModel,
                                onNavigateToCreate = { navController.navigate("status_create") },
                                onNavigateToCreateWithImage = { navController.navigate("status_create") },
                                onNavigateToCreateWithVideo = { navController.navigate("status_create") },
                                onNavigateToStatus = { statusId ->
                                    navController.navigate("status_view/$statusId")
                                }
                            )
                        }
                        composable(
                            route = "status_view/{statusId}",
                            arguments = listOf(
                                navArgument("statusId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val statusId = backStackEntry.arguments?.getString("statusId") ?: ""
                            StatusViewer(
                                statusId = statusId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateToProfile = { uid -> navController.navigate("user_profile/$uid") },
                                onMusicClick = { url, title, artist ->
                                    val encUrl    = java.net.URLEncoder.encode(url,    "UTF-8")
                                    val encTitle  = java.net.URLEncoder.encode(title  ?: "", "UTF-8")
                                    val encArtist = java.net.URLEncoder.encode(artist ?: "", "UTF-8")
                                    navController.navigate("music_library_details?url=$encUrl&title=$encTitle&artist=$encArtist")
                                }
                            )
                        }

                        // Musik-Details (aus Status Viewer)
                        composable(
                            route = "music_library_details?url={url}&title={title}&artist={artist}",
                            arguments = listOf(
                                navArgument("url")    { type = NavType.StringType; defaultValue = "" },
                                navArgument("title")  { type = NavType.StringType; defaultValue = "" },
                                navArgument("artist") { type = NavType.StringType; defaultValue = "" }
                            )
                        ) { entry ->
                            val musicUrl    = java.net.URLDecoder.decode(entry.arguments?.getString("url")    ?: "", "UTF-8")
                            val musicTitle  = java.net.URLDecoder.decode(entry.arguments?.getString("title")  ?: "", "UTF-8").takeIf { it.isNotBlank() }
                            val musicArtist = java.net.URLDecoder.decode(entry.arguments?.getString("artist") ?: "", "UTF-8").takeIf { it.isNotBlank() }
                            LetheMusicLibaryDetails(
                                musicUrl        = musicUrl,
                                musicTitle      = musicTitle,
                                musicArtist     = musicArtist,
                                viewModel       = viewModel,
                                onNavigateBack  = { navController.popBackStack() }
                            )
                        }
                        composable("status_create") {
                            StatusCreationScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("nearby_profile") {
                            NearbyProfileScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("nearby_detail") {
                            NearbyDetailScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToSparksProfile = { uid -> navController.navigate("sparks_profile/$uid") }
                            )
                        }
                        composable(
                            route = "nearby_chat/{matchId}",
                            arguments = listOf(
                                navArgument("matchId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val matchId = backStackEntry.arguments?.getString("matchId") ?: return@composable
                            NearbyMatchChatScreen(
                                matchId = matchId,
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToTiltNDrop = { navController.navigate("tilt_n_drop") },
                                onNavigateToNearbyDetail = { navController.navigate("nearby_detail") },
                                fontSizeMultiplier = preferences.fontSizeMultiplier
                            )
                        }
                        composable(
                            route = "chat/{chatId}?isGroup={isGroup}",
                            arguments = listOf(
                                navArgument("chatId") { type = NavType.StringType },
                                navArgument("isGroup") { type = NavType.BoolType; defaultValue = false }
                            )
                        ) { backStackEntry ->
                            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
                            val isGroup = backStackEntry.arguments?.getBoolean("isGroup") ?: false
                            val effectiveBubbleColor = Color(preferences.bubbleColor)
                            val effectivePartnerBubble = Color(preferences.bubbleColorPartner)
                            val effectiveBubbleColor2 = Color(preferences.bubbleColor2)
                            val effectivePartnerBubble2 = Color(preferences.bubbleColorPartner2)
                            val effectiveFocusBorderColor = Color(preferences.focusBorderColor)
                            val effectiveFocusBorderColor2 = Color(preferences.focusBorderColor2)
                            ChatScreen(
                                viewModel = viewModel,
                                chatId = chatId,
                                isGroup = isGroup,
                                bubbleColor = effectiveBubbleColor,
                                partnerBubbleColor = effectivePartnerBubble,
                                bubbleColor2 = effectiveBubbleColor2,
                                partnerBubbleColor2 = effectivePartnerBubble2,
                                focusBorderColor = effectiveFocusBorderColor,
                                focusBorderColor2 = effectiveFocusBorderColor2,
                                globalChatBackgroundUri = preferences.chatBackgroundUri.ifEmpty { null },
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToUserProfile = { userId ->
                                    navController.navigate("user_profile/$userId")
                                },
                                onNavigateToImageEditor = { uri ->
                                    val encodedUri = android.net.Uri.encode(uri.toString())
                                    navController.navigate("image_editor?uri=$encodedUri&chatId=$chatId&partnerId=$chatId")
                                },
                                onNavigateToMultiImageEditor = { uris ->
                                    viewModel.setPendingImageUris(uris)
                                    val encodedUri = android.net.Uri.encode(uris[0].toString())
                                    navController.navigate("image_editor?uri=$encodedUri&chatId=$chatId&partnerId=$chatId")
                                },
                                onOpenDocument = { url, fileName ->
                                    viewModel.openDocument(url, fileName)
                                    navController.navigate("document_viewer")
                                },
                                onNavigateTo3DViewer = { fileUrl, fn, texUrl ->
                                    val encoded = java.net.URLEncoder.encode(fileUrl, "UTF-8")
                                    val encodedTex = java.net.URLEncoder.encode(texUrl, "UTF-8")
                                    navController.navigate("fullscreen_3d/$encoded/$fn?textureUrl=$encodedTex")
                                },
                                onNavigateToContent = { contentId ->
                                    navController.navigate("content_view/$contentId")
                                },
                                onNavigateToSpark = { sparkId ->
                                    viewModel.setPendingSparkId(sparkId)
                                    navController.navigate("sparks_feed") {
                                        popUpTo("contacts") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onNavigateToLiveMaps = { cId ->
                                    navController.navigate("live_maps/$cId")
                                },
                                onNavigateToGames = { partnerId, partnerName ->
                                    viewModel.setPreselectedGamePartner(partnerId, partnerName)
                                    navController.navigate("games")
                                },
                                onNavigateToPinball = {
                                    navController.navigate("pinball")
                                },
                                onNavigateToJumpOrDie = {
                                    navController.navigate("jump_or_die")
                                },
                                onNavigateToVideoCall = {
                                    val pid = viewModel.activeCallPartnerId.value
                                    if (pid != null) {
                                        navController.navigate("video_call/$pid") {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                onNavigateToVideoEditor = { uri ->
                                    val encodedUri = android.net.Uri.encode(uri.toString())
                                    navController.navigate("chat_video_editor?uri=$encodedUri&chatId=$chatId&isGroup=$isGroup")
                                },
                                onNavigateToVideoEditorEmpty = {
                                    navController.navigate("chat_video_editor?uri=&chatId=$chatId&isGroup=$isGroup")
                                },
                                fontSizeMultiplier = preferences.fontSizeMultiplier,
                                avatarSizeMultiplier = preferences.avatarSizeMultiplier,
                                onNavigateToCoins = { navController.navigate("coins") }
                            )
                        }

                        // Chat-Video-Editor (Schneiden + Zuschneiden vor dem Senden)
                        composable(
                            route = "chat_video_editor?uri={uri}&chatId={chatId}&isGroup={isGroup}",
                            arguments = listOf(
                                navArgument("uri") { type = NavType.StringType; defaultValue = "" },
                                navArgument("chatId") { type = NavType.StringType },
                                navArgument("isGroup") { type = NavType.BoolType; defaultValue = false }
                            )
                        ) { entry ->
                            val rawUri = entry.arguments?.getString("uri") ?: ""
                            val editorChatId = entry.arguments?.getString("chatId") ?: ""
                            val editorIsGroup = entry.arguments?.getBoolean("isGroup") ?: false
                            com.securechat.app.ui.screens.ChatVideoEditorScreen(
                                // Leerer Start (aus dem Anhang-Menü) → kein Video vorgeladen
                                videoUri = if (rawUri.isNotBlank()) android.net.Uri.parse(rawUri) else null,
                                chatId = editorChatId,
                                isGroup = editorIsGroup,
                                viewModel = viewModel,
                                onCancel = { navController.popBackStack() }
                            )
                        }

                        composable("live_maps/{chatId}") { backStackEntry ->
                            val cId = backStackEntry.arguments?.getString("chatId") ?: return@composable
                            LiveMapsScreen(
                                chatId = cId,
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // Account
                        composable("settings/account") {
                            val currentUser by viewModel.currentUser.collectAsState()
                            val inviteLinkUrl by viewModel.inviteLinkUrl.collectAsState()
                            val inviteLinkError by viewModel.inviteLinkError.collectAsState()
                            val inviteLinkLoading by viewModel.inviteLinkLoading.collectAsState()
                            val myDatingProfile by viewModel.myDatingProfile.collectAsState()
                            val adminPanelPasswordSet by viewModel.adminPanelPasswordSet.collectAsState()
                            val adminPanelPasswordMessage by viewModel.adminPanelPasswordMessage.collectAsState()
                            LaunchedEffect(Unit) { viewModel.loadMyDatingProfile() }
                            AccountScreen(
                                onNavigateBack = { navController.popBackStack() },
                                userName = currentUser?.name ?: "",
                                fakeNumber = currentUser?.fakeNumber ?: "",
                                profileImageUrl = currentUser?.profileImageUrl,
                                inviteLinkUrl = inviteLinkUrl,
                                inviteLinkError = inviteLinkError,
                                inviteLinkLoading = inviteLinkLoading,
                                userInfo = currentUser?.info,
                                userLinks = currentUser?.links,
                                userInstagram = currentUser?.instagram,
                                userTiktok = currentUser?.tiktok,
                                userYoutube = currentUser?.youtube,
                                onUpdateInfo = { viewModel.updateInfo(it) },
                                onUpdateLinks = { viewModel.updateLinks(it) },
                                onUpdateInstagram = { viewModel.updateInstagram(it) },
                                onUpdateTiktok = { viewModel.updateTiktok(it) },
                                onUpdateYoutube = { viewModel.updateYoutube(it) },
                                onUpdateName = { viewModel.updateProfile(it) },
                                onUploadProfileImage = { viewModel.uploadProfileImage(it) },
                                onShareInvite = { viewModel.shareInviteLink(this@MainActivity) },
                                onGenerateInvite = { viewModel.generateInviteLink() },
                                onLogout = {
                                    viewModel.logout()
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                onSwitchAccount = {
                                    // Kein logout() – aktueller Account bleibt im Switcher gespeichert,
                                    // Login-Screen zeigt die gespeicherten Accounts zum Wechseln ohne Passwort.
                                    viewModel.loadSavedAccounts()
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                ageVerified = currentUser?.ageVerified ?: false,
                                nearbyUsername = myDatingProfile?.username,
                                letheId = currentUser?.letheId,
                                onNavigateToFriendeWerben = {
                                    navController.navigate("freunde_werben")
                                },
                                onResetOnboarding = { viewModel.resetOnboarding() },
                                isVerified = currentUser?.isVerified ?: false,
                                currentStyx = currentUser?.styx ?: 0,
                                onBuyVerification = { callback -> viewModel.buyVerification(callback) },
                                isAdmin = currentUser?.isAdmin == true,
                                isModerator = currentUser?.isModerator == true,
                                adminPanelPasswordSet = adminPanelPasswordSet,
                                onLoadAdminPanelPasswordStatus = { viewModel.loadAdminPanelPasswordStatus() },
                                onSetAdminPanelPassword = { pw -> viewModel.setAdminPanelPassword(pw) },
                                adminPanelPasswordMessage = adminPanelPasswordMessage,
                                onClearAdminPanelPasswordMessage = { viewModel.clearAdminPanelPasswordMessage() }
                            )
                        }

                        // Multi-Account
                        composable("settings/multi_account") {
                            val activeUser by viewModel.currentUser.collectAsState()
                            val savedAccounts by viewModel.savedAccounts.collectAsState()
                            val monitorAllAccountsEnabled by viewModel.monitorAllAccountsEnabled.collectAsState()
                            val mixedContactListEnabled by viewModel.mixedContactListEnabled.collectAsState()
                            LaunchedEffect(Unit) { viewModel.loadSavedAccounts() }
                            MultiAccountScreen(
                                onNavigateBack = { navController.popBackStack() },
                                otherSavedAccounts = savedAccounts.filter { it.userId != activeUser?.userId },
                                onSwitchAccount = { viewModel.switchAccount(it) },
                                onRemoveAccount = { viewModel.removeSavedAccount(it) },
                                monitorAllAccountsEnabled = monitorAllAccountsEnabled,
                                onMonitorAllAccountsChange = { viewModel.setMonitorAllAccountsEnabled(it) },
                                mixedContactListEnabled = mixedContactListEnabled,
                                onMixedContactListChange = { viewModel.setMixedContactListEnabled(it) }
                            )
                        }

                        composable(
                            "creator_apply?startTab={startTab}",
                            arguments = listOf(navArgument("startTab") { type = NavType.IntType; defaultValue = 0 })
                        ) { backStackEntry ->
                            CreatorBewerbungScreen(
                                viewModel = viewModel,
                                startTab = backStackEntry.arguments?.getInt("startTab") ?: 0,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // Datenschutz
                        composable("settings/privacy") {
                            val contacts by viewModel.contacts.collectAsState(initial = emptyList())
                            val onboardingStep by viewModel.currentOnboardingStep.collectAsState()
                            val statusPermittedIds = remember(currentUser?.statusPermitted) {
                                val json = currentUser?.statusPermitted
                                    ?: return@remember emptyList<String>()
                                try {
                                    val arr = org.json.JSONArray(json)
                                    (0 until arr.length()).map { arr.getString(it) }
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }
                            PrivacyScreen(
                                onNavigateBack = { navController.popBackStack() },
                                showOnlineStatus = preferences.showOnlineStatus,
                                showReadReceipts = preferences.readReceiptsEnabled,
                                statusVisible = preferences.statusVisible,
                                onUpdatePrivacy = { online, receipts ->
                                    viewModel.updatePrivacySettings(online, receipts)
                                },
                                onStatusVisibleChange = { viewModel.updateStatusVisible(it) },
                                contacts = contacts,
                                statusPermittedIds = statusPermittedIds,
                                onStatusPermittedChange = { viewModel.updateStatusPermitted(it) },
                                readReceiptAfterReply = preferences.readReceiptAfterReply,
                                onReadReceiptAfterReplyChange = { viewModel.setReadReceiptAfterReply(it) },
                                showOnboardingSpeechBubble = onboardingStep == com.securechat.app.ui.OnboardingStep.PRIVACY_SPEECH_BUBBLE,
                                onOnboardingSpeechBubbleDismiss = {
                                    viewModel.completeOnboardingStep(com.securechat.app.ui.OnboardingStep.PRIVACY_SPEECH_BUBBLE)
                                },
                                appLockBiometricEnabled = preferences.appLockBiometricEnabled,
                                hasAppLockPin = viewModel.hasAppLockPin(),
                                onSaveAppLockPin = { pin -> viewModel.saveAppLockPin(pin) },
                                onClearAppLockPin = { viewModel.clearAppLockPin() },
                                onSetAppLockBiometricEnabled = { viewModel.setAppLockBiometricEnabled(it) },
                                contactsAppIntegration = preferences.contactsAppIntegration,
                                onContactsAppIntegrationChange = { viewModel.setContactsAppIntegration(it) }
                            )
                        }

                        // Design & Darstellung
                        composable("settings/design") {
                            DesignScreen(
                                onNavigateBack = { navController.popBackStack() },
                                themeMode = preferences.themeMode,
                                onThemeModeChange = { viewModel.updateThemeMode(it) },
                                primaryColor = Color(preferences.primaryColor),
                                onPrimaryColorChange = { viewModel.updatePrimaryColor(it.toArgb()) },
                                accentColor = Color(preferences.accentColor),
                                onAccentColorChange = { viewModel.updateAccentColor(it.toArgb()) },
                                bubbleColor = Color(preferences.bubbleColor),
                                onBubbleColorChange = { viewModel.updateBubbleColor(it.toArgb()) },
                                partnerBubbleColor = Color(preferences.bubbleColorPartner),
                                onPartnerBubbleColorChange = { viewModel.updatePartnerBubbleColor(it.toArgb()) },
                                onResetToWhatsAppColors = { viewModel.resetToWhatsAppColors() },
                                onApplyPresetDark = { viewModel.applyPresetDark() },
                                onApplyPresetBlue = { viewModel.applyPresetBlue() },
                                barColor = customBarColor ?: if (isDarkTheme) Color(0xFF0F1E35) else Color(0xFFEFEFEF),
                                onBarColorChange = { viewModel.updateBarColor(it.toArgb()) },
                                onBarColorReset = { viewModel.resetBarColor() },
                                backgroundColor = customBackgroundColor ?: if (isDarkTheme) Color(0xFF07131F) else Color(0xFFEFEFEF),
                                onBackgroundColorChange = { viewModel.updateBackgroundColor(it.toArgb()) },
                                onBackgroundColorReset = { viewModel.resetBackgroundColor() },
                                isDarkTheme = isDarkTheme,
                                fontSizeMultiplier = preferences.fontSizeMultiplier,
                                onFontSizeChange = { viewModel.setFontSize(it) },
                                appTheme = preferences.appTheme,
                                onAppThemeChange = { viewModel.updateAppTheme(it) },
                                bubbleColor2 = Color(preferences.bubbleColor2),
                                onBubbleColor2Change = { viewModel.updateBubbleColor2(it.toArgb()) },
                                partnerBubbleColor2 = Color(preferences.bubbleColorPartner2),
                                onPartnerBubbleColor2Change = { viewModel.updatePartnerBubbleColor2(it.toArgb()) },
                                focusBorderColor = Color(preferences.focusBorderColor),
                                onFocusBorderColorChange = { viewModel.updateFocusBorderColor(it.toArgb()) },
                                focusBorderColor2 = Color(preferences.focusBorderColor2),
                                onFocusBorderColor2Change = { viewModel.updateFocusBorderColor2(it.toArgb()) },
                                avatarSizeMultiplier = preferences.avatarSizeMultiplier,
                                onAvatarSizeChange = { viewModel.setAvatarSizeMultiplier(it) },
                                chatBackgroundUri = preferences.chatBackgroundUri,
                                onChatBackgroundUriChange = { viewModel.updateChatBackgroundUri(it) },
                                onPickCustomChatBackground = { globalChatBgPickerLauncher.launch("image/*") }
                            )
                        }

                        // Tor / Onion-Routing Einstellungen
                        composable("settings/tor") {
                            TorSettingsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                torMode = try {
                                    com.securechat.app.data.network.TorMode.valueOf(preferences.torMode)
                                } catch (e: Exception) {
                                    com.securechat.app.data.network.TorMode.OFF
                                },
                                onionAddress = preferences.onionAddress,
                                onTorModeChange = { viewModel.setTorMode(it) },
                                onOnionAddressChange = { viewModel.setOnionAddress(it) },
                                onFetchOnionAddress = { viewModel.fetchOnionAddressFromServer() },
                                homeServerUrl = preferences.homeServerUrl,
                                onHomeServerUrlChange = { viewModel.setHomeServerUrl(it) }
                            )
                        }

                        // App Einstellungen (Design + Datenschutz Hub)
                        composable("settings/app_settings") {
                            // Backup: Passwort zwischenspeichern für Launcher-Callbacks
                            var pendingBackupPassword by remember { mutableStateOf("") }
                            var pendingImportPassword by remember { mutableStateOf("") }

                            val backupProgress by viewModel.backupProgress.collectAsState()
                            val chatBackupProgress by viewModel.chatBackupProgress.collectAsState()
                            val keyBackupInfo by viewModel.keyBackupInfo.collectAsState()

                            // Backup-Metadaten beim Öffnen der Einstellungen frisch laden
                            LaunchedEffect(Unit) { viewModel.loadKeyBackupInfo() }

                            // Launcher: .lethe-Datei erstellen (Export) – Ordner wählbar
                            val createBackupLauncher = rememberLauncherForActivityResult(
                                ActivityResultContracts.CreateDocument("application/octet-stream")
                            ) { uri ->
                                if (uri != null && pendingBackupPassword.isNotBlank()) {
                                    viewModel.exportFullBackup(pendingBackupPassword, uri) { _, msg ->
                                        android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                    pendingBackupPassword = ""
                                }
                            }

                            // Launcher: .lethe-Datei öffnen (Import)
                            val openBackupLauncher = rememberLauncherForActivityResult(
                                ActivityResultContracts.OpenDocument()
                            ) { uri ->
                                if (uri != null && pendingImportPassword.isNotBlank()) {
                                    viewModel.importFullBackup(pendingImportPassword, uri) { _, msg ->
                                        android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                    pendingImportPassword = ""
                                }
                            }

                            // Google Sign-In Launcher für Drive-Backup. Intent-Aufbau/Auswertung
                            // laufen über MainViewModel → GoogleAuthProvider (playstore: echtes
                            // Google-Sign-In; foss: nicht verfügbar) – MainActivity bleibt frei
                            // von jeder com.google.android.gms.auth.api.signin.*-Abhängigkeit.
                            val googleSignInLauncher = rememberLauncherForActivityResult(
                                ActivityResultContracts.StartActivityForResult()
                            ) { result ->
                                val account = viewModel.handleGoogleSignInResult(result.data) { errorMsg ->
                                    android.widget.Toast.makeText(this@MainActivity, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                                }
                                if (account != null && pendingBackupPassword.isNotBlank()) {
                                    viewModel.exportToGoogleDrive(pendingBackupPassword, account) { _, msg ->
                                        android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                                pendingBackupPassword = ""
                            }

                            AppSettingsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToDesign = { navController.navigate("settings/design") },
                                onNavigateToPrivacy = { navController.navigate("settings/privacy") },
                                onNavigateToNotifications = { navController.navigate("settings/notifications") },
                                onNavigateToMultiAccount = { navController.navigate("settings/multi_account") },
                                onExportBackup = { password, destination ->
                                    pendingBackupPassword = password
                                    when (destination) {
                                        com.securechat.app.data.BackupManager.BackupDestination.LOCAL -> {
                                            createBackupLauncher.launch("lethe_backup_${System.currentTimeMillis()}.lethe")
                                        }
                                        com.securechat.app.data.BackupManager.BackupDestination.GOOGLE_DRIVE -> {
                                            // Google Sign-In starten (über GoogleAuthProvider; foss: null → Hinweis)
                                            val signInIntent = viewModel.buildGoogleSignInIntent(this@MainActivity)
                                            if (signInIntent != null) {
                                                googleSignInLauncher.launch(signInIntent)
                                            } else {
                                                android.widget.Toast.makeText(
                                                    this@MainActivity,
                                                    "Google-Drive-Backup ist in dieser Version nicht verfügbar.",
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                                pendingBackupPassword = ""
                                            }
                                        }
                                        com.securechat.app.data.BackupManager.BackupDestination.NEXTCLOUD -> {
                                            // Wird über onExportBackupNextcloud behandelt
                                        }
                                    }
                                },
                                onExportBackupNextcloud = { password, serverUrl, ncUser, ncPassword ->
                                    viewModel.exportToNextcloud(password, serverUrl, ncUser, ncPassword) { _, msg ->
                                        android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                },
                                onImportBackup = { password ->
                                    pendingImportPassword = password
                                    openBackupLauncher.launch(arrayOf("*/*"))
                                },
                                backupProgress = backupProgress,
                                keyBackupInfo = keyBackupInfo,
                                onBackupKeys = { passphrase, onResult ->
                                    viewModel.backupKeys(passphrase, automatic = false, onResult = onResult)
                                },
                                onRestoreKeys = { passphrase, onResult ->
                                    viewModel.restoreKeysFromBackup(passphrase, onResult)
                                },
                                onNavigateToLogViewer = { navController.navigate("log_viewer") },
                                onNavigateToFamily = { navController.navigate("family") },
                                onNavigateToTor = { navController.navigate("settings/tor") },
                                onNavigateToDecentralized = { navController.navigate("settings/decentralized") },
                                onNavigateToAppEdit = { navController.navigate("settings/app_edit") },
                                onNavigateToAudio = { navController.navigate("settings/audio") },
                                onNavigateToMyMusic = { navController.navigate("my_music_library") },
                                enterToSend = preferences.enterToSend,
                                onEnterToSendChange = { viewModel.setEnterToSend(it) },
                                chatBackupEnabled = preferences.chatBackupEnabled,
                                onChatBackupChange = { viewModel.setChatBackup(it) },
                                chatBackupProgress = chatBackupProgress
                            )
                        }

                        // Audio-Einstellungen
                        composable("settings/audio") {
                            AudioSettingsScreen(
                                audioQuality = preferences.audioQuality,
                                onAudioQualityChange = { viewModel.setAudioQuality(it) },
                                audioOutputChannel = preferences.audioOutputChannel,
                                onAudioOutputChannelChange = { viewModel.setAudioOutputChannel(it) },
                                bluetoothHeadsetEnabled = preferences.bluetoothHeadsetEnabled,
                                onBluetoothHeadsetEnabledChange = { viewModel.setBluetoothHeadsetEnabled(it) },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // Dezentrales Messaging / P2P-Mesh
                        composable("settings/decentralized") {
                            val peers by viewModel.p2pPeers.collectAsState()
                            val meshActive by viewModel.p2pMeshActive.collectAsState()
                            val meshStatus by viewModel.p2pMeshStatus.collectAsState()
                            DecentralizedModeScreen(
                                onNavigateBack = { navController.popBackStack() },
                                decentralizedModeEnabled = preferences.decentralizedMode,
                                onDecentralizedModeChange = { viewModel.setDecentralizedMode(it) },
                                meshActive = meshActive,
                                discoveredPeers = peers,
                                meshStatus = meshStatus,
                                myPeerId = viewModel.myP2pPeerId,
                                onScanPeers = { viewModel.scanP2pPeers() }
                            )
                        }

                        // Bereiche anpassen
                        composable("settings/app_edit") {
                            AppEditScreen(
                                hiddenNavItems = preferences.hiddenNavItems,
                                onHiddenNavItemsChange = { viewModel.setHiddenNavItems(it) },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // Info
                        composable("settings/info") {
                            InfoScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("log_viewer") {
                            LogViewerScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("family") {
                            FamilyScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // Nutzer-Profil-Ansicht
                        composable(
                            route = "user_profile/{userId}",
                            arguments = listOf(
                                navArgument("userId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val userId = backStackEntry.arguments?.getString("userId") ?: ""
                            UserProfileView(
                                userId = userId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateToSparksProfile = { navController.navigate("sparks_profile/$it") }
                            )
                        }

                        // ── Video Call Screens ────────────────────────────────────────────────

                        // Eingehender Anruf (Klingel-Screen) – Video oder Sprache
                        composable("incoming_call") {
                            val incomingCall by viewModel.incomingCall.collectAsState()
                            if (incomingCall != null) {
                                val callerId = incomingCall!!.callerId
                                val callType = incomingCall!!.callType
                                IncomingCallScreen(
                                    callerName        = incomingCall!!.callerName,
                                    callerImageUrl    = incomingCall!!.callerImageUrl,
                                    callType          = callType,
                                    isGroupCall       = incomingCall!!.isGroupCall,
                                    groupParticipants = incomingCall!!.groupParticipants,
                                    groupName         = incomingCall!!.groupName,
                                    onAccept = {
                                        // callerId + callType vor acceptCall() sichern (acceptCall setzt _incomingCall = null)
                                        val targetRoute = if (callType == "VOICE")
                                            "voice_call/$callerId"
                                        else
                                            "video_call/$callerId"
                                        navController.navigate(targetRoute) {
                                            popUpTo("incoming_call") { inclusive = true }
                                        }
                                        viewModel.acceptCall()
                                    },
                                    onDecline = {
                                        viewModel.declineCall()
                                        navController.popBackStack()
                                    }
                                )
                            }
                            // Kein else-Zweig: Wenn incomingCall null wird (Abbruch/Timeout),
                            // erledigt der LaunchedEffect(incomingCall) in MainActivity das Schließen.
                        }

                        // Aktiver Videoanruf
                        composable(
                            route = "video_call/{partnerId}",
                            arguments = listOf(
                                navArgument("partnerId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val partnerId = backStackEntry.arguments?.getString("partnerId") ?: ""
                            VideoCallScreen(
                                partnerId   = partnerId,
                                viewModel   = viewModel,
                                onCallEnded = { navController.popBackStack() },
                                onToggleScreenShare = {
                                    if (viewModel.isScreenSharing.value) {
                                        viewModel.onStopScreenShare()
                                    } else {
                                        val mgr = getSystemService(MediaProjectionManager::class.java)
                                        screenShareLauncher.launch(mgr.createScreenCaptureIntent())
                                    }
                                }
                            )
                        }

                        // Aktiver Sprachanruf
                        composable(
                            route = "voice_call/{partnerId}",
                            arguments = listOf(
                                navArgument("partnerId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val partnerId = backStackEntry.arguments?.getString("partnerId") ?: ""
                            VoiceCallScreen(
                                partnerId   = partnerId,
                                viewModel   = viewModel,
                                onCallEnded = { navController.popBackStack() }
                            )
                        }

                        // Benachrichtigungen & Töne
                        composable("settings/notifications") {
                            NotificationsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                notificationsEnabled = preferences.notificationsEnabled,
                                onNotificationsChange = { viewModel.updateNotifications(it) },
                                vibrationEnabled = preferences.vibrationEnabled,
                                onVibrationChange = { viewModel.updateVibration(it) },
                                soundEnabled = preferences.soundEnabled,
                                onSoundChange = { viewModel.updateSound(it) },
                                notificationSound = preferences.notificationSound,
                                onNotificationSoundChange = { viewModel.setNotificationSound(it) },
                                chatSoundReceiveEnabled = preferences.chatSoundReceiveEnabled,
                                onChatSoundReceiveChange = { viewModel.setChatSoundReceive(it) },
                                chatSoundSendEnabled = preferences.chatSoundSendEnabled,
                                onChatSoundSendChange = { viewModel.setChatSoundSend(it) }
                            )
                        }

                        // Backend (Admin)
                        composable(
                            route = "settings/backend?initialTab={initialTab}",
                            arguments = listOf(navArgument("initialTab") { type = NavType.IntType; defaultValue = 0 })
                        ) { backStackEntry ->
                            // Zusätzlicher Faktor: Backend-Passwort muss bei jedem Öffnen erneut bestätigt
                            // werden, bevor das Panel gerendert wird (unabhängig von der is_admin/is_moderator-Rolle).
                            var adminPanelVerified by remember { mutableStateOf(false) }
                            var verifyPassword by remember { mutableStateOf("") }
                            var verifyError by remember { mutableStateOf<String?>(null) }
                            var verifyNeedsSetup by remember { mutableStateOf(false) }
                            var verifyLoading by remember { mutableStateOf(false) }

                            if (!adminPanelVerified) {
                                AlertDialog(
                                    onDismissRequest = { navController.popBackStack() },
                                    icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    title = { Text("Backend-Passwort") },
                                    text = {
                                        Column {
                                            Text(
                                                "Bitte gib dein Backend-Passwort ein, um das Admin-/Mod-Panel zu öffnen.",
                                                fontSize = 12.sp
                                            )
                                            Spacer(Modifier.height(12.dp))
                                            OutlinedTextField(
                                                value = verifyPassword,
                                                onValueChange = { verifyPassword = it; verifyError = null },
                                                label = { Text("Passwort") },
                                                singleLine = true,
                                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            if (verifyError != null) {
                                                Spacer(Modifier.height(8.dp))
                                                Text(verifyError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                                if (verifyNeedsSetup) {
                                                    Spacer(Modifier.height(8.dp))
                                                    TextButton(onClick = {
                                                        navController.popBackStack()
                                                        navController.navigate("settings/account")
                                                    }) { Text("Zu den Account-Einstellungen") }
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                verifyLoading = true
                                                viewModel.verifyAdminPanelPassword(verifyPassword) { success, message, needsSetup ->
                                                    verifyLoading = false
                                                    if (success) {
                                                        adminPanelVerified = true
                                                    } else {
                                                        verifyError = message ?: "Falsches Backend-Passwort."
                                                        verifyNeedsSetup = needsSetup
                                                    }
                                                }
                                            },
                                            enabled = verifyPassword.isNotBlank() && !verifyLoading
                                        ) { Text("Bestätigen") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { navController.popBackStack() }) { Text("Abbrechen") }
                                    }
                                )
                                return@composable
                            }

                            val adminLogs by viewModel.adminLogs.collectAsState()
                            val serverStatus by viewModel.serverStatus.collectAsState()
                            val turnStatus by viewModel.turnStatus.collectAsState()
                            val contactListAnimation by viewModel.contactListAnimation.collectAsState()
                            val restartLog by viewModel.restartLog.collectAsState()
                            val isRestarting by viewModel.isRestarting.collectAsState()
                            val adminSearchResults by viewModel.adminSearchResults.collectAsState()
                            val adminActionMessage by viewModel.adminActionMessage.collectAsState()
                            val instancesHealth by viewModel.instancesHealth.collectAsState()
                            val adminStats by viewModel.adminStats.collectAsState()
                            val adminServerInfo by viewModel.adminServerInfo.collectAsState()
                            val adminSupportTickets by viewModel.adminSupportTickets.collectAsState()
                            val adminSupportActionMessage by viewModel.adminSupportActionMessage.collectAsState()
                            val adminSupportLoading by viewModel.adminSupportLoading.collectAsState()
                            val adminSupportLoadError by viewModel.adminSupportLoadError.collectAsState()
                            val adminCreatorApplications by viewModel.adminCreatorApplications.collectAsState()
                            val adminCreatorApplicationsLoading by viewModel.adminCreatorApplicationsLoading.collectAsState()
                            val adminCreatorApplicationsError by viewModel.adminCreatorApplicationsError.collectAsState()
                            val adminCreatorApplicationActionMessage by viewModel.adminCreatorApplicationActionMessage.collectAsState()
                            val failoverLog by viewModel.failoverLog.collectAsState()
                            val failoverRunning by viewModel.failoverRunning.collectAsState()
                            val adminUserReports by viewModel.adminUserReports.collectAsState()
                            val adminUserReportsLoading by viewModel.adminUserReportsLoading.collectAsState()
                            val adminUserReportsError by viewModel.adminUserReportsError.collectAsState()
                            val backendInitialTab = backStackEntry.arguments?.getInt("initialTab") ?: 0
                            val diamondToEuroRate by viewModel.diamondToEuroRate.collectAsState()
                            val onlyFcmMode by viewModel.onlyFcmMode.collectAsState()
                            val smsGatewayStatus by viewModel.smsGatewayStatus.collectAsState()
                            BackendScreen(
                                initialTab = backendInitialTab,
                                onNavigateBack = { navController.popBackStack() },
                                adminLogs = adminLogs,
                                onLoadAdminLogs = { viewModel.loadAdminLogs() },
                                onRestartServer = { viewModel.restartServer() },
                                onRestartTurnServer = { viewModel.restartTurnServer() },
                                onHealDatabase = { viewModel.healDatabase() },
                                serverStatus = serverStatus,
                                turnStatus = turnStatus,
                                onRefreshStatus = { viewModel.loadAdminStatus() },
                                contactListAnimation = contactListAnimation,
                                onSetAnimation = { viewModel.setContactListAnimation(it) },
                                onSendGlobalLumis = { type -> viewModel.sendAdminGlobalLumis(type) },
                                restartLog = restartLog,
                                isRestarting = isRestarting,
                                onClearRestartLog = { viewModel.clearRestartLog() },
                                adminSearchResults = adminSearchResults,
                                adminActionMessage = adminActionMessage,
                                onAdminSearch = { viewModel.adminSearchUsers(it) },
                                onAdminBlock = { viewModel.adminBlockUser(it) },
                                onAdminUnblock = { viewModel.adminUnblockUser(it) },
                                onAdminDelete = { viewModel.adminDeleteUser(it) },
                                onAdminVerifyAge = { viewModel.adminVerifyAge(it) },
                                onAdminMakeCreator = { viewModel.adminMakeCreator(it) },
                                onAdminMakeModerator = { viewModel.adminMakeModerator(it) },
                                onAdminRemoveModerator = { viewModel.adminRemoveModerator(it) },
                                onAdminResetUsers = { viewModel.adminResetUsers() },
                                onAdminResetAll = { viewModel.adminResetAll() },
                                onClearAdminActionMessage = { viewModel.clearAdminActionMessage() },
                                isModerator = currentUser?.isModerator == true,
                                instancesHealth = instancesHealth,
                                onRefreshInstances = { viewModel.refreshInstances() },
                                onStartInstance = { viewModel.startInstance(it) },
                                onStopInstance = { viewModel.stopInstance(it) },
                                onStartBackupInstance = { viewModel.startBackupInstance(it) },
                                onStopBackupInstance = { viewModel.stopBackupInstance(it) },
                                adminServerInfo = adminServerInfo,
                                onLoadAdminServerInfo = { viewModel.loadAdminServerInfo() },
                                adminStats = adminStats,
                                onLoadAdminStats = { viewModel.loadAdminStats() },
                                adminSupportTickets = adminSupportTickets,
                                adminSupportActionMessage = adminSupportActionMessage,
                                adminSupportLoading = adminSupportLoading,
                                adminSupportLoadError = adminSupportLoadError,
                                onLoadAdminSupportTickets = { status -> viewModel.loadAdminSupportTickets(status) },
                                onAdminReplySupportTicket = { id, reply, status -> viewModel.adminReplySupportTicket(id, reply, status) },
                                onAdminUpdateSupportTicketStatus = { id, status -> viewModel.adminUpdateSupportTicketStatus(id, status) },
                                onClearAdminSupportActionMessage = { viewModel.clearAdminSupportActionMessage() },
                                adminCreatorApplications = adminCreatorApplications,
                                adminCreatorApplicationsLoading = adminCreatorApplicationsLoading,
                                adminCreatorApplicationsError = adminCreatorApplicationsError,
                                adminCreatorApplicationActionMessage = adminCreatorApplicationActionMessage,
                                onLoadAdminCreatorApplications = { status -> viewModel.loadAdminCreatorApplications(status) },
                                onAdminApproveCreatorApplication = { id -> viewModel.adminApproveCreatorApplication(id) },
                                onAdminRejectCreatorApplication = { id, note -> viewModel.adminRejectCreatorApplication(id, note) },
                                onAdminMessageCreatorApplication = { id, message, onSuccess, onError ->
                                    viewModel.adminMessageCreatorApplication(id, message, onSuccess, onError)
                                },
                                onClearAdminCreatorApplicationActionMessage = { viewModel.clearAdminCreatorApplicationActionMessage() },
                                adminUserReports = adminUserReports,
                                adminUserReportsLoading = adminUserReportsLoading,
                                adminUserReportsError = adminUserReportsError,
                                onLoadAdminUserReports = { status -> viewModel.loadAdminUserReports(status) },
                                onAdminUpdateReportStatus = { id, status -> viewModel.adminUpdateReportStatus(id, status) },
                                failoverLog = failoverLog,
                                failoverRunning = failoverRunning,
                                onFailoverStatus = { viewModel.runFailoverStatus() },
                                onFailoverPromote = { viewModel.runFailoverPromote() },
                                onFailoverRecover = { viewModel.runFailoverRecover() },
                                onClearFailoverLog = { viewModel.clearFailoverLog() },
                                onNavigateToAnnouncement = {
                                    navController.navigate("lethe_team_announcement")
                                },
                                onNavigateToUserManagement = {
                                    navController.navigate("admin_user_management")
                                },
                                diamondToEuroRate = diamondToEuroRate,
                                onLoadDiamondRate = { viewModel.loadAdminDiamondRate() },
                                onSetDiamondRate = { rate -> viewModel.setDiamondRate(rate) {} },
                                onlyFcmMode = onlyFcmMode,
                                onOnlyFcmModeChange = { viewModel.setOnlyFcmMode(it) },
                                onLoadServerSettings = { viewModel.loadServerSettings() },
                                smsGatewayStatus = smsGatewayStatus,
                                onRefreshSmsGatewayStatus = { viewModel.refreshSmsGatewayStatus() },
                                onRestartSmsGateway = { viewModel.restartSmsGateway() },
                                onSendGroupAnnouncement = { targetType, content ->
                                    viewModel.adminSendAnnouncement(
                                        targetType = targetType,
                                        targetUserId = null,
                                        content = content,
                                        isNotification = false,
                                        viaSms = true,
                                        onSuccess = { count ->
                                            android.widget.Toast.makeText(this@MainActivity, "SMS an $count Nutzer gesendet", android.widget.Toast.LENGTH_LONG).show()
                                        },
                                        onError = { msg ->
                                            android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    )
                                },
                                onNavigateToMusicLibrary = {
                                    navController.navigate("music_library_manage")
                                }
                            )
                        }

                        // Lethe Team Ankündigungs-Screen (nur für Admins)
                        composable("lethe_team_announcement") {
                            val cur = currentUser
                            if (cur?.isAdmin == true) {
                                LetheTeamAnnouncementScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }

                        // Admin: Nutzer anlegen / bearbeiten / löschen
                        composable("admin_user_management") {
                            val cur = currentUser
                            if (cur?.isAdmin == true) {
                                AdminUserManagementScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }

                        // Persoenliche Musikbibliothek (Bibliothek, Favoriten, Playlists)
                        composable("my_music_library") {
                            MyMusicLibraryScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onPlayTrack = { url, title, artist ->
                                    val encUrl = java.net.URLEncoder.encode(url, "UTF-8")
                                    val encTitle = java.net.URLEncoder.encode(title ?: "", "UTF-8")
                                    val encArtist = java.net.URLEncoder.encode(artist ?: "", "UTF-8")
                                    navController.navigate("music_library_details?url=$encUrl&title=$encTitle&artist=$encArtist")
                                }
                            )
                        }

                        // Admin: Musik-Bibliothek verwalten
                        composable("music_library_manage") {
                            MusicLibraryManageScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToAddEdit = { trackId ->
                                    val route = if (trackId != null)
                                        "music_add_edit?trackId=$trackId"
                                    else
                                        "music_add_edit"
                                    navController.navigate(route)
                                }
                            )
                        }

                        // Musik hinzufuegen / bearbeiten
                        composable(
                            route = "music_add_edit?trackId={trackId}",
                            arguments = listOf(
                                navArgument("trackId") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) { entry ->
                            val trackId = entry.arguments?.getString("trackId")
                            MusicAddEditScreen(
                                trackId = trackId,
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // Bild-Editor (Zeichnen + Text vor dem Senden)
                        composable(
                            route = "image_editor?uri={uri}&chatId={chatId}&partnerId={partnerId}&caption={caption}",
                            arguments = listOf(
                                navArgument("uri") { type = NavType.StringType },
                                navArgument("chatId") { type = NavType.StringType },
                                navArgument("partnerId") { type = NavType.StringType },
                                navArgument("caption") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) { entry ->
                            val rawUri = entry.arguments?.getString("uri") ?: ""
                            val chatId = entry.arguments?.getString("chatId") ?: ""
                            val partnerId = entry.arguments?.getString("partnerId") ?: ""
                            val caption = entry.arguments?.getString("caption")
                            if (rawUri.isNotBlank()) {
                                ImageEditorScreen(
                                    imageUri = android.net.Uri.parse(rawUri),
                                    chatId = chatId,
                                    partnerId = partnerId,
                                    initialCaption = caption,
                                    viewModel = viewModel,
                                    navController = navController
                                )
                            }
                        }
                    }
                }

                // --- Globale Kontaktanfrage (egal auf welchem Screen) ---
                val globalIncomingRequest by viewModel.incomingContactRequest.collectAsState()
                globalIncomingRequest?.let { request ->
                    IncomingContactRequestDialog(
                        fromName = request.fromName,
                        fromNumber = request.fromFakeNumber,
                        onAccept = {
                            viewModel.respondToContactRequest(request.contactEntryId, "accept")
                            viewModel.clearIncomingRequest()
                        },
                        onReject = {
                            viewModel.respondToContactRequest(request.contactEntryId, "reject")
                            viewModel.clearIncomingRequest()
                        }
                    )
                }

                // --- Globaler Handshake-Erneuerungs-Dialog ---
                val globalHandshakeRenew by viewModel.incomingHandshakeRenew.collectAsState()
                globalHandshakeRenew?.let { renew ->
                    IncomingHandshakeRenewDialog(
                        fromName = renew.fromName,
                        fromNumber = renew.fromFakeNumber,
                        onAccept = {
                            viewModel.respondHandshakeRenew(renew.fromUserId, "accept")
                        },
                        onReject = {
                            viewModel.respondHandshakeRenew(renew.fromUserId, "reject")
                        }
                    )
                }

                // --- Globaler Spieleinladungs-Dialog ---
                val pendingGameInvite by viewModel.pendingGameInvite.collectAsState()
                pendingGameInvite?.let { invite ->
                    // Nicht anzeigen wenn GamesScreen oder TiltNDropScreen bereits aktiv
                    val currentRoute2 = navController.currentBackStackEntryAsState().value?.destination?.route
                    if (currentRoute2 != "games" && currentRoute2 != "tilt_n_drop") {
                        val isTiltNDrop = invite.gameType == "TILT_N_DROP"
                        val gameDisplayName = if (isTiltNDrop) "Neon Tilt 'n' Drop" else "einem Spiel"
                        AlertDialog(
                            onDismissRequest = {
                                viewModel.sendGameWsMessage("game_decline", invite.senderId, emptyMap())
                                viewModel.clearPendingGameInvite()
                            },
                            title = { Text("Spieleinladung") },
                            text = { Text("${invite.fromName} lädt dich zu $gameDisplayName ein.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    if (isTiltNDrop) {
                                        viewModel.clearPendingGameInvite()
                                        viewModel.sendGameWsMessage(
                                            "game_accept", invite.senderId,
                                            mapOf("from_name" to (viewModel.currentUser.value?.name ?: ""))
                                        )
                                        viewModel.setTiltNDropParams(
                                            partnerId   = invite.senderId,
                                            partnerName = invite.fromName,
                                            isHost      = false
                                        )
                                        navController.navigate("tilt_n_drop") { launchSingleTop = true }
                                    } else {
                                        viewModel.sendGameWsMessage(
                                            "game_accept", invite.senderId,
                                            mapOf("from_name" to (viewModel.currentUser.value?.name ?: ""))
                                        )
                                        viewModel.markGameInviteAccepted()
                                        navController.navigate("games") { launchSingleTop = true }
                                    }
                                }) { Text("Annehmen") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    viewModel.sendGameWsMessage("game_decline", invite.senderId, emptyMap())
                                    viewModel.clearPendingGameInvite()
                                }) { Text("Ablehnen") }
                            }
                        )
                    }
                }

                // --- Update-Dialog Overlay ---
                if (updateInfo != null) {
                    UpdateDialog(
                        newVersion = updateInfo!!.version,
                        changelog = updateInfo!!.changelog,
                        downloadProgress = downloadProgress,
                        onDismiss = { viewModel.dismissUpdate() },
                        onDownloadNow = {
                            viewModel.downloadUpdate(updateInfo!!.apkUrl, updateInfo!!.version)
                        },
                        onInstallNow = {
                            installReady?.let { path ->
                                val apkFile = File(path)
                                installApk(apkFile)
                                // APK NICHT sofort löschen – der Installer läuft asynchron.
                                // Die Datei wird beim nächsten App-Start gelöscht, sobald die
                                // laufende Version >= der heruntergeladenen Version ist.
                                viewModel.clearInstallReady()
                            }
                        }
                    )
                }

                // --- Globaler Progress-Indikator (Download / Upload) ---
                // Zeigt unten einen dünnen Balken bei aktiven Downloads oder Uploads
                val showDownloadProgress = downloadProgress in 0..99
                val showUploadProgress = uploadProgress >= 0f && uploadProgress < 1f
                if (showDownloadProgress || showUploadProgress) {
                    Box(
                        modifier = androidx.compose.ui.Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        if (showDownloadProgress) {
                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { uploadProgress },
                                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }

                // App-Sperre Overlay
                if (isAppLocked && preferences.appLockBiometricEnabled && viewModel.hasAppLockPin()) {
                    val activity = this@MainActivity

                    // Biometrischen Prompt automatisch beim Sperren anzeigen
                    LaunchedEffect(isAppLocked) {
                        if (isAppLocked && !showAppLockPinInput) {
                            biometricHelper.showPrompt(
                                activity = activity,
                                onSuccess = {
                                    showAppLockPinInput = false
                                    appLockPinEntry = ""
                                    appLockPinError = false
                                    viewModel.unlockApp()
                                },
                                onError = {
                                    showAppLockPinInput = true
                                }
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showAppLockPinInput) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "App entsperren",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Bitte gib deinen PIN ein",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                OutlinedTextField(
                                    value = appLockPinEntry,
                                    onValueChange = { v ->
                                        if (v.length <= 8 && v.all { it.isDigit() }) {
                                            appLockPinEntry = v
                                            appLockPinError = false
                                        }
                                    },
                                    label = { Text("PIN") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    isError = appLockPinError,
                                    supportingText = if (appLockPinError) {{ Text("Falscher PIN") }} else null,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        if (viewModel.verifyAppLockPin(appLockPinEntry)) {
                                            showAppLockPinInput = false
                                            appLockPinEntry = ""
                                            appLockPinError = false
                                            viewModel.unlockApp()
                                        } else {
                                            appLockPinError = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Entsperren")
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                TextButton(
                                    onClick = {
                                        showAppLockPinInput = false
                                        biometricHelper.showPrompt(
                                            activity = activity,
                                            onSuccess = {
                                                showAppLockPinInput = false
                                                appLockPinEntry = ""
                                                appLockPinError = false
                                                viewModel.unlockApp()
                                            },
                                            onError = {
                                                showAppLockPinInput = true
                                            }
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Fingerprint,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text("Biometrie verwenden")
                                }
                            }
                        } else {
                            // Warte auf Biometrie-Prompt
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.Fingerprint,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "App gesperrt",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { showAppLockPinInput = true }) {
                                    Text("PIN verwenden")
                                }
                            }
                        }
                    }
                }

                } // closes inner Box
                } // closes Row
            }
        }
    }

    /** Gibt true zurück wenn current älter als pending ist (z.B. "9.97.90" < "9.97.91"). */
    private fun isOlderVersion(current: String, pending: String): Boolean {
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        val p = pending.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(c.size, p.size)
        for (i in 0 until len) {
            val cv = c.getOrElse(i) { 0 }
            val pv = p.getOrElse(i) { 0 }
            if (cv < pv) return true
            if (cv > pv) return false
        }
        return false
    }

    /** Startet die Installation einer heruntergeladenen APK. */
    private fun installApk(apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !packageManager.canRequestPackageInstalls()) {
                // Nutzer zu den Einstellungen schicken um die Installation zu erlauben
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                })
            } else {
                startActivity(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("Update", "APK-Installation fehlgeschlagen: ${e.message}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Anruf-Aktionen vom Sperrbildschirm / Benachrichtigungs-Buttons
        val newIntentNavigateTo = intent.getStringExtra("navigate_to")
        when (newIntentNavigateTo) {
            "accept_call"  -> { viewModel.acceptCallFromStore(); return }
            "decline_call" -> { viewModel.declineCallFromStore(); return }
            "switch_account" -> {
                intent.getStringExtra("switch_account_profile_key")?.let { viewModel.switchAccount(it) }
                return
            }
        }

        // Kind-Familien-Einladungstoken aus Notification speichern
        val newChildInviteToken = intent.getStringExtra("child_invite_token")
        if (newChildInviteToken != null) {
            viewModel.storePendingChildInviteToken(newChildInviteToken)
        }
        // Benachrichtigungs-Deep-Link (chat_id / navigate_to) reaktiv weiterleiten
        // Bei navigate_to="nearby_chat" wird match_id als chatId weitergegeben
        val newIntentChatId = if (newIntentNavigateTo == "nearby_chat")
            intent.getStringExtra("match_id")
        else
            intent.getStringExtra("chat_id")
        viewModel.setPendingDeepLink(
            chatId = newIntentChatId,
            navigateTo = newIntentNavigateTo
        )
        // Share-Intent und Deep-Links aus anderen Apps verarbeiten
        handleIncomingIntent(intent)
    }

    /**
     * Ermittelt den tatsächlichen MIME-Typ anhand der Dateiendung, wenn der vom System
     * gemeldete Typ zu generisch ist (z.B. application/octet-stream für RAW-Dateien).
     */
    private fun resolveActualMimeType(uri: Uri, declaredType: String): String {
        // Bereits ein konkreter image/-Typ → beibehalten
        if (declaredType.startsWith("image/") && declaredType != "image/*") return declaredType

        // Dateiname aus ContentResolver ermitteln
        val fileName: String = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Exception) { null }
            ?: uri.lastPathSegment
            ?: ""

        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg"   -> "image/jpeg"
            "png"           -> "image/png"
            "bmp"           -> "image/bmp"
            "gif"           -> "image/gif"
            "webp"          -> "image/webp"
            "heic", "heif"  -> "image/heic"
            // Nikon RAW
            "nef"           -> "image/x-nikon-nef"
            "nrw"           -> "image/x-nikon-nrw"
            // Canon RAW
            "cr2"           -> "image/x-canon-cr2"
            "cr3"           -> "image/x-canon-cr3"
            // Adobe DNG / Android RAW
            "dng"           -> "image/x-adobe-dng"
            // Sony RAW
            "arw"           -> "image/x-sony-arw"
            // Fujifilm RAW
            "raf"           -> "image/x-fuji-raf"
            // Olympus RAW
            "orf"           -> "image/x-olympus-orf"
            // Panasonic RAW
            "rw2"           -> "image/x-panasonic-rw2"
            // Pentax RAW
            "pef"           -> "image/x-pentax-pef"
            else            -> declaredType
        }
    }

    private fun handleIncomingIntent(intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_SEND -> {
                val type = intent.type ?: ""
                // Direct Share: shortcut-ID enthält die userId des vorausgewählten Kontakts
                val shortcutId = intent.getStringExtra("android.intent.extra.shortcut.ID")
                when {
                    type == "text/plain" -> {
                        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                        if (text.isNotBlank()) {
                            // HLS-Playlist-URL (z.B. letheapp.de/.../playlist.m3u8) → SparksFeedScreen
                            if (text.contains(".m3u8") &&
                                (text.contains("letheapp.de") || text.startsWith("/"))) {
                                viewModel.prependSparkUrlToFeed(text.trim())
                                viewModel.setPendingDeepLink(chatId = null, navigateTo = "spark_view")
                            } else {
                                viewModel.setPendingShare(text = text, mimeType = type,
                                    targetChatId = shortcutId)
                            }
                        }
                    }
                    else -> {
                        @Suppress("DEPRECATION")
                        val uri = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                        } else {
                            intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
                        }) ?: intent.clipData?.getItemAt(0)?.uri  // Fallback: System-Screenshot-Share nutzt ClipData statt EXTRA_STREAM
                        if (uri != null) {
                            // Persistente URI-Berechtigung sichern, damit der Zugriff auch
                            // nach Activity-Hintergrundstellung noch funktioniert
                            try {
                                contentResolver.takePersistableUriPermission(
                                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (_: SecurityException) { /* URI unterstützt kein Persistieren */ }
                            // MIME-Typ anhand Dateiendung präzisieren (z.B. RAW-Formate als image/*)
                            val resolvedType = resolveActualMimeType(uri, type)
                            // Begleit-Text (z.B. Song-Empfehlung + Link aus Lethe Media Player)
                            // → wird im Bild-Editor als Bildunterschrift vorbelegt.
                            val caption = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
                            viewModel.setPendingShare(uri = uri, mimeType = resolvedType,
                                caption = caption, targetChatId = shortcutId)
                        }
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val type = intent.type ?: ""
                val shortcutId = intent.getStringExtra("android.intent.extra.shortcut.ID")
                @Suppress("DEPRECATION")
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
                        ?.filterIsInstance<Uri>()
                }
                if (!uris.isNullOrEmpty()) {
                    // Persistente Berechtigungen für alle URIs sichern
                    uris.forEach { uri ->
                        try {
                            contentResolver.takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (_: SecurityException) {}
                    }
                    // MIME-Typ anhand der ersten Datei präzisieren (alle Dateien sind typischerweise gleich)
                    val resolvedType = resolveActualMimeType(uris.first(), type)
                    viewModel.setPendingShare(uris = uris, mimeType = resolvedType,
                        targetChatId = shortcutId)
                }
            }
            // Google Assistant: "Hey Google, schreibe eine Lethe an [Name]"
            // URI-Schema: smsto:FAKE_NUMBER_ODER_NAME, Nachrichtentext in "sms_body"
            // Auch: Google Assistant App Action (actions.intent.SEND_MESSAGE aus shortcuts.xml)
            //   liefert recipient_name + message_text als Extras statt im smsto:-URI.
            Intent.ACTION_SENDTO -> {
                // App Action (Google Assistant / Android Auto): Extras bevorzugen
                val appActionRecipient = intent.getStringExtra("recipient_name")
                val appActionText = intent.getStringExtra("message_text")
                if (!appActionRecipient.isNullOrBlank()) {
                    // Assistant hat Empfänger per Sprache geliefert
                    viewModel.handleAssistantSendToIntent(
                        recipient = appActionRecipient,
                        messageBody = appActionText
                    )
                    return
                }
                // Legacy smsto-Schema: recipient aus URI lesen
                val recipient = intent.data?.schemeSpecificPart?.trimStart(':')
                    ?.takeIf { it.isNotBlank() } ?: return
                val messageBody = intent.getStringExtra("sms_body")
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                viewModel.handleAssistantSendToIntent(recipient, messageBody)
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                if (uri.scheme == "lethe" && uri.host == "add") {
                    // Deep Link: lethe://add?token=TOKEN&u=FAKE_NUMBER
                    val token = uri.getQueryParameter("token") ?: return
                    val fakeNumber = uri.getQueryParameter("u")
                    viewModel.redeemInviteToken(token, fakeNumber)
                    android.widget.Toast.makeText(
                        this,
                        "Kontaktanfrage wird gesendet…",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else if (uri.scheme == "lethe" && uri.host == "post") {
                    // Deep Link: lethe://post?C={contentId} (interner Content-Link, Legacy)
                    val contentId = uri.getQueryParameter("C") ?: uri.getQueryParameter("c") ?: return
                    viewModel.setPendingDeepLink(chatId = contentId, navigateTo = "content_view")
                } else if (uri.scheme == "lethe" && uri.host == "sp") {
                    // Deep Link: lethe://sp?id={contentId} (Spark per ID)
                    //            lethe://sp?url={hlsUrl}    (Spark per direkter Playlist-URL)
                    val urlParam = uri.getQueryParameter("url")
                    val contentId = uri.getQueryParameter("id")
                    when {
                        urlParam != null -> viewModel.prependSparkUrlToFeed(urlParam)
                        contentId != null -> viewModel.setPendingSparkId(contentId)
                        else -> return
                    }
                    viewModel.setPendingDeepLink(chatId = null, navigateTo = "spark_view")
                } else if (uri.scheme == "lethe" && uri.host == "li") {
                    // Deep Link: lethe://li?id={contentId} (Livestream)
                    val contentId = uri.getQueryParameter("id") ?: return
                    viewModel.setPendingDeepLink(chatId = contentId, navigateTo = "content_view")
                } else if (uri.scheme == "https" && uri.host == "letheapp.de" &&
                    uri.path?.startsWith("/invite.php") == true) {
                    // Deep Link: https://letheapp.de/invite.php?token=TOKEN (Web-Einladungslink)
                    uri.getQueryParameter("token")?.let { token ->
                        getSharedPreferences("lethe_invite", android.content.Context.MODE_PRIVATE)
                            .edit().putString("pending_invite_token", token).apply()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setAppForeground(true)
        // Sicherheitsnetz: falls ein Anruf durch Falschbedienung/Absturz nie sauber beendet wurde,
        // hängt das System evtl. noch im Telefonmodus (MODE_IN_COMMUNICATION) und routet Sounds über
        // die Ohrmuschel. Beim Zurückkehren in den Vordergrund den Modus zurücksetzen, falls kein
        // Anruf mehr aktiv ist.
        viewModel.ensureNoStuckCallAudioMode()
        viewModel.checkForUpdate()
        // Alle ausstehenden Benachrichtigungen löschen wenn die App in den Vordergrund kommt
        // (unabhängig davon ob der User eine Notification angetippt hat oder die App direkt geöffnet hat).
        // cancelAll() lässt Ongoing-/ForegroundService-Notifications (ID 42) unberührt.
        (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager).cancelAll()
        // Biometrie-Prompt anzeigen wenn App gesperrt
        if (viewModel.isAppLocked.value) {
            biometricHelper.showPrompt(
                activity = this,
                onSuccess = {
                    viewModel.unlockApp()
                },
                onError = {
                    // PIN-Eingabe wird durch den Overlay-Composable angezeigt
                }
            )
        }
        // USE_FULL_SCREEN_INTENT bei jedem Foreground prüfen (max. einmal pro 24 h),
        // damit Samsung / MIUI das Recht nicht still entziehen können ohne Hinweis.
        val prefs = getSharedPreferences("lethe_startup", android.content.Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong("fsi_perm_last_check_ts", 0L)
        val oneDayMs = 24L * 60 * 60 * 1000
        if (System.currentTimeMillis() - lastCheck > oneDayMs) {
            prefs.edit().putLong("fsi_perm_last_check_ts", System.currentTimeMillis()).apply()
            checkAndRequestFullScreenPermission(this)
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.setAppForeground(false)
        // App sperren wenn Biometrie-Sperre aktiv und PIN gesetzt
        if (viewModel.userPrefs.value.appLockBiometricEnabled && viewModel.hasAppLockPin()) {
            viewModel.lockApp()
        }
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch {
            cacheManager.cleanOldMedia(daysOld = 7)
            cacheManager.enforceMaxCacheSize(maxMb = 500)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (networkCallbackRegistered) {
            try {
                val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(networkCallback)
            } catch (_: Exception) {}
            networkCallbackRegistered = false
        }
        audioFocusManager.unregister()
    }
}

/**
 * Globaler Cast-Geräte-Picker (Google-frei). Zeigt die per mDNS gefundenen
 * Chromecast-Geräte als Auswahl-Dialog, sobald [CastDiscoveryManager.requestDevicePicker]
 * aufgerufen wurde. Ersetzt den früheren MediaRouteChooserDialog des Google-Cast-SDK.
 */
@Composable
private fun GlobalCastDevicePicker(manager: com.securechat.app.cast.CastDiscoveryManager) {
    val show by manager.showPicker.collectAsState()
    if (!show) return
    val devices by manager.devices.collectAsState()

    AlertDialog(
        onDismissRequest = { manager.dismissPicker() },
        icon = { Icon(Icons.Default.Cast, contentDescription = null, tint = Color(0xFF4FC3F7)) },
        title = { Text("Auf Gerät streamen", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                if (devices.isEmpty()) {
                    Text(
                        "Suche nach Cast-Geräten …",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    devices.forEach { device ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { manager.connectToDevice(device) }
                                .padding(horizontal = 4.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                Icons.Default.Cast,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { manager.dismissPicker() }) {
                Text("Abbrechen")
            }
        }
    )
}