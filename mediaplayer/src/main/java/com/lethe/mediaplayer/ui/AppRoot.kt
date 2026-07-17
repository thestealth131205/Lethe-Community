package com.lethe.mediaplayer.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.lethe.mediaplayer.auth.SessionResult

@Composable
fun AppRoot() {
    val vm: PlayerViewModel = hiltViewModel()
    val session by vm.session.collectAsState()
    val pendingImportUri by vm.pendingImportUri.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.loadSession() }

    var showNowPlaying by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    // Von einer Playlist-Kachel auf dem Home-Bildschirm angeforderte Playlist, die in der
    // Bibliothek direkt in ihrer Detailansicht geöffnet werden soll.
    var openPlaylistId by remember { mutableStateOf<String?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var showAppInfo by remember { mutableStateOf(false) }
    var showFriendsGroup by remember { mutableStateOf(false) }
    var showArtists by remember { mutableStateOf(false) }
    var showArtistArea by remember { mutableStateOf(false) }
    var showAdminArtists by remember { mutableStateOf(false) }
    var showAdminPlaylists by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val s = session) {
            null -> Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            is SessionResult.AppMissing -> GateScreen(
                title = "Lethe erforderlich",
                message = "Der Lethe Medie Player benötigt die installierte Lethe-App, um auf deine Bibliothek, Favoriten und Playlists zuzugreifen.",
                buttonLabel = "Lethe öffnen / installieren",
                onButton = {
                    val launch = context.packageManager.getLaunchIntentForPackage("com.Lethe.app")
                    if (launch != null) context.startActivity(launch)
                    else runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://letheapp.de"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )

            is SessionResult.NotLoggedIn -> GateScreen(
                title = "In Lethe anmelden",
                message = "Bitte melde dich zuerst in der Lethe-App an. Danach kannst du hier deine Musik abspielen.",
                buttonLabel = "Lethe öffnen",
                onButton = {
                    context.packageManager.getLaunchIntentForPackage("com.Lethe.app")?.let { context.startActivity(it) }
                }
            )

            is SessionResult.Available -> {
                if (showLibrary) {
                    BackHandler { showLibrary = false }
                    BrowseScreen(
                        vm = vm,
                        onOpenNowPlaying = { showNowPlaying = true },
                        onBack = { showLibrary = false },
                        openPlaylistId = openPlaylistId,
                        onPlaylistOpened = { openPlaylistId = null }
                    )
                } else {
                    HomeScreen(
                        vm = vm,
                        onOpenNowPlaying = { showNowPlaying = true },
                        onOpenLibrary = { showLibrary = true },
                        onOpenPlaylist = { id -> openPlaylistId = id; showLibrary = true },
                        onOpenFriendsGroup = { showFriendsGroup = true },
                        onOpenArtists = { showArtists = true },
                        onOpenArtistArea = { showArtistArea = true },
                        onOpenAdminArea = { showAdminArtists = true },
                        onOpenAdminPlaylists = { showAdminPlaylists = true },
                        onOpenAppInfo = { showAppInfo = true }
                    )
                }

                AnimatedVisibility(
                    visible = showNowPlaying,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    BackHandler { showNowPlaying = false }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        NowPlayingScreen(
                            vm = vm,
                            onCollapse = { showNowPlaying = false },
                            onOpenAudioSheet = { showAudioSheet = true },
                            onOpenQueue = { showQueue = true },
                            onPlayMix = {
                                val lib = vm.browse.value.library
                                if (lib.isNotEmpty()) {
                                    vm.playerController.play(lib, lib.indices.random())
                                    if (!vm.playerController.shuffle.value) vm.playerController.toggleShuffle()
                                }
                            }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showQueue,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    BackHandler { showQueue = false }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        QueueScreen(vm = vm, onBack = { showQueue = false })
                    }
                }

                AnimatedVisibility(
                    visible = showFriendsGroup,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    BackHandler { showFriendsGroup = false }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        FriendsGroupScreen(vm = vm, onBack = { showFriendsGroup = false })
                    }
                }

                AnimatedVisibility(
                    visible = showAppInfo,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    BackHandler { showAppInfo = false }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AppInfoScreen(vm = vm, onBack = { showAppInfo = false })
                    }
                }

                AnimatedVisibility(
                    visible = showArtists,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    BackHandler { showArtists = false }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        ArtistDiscoveryScreen(
                            vm = vm,
                            onBack = { showArtists = false },
                            onOpenNowPlaying = { showNowPlaying = true }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showArtistArea,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    BackHandler { showArtistArea = false }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        ArtistAreaScreen(vm = vm, onBack = { showArtistArea = false })
                    }
                }

                AnimatedVisibility(
                    visible = showAdminArtists,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    BackHandler { showAdminArtists = false }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AdminArtistsScreen(vm = vm, onBack = { showAdminArtists = false })
                    }
                }

                AnimatedVisibility(
                    visible = showAdminPlaylists,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    BackHandler { showAdminPlaylists = false }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AdminPlaylistsScreen(vm = vm, onBack = { showAdminPlaylists = false })
                    }
                }

                // damit s referenziert bleibt (Session-Token wird im Interceptor genutzt)
                LaunchedEffect(s) { /* Session aktiv */ }

                // Session bereits aktiv (App lief schon) + neue Datei per "Öffnen mit" übergeben
                // (MainActivity.onNewIntent) -> sofort importieren, statt nur beim App-Start.
                LaunchedEffect(pendingImportUri) {
                    if (pendingImportUri != null) vm.importPendingExternalAudioIfAny()
                }

                // Sobald der Import fertig ist und die Wiedergabe startet: Now-Playing öffnen.
                LaunchedEffect(Unit) {
                    vm.externalPlaybackEvent.collect { showNowPlaying = true }
                }
            }
        }
    }

    if (showAudioSheet) {
        AudioSettingsSheet(
            settings = vm.settings,
            castManager = vm.castManager,
            onDismiss = { showAudioSheet = false }
        )
    }
}
