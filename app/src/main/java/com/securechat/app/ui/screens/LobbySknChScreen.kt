package com.securechat.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.theme.topBarTitleColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler

// ─────────────────────────────────────────────────────────────────
// Wortliste
// ─────────────────────────────────────────────────────────────────
private val SKNCH_WORDS = listOf(
    // ── Alltag & Natur ───────────────────────────────────────────
    "Hund", "Katze", "Haus", "Auto", "Baum", "Sonne", "Mond", "Stern",
    "Blume", "Berg", "Fluss", "Meer", "Boot", "Flugzeug", "Zug", "Fahrrad",
    "Brücke", "Schloss", "Turm", "Pizza", "Kuchen", "Apfel", "Banane",
    "Telefon", "Computer", "Buch", "Gitarre", "Fußball", "Regenbogen",
    "Wolke", "Feuer", "Elefant", "Löwe", "Pinguin", "Delfin", "Krone",
    "Herz", "Blitz", "Rakete", "Schmetterling", "Einhorn", "Drache",
    "Roboter", "Brille", "Hut", "Uhr", "Hammer", "Schere", "Pinsel",
    "Lampe", "Stuhl", "Anker", "Kaktus", "Ballon", "Würfel", "Treppe",
    "Burg", "Insel", "Vulkan", "Wasserfall", "Sandburg", "Trompete",
    "Trommel", "Kompass", "Spiegel", "Koffer", "Zelt", "Lagerfeuer",
    "Schneemann", "Igel", "Pilz", "Palme", "Leuchtturm", "Windmühle",
    // ── Games – Nintendo / Sega / Klassiker ──────────────────────
    "Super Mario", "Luigi", "Peach", "Yoshi", "Bowser", "Toad",
    "Donkey Kong", "Link", "Zelda", "Ganon", "Pikachu", "Snorlax",
    "Kirby", "Samus", "Fox McCloud", "Captain Falcon",
    "Sonic", "Knuckles", "Tails", "Dr. Eggman",
    "Mega Man", "Pac-Man", "Tetris", "Space Invaders",
    "Minecraft", "Creeper", "Enderman", "Steve", "Diamond-Schwert",
    "Among Us", "Impostor", "Raumschiff", "Notfall-Meeting",
    "Fortnite", "Battle Bus", "Llama", "Pickaxe",
    "Lara Croft", "Master Chief", "Gordon Freeman", "Kratos",
    "Cloud Strife", "Chocobo", "Moogle", "Bahamut",
    "Crash Bandicoot", "Spyro", "Banjo-Kazooie", "Rayman",
    "Overwatch", "Tracer", "Roadhog", "Zarya",
    "Joystick", "Gamepad", "Pixelschwert", "Leben-Herz", "Power-Up",
    // ── Filme & Superhelden ──────────────────────────────────────
    "Iron Man", "Captain America", "Thor", "Hulk", "Black Widow",
    "Spider-Man", "Deadpool", "Wolverine", "Thanos", "Infinity-Handschuh",
    "Batman", "Superman", "Joker", "Wonder Woman", "The Flash",
    "Darth Vader", "Yoda", "R2-D2", "C-3PO", "BB-8",
    "Luke Skywalker", "Han Solo", "Millennium Falcon", "Lichtschwert",
    "Harry Potter", "Zauberstab", "Hogwarts", "Dobby", "Dementor",
    "Gandalf", "Frodo", "Gollum", "Der Eine Ring", "Ork",
    "Simba", "Timon", "Pumbaa", "Elsa", "Olaf",
    "Buzz Lightyear", "Woody", "Wall-E", "Nemo", "Dory",
    "Jack Sparrow", "Fliegender Holländer", "Kraken",
    "Sherlock Holmes", "James Bond", "Aston Martin",
    "King Kong", "Godzilla", "Alien", "Terminator",
    "Optimus Prime", "Bumblebee", "Megatron",
    "Shrek", "Donkey", "Puss in Boots", "Fiona",
    "Katniss", "Hunger Games", "Pfeil und Bogen",
    "Neo", "Matrix", "Rote Pille"
)

// Styx pro Runde
private const val STYX_DRAWER = 10
private const val STYX_GUESSER = 15

// ─────────────────────────────────────────────────────────────────
// Datenklassen
// ─────────────────────────────────────────────────────────────────
private enum class SknChPhase {
    LOBBY, WAITING_ROOM, PRE_START, SPECTATING,
    WORD_SELECTION, COUNTDOWN, DRAWING, GUESSING, ROUND_RESULT, GAME_OVER
}

private data class LobbyEntry(
    val gameId: String,
    val creatorId: String,
    val creatorUsername: String,
    val countdownSeconds: Int,
    val playersJoined: Int,
    val playersRequired: Int,
    val spectatorsCount: Int = 0
)

private data class SknChPlayer(
    val userId: String,
    val username: String,
    val score: Int = 0,
    val isDrawing: Boolean = false
)

private data class SknChStroke(
    val points: List<Offset>,
    val color: Color = Color.Black,
    val width: Float = 10f
)

private data class SpectMessage(
    val username: String,
    val text: String
)

// ─────────────────────────────────────────────────────────────────
// Hauptscreen
// ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbySknChScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val myUsername = currentUser?.name?.ifEmpty { currentUser?.fakeNumber } ?: "Du"
    val myUserId = currentUser?.userId ?: "me"

    // Phase
    var phase by remember { mutableStateOf(SknChPhase.LOBBY) }

    // Lobby-State – live vom Server
    var lobbyGames by remember { mutableStateOf(listOf<LobbyEntry>()) }

    // Warteraum-Einstellungen
    var selectedCountdown by remember { mutableStateOf(240) }
    var selectedPlayerCount by remember { mutableStateOf(4) }
    var waitingPlayers by remember { mutableStateOf(listOf<SknChPlayer>()) }
    var preStartCountdown by remember { mutableStateOf(20) }
    var isCreator by remember { mutableStateOf(false) }
    var publishedGameId by remember { mutableStateOf<String?>(null) }
    var isPublished by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }

    // Spielzustand
    var players by remember { mutableStateOf(listOf<SknChPlayer>()) }
    var currentWord by remember { mutableStateOf("") }
    var currentDrawerIndex by remember { mutableStateOf(0) }
    var timeLeft by remember { mutableStateOf(240) }
    var countdownValue by remember { mutableStateOf(3) }
    var correctGuessers by remember { mutableStateOf(listOf<String>()) }
    var guessInput by remember { mutableStateOf("") }
    var guessFeedback by remember { mutableStateOf<String?>(null) }
    var roundResultWord by remember { mutableStateOf("") }
    var roundResultPlayers by remember { mutableStateOf(listOf<SknChPlayer>()) }

    // Wortauswahl
    var wordOptions by remember { mutableStateOf(listOf<String>()) }
    var isDrawer by remember { mutableStateOf(false) }

    // Spectator-Chat
    var spectMessages by remember { mutableStateOf(listOf<SpectMessage>()) }
    var spectInput by remember { mutableStateOf("") }

    // Zeichenzustand – mutableStateListOf löst korrekt Recomposition aus
    val strokes = remember { mutableStateListOf<SknChStroke>() }
    val currentStrokePoints = remember { mutableStateListOf<Offset>() }
    var currentStrokeColor by remember { mutableStateOf(Color.Black) }
    var currentStrokeWidth by remember { mutableStateOf(10f) }
    var selectedColor by remember { mutableStateOf(Color.Black) }
    var strokeWidth by remember { mutableStateOf(10f) }
    var isEraser by remember { mutableStateOf(false) }

    // Hilfsfunktion: an alle anderen Spieler broadcasten
    fun broadcastToOthers(type: String, payload: Map<String, Any>) {
        players.filter { it.userId != myUserId }.forEach { player ->
            viewModel.sendGameWsMessage(type, player.userId, payload)
        }
    }

    // ── WebSocket-Events empfangen ────────────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.incomingGameEvent.collect { msg ->
            val payload = msg.payload as? Map<*, *> ?: return@collect
            when (msg.type) {
                // Ersteller sendet: Spiel startet, hier die Spielerliste
                "sknch_pre_start" -> {
                    if (phase == SknChPhase.WAITING_ROOM || phase == SknChPhase.LOBBY) {
                        @Suppress("UNCHECKED_CAST")
                        val rawPlayers = payload["players"] as? List<Map<String, String>> ?: emptyList()
                        val drawerIdx = (payload["drawer_index"] as? Number)?.toInt() ?: 0
                        players = rawPlayers.mapIndexed { idx, p ->
                            SknChPlayer(
                                userId = p["user_id"] ?: "",
                                username = p["username"] ?: "?",
                                isDrawing = idx == drawerIdx
                            )
                        }
                        currentDrawerIndex = drawerIdx
                        isDrawer = players.getOrNull(drawerIdx)?.userId == myUserId
                        preStartCountdown = (payload["countdown"] as? Number)?.toInt() ?: 20
                        phase = SknChPhase.PRE_START
                    }
                }

                // Ersteller schickt Wortoptionen an den Zeichner
                "sknch_word_options" -> {
                    if (phase == SknChPhase.WORD_SELECTION && isDrawer) {
                        @Suppress("UNCHECKED_CAST")
                        val opts = payload["options"] as? List<String> ?: emptyList()
                        wordOptions = opts
                    }
                }

                // Zeichner hat Wort gewählt – alle gehen zu COUNTDOWN
                "sknch_word_chosen" -> {
                    if (phase == SknChPhase.WORD_SELECTION && !isDrawer) {
                        correctGuessers = emptyList()
                        strokes.clear()
                        currentStrokePoints.clear()
                        guessFeedback = null
                        phase = SknChPhase.COUNTDOWN
                    }
                }

                // Zeichner überträgt aktuellen Strich live
                "sknch_draw_partial" -> {
                    if ((phase == SknChPhase.DRAWING || phase == SknChPhase.GUESSING) && !isDrawer) {
                        val pts = (payload["points"] as? List<*>)
                            ?.filterIsInstance<Number>()?.map { it.toFloat() } ?: return@collect
                        val color = Color((payload["color"] as? Number)?.toInt() ?: 0xFF000000.toInt())
                        val width = (payload["width"] as? Number)?.toFloat() ?: 10f
                        currentStrokePoints.clear()
                        var i = 0
                        while (i + 1 < pts.size) {
                            currentStrokePoints.add(Offset(pts[i], pts[i + 1]))
                            i += 2
                        }
                        currentStrokeColor = color
                        currentStrokeWidth = width
                    }
                }

                // Zeichner hat Strich abgeschlossen
                "sknch_draw_stroke" -> {
                    if ((phase == SknChPhase.DRAWING || phase == SknChPhase.GUESSING) && !isDrawer) {
                        val pts = (payload["points"] as? List<*>)
                            ?.filterIsInstance<Number>()?.map { it.toFloat() } ?: return@collect
                        val color = Color((payload["color"] as? Number)?.toInt() ?: 0xFF000000.toInt())
                        val width = (payload["width"] as? Number)?.toFloat() ?: 10f
                        val offsets = mutableListOf<Offset>()
                        var i = 0
                        while (i + 1 < pts.size) {
                            offsets.add(Offset(pts[i], pts[i + 1]))
                            i += 2
                        }
                        strokes.add(SknChStroke(offsets, color, width))
                        currentStrokePoints.clear()
                    }
                }

                // Zeichner hat alles gelöscht
                "sknch_draw_clear" -> {
                    if (phase == SknChPhase.DRAWING || phase == SknChPhase.GUESSING) {
                        strokes.clear()
                        currentStrokePoints.clear()
                    }
                }

                // Zeichner hat Rückgängig gedrückt
                "sknch_draw_undo" -> {
                    if (phase == SknChPhase.DRAWING || phase == SknChPhase.GUESSING) {
                        strokes.removeLastOrNull()
                    }
                }

                // Rater schickt Tipp an Zeichner
                "sknch_guess" -> {
                    if (phase == SknChPhase.DRAWING && isDrawer) {
                        val text = payload["text"] as? String ?: return@collect
                        val guesserUsername = payload["username"] as? String ?: "?"
                        val guesserId = msg.senderId ?: return@collect
                        if (text.trim().equals(currentWord, ignoreCase = true)) {
                            correctGuessers = correctGuessers + guesserUsername
                            players = players.map { p ->
                                if (p.userId == guesserId) p.copy(score = p.score + STYX_GUESSER)
                                else if (p.isDrawing) p.copy(score = p.score + STYX_DRAWER)
                                else p
                            }
                            broadcastToOthers("sknch_correct", mapOf("username" to guesserUsername, "guesser_id" to guesserId))
                            // Alle haben geraten → Runde beenden
                            val guessers = players.filter { !it.isDrawing }
                            if (correctGuessers.size >= guessers.size) {
                                delay(1500)
                                roundResultWord = currentWord
                                roundResultPlayers = players
                                broadcastToOthers("sknch_round_end", mapOf("word" to currentWord, "scores" to players.map { mapOf("user_id" to it.userId, "score" to it.score) }))
                                phase = SknChPhase.ROUND_RESULT
                            }
                        }
                    }
                }

                // Jemand hat richtig geraten
                "sknch_correct" -> {
                    if (phase == SknChPhase.GUESSING || phase == SknChPhase.DRAWING) {
                        val username = payload["username"] as? String ?: return@collect
                        val guesserId = payload["guesser_id"] as? String ?: return@collect
                        correctGuessers = correctGuessers + username
                        players = players.map { p ->
                            if (p.userId == guesserId) p.copy(score = p.score + STYX_GUESSER)
                            else if (p.isDrawing) p.copy(score = p.score + STYX_DRAWER)
                            else p
                        }
                        // Wenn ich der Rater bin und mein Username übereinstimmt
                        if (username == myUsername) {
                            guessFeedback = "Richtig! +$STYX_GUESSER Styx"
                            delay(1500)
                            guessFeedback = null
                        }
                    }
                }

                // Runde beendet (Zeichner sendet das, nachdem alle geraten haben oder Zeit abläuft)
                "sknch_round_end" -> {
                    if (phase == SknChPhase.GUESSING) {
                        val word = payload["word"] as? String ?: ""
                        @Suppress("UNCHECKED_CAST")
                        val scoresList = payload["scores"] as? List<Map<String, Any>> ?: emptyList()
                        players = players.map { p ->
                            val sc = scoresList.find { it["user_id"] == p.userId }
                            val newScore = (sc?.get("score") as? Number)?.toInt() ?: p.score
                            p.copy(score = newScore)
                        }
                        roundResultWord = word
                        roundResultPlayers = players
                        phase = SknChPhase.ROUND_RESULT
                    }
                }

                // Nächste Runde beginnt
                "sknch_next_round" -> {
                    if (phase == SknChPhase.ROUND_RESULT) {
                        val drawerIdx = (payload["drawer_index"] as? Number)?.toInt() ?: 0
                        currentDrawerIndex = drawerIdx
                        isDrawer = players.getOrNull(drawerIdx)?.userId == myUserId
                        players = players.mapIndexed { idx, p -> p.copy(isDrawing = idx == drawerIdx) }
                        correctGuessers = emptyList()
                        strokes.clear()
                        currentStrokePoints.clear()
                        guessFeedback = null
                        wordOptions = emptyList()
                        phase = SknChPhase.WORD_SELECTION
                    }
                }

                // Spieler hat das aktive Spiel verlassen
                "sknch_player_left" -> {
                    val leftUsername = payload["username"] as? String ?: "Ein Spieler"
                    val leftId = msg.senderId ?: ""
                    snackbarHostState.showSnackbar(
                        message = "$leftUsername hat das Spiel verlassen.",
                        duration = SnackbarDuration.Short
                    )
                    // Spieler aus der Liste entfernen
                    players = players.filter { it.userId != leftId }
                    // Zu wenig Spieler → Spiel abbrechen
                    if (players.size < 2 &&
                        phase in setOf(
                            SknChPhase.WORD_SELECTION, SknChPhase.COUNTDOWN,
                            SknChPhase.DRAWING, SknChPhase.GUESSING,
                            SknChPhase.ROUND_RESULT
                        )
                    ) {
                        phase = SknChPhase.GAME_OVER
                        snackbarHostState.showSnackbar(
                            message = "Nicht genug Spieler – Spiel beendet.",
                            duration = SnackbarDuration.Long
                        )
                    }
                }
            }
        }
    }

    // Live-Lobby-Liste – Polling alle 5 Sekunden
    LaunchedEffect(phase) {
        if (phase == SknChPhase.LOBBY) {
            while (true) {
                val games = viewModel.getSknChGames()
                lobbyGames = games.map { g ->
                    LobbyEntry(g.gameId, g.creatorId, g.creatorUsername, g.countdownSeconds, g.playersJoined, g.playersRequired, g.spectatorsCount)
                }
                delay(5_000)
            }
        }
    }

    // Warteraum-Polling: Spielerliste alle 5s aktualisieren (Ersteller)
    LaunchedEffect(phase, publishedGameId) {
        if (phase == SknChPhase.WAITING_ROOM && publishedGameId != null) {
            while (true) {
                delay(5_000)
                if (phase != SknChPhase.WAITING_ROOM) break
                val gid = publishedGameId ?: break
                val games = viewModel.getSknChGames()
                val updated = games.find { it.gameId == gid } ?: break
                waitingPlayers = updated.players.map { p -> SknChPlayer(p.userId, p.username) }
                selectedPlayerCount = updated.playersRequired
                selectedCountdown = updated.countdownSeconds
            }
        }
    }

    // Auto-Start wenn im Warteraum alle Plätze belegt sind (nur Ersteller löst aus)
    LaunchedEffect(waitingPlayers.size, selectedPlayerCount, phase) {
        if (phase == SknChPhase.WAITING_ROOM && isCreator &&
            waitingPlayers.size >= selectedPlayerCount && waitingPlayers.isNotEmpty()) {

            // Spielerzustand aufbauen
            players = waitingPlayers.mapIndexed { idx, p ->
                p.copy(isDrawing = idx == 0, score = 0)
            }
            currentDrawerIndex = 0
            isDrawer = players.getOrNull(0)?.userId == myUserId

            // PRE_START bei allen anderen Spielern auslösen
            val playerMaps = players.map { mapOf("user_id" to it.userId, "username" to it.username) }
            players.filter { it.userId != myUserId }.forEach { player ->
                viewModel.sendGameWsMessage(
                    "sknch_pre_start", player.userId,
                    mapOf("players" to playerMaps, "drawer_index" to 0, "countdown" to 20)
                )
            }

            preStartCountdown = 20
            phase = SknChPhase.PRE_START
        }
    }

    // Timer & Countdown
    LaunchedEffect(phase) {
        when (phase) {
            SknChPhase.PRE_START -> {
                while (preStartCountdown > 0) {
                    delay(1000)
                    preStartCountdown--
                }
                // Wortauswahl starten
                wordOptions = emptyList()
                correctGuessers = emptyList()
                strokes.clear()
                currentStrokePoints.clear()
                guessFeedback = null

                if (isCreator) {
                    // Ersteller schickt Wortoptionen an Zeichner (oder zeigt sie lokal)
                    val opts = SKNCH_WORDS.shuffled().take(3)
                    val drawerId = players.getOrNull(currentDrawerIndex)?.userId ?: ""
                    if (drawerId == myUserId) {
                        // Ich bin Zeichner: Optionen lokal anzeigen
                        wordOptions = opts
                    } else {
                        // Zeichner ist jemand anderes: per WS schicken
                        viewModel.sendGameWsMessage(
                            "sknch_word_options", drawerId,
                            mapOf("options" to opts)
                        )
                    }
                }
                phase = SknChPhase.WORD_SELECTION
            }
            SknChPhase.COUNTDOWN -> {
                for (i in 3 downTo 1) {
                    countdownValue = i
                    delay(1000)
                }
                phase = if (isDrawer) SknChPhase.DRAWING else SknChPhase.GUESSING
            }
            SknChPhase.DRAWING, SknChPhase.GUESSING -> {
                timeLeft = selectedCountdown
                while (timeLeft > 0 && (phase == SknChPhase.DRAWING || phase == SknChPhase.GUESSING)) {
                    delay(1000)
                    timeLeft--
                }
                if (timeLeft <= 0) {
                    roundResultWord = currentWord
                    roundResultPlayers = players
                    if (isDrawer) {
                        // Zeichner informiert alle über Rundenende
                        broadcastToOthers(
                            "sknch_round_end",
                            mapOf("word" to currentWord, "scores" to players.map { mapOf("user_id" to it.userId, "score" to it.score) })
                        )
                    }
                    phase = SknChPhase.ROUND_RESULT
                }
            }
            else -> {}
        }
    }

    val topBarTitle = when (phase) {
        SknChPhase.LOBBY -> "Sketch n Check"
        SknChPhase.WAITING_ROOM -> "Warteraum"
        SknChPhase.PRE_START -> "Spiel startet bald!"
        SknChPhase.SPECTATING -> "Zuschauen"
        SknChPhase.WORD_SELECTION -> if (isDrawer) "Wort auswählen" else "Warte auf Zeichner…"
        SknChPhase.COUNTDOWN -> "Los geht's!"
        SknChPhase.DRAWING -> "Zeichnen – ${timeLeft}s"
        SknChPhase.GUESSING -> "Raten – ${timeLeft}s"
        SknChPhase.ROUND_RESULT -> "Runde beendet"
        SknChPhase.GAME_OVER -> "Spiel beendet"
    }

    // Android-Zurück-Geste abfangen wenn Spiel aktiv
    val activeGamePhases = setOf(
        SknChPhase.WORD_SELECTION, SknChPhase.COUNTDOWN,
        SknChPhase.DRAWING, SknChPhase.GUESSING, SknChPhase.ROUND_RESULT
    )
    BackHandler(enabled = phase in activeGamePhases) {
        val leavingUser = myUsername
        players.filter { it.userId != myUserId }.forEach { p ->
            viewModel.sendGameWsMessage(
                "sknch_player_left", p.userId,
                mapOf("username" to leavingUser)
            )
        }
        scope.launch {
            publishedGameId?.let { viewModel.cancelOrLeaveSknChGame(it) }
            strokes.clear()
            players         = emptyList()
            waitingPlayers  = emptyList()
            isPublished     = false
            publishedGameId = null
            phase           = SknChPhase.LOBBY
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(topBarTitle, color = topBarTitleColor()) },
                navigationIcon = {
                    IconButton(onClick = {
                        when (phase) {
                            SknChPhase.LOBBY -> onNavigateBack()
                            SknChPhase.WAITING_ROOM -> {
                                scope.launch {
                                    if (!isCreator && publishedGameId != null) {
                                        viewModel.cancelOrLeaveSknChGame(publishedGameId!!)
                                    }
                                    strokes.clear()
                                    players = emptyList()
                                    waitingPlayers = emptyList()
                                    isPublished = false
                                    isPublishing = false
                                    publishedGameId = null
                                    phase = SknChPhase.LOBBY
                                }
                            }
                            SknChPhase.PRE_START, SknChPhase.SPECTATING, SknChPhase.GAME_OVER -> {
                                scope.launch {
                                    publishedGameId?.let { viewModel.cancelOrLeaveSknChGame(it) }
                                    strokes.clear()
                                    players = emptyList()
                                    waitingPlayers = emptyList()
                                    isPublished = false
                                    publishedGameId = null
                                    phase = SknChPhase.LOBBY
                                }
                            }
                            else -> {
                                // Aktives Spiel verlassen: alle Mitspieler benachrichtigen
                                val leavingUser = myUsername
                                players.filter { it.userId != myUserId }.forEach { p ->
                                    viewModel.sendGameWsMessage(
                                        "sknch_player_left", p.userId,
                                        mapOf("username" to leavingUser)
                                    )
                                }
                                scope.launch {
                                    publishedGameId?.let { viewModel.cancelOrLeaveSknChGame(it) }
                                    strokes.clear()
                                    players         = emptyList()
                                    waitingPlayers  = emptyList()
                                    isPublished     = false
                                    publishedGameId = null
                                    phase           = SknChPhase.LOBBY
                                }
                            }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        when (phase) {
            SknChPhase.LOBBY -> LobbyPhase(
                modifier = Modifier.padding(padding),
                games = lobbyGames,
                myUserId = myUserId,
                onStartGame = {
                    isCreator = true
                    isPublished = false
                    publishedGameId = null
                    waitingPlayers = listOf(SknChPlayer(myUserId, myUsername))
                    phase = SknChPhase.WAITING_ROOM
                },
                onSpectate = { _ ->
                    currentWord = "(Wort wird vom Server übertragen)"
                    phase = SknChPhase.SPECTATING
                },
                onJoin = { gameId ->
                    scope.launch {
                        val result = viewModel.joinSknChGame(gameId)
                        if (result != null) {
                            val isOwnGame = result.creatorId == myUserId
                            isCreator = isOwnGame
                            isPublished = true
                            publishedGameId = gameId
                            selectedCountdown = result.countdownSeconds
                            selectedPlayerCount = result.playersRequired
                            waitingPlayers = result.players.map { p ->
                                SknChPlayer(p.userId, p.username)
                            }
                            // Warten auf sknch_pre_start vom Ersteller statt sofort PRE_START
                            phase = SknChPhase.WAITING_ROOM
                        }
                    }
                }
            )

            SknChPhase.WAITING_ROOM -> WaitingRoomPhase(
                modifier = Modifier.padding(padding),
                selectedCountdown = selectedCountdown,
                selectedPlayerCount = selectedPlayerCount,
                waitingPlayers = waitingPlayers,
                isCreator = isCreator,
                isPublished = isPublished,
                isPublishing = isPublishing,
                onCountdownChange = { sec ->
                    if (isCreator) {
                        selectedCountdown = sec
                        publishedGameId?.let { id ->
                            scope.launch { viewModel.updateSknChGame(id, sec, selectedPlayerCount) }
                        }
                    }
                },
                onPlayerCountChange = { count ->
                    if (isCreator) {
                        selectedPlayerCount = count
                        publishedGameId?.let { id ->
                            scope.launch { viewModel.updateSknChGame(id, selectedCountdown, count) }
                        }
                    }
                },
                onPublish = {
                    scope.launch {
                        isPublishing = true
                        val result = viewModel.createSknChGame(selectedCountdown, selectedPlayerCount)
                        isPublishing = false
                        if (result != null) {
                            publishedGameId = result.gameId
                            isPublished = true
                            snackbarHostState.showSnackbar("Spiel veröffentlicht! Andere Spieler können jetzt beitreten.")
                        } else {
                            snackbarHostState.showSnackbar("Fehler: Spiel konnte nicht veröffentlicht werden. Bitte erneut versuchen.")
                        }
                    }
                },
                onCancel = {
                    scope.launch {
                        publishedGameId?.let { viewModel.cancelOrLeaveSknChGame(it) }
                        strokes.clear()
                        players = emptyList()
                        waitingPlayers = emptyList()
                        isPublished = false
                        isPublishing = false
                        publishedGameId = null
                        phase = SknChPhase.LOBBY
                    }
                }
            )

            SknChPhase.PRE_START -> PreStartPhase(
                modifier = Modifier.padding(padding),
                countdown = preStartCountdown,
                playerCount = players.size.takeIf { it > 0 } ?: waitingPlayers.size,
                requiredCount = selectedPlayerCount
            )

            SknChPhase.SPECTATING -> SpectatingPhase(
                modifier = Modifier.padding(padding),
                strokes = strokes,
                currentStrokePoints = currentStrokePoints,
                currentStrokeColor = currentStrokeColor,
                currentStrokeWidth = currentStrokeWidth,
                currentWord = currentWord,
                messages = spectMessages,
                messageInput = spectInput,
                onMessageChange = { spectInput = it },
                onSendMessage = {
                    if (spectInput.isNotBlank()) {
                        spectMessages = spectMessages + SpectMessage(myUsername, spectInput.trim())
                        spectInput = ""
                    }
                }
            )

            SknChPhase.WORD_SELECTION -> WordSelectionPhase(
                modifier = Modifier.padding(padding),
                isDrawer = isDrawer,
                wordOptions = wordOptions,
                onWordSelected = { word ->
                    currentWord = word
                    // Allen Spielern signalisieren dass Zeichnung beginnt (Wort nicht übertragen!)
                    broadcastToOthers("sknch_word_chosen", mapOf("ready" to true))
                    correctGuessers = emptyList()
                    strokes.clear()
                    currentStrokePoints.clear()
                    guessFeedback = null
                    phase = SknChPhase.COUNTDOWN
                }
            )

            SknChPhase.COUNTDOWN -> CountdownPhase(
                modifier = Modifier.padding(padding),
                value = countdownValue,
                word = if (isDrawer) currentWord else null
            )

            SknChPhase.DRAWING -> DrawingPhase(
                modifier = Modifier.padding(padding),
                word = currentWord,
                timeLeft = timeLeft,
                totalTime = selectedCountdown,
                strokes = strokes,
                currentStrokePoints = currentStrokePoints,
                currentStrokeColor = currentStrokeColor,
                currentStrokeWidth = currentStrokeWidth,
                selectedColor = selectedColor,
                strokeWidth = strokeWidth,
                isEraser = isEraser,
                correctGuessers = correctGuessers,
                players = players,
                onStrokeStart = { offset ->
                    currentStrokePoints.clear()
                    currentStrokePoints.add(offset)
                    currentStrokeColor = if (isEraser) Color.White else selectedColor
                    currentStrokeWidth = if (isEraser) strokeWidth * 3f else strokeWidth
                },
                onStrokeMove = { offset ->
                    currentStrokePoints.add(offset)
                    // Alle 4 Punkte partial update senden (Throttling)
                    if (currentStrokePoints.size % 4 == 0) {
                        val flatPoints = currentStrokePoints.flatMap { listOf(it.x, it.y) }
                        broadcastToOthers(
                            "sknch_draw_partial",
                            mapOf(
                                "points" to flatPoints,
                                "color" to currentStrokeColor.toArgb(),
                                "width" to currentStrokeWidth
                            )
                        )
                    }
                },
                onStrokeEnd = {
                    if (currentStrokePoints.isNotEmpty()) {
                        val stroke = SknChStroke(currentStrokePoints.toList(), currentStrokeColor, currentStrokeWidth)
                        strokes.add(stroke)
                        val flatPoints = stroke.points.flatMap { listOf(it.x, it.y) }
                        broadcastToOthers(
                            "sknch_draw_stroke",
                            mapOf(
                                "points" to flatPoints,
                                "color" to stroke.color.toArgb(),
                                "width" to stroke.width
                            )
                        )
                        currentStrokePoints.clear()
                    }
                },
                onColorChange = { selectedColor = it; isEraser = false },
                onWidthChange = { strokeWidth = it },
                onEraserToggle = { isEraser = !isEraser },
                onUndo = {
                    if (strokes.isNotEmpty()) {
                        strokes.removeLastOrNull()
                        broadcastToOthers("sknch_draw_undo", emptyMap())
                    }
                },
                onClear = {
                    strokes.clear()
                    currentStrokePoints.clear()
                    broadcastToOthers("sknch_draw_clear", emptyMap())
                }
            )

            SknChPhase.GUESSING -> GuessingPhase(
                modifier = Modifier.padding(padding),
                strokes = strokes,
                currentStrokePoints = currentStrokePoints,
                currentStrokeColor = currentStrokeColor,
                currentStrokeWidth = currentStrokeWidth,
                timeLeft = timeLeft,
                totalTime = selectedCountdown,
                guessInput = guessInput,
                feedback = guessFeedback,
                correctGuessers = correctGuessers,
                currentDrawerName = players.getOrNull(currentDrawerIndex)?.username ?: "",
                players = players,
                onGuessChange = { guessInput = it },
                onGuessSubmit = {
                    val trimmed = guessInput.trim()
                    if (trimmed.isNotEmpty()) {
                        val drawerId = players.getOrNull(currentDrawerIndex)?.userId ?: ""
                        // Tipp an Zeichner schicken (der validiert)
                        viewModel.sendGameWsMessage(
                            "sknch_guess", drawerId,
                            mapOf("text" to trimmed, "username" to myUsername)
                        )
                    }
                    guessInput = ""
                }
            )

            SknChPhase.ROUND_RESULT -> RoundResultPhase(
                modifier = Modifier.padding(padding),
                word = roundResultWord,
                players = roundResultPlayers,
                drawerStyx = STYX_DRAWER,
                guesserStyx = STYX_GUESSER,
                isCoordinator = isCreator,
                onNextRound = {
                    val eligibleDrawers = players.indices.filter { it != currentDrawerIndex }
                    if (eligibleDrawers.isEmpty()) {
                        val myFinalScore = players.find { it.userId == myUserId }?.score ?: 0
                        val winner = players.maxByOrNull { it.score }
                        val isWin = winner?.userId == myUserId
                        val opponents = players.filter { it.userId != myUserId }.joinToString(", ") { it.username }
                        viewModel.claimSknChReward(myFinalScore, if (isWin) "win" else "lose", opponents.ifEmpty { null })
                        phase = SknChPhase.GAME_OVER
                    } else {
                        val newDrawerIdx = eligibleDrawers.random()
                        currentDrawerIndex = newDrawerIdx
                        isDrawer = players.getOrNull(newDrawerIdx)?.userId == myUserId
                        players = roundResultPlayers.map { p ->
                            p.copy(isDrawing = p.userId == players[newDrawerIdx].userId)
                        }
                        correctGuessers = emptyList()
                        strokes.clear()
                        currentStrokePoints.clear()
                        guessFeedback = null
                        wordOptions = emptyList()

                        if (isCreator) {
                            // Ersteller informiert alle über neue Runde
                            broadcastToOthers("sknch_next_round", mapOf("drawer_index" to newDrawerIdx))
                            // Wortoptionen an Zeichner schicken
                            val opts = SKNCH_WORDS.shuffled().take(3)
                            val drawerId = players.getOrNull(newDrawerIdx)?.userId ?: ""
                            if (drawerId == myUserId) {
                                wordOptions = opts
                            } else {
                                viewModel.sendGameWsMessage(
                                    "sknch_word_options", drawerId,
                                    mapOf("options" to opts)
                                )
                            }
                        }
                        phase = SknChPhase.WORD_SELECTION
                    }
                }
            )

            SknChPhase.GAME_OVER -> GameOverPhase(
                modifier = Modifier.padding(padding),
                players = players.sortedByDescending { it.score },
                onBackToLobby = {
                    strokes.clear()
                    players = emptyList()
                    phase = SknChPhase.LOBBY
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Lobby
// ─────────────────────────────────────────────────────────────────
@Composable
private fun LobbyPhase(
    modifier: Modifier = Modifier,
    games: List<LobbyEntry>,
    myUserId: String,
    onStartGame: () -> Unit,
    onSpectate: (String) -> Unit,
    onJoin: (String) -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        Button(
            onClick = onStartGame,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.AddCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Öffentliches Spiel starten", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        HorizontalDivider()

        if (games.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Keine offenen Spiele", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(games, key = { it.gameId }) { entry ->
                    LobbyGameRow(
                        entry = entry,
                        isOwnGame = entry.creatorId == myUserId,
                        onSpectate = { onSpectate(entry.gameId) },
                        onJoin = { onJoin(entry.gameId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LobbyGameRow(
    entry: LobbyEntry,
    isOwnGame: Boolean,
    onSpectate: () -> Unit,
    onJoin: () -> Unit
) {
    val isFull = entry.playersJoined >= entry.playersRequired
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Brush,
            contentDescription = null,
            tint = if (isOwnGame) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(12.dp))
        // Linke Textseite – bekommt den verbleibenden Platz
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isOwnGame) "Mein Spiel" else entry.creatorUsername,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = if (isOwnGame) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${entry.countdownSeconds}s · ${entry.playersJoined}/${entry.playersRequired} Spieler",
                fontSize = 13.sp,
                color = if (isFull) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (entry.spectatorsCount > 0) {
                Text(
                    "👁 ${entry.spectatorsCount} zuschauend",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // Rechte Button-Spalte – beide Buttons untereinander, bündig rechts
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!isOwnGame) {
                OutlinedButton(
                    onClick = onSpectate,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Zuschauen", fontSize = 11.sp)
                }
            }
            Button(
                onClick = onJoin,
                enabled = isOwnGame || !isFull,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    if (isOwnGame) Icons.Default.PlayArrow else Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(if (isOwnGame) "Warteraum" else "Beitreten", fontSize = 11.sp)
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ─────────────────────────────────────────────────────────────────
// Warteraum
// ─────────────────────────────────────────────────────────────────
@Composable
private fun WaitingRoomPhase(
    modifier: Modifier = Modifier,
    selectedCountdown: Int,
    selectedPlayerCount: Int,
    waitingPlayers: List<SknChPlayer>,
    isCreator: Boolean,
    isPublished: Boolean,
    isPublishing: Boolean = false,
    onCountdownChange: (Int) -> Unit,
    onPlayerCountChange: (Int) -> Unit,
    onPublish: () -> Unit,
    onCancel: () -> Unit
) {
    val countdownOptions = listOf(120, 240, 480, 600)
    val playerOptions = listOf(2, 3, 4, 5, 6, 8, 10)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                "Warten auf Spieler",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "${waitingPlayers.size} / $selectedPlayerCount beigetreten",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (isPublished) {
                Text(
                    "Spiel ist öffentlich sichtbar",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text("Rundenzeit", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(countdownOptions) { sec ->
                    FilterChip(
                        selected = selectedCountdown == sec,
                        onClick = { onCountdownChange(sec) },
                        enabled = isCreator,
                        label = { Text("${sec}s") }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text("Spieleranzahl", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(playerOptions) { count ->
                    FilterChip(
                        selected = selectedPlayerCount == count,
                        onClick = { onPlayerCountChange(count) },
                        enabled = isCreator,
                        label = { Text("$count") }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("Beigetretene Spieler", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(waitingPlayers, key = { it.userId }) { player ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(player.username, fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isCreator && !isPublished) {
                Button(
                    onClick = onPublish,
                    enabled = !isPublishing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isPublishing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Wird veröffentlicht…", fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Default.Public, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Spiel veröffentlichen", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Text(
                text = if (isCreator) "Das Spiel startet automatisch, sobald alle Plätze belegt sind."
                       else "Warte auf den Start durch den Spielleiter …",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 72.dp)
            )
        }

        Button(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(56.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Abbrechen",
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Pre-Start Countdown
// ─────────────────────────────────────────────────────────────────
@Composable
private fun PreStartPhase(
    modifier: Modifier = Modifier,
    countdown: Int,
    playerCount: Int,
    requiredCount: Int
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "$playerCount / $requiredCount Spieler bereit",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text("Spiel startet in", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(
                "$countdown",
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text("Sekunden", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Wortauswahl
// ─────────────────────────────────────────────────────────────────
@Composable
private fun WordSelectionPhase(
    modifier: Modifier = Modifier,
    isDrawer: Boolean,
    wordOptions: List<String>,
    onWordSelected: (String) -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isDrawer) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    Icons.Default.Brush,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Du bist dran! Wähle ein Wort:",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))

                if (wordOptions.isEmpty()) {
                    CircularProgressIndicator()
                } else {
                    wordOptions.forEach { word ->
                        Button(
                            onClick = { onWordSelected(word) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(word, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    "Zeichner wählt ein Wort…",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Zuschauen
// ─────────────────────────────────────────────────────────────────
@Composable
private fun SpectatingPhase(
    modifier: Modifier = Modifier,
    strokes: List<SknChStroke>,
    currentStrokePoints: List<Offset>,
    currentStrokeColor: Color,
    currentStrokeWidth: Float,
    currentWord: String,
    messages: List<SpectMessage>,
    messageInput: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit
) {
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
        ) {
            SknChCanvas(
                strokes = strokes,
                currentStrokePoints = currentStrokePoints,
                currentStrokeColor = currentStrokeColor,
                currentStrokeWidth = currentStrokeWidth,
                interactive = false
            )
        }

        Text(
            text = "Geraten wird: $currentWord",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary
        )

        HorizontalDivider()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.7f)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages) { msg ->
                Row {
                    Text("${msg.username}: ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(msg.text, fontSize = 13.sp)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageInput,
                onValueChange = onMessageChange,
                placeholder = { Text("Nachricht…", fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendMessage(); focusManager.clearFocus() })
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { onSendMessage(); focusManager.clearFocus() },
                enabled = messageInput.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Senden",
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Countdown
// ─────────────────────────────────────────────────────────────────
@Composable
private fun CountdownPhase(
    modifier: Modifier = Modifier,
    value: Int,
    word: String?
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(
                visible = true,
                enter = scaleIn(tween(300)) + fadeIn()
            ) {
                Text(
                    text = if (value > 0) value.toString() else "Los!",
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (word != null) {
                Spacer(Modifier.height(24.dp))
                Text("Dein Wort:", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(word, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary)
            } else {
                Spacer(Modifier.height(24.dp))
                Text("Mach dich bereit zum Raten!", fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Zeichnen
// ─────────────────────────────────────────────────────────────────
@Composable
private fun DrawingPhase(
    modifier: Modifier = Modifier,
    word: String,
    timeLeft: Int,
    totalTime: Int,
    strokes: List<SknChStroke>,
    currentStrokePoints: List<Offset>,
    currentStrokeColor: Color,
    currentStrokeWidth: Float,
    selectedColor: Color,
    strokeWidth: Float,
    isEraser: Boolean,
    correctGuessers: List<String>,
    players: List<SknChPlayer>,
    onStrokeStart: (Offset) -> Unit,
    onStrokeMove: (Offset) -> Unit,
    onStrokeEnd: () -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onEraserToggle: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit
) {
    val drawColors = listOf(
        Color.Black, Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047),
        Color(0xFFFDD835), Color(0xFFFF6F00), Color(0xFF8E24AA), Color(0xFF6D4C41),
        Color.White
    )

    Column(modifier = modifier.fillMaxSize()) {
        SknChTimerBar(timeLeft = timeLeft, totalTime = totalTime)
        Text(
            text = word,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
        ) {
            SknChCanvas(
                strokes = strokes,
                currentStrokePoints = currentStrokePoints,
                currentStrokeColor = currentStrokeColor,
                currentStrokeWidth = currentStrokeWidth,
                interactive = true,
                onStrokeStart = onStrokeStart,
                onStrokeMove = onStrokeMove,
                onStrokeEnd = onStrokeEnd
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Rückgängig",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Alles löschen",
                    tint = MaterialTheme.colorScheme.error)
            }
            IconButton(onClick = onEraserToggle) {
                Icon(
                    imageVector = if (isEraser) Icons.Default.Edit else Icons.Default.AutoFixNormal,
                    contentDescription = "Radierer",
                    tint = if (isEraser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.weight(1f))
            Slider(
                value = strokeWidth,
                onValueChange = onWidthChange,
                valueRange = 4f..30f,
                modifier = Modifier.width(100.dp)
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(drawColors) { color ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (selectedColor == color && !isEraser) 3.dp else 1.dp,
                            color = if (selectedColor == color && !isEraser)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                        .clickable { onColorChange(color) }
                )
            }
        }

        if (correctGuessers.isNotEmpty()) {
            Text(
                text = "Erraten: ${correctGuessers.joinToString(", ")}",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Raten
// ─────────────────────────────────────────────────────────────────
@Composable
private fun GuessingPhase(
    modifier: Modifier = Modifier,
    strokes: List<SknChStroke>,
    currentStrokePoints: List<Offset>,
    currentStrokeColor: Color,
    currentStrokeWidth: Float,
    timeLeft: Int,
    totalTime: Int,
    guessInput: String,
    feedback: String?,
    correctGuessers: List<String>,
    currentDrawerName: String,
    players: List<SknChPlayer>,
    onGuessChange: (String) -> Unit,
    onGuessSubmit: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier.fillMaxSize()) {
        SknChTimerBar(timeLeft = timeLeft, totalTime = totalTime)

        Text(
            text = "$currentDrawerName zeichnet…",
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
        ) {
            SknChCanvas(
                strokes = strokes,
                currentStrokePoints = currentStrokePoints,
                currentStrokeColor = currentStrokeColor,
                currentStrokeWidth = currentStrokeWidth,
                interactive = false
            )
        }

        AnimatedVisibility(visible = feedback != null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = feedback ?: "",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                color = if (feedback?.startsWith("Richtig") == true)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }

        if (correctGuessers.isNotEmpty()) {
            Text(
                "Erraten von: ${correctGuessers.joinToString(", ")}",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = guessInput,
                onValueChange = onGuessChange,
                placeholder = { Text("Wort eingeben…") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onGuessSubmit(); focusManager.clearFocus() })
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onGuessSubmit(); focusManager.clearFocus() },
                enabled = guessInput.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Raten")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Rundenergebnis
// ─────────────────────────────────────────────────────────────────
@Composable
private fun RoundResultPhase(
    modifier: Modifier = Modifier,
    word: String,
    players: List<SknChPlayer>,
    drawerStyx: Int,
    guesserStyx: Int,
    isCoordinator: Boolean,
    onNextRound: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Das Wort war:", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = word,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Zeichner: +$drawerStyx Styx  ·  Errater: +$guesserStyx Styx",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(players.sortedByDescending { it.score }) { index, player ->
                PlayerScoreRow(rank = index + 1, player = player)
            }
        }

        if (isCoordinator) {
            Button(
                onClick = onNextRound,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Nächste Runde")
            }
        } else {
            Text(
                "Warte auf den Spielleiter…",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Spiel beendet
// ─────────────────────────────────────────────────────────────────
@Composable
private fun GameOverPhase(
    modifier: Modifier = Modifier,
    players: List<SknChPlayer>,
    onBackToLobby: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Icon(
            Icons.Default.EmojiEvents,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color(0xFFFFD700)
        )
        Spacer(Modifier.height(8.dp))
        Text("Spiel beendet!", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(players) { index, player ->
                PlayerScoreRow(rank = index + 1, player = player, showStyx = true)
            }
        }

        Button(
            onClick = onBackToLobby,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Home, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Zurück zur Lobby")
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Hilfscomposables
// ─────────────────────────────────────────────────────────────────

@Composable
private fun SknChTimerBar(timeLeft: Int, totalTime: Int) {
    val progress by animateFloatAsState(
        targetValue = if (totalTime > 0) timeLeft.toFloat() / totalTime.toFloat() else 0f,
        animationSpec = tween(900),
        label = "timerProgress"
    )
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
        color = when {
            progress > 0.5f -> MaterialTheme.colorScheme.primary
            progress > 0.25f -> Color(0xFFFF9800)
            else -> MaterialTheme.colorScheme.error
        },
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
private fun PlayerScoreRow(rank: Int, player: SknChPlayer, showStyx: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank.",
            modifier = Modifier.width(32.dp),
            fontWeight = FontWeight.Bold,
            color = when (rank) {
                1 -> Color(0xFFFFD700)
                2 -> Color(0xFFC0C0C0)
                3 -> Color(0xFFCD7F32)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Icon(Icons.Default.Person, contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            player.username,
            modifier = Modifier.weight(1f),
            fontWeight = if (player.isDrawing) FontWeight.SemiBold else FontWeight.Normal
        )
        if (player.isDrawing) {
            Icon(Icons.Default.Brush, contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = if (showStyx) "${player.score} Styx" else "${player.score} Pkt.",
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Zeichencanvas – interaktiv für den Zeichner, read-only für alle anderen.
 * currentStrokePoints als SnapshotStateList sorgt für korrekte Recomposition.
 */
@Composable
private fun SknChCanvas(
    strokes: List<SknChStroke>,
    currentStrokePoints: List<Offset>,
    currentStrokeColor: Color,
    currentStrokeWidth: Float,
    interactive: Boolean,
    onStrokeStart: (Offset) -> Unit = {},
    onStrokeMove: (Offset) -> Unit = {},
    onStrokeEnd: () -> Unit = {}
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (interactive) Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        onStrokeStart(down.position)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    onStrokeMove(change.position)
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        onStrokeEnd()
                    }
                } else Modifier
            )
    ) {
        drawRect(color = Color.White, size = size)

        for (stroke in strokes) {
            if (stroke.points.size < 2) {
                if (stroke.points.size == 1) {
                    drawCircle(
                        color = stroke.color,
                        radius = stroke.width / 2f,
                        center = stroke.points[0]
                    )
                }
                continue
            }
            val path = Path().apply {
                moveTo(stroke.points[0].x, stroke.points[0].y)
                for (i in 1 until stroke.points.size) {
                    lineTo(stroke.points[i].x, stroke.points[i].y)
                }
            }
            drawPath(path, color = stroke.color, style = Stroke(
                width = stroke.width,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            ))
        }

        // Aktueller Strich (in Bearbeitung)
        if (currentStrokePoints.size >= 2) {
            val path = Path().apply {
                moveTo(currentStrokePoints[0].x, currentStrokePoints[0].y)
                for (i in 1 until currentStrokePoints.size) {
                    lineTo(currentStrokePoints[i].x, currentStrokePoints[i].y)
                }
            }
            drawPath(path, color = currentStrokeColor, style = Stroke(
                width = currentStrokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            ))
        } else if (currentStrokePoints.size == 1) {
            drawCircle(
                color = currentStrokeColor,
                radius = currentStrokeWidth / 2f,
                center = currentStrokePoints[0]
            )
        }
    }
}
