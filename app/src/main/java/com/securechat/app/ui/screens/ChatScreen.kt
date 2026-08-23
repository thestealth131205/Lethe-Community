@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.securechat.app.ui.screens

import android.Manifest
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import timber.log.Timber
import android.provider.CalendarContract
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.media.MediaRecorder
import android.net.Uri
import android.location.Geocoder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.securechat.app.R
import com.securechat.app.data.local.AppTheme
import com.securechat.app.data.webrtc.WebRTCDataChannelManager
import com.securechat.app.ui.theme.BubbleOwnDark
import com.securechat.app.ui.theme.BubblePartnerDark
import com.securechat.app.ui.theme.BubblePartnerLight
import com.securechat.app.ui.theme.ChatBgDark
import com.securechat.app.ui.theme.ChatBgLight
import com.securechat.app.ui.theme.LocalAppTheme
import com.securechat.app.ui.theme.contrastColor
import com.securechat.app.ui.theme.isLightSurface
import com.securechat.app.ui.theme.topBarTitleColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.media.AudioManager

import com.securechat.app.getCurrentLocationOnce
import org.osmdroid.util.GeoPoint
import androidx.core.content.FileProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically

import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as lazyGridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.securechat.app.data.local.MessageEntity
import com.securechat.app.data.local.PollEntity
import com.securechat.app.ui.MainViewModel
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.ln
import kotlin.math.tan
import kotlin.math.cos
import kotlin.math.PI
import android.app.DownloadManager
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.runtime.derivedStateOf
import coil.compose.AsyncImage
import coil.ImageLoader
import okhttp3.OkHttpClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

// --- Datum-Trenner Hilfsdatenklassen ---
@androidx.compose.runtime.Immutable
internal sealed class ChatListItem {
    @androidx.compose.runtime.Immutable
    data class Message(
        val entity: MessageEntity,
        val formattedTime: String,
        val formattedViewedAt: String?,
        val annotatedContent: androidx.compose.ui.text.AnnotatedString?,
        val emojiOnlyCount: Int?,
        // Gruppen-Absender-Infos: EINMAL beim Listenaufbau aufgelöst (statt .find() pro Bubble).
        // Nur für fremde Gruppen-Nachrichten gesetzt, sonst null/false.
        val groupSenderName: String? = null,
        val groupSenderAvatarUrl: String? = null,
        val groupSenderIsVerified: Boolean = false,
        // Anzeigename des Absenders der zitierten Nachricht (nur im Gruppenchat aufgelöst,
        // "Du" für eigene). Sonst null → Bubble nutzt 1:1-Fallback.
        val replyToSenderName: String? = null
    ) : ChatListItem()
    @androidx.compose.runtime.Immutable
    data class DateHeader(val dateText: String) : ChatListItem()
}

// Pro Gruppen-Mitglied einmal aufgelöste Anzeige-Infos (Name/Avatar/verifiziert).
@androidx.compose.runtime.Immutable
internal data class GroupSenderDisplay(
    val name: String?,
    val avatarUrl: String?,
    val isVerified: Boolean
)

private val CHAT_HEADER_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE dd.MM.yyyy", Locale.GERMAN)
private val CHAT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

// Begrenzt die gleichzeitige stumme Vorschau von Circle-Videos: max. 2 spielen zugleich,
// beim Beenden rücken bis zu 2 wartende nach.
private object CircleVideoPreviewCoordinator {
    private const val MAX_CONCURRENT = 2
    private val active = LinkedHashSet<String>()
    private val waiting = ArrayDeque<String>()
    private val grantCallbacks = HashMap<String, () -> Unit>()

    @Synchronized
    fun request(id: String, onGrant: () -> Unit) {
        grantCallbacks[id] = onGrant
        if (id in active) { onGrant(); return }
        if (active.size < MAX_CONCURRENT) {
            active.add(id)
            onGrant()
        } else if (id !in waiting) {
            waiting.addLast(id)
        }
    }

    @Synchronized
    fun release(id: String) {
        val wasActive = active.remove(id)
        waiting.remove(id)
        if (wasActive) promoteNext()
    }

    private fun promoteNext() {
        while (active.size < MAX_CONCURRENT && waiting.isNotEmpty()) {
            val next = waiting.removeFirst()
            val cb = grantCallbacks[next] ?: continue
            active.add(next)
            cb()
        }
    }
}

private suspend fun buildChatItemList(
    messages: List<MessageEntity>,
    myUserId: String? = null,
    groupSenderLookup: Map<String, GroupSenderDisplay>? = null
): List<ChatListItem> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
    val distinct = messages.distinctBy { it.messageId?.takeIf(String::isNotBlank) ?: "local_${it.localId}" }
    val result = mutableListOf<ChatListItem>()
    var lastDate: LocalDate? = null
    val headerFormatter = CHAT_HEADER_FORMATTER
    val timeFormatter = CHAT_TIME_FORMATTER
    for (msg in distinct) { // messages ist ASC (älteste zuerst)
        val msgDate = Instant.ofEpochMilli(msg.timestamp)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        if (lastDate == null || msgDate != lastDate) {
            result.add(ChatListItem.DateHeader(msgDate.format(headerFormatter)))
            lastDate = msgDate
        }
        val formattedTime = Instant.ofEpochMilli(msg.timestamp)
            .atZone(ZoneId.systemDefault())
            .format(timeFormatter)
        val formattedViewedAt: String? = null
        val emojiCount = msg.content?.let { getEmojiOnlyCount(it) }
        val annotatedContent = if (emojiCount == null && !msg.content.isNullOrBlank()) {
            annotateWithLinks(msg.content)
        } else null
        // Gruppen-Absender nur für fremde Nachrichten auflösen (einmalig statt pro Bubble)
        val senderDisplay = if (groupSenderLookup != null && msg.senderId != myUserId) {
            groupSenderLookup[msg.senderId]
        } else null
        // Reply-Absendername im Gruppenchat auflösen: eigene → "Du", sonst Mitglieds-Anzeigename.
        // In 1:1-Chats null lassen → Bubble nutzt weiterhin partnerName/"Du"-Fallback.
        val replyToSenderName = if (groupSenderLookup != null && !msg.replyToSenderId.isNullOrBlank()) {
            if (msg.replyToSenderId == myUserId) "Du"
            else groupSenderLookup[msg.replyToSenderId]?.name ?: msg.replyToSenderId
        } else null
        result.add(ChatListItem.Message(
            entity = msg,
            formattedTime = formattedTime,
            formattedViewedAt = formattedViewedAt,
            annotatedContent = annotatedContent,
            emojiOnlyCount = emojiCount,
            groupSenderName = senderDisplay?.name,
            groupSenderAvatarUrl = senderDisplay?.avatarUrl,
            groupSenderIsVerified = senderDisplay?.isVerified == true,
            replyToSenderName = replyToSenderName
        ))
    }
    // Umkehren: neueste zuerst (für reverseLayout=true)
    result.reversed()
}

// --- Emoji-Only Erkennung & Animationen ---

// Spezielle Emoji-Codepoints (als Surrogate-Pair-Strings)
private val EMOJI_HEART_EYES = "\uD83D\uDE0D"  // 😍
private val EMOJI_KISS_FACE  = "\uD83D\uDE18"  // 😘
private val EMOJI_TONGUE_MID = "\uD83D\uDE1B"  // 😛
private val EMOJI_TONGUE_WNK = "\uD83D\uDE1C"  // 😜

/**
 * Gibt die Anzahl der Emojis zurück (1-3), wenn der Text NUR aus Emojis besteht.
 * Gibt null zurück wenn Text auch nicht-Emoji-Zeichen enthält oder > 3 Emojis.
 */
private fun getEmojiOnlyCount(text: String): Int? {
    if (text.isBlank()) return null
    var count = 0
    val codePoints = text.codePoints().iterator()
    while (codePoints.hasNext()) {
        val cp = codePoints.nextInt()
        when {
            // Modifier, Joiner, Selectors ignorieren
            cp == 0x200D || cp == 0xFE0F || cp in 0x1F3FB..0x1F3FF -> { /* skip */ }
            // Alle Emoji-Bereiche
            cp in 0x1F300..0x1FAFF -> count++   // Alle supplemental emoji (emoticons, transport, etc.)
            cp in 0x2300..0x27BF   -> count++   // Misc technical, dingbats
            cp in 0x2B00..0x2BFF   -> count++   // Misc symbols and arrows
            cp in 0x00A9..0x00AE   -> count++   // © ®
            cp.toChar().isWhitespace() -> { /* skip whitespace */ }
            else -> return null                  // Kein Emoji → kein Emoji-only
        }
    }
    return if (count in 1..3) count else null
}

/**
 * Erkennt eine Straßenadresse (mind. Straßenname + Hausnummer + Stadt) im Text.
 * Gibt die gefundene Adresse zurück oder null wenn keine erkannt.
 */
/** Berechnet die OSM-Tile-URL für gegebene Koordinaten. */
private fun osmTileUrl(lat: Double, lng: Double, zoom: Int = 15): String {
    val n = 1 shl zoom
    val xTile = ((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    val latRad = Math.toRadians(lat)
    val yTile = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
    return "https://tile.openstreetmap.org/$zoom/$xTile/$yTile.png"
}

/**
 * Liefert einen Coil-ImageLoader mit OkHttp-Interceptor, der einen App-spezifischen
 * User-Agent setzt. tile.openstreetmap.org blockiert den generischen OkHttp-User-Agent
 * ("okhttp/4.x"); .header() überschreibt diesen vollständig (addHeader würde nur ergänzen).
 * Cache-Control/Pragma: no-cache werden entfernt, damit OSM-Tile-Caching greift.
 * Wird sowohl für die Standort-Vorschau als auch für die Karten-Bubble genutzt.
 */
@Composable
private fun rememberOsmImageLoader(context: android.content.Context): ImageLoader {
    return remember(context.applicationContext) {
        ImageLoader.Builder(context.applicationContext)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addNetworkInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("User-Agent", "LetheApp/Android (contact@letheapp.de)")
                                .removeHeader("Cache-Control")
                                .removeHeader("Pragma")
                                .build()
                        )
                    }
                    .build()
            }
            .build()
    }
}

private val GOOGLE_MAPS_LINK_REGEX = Regex(
    """https?://(?:maps\.app\.goo\.gl/\S+|goo\.gl/maps/\S+|(?:www\.)?google\.com/maps\S*|maps\.google\.com\S*)"""
)
private val MAPS_COORDS_Q_REGEX = Regex("""[?&]q=([-\d.]+),([-\d.]+)""")
private val MAPS_COORDS_AT_REGEX = Regex("""/@([-\d.]+),([-\d.]+)""")

/** Erkennt Google-Maps-Links (maps.app.goo.gl, goo.gl/maps, google.com/maps, maps.google.com). */
private fun detectGoogleMapsLink(text: String): String? {
    return GOOGLE_MAPS_LINK_REGEX.find(text)?.value?.trimEnd('.')
}

/** Extrahiert Koordinaten aus einer Google-Maps-URL (diverse Formate). */
private fun extractCoordsFromMapsUrl(url: String): Pair<Double, Double>? {
    // Format: maps.google.com/?q=lat,lng oder google.com/maps?q=lat,lng
    MAPS_COORDS_Q_REGEX.find(url)?.let {
        val lat = it.groupValues[1].toDoubleOrNull()
        val lng = it.groupValues[2].toDoubleOrNull()
        if (lat != null && lng != null) return lat to lng
    }
    // Format: google.com/maps/@lat,lng,zoom
    MAPS_COORDS_AT_REGEX.find(url)?.let {
        val lat = it.groupValues[1].toDoubleOrNull()
        val lng = it.groupValues[2].toDoubleOrNull()
        if (lat != null && lng != null) return lat to lng
    }
    // Format: google.com/maps/place/.../@lat,lng oder /place/lat,lng
    Regex("""/place/([-\d.]+),([-\d.]+)""").find(url)?.let {
        val lat = it.groupValues[1].toDoubleOrNull()
        val lng = it.groupValues[2].toDoubleOrNull()
        if (lat != null && lng != null) return lat to lng
    }
    return null
}

private fun detectStreetAddress(text: String): String? {
    // Bedingung: Es muss mindestens eine 5-stellige Zahl (Postleitzahl) UND
    // eine separate 1-3-stellige Zahl (Hausnummer) im Text vorhanden sein,
    // sonst wird keine Karte angezeigt. Verhindert Fehlauslöser wie "Ring 3 von 6".
    val has5Digit = Regex("""(?<!\d)\d{5}(?!\d)""").containsMatchIn(text)
    val has1to3Digit = Regex("""(?<!\d)\d{1,3}(?!\d)""").containsMatchIn(text)
    if (!has5Digit || !has1to3Digit) return null

    val pattern = Regex(
        """[A-ZÄÖÜa-zäöüß][\wäöüÄÖÜß\-\s]{1,30}?""" +
        """(?:straße|strasse|str\.|gasse|weg|allee|ring|platz|damm|chaussee|avenue|street|road|boulevard|lane|drive)""" +
        """\s+\d{1,4}\s*[a-zA-Z]?""" +
        """[,\s]+""" +
        """\d{5}\s+""" +
        """[A-ZÄÖÜ][A-ZÄÖÜa-zäöüß\s\-]{2,30}""",
        RegexOption.IGNORE_CASE
    )
    return pattern.find(text)?.value?.trim()?.trimEnd(',')?.trim()
}

/** Einfaches Syntax-Highlighting für Code-Blöcke (rein optisch, VS Code Dark Theme). */
private fun highlightCode(code: String): androidx.compose.ui.text.AnnotatedString {
    val keywords = setOf(
        "fun", "val", "var", "if", "else", "when", "for", "while", "do", "return",
        "class", "object", "interface", "data", "sealed", "enum", "abstract", "open",
        "override", "private", "public", "protected", "internal", "companion",
        "import", "package", "suspend", "inline", "reified", "crossinline",
        "in", "out", "is", "as", "by", "true", "false", "null", "this", "super",
        "def", "let", "const", "from", "export", "default", "struct", "impl",
        "fn", "use", "pub", "mod", "trait", "type", "static", "void", "new",
        "int", "string", "bool", "float", "double", "long", "short", "byte",
        "String", "Int", "Boolean", "Float", "Double", "Long", "List", "Map",
        "try", "catch", "finally", "throw", "throws", "extends", "implements",
        "switch", "case", "break", "continue", "with", "lambda", "yield"
    )
    val colorDefault  = Color(0xFFD4D4D4)
    val colorKeyword  = Color(0xFF569CD6)
    val colorString   = Color(0xFFCE9178)
    val colorComment  = Color(0xFF6A9955)
    val colorNumber   = Color(0xFFB5CEA8)

    return buildAnnotatedString {
        var i = 0
        while (i < code.length) {
            when {
                // Zeilenkommentar //
                i + 1 < code.length && code[i] == '/' && code[i + 1] == '/' -> {
                    val end = code.indexOf('\n', i).let { if (it == -1) code.length else it }
                    withStyle(SpanStyle(color = colorComment)) { append(code.substring(i, end)) }
                    i = end
                }
                // Blockkommentar /* ... */
                i + 1 < code.length && code[i] == '/' && code[i + 1] == '*' -> {
                    val end = code.indexOf("*/", i + 2).let { if (it == -1) code.length else it + 2 }
                    withStyle(SpanStyle(color = colorComment)) { append(code.substring(i, end)) }
                    i = end
                }
                // Hash-Kommentar #
                code[i] == '#' -> {
                    val end = code.indexOf('\n', i).let { if (it == -1) code.length else it }
                    withStyle(SpanStyle(color = colorComment)) { append(code.substring(i, end)) }
                    i = end
                }
                // String-Literal "..."
                code[i] == '"' -> {
                    var j = i + 1
                    while (j < code.length && code[j] != '"') {
                        if (code[j] == '\\') { j++; if (j >= code.length) break }
                        j++
                    }
                    if (j < code.length) j++
                    withStyle(SpanStyle(color = colorString)) { append(code.substring(i, minOf(j, code.length))) }
                    i = minOf(j, code.length)
                }
                // String-Literal '...'
                code[i] == '\'' -> {
                    var j = i + 1
                    while (j < code.length && code[j] != '\'') {
                        if (code[j] == '\\') { j++; if (j >= code.length) break }
                        j++
                    }
                    if (j < code.length) j++
                    withStyle(SpanStyle(color = colorString)) { append(code.substring(i, minOf(j, code.length))) }
                    i = minOf(j, code.length)
                }
                // Zahl
                code[i].isDigit() -> {
                    var j = i
                    while (j < code.length && (code[j].isDigit() || code[j] == '.' ||
                                code[j] == 'x' || code[j] == 'X' || code[j] == '_' ||
                                (j > i && code[j] in 'a'..'f') || (j > i && code[j] in 'A'..'F'))) {
                        j++
                    }
                    withStyle(SpanStyle(color = colorNumber)) { append(code.substring(i, j)) }
                    i = j
                }
                // Bezeichner / Keyword
                code[i].isLetter() || code[i] == '_' -> {
                    var j = i
                    while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                    val word = code.substring(i, j)
                    val color = if (word in keywords) colorKeyword else colorDefault
                    withStyle(SpanStyle(color = color)) { append(word) }
                    i = j
                }
                // Alles andere
                else -> {
                    withStyle(SpanStyle(color = colorDefault)) { append(code[i].toString()) }
                    i++
                }
            }
        }
    }
}

private val URL_REGEX = Regex("""https?://[^\s<>"']+""")
private val URL_PINK = Color(0xFFE91E8C)

private fun annotateWithLinks(text: String): androidx.compose.ui.text.AnnotatedString =
    buildAnnotatedString {
        append(text)
        URL_REGEX.findAll(text).forEach { match ->
            addStyle(
                style = SpanStyle(color = URL_PINK, textDecoration = TextDecoration.Underline),
                start = match.range.first,
                end = match.range.last + 1
            )
            addStringAnnotation(
                tag = "URL",
                annotation = match.value,
                start = match.range.first,
                end = match.range.last + 1
            )
        }
    }

/** Animierter Emoji-Composable für Nachrichten mit 1-3 Emojis. */
@Composable
private fun AnimatedEmojiMessage(
    text: String,
    emojiCount: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val fontSize = when (emojiCount) {
        1    -> 64.sp
        2    -> 52.sp
        else -> 44.sp
    }

    // Eingangs-Animation: Bounce-Scale
    var appeared by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.25f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ), label = "emojiScale"
    )
    LaunchedEffect(text) { appeared = true }

    val hasHeartEyes  = EMOJI_HEART_EYES in text
    val hasKiss       = EMOJI_KISS_FACE  in text
    val hasTongueMid  = EMOJI_TONGUE_MID in text
    val hasTongueWink = EMOJI_TONGUE_WNK in text

    // 😍 Herzpuls-Animation
    val infiniteHeart = rememberInfiniteTransition(label = "heart")
    val heartScale by infiniteHeart.animateFloat(
        initialValue = 1f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(700), repeatMode = RepeatMode.Reverse
        ), label = "heartScale"
    )

    // 😘 Kuss-Animation: 💋 fliegt nach rechts oben
    var kissLaunched by remember { mutableStateOf(false) }
    val kissOffsetX by animateFloatAsState(
        targetValue = if (kissLaunched) 80f else 0f,
        animationSpec = tween(900), label = "kissX"
    )
    val kissOffsetY by animateFloatAsState(
        targetValue = if (kissLaunched) -60f else 0f,
        animationSpec = tween(900), label = "kissY"
    )
    val kissAlpha by animateFloatAsState(
        targetValue = if (kissLaunched) 0f else 1f,
        animationSpec = tween(900), label = "kissAlpha"
    )
    LaunchedEffect(text) {
        if (hasKiss) {
            delay(400)
            kissLaunched = true
        }
    }

    // 😛 / 😜 Wackeln links-rechts
    val infiniteWiggle = rememberInfiniteTransition(label = "wiggle")
    val wiggleRotation by infiniteWiggle.animateFloat(
        initialValue = -8f, targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(400), repeatMode = RepeatMode.Reverse
        ), label = "wiggleRot"
    )

    Box(
        modifier = modifier.scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // Haupt-Emoji
        Text(
            text = text,
            fontSize = fontSize,
            modifier = when {
                hasHeartEyes -> Modifier.scale(heartScale)
                hasTongueMid || hasTongueWink -> Modifier.rotate(wiggleRotation)
                else -> Modifier
            }
        )

        // 😘 Kuss-Partikel
        if (hasKiss) {
            Text(
                text = "💋",
                fontSize = (fontSize.value * 0.5f).sp,
                modifier = Modifier
                    .offset(x = kissOffsetX.dp, y = kissOffsetY.dp)
                    .alpha(kissAlpha)
            )
        }

        // 😍 Herz-Partikel (floaten nach oben)
        if (hasHeartEyes) {
            Text(
                text = "❤️",
                fontSize = (fontSize.value * 0.45f).sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .scale(heartScale * 0.8f)
                    .alpha(0.8f)
            )
        }
    }
}

// Meistgenutzte Emojis (erste Reihe) + erweiterte Liste
private val MOST_USED_EMOJIS = listOf(
    "😂", "❤️", "😍", "🙏", "😊", "🥰", "😎", "🤣", "😘", "😁",
    "👍", "🎉", "🔥", "✨", "💪", "😅", "🤔", "👀", "😭", "💯",
    "🫶", "😴", "🙄", "😜", "🤗", "🥲", "😮", "🤦", "🤷", "🤪",
    "😇", "🥳", "🤩", "😏", "😬", "🫠", "🥺", "😤", "😆", "🫂"
)

/**
 * Verfolgt Emoji-Nutzung mit Zeitstempeln und liefert eine zeitgewichtete Rangliste.
 * Halbwertszeit 48h: Emojis aus den letzten ~2 Tagen haben den stärksten Einfluss.
 * Ältere Nutzungen verlieren exponentiell an Gewicht, sodass die Liste immer aktuell bleibt.
 */
private object EmojiUsageTracker {
    private const val PREFS_NAME = "emoji_usage_v1"
    private const val KEY_USAGE  = "usage"
    private const val HALF_LIFE_HOURS = 48.0          // 2-Tage-Halbwertszeit
    private const val MAX_AGE_MS = 14L * 24 * 3_600_000 // 14 Tage behalten
    private val gson = Gson()

    private fun load(ctx: Context): MutableMap<String, MutableList<Long>> {
        val json = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USAGE, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, MutableList<Long>>>() {}.type
            gson.fromJson(json, type) ?: mutableMapOf()
        } catch (_: Exception) { mutableMapOf() }
    }

    private fun save(ctx: Context, usage: Map<String, List<Long>>) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_USAGE, gson.toJson(usage)).apply()
    }

    /** Nutzung eines Emojis aufzeichnen. */
    fun recordUsage(ctx: Context, emoji: String) {
        val usage = load(ctx)
        val now   = System.currentTimeMillis()
        val list  = usage.getOrPut(emoji) { mutableListOf() }
        list.add(now)
        usage[emoji] = list.filter { it > now - MAX_AGE_MS }.toMutableList()
        save(ctx, usage)
    }

    /** Gibt baseList nach zeitgewichtetem Score sortiert zurück. */
    fun getRankedEmojis(ctx: Context, baseList: List<String>): List<String> {
        val usage = load(ctx)
        val now   = System.currentTimeMillis()
        val k     = ln(2.0) / HALF_LIFE_HOURS
        fun score(emoji: String): Double =
            usage[emoji]?.sumOf { t ->
                kotlin.math.exp(-k * ((now - t) / 3_600_000.0))
            } ?: 0.0
        return baseList.sortedByDescending { score(it) }
    }
}

private val MORE_EMOJIS = listOf(
    // Weitere Smileys
    "😀","😃","😄","😆","😉","😋","😏","😒","😞","😔",
    "😟","😕","🙁","☹️","😣","😖","😫","😩","🥺","😢",
    "😤","😠","😡","🤬","😈","👿","💀","💩","🤡","👻",
    "👽","🤖","😺","😸","😹","😻","😼","😽","🙀","😿",
    // Gesichter mit Merkmalen
    "🥳","🥴","🥸","🤩","😵","😵‍💫","🤯","🤠","🥶","🥵",
    "😷","🤒","🤕","🤑","🤧","😇","🤓","🧐","🤫","🫨",
    "🫠","🫡","🫢","🫣","🥹","🤭","😶‍🌫️","🫥","😑","😬",
    // Gesten
    "👋","🤚","🖐️","✋","🤙","💅","🤝","🙌","👏","🤜",
    "🤛","✊","👊","🤞","🖖","🤟","🤘","👌","🤌","🫰",
    "🫵","🫱","🫲","🫳","🫴","👈","👉","👆","👇","☝️",
    "🤲","🙏","💪","🦾","🦵","🦶","👂","🦻","👃","🫀",
    // Herzen & Liebe
    "💔","🧡","💛","💚","💙","💜","🖤","🤍","🤎","❣️",
    "💕","💞","💓","💗","💖","💝","💘","❤️‍🔥","❤️‍🩹","🫶",
    "💌","💟","☮️","✌️","🕊️","💒","👫","👬","👭","🫂",
    // Natur & Wetter
    "🌸","🌺","🌻","🌹","🌷","🍀","🌿","🍁","🌊","⭐",
    "🌙","☀️","🌈","⚡","🔥","🌊","🍃","🌱","🌵","🌴",
    "🍂","🍄","🌾","🎋","🎍","🌏","🌍","🌎","🌠","🌃",
    "🌁","🌅","🌄","🌇","🌆","🏙️","🌉","🌌","🌃","🎑",
    // Essen & Trinken
    "🍕","🍔","🍟","🌮","🌯","🥙","🍜","🍝","🍣","🍱",
    "🍰","🎂","🍩","🧁","🍪","🍫","🍬","🍭","🍺","🍻",
    "🥂","🍾","☕","🧋","🥤","🧃","🫖","🍵","🧉","🍷",
    // Früchte & Gemüse
    "🍏","🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐",
    "🍈","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🍆","🥑",
    "🫛","🥦","🥬","🥒","🌶️","🫑","🌽","🥕","🫒","🧄",
    "🧅","🥔","🍠","🫚","🥐","🥯","🍞","🥖","🥨","🧀",
    // Wasser & Meer
    "💧","💦","🌊","🧜‍♀️","🧜‍♂️","🧜","🐃","🚿","🛁","🫧",
    // Aktivitäten & Hobbys
    "⚽","🏀","🎮","🎯","🏆","🥇","🎸","🎵","🎤","🎭",
    "🎨","📸","📱","💻","🚀","✈️","🚗","🏠","🎁","🛍️",
    "🎪","🎠","🎡","🎢","🎬","🎥","🎞️","📺","📻","🎼",
    "🥊","⛸️","🎿","🏂","🤸","🏋️","🤼","🤺","🏊","🚴",
    // Objekte & Symbole
    "💎","💍","👑","🏅","🎖️","🔮","💫","✨","🌟","⭐",
    "💥","🎇","🎆","🎉","🎊","🪅","🎃","🎄","🎋","🎍",
    "🔑","🗝️","🔒","🔓","💡","🔦","🕯️","💰","💳","🏧",
    "📦","📫","📮","✉️","📧","📲","☎️","📞","🔔","🔕",
    // Tiere
    "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯",
    "🦁","🐮","🐷","🐸","🐵","🙈","🙉","🙊","🐔","🐧",
    "🐦","🦆","🦅","🦉","🦇","🐺","🐗","🦄","🐝","🦋",
    // Flags & Symbole
    "🏳️","🏴","🚩","🏁","🎌","🏳️‍🌈","🏳️‍⚧️","🔴","🟠","🟡",
    "🟢","🔵","🟣","⚫","⚪","🟤","🔶","🔷","🔸","🔹",
    // Personen & Berufe
    "👶","🧒","👦","👧","🧑","👱","👨","🧔","👩","👴",
    "👵","👮","👷","💂","🕵️","👩‍⚕️","👨‍🍳","👩‍🎤","👨‍💻","👩‍🎨",
    "👰","🤵","🦸","🦹","🧙","🧝","🧛","🧟","🧞","🧜",
    // Reisen & Orte
    "🚂","🚃","🚄","🚅","🚆","🚇","🚈","🚉","🚊","🚝",
    "🚞","🚋","🚌","🚍","🚎","🚐","🚑","🚒","🚓","🚔",
    "🚕","🚖","🚗","🚘","🚙","🛻","🚚","🚛","🚜","🏎️",
    "🛵","🏍️","🛺","🛴","🚲","🛹","🛼","🛷","⛷️","🏇",
    "🚁","🛸","⛵","🚤","🛥️","🛳️","⛴️","🚢","✈️","🛩️",
    "🪂","💺","🚀","🛰️","🎡","🎢","🎠","🏗️","🏘️","🏚️",
    "🏠","🏡","🏢","🏣","🏤","🏥","🏦","🏨","🏩","🏪",
    // Gegenstände & Technik
    "📱","💻","🖥️","🖨️","⌨️","🖱️","🖲️","💽","💾","💿",
    "📀","📷","📸","📹","🎥","📽️","🎞️","📞","☎️","📟",
    "📠","📺","📻","🧭","⏱️","⏲️","⏰","🕰️","⌚","📡",
    "🔋","🔌","💡","🔦","🕯️","🪔","🧯","🛢️","💰","💴",
    // Essen extra
    "🥗","🥘","🍲","🫕","🥫","🍱","🍘","🍙","🍚","🍛",
    "🍜","🍝","🍠","🍢","🍣","🍤","🍥","🥮","🍡","🥟",
    "🥠","🥡","🦀","🦞","🦐","🦑","🦪","🍦","🍧","🍨",
    // Sport & Fitness
    "🏅","🥇","🥈","🥉","🏆","🎖️","🥊","🥋","🎽","🛹",
    "🎯","🎱","🏓","🏸","🥅","⛳","🎣","🤿","🎽","⛸️",
    "🎿","🛷","🥌","🏒","🏑","🏏","🥍","🏐","🏀","⚽",
    "🏈","🏉","🎾","🏸","🎱","🏓","🏊","🚵","🤸","🤼",
    // Musik & Kunst
    "🎵","🎶","🎸","🎹","🥁","🎷","🎺","🎻","🪕","🎙️",
    "🎚️","🎛️","🎤","🎧","📯","🪘","🪗","🪈","🎼","🎭",
    // Symbole & Zeichen
    "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💯",
    "✅","❌","⭕","🔴","🟠","🟡","🟢","🔵","🟣","⚫",
    "💢","💬","💭","🗯️","♠️","♥️","♦️","♣️","🃏","🀄",
    "🎴","🔆","🔅","📶","📳","📴","📵","🚫","⛔","🔞",
    "🔃","🔄","🔙","🔚","🔛","🔜","🔝","🆗","🆙","🆒",
    // Sternzeichen & Astrologie
    "♈","♉","♊","♋","♌","♍","♎","♏","♐","♑","♒","♓",
    "⛎","🔯","✡️","☯️","☮️","🕎","🛐","⚛️","🉑","🈴",
    // Pflanzen & Blumen
    "🌼","🌻","🌺","🌸","🌹","🥀","🌷","🌱","🌲","🌳",
    "🌴","🌵","🎋","🎍","🍀","🍁","🍂","🍃","🪴","🌾",
    // Mond & Sterne
    "🌑","🌒","🌓","🌔","🌕","🌖","🌗","🌘","🌙","🌚",
    "🌛","🌜","🌝","🌞","🪐","⭐","🌟","💫","✨","☄️"
)

private val PEOPLE_SKIN_EMOJIS = listOf(
    // Hände mit Hautfarben
    "👋🏻","👋🏼","👋🏽","👋🏾","👋🏿",
    "👍🏻","👍🏼","👍🏽","👍🏾","👍🏿",
    "👎🏻","👎🏼","👎🏽","👎🏾","👎🏿",
    "✊🏻","✊🏼","✊🏽","✊🏾","✊🏿",
    "👊🏻","👊🏼","👊🏽","👊🏾","👊🏿",
    "✌️🏻","✌️🏼","✌️🏽","✌️🏾","✌️🏿",
    "👌🏻","👌🏼","👌🏽","👌🏾","👌🏿",
    "🤌🏻","🤌🏼","🤌🏽","🤌🏾","🤌🏿",
    "🫰🏻","🫰🏼","🫰🏽","🫰🏾","🫰🏿",
    "👏🏻","👏🏼","👏🏽","👏🏾","👏🏿",
    "🙌🏻","🙌🏼","🙌🏽","🙌🏾","🙌🏿",
    "🤲🏻","🤲🏼","🤲🏽","🤲🏾","🤲🏿",
    "🙏🏻","🙏🏼","🙏🏽","🙏🏾","🙏🏿",
    "💪🏻","💪🏼","💪🏽","💪🏾","💪🏿",
    "✋🏻","✋🏼","✋🏽","✋🏾","✋🏿",
    "🖐️🏻","🖐️🏼","🖐️🏽","🖐️🏾","🖐️🏿",
    "🤚🏻","🤚🏼","🤚🏽","🤚🏾","🤚🏿",
    "🖖🏻","🖖🏼","🖖🏽","🖖🏾","🖖🏿",
    "🤟🏻","🤟🏼","🤟🏽","🤟🏾","🤟🏿",
    "🤘🏻","🤘🏼","🤘🏽","🤘🏾","🤘🏿",
    "🤙🏻","🤙🏼","🤙🏽","🤙🏾","🤙🏿",
    "🤞🏻","🤞🏼","🤞🏽","🤞🏾","🤞🏿",
    "🫵🏻","🫵🏼","🫵🏽","🫵🏾","🫵🏿",
    "👈🏻","👈🏼","👈🏽","👈🏾","👈🏿",
    "👉🏻","👉🏼","👉🏽","👉🏾","👉🏿",
    "👆🏻","👆🏼","👆🏽","👆🏾","👆🏿",
    "👇🏻","👇🏼","👇🏽","👇🏾","👇🏿",
    "☝️🏻","☝️🏼","☝️🏽","☝️🏾","☝️🏿",
    "🫶🏻","🫶🏼","🫶🏽","🫶🏾","🫶🏿",
    "🤜🏻","🤜🏼","🤜🏽","🤜🏾","🤜🏿",
    "🤛🏻","🤛🏼","🤛🏽","🤛🏾","🤛🏿",
    "💅🏻","💅🏼","💅🏽","💅🏾","💅🏿",
    "🤳🏻","🤳🏼","🤳🏽","🤳🏾","🤳🏿",
    "🫱🏻","🫱🏼","🫱🏽","🫱🏾","🫱🏿",
    "🫲🏻","🫲🏼","🫲🏽","🫲🏾","🫲🏿",
    "🫳🏻","🫳🏼","🫳🏽","🫳🏾","🫳🏿",
    "🫴🏻","🫴🏼","🫴🏽","🫴🏾","🫴🏿",
    // Personen mit Hautfarben
    "👶🏻","👶🏼","👶🏽","👶🏾","👶🏿",
    "🧒🏻","🧒🏼","🧒🏽","🧒🏾","🧒🏿",
    "👦🏻","👦🏼","👦🏽","👦🏾","👦🏿",
    "👧🏻","👧🏼","👧🏽","👧🏾","👧🏿",
    "🧑🏻","🧑🏼","🧑🏽","🧑🏾","🧑🏿",
    "👱🏻","👱🏼","👱🏽","👱🏾","👱🏿",
    "👨🏻","👨🏼","👨🏽","👨🏾","👨🏿",
    "🧔🏻","🧔🏼","🧔🏽","🧔🏾","🧔🏿",
    "👩🏻","👩🏼","👩🏽","👩🏾","👩🏿",
    "🧓🏻","🧓🏼","🧓🏽","🧓🏾","🧓🏿",
    "👴🏻","👴🏼","👴🏽","👴🏾","👴🏿",
    "👵🏻","👵🏼","👵🏽","👵🏾","👵🏿",
    "👮🏻","👮🏼","👮🏽","👮🏾","👮🏿",
    "👷🏻","👷🏼","👷🏽","👷🏾","👷🏿",
    "💂🏻","💂🏼","💂🏽","💂🏾","💂🏿",
    // Aktivitäten mit Hautfarben
    "💆🏻","💆🏼","💆🏽","💆🏾","💆🏿",
    "💇🏻","💇🏼","💇🏽","💇🏾","💇🏿",
    "🚶🏻","🚶🏼","🚶🏽","🚶🏾","🚶🏿",
    "🧍🏻","🧍🏼","🧍🏽","🧍🏾","🧍🏿",
    "🧎🏻","🧎🏼","🧎🏽","🧎🏾","🧎🏿",
    "🏃🏻","🏃🏼","🏃🏽","🏃🏾","🏃🏿",
    "💃🏻","💃🏼","💃🏽","💃🏾","💃🏿",
    "🕺🏻","🕺🏼","🕺🏽","🕺🏾","🕺🏿",
    "🏊🏻","🏊🏼","🏊🏽","🏊🏾","🏊🏿",
    "🚴🏻","🚴🏼","🚴🏽","🚴🏾","🚴🏿",
    "🤸🏻","🤸🏼","🤸🏽","🤸🏾","🤸🏿",
    "🏋️🏻","🏋️🏼","🏋️🏽","🏋️🏾","🏋️🏿",
    "🧘🏻","🧘🏼","🧘🏽","🧘🏾","🧘🏿",
    "🛀🏻","🛀🏼","🛀🏽","🛀🏾","🛀🏿",
    "🧖🏻","🧖🏼","🧖🏽","🧖🏾","🧖🏿",
    "🧗🏻","🧗🏼","🧗🏽","🧗🏾","🧗🏿",
    "🏄🏻","🏄🏼","🏄🏽","🏄🏾","🏄🏿",
    "🚣🏻","🚣🏼","🚣🏽","🚣🏾","🚣🏿",
    "🏇🏻","🏇🏼","🏇🏽","🏇🏾","🏇🏿",
    "🤺🏻","🤺🏼","🤺🏽","🤺🏾","🤺🏿",
)

/** Deutsche Stichwörter für die Emoji-Suche. Emoji → Liste von Stichwörtern (Kleinbuchstaben). */
private val EMOJI_KEYWORDS: Map<String, List<String>> = mapOf(
    // Gefühle – positiv
    "😀" to listOf("lachen", "froh", "glücklich", "freude", "grinsen"),
    "😃" to listOf("lachen", "froh", "glücklich", "freude", "grinsen"),
    "😄" to listOf("lachen", "froh", "glücklich", "freude"),
    "😁" to listOf("lachen", "grinsen", "froh", "glücklich"),
    "😆" to listOf("lachen", "haha", "lustig", "komisch"),
    "😊" to listOf("lächeln", "glücklich", "froh", "nett", "freundlich"),
    "😍" to listOf("verliebt", "liebe", "herz", "begeistert", "toll"),
    "🥰" to listOf("verliebt", "liebe", "herz", "kuscheln", "süß"),
    "😎" to listOf("cool", "sonnenbrille", "lässig", "stark"),
    "🤩" to listOf("begeistert", "wow", "super", "toll", "star"),
    "🥳" to listOf("party", "feiern", "geburtstag", "feier"),
    "😇" to listOf("engel", "heilig", "unschuldig", "gut"),
    "🤗" to listOf("umarmen", "umarmung", "herzlich", "nett"),
    "😋" to listOf("lecker", "genießen", "essen", "köstlich"),
    "😏" to listOf("frech", "wissen", "schlau", "lächeln"),
    "🫶" to listOf("liebe", "herz", "hände", "freundschaft", "lieben"),
    // Gefühle – negativ / traurig
    "😢" to listOf("traurig", "weinen", "schade", "trauer", "traurigkeit"),
    "😭" to listOf("weinen", "heulen", "traurig", "schluchzen", "trauer"),
    "😔" to listOf("traurig", "nachdenklich", "betrübt", "schade"),
    "😞" to listOf("enttäuscht", "traurig", "schade", "niedergeschlagen"),
    "😟" to listOf("besorgt", "traurig", "sorge", "unglücklich"),
    "😕" to listOf("verwirrt", "unsicher", "schade", "durcheinander"),
    "🙁" to listOf("traurig", "unglücklich", "schade"),
    "☹️" to listOf("traurig", "unglücklich", "schade", "betrübt"),
    "😣" to listOf("gestresst", "schmerz", "verzweiflung"),
    "😖" to listOf("gestresst", "verwirrt", "aufgewühlt"),
    "😩" to listOf("erschöpft", "müde", "verzweiflung", "stöhnen"),
    "😫" to listOf("erschöpft", "müde", "abgespannt"),
    "🥺" to listOf("bitte", "traurig", "flehend", "niedlich", "cute"),
    "😿" to listOf("traurig", "katze", "weinen"),
    "💔" to listOf("herz", "gebrochen", "trennung", "liebe", "traurig"),
    // Wut / Frustration
    "😡" to listOf("wut", "ärger", "wütend", "sauer"),
    "🤬" to listOf("wut", "ärger", "fluchen", "sehr wütend"),
    "😤" to listOf("frustriert", "schnaufen", "ärger", "aufgebracht"),
    "😠" to listOf("ärger", "böse", "wütend"),
    "👿" to listOf("böse", "teufel", "wut", "böser"),
    // Überraschung / Schock
    "😮" to listOf("überrascht", "oh", "schock", "wow", "staunen"),
    "😲" to listOf("schock", "überrascht", "fassungslos", "wow"),
    "😱" to listOf("schock", "angst", "schrei", "horror", "überrascht"),
    "🤯" to listOf("mind blown", "explosion", "schock", "unglaublich"),
    // Müde / Krank
    "😴" to listOf("schlafen", "müde", "schläfrig", "gute nacht"),
    "😪" to listOf("schläfrig", "müde", "schlaf"),
    "🥱" to listOf("gähnen", "müde", "langweilig"),
    "😷" to listOf("krank", "maske", "grippe", "covid"),
    "🤒" to listOf("krank", "fieber", "grippe"),
    "🤕" to listOf("verletzt", "kopfschmerzen", "schmerz"),
    // Herz & Liebe
    "❤️" to listOf("herz", "liebe", "rot"),
    "🧡" to listOf("herz", "orange", "liebe"),
    "💛" to listOf("herz", "gelb", "liebe"),
    "💚" to listOf("herz", "grün", "liebe"),
    "💙" to listOf("herz", "blau", "liebe"),
    "💜" to listOf("herz", "lila", "liebe"),
    "🖤" to listOf("herz", "schwarz", "liebe"),
    "🤍" to listOf("herz", "weiß", "liebe"),
    "💕" to listOf("liebe", "herzen", "paar"),
    "💞" to listOf("liebe", "herzen", "drehend"),
    "💗" to listOf("liebe", "herz", "wachsend"),
    "💖" to listOf("liebe", "herz", "glitzern"),
    "💘" to listOf("liebe", "amor", "herz", "pfeil"),
    "💌" to listOf("liebesbrief", "brief", "liebe"),
    "❤️‍🔥" to listOf("leidenschaft", "liebe", "feuer", "herz"),
    // Gesten & Hände
    "👍" to listOf("daumen hoch", "gut", "super", "ok", "toll"),
    "👎" to listOf("daumen runter", "schlecht", "nein", "ablehnen"),
    "👋" to listOf("winken", "hallo", "tschüss", "verabschiedung"),
    "🤝" to listOf("handschlag", "abmachung", "einigung"),
    "🙌" to listOf("klatschen", "feiern", "juhu", "beifall"),
    "👏" to listOf("klatschen", "applaus", "bravo"),
    "🙏" to listOf("bitte", "danke", "beten", "falten", "gebet"),
    "💪" to listOf("stark", "muskel", "fitness", "kraft", "stärke"),
    "✌️" to listOf("frieden", "sieg", "peace", "zwei"),
    "🤞" to listOf("daumen drücken", "glück", "hoffen", "kreuzfinger"),
    "🖖" to listOf("vulkan", "star trek", "gruß"),
    "👌" to listOf("ok", "perfekt", "gut", "okay"),
    "🤌" to listOf("perfekt", "chef küss", "italien"),
    "💅" to listOf("nagel", "chic", "schönheit", "lackieren"),
    "🫂" to listOf("umarmung", "freundschaft", "umarmen"),
    // Feiern & Spaß
    "🎉" to listOf("party", "feiern", "feier", "hurra", "konfetti"),
    "🎊" to listOf("konfetti", "party", "feiern", "feier"),
    "🎈" to listOf("luftballon", "geburtstag", "party", "feiern"),
    "🎂" to listOf("geburtstagskuchen", "geburtstag", "kuchen", "feier"),
    "🎁" to listOf("geschenk", "present", "geburtstag", "feier"),
    "🥂" to listOf("anstoßen", "feiern", "party", "champagner", "prost"),
    // Natur
    "🔥" to listOf("feuer", "heiß", "brennen", "hot"),
    "⭐" to listOf("stern", "super", "gut", "bewerung"),
    "🌟" to listOf("stern", "glänzend", "super"),
    "✨" to listOf("glitzern", "funken", "magie", "super"),
    "🌈" to listOf("regenbogen", "bunt", "farben"),
    "☀️" to listOf("sonne", "sonnig", "warm", "gut"),
    "🌙" to listOf("mond", "nacht", "schlafen"),
    "🌊" to listOf("welle", "wasser", "strand", "meer"),
    "🌸" to listOf("kirschblüte", "blume", "frühling", "japan"),
    "🌺" to listOf("blume", "hibiskus", "natur"),
    "🌻" to listOf("sonnenblume", "blume", "sommer"),
    "🌹" to listOf("rose", "liebe", "blume", "romantik"),
    // Tiere
    "🐶" to listOf("hund", "welpe", "tier", "niedlich", "cute"),
    "🐱" to listOf("katze", "mieze", "tier", "niedlich", "cute"),
    "🐰" to listOf("hase", "kaninchen", "niedlich", "ostern"),
    "🐻" to listOf("bär", "niedlich", "tier"),
    "🦊" to listOf("fuchs", "schlau", "tier"),
    "🐼" to listOf("panda", "niedlich", "tier"),
    "🐷" to listOf("schwein", "tier"),
    "🦁" to listOf("löwe", "stark", "tier", "könig"),
    "🐸" to listOf("frosch", "grün", "tier"),
    "🐵" to listOf("affe", "tier"),
    "🦋" to listOf("schmetterling", "tier", "schön"),
    // Essen
    "🍕" to listOf("pizza", "essen", "italien"),
    "🍔" to listOf("burger", "essen", "fast food"),
    "🍟" to listOf("pommes", "essen", "fast food"),
    "🍣" to listOf("sushi", "essen", "japan"),
    "🍩" to listOf("donut", "kuchen", "süß", "essen"),
    "🍫" to listOf("schokolade", "süß", "essen"),
    "🍺" to listOf("bier", "prost", "trinken", "alkohol"),
    "☕" to listOf("kaffee", "tee", "trinken", "warm"),
    "🧋" to listOf("bubble tea", "tee", "trinken"),
    // Früchte & Gemüse
    "🍏" to listOf("apfel", "grüner apfel", "frucht", "obst", "essen"),
    "🍎" to listOf("apfel", "roter apfel", "frucht", "obst", "essen"),
    "🍐" to listOf("birne", "frucht", "obst", "essen"),
    "🍊" to listOf("orange", "mandarine", "frucht", "obst", "essen"),
    "🍋" to listOf("zitrone", "limette", "sauer", "frucht", "obst", "essen"),
    "🍌" to listOf("banane", "frucht", "obst", "essen"),
    "🍉" to listOf("wassermelone", "melone", "frucht", "obst", "sommer", "essen"),
    "🍇" to listOf("trauben", "weintrauben", "frucht", "obst", "wein", "essen"),
    "🍓" to listOf("erdbeere", "frucht", "obst", "beere", "essen"),
    "🫐" to listOf("heidelbeere", "blaubeere", "beere", "frucht", "obst", "essen"),
    "🍈" to listOf("melone", "honigmelone", "frucht", "obst", "essen"),
    "🍒" to listOf("kirsche", "kirschen", "frucht", "obst", "essen"),
    "🍑" to listOf("pfirsich", "frucht", "obst", "po", "hintern", "essen"),
    "🥭" to listOf("mango", "frucht", "obst", "tropisch", "essen"),
    "🍍" to listOf("ananas", "frucht", "obst", "tropisch", "essen"),
    "🥥" to listOf("kokosnuss", "kokos", "frucht", "obst", "essen"),
    "🥝" to listOf("kiwi", "frucht", "obst", "essen"),
    "🍅" to listOf("tomate", "gemüse", "essen"),
    "🍆" to listOf("aubergine", "gemüse", "essen"),
    "🥑" to listOf("avocado", "gemüse", "frucht", "essen"),
    "🫛" to listOf("erbsen", "erbsenschote", "gemüse", "essen"),
    "🥦" to listOf("brokkoli", "gemüse", "grün", "essen"),
    "🥬" to listOf("salat", "kohl", "gemüse", "grün", "essen"),
    "🥒" to listOf("gurke", "gemüse", "essen"),
    "🌶️" to listOf("chili", "peperoni", "scharf", "gemüse", "essen"),
    "🫑" to listOf("paprika", "gemüse", "essen"),
    "🌽" to listOf("mais", "gemüse", "essen"),
    "🥕" to listOf("karotte", "möhre", "gemüse", "essen"),
    "🫒" to listOf("olive", "gemüse", "essen"),
    "🧄" to listOf("knoblauch", "gemüse", "essen"),
    "🧅" to listOf("zwiebel", "gemüse", "essen"),
    "🥔" to listOf("kartoffel", "gemüse", "essen"),
    "🍠" to listOf("süßkartoffel", "gemüse", "essen"),
    "🫚" to listOf("ingwer", "gemüse", "wurzel", "essen"),
    "🥐" to listOf("croissant", "gebäck", "essen"),
    "🥯" to listOf("bagel", "gebäck", "essen"),
    "🍞" to listOf("brot", "essen"),
    "🥖" to listOf("baguette", "brot", "essen"),
    "🥨" to listOf("brezel", "gebäck", "essen"),
    "🧀" to listOf("käse", "essen"),
    // Wasser & Meer
    "💧" to listOf("wasser", "tropfen", "tröpfchen", "flüssigkeit"),
    "💦" to listOf("wasser", "spritzen", "schweiß", "tropfen", "nass", "spritzer"),
    "🚿" to listOf("dusche", "wasser", "duschen", "bad"),
    "🛁" to listOf("badewanne", "wasser", "baden", "bad"),
    "🫧" to listOf("blasen", "seifenblasen", "wasser", "sauber", "schaum"),
    "🧜‍♀️" to listOf("meerjungfrau", "nixe", "wasser", "meer", "fabelwesen"),
    "🧜‍♂️" to listOf("wassermann", "meermann", "wasser", "meer", "fabelwesen"),
    "🧜" to listOf("meervolk", "meermensch", "wasser", "meer", "fabelwesen"),
    "🐃" to listOf("wasserbüffel", "büffel", "wasser", "tier"),
    // Sport & Aktivität
    "⚽" to listOf("fußball", "sport", "ball"),
    "🏀" to listOf("basketball", "sport", "ball"),
    "🎮" to listOf("gaming", "spiel", "videospiel", "controller"),
    "🏆" to listOf("trophäe", "gewinner", "sieger", "champion"),
    "🥇" to listOf("gold", "gewonnen", "sieger", "erster"),
    "💯" to listOf("hundert", "perfekt", "super", "toll"),
    "🎯" to listOf("ziel", "treffer", "darts", "perfekt"),
    // Musik
    "🎵" to listOf("musik", "note", "lied", "song"),
    "🎶" to listOf("musik", "noten", "melodie"),
    "🎸" to listOf("gitarre", "musik", "rock"),
    "🎤" to listOf("mikrofon", "singen", "musik", "karaoke"),
    "🎧" to listOf("kopfhörer", "musik", "hören"),
    // Technik
    "📱" to listOf("handy", "smartphone", "telefon", "nachricht"),
    "💻" to listOf("laptop", "computer", "arbeiten"),
    "📷" to listOf("kamera", "foto", "bild"),
    "🔑" to listOf("schlüssel", "öffnen", "zugang"),
    "💡" to listOf("idee", "licht", "einfall"),
    // Orte & Reise
    "✈️" to listOf("flugzeug", "fliegen", "reise", "urlaub"),
    "🚀" to listOf("rakete", "start", "schnell", "weltall"),
    "🏠" to listOf("haus", "zuhause", "home"),
    "🌍" to listOf("welt", "erde", "global"),
    // Symbole
    "✅" to listOf("haken", "richtig", "erledigt", "fertig", "ok"),
    "❌" to listOf("falsch", "nein", "fehler", "kreuz"),
    "⚠️" to listOf("warnung", "achtung", "vorsicht"),
    "🔴" to listOf("rot", "kreis", "farbe"),
    "🟢" to listOf("grün", "kreis", "farbe"),
    "🔵" to listOf("blau", "kreis", "farbe"),
    "⭕" to listOf("kreis", "richtig", "ok"),
    "💢" to listOf("ärger", "wut", "comic"),
    "💬" to listOf("nachricht", "chat", "gespräch"),
    // Sonstiges
    "🤔" to listOf("nachdenken", "hmm", "überlegen", "fragen"),
    "🙄" to listOf("augen verdrehen", "genervt", "egal"),
    "🤦" to listOf("facepalm", "kopf in hand", "ärger", "fehler"),
    "🤷" to listOf("achselzucken", "keine ahnung", "egal"),
    "👀" to listOf("augen", "schauen", "beobachten", "sehen", "schau"),
    "🫠" to listOf("schmelzen", "peinlich", "unangenehm"),
    "😬" to listOf("grimasse", "peinlich", "unangenehm"),
    "🤪" to listOf("verrückt", "albern", "silly"),
    "🥲" to listOf("lachen weinen", "rührend", "glücklich traurig"),
    "😅" to listOf("erleichtert", "nervös", "schwitzen", "lachen"),
    "😜" to listOf("zunge raus", "albern", "winking"),
)

/** Sucht Emojis anhand eines deutschen Stichworts. Durchsucht alle Listen. */
private fun searchEmojis(query: String): List<String> {
    if (query.isBlank()) return emptyList()
    val q = query.trim().lowercase()
    val allEmojis = (MOST_USED_EMOJIS + MORE_EMOJIS + PEOPLE_SKIN_EMOJIS).distinct()
    return allEmojis.filter { emoji ->
        val keywords = EMOJI_KEYWORDS[emoji] ?: return@filter false
        keywords.any { kw -> kw.contains(q) }
    }
}

@Composable
private fun P2pStatusBadge(
    viewModel: MainViewModel,
    chatId: String,
    isGroup: Boolean,
    isSelfChat: Boolean,
    p2pState: WebRTCDataChannelManager.P2PState?
) {
    val p2pPartnerDisabled by viewModel.p2pPartnerDisabled.collectAsState()
    val partnerHasDisabled = !isGroup && !isSelfChat && p2pPartnerDisabled[chatId] == true
    val p2pBadgeColor = when {
        partnerHasDisabled -> Color(0xFFFF9800)
        p2pState == WebRTCDataChannelManager.P2PState.CONNECTED    -> Color(0xFF4CAF50)
        p2pState == WebRTCDataChannelManager.P2PState.CONNECTING   -> Color(0xFFFF9800)
        p2pState == WebRTCDataChannelManager.P2PState.FAILED       -> Color(0xFFF44336)
        p2pState == WebRTCDataChannelManager.P2PState.DISCONNECTED -> Color(0xFF9E9E9E)
        else -> null
    }
    if (p2pBadgeColor != null) {
        Spacer(Modifier.width(6.dp))
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = p2pBadgeColor.copy(alpha = 0.15f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(p2pBadgeColor)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "P2P",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = p2pBadgeColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: MainViewModel,
    chatId: String,
    isGroup: Boolean = false,
    bubbleColor: Color = MaterialTheme.colorScheme.primary,
    partnerBubbleColor: Color = Color.White,
    bubbleColor2: Color = bubbleColor,
    partnerBubbleColor2: Color = partnerBubbleColor,
    focusBorderColor: Color = bubbleColor,
    focusBorderColor2: Color = bubbleColor2,
    onNavigateBack: () -> Unit,
    onNavigateToUserProfile: ((String) -> Unit)? = null,
    onNavigateToImageEditor: ((Uri) -> Unit)? = null,
    onNavigateToMultiImageEditor: ((List<Uri>) -> Unit)? = null,
    onOpenDocument: ((url: String, fileName: String) -> Unit)? = null,
    onNavigateTo3DViewer: ((fileUrl: String, filename: String, textureUrl: String) -> Unit)? = null,
    onNavigateToContent: ((contentId: String) -> Unit)? = null,
    onNavigateToSpark: ((sparkId: String) -> Unit)? = null,
    onNavigateToLiveMaps: ((chatId: String) -> Unit)? = null,
    onNavigateToGames: ((partnerId: String, partnerName: String) -> Unit)? = null,
    onNavigateToPinball: (() -> Unit)? = null,
    onNavigateToJumpOrDie: (() -> Unit)? = null,
    onNavigateToVideoCall: (() -> Unit)? = null,
    onNavigateToVideoEditor: ((Uri) -> Unit)? = null,
    onNavigateToVideoEditorEmpty: (() -> Unit)? = null,
    fontSizeMultiplier: Float = 1.0f,
    avatarSizeMultiplier: Float = 1.0f,
    globalChatBackgroundUri: String? = null,
    onNavigateToCoins: (() -> Unit)? = null
) {
    val messages by viewModel.getMessagesForChat(chatId).collectAsState(initial = emptyList())
    val isLoadingOlderMessages by viewModel.isLoadingOlderMessages.collectAsState()
    val playedAudioUrlsInChat by viewModel.playedAudioUrls.collectAsState()
    // Für jede Audio-URL: die URL der nächsten noch nicht abgehörten Sprachnachricht (auto-next)
    val audioNextUrlMap: Map<String, String?> = remember(messages, playedAudioUrlsInChat) {
        val audioMsgs = messages.filter { it.mediaType == "audio" && !it.mediaUrl.isNullOrBlank() }
        // Einmal rückwärts laufen (O(n)): jeweils die nächste noch nicht abgehörte URL mitführen
        val result = HashMap<String, String?>(audioMsgs.size)
        var nextUnplayed: String? = null
        for (i in audioMsgs.indices.reversed()) {
            val url = audioMsgs[i].mediaUrl ?: continue
            result[url] = nextUnplayed
            if (url !in playedAudioUrlsInChat) nextUnplayed = url
        }
        result
    }
    // Musik-Nachrichten: Prev/Next für MusicMessagePlayer + Playlist für ViewModel
    val allChatMusicUrls: List<String> = remember(messages) {
        messages.filter { it.mediaType == "audio_music" && !it.mediaUrl.isNullOrBlank() }
            .mapNotNull { it.mediaUrl }
    }
    val musicPrevUrlMap: Map<String, String?> = remember(allChatMusicUrls) {
        allChatMusicUrls.mapIndexed { idx, url ->
            url to allChatMusicUrls.getOrNull(idx - 1)
        }.toMap()
    }
    val musicNextUrlMap: Map<String, String?> = remember(allChatMusicUrls) {
        allChatMusicUrls.mapIndexed { idx, url ->
            url to allChatMusicUrls.getOrNull(idx + 1)
        }.toMap()
    }
    // Playlist immer aktuell halten wenn neue Musik-Nachrichten ankommen
    // + Metadaten beim Betreten des Chats vorab laden (ID3-Tags + Cover in DB-Cache)
    LaunchedEffect(allChatMusicUrls) {
        viewModel.setMusicChatUrls(allChatMusicUrls, chatId)
        viewModel.prefetchMusicMetadata(allChatMusicUrls)
    }
    val contacts by viewModel.contacts.collectAsState(initial = emptyList())
    val groups by viewModel.groups.collectAsState(initial = emptyList())
    val contact = contacts.find { it.userId == chatId }
    val contactStatus by viewModel.contactStatus.collectAsState()
    val partnerIsTyping by viewModel.partnerIsTyping.collectAsState()
    val typingContactIds by viewModel.typingContactIds.collectAsState()
    val typingGroupMembers by viewModel.typingGroupMembers.collectAsState()
    val linkPreview by viewModel.linkPreview.collectAsState()
    val linkPreviewLoading by viewModel.linkPreviewLoading.collectAsState()
    val allScheduledMessages by viewModel.scheduledMessages.collectAsState()
    val myUserId = viewModel.currentUser.collectAsState().value?.userId
    val scheduledMessages = allScheduledMessages.filter {
        it.receiverId == chatId || (chatId == "self_notes" && it.receiverId == myUserId)
    }
    val pendingChatText by viewModel.pendingChatText.collectAsState()
    val mediaUploadStatus by viewModel.mediaUploadStatus.collectAsState()
    val activeLumis by viewModel.activeLumis.collectAsState()
    val listenTogetherActive    by viewModel.listenTogetherActive.collectAsState()
    val listenTogetherChatId    by viewModel.listenTogetherChatId.collectAsState()
    val listenTogetherTrack     by viewModel.listenTogetherTrack.collectAsState()
    val listenTogetherPlaying   by viewModel.listenTogetherIsPlaying.collectAsState()
    val listenTogetherPos       by viewModel.listenTogetherPosition.collectAsState()
    val listenTogetherInvite    by viewModel.listenTogetherInvite.collectAsState()
    val isListenTogetherHost    by viewModel.isListenTogetherHost.collectAsState()
    val listenTogetherWaiting   by viewModel.listenTogetherWaiting.collectAsState()
    val listenTogetherRejected  by viewModel.listenTogetherRejected.collectAsState()
    val listenTogetherPending      by viewModel.listenTogetherPendingAction.collectAsState()
    val listenTogetherActionReq    by viewModel.listenTogetherActionRequest.collectAsState()
    val listenTogetherPlaylist     by viewModel.listenTogetherPlaylist.collectAsState()
    val listenTogetherTrackIndex   by viewModel.listenTogetherTrackIndex.collectAsState()
    val listenTogetherShuffleActive by viewModel.listenTogetherShuffleActive.collectAsState()
    val listenTogetherAvailableTracks by viewModel.listenTogetherAvailableTracks.collectAsState()
    val savedListenTogetherPlaylist by viewModel.savedListenTogetherPlaylist.collectAsState()
    val savedListenTogetherChatId   by viewModel.savedListenTogetherChatId.collectAsState()
    var showListenTogetherPlayer by remember { mutableStateOf(true) }
    var showListenTogetherSetup by remember { mutableStateOf(false) }
    var showDetachedMusicPlayer by remember { mutableStateOf(false) }

    // Gruppen-Objekt EINMALIG auflösen statt zweifacher .find() pro Recomposition:
    // hängt nur an chatId/groups, nicht am gesamten Screen-State.
    val currentGroup = remember(isGroup, chatId, groups) {
        if (isGroup) groups.find { it.groupId == chatId } else null
    }
    val groupName = if (isGroup) currentGroup?.name ?: chatId else null
    val groupImageUrl = if (isGroup) currentGroup?.groupImageUrl else null
    val isSelfChat = chatId == "self_notes"
    val isLetheTeamChat = contact?.fakeNumber == "LetheTeam" || chatId == "00000000-0000-0000-0000-000000000000"
    val title = when {
        isSelfChat -> "Eigene Notizen"
        isLetheTeamChat -> "Lethe Team"
        else -> groupName ?: contact?.username ?: contact?.fakeNumber ?: "Unbekannt"
    }

    var replyToMessage by remember { mutableStateOf<MessageEntity?>(null) }

    // Nachrichten-Bearbeitung (kein Dialog — Text landet direkt im Eingabefeld)

    // Chat-Sounds (in-App Sound beim Senden/Empfangen)
    val chatSoundReceive by viewModel.userPrefs.collectAsState()
    val chatSoundEnabled = chatSoundReceive.chatSoundReceiveEnabled
    val chatSoundSendOn = chatSoundReceive.chatSoundSendEnabled
    val notificationSound = chatSoundReceive.notificationSound
    val enterToSend = chatSoundReceive.enterToSend
    val currentUserForSound by viewModel.currentUser.collectAsState()
    val p2pConnectionStates by viewModel.p2pConnectionStates.collectAsState()
    val p2pState = if (!isGroup && !isSelfChat) p2pConnectionStates[chatId] else null
    // Sequenzielles Medien-Laden: Bilder/Videos/Sprachnachrichten im sichtbaren Bereich werden
    // NIE parallel geladen — erst wenn eine fertig ist (Erfolg oder Fehler), startet die nächste.
    // Die sichtbare Reihenfolge (visibleMediaQueue) wird nur neu berechnet wenn nicht gescrollt
    // wird (siehe Effekt weiter unten) — scrollt der Nutzer schneller als geladen werden kann,
    // wird währenddessen nichts Neues angestoßen.
    var loadedMediaUrls by remember { mutableStateOf(emptySet<String>()) }
    var forcedMediaUrls by remember { mutableStateOf(emptySet<String>()) }
    var visibleMediaQueue by remember { mutableStateOf(emptyList<String>()) }
    val activeLoadingMediaUrl by remember {
        derivedStateOf { visibleMediaQueue.firstOrNull { it !in loadedMediaUrls } }
    }
    val onboardingStep by viewModel.currentOnboardingStep.collectAsState()
    var showFirstMessageCelebration by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<MessageEntity?>(null) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val videoPreviewView = remember { PreviewView(context) }
    val videoPreviewViewCircle = remember { PreviewView(context) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current

    // Kurze Vibration (50ms) beim Senden/Empfangen
    fun vibrateShort() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(50)
                }
            }
        } catch (_: Exception) {}
    }

    var textState by remember { mutableStateOf(TextFieldValue("")) }
    var typingKeyCounter by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    var userHolding by remember { mutableStateOf(false) }
    var userScrolledUp by remember(chatId) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showAttachSheet by remember { mutableStateOf(false) }
    var showAttachPanel by remember { mutableStateOf(false) }
    var showLocationSubMenu by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var showContactPickerDialog by remember { mutableStateOf(false) }
    var showEmojiPanel by remember { mutableStateOf(false) }
    var isTextFieldFocused by remember { mutableStateOf(false) }

    // --- WhatsApp-style Keyboard↔EmojiPicker Transition ---
    // Keyboard-Höhe live verfolgen und letzten positiven Wert merken,
    // damit beim Wechsel Keyboard→EmojiPicker kein Layout-Sprung entsteht.
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    var lastImeHeightPx by remember { mutableIntStateOf(0) }
    if (imeBottomPx > 0) lastImeHeightPx = imeBottomPx

    // Transition-Flag: hält den Platz frei wenn Emoji-Panel → Tastatur gewechselt wird,
    // damit kein Layout-Sprung entsteht bis die Tastatur sichtbar wird.
    var keepBottomSpaceForTransition by remember { mutableStateOf(false) }
    LaunchedEffect(imeBottomPx > 0) {
        if (imeBottomPx > 0) keepBottomSpaceForTransition = false
    }

    // Der reservierte Platz am unteren Rand:
    //   • EmojiPicker offen          → letzter IME-Wert (feste Höhe, kein Sprung)
    //   • Transition Emoji→Tastatur  → letzter IME-Wert (Platz halten bis Tastatur da)
    //   • Tastatur sichtbar          → live IME-Höhe (passt sich bei Resize an)
    //   • sonst                      → 0
    val bottomSpaceDp = with(density) {
        val imeAdj = 20.dp
        when {
            showEmojiPanel || keepBottomSpaceForTransition -> (lastImeHeightPx.toDp() - imeAdj).coerceAtLeast(300.dp)
            imeBottomPx > 0  -> (imeBottomPx.toDp() - imeAdj).coerceAtLeast(0.dp)
            else             -> 0.dp
        }
    }

    val textFieldFocusRequester = remember { FocusRequester() }

    // Sicherheits-Timeout für den Wechsel EmojiPicker → Tastatur:
    // Normalerweise hebt der LaunchedEffect oben (imeBottomPx > 0) den reservierten Platz
    // auf, sobald die Tastatur erscheint. Schlägt requestFocus()/show() aber fehl (z.B. weil
    // das Feld den Fokus verloren hat), käme die Tastatur nie → der reservierte Platz bliebe
    // leer stehen. Hier nach kurzer Wartezeit erneut versuchen und sonst den Platz freigeben.
    LaunchedEffect(keepBottomSpaceForTransition) {
        if (keepBottomSpaceForTransition) {
            delay(450)
            if (keepBottomSpaceForTransition) {
                try { textFieldFocusRequester.requestFocus() } catch (_: Exception) {}
                keyboardController?.show()
                delay(350)
                keepBottomSpaceForTransition = false
            }
        }
    }

    // Zurück-Taste schließt EmojiPicker oder Anhang-Panel, verlässt nicht den Chat
    BackHandler(enabled = showEmojiPanel || showAttachPanel) {
        if (showAttachPanel) showAttachPanel = false
        else showEmojiPanel = false
    }
    val isGlossyMorphChat = LocalAppTheme.current == AppTheme.GLOSSY_MORPH
    var showInAppCamera by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var scheduleDialogText by remember { mutableStateOf("") }
    var isVideoRecording by remember { mutableStateOf(false) }
    var activeVideoRecording by remember { mutableStateOf<Recording?>(null) }
    var videoOutputFile by remember { mutableStateOf<File?>(null) }
    var videoRecordingDurationSec by remember { mutableIntStateOf(0) }
    var videoCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var onVideoFinalizedCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Gesetzt wenn stopVideoAndSend() aufgerufen wird bevor die Kamera-Initialisierung abgeschlossen ist
    var pendingStopAndSend by remember { mutableStateOf(false) }
    var circleVideoMode by remember { mutableStateOf(false) }
    var showChatMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedSearchQuery by remember { mutableStateOf("") }
    LaunchedEffect(searchQuery) {
        delay(250)
        debouncedSearchQuery = searchQuery
    }
    var searchResultIndex by remember { mutableIntStateOf(0) }
    var showMediaGallery by remember { mutableStateOf(false) }
    var showContactProfile by remember { mutableStateOf(false) }
    var showLumisPicker by remember { mutableStateOf(false) }
    var showReportSheet by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showGamePickerDialog by remember { mutableStateOf(false) }
    var showScheduledMessagesDialog by remember { mutableStateOf(false) }
    var chatExportInProgress by remember { mutableStateOf(false) }

    // Geblockt-Status: prüfe ob dieser Kontakt von mir blockiert wurde
    val blockedUsers by viewModel.blockedUsers.collectAsState()
    val isContactBlocked = remember(blockedUsers, chatId) {
        blockedUsers.any { it.blockedId == chatId }
    }
    // Umgekehrter Fall: der Partner hat MICH blockiert (ich kann ihm nicht mehr schreiben)
    val amIBlockedByContact = contact?.blockedByPartner == true
    var showEditGroupDialog by remember { mutableStateOf(false) }
    var showGroupEditScreen by remember { mutableStateOf(false) }
    var showGroupCalendarSheet by remember { mutableStateOf(false) }
    var showGroupMembersSheet by remember { mutableStateOf(false) }
    var showGroupInfoScreen by remember { mutableStateOf(false) }
    var groupMemberProfileUserId by remember { mutableStateOf<String?>(null) }
    val groupMembersMap by viewModel.groupMembers.collectAsState()
    // Gruppenbild-Picker
    val groupImagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.uploadGroupImage(chatId, it) }
    }

    // Chat-Hintergrundbild
    var chatBgUri by remember { mutableStateOf<String?>(viewModel.getChatBackground(chatId)) }
    val chatBgPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val uriStr = uri.toString()
            viewModel.setChatBackground(chatId, uriStr)
            chatBgUri = uriStr
        }
    }

    // Auswahl-Modus
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    var showForwardSheet by remember { mutableStateOf(false) }
    var showMessageInfoDialog by remember { mutableStateOf(false) }
    var showSelectionMoreMenu by remember { mutableStateOf(false) }
    val groupMessageReads by viewModel.groupMessageReads.collectAsState()
    // ID der Nachricht für die der Emoji-Picker angezeigt werden soll (nur bei genau 1 Auswahl)
    var emojiPickerMessageId by remember { mutableStateOf<Long?>(null) }
    // ID der Nachricht für die das vollständige Emoji-Raster angezeigt wird (unabhängig von Auswahl-Modus)
    var fullEmojiPickerMessageId by remember { mutableStateOf<Long?>(null) }

    // Sprachnachrichten
    var isRecording by remember { mutableStateOf(false) }
    var isRecordingLocked by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    // WakeLock: verhindert, dass Xiaomi/MIUI das Mikrofon bei Bildschirm-Dimming abschaltet
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var recordingWakeLock by remember { mutableStateOf<PowerManager.WakeLock?>(null) }

    // Amplitude-Samples während der Aufnahme (für Waveform-Visualisierung)
    val recordingAmplitudes = remember { mutableStateListOf<Float>() }
    val recordingScope = rememberCoroutineScope()
    var amplitudeSamplerJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Fehleranzeige: beobachtet voiceRecordError aus dem ViewModel und zeigt Toast
    val voiceRecordError by viewModel.voiceRecordError.collectAsState()
    LaunchedEffect(voiceRecordError) {
        val msg = voiceRecordError ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        viewModel.setVoiceRecordError(null)
    }

    fun startRecording() {
        // Xiaomi/POCO/Redmi: AudioRecord-Zweig im ViewModel nutzen (MIUI/HyperOS blockiert
        // MediaRecorder-basierte Aufnahmen häufig durch Berechtigungs-Policy).
        if (viewModel.isXiaomiDevice) {
            Timber.tag("LETHE_AUDIO").i("ChatScreen: Xiaomi erkannt → delegiere an ViewModel.startVoiceRecording()")
            if (viewModel.startVoiceRecording()) {
                isRecording = true
            }
            return
        }

        // AGC-State zurücksetzen: verhindert Carry-over von Spitzenwerten vorangegangener Aufnahmen
        viewModel.resetAgcState()

        // Laufzeit-Berechtigungs-Check (erforderlich für Android 14 / Xiaomi MIUI)
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.tag("LETHE_AUDIO").w("startRecording: RECORD_AUDIO-Berechtigung fehlt")
            viewModel.setVoiceRecordError("Fehler: Mikrofon-Zugriff verweigert. Bitte Berechtigung erteilen.")
            return
        }

        // Temporäre Datei im app-spezifischen Cache-Verzeichnis (umgeht Scoped Storage)
        val f = File(context.cacheDir.absolutePath + "/temp_voice_record.mp4")
        if (f.exists()) f.delete()

        @Suppress("DEPRECATION")
        val mr = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

        try {
            mr.apply {
                // VOICE_COMMUNICATION: Aktiviert Hardware-Noise-Suppression + Echo-Cancellation.
                // Xiaomi/MIUI-Geräte können mit VOICE_COMMUNICATION Probleme haben – diese werden
                // jedoch schon früher über isXiaomiDevice in MainViewModel abgefangen und nutzen
                // den AudioRecord-Zweig statt diesen MediaRecorder-Pfad.
                // Samsung/Google(Pixel): Knox/Sound Intelligence bzw. Tensor-DSP liefert bei
                // VOICE_COMMUNICATION sehr niedrige bis stumme Amplitudenwerte → Sprachnachricht
                // ist still bzw. wird am maxAmp < 0.02f-Check verworfen.
                // MIC liefert native Amplituden ohne herstellerspezifische DSP-Verzerrung.
                val audioSource = if (viewModel.isVoiceCommProblematicDevice)
                    MediaRecorder.AudioSource.MIC
                else
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION
                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(44_100)
                // 64 kbps: breite Kompatibilität, ausreichende Sprachqualität
                setAudioEncodingBitRate(64_000)
                setOutputFile(f.absolutePath)
            }
            Timber.tag("LETHE_AUDIO").d("MediaRecorder konfiguriert – prepare() wird aufgerufen")
            mr.prepare()
            Timber.tag("LETHE_AUDIO").d("prepare() OK – start() wird aufgerufen")
            mr.start()
            Timber.tag("LETHE_AUDIO").i("Aufnahme gestartet: ${f.absolutePath}")
        } catch (e: java.io.IOException) {
            Timber.tag("LETHE_AUDIO").e(e, "prepare() fehlgeschlagen (IOException)")
            mr.release()
            f.delete()
            viewModel.setVoiceRecordError("Fehler: Aufnahme fehlgeschlagen (I/O). Bitte erneut versuchen.")
            return
        } catch (e: IllegalStateException) {
            Timber.tag("LETHE_AUDIO").e(e, "start() fehlgeschlagen (IllegalStateException) – MediaRecorder im falschen Zustand")
            mr.release()
            f.delete()
            viewModel.setVoiceRecordError("Fehler: Aufnahme konnte nicht gestartet werden. Bitte erneut versuchen.")
            return
        } catch (e: SecurityException) {
            Timber.tag("LETHE_AUDIO").e(e, "Mikrofon-Zugriff durch System verweigert (SecurityException) – MIUI-Restriktion?")
            mr.release()
            f.delete()
            viewModel.setVoiceRecordError("Fehler: Mikrofon-Zugriff durch System verweigert. Bitte Mikrofon-Berechtigung in den App-Einstellungen prüfen.")
            return
        } catch (e: Exception) {
            Timber.tag("LETHE_AUDIO").e(e, "Unbekannter Fehler beim Aufnahmestart: ${e.javaClass.simpleName}")
            mr.release()
            f.delete()
            viewModel.setVoiceRecordError("Fehler: ${e.message ?: "Unbekannter Aufnahmefehler"}")
            return
        }

        // WakeLock halten: verhindert, dass MIUI/HyperOS den App-Prozess beim
        // Bildschirm-Dimming einfriert und damit den Mikrofon-Datenstrom unterbricht.
        // PARTIAL_WAKE_LOCK hält die CPU wach ohne den Bildschirm einzuschalten.
        // Timeout 10 min als Sicherheitsnetz; wird in stop/cancel manuell freigegeben.
        val wl = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "lethe:voicerecording"
        ).also { it.acquire(10 * 60 * 1000L) }
        recordingWakeLock = wl
        Timber.tag("LETHE_AUDIO").d("WakeLock (PARTIAL) erworben")

        // VoiceRecordingService starten: hält Mikrofon-Foreground-Type auf Android 14+ (Xiaomi-Absicherung)
        // startService() statt startForegroundService(): Die App ist garantiert im Vordergrund
        // (Nutzer drückt aktiv den Button), daher entfällt der 5s-ForegroundService-ANR-Timer.
        // Der Service ruft startForeground() selbst in onCreate() auf.
        try {
            val svcIntent = android.content.Intent(context, com.securechat.app.VoiceRecordingService::class.java)
                .setAction(com.securechat.app.VoiceRecordingService.ACTION_START)
            context.startService(svcIntent)
        } catch (e: Exception) {
            Timber.tag("LETHE_AUDIO").w(e, "VoiceRecordingService konnte nicht gestartet werden (unkritisch)")
        }

        // Haptisches Feedback: kurze Vibration signalisiert dem User den Aufnahme-Start
        // (besonders wichtig auf Xiaomi wo der UI-Übergang manchmal verzögert ist)
        vibrateShort()

        recorder = mr
        recordingFile = f
        recordingAmplitudes.clear()
        isRecording = true

        // Amplitude alle 80 ms samplen → Live-Waveform (AGC) + Waveform-Cache nach Aufnahme
        amplitudeSamplerJob = recordingScope.launch {
            delay(100) // kurz warten bis Recorder läuft
            while (isRecording) {
                val raw = recorder?.maxAmplitude ?: 0
                // AGC im ViewModel: normiert + geglättet → _liveRecordingAmplitude StateFlow
                viewModel.updateLiveAmplitude(raw)
                // Roh-normierter Wert für nachgelagerte Datei-Normalisierung speichern
                val norm = (raw / 32767f).coerceIn(0f, 1f)
                recordingAmplitudes.add(norm)
                delay(80)
            }
        }
    }

    fun stopAndSend() {
        // Xiaomi: Aufnahme über ViewModel-Zweig beenden
        if (viewModel.isXiaomiDevice && recorder == null && isRecording) {
            isRecording = false
            isRecordingLocked = false
            viewModel.stopAndSendVoiceMessage(chatId)
            return
        }
        amplitudeSamplerJob?.cancel()
        amplitudeSamplerJob = null
        try {
            recorder?.apply { stop(); release() }
        } catch (_: Exception) {}
        recorder = null
        isRecording = false
        isRecordingLocked = false
        // Haptisches Feedback: Aufnahme beendet (zwei kurze Pulse via einmaliger Vibration)
        vibrateShort()
        // WakeLock freigeben sobald Aufnahme beendet
        recordingWakeLock?.let { wl ->
            if (wl.isHeld) {
                wl.release()
                Timber.tag("LETHE_AUDIO").d("WakeLock freigegeben (stopAndSend)")
            }
        }
        recordingWakeLock = null
        // VoiceRecordingService stoppen
        try {
            context.stopService(android.content.Intent(context, com.securechat.app.VoiceRecordingService::class.java))
        } catch (_: Exception) {}
        val fileToSend = recordingFile
        recordingFile = null

        if (fileToSend != null) {
            // Amplitude-Snapshot vor Coroutine-Launch sichern (Thread-Safety)
            val ampSnapshot = recordingAmplitudes.toList()
            recordingAmplitudes.clear()

            // Zu kurz oder zu leise → keine Nachricht senden
            val maxAmp = ampSnapshot.maxOrNull() ?: 0f
            if (ampSnapshot.size < 5 || maxAmp < 0.02f) {
                fileToSend.delete()
                return
            }

            // Waveform sofort aus Live-Amplituden cachen – Bubble zeigt Waveform ohne Wartezeit
            val liveWaveform = com.securechat.app.data.local.AudioWaveformAnalyzer
                .computeWaveformFromRecordingAmplitudes(ampSnapshot)
            viewModel.cacheWaveformForFile(fileToSend, liveWaveform)

            recordingScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                // Normalisierung überspringen wenn geschätzter Gain < +3 dB (maxAmp ≥ 0,50).
                // VOICE_COMMUNICATION-Quelle liefert bereits AGC-Audio; bei normaler Sprachlautstärke
                // entfällt so der decode/encode-Durchlauf und der Upload startet sofort.
                val estimatedGain = 0.708f / maxAmp.coerceAtLeast(0.01f)
                val readyFile = if (estimatedGain <= 1.42f) {
                    Timber.tag("LETHE_AUDIO").d("Normalisierung übersprungen (estimatedGain=%.2f)".format(estimatedGain))
                    fileToSend
                } else {
                    com.securechat.app.data.local.AudioWaveformAnalyzer.normalizeAudio(fileToSend)
                }

                // Waveform-Cache-Key auf finale Datei übertragen (falls Normalisierung neue Datei erstellt hat)
                if (readyFile != fileToSend) {
                    viewModel.cacheWaveformForFile(readyFile, liveWaveform)
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (isGroup) viewModel.sendGroupMediaMessage(chatId, Uri.fromFile(readyFile), "audio")
                    else viewModel.sendMediaMessage(chatId, Uri.fromFile(readyFile), "audio")
                    // Chat-Sound: Sprachnachricht gesendet
                    if (chatSoundSendOn) {
                        try {
                            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                        } catch (_: Exception) {}
                    }
                }
                // Originaldatei löschen wenn eine neue normalisierte Datei erstellt wurde
                if (readyFile != fileToSend) fileToSend.delete()
            }
        }
    }

    fun cancelRecording() {
        // Xiaomi: Aufnahme über ViewModel-Zweig abbrechen
        if (viewModel.isXiaomiDevice && recorder == null && isRecording) {
            isRecording = false
            isRecordingLocked = false
            viewModel.cancelVoiceRecording()
            return
        }
        amplitudeSamplerJob?.cancel()
        amplitudeSamplerJob = null
        try {
            recorder?.apply { stop(); release() }
        } catch (_: Exception) {}
        recorder = null
        isRecording = false
        isRecordingLocked = false
        // WakeLock freigeben wenn Aufnahme abgebrochen wird
        recordingWakeLock?.let { wl ->
            if (wl.isHeld) {
                wl.release()
                Timber.tag("LETHE_AUDIO").d("WakeLock freigegeben (cancelRecording)")
            }
        }
        recordingWakeLock = null
        // VoiceRecordingService stoppen
        try {
            context.stopService(android.content.Intent(context, com.securechat.app.VoiceRecordingService::class.java))
        } catch (_: Exception) {}
        recordingAmplitudes.clear()
        recordingFile?.delete()
        recordingFile = null
    }

    fun stopVideoAndSend() {
        val f = videoOutputFile
        val rec = activeVideoRecording
        if (rec == null) {
            // Kamera noch nicht initialisiert (Race Condition) – Flag setzen damit
            // der Start-Callback das Stoppen übernimmt.
            // isVideoRecording NICHT auf false setzen – Overlay bleibt sichtbar
            // bis die Kamera-Init abgeschlossen ist und den Stop übernimmt.
            pendingStopAndSend = true
            return
        }
        val wasCircleVideo = circleVideoMode
        videoOutputFile = null
        activeVideoRecording = null
        isVideoRecording = false
        circleVideoMode = false
        pendingStopAndSend = false
        vibrateShort()
        val providerToUnbind = videoCameraProvider
        videoCameraProvider = null
        if (f != null) {
            onVideoFinalizedCallback = {
                providerToUnbind?.unbindAll()
                recordingScope.launch {
                    if (f.exists() && f.length() > 10_000L) {
                        withContext(Dispatchers.Main) {
                            val type = if (wasCircleVideo) "circle_video" else "video"
                            if (isGroup) viewModel.sendGroupMediaMessage(chatId, Uri.fromFile(f), type)
                            else viewModel.sendMediaMessage(chatId, Uri.fromFile(f), type)
                        }
                    } else {
                        f.delete()
                    }
                }
            }
            rec.stop()
        } else {
            onVideoFinalizedCallback = null
            providerToUnbind?.unbindAll()
            rec.stop()
        }
    }

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    fun startVideoRecording(frontCamera: Boolean = false) {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            || context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) return
        val f = File(context.cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
        if (f.exists()) f.delete()
        videoOutputFile = f
        videoRecordingDurationSec = 0
        isVideoRecording = true
        circleVideoMode = frontCamera
        vibrateShort()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                videoCameraProvider = cameraProvider
                val resolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .build()
                val previewBuilder = CameraXPreview.Builder()
                    .setResolutionSelector(resolutionSelector)
                // Amazon Fire/Kids-Tablets liefern bei Video (Preview+VideoCapture) ein extrem
                // dunkles Bild, weil die Frontkamera auf eine feste hohe FPS-Range mit zu kurzer
                // Belichtungszeit einrastet (Fotos/ImageCapture sind nicht betroffen). Eine
                // AE-FPS-Range mit niedrigerem Minimum erlaubt der Auto-Belichtung längere
                // Belichtungszeiten → helleres Bild. Nur auf Amazon-Geräten, um Regressionen zu vermeiden.
                if (android.os.Build.MANUFACTURER.equals("Amazon", ignoreCase = true)) {
                    androidx.camera.camera2.interop.Camera2Interop.Extender(previewBuilder)
                        .setCaptureRequestOption(
                            android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE,
                            android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE_ON
                        )
                        .setCaptureRequestOption(
                            android.hardware.camera2.CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                            android.util.Range(15, 30)
                        )
                }
                val preview = previewBuilder.build().also {
                    it.setSurfaceProvider(if (frontCamera) videoPreviewViewCircle.surfaceProvider else videoPreviewView.surfaceProvider)
                }
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.fromOrderedList(
                            listOf(Quality.FHD, Quality.HD, Quality.SD),
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                        )
                    )
                    .build()
                val vc = VideoCapture.withOutput(recorder)
                cameraProvider.unbindAll()
                val camSelector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.bindToLifecycle(lifecycleOwner, camSelector, preview, vc)
                val outputOptions = FileOutputOptions.Builder(f).build()
                val rec = vc.output
                    .prepareRecording(context, outputOptions)
                    .withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(context)) { event ->
                        when (event) {
                            is VideoRecordEvent.Start -> {
                                Timber.tag("LETHE_VIDEO").d("Videoaufnahme gestartet")
                                // Race Condition: Loslassen vor Kamera-Init abgeschlossen
                                if (pendingStopAndSend) {
                                    pendingStopAndSend = false
                                    stopVideoAndSend()
                                }
                            }
                            is VideoRecordEvent.Finalize -> {
                                Timber.tag("LETHE_VIDEO").d("Finalisiert: ${event.outputResults.outputUri}")
                                onVideoFinalizedCallback?.invoke()
                                onVideoFinalizedCallback = null
                            }
                            else -> {}
                        }
                    }
                activeVideoRecording = rec
            } catch (e: Exception) {
                Timber.tag("LETHE_VIDEO").e(e, "Fehler beim Starten der Videoaufnahme")
                isVideoRecording = false
                pendingStopAndSend = false
                videoOutputFile?.delete()
                videoOutputFile = null
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun cancelVideoRecording() {
        activeVideoRecording?.stop()
        activeVideoRecording = null
        isVideoRecording = false
        circleVideoMode = false
        pendingStopAndSend = false
        videoCameraProvider?.unbindAll()
        videoCameraProvider = null
        videoOutputFile?.delete()
        videoOutputFile = null
        onVideoFinalizedCallback = null
    }

    // Videoaufnahme: Sekunden-Timer
    LaunchedEffect(isVideoRecording) {
        if (isVideoRecording) {
            videoRecordingDurationSec = 0
            while (isVideoRecording) {
                delay(1000)
                videoRecordingDurationSec++
                if (circleVideoMode && videoRecordingDurationSec >= 60) {
                    stopVideoAndSend()
                    break
                }
            }
        }
    }

    // Kamera-Ressourcen und IME beim Verlassen des Screens freigeben
    DisposableEffect(Unit) {
        onDispose {
            // IME explizit trennen bevor die View disposed wird – verhindert
            // LegacyCursorAnchorInfoController.updateCursorAnchorInfo NPE
            // (Compose-Bug: IME-Callback feuert auf bereits detachter View)
            try {
                val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
            } catch (_: Exception) {}
            try { activeVideoRecording?.stop() } catch (_: Exception) {}
            try { videoCameraProvider?.unbindAll() } catch (_: Exception) {}
        }
    }

    // Vorgefüllter Text nach Share-Intent
    LaunchedEffect(pendingChatText) {
        val t = pendingChatText
        if (t != null) {
            textState = TextFieldValue(t)
            viewModel.clearPendingChatText()
        }
    }

    // Emoji einfügen an Cursor-Position
    fun insertEmoji(emoji: String) {
        try {
            val text = textState.text
            val sel = textState.selection
            val start = sel.start.coerceIn(0, text.length)
            val end = sel.end.coerceIn(0, text.length)
            val newText = text.substring(0, start) + emoji + text.substring(end)
            textState = TextFieldValue(text = newText, selection = TextRange(start + emoji.length))
        } catch (_: Exception) { /* defensiv */ }
    }

    // Rückwärts-Löschen (Backspace) aus dem Eingabefeld – nutzbar vom Emoji-Picker aus,
    // wo die Tastatur (und damit deren Backspace-Taste) verdeckt ist.
    fun backspaceText() {
        try {
            val text = textState.text
            val sel = textState.selection
            val start = sel.start.coerceIn(0, text.length)
            val end = sel.end.coerceIn(0, text.length)
            if (start != end) {
                // Markierung vorhanden → markierten Bereich löschen
                val newText = text.substring(0, start) + text.substring(end)
                textState = TextFieldValue(text = newText, selection = TextRange(start))
            } else if (start > 0) {
                // ein vollständiges Zeichen (Codepoint, inkl. Surrogat-Paar) vor dem Cursor löschen
                val delStart = text.offsetByCodePoints(start, -1)
                val newText = text.substring(0, delStart) + text.substring(start)
                textState = TextFieldValue(text = newText, selection = TextRange(delStart))
            }
        } catch (_: Exception) { /* defensiv */ }
    }

    // Media-Picker Launcher (Mehrfachauswahl bis 50 Bilder)
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val limited = uris.take(50)
        if (limited.size == 1) {
            // Einzelbild → normaler Editor
            if (onNavigateToImageEditor != null) {
                onNavigateToImageEditor(limited[0])
            } else {
                if (isGroup) viewModel.sendGroupMediaMessage(chatId, limited[0], "image")
                else viewModel.sendMediaMessage(chatId, limited[0], "image")
            }
        } else {
            // Mehrere Bilder → Multi-Editor oder direkt senden
            if (onNavigateToMultiImageEditor != null) {
                onNavigateToMultiImageEditor(limited)
            } else {
                limited.forEach {
                    if (isGroup) viewModel.sendGroupMediaMessage(chatId, it, "image")
                    else viewModel.sendMediaMessage(chatId, it, "image")
                }
            }
        }
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        if (uris.size == 1 && onNavigateToVideoEditor != null) {
            // Einzelvideo → Video-Editor öffnen
            onNavigateToVideoEditor(uris[0])
        } else {
            if (uris.size > 4) {
                android.widget.Toast.makeText(
                    context, "Maximal 4 Videos auf einmal", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            uris.take(4).forEach {
                if (isGroup) viewModel.sendGroupMediaMessage(chatId, it, "video")
                else viewModel.sendMediaMessage(chatId, it, "video")
            }
        }
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            if (isGroup) viewModel.sendGroupMediaMessage(chatId, it, "audio")
            else viewModel.sendMediaMessage(chatId, it, "audio")
        }
    }
    val audioMusicLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            if (isGroup) viewModel.sendGroupMediaMessage(chatId, it, "audio_music")
            else viewModel.sendMediaMessage(chatId, it, "audio_music")
        }
    }
    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.sendDocumentMessage(chatId, it, isGroup) }
    }
    var pendingObjUri by remember { mutableStateOf<Uri?>(null) }
    // Zustand für den Preis-Dialog vor dem 3D-Versand (zusammengefasst um Register zu sparen)
    var threeDPending by remember { mutableStateOf(ThreeDPending()) }

    val textureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val objUri = pendingObjUri
        if (objUri != null) {
            threeDPending = ThreeDPending(uri = objUri, textureUri = uri, filename = threeDPending.filename, show = true)
            pendingObjUri = null
        }
    }
    val threeDLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val name = try {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
                }
            } catch (_: Exception) { null } ?: ""
            if (name.lowercase().endsWith(".obj")) {
                pendingObjUri = uri
                threeDPending = threeDPending.copy(filename = name)
                textureLauncher.launch("image/*")
            } else {
                threeDPending = ThreeDPending(uri = uri, textureUri = null, filename = name, show = true)
            }
        }
    }

    if (threeDPending.show) {
        ThreeDPriceDialog(
            filename = threeDPending.filename,
            onConfirm = { price ->
                val pendingUri = threeDPending.uri
                val pendingTexture = threeDPending.textureUri
                threeDPending = ThreeDPending()
                if (pendingUri != null) {
                    viewModel.send3DFileMessage(chatId, pendingUri, pendingTexture, isGroup, price)
                }
            },
            onDismiss = {
                threeDPending = ThreeDPending()
            }
        )
    }
    // Dedizierter Musik-Launcher nur für Listen Together – mehrere Dateien → Playlist
    val listenTogetherMusicLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) viewModel.uploadAndStartListenTogether(chatId, uris)
    }

    // --- Chat-Export als HTML ---
    val chatExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri: Uri? ->
        if (uri == null) { chatExportInProgress = false; return@rememberLauncherForActivityResult }
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val myId = currentUserForSound?.userId
                val partnerName = title
                val myName = currentUserForSound?.name?.takeIf { it.isNotBlank() } ?: currentUserForSound?.fakeNumber ?: "Ich"
                val dateFmt = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                val sb = StringBuilder()
                sb.append("""<!DOCTYPE html><html lang="de"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">""")
                sb.append("<title>Chat mit $partnerName</title>")
                sb.append("""<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#e5ddd5;padding:16px;max-width:720px;margin:0 auto}
h1{text-align:center;padding:16px;color:#075e54;font-size:1.3em}
.date-sep{text-align:center;margin:16px 0 8px;font-size:0.8em;color:#888;background:#e1dbd1;display:inline-block;padding:4px 12px;border-radius:8px;margin-left:auto;margin-right:auto}
.date-wrap{text-align:center;margin:12px 0}
.msg{max-width:80%;padding:8px 12px;margin:3px 0;border-radius:10px;position:relative;clear:both;word-wrap:break-word;line-height:1.4}
.msg.me{background:#dcf8c6;float:right;border-bottom-right-radius:2px}
.msg.them{background:#fff;float:left;border-bottom-left-radius:2px}
.sender{font-weight:bold;font-size:0.82em;color:#075e54;margin-bottom:2px}
.time{font-size:0.7em;color:#999;float:right;margin-left:12px;margin-top:4px}
.media{max-width:100%;border-radius:6px;margin:4px 0}
.media-placeholder{background:#ccc;color:#666;padding:12px;border-radius:6px;font-size:0.85em;margin:4px 0}
.reply{border-left:3px solid #075e54;padding:4px 8px;margin-bottom:4px;background:rgba(0,0,0,0.05);border-radius:4px;font-size:0.85em;color:#555}
.clear{clear:both}
.audio-label{font-style:italic;color:#555}
</style></head><body>""")
                sb.append("<h1>Chat mit $partnerName</h1>")

                var lastDate = ""
                for (msg in messages) {
                    val date = dateFmt.format(java.util.Date(msg.timestamp))
                    val time = timeFmt.format(java.util.Date(msg.timestamp))
                    if (date != lastDate) {
                        sb.append("""<div class="clear"></div><div class="date-wrap"><span class="date-sep">$date</span></div>""")
                        lastDate = date
                    }

                    val isMine = msg.senderId == myId
                    val cssClass = if (isMine) "me" else "them"
                    val senderLabel = if (isMine) myName else {
                        if (isGroup) {
                            val savedContact = contacts.find { it.userId == msg.senderId }
                            savedContact?.let { it.customAlias ?: it.username ?: it.fakeNumber }
                                ?: msg.senderId.take(8)
                        } else partnerName
                    }

                    sb.append("""<div class="msg $cssClass">""")
                    if (!isMine || isGroup) {
                        sb.append("""<div class="sender">$senderLabel</div>""")
                    }

                    // Reply
                    if (!msg.replyToContent.isNullOrBlank()) {
                        val replyLabel = if (msg.replyToSenderId == myId) myName else partnerName
                        sb.append("""<div class="reply"><b>$replyLabel</b><br>${android.text.Html.escapeHtml(msg.replyToContent)}</div>""")
                    }

                    // Media
                    when (msg.mediaType) {
                        "image" -> {
                            val imgUrl = msg.mediaUrl
                            if (!imgUrl.isNullOrBlank()) {
                                // Bild als Base64 einbetten
                                try {
                                    val fullUrl = if (imgUrl.startsWith("http")) imgUrl else "https://letheapp.de$imgUrl"
                                    val conn = java.net.URL(fullUrl).openConnection() as java.net.HttpURLConnection
                                    conn.connectTimeout = 8000
                                    conn.readTimeout = 8000
                                    conn.connect()
                                    if (conn.responseCode == 200) {
                                        val mime = conn.contentType ?: "image/jpeg"
                                        val bytes = conn.inputStream.readBytes()
                                        conn.disconnect()
                                        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                        sb.append("""<img class="media" src="data:$mime;base64,$b64" alt="Bild">""")
                                    } else {
                                        conn.disconnect()
                                        sb.append("""<div class="media-placeholder">Bild: $imgUrl</div>""")
                                    }
                                } catch (_: Exception) {
                                    sb.append("""<div class="media-placeholder">Bild: $imgUrl</div>""")
                                }
                            }
                        }
                        "video", "circle_video" -> {
                            val vidUrl = msg.mediaUrl
                            if (!vidUrl.isNullOrBlank()) {
                                val fullUrl = if (vidUrl.startsWith("http")) vidUrl else "https://letheapp.de$vidUrl"
                                sb.append("""<video class="media" controls style="max-height:360px"><source src="$fullUrl"></video>""")
                            }
                        }
                        "audio", "audio_music" -> {
                            val audioUrl = msg.mediaUrl
                            if (!audioUrl.isNullOrBlank()) {
                                val fullUrl = if (audioUrl.startsWith("http")) audioUrl else "https://letheapp.de$audioUrl"
                                val label = if (msg.mediaType == "audio") "Sprachnachricht" else "Musik"
                                sb.append("""<span class="audio-label">$label:</span><br><audio controls style="width:100%;margin-top:4px"><source src="$fullUrl"></audio>""")
                            }
                        }
                        "document" -> {
                            val docUrl = msg.mediaUrl
                            if (!docUrl.isNullOrBlank()) {
                                val fullUrl = if (docUrl.startsWith("http")) docUrl else "https://letheapp.de$docUrl"
                                sb.append("""<div class="media-placeholder"><a href="$fullUrl">Dokument</a></div>""")
                            }
                        }
                        "poll" -> {
                            sb.append("""<div class="media-placeholder">Umfrage</div>""")
                        }
                    }

                    // Text content
                    if (!msg.content.isNullOrBlank() && msg.mediaType != "poll") {
                        val escaped = android.text.Html.escapeHtml(msg.content).replace("\n", "<br>")
                        sb.append("<span>$escaped</span>")
                    }

                    // Reaction
                    if (!msg.reaction.isNullOrBlank()) {
                        sb.append(""" <span style="font-size:1.2em">${msg.reaction}</span>""")
                    }

                    sb.append("""<span class="time">$time</span>""")
                    sb.append("</div>")
                    sb.append("""<div class="clear"></div>""")
                }

                sb.append("</body></html>")

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(sb.toString().toByteArray(Charsets.UTF_8))
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Chat exportiert", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Chat-Export fehlgeschlagen")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Export fehlgeschlagen: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } finally {
                chatExportInProgress = false
            }
        }
    }

    // Foto auf 9:16 zuschneiden (center-crop, läuft auf IO-Thread)
    fun cropTo9x16(uri: Uri): Uri {
        // EXIF-Orientierung lesen, bevor der Bitmap-Stream geöffnet wird
        val exifDegrees = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = android.media.ExifInterface(stream)
                when (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) { 0f }

        val input = context.contentResolver.openInputStream(uri) ?: return uri
        val rawBitmap = BitmapFactory.decodeStream(input)
        input.close()
        if (rawBitmap == null) return uri

        // Bitmap drehen falls nötig, damit srcW/srcH korrekt sind
        val original: Bitmap = if (exifDegrees != 0f) {
            val matrix = android.graphics.Matrix().apply { postRotate(exifDegrees) }
            val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            rawBitmap.recycle()
            rotated
        } else rawBitmap

        val srcW = original.width
        val srcH = original.height
        val cropW: Int; val cropH: Int; val offsetX: Int; val offsetY: Int
        if (srcH >= srcW) {
            // Portrait: auf 9:16 zuschneiden
            cropW = srcW
            cropH = (srcW * 16f / 9f).toInt().coerceAtMost(srcH)
            offsetX = 0
            offsetY = (srcH - cropH) / 2
        } else {
            // Landscape: auf 16:9 zuschneiden (nicht 9:16 – würde Bild stark beschneiden)
            cropH = srcH
            cropW = (srcH * 16f / 9f).toInt().coerceAtMost(srcW)
            offsetX = (srcW - cropW) / 2
            offsetY = 0
        }
        val cropped = Bitmap.createBitmap(original, offsetX, offsetY, cropW, cropH)
        if (cropped !== original) original.recycle()
        context.contentResolver.openOutputStream(uri)?.use { out ->
            cropped.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        cropped.recycle()
        return uri
    }

    // Hilfsfunktion: Custom Kamera öffnen (nach Permission-Check)
    fun launchCamera() {
        showInAppCamera = true
    }

    // Kamera-Permission-Launcher
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
    }

    // Mikrofon-Permission-Launcher
    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
    }

    // Video-Permission-Launcher (Kamera + Mikrofon)
    val videoPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Benutzer versucht Videoaufnahme erneut nach Genehmigung */ }

    // Standort-Launcher: Berechtigung anfordern, dann Standort-Untermenü im Anhang-Sheet anzeigen
    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showLocationSubMenu = true
            scope.launch { showAttachSheet = true; sheetState.show() }
        }
    }

    // Chat öffnen: Status laden, Read-Receipts senden, Nachrichten vom Server sync
    LaunchedEffect(chatId) {
        viewModel.onChatOpened(chatId, isGroup)
        if (!isSelfChat) {
            if (isGroup) {
                viewModel.loadGroupMessages(chatId)
                viewModel.loadGroupMembers(chatId)
            } else {
                viewModel.loadMessages(chatId)
                viewModel.loadScheduledMessages()
                viewModel.initP2PChat(chatId)
            }
        }
    }

    // Online-Status alle 30 Sekunden neu laden (schnellere offline-Erkennung)
    LaunchedEffect(chatId) {
        if (!isGroup && !isSelfChat) {
            while (true) {
                delay(30_000)
                viewModel.refreshContactStatus(chatId)
            }
        }
    }

    // Draft-Text wiederherstellen wenn Chat geöffnet wird
    // (nicht überschreiben, falls inzwischen z.B. ein geteilter Link per
    // pendingChatText ins Feld eingetragen wurde – Race Condition Fix)
    LaunchedEffect(chatId) {
        val draft = viewModel.getChatDraft(chatId)
        if (draft.isNotEmpty() && textState.text.isEmpty()) {
            textState = TextFieldValue(draft)
        }
    }

    // URL-Analyse + Draft-Speicherung: Seiteneffekte außerhalb von onValueChange
    LaunchedEffect(textState.text) {
        viewModel.onChatTextChanged(chatId, textState.text)
    }

    // Chat verlassen: aktiveChatId zurücksetzen + Draft speichern
    DisposableEffect(chatId) {
        onDispose {
            viewModel.onChatClosed()
            // Entwurf speichern (leer = Entwurf löschen)
            viewModel.saveChatDraft(chatId, textState.text)
        }
    }

    // Initiales Medien-Preloading: sichtbare Nachrichten + 1 nächstes Medium vorladen
    LaunchedEffect(messages) {
        if (messages.isEmpty()) return@LaunchedEffect
        // Sichtbare Elemente schätzen (Fallback: 12); letztes Medium außerhalb des Viewports finden
        val visibleCount = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(12)
        var nextMediaIdx = -1
        for (i in visibleCount until messages.size) {
            val m = messages[i]
            if (!m.mediaUrl.isNullOrBlank() && m.mediaType != "text") {
                nextMediaIdx = i
                break
            }
        }
        val preloadEnd = if (nextMediaIdx >= 0) nextMediaIdx else (visibleCount - 1)
        viewModel.preloadChatMedia(messages, chatId, 0, preloadEnd)
    }

    // Scroll-basiertes Preloading: nur sichtbare Nachrichten vorladen (kein Vorladen außerhalb des
    // Viewports) — feuert erst wenn das Scrollen stoppt, damit während schnellen Scrollens keine
    // Netzwerk-/Dekodier-Last durch nachfolgende Bubbles entsteht.
    LaunchedEffect(Unit) {
        snapshotFlow {
            if (listState.isScrollInProgress) null else listState.layoutInfo
        }
            .distinctUntilChanged()
            .collect { layoutInfo ->
                if (layoutInfo == null) return@collect
                val first = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: return@collect
                val last = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@collect
                viewModel.preloadChatMedia(messages, chatId, first, last)
            }
    }

    // Sequenzielle Sichtbarkeits-Warteschlange: wird NUR neu berechnet wenn nicht (mehr) gescrollt
    // wird — während des Scrollens wird nichts Neues angestoßen, egal wie schnell gescrollt wird.
    // Sobald das Scrollen stoppt, enthält visibleMediaQueue genau die aktuell sichtbaren Bild-/
    // Video-/Sprachnachrichten-URLs (in Reihenfolge) — geladen wird davon immer nur die erste noch
    // nicht fertige (activeLoadingMediaUrl), die nächste erst wenn diese fertig ist (siehe oben).
    LaunchedEffect(Unit) {
        snapshotFlow {
            if (listState.isScrollInProgress) null
            else listState.layoutInfo.visibleItemsInfo.sortedBy { it.index }.map { it.key }
        }
            .distinctUntilChanged()
            .collect { visibleKeys ->
                if (visibleKeys == null) return@collect
                delay(150)
                if (listState.isScrollInProgress) return@collect
                val byKey = messages.associateBy { it.localId }
                visibleMediaQueue = visibleKeys.mapNotNull { byKey[it] }
                    .filter { it.mediaType in setOf("image", "video", "audio") && !it.mediaUrl.isNullOrBlank() }
                    .mapNotNull { it.mediaUrl }
            }
    }

    // Scroll-Position (für Scroll-nach-unten-Button): sofort, ohne Debounce.
    LaunchedEffect(chatId) {
        snapshotFlow { listState.firstVisibleItemIndex > 0 }
            .distinctUntilChanged()
            .collect { userScrolledUp = it }
    }

    // Batch-Nachladen älterer Nachrichten: Triggert NUR wenn der Nutzer tatsächlich den oberen Rand
    // der aktuell geladenen Nachrichten erreicht hat (das "load_more_indicator"-Sentinel-Item wird
    // sichtbar) – kein kontinuierliches Nachladen mehr während des Scrollens. Nach dem Erreichen wird
    // genau ein Batch von 50 Nachrichten geladen (Ladekreis erscheint), danach wird erst beim erneuten
    // Erreichen des oberen Randes der nächste Batch nachgeladen.
    LaunchedEffect(chatId) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val sentinelIndex = totalItems - 1
            // In reverseLayout sitzt das Sentinel-Item (load_more_indicator) am oberen Bildschirmrand
            totalItems > 0 && layoutInfo.visibleItemsInfo.any { it.index == sentinelIndex }
        }
            .distinctUntilChanged()
            .collect { reachedTop ->
                if (reachedTop && viewModel.hasMoreMessages(chatId)) {
                    viewModel.loadOlderMessages(chatId)
                }
            }
    }

    // initialLoadDone: verhindert Notification-Ton beim ersten Öffnen des Chats
    var initialLoadDone by remember(chatId) { mutableStateOf(false) }
    // Verhindert Scroll nach unten wenn nur ältere Nachrichten nachgeladen werden
    var lastNewestMsgId by remember(chatId) { mutableStateOf<Long?>(null) }

    LaunchedEffect(messages.firstOrNull()?.localId) {
        if (messages.isNotEmpty()) {
            val currentNewestId = messages.first().localId
            val newestChanged = currentNewestId != lastNewestMsgId
            lastNewestMsgId = currentNewestId
            // Nur scrollen wenn sich die neueste Nachricht geändert hat (neue Nachricht oder erster Load)
            // Nicht scrollen wenn nur ältere Nachrichten nachgeladen wurden
            if (newestChanged && !userHolding && !userScrolledUp) {
                listState.animateScrollToItem(0)
            }
            // Chat-Sound + Vibration: nur bei wirklich neuen Nachrichten, nicht beim initialen Laden
            if (initialLoadDone) {
                val newest = messages.first()
                if (newest.senderId != currentUserForSound?.userId) {
                    vibrateShort()
                    if (chatSoundEnabled) {
                        try {
                            // Nachrichtenton als reine Benachrichtigung ausgeben (USAGE_NOTIFICATION),
                            // NICHT auf dem Klingel-/Kommunikations-Stream. Sonst routet Android den
                            // Ton bei verbundenem Bluetooth-Auto (HFP) über den Telefon-Kanal und
                            // reißt die laufende Android-Auto-/A2DP-Wiedergabe an sich.
                            val notifAttrs = android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                            if (notificationSound == "pocker") {
                                // setAudioAttributes muss vor prepare() greifen → MediaPlayer manuell
                                // aufbauen statt MediaPlayer.create() (das intern schon prepared).
                                val afd = context.resources.openRawResourceFd(com.securechat.app.R.raw.pocker)
                                val mp = MediaPlayer()
                                mp.setAudioAttributes(notifAttrs)
                                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                afd.close()
                                mp.setOnCompletionListener { it.release() }
                                mp.setOnPreparedListener { it.start() }
                                mp.prepareAsync()
                            } else {
                                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                                val ringtone = RingtoneManager.getRingtone(context, uri)
                                ringtone?.audioAttributes = notifAttrs
                                ringtone?.play()
                            }
                        } catch (_: Exception) {}
                    }
                }
            } else {
                initialLoadDone = true
            }
        }
    }

    // Nutzer melden – Bottom Sheet
    if (showReportSheet && !isGroup) {
        ReportUserBottomSheet(
            viewModel = viewModel,
            reportedUserId = chatId,
            contextSource = "CHAT",
            onDismiss = { showReportSheet = false }
        )
    }

    // Hilfe / Tipps Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null) },
            title = { Text("Hilfe & Tipps", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    @Composable
                    fun HelpSection(title: String, items: List<Pair<String, String>>) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp
                        )
                        items.forEach { (label, desc) ->
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(text = desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    HelpSection(
                        title = "Sprachnachrichten",
                        items = listOf(
                            "Aufnehmen" to "Mikrofon-Button gedrückt halten → aufnehmen → loslassen zum Senden.",
                            "Sperren" to "Während der Aufnahme nach oben wischen → Aufnahme läuft ohne Halten weiter; Stop-Button tippen zum Senden.",
                            "Abbrechen" to "Während der Aufnahme nach links wischen → Aufnahme wird verworfen.",
                            "Abspielen" to "Play-Button antippen, um eine Sprachnachricht zu starten oder zu pausieren.",
                            "Springen" to "Auf die Wellenform tippen oder ziehen, um zu einer bestimmten Stelle zu springen."
                        )
                    )
                    HorizontalDivider()
                    HelpSection(
                        title = "Nachrichten schreiben",
                        items = listOf(
                            "Code-Block" to "Text mit /* und */ einschließen, z.\u202fB. /*dein Code*/ → wird als Codeblock mit Kopier-Schaltfläche angezeigt.",
                            "Emojis" to "Smiley-Symbol neben dem Textfeld antippen, um den Emoji-Picker zu öffnen.",
                            "Antworten" to "Nachricht lange drücken → \"Antworten\" wählen, um direkt auf eine Nachricht zu antworten."
                        )
                    )
                    HorizontalDivider()
                    HelpSection(
                        title = "Geplante Nachrichten",
                        items = listOf(
                            "Nachricht planen" to "Text ins Eingabefeld tippen, dann den Senden-Button lange drücken → Datum und Uhrzeit auswählen → \"Planen\" tippen. Die Nachricht wird automatisch zum gewählten Zeitpunkt gesendet.",
                            "Geplante Nachrichten anzeigen" to "Über das ⋮-Menü \"Geplante Nachrichten\" öffnen oder den Hinweis-Streifen über dem Eingabefeld antippen.",
                            "Löschen" to "Geplante Nachricht in der Übersicht antippen → \"Löschen\" wählen, um sie zu stornieren.",
                            "Hinweis" to "Geplante Nachrichten sind nur in Einzelchats verfügbar, nicht in Gruppen."
                        )
                    )
                    HorizontalDivider()
                    HelpSection(
                        title = "Anhänge senden (+)",
                        items = listOf(
                            "Bild / Video" to "Foto oder Video aus der Galerie auswählen und senden.",
                            "Musik" to "Musikdatei aus dem Gerät senden.",
                            "Dokument" to "Beliebige Datei (PDF, ZIP, …) versenden.",
                            "Standort" to "Aktuellen GPS-Standort als Google-Maps-Link senden.",
                            "Umfrage" to "Abstimmung mit Frage und Antwortoptionen erstellen.",
                            "3D-Datei" to "STL-, 3MF- oder OBJ-Datei senden und im Chat als 3D-Vorschau anzeigen lassen."
                        )
                    )
                    HorizontalDivider()
                    HelpSection(
                        title = "Nachrichten-Aktionen (lange drücken)",
                        items = listOf(
                            "Antworten" to "Direkt auf eine Nachricht antworten.",
                            "Weiterleiten" to "Nachrichten an andere Kontakte oder Gruppen weiterleiten.",
                            "Markieren (⭐)" to "Wichtige Nachrichten mit einem Stern markieren.",
                            "Kopieren" to "Nachrichtentext in die Zwischenablage kopieren.",
                            "Löschen" to "Nachrichten bei beiden Seiten entfernen."
                        )
                    )
                    HorizontalDivider()
                    if (isGroup) {
                        HelpSection(
                            title = "Gruppen-Befehle (/)",
                            items = listOf(
                                "/ranking" to "Zeigt die aktivsten Mitglieder der Gruppe nach Nachrichtenanzahl.",
                                "/würfeln" to "Würfelt eine zufällige Zahl zwischen 1 und 6."
                            )
                        )
                        HorizontalDivider()
                    }
                    HelpSection(
                        title = "Weitere Funktionen",
                        items = listOf(
                            "Lumis senden" to "Bunte Animations-Effekte an den Gesprächspartner senden (über ⋮-Menü).",
                            "Hintergrund" to "Chat-Hintergrundbild individuell einstellen oder entfernen (über ⋮-Menü).",
                            "Medien" to "Alle gesendeten Bilder und Videos im Überblick anzeigen (über ⋮-Menü).",
                            "Sprachanruf / Videoanruf" to "Telefon- oder Video-Symbol in der Titelleiste antippen."
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Schließen")
                }
            }
        )
    }

    // Info-Dialog: wer hat die Nachricht gelesen (nur Gruppenchats)
    if (showMessageInfoDialog) {
        val selMsg = messages.firstOrNull { it.localId in selectedIds }
        val members = groupMembersMap[chatId] ?: emptyList()
        val currentUserInfo by viewModel.currentUser.collectAsState()

        Dialog(onDismissRequest = { showMessageInfoDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Nachrichteninfo",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    if (groupMessageReads.isEmpty()) {
                        Text(
                            text = "Noch niemand hat diese Nachricht gelesen.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            text = "Gelesen von",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        groupMessageReads.forEach { entry ->
                            val isSelf = entry.userId == currentUserInfo?.userId
                            if (!isSelf) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!entry.profileImageUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = entry.profileImageUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = (entry.name ?: entry.fakeNumber ?: "?").take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.name ?: entry.fakeNumber ?: "Unbekannt",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp
                                        )
                                        if (!entry.readAt.isNullOrBlank()) {
                                            val readTime = remember(entry.readAt) {
                                                try {
                                                    val instant = java.time.Instant.parse(entry.readAt)
                                                    val dt = instant.atZone(java.time.ZoneId.systemDefault())
                                                    val today = java.time.LocalDate.now()
                                                    if (dt.toLocalDate() == today) {
                                                        dt.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                                                    } else {
                                                        dt.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                                                    }
                                                } catch (_: Exception) { entry.readAt }
                                            }
                                            Text(
                                                text = readTime,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Icon(
                                        Icons.Default.DoneAll,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        // Nicht-Leser anzeigen
                        val readerIds = groupMessageReads.map { it.userId }.toSet()
                        val nonReaders = members.filter { m ->
                            m.userId !in readerIds && m.userId != currentUserInfo?.userId && m.userId != selMsg?.senderId
                        }
                        if (nonReaders.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Noch nicht gelesen",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            nonReaders.forEach { member ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!member.profileImageUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = member.profileImageUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = (member.name ?: member.fakeNumber ?: "?").take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = member.name ?: member.fakeNumber ?: "Unbekannt",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        Icons.Default.Done,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = { showMessageInfoDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Schließen")
                    }
                }
            }
        }
    }

    // Weiterleiten-Sheet
    if (showForwardSheet) {
        val selectedMsgs = messages.filter { it.localId in selectedIds }
        val frequencyOrder by viewModel.chatIdsSortedByFrequency.collectAsState(initial = emptyList())
        val pinnedContactIds by viewModel.pinnedContactIds.collectAsState()
        val pinnedGroupIds by viewModel.pinnedGroupIds.collectAsState()
        ForwardSheet(
            contacts = contacts,
            groups = groups,
            frequencyOrder = frequencyOrder,
            pinnedContactIds = pinnedContactIds,
            pinnedGroupIds = pinnedGroupIds,
            onForwardTo = { targetId ->
                viewModel.forwardMessages(selectedMsgs, targetId)
                selectedIds = emptySet()
            },
            onDismiss = { showForwardSheet = false }
        )
    }

    val chatBg = if (isLightSurface()) ChatBgLight else ChatBgDark
    // Kein verschachteltes Scaffold: TopBar ist physisch VOR dem imePadding-Scope,
    // dadurch kann die Tastatur sie niemals aus dem sichtbaren Bereich schieben.
    // Box erlaubt das Überlagern des LumisPlayer-Overlays über den gesamten Chat-Bereich
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // --- TopBar (außerhalb des IME-Scopes – bleibt immer am oberen Rand) ---
        val selShareCtx = LocalContext.current
        val selShareScope = rememberCoroutineScope()
        if (isSelectionMode) {
                // --- Auswahl-Modus TopBar ---
                val allStarred = messages.filter { it.localId in selectedIds }.all { it.isImportant }
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Abbrechen")
                        }
                    },
                    title = {
                        Text(
                            text = "${selectedIds.size}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    actions = {
                        // Bearbeiten (nur bei genau 1 eigener Text-Nachricht)
                        val selectedMsgList = messages.filter { it.localId in selectedIds }
                        val currentUser by viewModel.currentUser.collectAsState()
                        // Erneut senden (nur bei genau 1 eigener ausstehender Nachricht)
                        if (selectedMsgList.size == 1
                            && selectedMsgList[0].deliveryStatus == 0
                            && selectedMsgList[0].senderId == currentUser?.userId
                            && selectedMsgList[0].mediaType == "text"
                        ) {
                            IconButton(onClick = {
                                viewModel.retrySingleMessage(selectedMsgList[0].localId)
                                selectedIds = emptySet()
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Erneut senden")
                            }
                        }
                        if (selectedMsgList.size == 1
                            && selectedMsgList[0].mediaType == "text"
                            && selectedMsgList[0].senderId == currentUser?.userId
                        ) {
                            IconButton(onClick = {
                                val msg = selectedMsgList[0]
                                editingMessage = msg
                                textState = TextFieldValue(msg.content ?: "")
                                selectedIds = emptySet()
                                keyboardController?.show()
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Bearbeiten")
                            }
                        }
                        // Antworten
                        IconButton(onClick = {
                            messages.firstOrNull { it.localId in selectedIds }?.let { replyToMessage = it }
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Antworten")
                        }
                        // Kopieren (nur Text-Nachrichten)
                        val copyableText = messages
                            .filter { it.localId in selectedIds && it.mediaType == "text" && !it.content.isNullOrBlank() }
                            .joinToString("\n") { it.content!! }
                        if (copyableText.isNotBlank()) {
                            val context = LocalContext.current
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Nachricht", copyableText))
                                selectedIds = emptySet()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Kopieren")
                            }
                        }
                        // Extern teilen (Musik/Audio/Bild/Video – Quick Share + andere Apps)
                        val sharableMediaMsg = selectedMsgList.singleOrNull {
                            it.mediaType in listOf("audio", "audio_music", "image", "multi_image", "video") && !it.mediaUrl.isNullOrBlank()
                        }
                        if (sharableMediaMsg != null) {
                            IconButton(onClick = {
                                android.widget.Toast.makeText(selShareCtx, "Wird vorbereitet…", android.widget.Toast.LENGTH_SHORT).show()
                                val shareUrl = sharableMediaMsg.mediaUrl!!
                                val shareType = sharableMediaMsg.mediaType ?: ""
                                selectedIds = emptySet()
                                selShareScope.launch {
                                    val ok = quickShareMediaFile(selShareCtx, shareUrl, shareType)
                                    if (!ok) android.widget.Toast.makeText(selShareCtx, "Teilen fehlgeschlagen", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.IosShare, contentDescription = "Extern teilen")
                            }
                        }
                        // Stern / Wichtig markieren
                        IconButton(onClick = {
                            viewModel.starMessages(selectedIds, !allStarred)
                            selectedIds = emptySet()
                        }) {
                            Icon(
                                if (allStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Wichtig",
                                tint = if (allStarred) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Löschen
                        IconButton(onClick = {
                            viewModel.deleteMessages(selectedIds)
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Löschen")
                        }
                        // Weiterleiten (nur wenn alle ausgewählten Nachrichten gesendet wurden)
                        val anyUnsent = selectedMsgList.any { it.deliveryStatus == 0 }
                        if (!anyUnsent) {
                            IconButton(onClick = { showForwardSheet = true }) {
                                Icon(Icons.Default.Share, contentDescription = "Weiterleiten")
                            }
                        }
                        // 3-Punkte-Menü
                        Box {
                            IconButton(onClick = { showSelectionMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Mehr")
                            }
                            DropdownMenu(
                                expanded = showSelectionMoreMenu,
                                onDismissRequest = { showSelectionMoreMenu = false }
                            ) {
                                val selMsg = messages.firstOrNull { it.localId in selectedIds }
                                if (isGroup && selectedIds.size == 1 && selMsg?.messageId != null) {
                                    DropdownMenuItem(
                                        text = { Text("Info") },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                        onClick = {
                                            showSelectionMoreMenu = false
                                            viewModel.loadGroupMessageReads(chatId, selMsg.messageId!!)
                                            showMessageInfoDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            } else {
                // --- Normaler TopBar ---
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.let {
                                if (!isGroup && !isSelfChat && contact != null && !contact.isAnonymous) {
                                    it.clickable { showContactProfile = true }
                                } else it
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .then(
                                        when {
                                            isGroup -> Modifier.clickable {
                                                viewModel.loadGroupMembers(chatId)
                                                showGroupInfoScreen = true
                                            }
                                            !isSelfChat && contact != null && !contact.isAnonymous ->
                                                Modifier.clickable { showContactProfile = true }
                                            else -> Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelfChat) {
                                    Icon(
                                        Icons.Default.Book,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    val avatarUrl = contact?.profileImageUrl ?: groupImageUrl
                                    if (avatarUrl != null) {
                                        Image(
                                            painter = rememberAsyncImagePainter(avatarUrl),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            if (isGroup) Icons.Default.Group else Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = title,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (contact?.isVerified == true) {
                                        Spacer(Modifier.width(4.dp))
                                        VerifiedBadge()
                                    }
                                    if (contact?.isBot == true) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "BOT",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    // P2P-Verbindungsstatus-Badge
                                    P2pStatusBadge(
                                        viewModel = viewModel,
                                        chatId = chatId,
                                        isGroup = isGroup,
                                        isSelfChat = isSelfChat,
                                        p2pState = p2pState
                                    )
                                }
                                if (!isSelfChat) {
                                    if (contact?.isBot == true) {
                                        Text(
                                            text = "Automatisierter Bot · kein E2EE",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                        )
                                    } else if (isGroup) {
                                        val groupTypingIds = typingGroupMembers[chatId] ?: emptySet()
                                        val typingMembers = groupMembersMap[chatId]?.filter { it.userId in groupTypingIds } ?: emptyList()
                                        if (typingMembers.isNotEmpty()) {
                                            val typingText = if (typingMembers.size == 1) {
                                                "${typingMembers[0].name ?: typingMembers[0].fakeNumber ?: "Jemand"} tippt…"
                                            } else {
                                                "${typingMembers.size} tippen…"
                                            }
                                            Text(
                                                text = typingText,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                            )
                                        }
                                    } else if (partnerIsTyping) {
                                        TypingIndicatorText()
                                    } else {
                                        val statusText = when {
                                            contactStatus?.isOnline == true -> "Online"
                                            contactStatus?.lastActive != null -> {
                                                val raw = contactStatus!!.lastActive
                                                try {
                                                    val dt = java.time.LocalDateTime.parse(
                                                        raw.take(19).replace(" ", "T")
                                                    )
                                                    val today = java.time.LocalDate.now()
                                                    val formatted = if (dt.toLocalDate() == today) {
                                                        dt.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                                                    } else {
                                                        dt.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"))
                                                    }
                                                    "Zul. aktiv: $formatted"
                                                } catch (_: Exception) { null }
                                            }
                                            else -> null
                                        }
                                        if (statusText != null) {
                                            Text(
                                                text = statusText,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "Nur für dich sichtbar",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        // Anruf-Buttons (nur bei 1:1 Chats, nicht bei Self-Notes)
                        if (!isGroup && !isSelfChat && contact != null) {
                            CallDropdownTopBarAction(
                                partnerId = chatId,
                                viewModel = viewModel,
                                onStartVoiceCall = { viewModel.startVoiceCall(chatId) },
                                onStartVideoCall = { viewModel.startVideoCall(chatId) },
                                onNavigateToActiveCall = onNavigateToVideoCall
                            )
                        }
                        // Anruf-Button für Gruppen-Chats
                        if (isGroup && !isSelfChat) {
                            GroupCallDropdownTopBarAction(
                                groupId = chatId,
                                groupName = groupName,
                                viewModel = viewModel,
                                members = groupMembersMap[chatId] ?: emptyList(),
                                onNavigateToActiveCall = onNavigateToVideoCall
                            )
                        }
                        // Spiel-Button (nur bei 1:1 Chats, nicht bei Self-Notes)
                        if (!isGroup && !isSelfChat && contact != null) {
                            IconButton(onClick = { showGamePickerDialog = true }) {
                                Icon(Icons.Default.SportsEsports, contentDescription = "Spiel starten")
                            }
                        }
                        // Listen-Together-Button (sichtbar wenn Session aktiv für diesen Chat)
                        if (listenTogetherActive && listenTogetherChatId == chatId) {
                            IconButton(onClick = { showListenTogetherPlayer = true }) {
                                Icon(
                                    if (listenTogetherPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Listen Together",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { showChatMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Optionen")
                        }
                        DropdownMenu(expanded = showChatMenu, onDismissRequest = { showChatMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Suchen") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                onClick = {
                                    showChatMenu = false
                                    searchQuery = ""
                                    searchResultIndex = 0
                                    showSearch = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Medien") },
                                leadingIcon = { Icon(Icons.Default.PermMedia, null) },
                                onClick = {
                                    showChatMenu = false
                                    showMediaGallery = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Listen together") },
                                leadingIcon = { Icon(Icons.Default.PlayCircle, null) },
                                onClick = {
                                    showChatMenu = false
                                    when {
                                        listenTogetherActive && listenTogetherChatId == chatId && listenTogetherPlaylist.isNotEmpty() -> {
                                            showListenTogetherPlayer = true
                                        }
                                        savedListenTogetherChatId == chatId && savedListenTogetherPlaylist.isNotEmpty() -> {
                                            viewModel.requestListenTogether(chatId, savedListenTogetherPlaylist.first(), savedListenTogetherPlaylist)
                                            showListenTogetherPlayer = true
                                        }
                                        else -> {
                                            listenTogetherMusicLauncher.launch("audio/*")
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Musikplayer ablösen") },
                                leadingIcon = { Icon(Icons.Default.MusicNote, null) },
                                enabled = allChatMusicUrls.isNotEmpty(),
                                onClick = {
                                    showChatMenu = false
                                    showDetachedMusicPlayer = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Playlist einrichten") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                                onClick = {
                                    showChatMenu = false
                                    showListenTogetherSetup = true
                                }
                            )
                            if (isGroup) {
                                DropdownMenuItem(
                                    text = { Text("Gruppe bearbeiten") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = {
                                        showChatMenu = false
                                        showGroupEditScreen = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Termin Kalender") },
                                    leadingIcon = { Icon(Icons.Default.CalendarMonth, null) },
                                    onClick = {
                                        showChatMenu = false
                                        viewModel.loadGroupAppointments(chatId)
                                        showGroupCalendarSheet = true
                                    }
                                )
                            }
                            // Lumis-Effekt senden (nur wenn nicht blockiert, in keine Richtung)
                            if (!isContactBlocked && !amIBlockedByContact) {
                                DropdownMenuItem(
                                    text = { Text("Lumis senden") },
                                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
                                    onClick = {
                                        showChatMenu = false
                                        showLumisPicker = true
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Hintergrund ändern") },
                                leadingIcon = { Icon(Icons.Default.Wallpaper, null) },
                                onClick = {
                                    showChatMenu = false
                                    chatBgPickerLauncher.launch("image/*")
                                }
                            )
                            if (chatBgUri != null) {
                                DropdownMenuItem(
                                    text = { Text("Hintergrund entfernen") },
                                    leadingIcon = { Icon(Icons.Default.HideImage, null) },
                                    onClick = {
                                        showChatMenu = false
                                        viewModel.clearChatBackground(chatId)
                                        chatBgUri = null
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (chatExportInProgress) "Exportiert..." else "Chat exportieren") },
                                leadingIcon = { Icon(Icons.Default.IosShare, null) },
                                enabled = !chatExportInProgress,
                                onClick = {
                                    showChatMenu = false
                                    chatExportInProgress = true
                                    val safeName = title.replace(Regex("[^a-zA-Z0-9äöüÄÖÜß _-]"), "")
                                    chatExportLauncher.launch("Chat_${safeName}.html")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Nachrichten löschen") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = {
                                    showChatMenu = false
                                    viewModel.clearChatMessages(chatId)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_menu_scheduled_messages)) },
                                leadingIcon = { Icon(Icons.Default.Schedule, null) },
                                onClick = {
                                    showChatMenu = false
                                    showScheduledMessagesDialog = true
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Hilfe / Tipps") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Help, null) },
                                onClick = {
                                    showChatMenu = false
                                    showHelpDialog = true
                                }
                            )
                            if (!isGroup && !isSelfChat) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Spiel starten") },
                                    leadingIcon = { Icon(Icons.Default.SportsEsports, null) },
                                    onClick = {
                                        showChatMenu = false
                                        showGamePickerDialog = true
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Nutzer melden", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Flag, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showChatMenu = false
                                        showReportSheet = true
                                    }
                                )
                            }
                        }
                    }
                )
            }
        // --- Content-Bereich (Nachrichten + Eingabe) – reagiert auf Tastatur ---
        // Kein bottom-padding hier – der reservierte Platz wird durch einen Geschwister-
        // Spacer/EmojiPanel am Ende der Hauptspalte bereitgestellt.
        Box(
            modifier = Modifier
                .weight(1f)
        ) {
            // Hintergrundbild (falls gesetzt) oder Standard-Chat-Farbe
            // Reihenfolge: per-Chat > global preset > global custom > Farbe
            val effectiveBgUri = chatBgUri ?: globalChatBackgroundUri
            val presetDrawableRes: Int? = when {
                effectiveBgUri?.startsWith("preset:") == true -> when (effectiveBgUri.removePrefix("preset:")) {
                    "1" -> R.drawable.chat_bg_preset_1
                    "2" -> R.drawable.chat_bg_preset_2
                    "3" -> R.drawable.chat_bg_preset_3
                    "4" -> R.drawable.chat_bg_preset_4
                    "5" -> R.drawable.chat_bg_preset_5
                    "6" -> R.drawable.chat_bg_preset_6
                    "7" -> R.drawable.chat_bg_preset_7
                    "default_dark" -> R.drawable.chat_bg_preset_default_dark
                    else -> null
                }
                else -> null
            }
            when {
                presetDrawableRes != null -> {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(presetDrawableRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(modifier = Modifier.fillMaxSize().background(chatBg.copy(alpha = 0.35f)))
                }
                effectiveBgUri != null && !effectiveBgUri.startsWith("preset:") -> {
                    AsyncImage(
                        model = android.net.Uri.parse(effectiveBgUri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Leichte Verdunkelung für bessere Lesbarkeit der Nachrichten
                    Box(modifier = Modifier.fillMaxSize().background(chatBg.copy(alpha = 0.35f)))
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize().background(chatBg))
                }
            }
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Upload-Fortschrittsbalken (zeigt aktive Video- oder Datei-Uploads)
            val videoProgressMap by viewModel.videoUploadProgress.collectAsState()
            val uploadProgress by viewModel.uploadProgress.collectAsState()
            val activeVideoProgress = videoProgressMap.values.firstOrNull()
            val showProgress = (activeVideoProgress != null && activeVideoProgress >= 0f) ||
                               (uploadProgress in 0f..0.9999f)
            if (showProgress) {
                val progressVal = when {
                    activeVideoProgress != null && activeVideoProgress > 100f -> null // Transkodierung → unbestimmt
                    activeVideoProgress != null -> activeVideoProgress / 100f
                    else -> uploadProgress
                }
                if (progressVal != null) {
                    LinearProgressIndicator(
                        progress = { progressVal },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // Nachrichten-Liste mit Datum-Trennern und Scroll-Indikator (async im Hintergrund)
            // Gruppen-Absender-Infos (Name/Avatar/verifiziert) EINMAL je Mitglied auflösen,
            // statt pro Bubble via .find() in jeder Recomposition.
            val myUserId = currentUserForSound?.userId
            // NUR die Mitglieder DIESER Gruppe als Dependency — sonst baut sich die Lookup-Map
            // bei jeder fremden Gruppen-Änderung in groupMembersMap O(N×M) neu auf.
            val currentGroupMembers = remember(isGroup, chatId, groupMembersMap) {
                if (!isGroup) emptyList() else groupMembersMap[chatId] ?: emptyList()
            }
            val groupSenderLookup: Map<String, GroupSenderDisplay>? = remember(isGroup, chatId, contacts, currentGroupMembers) {
                if (!isGroup) null else {
                    val members = currentGroupMembers
                    val contactById = contacts.associateBy { it.userId }
                    val ids = (members.map { it.userId } + contacts.map { it.userId }).toSet()
                    ids.associateWith { sid ->
                        val c = contactById[sid]
                        val m = members.firstOrNull { it.userId == sid }
                        val nm = c?.let { it.customAlias ?: it.username ?: it.fakeNumber }
                            ?: m?.name ?: m?.fakeNumber
                        val av = (m?.profileImageUrl ?: c?.profileImageUrl)?.let {
                            if (it.startsWith("http")) it else "https://letheapp.de$it"
                        }
                        GroupSenderDisplay(nm, av, c?.isVerified == true)
                    }
                }
            }
            val chatItems by androidx.compose.runtime.produceState(
                initialValue = emptyList<ChatListItem>(), messages, myUserId, groupSenderLookup
            ) {
                value = buildChatItemList(messages, myUserId, groupSenderLookup)
            }

            // --- Chat-Suche ---
            val searchResults = remember(debouncedSearchQuery, chatItems) {
                if (debouncedSearchQuery.isBlank()) emptyList()
                else chatItems.indices.filter { idx ->
                    (chatItems[idx] as? ChatListItem.Message)?.entity?.content
                        ?.contains(debouncedSearchQuery, ignoreCase = true) == true
                }
            }
            LaunchedEffect(searchResultIndex, searchResults) {
                if (searchResults.isNotEmpty()) {
                    listState.scrollToItem(
                        searchResults[searchResultIndex.coerceIn(0, searchResults.size - 1)]
                    )
                }
            }

            // Beim Titel-Wechsel (lokal oder Cast) automatisch zur Nachricht mit dem Player scrollen
            val chatMusicUrlLocal by viewModel.currentMusicUrl.collectAsState()
            val chatMusicUrlCast  by viewModel.castDiscoveryManager.castCurrentUrl.collectAsState()
            val chatIsCasting     by viewModel.castDiscoveryManager.isCasting.collectAsState()
            val activeMusicUrl    = if (chatIsCasting) chatMusicUrlCast else chatMusicUrlLocal
            LaunchedEffect(activeMusicUrl, chatItems.size) {
                val url = activeMusicUrl ?: return@LaunchedEffect
                val idx = chatItems.indexOfFirst { item ->
                    item is ChatListItem.Message &&
                        item.entity.mediaType == "audio" &&
                        item.entity.mediaUrl == url
                }
                if (idx >= 0) {
                    listState.animateScrollToItem(idx)
                }
            }

            val currentSearchResultLocalId: Long? = remember(searchResults, searchResultIndex, debouncedSearchQuery) {
                if (searchResults.isNotEmpty() && debouncedSearchQuery.isNotBlank()) {
                    (chatItems.getOrNull(
                        searchResults[searchResultIndex.coerceIn(0, searchResults.size - 1)]
                    ) as? ChatListItem.Message)?.entity?.localId
                } else null
            }
            if (showSearch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 8.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it; searchResultIndex = 0 },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        ),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Im Chat suchen…",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotBlank()) {
                        Text(
                            text = if (searchResults.isNotEmpty()) "${searchResultIndex + 1}/${searchResults.size}" else "0/0",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        IconButton(onClick = {
                            if (searchResults.isNotEmpty())
                                searchResultIndex = (searchResultIndex + 1) % searchResults.size
                        }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Nächste")
                        }
                        IconButton(onClick = {
                            if (searchResults.isNotEmpty())
                                searchResultIndex = (searchResultIndex - 1 + searchResults.size) % searchResults.size
                        }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Vorherige")
                        }
                    }
                    IconButton(onClick = { showSearch = false; searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen")
                    }
                }
                HorizontalDivider()
            }

            // Lethe Team sticky Banner
            if (isLetheTeamChat) {
                Box(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFA8A800))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "⚠️ Dieser Chat ist unverschlüsselt und kommt ausschließlich von autorisierten Mitarbeitern des Lethe Teams. Er dient nur der Aufklärung und Information. Gib niemals dein Passwort oder andere persönliche Daten weiter.",
                        fontSize = 11.sp,
                        color = Color.Black,
                        lineHeight = 15.sp
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (messages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Keine Nachrichten", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        userHolding = event.changes.any { it.pressed }
                                    }
                                }
                            },
                        reverseLayout = true,
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        // Tipp-Blase: erscheint am unteren Ende wenn Gegenüber schreibt
                        item(key = "typing_bubble") {
                            val groupTypingIds = if (isGroup) typingGroupMembers[chatId] ?: emptySet() else emptySet()
                            val showTyping = !isSelfChat && contact?.isBot != true &&
                                (partnerIsTyping || (isGroup && groupTypingIds.isNotEmpty()))
                            val typingAvatarUrl = if (isGroup) {
                                val typingMember = groupMembersMap[chatId]?.firstOrNull { it.userId in groupTypingIds }
                                typingMember?.profileImageUrl?.let {
                                    if (it.startsWith("http")) it else "https://letheapp.de$it"
                                }
                            } else null
                            Column {
                                AnimatedVisibility(
                                    visible = showTyping,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    TypingBubble(avatarUrl = typingAvatarUrl)
                                }
                            }
                        }
                        items(chatItems, key = { item ->
                            when (item) {
                                is ChatListItem.Message -> item.entity.localId
                                is ChatListItem.DateHeader -> "header_${item.dateText}"
                            }
                        }, contentType = { item ->
                            when (item) {
                                is ChatListItem.DateHeader -> "date_header"
                                is ChatListItem.Message -> "msg_${item.entity.mediaType}"
                            }
                        }) { item ->
                            when (item) {
                                is ChatListItem.DateHeader -> {
                                    // --- Datum-Trenner ---
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .height(1.dp)
                                                .weight(1f)
                                                .background(Color.Gray.copy(alpha = 0.3f))
                                        )
                                        Text(
                                            text = item.dateText,
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            modifier = Modifier
                                                .padding(horizontal = 10.dp)
                                                .background(
                                                    Color.Gray.copy(alpha = 0.12f),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .height(1.dp)
                                                .weight(1f)
                                                .background(Color.Gray.copy(alpha = 0.3f))
                                        )
                                    }
                                }
                                is ChatListItem.Message -> {
                                    val msg = item.entity

                                    // Systemnachricht (z.B. Lumis-Event): zentriert als Chip anzeigen
                                    if (msg.mediaType == "system") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp, horizontal = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = msg.content ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                modifier = Modifier
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                                        RoundedCornerShape(12.dp)
                                                    )
                                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                            )
                                        }
                                        return@items
                                    }

                                    val isSelected = msg.localId in selectedIds
                                    val isSearchHighlight = searchQuery.isNotBlank() && msg.localId == currentSearchResultLocalId
                                    var offsetX by remember { mutableStateOf(0f) }
                                    val msgIsFromMe = currentUserForSound?.userId?.let { msg.senderId == it } ?: (msg.senderId != chatId)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .semantics(mergeDescendants = true) {}
                                            .background(
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                                    isSearchHighlight -> Color(0xFFFFEB3B).copy(alpha = 0.25f)
                                                    else -> Color.Transparent
                                                }
                                            ),
                                        verticalAlignment = if (isGroup && !msgIsFromMe) Alignment.Top else Alignment.Bottom
                                    ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .offset { IntOffset(offsetX.roundToInt(), 0) }
                                            .pointerInput(msg.localId) {
                                                awaitEachGesture {
                                                    val down = awaitFirstDown(requireUnconsumed = false)
                                                    var longPressTriggered = false
                                                    var dragStarted = false
                                                    val slop = viewConfiguration.touchSlop
                                                    var cumulativeX = 0f
                                                    var cumulativeY = 0f
                                                    var cumulativeXActual = 0f // tatsächliche Bewegung, ignoriert Consumption (für Emoji-Row-Scroll)

                                                    val longPressJob = scope.launch {
                                                        delay(800L)
                                                        if (!dragStarted) {
                                                            longPressTriggered = true
                                                            selectedIds = selectedIds + msg.localId
                                                            emojiPickerMessageId = msg.localId
                                                            fullEmojiPickerMessageId = null
                                                        }
                                                    }

                                                    try {
                                                        while (true) {
                                                            val event = awaitPointerEvent()
                                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                                            if (change.changedToUpIgnoreConsumed()) {
                                                                longPressJob.cancel()
                                                                if (!longPressTriggered && !dragStarted) {
                                                                    // Kurzer Tap — aber NICHT wenn der User gerade die Emoji-Row gescrollt hat
                                                                    val wasEmojiRowScroll = emojiPickerMessageId == msg.localId &&
                                                                        kotlin.math.abs(cumulativeXActual) > slop
                                                                    if (!wasEmojiRowScroll && selectedIds.isNotEmpty()) {
                                                                        val currentlySelected = msg.localId in selectedIds
                                                                        if (currentlySelected) {
                                                                            if (emojiPickerMessageId == msg.localId) emojiPickerMessageId = null
                                                                            selectedIds = selectedIds - msg.localId
                                                                        } else {
                                                                            selectedIds = selectedIds + msg.localId
                                                                        }
                                                                    }
                                                                }
                                                                if (dragStarted) {
                                                                    if (offsetX > 120f) replyToMessage = msg
                                                                    offsetX = 0f
                                                                }
                                                                break
                                                            }

                                                            // Tatsächliche Bewegung tracken (ignoriert ob Events von LazyRow konsumiert wurden)
                                                            cumulativeXActual += (change.position - change.previousPosition).x

                                                            if (!longPressTriggered && !dragStarted) {
                                                                val dx = change.positionChange().x
                                                                val dy = change.positionChange().y
                                                                cumulativeX += dx
                                                                cumulativeY += dy
                                                                val absX = kotlin.math.abs(cumulativeX)
                                                                val absY = kotlin.math.abs(cumulativeY)
                                                                if (absY > slop && absY > absX) {
                                                                    // Vertikales Scrollen erkannt → Long-Press abbrechen,
                                                                    // Gesture freigeben damit LazyColumn scrollen kann
                                                                    longPressJob.cancel()
                                                                    break
                                                                } else if (absX > slop * 5f && selectedIds.isEmpty()) {
                                                                    longPressJob.cancel()
                                                                    dragStarted = true
                                                                    if (showEmojiPanel) showEmojiPanel = false
                                                                }
                                                            }

                                                            if (dragStarted) {
                                                                offsetX = (offsetX + change.positionChange().x).coerceIn(0f, 160f)
                                                                change.consume()
                                                            }
                                                        }
                                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                                        longPressJob.cancel()
                                                        offsetX = 0f
                                                        throw e
                                                    } finally {
                                                        longPressJob.cancel()
                                                    }
                                                }
                                            }
                                    ) {
                                        MessageBubble(
                                            message = msg,
                                            partnerId = chatId,
                                            bubbleColor = bubbleColor,
                                            partnerBubbleColor = partnerBubbleColor,
                                            bubbleColor2 = bubbleColor2,
                                            partnerBubbleColor2 = partnerBubbleColor2,
                                            precomputedItem = item,
                                            onReplyJump = {
                                                // Ziel-Nachricht suchen: erst per replyToMessageId (server/client ID), dann Fallback per Inhalt+Sender
                                                val targetIdx = chatItems.indexOfFirst { ci ->
                                                    ci is ChatListItem.Message && (
                                                        (msg.replyToMessageId != null && (
                                                            ci.entity.messageId == msg.replyToMessageId ||
                                                            ci.entity.clientMessageId == msg.replyToMessageId
                                                        )) ||
                                                        (msg.replyToMessageId == null &&
                                                            ci.entity.content == msg.replyToContent &&
                                                            ci.entity.senderId == msg.replyToSenderId)
                                                    )
                                                }
                                                if (targetIdx >= 0) scope.launch {
                                                    listState.animateScrollToItem(targetIdx)
                                                }
                                            },
                                            partnerName = contact?.username ?: contact?.fakeNumber,
                                            groupSenderName = item.groupSenderName,
                                            groupSenderIsVerified = item.groupSenderIsVerified,
                                            partnerAvatarUrl = contact?.profileImageUrl?.let {
                                                if (it.startsWith("http")) it else "https://letheapp.de$it"
                                            },
                                            viewModel = viewModel,
                                            reaction = msg.reaction,
                                            onReaction = { emoji ->
                                                viewModel.sendReaction(
                                                    messageId = msg.messageId ?: "",
                                                    chatPartnerId = chatId,
                                                    localId = msg.localId,
                                                    emoji = emoji
                                                )
                                                selectedIds = emptySet()
                                                emojiPickerMessageId = null
                                                fullEmojiPickerMessageId = null
                                            },
                                            showEmojiPicker = (emojiPickerMessageId == msg.localId && selectedIds.size == 1),
                                            onHideEmojiPicker = { emojiPickerMessageId = null },
                                            showFullEmojiPicker = (fullEmojiPickerMessageId == msg.localId),
                                            onShowFullEmojiPicker = { fullEmojiPickerMessageId = msg.localId },
                                            onHideFullEmojiPicker = { fullEmojiPickerMessageId = null },
                                            onLongClick = {
                                                selectedIds = selectedIds + msg.localId
                                                emojiPickerMessageId = msg.localId
                                            },
                                            isSelectionMode = isSelectionMode,
                                            onOpenDocument = onOpenDocument,
                                            onNavigateTo3DViewer = onNavigateTo3DViewer,
                                            onNavigateToContent = onNavigateToContent,
                                            onNavigateToSpark = onNavigateToSpark,
                                            onNavigateToCoins = onNavigateToCoins,
                                            chatId = chatId,
                                            onNavigateToLiveMaps = onNavigateToLiveMaps,
                                            onNavigateToGames = onNavigateToGames,
                                            fontSizeMultiplier = fontSizeMultiplier,
                                            isGroup = isGroup,
                                            nextAudioUrl = audioNextUrlMap[msg.mediaUrl],
                                            prevMusicUrl = musicPrevUrlMap[msg.mediaUrl],
                                            nextMusicUrl = musicNextUrlMap[msg.mediaUrl],
                                            allChatMusicUrls = allChatMusicUrls,
                                            activeLoadingMediaUrl = activeLoadingMediaUrl,
                                            isMediaApproved = { url -> url == activeLoadingMediaUrl || url in loadedMediaUrls || url in forcedMediaUrls },
                                            onMediaLoaded = { url -> loadedMediaUrls = loadedMediaUrls + url },
                                            onForceLoadMedia = { url -> forcedMediaUrls = forcedMediaUrls + url },
                                            onStartVoiceRecording = {
                                                // Wie "Senden-Button gehalten + hochgeschoben": direkt Aufnahme starten + sperren
                                                startRecording()
                                                if (isRecording) isRecordingLocked = true
                                            },
                                            onCallBack = { isVideo ->
                                                if (isVideo) viewModel.startVideoCall(chatId)
                                                else viewModel.startVoiceCall(chatId)
                                            },
                                            onDetachMusicPlayer = {
                                                showDetachedMusicPlayer = true
                                            }
                                        )
                                    }
                                    if (isGroup && !msgIsFromMe) {
                                        Spacer(Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .padding(end = 8.dp, top = 4.dp)
                                                .size((32f * avatarSizeMultiplier).dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                                .clickable { groupMemberProfileUserId = msg.senderId },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val senderAvatarUrl = item.groupSenderAvatarUrl
                                            if (senderAvatarUrl != null) {
                                                AsyncImage(senderAvatarUrl, null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                                            } else {
                                                Icon(Icons.Default.Person, null, Modifier.size((18f * avatarSizeMultiplier).dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                            }
                                        }
                                    }
                                    } // closes Row
                                }
                            }
                        }
                        // Lade-Indikator: wird oben angezeigt (reverseLayout=true) während ältere Nachrichten geladen werden
                        item(key = "load_more_indicator") {
                            if (isLoadingOlderMessages.contains(chatId)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }

                    // --- Draggable Scrollleiste rechts ---
                    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
                    val totalItems = chatItems.size
                    if (totalItems > 1) {
                        var isDragging by remember { mutableStateOf(false) }
                        var dragFraction by remember { mutableFloatStateOf(0f) }
                        var lastSeparatorIndex by remember { mutableIntStateOf(-1) }
                        var trackHeightPx by remember { mutableFloatStateOf(1f) }
                        val speedMultiplier = remember { Animatable(1f) }

                        // Einblenden wenn Liste scrollt oder Drag aktiv
                        val isListScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
                        var showScrollbar by remember { mutableStateOf(false) }
                        LaunchedEffect(isListScrolling, isDragging) {
                            if (isListScrolling || isDragging) {
                                showScrollbar = true
                            } else {
                                delay(800)
                                showScrollbar = false
                            }
                        }

                        val thumbFraction = if (isDragging) dragFraction
                            else (firstVisible.toFloat() / (totalItems - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
                        val thumbAlpha by animateFloatAsState(
                            targetValue = if (isDragging) 0.75f else 0.45f,
                            label = "thumbAlpha"
                        )
                        // Einblend-Alpha + Slide von rechts
                        val scrollbarVisAlpha by animateFloatAsState(
                            targetValue = if (showScrollbar) 1f else 0f,
                            animationSpec = tween(if (showScrollbar) 200 else 400),
                            label = "scrollbarVis"
                        )
                        val scrollbarSlide by animateFloatAsState(
                            targetValue = if (showScrollbar) 0f else 1f,
                            animationSpec = tween(if (showScrollbar) 200 else 400),
                            label = "scrollbarSlide"
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .alpha(scrollbarVisAlpha)
                                .offset { IntOffset((scrollbarSlide * 24f).roundToInt(), 0) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .fillMaxHeight()
                                    .padding(vertical = 16.dp)
                                    .onGloballyPositioned { coords ->
                                        trackHeightPx = coords.size.height.toFloat().coerceAtLeast(1f)
                                    }
                                    .pointerInput(totalItems) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                isDragging = true
                                                dragFraction = (offset.y / trackHeightPx).coerceIn(0f, 1f)
                                                scope.launch { speedMultiplier.snapTo(1f) }
                                            },
                                            onDragEnd = { isDragging = false },
                                            onDragCancel = { isDragging = false },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val speed = speedMultiplier.value
                                                dragFraction = (dragFraction + dragAmount.y / trackHeightPx * speed)
                                                    .coerceIn(0f, 1f)
                                                val targetIndex = (dragFraction * (totalItems - 1))
                                                    .roundToInt().coerceIn(0, totalItems - 1)
                                                val item = chatItems.getOrNull(targetIndex)
                                                if (item is ChatListItem.DateHeader && targetIndex != lastSeparatorIndex) {
                                                    lastSeparatorIndex = targetIndex
                                                    scope.launch {
                                                        // Stärker abbremsen (500ms statt 300ms)
                                                        speedMultiplier.animateTo(
                                                            0f,
                                                            animationSpec = tween(500, easing = FastOutLinearInEasing)
                                                        )
                                                        // Tagestrennner scrollen + in Bildschirmmitte bringen
                                                        listState.scrollToItem(targetIndex)
                                                        val viewportHeight = listState.layoutInfo.viewportSize.height
                                                        listState.scrollBy(-(viewportHeight / 2f))
                                                        // 800ms pausieren (stärker)
                                                        delay(800)
                                                        // Sanft beschleunigen
                                                        speedMultiplier.animateTo(
                                                            1f,
                                                            animationSpec = tween(250, easing = LinearOutSlowInEasing)
                                                        )
                                                    }
                                                } else if (item !is ChatListItem.DateHeader) {
                                                    lastSeparatorIndex = -1
                                                    scope.launch { listState.scrollToItem(targetIndex) }
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val th = size.height
                                    val thumbH = (th / totalItems * 10).coerceIn(20f, maxOf(20f, th * 0.25f))
                                    val availableTrack = (th - thumbH).coerceAtLeast(0f)
                                    val thumbTop = thumbFraction * availableTrack
                                    // Track
                                    drawRoundRect(
                                        color = Color.Gray.copy(alpha = 0.15f),
                                        cornerRadius = CornerRadius(4f)
                                    )
                                    // Thumb
                                    drawRoundRect(
                                        color = Color.Gray.copy(alpha = thumbAlpha),
                                        topLeft = Offset(2f, thumbTop),
                                        size = Size(size.width - 4f, thumbH),
                                        cornerRadius = CornerRadius(4f)
                                    )
                                }
                            }
                        }
                    }

                    // --- Scroll-to-Bottom Button ---
                    val isAtBottom by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
                    if (!isAtBottom) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 16.dp, end = 2.dp)
                                .size(width = 22.dp, height = 54.dp)
                                .clickable { scope.launch { userScrolledUp = false; listState.animateScrollToItem(0) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .border(1.5.dp, Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 1.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy((-13).dp)
                                ) {
                                    repeat(3) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Reply-Bar
            AnimatedVisibility(
                visible = replyToMessage != null,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                val rm = replyToMessage
                if (rm != null) {
                    // Gruppenchat: Absender per Lookup auflösen (chatId ist die Gruppen-ID, nicht der
                    // Absender). 1:1: senderId==chatId → Partner, sonst eigene Nachricht.
                    val replyerName = if (isGroup) {
                        if (rm.senderId == myUserId) "Du"
                        else groupSenderLookup?.get(rm.senderId)?.name ?: rm.senderId
                    } else {
                        if (rm.senderId == chatId) contact?.username ?: chatId else "Du"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(36.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(8.dp))
                        // Thumbnail für Bild/Video – mediaUrl hat Vorrang vor content
                        when (rm.mediaType) {
                            "image", "video", "gif", "sticker" -> {
                                val thumbUrl = rm.mediaUrl?.takeIf { it.isNotBlank() } ?: rm.content
                                AsyncImage(
                                    model = thumbUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(replyerName, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                            when (rm.mediaType) {
                                "image" -> Text("🖼 Foto", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                "gif" -> Text("GIF", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                "sticker" -> Text("Sticker", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                "multi_image" -> Text("🖼 Fotos", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                "video" -> Text("📹 Video", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                "audio" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Mic, contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                    Text("🎙 Sprachnachricht", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                "poll" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Poll, contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                    Text("📊 Umfrage", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                "game_result" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SportsEsports, contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                    Text("🎮 Spielergebnis", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                "link" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                    val linkTitle = remember(rm.content) {
                                        try {
                                            val j = org.json.JSONObject(rm.content ?: "{}")
                                            j.optString("title", j.optString("url", "Link")).ifBlank { "Link" }
                                        } catch (_: Exception) { rm.content?.take(80) ?: "Link" }
                                    }
                                    Text(linkTitle, fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                else -> Text(rm.content?.take(100) ?: "", maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                            }
                        }
                        IconButton(onClick = { replyToMessage = null }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                }
            }

            // Edit-Bar (Bearbeitungs-Indikator, analog zur Reply-Bar)
            AnimatedVisibility(
                visible = editingMessage != null,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                val em = editingMessage
                if (em != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Nachricht bearbeiten",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                em.content?.take(100) ?: "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        IconButton(onClick = {
                            editingMessage = null
                            textState = TextFieldValue("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Bearbeitung abbrechen")
                        }
                    }
                }
            }

            // Link-Vorschau (über dem Eingabefeld, wenn URL erkannt)
            AnimatedVisibility(
                visible = linkPreviewLoading || linkPreview != null,
                enter = expandVertically(expandFrom = Alignment.Bottom),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 4.dp
                ) {
                    if (linkPreviewLoading && linkPreview == null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Vorschau wird geladen…",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    } else if (linkPreview != null) {
                        val lp = linkPreview!!
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Vorschau-Bild
                            if (lp.imageUrl != null) {
                                // eBay/Amazon-Bild-CDNs (i.ebayimg.com, m.media-amazon.com)
                                // blocken Anfragen ohne Browser-User-Agent/Referer mit 403 →
                                // Header mitschicken, damit das Produktbild geladen wird.
                                val previewImgCtx = LocalContext.current
                                AsyncImage(
                                    model = ImageRequest.Builder(previewImgCtx)
                                        .data(lp.imageUrl)
                                        .headers(
                                            okhttp3.Headers.Builder()
                                                .add("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                                                .add("Referer", lp.url)
                                                .build()
                                        )
                                        .build(),
                                    contentDescription = "Link-Bild",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                                Spacer(Modifier.width(10.dp))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                if (lp.siteName != null) {
                                    Text(
                                        lp.siteName,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    lp.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (lp.description != null) {
                                    Text(
                                        lp.description,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.clearLinkPreview() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Vorschau schließen",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Slash-Command-Vorschläge (Gruppen + Bot-1:1-Chats)
            val groupCommands = listOf("/ranking", "/würfeln")
            // Bot-Befehle: gespeichert als JSON im links-Feld des Kontakts, z.B. ["/start","/help:Hilfe anzeigen"]
            val botCommands: List<Pair<String, String>> = if (!isGroup && contact?.isBot == true) {
                val raw = contact.username?.let { null } ?: run {
                    // Standard-Befehle wenn keine spezifischen hinterlegt
                    listOf("/start:Bot starten", "/help:Alle Befehle anzeigen")
                }
                raw.map { entry ->
                    val parts = entry.split(":", limit = 2)
                    Pair(parts[0], parts.getOrElse(1) { "" })
                }
            } else emptyList()
            val commandQuery = textState.text
            val showCommandSuggestions = commandQuery.startsWith("/") && !commandQuery.contains(" ") && (
                (isGroup && groupCommands.any { it.startsWith(commandQuery, ignoreCase = true) }) ||
                (!isGroup && contact?.isBot == true && botCommands.any { it.first.startsWith(commandQuery, ignoreCase = true) })
            )
            if (showCommandSuggestions) {
                Surface(tonalElevation = 8.dp) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isGroup) {
                            groupCommands.filter { it.startsWith(commandQuery, ignoreCase = true) }
                                .forEach { cmd ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { textState = TextFieldValue(cmd, TextRange(cmd.length)) }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.Terminal, contentDescription = null,
                                            modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        Column {
                                            Text(cmd, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            Text(
                                                when (cmd) {
                                                    "/ranking" -> "Rangliste der aktivsten Mitglieder"
                                                    "/würfeln" -> "Würfle eine Zahl von 1–6"
                                                    else -> ""
                                                },
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                    HorizontalDivider(thickness = 0.5.dp)
                                }
                        } else {
                            botCommands.filter { it.first.startsWith(commandQuery, ignoreCase = true) }
                                .forEach { (cmd, desc) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { textState = TextFieldValue(cmd, TextRange(cmd.length)) }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.SmartToy, contentDescription = null,
                                            modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        Column {
                                            Text(cmd, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            if (desc.isNotBlank()) {
                                                Text(desc, fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            }
                                        }
                                    }
                                    HorizontalDivider(thickness = 0.5.dp)
                                }
                        }
                    }
                }
            }

            // ── Schwebende Upload-Status-Pille ─────────────────────────────────────────
            AnimatedVisibility(
                visible = mediaUploadStatus !is com.securechat.app.ui.MainViewModel.MediaUploadStatus.Idle,
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
            ) {
                val statusText = when (val s = mediaUploadStatus) {
                    is com.securechat.app.ui.MainViewModel.MediaUploadStatus.Uploading ->
                        "Wird hochgeladen… ${s.progress}%"
                    is com.securechat.app.ui.MainViewModel.MediaUploadStatus.GeneratingPreview ->
                        "Vorschau wird generiert…"
                    is com.securechat.app.ui.MainViewModel.MediaUploadStatus.Processing ->
                        "Wird verarbeitet…"
                    else -> ""
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Anhang-Panel oberhalb der Bottombar (über Tastatur oder über Input-Bar)
            AnimatedVisibility(
                visible = showAttachPanel,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(animationSpec = tween(150)),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(animationSpec = tween(150))
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Text(
                            text = "Anhang senden",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            userScrollEnabled = false
                        ) {
                            lazyGridItems(
                                listOf<Triple<ImageVector, String, Color>>(
                                    Triple(Icons.Default.Image,       "Bild",            Color(0xFF4CAF50)),
                                    Triple(Icons.Default.Videocam,    "Video",           Color(0xFF2196F3)),
                                    Triple(Icons.Default.MusicNote,   "Musik",           Color(0xFFFF9800)),
                                    Triple(Icons.Default.Mic,         "Sprach-\nnachricht", Color(0xFFE91E63)),
                                    Triple(Icons.Default.Description, "Dokument",        Color(0xFF607D8B)),
                                    Triple(Icons.Default.LocationOn,  "Standort",        Color(0xFF009688)),
                                    Triple(Icons.Default.Poll,        "Umfrage",         Color(0xFF9C27B0)),
                                    Triple(Icons.Default.ViewInAr,    "3D-Datei\n.stl .obj .3mf", Color(0xFFA8A800)),
                                    Triple(Icons.Default.Contacts,    "Kontakte",        Color(0xFF00BCD4)),
                                )
                            ) { (icon, label, tint) ->
                                AttachOption(icon = icon, label = label, tint = tint) {
                                    showAttachPanel = false
                                    when (label) {
                                        "Bild"            -> imageLauncher.launch("image/*")
                                        "Video"           -> videoLauncher.launch("video/*")
                                        "Musik"           -> audioMusicLauncher.launch("audio/*")
                                        "Sprach-\nnachricht" -> audioLauncher.launch("audio/*")
                                        "Dokument"        -> documentLauncher.launch("*/*")
                                        "Standort"        -> {
                                            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                                                == PackageManager.PERMISSION_GRANTED
                                            ) {
                                                showLocationSubMenu = true
                                                scope.launch { showAttachSheet = true; sheetState.show() }
                                            } else {
                                                locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                            }
                                        }
                                        "Umfrage"         -> showPollDialog = true
                                        "3D-Datei\n.stl .obj .3mf" -> threeDLauncher.launch("*/*")
                                        "Kontakte"        -> showContactPickerDialog = true
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Geplante Nachrichten Strip ───────────────────────────────────────
            if (scheduledMessages.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${scheduledMessages.size} geplante Nachricht${if (scheduledMessages.size != 1) "en" else ""}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { showScheduledMessagesDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("Verwalten", fontSize = 12.sp) }
                    }
                }
            }
            if (showScheduledMessagesDialog) {
                AlertDialog(
                    onDismissRequest = { showScheduledMessagesDialog = false },
                    title = { Text(stringResource(R.string.chat_menu_scheduled_messages)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (scheduledMessages.isEmpty()) {
                                Text(
                                    "Keine geplanten Nachrichten vorhanden.\nNachrichten können über das Eingabefeld geplant werden.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                )
                            }
                            scheduledMessages.forEach { sm ->
                                val fmt = remember(sm.scheduledAt) {
                                    try {
                                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                                        val outFmt = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm 'Uhr'", java.util.Locale.getDefault())
                                        outFmt.format(sdf.parse(sm.scheduledAt) ?: return@remember sm.scheduledAt)
                                    } catch (_: Exception) { sm.scheduledAt }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(fmt, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary)
                                            Text(sm.mediaType, fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(
                                            onClick = { viewModel.cancelScheduledMessage(sm.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, "Löschen",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showScheduledMessagesDialog = false }) { Text("Schließen") }
                    }
                )
            }

            // Eingabe-Bereich
            if (isLetheTeamChat) {
                // Lethe Team Chat — nur lesbar, keine Antwort möglich
                Surface(tonalElevation = 8.dp) {
                    Box(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Du kannst auf Lethe Team Nachrichten nicht antworten.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else if (isContactBlocked) {
                // Nutzer ist blockiert — komplett ausgegraut, keine Funktionen
                Surface(tonalElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {}, enabled = false, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.EmojiEmotions,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                        }
                        TextField(
                            value = TextFieldValue(""),
                            onValueChange = {},
                            enabled = false,
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    "Diese Funktion steht nicht zur Verfügung",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                disabledIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = {}, enabled = false, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                        }
                        IconButton(onClick = {}, enabled = false, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                        }
                    }
                }
            } else if (amIBlockedByContact) {
                // Partner hat MICH blockiert — komplett ausgegraut, keine Funktionen
                Surface(tonalElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {}, enabled = false, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.EmojiEmotions,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                        }
                        TextField(
                            value = TextFieldValue(""),
                            onValueChange = {},
                            enabled = false,
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    "Du kannst diesem Kontakt keine Nachrichten mehr senden",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                disabledIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = {}, enabled = false, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                        }
                        IconButton(onClick = {}, enabled = false, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                        }
                    }
                }
            } else {
            Surface(tonalElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Emoji-Button (links)
                    IconButton(
                        onClick = {
                            if (showEmojiPanel) {
                                // EmojiPicker → Tastatur: Picker schließen, Fokus+Tastatur wieder zeigen
                                // Transition-Flag setzt den Platz bis Tastatur sichtbar ist
                                keepBottomSpaceForTransition = true
                                showEmojiPanel = false
                                scope.launch {
                                    textFieldFocusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                            } else {
                                // Tastatur → EmojiPicker: Tastatur verstecken, Panel zeigen.
                                // requestFocus() wird NICHT aufgerufen – das würde die Tastatur
                                // sofort wieder aufploppen lassen wenn das Feld noch nicht fokussiert war.
                                keyboardController?.hide()
                                showEmojiPanel = true
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            if (showEmojiPanel) Icons.Default.Keyboard else Icons.Default.EmojiEmotions,
                            contentDescription = "Emojis",
                            tint = if (showEmojiPanel) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Texteingabe oder Live-Waveform während Aufnahme
                    if (isRecording) {
                        AudioWaveformVisualizer(
                            viewModel = viewModel,
                            isLocked = isRecordingLocked,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        )
                    } else {
                        TextField(
                            value = textState,
                            onValueChange = { newValue ->
                                try {
                                    // Tabulator-Zeichen und problematische Unicode-Steuerzeichen
                                    // sanitieren – Android's Text-Layout-Engine kann damit crashen
                                    val sanitized = newValue.text
                                        .replace("\r\n", "\n") // CRLF (Windows-Clipboard) → LF
                                        .replace("\r", "\n")   // CR (altes Mac-Format) → LF
                                        .replace("\t", "    ")
                                        .replace("\u2028", "\n")  // Line Separator → Zeilenumbruch
                                        .replace("\u2029", "\n")  // Paragraph Separator → Zeilenumbruch
                                        .filter { c ->
                                            (c.code >= 0x20 || c == '\n')
                                            && c.code != 0xFFFC  // Object Replacement Character
                                            && c.code != 0xFFFD  // Replacement Character
                                            && c.code != 0xFFFE
                                            && c.code != 0xFFFF
                                            // BiDi-Steuerzeichen → crashen Android's Text-Layout-Engine
                                            && c.code != 0x200E && c.code != 0x200F  // LRM / RLM
                                            && c.code !in 0x202A..0x202E            // LRE/RLE/PDF/LRO/RLO
                                            && c.code !in 0x2066..0x2069            // LRI/RLI/FSI/PDI
                                        }
                                    // Cursor-Position immer begrenzen – auch ohne Sanitierung
                                    // (einige IMEs liefern selection.end > text.length beim Einfügen)
                                    val clampedStart = newValue.selection.start.coerceIn(0, sanitized.length)
                                    val clampedEnd = newValue.selection.end.coerceIn(0, sanitized.length)
                                    // Composition-Range ebenfalls klemmen und beibehalten.
                                    // Ohne dies erzwingt das Löschen der Composition einen IME-Neustart,
                                    // was bei langen Texten zu einem IME↔Compose-Feedback-Loop und
                                    // internem Crash in BasicTextField führt (Compose BOM ≤ 2024.09).
                                    val clampedComposition = newValue.composition?.let { comp ->
                                        val s = comp.start.coerceIn(0, sanitized.length)
                                        val e = comp.end.coerceIn(0, sanitized.length)
                                        if (s < e) TextRange(s, e) else null
                                    }
                                    val sanitizedValue = TextFieldValue(
                                        text = sanitized,
                                        selection = TextRange(clampedStart, clampedEnd),
                                        composition = clampedComposition
                                    )
                                    if (sanitizedValue.text.length <= 10000) {
                                        val prevLen = textState.text.length
                                        textState = sanitizedValue
                                        if (showEmojiPanel) {
                                            // User tippt → Emoji-Panel schließen und Tastatur zeigen
                                            keepBottomSpaceForTransition = true
                                            showEmojiPanel = false
                                            keyboardController?.show()
                                        }
                                        // Tipp-Indikator nur bei echten Finger-Eingaben senden
                                        // (kein Paste/Einfügen – erkennbar an Längensprung > 2 Zeichen)
                                        val charDelta = sanitizedValue.text.length - prevLen
                                        if (sanitizedValue.text.isNotBlank() && charDelta in -2..2) {
                                            typingKeyCounter++
                                            if (typingKeyCounter % 10 == 0) {
                                                viewModel.sendTypingEvent(chatId)
                                            }
                                        }
                                    }
                                } catch (_: Exception) {
                                    // Defensiv: kein Crash bei unerwartetem Eingabe-Zustand
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(textFieldFocusRequester)
                                .then(
                                    if (isTextFieldFocused) {
                                        if (isGlossyMorphChat)
                                            Modifier.border(1.5.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(focusBorderColor, focusBorderColor2)), RoundedCornerShape(8.dp))
                                        else
                                            Modifier.border(1.5.dp, focusBorderColor, RoundedCornerShape(8.dp))
                                    } else Modifier
                                )
                                .onFocusChanged { focusState ->
                                    isTextFieldFocused = focusState.isFocused
                                    if (focusState.isFocused && showEmojiPanel) {
                                        // User hat direkt ins Textfeld getippt → Panel schließen
                                        showEmojiPanel = false
                                        keepBottomSpaceForTransition = false
                                    }
                                },
                            placeholder = { Text("Nachricht...") },
                            maxLines = if (enterToSend) 1 else 6,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                keyboardType = KeyboardType.Text,
                                imeAction = if (enterToSend) ImeAction.Send else ImeAction.Default
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (enterToSend && editingMessage == null) {
                                        val sendTxt = textState.text.trim()
                                        if (sendTxt.isNotBlank()) {
                                            val reply = replyToMessage
                                            val replyMsgId = reply?.messageId ?: reply?.clientMessageId
                                            val replyContentStr = if (reply?.mediaType in listOf("image", "video", "gif", "sticker"))
                                                (reply?.mediaUrl ?: reply?.content)?.take(200)
                                            else reply?.content?.take(200)
                                            val lp = linkPreview
                                            if (lp != null) {
                                                val json = org.json.JSONObject().apply {
                                                    put("url", lp.url); put("title", lp.title)
                                                    if (lp.description != null) put("description", lp.description)
                                                    if (lp.imageUrl != null) put("image", lp.imageUrl)
                                                    if (lp.siteName != null) put("site_name", lp.siteName)
                                                    if (sendTxt.isNotBlank()) put("text", sendTxt)
                                                }.toString()
                                                if (isGroup) viewModel.sendGroupMessage(chatId, json, replyContentStr, reply?.senderId, replyToMediaType = reply?.mediaType, mediaType = "link", replyToMessageId = replyMsgId)
                                                else viewModel.sendMessage(chatId, json, replyContentStr, reply?.senderId, mediaType = "link", replyToMediaType = reply?.mediaType, replyToMessageId = replyMsgId)
                                                viewModel.clearLinkPreview()
                                            } else if (isGroup) {
                                                viewModel.sendGroupMessage(chatId, sendTxt, replyContentStr, reply?.senderId, replyToMediaType = reply?.mediaType, replyToMessageId = replyMsgId)
                                            } else {
                                                viewModel.sendMessage(chatId, sendTxt, replyContentStr, reply?.senderId, replyToMediaType = reply?.mediaType, replyToMessageId = replyMsgId)
                                            }
                                            replyToMessage = null
                                            textState = TextFieldValue("")
                                            showEmojiPanel = false
                                            vibrateShort()
                                            if (chatSoundSendOn) {
                                                try {
                                                    val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 60)
                                                    tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 80)
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    }
                                }
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Anhang-Button (rechts, immer sichtbar solange keine Aufnahme)
                    if (!isRecording) {
                        IconButton(
                            onClick = {
                                if (showAttachPanel) {
                                    showAttachPanel = false
                                } else {
                                    showAttachPanel = true
                                    if (showEmojiPanel) showEmojiPanel = false
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = "Anhang",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Kamera-Button: Tap → Foto (oder Aufnahme stoppen), Halten → Videoaufnahme
                    if (textState.text.isBlank() && !isRecording) {
                        var camDragX by remember { mutableStateOf(0f) }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isVideoRecording) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                // Kurzer Tap: Foto aufnehmen ODER laufende Videoaufnahme stoppen
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            if (isVideoRecording) {
                                                stopVideoAndSend()
                                            } else {
                                                keyboardController?.hide()
                                                if (context.checkSelfPermission(Manifest.permission.CAMERA)
                                                    == PackageManager.PERMISSION_GRANTED
                                                ) {
                                                    launchCamera()
                                                } else {
                                                    cameraPermLauncher.launch(Manifest.permission.CAMERA)
                                                }
                                            }
                                        }
                                    )
                                }
                                // Langes Halten: Videoaufnahme starten/stoppen (wie Mikrofon-Button)
                                .pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            if (!isVideoRecording) {
                                                val hasCam = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                                val hasAud = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                                if (hasCam && hasAud) {
                                                    startVideoRecording(frontCamera = true)
                                                } else {
                                                    videoPermLauncher.launch(
                                                        arrayOf(
                                                            Manifest.permission.CAMERA,
                                                            Manifest.permission.RECORD_AUDIO
                                                        )
                                                    )
                                                }
                                                camDragX = 0f
                                            }
                                        },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            camDragX += delta.x
                                            if (camDragX < -80f && isVideoRecording) {
                                                cancelVideoRecording()
                                                camDragX = 0f
                                            }
                                        },
                                        onDragEnd = {
                                            if (isVideoRecording) stopVideoAndSend()
                                            camDragX = 0f
                                        },
                                        onDragCancel = {
                                            if (isVideoRecording) cancelVideoRecording()
                                            camDragX = 0f
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isVideoRecording) Icons.Default.FiberManualRecord else Icons.Default.CameraAlt,
                                contentDescription = if (isVideoRecording) "Aufnahme läuft" else "Kamera",
                                tint = if (isVideoRecording) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (textState.text.isBlank() && editingMessage == null) {
                        if (isRecordingLocked) {
                            // === GESPERRTER AUFNAHME-MODUS ===
                            // Pulsierender roter Stop-Button – Tap sendet, Swipe links bricht ab
                            val pulseTransition = rememberInfiniteTransition(label = "pulse")
                            val pulseAlpha by pulseTransition.animateFloat(
                                initialValue = 0.55f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulseAlpha"
                            )
                            var swipeDeltaX by remember { mutableStateOf(0f) }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha))
                                    .pointerInput(Unit) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {},
                                            onDrag = { change, delta ->
                                                change.consume()
                                                swipeDeltaX += delta.x
                                                if (swipeDeltaX < -60f) {
                                                    cancelRecording()
                                                    swipeDeltaX = 0f
                                                }
                                            },
                                            onDragEnd = { swipeDeltaX = 0f },
                                            onDragCancel = { swipeDeltaX = 0f }
                                        )
                                    }
                                    .clickable { stopAndSend() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "Aufnahme senden",
                                    tint = Color.Black
                                )
                            }
                        } else {
                            // === NORMALER MIC-BUTTON (Hold-to-Record + Swipe-Gesten) ===
                            // Kein CompositionLocalProvider mehr: Die 250ms-LongPress-Schwelle wird
                            // direkt per withTimeoutOrNull im pointerInput-Block realisiert.
                            // Das verhindert, dass bei jeder Recomposition (z.B. isRecording=true)
                            // ein neues anonymes ViewConfiguration-Objekt entsteht und der
                            // pointerInput-Node destabilisiert wird.
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isRecording) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary
                                    )
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            // Auf ersten Touch warten
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            var dragX = 0f
                                            var dragY = 0f

                                            // Wiederherstellung: Wenn durch einen früher
                                            // unterbrochenen Gesten-Stream noch eine (nicht
                                            // gesperrte) Aufnahme aktiv ist, beendet ein erneuter
                                            // Tipp sie zuverlässig → kein "lässt sich nicht stoppen".
                                            if (isRecording && !isRecordingLocked) {
                                                waitForUpOrCancellation()
                                                stopAndSend()
                                                return@awaitEachGesture
                                            }

                                            // 250 ms abwarten: wenn der Finger vorher losgelassen
                                            // wird, ist es kein Long-Press → nichts tun.
                                            val released = withTimeoutOrNull(250L) {
                                                waitForUpOrCancellation()
                                            }
                                            if (released != null) return@awaitEachGesture

                                            // Long-Press erkannt → Aufnahme starten
                                            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                                                != PackageManager.PERMISSION_GRANTED
                                            ) {
                                                audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                return@awaitEachGesture
                                            }
                                            startRecording()

                                            // Finger-Bewegung und Loslassen verfolgen
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                // Pointer-Stream durch Layout-Wechsel (TextField→Waveform,
                                                // Anhang-Button entfernt) unterbrochen: down.id verschwindet
                                                // aus den Changes. Früher: stilles break → isRecording blieb
                                                // true → Aufnahme ließ sich nicht mehr beenden. Jetzt wie ein
                                                // Loslassen behandeln (außer im gesperrten Modus).
                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                if (change == null) {
                                                    if (!isRecordingLocked) stopAndSend()
                                                    break
                                                }

                                                // positionChange() vor consume() lesen – danach gibt es Offset.Zero zurück
                                                val pos = change.positionChange()
                                                change.consume()

                                                if (change.changedToUpIgnoreConsumed()) {
                                                    // Finger losgelassen → senden (wenn nicht gesperrt)
                                                    if (!isRecordingLocked) stopAndSend()
                                                    break
                                                }

                                                dragX += pos.x
                                                dragY += pos.y

                                                // Swipe hoch → Aufnahme sperren
                                                if (dragY < -80f && isRecording && !isRecordingLocked) {
                                                    isRecordingLocked = true
                                                }
                                                // Swipe links → Aufnahme abbrechen
                                                if (dragX < -80f && isRecording) {
                                                    cancelRecording()
                                                    break
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = if (isRecording) "Aufnahme läuft" else "Sprachnachricht",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    } else if (editingMessage != null) {
                        // Speichern-Button (Bearbeitung aktiv)
                        IconButton(
                            onClick = {
                                val em = editingMessage!!
                                val trimmed = textState.text.trim()
                                if (trimmed.isNotBlank()) {
                                    viewModel.editMessage(em.localId, trimmed)
                                }
                                editingMessage = null
                                textState = TextFieldValue("")
                                showEmojiPanel = false
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Speichern", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    } else {
                        // Senden-Button (wenn Text vorhanden) — Long-Press öffnet Zeitplaner
                        val sendText = textState.text.trim()
                        val reply = replyToMessage
                        val replyMsgId = reply?.messageId ?: reply?.clientMessageId
                        val lp = linkPreview
                        fun replyContent(msg: MessageEntity?) =
                            if (msg?.mediaType in listOf("image", "video", "gif", "sticker"))
                                (msg?.mediaUrl ?: msg?.content)?.take(200)
                            else msg?.content?.take(200)

                        fun doSend() {
                            if (sendText.isBlank()) return
                            if (lp != null) {
                                val rawText = textState.text.trim()
                                val json = org.json.JSONObject().apply {
                                    put("url", lp.url)
                                    put("title", lp.title)
                                    if (lp.description != null) put("description", lp.description)
                                    if (lp.imageUrl != null) put("image", lp.imageUrl)
                                    if (lp.siteName != null) put("site_name", lp.siteName)
                                    if (rawText.isNotBlank()) put("text", rawText)
                                }.toString()
                                if (isGroup) {
                                    viewModel.sendGroupMessage(chatId, json,
                                        replyContent(reply), reply?.senderId,
                                        replyToMediaType = reply?.mediaType, mediaType = "link",
                                        replyToMessageId = replyMsgId)
                                } else {
                                    viewModel.sendMessage(chatId, json,
                                        replyContent(reply), reply?.senderId,
                                        mediaType = "link",
                                        replyToMediaType = reply?.mediaType,
                                        replyToMessageId = replyMsgId)
                                }
                                viewModel.clearLinkPreview()
                            } else if (isGroup) {
                                viewModel.sendGroupMessage(chatId, textState.text.trim(),
                                    replyContent(reply), reply?.senderId,
                                    replyToMediaType = reply?.mediaType,
                                    replyToMessageId = replyMsgId)
                            } else {
                                viewModel.sendMessage(chatId, textState.text.trim(),
                                    replyContent(reply), reply?.senderId,
                                    replyToMediaType = reply?.mediaType,
                                    replyToMessageId = replyMsgId)
                            }
                            replyToMessage = null
                            textState = TextFieldValue("")
                            showEmojiPanel = false
                            if (onboardingStep == com.securechat.app.ui.OnboardingStep.FIRST_MESSAGE) {
                                viewModel.completeOnboardingStep(com.securechat.app.ui.OnboardingStep.FIRST_MESSAGE)
                                showFirstMessageCelebration = true
                            }
                            vibrateShort()
                            if (chatSoundSendOn) {
                                try {
                                    val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
                                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                                } catch (_: Exception) {}
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .combinedClickable(
                                    onClick = { doSend() },
                                    onLongClick = {
                                        if (sendText.isNotBlank() && !isGroup) {
                                            scheduleDialogText = sendText
                                            showScheduleDialog = true
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Senden", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
            } // closes else (isContactBlocked)
        }
    }
        // --- Bottom-Bereich: Tastatur-Platzhalter oder Emoji-Panel ---
        // Der EmojiPicker ist ein direktes Kind der Hauptspalte (Geschwister des
        // ContentBox). Dadurch belegt er exakt den Bereich wo die Tastatur war,
        // statt oberhalb dieses Bereichs zu schweben.
        if (showEmojiPanel) {
            // Wenn die echte System-Tastatur zusätzlich sichtbar ist (z.B. weil die Emoji- oder
            // GIF-Suche innerhalb des Panels fokussiert wurde), hat sich das Fenster bereits um
            // deren Höhe verkleinert – das Panel darf dann keine volle Tastaturhöhe mehr
            // beanspruchen, sonst wird die Suchleiste hinter der echten Tastatur verdeckt.
            val panelHeight = if (imeBottomPx > 0) {
                180.dp
            } else {
                (with(density) { lastImeHeightPx.toDp() } - 20.dp).coerceAtLeast(350.dp)
            }
            // Bei adjustNothing schiebt die System-Tastatur den Inhalt NICHT hoch, sondern
            // zeichnet über ihn. Ist die Suche im Panel fokussiert (imeBottomPx > 0), muss
            // das Panel per Bottom-Padding um die Tastaturhöhe angehoben werden, damit es
            // ÜBER der Tastatur schwebt statt dahinter zu verschwinden.
            val imeBottomDp = with(density) { imeBottomPx.toDp() }
            EmojiPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (imeBottomPx > 0) Modifier.padding(bottom = imeBottomDp) else Modifier)
                    .height(panelHeight),
                onEmojiClick = { emoji -> insertEmoji(emoji) },
                showLumisTab = true,
                onLumisClick = { lumisType ->
                    showEmojiPanel = false
                    viewModel.sendLumis(contactId = chatId, lumisType = lumisType, isGroup = isGroup)
                },
                viewModel = viewModel,
                onGifSelected = { gifUrl ->
                    showEmojiPanel = false
                    viewModel.sendGifMessage(chatId, gifUrl)
                },
                onStickerSelected = { stickerUrl ->
                    showEmojiPanel = false
                    viewModel.sendStickerMessage(chatId, stickerUrl)
                },
                onBackspace = { backspaceText() }
            )
        } else if (bottomSpaceDp > 0.dp) {
            Spacer(modifier = Modifier.fillMaxWidth().height(bottomSpaceDp))
        }
    } // closes Column(fillMaxSize) Hauptspalte

    // ── Video-Aufnahme-Overlay ─────────────────────────────────────────────────
    // AndroidView ist IMMER in der Composition, damit kein View-Hierarchy-Change
    // während einer laufenden Gesture den Touch-Event-Stream unterbricht.
    // Sichtbarkeit wird per update{} gesteuert statt per if-Bedingung.
    // Wenn NICHT aufgenommen wird: Box auf 0dp schrumpfen, damit der unsichtbare
    // AndroidView (CameraX-PreviewView) keine Touch-Events mehr abfangen kann.
    // Ohne diese Maßnahme liegt der hochgeschobene Senden-Button (imePadding) im
    // 80%-Overlay-Bereich und ist durch den INVISIBLE-View nicht tappbar.
    // Kreisförmiges Video-Recording-Overlay (Frontkamera-Modus)
    if (isVideoRecording && circleVideoMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .align(Alignment.TopCenter)
        ) {
            // Timer-Badge oben
            val min = videoRecordingDurationSec / 60
            val sec = videoRecordingDurationSec % 60
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Red)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "%d:%02d".format(min, sec),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            // Kreisförmige Kamera-Vorschau mit Fortschrittsring
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                // Fortschrittsring (Akzentfarbe)
                val progress = videoRecordingDurationSec / 60f
                val accentColor = MaterialTheme.colorScheme.primary
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 6.dp.toPx()
                    val inset = stroke / 2f
                    // Hintergrundring
                    drawArc(
                        color = Color.White.copy(alpha = 0.2f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                    )
                    // Fortschrittsring
                    drawArc(
                        color = accentColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = stroke,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }
                // Kreisförmiger Kamera-Preview
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { videoPreviewViewCircle },
                        update = { it.visibility = if (isVideoRecording) View.VISIBLE else View.INVISIBLE },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Untere Steuerelemente
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp, start = 32.dp, end = 32.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Kamera-Wechsel Button
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable {
                            // Kamera wechseln: aktuell immer Frontkamera
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FlipCameraAndroid,
                        contentDescription = "Kamera wechseln",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // "Zum Abbrechen wischen" Text
                Text(
                    "← Zum Abbrechen wischen",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                // Lock-Icon (Platzhalter, wie im Screenshot)
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Gesperrt",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }

    // Standard-Video-Aufnahme-Overlay (Rückkamera)
    Box(
        modifier = if (isVideoRecording && !circleVideoMode)
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .align(Alignment.TopCenter)
                .background(Color.Black)
        else
            Modifier.size(0.dp)
    ) {
        AndroidView(
            factory = { videoPreviewView },
            update = { it.visibility = if (isVideoRecording && !circleVideoMode) View.VISIBLE else View.INVISIBLE },
            modifier = Modifier.fillMaxSize()
        )
        if (isVideoRecording && !circleVideoMode) {
            // Roter Punkt + Timer oben links
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val pulseTransition = rememberInfiniteTransition(label = "vidPulse")
                val pulseAlpha by pulseTransition.animateFloat(
                    initialValue = 0.4f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600), repeatMode = RepeatMode.Reverse
                    ),
                    label = "vidPulseAlpha"
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = pulseAlpha))
                )
                Spacer(modifier = Modifier.width(8.dp))
                val min2 = videoRecordingDurationSec / 60
                val sec2 = videoRecordingDurationSec % 60
                Text(
                    text = "%02d:%02d".format(min2, sec2),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            // Hinweise unten
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Loslassen zum Senden",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "← Schieben zum Abbrechen",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    }

    // ── Lumis-Overlay ─────────────────────────────────────────────────────────
    // Liegt als letztes Kind der Box über dem gesamten Chat-Bereich.
    // Pointer-Events passieren durch (kein pointerInput-Verbrauch), sodass
    // TopBar und BottomBar weiterhin bedienbar bleiben.
    // Die Animation dauert exakt 6 Sekunden; danach wird onLumisDismissed() aufgerufen.
    if (activeLumis != LumisType.NONE) {
        LumisPlayer(
            lumisType      = activeLumis,
            onAnimationEnd = { viewModel.onLumisDismissed() },
            modifier       = Modifier.fillMaxSize()
        )
    }
    // ── FloatingSharedMusicPlayer ─────────────────────────────────────────────
    if (listenTogetherActive && listenTogetherChatId == chatId && showListenTogetherPlayer) {
        val floatingCastAvailable by viewModel.castDiscoveryManager.castAvailable.collectAsState()
        FloatingSharedMusicPlayer(
            track           = listenTogetherTrack,
            isPlaying       = listenTogetherPlaying,
            positionMs      = listenTogetherPos,
            pendingAction   = listenTogetherPending,
            trackIndex      = listenTogetherTrackIndex,
            playlistSize    = listenTogetherPlaylist.size.coerceAtLeast(1),
            shuffleActive   = listenTogetherShuffleActive,
            castAvailableBar = floatingCastAvailable,
            onCastClick     = {
                viewModel.castDiscoveryManager.pendingCastUrl = listenTogetherTrack?.url
                viewModel.castDiscoveryManager.requestDevicePicker()
            },
            onRequestAction = { action -> viewModel.requestPlayAction(action) },
            onPositionSync  = { pos -> viewModel.syncListenTogetherState(listenTogetherPlaying, pos) },
            onShuffle       = { viewModel.shuffleListenTogetherPlaylist() },
            onCollapse      = { showListenTogetherPlayer = false },
            onClose         = { viewModel.leaveListenTogether() },
            modifier        = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp, start = 12.dp, end = 12.dp)
        )
    }
    // ── DetachedMusicPlayerOverlay ────────────────────────────────────────────
    if (showDetachedMusicPlayer && allChatMusicUrls.isNotEmpty()) {
        DetachedMusicPlayerOverlay(
            viewModel        = viewModel,
            allChatMusicUrls = allChatMusicUrls,
            onDismiss        = { showDetachedMusicPlayer = false },
            modifier         = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp, start = 12.dp, end = 12.dp)
        )
    }
    if (showListenTogetherSetup) {
        ListenTogetherSetupScreen(
            chatId            = chatId,
            availableTracks   = listenTogetherAvailableTracks,
            viewModel         = viewModel,
            onDismiss         = { showListenTogetherSetup = false },
            onStart           = { playlist, _ ->
                showListenTogetherSetup = false
                if (playlist.isNotEmpty()) {
                    viewModel.saveListenTogetherPlaylist(chatId, playlist)
                }
            }
        )
    }
    // ── Proximity-Touch-Sperre während Sprachnachricht am Ohr ─────────────────
    // Meldet der Näherungssensor "nah" (Ohr/Gesicht am Display) während eine
    // Sprachnachricht abgespielt wird, legt sich diese unsichtbare Box als
    // letztes Kind über den gesamten Chat und verschluckt ALLE Touch-Eingaben,
    // damit nicht versehentlich Buttons o.ä. ausgelöst werden. Funktioniert auch
    // auf Xiaomi/MIUI, wo der PROXIMITY_SCREEN_OFF_WAKE_LOCK nicht greift.
    val audioProximityNear by viewModel.audioProximityNear.collectAsState()
    if (audioProximityNear) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        )
    }
    } // closes Box (fillMaxSize für Lumis-Overlay)

    // Lumis-Auswahl-Dialog
    if (showLumisPicker) {
        LumisPickerDialog(
            onLumisSelected = { selectedType ->
                showLumisPicker = false
                viewModel.sendLumis(contactId = chatId, lumisType = selectedType, isGroup = isGroup)
            },
            onDismiss = { showLumisPicker = false }
        )
    }

    // Listen-Together-Einladungs-Dialog
    if (listenTogetherInvite != null) {
        val invite = listenTogetherInvite!!
        val inviteContact = contacts.find { it.userId == invite.fromUserId }
        val inviterName = inviteContact?.username ?: inviteContact?.fakeNumber ?: invite.fromUserId
        AlertDialog(
            onDismissRequest = { viewModel.dismissListenTogetherInvite() },
            icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
            title = { Text("Listen Together") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$inviterName lädt dich ein, gemeinsam Musik zu hören.")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = invite.track.title.ifBlank { "Unbekannter Titel" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (invite.track.artist.isNotBlank()) {
                        Text(
                            text = invite.track.artist,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.acceptListenTogether()
                    showListenTogetherPlayer = true
                }) { Text("Beitreten") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissListenTogetherInvite() }) { Text("Ablehnen") }
            }
        )
    }

    // ── Listen-Together: Host wartet auf Bestätigung ──────────────────────────
    if (listenTogetherWaiting) {
        AlertDialog(
            onDismissRequest = {},
            icon  = { Icon(Icons.Default.MusicNote, contentDescription = null) },
            title = { Text("Anfrage wurde gesendet") },
            text  = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Text(
                        text  = "Warten auf Bestätigung",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.leaveListenTogether() }) { Text("Abbrechen") }
            }
        )
    }

    // ── Listen-Together: Anfrage abgelehnt ────────────────────────────────────
    if (listenTogetherRejected) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissListenTogetherRejected() },
            icon  = { Icon(Icons.Default.MusicOff, contentDescription = null) },
            title = { Text("Anfrage abgelehnt") },
            text  = { Text("Die Anfrage wurde abgelehnt.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissListenTogetherRejected() }) { Text("OK") }
            }
        )
    }

    // ── Listen-Together: Aktionsanfrage vom Partner bestätigen ─────────────────
    if (listenTogetherActionReq != null) {
        val req = listenTogetherActionReq!!
        val actionLabel = when (req.action) {
            "play"  -> "abspielen"
            "pause" -> "pausieren"
            "next"  -> "zum nächsten Titel springen"
            "prev"  -> "zum vorherigen Titel springen"
            else    -> req.action
        }
        AlertDialog(
            onDismissRequest = { viewModel.rejectPeerAction() },
            icon  = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
            title = { Text("Aktion angefragt") },
            text  = { Text("${req.senderName} möchte $actionLabel.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmPeerAction() }) { Text("Bestätigen") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.rejectPeerAction() }) { Text("Ablehnen") }
            }
        )
    }

    // Gruppenbearbeitung-Dialog
    if (showEditGroupDialog && isGroup) {
        val currentGroup = groups.find { it.groupId == chatId }
        if (currentGroup != null) {
            val contactsList by viewModel.contacts.collectAsState(initial = emptyList())
            EditGroupDialog(
                group = currentGroup,
                allContacts = contactsList,
                onDismiss = { showEditGroupDialog = false },
                onSave = { name, addIds, removeIds ->
                    if (name != currentGroup.name) viewModel.updateGroup(chatId, name)
                    if (addIds.isNotEmpty()) viewModel.addGroupMembers(chatId, addIds)
                    removeIds.forEach { viewModel.removeGroupMember(chatId, it) }
                    showEditGroupDialog = false
                }
            )
        }
    }

    // Gruppen-Termin-Kalender
    if (showGroupCalendarSheet && isGroup) {
        GroupAppointmentCalendarSheet(
            groupId = chatId,
            viewModel = viewModel,
            onDismiss = { showGroupCalendarSheet = false }
        )
    }

    // Mitgliederverwaltung
    if (showGroupMembersSheet && isGroup) {
        val currentGroup = groups.find { it.groupId == chatId }
        GroupMembersManagementSheet(
            groupId = chatId,
            isCreator = currentGroup?.createdBy == viewModel.currentUser.collectAsState().value?.userId,
            viewModel = viewModel,
            onDismiss = { showGroupMembersSheet = false }
        )
    }

    // Gruppe-Bearbeiten-Vollbild
    if (showGroupEditScreen && isGroup) {
        val currentGroup = groups.find { it.groupId == chatId }
        GroupEditScreen(
            group = currentGroup,
            groupId = chatId,
            viewModel = viewModel,
            onDismiss = { showGroupEditScreen = false }
        )
    }

    // Anhang-Auswahl Bottom Sheet
    if (showAttachSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false; showLocationSubMenu = false },
            sheetState = sheetState
        ) {
            if (showLocationSubMenu) {
                // Standort-Untermenü
                AttachLocationSubMenu(
                    context = context,
                    receiverId = chatId,
                    onBack = { showLocationSubMenu = false },
                    onSend = { message ->
                        if (isGroup) viewModel.sendGroupMessage(chatId, message)
                        else viewModel.sendMessage(chatId, message)
                        showAttachSheet = false
                        showLocationSubMenu = false
                    },
                    onNavigateToLiveMaps = {
                        showAttachSheet = false
                        showLocationSubMenu = false
                        onNavigateToLiveMaps?.invoke(chatId)
                    },
                    onUploadMapPreview = { lat, lng -> viewModel.uploadMapPreviewImage(lat, lng) }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "Anhang senden",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        userScrollEnabled = false
                    ) {
                        lazyGridItems(
                            listOf<Triple<ImageVector, String, Color>>(
                                Triple(Icons.Default.Image,       "Bild",            Color(0xFF4CAF50)),
                                Triple(Icons.Default.Videocam,    "Video",           Color(0xFF2196F3)),
                                Triple(Icons.Default.MusicNote,   "Musik",           Color(0xFFFF9800)),
                                Triple(Icons.Default.Mic,         "Sprach-\nnachricht", Color(0xFFE91E63)),
                                Triple(Icons.Default.Description, "Dokument",        Color(0xFF607D8B)),
                                Triple(Icons.Default.LocationOn,  "Standort",        Color(0xFF009688)),
                                Triple(Icons.Default.Poll,        "Umfrage",         Color(0xFF9C27B0)),
                                Triple(Icons.Default.ViewInAr,    "3D-Datei\n.stl .obj .3mf", Color(0xFFA8A800)),
                                Triple(Icons.Default.Contacts,    "Kontakte",        Color(0xFF00BCD4)),
                                Triple(Icons.Default.Movie,       "Video-Editor",    Color(0xFF3F51B5)),
                            )
                        ) { (icon, label, tint) ->
                            AttachOption(icon = icon, label = label, tint = tint) {
                                when (label) {
                                    "Bild"            -> { showAttachSheet = false; imageLauncher.launch("image/*") }
                                    "Video"           -> { showAttachSheet = false; videoLauncher.launch("video/*") }
                                    "Musik"           -> { showAttachSheet = false; audioMusicLauncher.launch("audio/*") }
                                    "Sprach-\nnachricht" -> { showAttachSheet = false; audioLauncher.launch("audio/*") }
                                    "Dokument"        -> { showAttachSheet = false; documentLauncher.launch("*/*") }
                                    "Standort"        -> {
                                        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                                            == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            showLocationSubMenu = true
                                        } else {
                                            showAttachSheet = false
                                            locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                        }
                                    }
                                    "Umfrage"         -> { showAttachSheet = false; showPollDialog = true }
                                    "3D-Datei\n.stl .obj .3mf" -> { showAttachSheet = false; threeDLauncher.launch("*/*") }
                                    "Kontakte"        -> { showAttachSheet = false; showContactPickerDialog = true }
                                    "Video-Editor"    -> { showAttachSheet = false; onNavigateToVideoEditorEmpty?.invoke() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Umfrage-Dialog
    if (showPollDialog) {
        PollCreationDialog(
            onDismiss = { showPollDialog = false },
            onCreatePoll = { question, options, allowMultipleChoice ->
                viewModel.createPoll(question, options, chatId, allowMultipleChoice)
                showPollDialog = false
            }
        )
    }

    // Kontakt-Auswahl-Dialog
    if (showContactPickerDialog) {
        ContactPickerDialog(
            contacts = contacts,
            onDismiss = { showContactPickerDialog = false },
            onSendContact = { selectedContact ->
                val json = org.json.JSONObject().apply {
                    put("user_id", selectedContact.userId)
                    put("username", selectedContact.customAlias ?: selectedContact.username ?: selectedContact.fakeNumber)
                    put("fake_number", selectedContact.fakeNumber)
                    put("lethe_id", selectedContact.letheId ?: selectedContact.fakeNumber)
                    put("profile_image", selectedContact.profileImageUrl ?: "")
                    put("is_anonymous", selectedContact.isAnonymous)
                }
                viewModel.sendMessage(chatId, json.toString(), mediaType = "contact_card")
                showContactPickerDialog = false
            }
        )
    }

    // Medien-Galerie
    if (showMediaGallery) {
        val mediaMessages = messages
            .filter {
                it.mediaType in listOf(
                    "image", "multi_image", "video", "audio", "audio_music",
                    "sticker", "gif", "document", "file", "pdf", "3dprint", "3d"
                )
            }
            .sortedBy { it.timestamp }
        ChatMediaDialog(
            messages = mediaMessages,
            viewModel = viewModel,
            onDismiss = { showMediaGallery = false }
        )
    }

    // Kontakt-Profilanzeige (nur bei nicht-anonymen 1:1-Chats)
    if (showContactProfile && contact != null && !contact.isAnonymous) {
        val mediaMessages = messages
            .filter {
                it.mediaType in listOf(
                    "image", "multi_image", "video", "audio", "audio_music",
                    "sticker", "gif", "document", "file", "pdf", "3dprint", "3d"
                )
            }
            .sortedBy { it.timestamp }
        ContactProfileDialog(
            contact = contact,
            messages = mediaMessages,
            viewModel = viewModel,
            onDismiss = { showContactProfile = false },
            onForwardMessage = { /* wird intern behandelt */ }
        )
    }

    // Gruppen-Info-Screen (Klick auf Gruppenbild)
    if (showGroupInfoScreen && isGroup) {
        val currentGroup = groups.find { it.groupId == chatId }
        val mediaMessages = messages
            .filter {
                it.mediaType in listOf(
                    "image", "multi_image", "video", "audio", "audio_music",
                    "sticker", "gif", "document", "file", "pdf", "3dprint", "3d"
                )
            }
            .sortedBy { it.timestamp }
        GroupInfoScreen(
            group = currentGroup,
            groupId = chatId,
            messages = mediaMessages,
            viewModel = viewModel,
            onDismiss = { showGroupInfoScreen = false }
        )
    }

    // Gruppen-Mitglied-Profil (Klick auf Absender-Avatar in Gruppe)
    val memberProfileId = groupMemberProfileUserId
    if (memberProfileId != null && isGroup) {
        val memberMessages = messages
            .filter {
                it.senderId == memberProfileId &&
                it.mediaType in listOf(
                    "image", "multi_image", "video", "audio", "audio_music",
                    "sticker", "gif", "document", "file", "pdf", "3dprint", "3d"
                )
            }
            .sortedBy { it.timestamp }
        val memberContact = contacts.find { it.userId == memberProfileId && !it.isAnonymous }
        if (memberContact != null) {
            ContactProfileDialog(
                contact = memberContact,
                messages = memberMessages,
                viewModel = viewModel,
                onDismiss = { groupMemberProfileUserId = null }
            )
        } else {
            val memberInfo = groupMembersMap[chatId]?.find { it.userId == memberProfileId }
            GroupMemberProfileDialog(
                memberInfo = memberInfo,
                memberId = memberProfileId,
                messages = memberMessages,
                viewModel = viewModel,
                onDismiss = { groupMemberProfileUserId = null }
            )
        }
    }

    // Onboarding: Feier-Overlay nach erster Nachricht
    if (showFirstMessageCelebration) {
        FirstMessageCelebrationOverlay(
            onDismiss = { showFirstMessageCelebration = false },
            ageVerified = currentUserForSound?.ageVerified ?: false
        )
    }

    // ── Zeitplaner-Dialog ────────────────────────────────────────────────────
    if (showScheduleDialog) {
        var pickedDate by remember { mutableStateOf<java.util.Calendar?>(null) }
        var pickedTime by remember { mutableStateOf<Pair<Int,Int>?>(null) }
        val ctx = LocalContext.current

        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            title = { Text("Nachricht planen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Vorschau des Nachrichtentextes
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            scheduleDialogText.take(120) + if (scheduleDialogText.length > 120) "…" else "",
                            modifier = Modifier.padding(10.dp),
                            fontSize = 13.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Datum wählen
                    OutlinedButton(
                        onClick = {
                            val now = java.util.Calendar.getInstance()
                            android.app.DatePickerDialog(
                                ctx,
                                { _, y, m, d ->
                                    pickedDate = java.util.Calendar.getInstance().apply {
                                        set(y, m, d)
                                    }
                                },
                                now.get(java.util.Calendar.YEAR),
                                now.get(java.util.Calendar.MONTH),
                                now.get(java.util.Calendar.DAY_OF_MONTH)
                            ).also { dlg ->
                                dlg.datePicker.minDate = System.currentTimeMillis()
                            }.show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Event, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(pickedDate?.let {
                            "%02d.%02d.%04d".format(
                                it.get(java.util.Calendar.DAY_OF_MONTH),
                                it.get(java.util.Calendar.MONTH) + 1,
                                it.get(java.util.Calendar.YEAR)
                            )
                        } ?: "Datum wählen")
                    }

                    // Uhrzeit wählen
                    OutlinedButton(
                        onClick = {
                            val now = java.util.Calendar.getInstance()
                            android.app.TimePickerDialog(
                                ctx,
                                { _, h, min -> pickedTime = h to min },
                                now.get(java.util.Calendar.HOUR_OF_DAY),
                                now.get(java.util.Calendar.MINUTE),
                                true
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(pickedTime?.let { "%02d:%02d Uhr".format(it.first, it.second) } ?: "Uhrzeit wählen")
                    }
                }
            },
            confirmButton = {
                val canConfirm = pickedDate != null && pickedTime != null
                Button(
                    onClick = {
                        val cal = pickedDate!!.clone() as java.util.Calendar
                        val (h, min) = pickedTime!!
                        cal.set(java.util.Calendar.HOUR_OF_DAY, h)
                        cal.set(java.util.Calendar.MINUTE, min)
                        cal.set(java.util.Calendar.SECOND, 0)
                        // ISO-8601 in UTC – explizit UTC setzen, sonst würde die Geräte-Zeitzone verwendet
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        val scheduledAtStr = sdf.format(cal.time)
                        val textToSchedule = scheduleDialogText
                        val reply = replyToMessage
                        val replyMsgId = reply?.messageId ?: reply?.clientMessageId
                        viewModel.scheduleMessage(
                            chatId = chatId,
                            content = textToSchedule,
                            scheduledAt = scheduledAtStr,
                            replyToContent = reply?.content?.take(200),
                            replyToSenderId = reply?.senderId,
                            replyToMediaType = reply?.mediaType,
                            replyToMessageId = replyMsgId,
                        ) { ok, errorMsg ->
                            if (ok) {
                                textState = TextFieldValue("")
                                replyToMessage = null
                                android.widget.Toast.makeText(ctx, "Nachricht geplant", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(ctx, errorMsg ?: "Fehler beim Planen", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                        showScheduleDialog = false
                    },
                    enabled = canConfirm
                ) { Text("Planen") }
            },
            dismissButton = {
                TextButton(onClick = { showScheduleDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    // In-App Kamera (Foto + Video, Zoom, Kamera wechseln, Blitz)
    if (showInAppCamera) {
        InAppCameraScreen(
            onPhotoCaptured = { uri ->
                showInAppCamera = false
                scope.launch {
                    val croppedUri = withContext(Dispatchers.IO) { cropTo9x16(uri) }
                    if (onNavigateToImageEditor != null) {
                        onNavigateToImageEditor(croppedUri)
                    } else {
                        if (isGroup) viewModel.sendGroupMediaMessage(chatId, croppedUri, "image")
                        else viewModel.sendMediaMessage(chatId, croppedUri, "image")
                    }
                }
            },
            onVideoCaptured = { uri ->
                showInAppCamera = false
                if (isGroup) viewModel.sendGroupMediaMessage(chatId, uri, "video")
                else viewModel.sendMediaMessage(chatId, uri, "video")
            },
            onDismiss = { showInAppCamera = false }
        )
    }

    if (showGamePickerDialog) {
        val pName = contact?.username ?: contact?.fakeNumber ?: ""
        ChatGamePickerDialog(
            partnerName = pName,
            onNavigateToMultiplayer = {
                showGamePickerDialog = false
                onNavigateToGames?.invoke(chatId, pName)
            },
            onNavigateToPinball = {
                showGamePickerDialog = false
                onNavigateToPinball?.invoke()
            },
            onNavigateToJumpOrDie = {
                showGamePickerDialog = false
                onNavigateToJumpOrDie?.invoke()
            },
            onDismiss = { showGamePickerDialog = false }
        )
    }
}

@Composable
private fun ChatGamePickerDialog(
    partnerName: String,
    onNavigateToMultiplayer: () -> Unit,
    onNavigateToPinball: () -> Unit,
    onNavigateToJumpOrDie: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Spiel wählen",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                // Multiplayer-Spiele (Jump & Run, Sketch 'n' Check … mit Partner)
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToMultiplayer() },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Multiplayer-Spiele", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Jump & Run, Sketch 'n' Check · mit $partnerName", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // Singleplayer-Spiele (Jump or Die + Flipper)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("Singleplayer", fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onNavigateToJumpOrDie() }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.DirectionsRun, contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Jump or Die", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Singleplayer · Weltrangliste", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onNavigateToPinball() }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.SportsEsports, contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Lethe Flipper", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Singleplayer · Weltrangliste", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Abbrechen")
                }
            }
        }
    }
}

/** Animierter "tippt…"-Hinweis mit 1-2-3-Punkt-Zyklus. */
@Composable
private fun TypingIndicatorText() {
    val transition = rememberInfiniteTransition(label = "typing")
    val dotCount by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(900)),
        label = "dots"
    )
    Text(
        text = "tippt" + ".".repeat((dotCount.toInt() % 3) + 1),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
    )
}

/** Animierte 3-Punkte-Blase im Nachrichtenverlauf (wie WhatsApp). */
@Composable
private fun TypingBubble(avatarUrl: String? = null) {
    val transition = rememberInfiniteTransition(label = "typing_bubble")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = if (avatarUrl != null)
                RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
            else
                RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                (0..2).forEach { i ->
                    val offsetY by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 1200
                                0f at 0 using FastOutSlowInEasing
                                -6f at 480 using FastOutSlowInEasing
                                0f at 960
                                0f at 1200
                            },
                            initialStartOffset = StartOffset(i * 200)
                        ),
                        label = "dot_$i"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .offset(y = offsetY.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                CircleShape
                            )
                    )
                }
            }
        }
        if (avatarUrl != null) {
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun EmojiPanel(
    modifier: Modifier = Modifier,
    onEmojiClick: (String) -> Unit,
    showLumisTab: Boolean = false,
    onLumisClick: ((LumisType) -> Unit)? = null,
    viewModel: com.securechat.app.ui.MainViewModel? = null,
    onGifSelected: ((String) -> Unit)? = null,
    onStickerSelected: ((String) -> Unit)? = null,
    onGifSearchKeyboardActive: ((Boolean) -> Unit)? = null,
    onBackspace: (() -> Unit)? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var emojiSearchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    // Rangliste einmalig beim Öffnen berechnen (frisch, weil EmojiPanel nur bei showEmojiPanel=true existiert)
    val rankedMostUsed = remember { EmojiUsageTracker.getRankedEmojis(context, MOST_USED_EMOJIS) }
    // Tracker-Wrapper: Nutzung aufzeichnen, dann Original-Handler aufrufen
    val trackAndClick: (String) -> Unit = { emoji ->
        EmojiUsageTracker.recordUsage(context, emoji)
        onEmojiClick(emoji)
    }
    // Suchergebnisse (reaktiv auf Query-Änderungen)
    val emojiSearchResults = remember(emojiSearchQuery) { searchEmojis(emojiSearchQuery) }

    LaunchedEffect(selectedTab) {
        if (selectedTab != 1) onGifSearchKeyboardActive?.invoke(false)
    }

    Surface(
        tonalElevation = 4.dp,
        modifier = modifier
    ) {
        Column {
            // Tab-Leiste: Emojis | GIF | Sticker | Lumis(optional)
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Emojis", fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("GIF", fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Sticker", fontSize = 13.sp) }
                )
                if (showLumisTab) {
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Lumis", fontSize = 13.sp) }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    // Emoji-Inhalt mit Suchleiste
                    Column(modifier = Modifier.fillMaxSize()) {
                    // Suchleiste (verkürzt) + Backspace-Taste rechts zum Löschen im Eingabefeld
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = emojiSearchQuery,
                        onValueChange = { emojiSearchQuery = it },
                        placeholder = { Text("Suchen… z.B. liebe", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = if (emojiSearchQuery.isNotEmpty()) {
                            { IconButton(onClick = { emojiSearchQuery = "" }, modifier = Modifier.size(18.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Löschen", modifier = Modifier.size(16.dp))
                            } }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    )
                    if (onBackspace != null) {
                        IconButton(
                            onClick = { onBackspace() },
                            modifier = Modifier.padding(start = 6.dp).size(44.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Zeichen löschen",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (emojiSearchQuery.isNotBlank()) {
                            // Suchergebnisse anzeigen
                            if (emojiSearchResults.isEmpty()) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                                        Text("Keine Emojis gefunden", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 13.sp)
                                    }
                                }
                            } else {
                                item {
                                    Text(
                                        "Ergebnisse (${emojiSearchResults.size})",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                    )
                                }
                                items(emojiSearchResults.chunked(8)) { rowEmojis ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        rowEmojis.forEach { emoji ->
                                            TextButton(
                                                onClick = { trackAndClick(emoji) },
                                                contentPadding = PaddingValues(4.dp),
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Text(emoji, fontSize = 22.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                        // Meistgenutzte Reihe – als 2D-Grid (à 8 Emojis) damit 🎉 auch im
                        // Landscape-/Call-Modus ohne Scrollen sichtbar ist
                        item {
                            Text(
                                "Meistgenutzt",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                            )
                        }
                        items(rankedMostUsed.chunked(8)) { rowEmojis ->
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                rowEmojis.forEach { emoji ->
                                    TextButton(
                                        onClick = { trackAndClick(emoji) },
                                        contentPadding = PaddingValues(4.dp),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Text(emoji, fontSize = 22.sp)
                                    }
                                }
                            }
                        }

                        // Menschen & Hautfarben
                        item {
                            Text(
                                "Menschen",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
                            )
                        }
                        items(PEOPLE_SKIN_EMOJIS.chunked(8)) { rowEmojis ->
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                rowEmojis.forEach { emoji ->
                                    TextButton(
                                        onClick = { trackAndClick(emoji) },
                                        contentPadding = PaddingValues(4.dp),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Text(emoji, fontSize = 22.sp)
                                    }
                                }
                            }
                        }

                        // Weitere Emojis in Reihen à 8
                        item {
                            Text(
                                "Alle",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
                            )
                        }

                        val rows = MORE_EMOJIS.chunked(8)
                        items(rows) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                row.forEach { emoji ->
                                    TextButton(
                                        onClick = { trackAndClick(emoji) },
                                        contentPadding = PaddingValues(4.dp),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Text(emoji, fontSize = 22.sp)
                                    }
                                }
                            }
                        }
                        } // end else (kein Suchtext)
                    } // end LazyColumn
                    } // end Column
                }
                1 -> {
                    // GIF-Inhalt via Giphy
                    if (viewModel != null && onGifSelected != null) {
                        GiphyPickerSheet(
                            viewModel = viewModel,
                            onGifSelected = onGifSelected,
                            modifier = Modifier.fillMaxSize(),
                            onSearchFocusChanged = { focused -> onGifSearchKeyboardActive?.invoke(focused) }
                        )
                    }
                }
                2 -> {
                    // Sticker-Tab
                    if (viewModel != null && onStickerSelected != null) {
                        StickerPickerSheet(
                            viewModel = viewModel,
                            onStickerSelected = onStickerSelected,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                else -> {
                    // Lumis-Inhalt (Tab 3)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        val lumisItems = listOf(
                            Triple(LumisType.LOVE,   "❤️", "Love"),
                            Triple(LumisType.SNOW,   "❄️", "Snow"),
                            Triple(LumisType.KISS,   "💋", "Kiss"),
                            Triple(LumisType.RAIN,   "🌧️", "Rain"),
                            Triple(LumisType.SUMMER, "🌴", "Summer")
                        )
                        items(lumisItems) { (type, emoji, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onLumisClick?.invoke(type) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(emoji, fontSize = 28.sp, modifier = Modifier.padding(end = 16.dp))
                                Text(
                                    label,
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachOption(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(6.dp)
            .aspectRatio(0.9f),
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.10f),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = tint.copy(alpha = 0.20f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                maxLines = 3,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private val locationDurationOptions = listOf(
    "30m" to "30 Min",
    "1h"  to "1 Std",
    "2h"  to "2 Std",
    "4h"  to "4 Std",
    "8h"  to "8 Std"
)

@Composable
private fun AttachLocationSubMenu(
    context: android.content.Context,
    receiverId: String,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onNavigateToLiveMaps: () -> Unit,
    onUploadMapPreview: suspend (Double, Double) -> String? = { _, _ -> null }
) {
    var selectedMode by remember { mutableStateOf<String?>(null) } // null = aktueller Standort, "30m"/... = live
    var locationLoading by remember { mutableStateOf(true) }
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var uploadedPreviewUrl by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun fetchLocation() {
        locationLoading = true
        getCurrentLocationOnce(context) { loc ->
            locationLoading = false
            loc?.let {
                val point = GeoPoint(it.latitude, it.longitude)
                currentLocation = point
                // Vorschaubild im Hintergrund hochladen während der Nutzer noch entscheidet
                coroutineScope.launch {
                    val url = onUploadMapPreview(point.latitude, point.longitude)
                    if (url != null) uploadedPreviewUrl = url
                }
            }
        }
    }

    LaunchedEffect(Unit) { fetchLocation() }

    // Pulsierender Live-Punkt
    val infiniteTransition = rememberInfiniteTransition(label = "locationSubMenuPulse")
    val livePulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "livePulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        // Header mit Zurück-Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
            }
            Text(
                text = "Standort teilen",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        HorizontalDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Aktueller Standort ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { selectedMode = null }
                    .background(
                        if (selectedMode == null)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else Color.Transparent
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(
                    selected = selectedMode == null,
                    onClick = { selectedMode = null },
                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                )
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Aktueller Standort", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("Einmalig deinen genauen Standort teilen", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Kartenvorschau rechts — wird sichtbar sobald Standort ermittelt
                val loc = currentLocation
                if (loc != null) {
                    val previewTileUrl = remember(loc) {
                        osmTileUrl(loc.latitude, loc.longitude, zoom = 14)
                    }
                    val osmLoader = rememberOsmImageLoader(context)
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A2A3A))
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(previewTileUrl)
                                .build(),
                            imageLoader = osmLoader,
                            contentDescription = "Kartenvorschau",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFFE53935),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider()

            // --- Live-Standort Header ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .scale(livePulse)
                            .alpha(1.4f - livePulse)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFFE53935))
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFFE53935))
                    )
                }
                Text("Live-Standort", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            // Dauer-Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                locationDurationOptions.forEach { (key, label) ->
                    FilterChip(
                        selected = selectedMode == key,
                        onClick = { selectedMode = key },
                        label = { Text(label, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            if (selectedMode != null) {
                val durLabel = locationDurationOptions.first { it.first == selectedMode }.second
                Text(
                    "Dein Live-Standort wird für $durLabel geteilt.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Senden-Button
            Button(
                onClick = {
                    val loc = currentLocation
                    if (loc != null) {
                        val previewSuffix = uploadedPreviewUrl?.let { "|preview:$it" } ?: ""
                        val msg = if (selectedMode == null) {
                            "\uD83D\uDCCD https://maps.google.com/?q=${loc.latitude},${loc.longitude}$previewSuffix"
                        } else {
                            "\uD83D\uDCCDlive:${selectedMode} https://maps.google.com/?q=${loc.latitude},${loc.longitude}$previewSuffix"
                        }
                        onSend(msg)
                        // Foreground Service für kontinuierliches Live-Tracking starten
                        if (selectedMode != null) {
                            val durationMs = when (selectedMode) {
                                "30m" -> 30 * 60 * 1000L
                                "1h"  -> 60 * 60 * 1000L
                                "2h"  -> 2 * 60 * 60 * 1000L
                                "4h"  -> 4 * 60 * 60 * 1000L
                                "8h"  -> 8 * 60 * 60 * 1000L
                                else  -> 30 * 60 * 1000L
                            }
                            val svcIntent = Intent(context, com.securechat.app.LiveLocationService::class.java).apply {
                                action = com.securechat.app.LiveLocationService.ACTION_START_OR_RESUME
                                putExtra(com.securechat.app.LiveLocationService.EXTRA_RECEIVER_ID, receiverId)
                                putExtra(com.securechat.app.LiveLocationService.EXTRA_DURATION_MS, durationMs)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(svcIntent)
                            } else {
                                context.startService(svcIntent)
                            }
                        }
                    }
                },
                enabled = currentLocation != null && !locationLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (locationLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Standort wird ermittelt…")
                } else {
                    Text(
                        if (selectedMode == null) "Standort senden" else "Live-Standort senden",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PollCreationDialog(
    onDismiss: () -> Unit,
    onCreatePoll: (String, List<String>, Boolean) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var allowMultipleChoice by remember { mutableStateOf(false) }
    val options = remember { mutableStateListOf("", "", "", "") }

    // Wenn alle aktuellen Felder ausgefüllt sind und noch Platz vorhanden → neues Feld hinzufügen
    val allFilled = options.all { it.isNotBlank() }
    if (allFilled && options.size < 15) {
        options.add("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Poll, contentDescription = null) },
        title = { Text("Umfrage erstellen") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Frage") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                options.forEachIndexed { index, value ->
                    val isOptional = index >= 2
                    OutlinedTextField(
                        value = value,
                        onValueChange = { options[index] = it },
                        label = { Text(if (isOptional) "Option ${index + 1} (optional)" else "Option ${index + 1}") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { allowMultipleChoice = !allowMultipleChoice }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Mehrfachauswahl zulassen")
                    Switch(
                        checked = allowMultipleChoice,
                        onCheckedChange = { allowMultipleChoice = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val opts = options.map { it.trim() }.filter { it.isNotBlank() }
                    if (question.isNotBlank() && opts.size >= 2) {
                        onCreatePoll(question.trim(), opts, allowMultipleChoice)
                    }
                },
                enabled = question.isNotBlank() &&
                        options.getOrElse(0) { "" }.isNotBlank() &&
                        options.getOrElse(1) { "" }.isNotBlank()
            ) { Text("Erstellen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun LocationMessageBubble(lat: Double, lng: Double, isFromMe: Boolean, liveDuration: String? = null, messageTimestamp: Long = 0L, previewImageUrl: String? = null, onTapLive: (() -> Unit)? = null) {
    val context = LocalContext.current
    val mapTileUrl = remember(lat, lng, previewImageUrl) {
        if (previewImageUrl != null) previewImageUrl else osmTileUrl(lat, lng)
    }
    val cornerShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isFromMe) 4.dp else 16.dp,
        bottomEnd = if (isFromMe) 16.dp else 4.dp
    )
    val durationMs = when (liveDuration) {
        "30m" -> 30L * 60_000
        "1h"  -> 60L * 60_000
        "2h"  -> 2L * 60 * 60_000
        "4h"  -> 4L * 60 * 60_000
        "8h"  -> 8L * 60 * 60_000
        else  -> null
    }
    val isExpired = durationMs != null && messageTimestamp > 0L &&
            System.currentTimeMillis() - messageTimestamp > durationMs
    val durationLabel = if (isExpired) null else when (liveDuration) {
        "30m" -> "30 Min"
        "1h"  -> "1 Std"
        "2h"  -> "2 Std"
        "4h"  -> "4 Std"
        "8h"  -> "8 Std"
        else  -> null
    }

    // Pulsierender Punkt für Live-Standort
    val infiniteTransition = rememberInfiniteTransition(label = "liveBubblePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            tween(800), RepeatMode.Reverse
        ),
        label = "pulseBubbleScale"
    )

    Box(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 260.dp)
            .height(160.dp)
            .clip(cornerShape)
    ) {
        // OSM-Tile direkt laden (tile.openstreetmap.org) – kein API-Key, kein Google Maps SDK,
        // kleines 256x256 Bild → geringer Speicherverbrauch in LazyColumn.
        val osmLoader = rememberOsmImageLoader(context)
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A2A3A)))
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(mapTileUrl)
                .build(),
            imageLoader = osmLoader,
            contentDescription = "Standort",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Location-Pin als Compose-Overlay in der Bildmitte
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = Color(0xFFE53935),
            modifier = Modifier
                .align(Alignment.Center)
                .size(36.dp)
                .offset(y = (-6).dp)
        )
        // Live-Badge oben links
        if (durationLabel != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color(0xCCE53935), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .scale(pulseScale)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.White)
                )
                Text("LIVE · $durationLabel", color = Color.White, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
        // Label Overlay unten
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("In Maps öffnen", color = Color.White, fontSize = 11.sp)
            }
        }
        // Transparenter Touch-Interceptor (liegt über allem, fängt Klicks ab)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    if (liveDuration != null && onTapLive != null) {
                        try {
                            onTapLive()
                        } catch (e: Exception) {
                            android.util.Log.w("LocationBubble", "onTapLive Fehler: ${e.message}")
                        }
                    } else {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$lat,$lng"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.util.Log.w("LocationBubble", "Maps-Intent fehlgeschlagen: ${e.message}")
                            // Fallback: Browser-URL
                            try {
                                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/?mlat=$lat&mlon=$lng&zoom=15"))
                                context.startActivity(fallbackIntent)
                            } catch (_: Exception) {}
                        }
                    }
                }
        )
    }
}

/**
 * Zeigt eine Vorschau-Karte für einen Google-Maps-Link an.
 * Wenn Koordinaten extrahiert werden können → echte Karte mit Pin.
 * Sonst → Fallback-Card mit Link.
 */
@Composable
private fun MapsLinkCard(url: String, isFromMe: Boolean = true) {
    val context = LocalContext.current
    val coords = remember(url) { extractCoordsFromMapsUrl(url) }

    if (coords != null) {
        // Echte Karte mit Pin
        LocationMessageBubble(lat = coords.first, lng = coords.second, isFromMe = isFromMe)
    } else {
        // Kurz-Link ohne extrahierbare Koordinaten → Fallback
        Box(
            modifier = Modifier
                .widthIn(min = 200.dp, max = 260.dp)
                .height(100.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A2A3A))
                .clickable {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: Exception) {}
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFF4DB6AC),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Google Maps",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "In Karte öffnen",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Zeigt eine Google-Maps-Karte für eine erkannte Adresse an.
 * Geocodiert die Adresse und zeigt bei Erfolg eine interaktive Karte;
 * bei Fehler einen Fallback-Card mit "In Maps öffnen"-Link.
 */
@Composable
private fun AddressMessageCard(address: String) {
    val context = LocalContext.current
    var geocodedLatLng by remember(address) { mutableStateOf<GeoPoint?>(null) }
    var geocodeAttempted by remember(address) { mutableStateOf(false) }

    LaunchedEffect(address) {
        withContext(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                val results = Geocoder(context, Locale.getDefault()).getFromLocationName(address, 1)
                if (!results.isNullOrEmpty()) {
                    geocodedLatLng = GeoPoint(results[0].latitude, results[0].longitude)
                }
            } catch (_: Exception) { /* Geocoding nicht verfügbar */ }
            // Fallback: OpenStreetMap Nominatim, wenn der Android-Geocoder nichts lieferte.
            // Liefert Koordinaten → echte OSM-Karten-Vorschau statt schmuckloser Fallback-Card.
            if (geocodedLatLng == null) {
                try {
                    val url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + Uri.encode(address)
                    val client = OkHttpClient()
                    val req = okhttp3.Request.Builder()
                        .url(url)
                        .header("User-Agent", "LetheApp/Android (contact@letheapp.de)")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val arr = org.json.JSONArray(body)
                            if (arr.length() > 0) {
                                val obj = arr.getJSONObject(0)
                                val lat = obj.optString("lat").toDoubleOrNull()
                                val lon = obj.optString("lon").toDoubleOrNull()
                                if (lat != null && lon != null) geocodedLatLng = GeoPoint(lat, lon)
                            }
                        }
                    }
                } catch (_: Exception) { /* Nominatim nicht erreichbar */ }
            }
            geocodeAttempted = true
        }
    }

    val currentLatLng = geocodedLatLng
    val cornerShape = RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .height(150.dp)
            .clip(cornerShape)
    ) {
        when {
            currentLatLng != null -> {
                val mapTileUrl = remember(currentLatLng) {
                    osmTileUrl(currentLatLng.latitude, currentLatLng.longitude)
                }
                val osmLoader = rememberOsmImageLoader(context)
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A2A3A)))
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(mapTileUrl)
                        .build(),
                    imageLoader = osmLoader,
                    contentDescription = "Standort",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFFE53935),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                        .offset(y = (-6).dp)
                )
                // Adress-Label unten
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = address,
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                    }
                }
            }
            !geocodeAttempted -> {
                // Lade-Zustand
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF1A2A3A)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                }
            }
            else -> {
                // Geocoding fehlgeschlagen oder Map-Fehler → Fallback
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF1A2A3A)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(address, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text("In Maps öffnen →", color = Color(0xFF4FC3F7), fontSize = 11.sp)
                    }
                }
            }
        }
        // Transparenter Touch-Interceptor – verhindert dass GoogleMap Touch-Events konsumiert
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    val encoded = Uri.encode(address)
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$encoded")))
                    } catch (_: Exception) {}
                }
        )
    }
}

/** Einheitliches Weiterleiten-Ziel (Kontakt oder Gruppe) für Sortierung/Anzeige im ForwardSheet. */
private data class ForwardTarget(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val isGroup: Boolean
)

@Composable
private fun ForwardSectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ForwardTargetRow(target: ForwardTarget, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(target.name) },
        leadingContent = {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (target.imageUrl != null) {
                    AsyncImage(
                        model = target.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        if (target.isGroup) Icons.Default.Group else Icons.Default.Person,
                        null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardSheet(
    contacts: List<com.securechat.app.data.local.ContactEntity>,
    groups: List<com.securechat.app.data.local.GroupEntity>,
    frequencyOrder: List<String> = emptyList(),
    pinnedContactIds: Set<String> = emptySet(),
    pinnedGroupIds: Set<String> = emptySet(),
    onForwardTo: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val orderIndex = remember(frequencyOrder) {
        frequencyOrder.withIndex().associate { (i, id) -> id to i }
    }
    val sortedTargets = remember(contacts, groups, orderIndex) {
        val all = contacts.map { ForwardTarget(it.userId, it.username ?: it.fakeNumber, it.profileImageUrl, false) } +
            groups.map { ForwardTarget(it.groupId, it.name, it.groupImageUrl, true) }
        all.sortedBy { orderIndex[it.id] ?: Int.MAX_VALUE }
    }
    val pinnedTargets = sortedTargets.filter {
        if (it.isGroup) it.id in pinnedGroupIds else it.id in pinnedContactIds
    }
    val unpinnedTargets = sortedTargets - pinnedTargets.toSet()
    val unpinnedContacts = unpinnedTargets.filter { !it.isGroup }
    val unpinnedGroups = unpinnedTargets.filter { it.isGroup }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.85f)) {
            Text(
                "Weiterleiten an...",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            ListItem(
                headlineContent = { Text("Status") },
                leadingContent = { Icon(Icons.Default.AutoStories, contentDescription = null) },
                modifier = Modifier.clickable { /* Status-Weiterleitung: TODO */ onDismiss() }
            )
            HorizontalDivider()
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (pinnedTargets.isNotEmpty()) {
                    item { ForwardSectionHeader("Angepinnt") }
                    items(pinnedTargets, key = { "pinned_${it.id}" }) { t ->
                        ForwardTargetRow(t) { onForwardTo(t.id); onDismiss() }
                    }
                }
                if (unpinnedContacts.isNotEmpty()) {
                    item { ForwardSectionHeader("Kontakte") }
                    items(unpinnedContacts, key = { "contact_${it.id}" }) { t ->
                        ForwardTargetRow(t) { onForwardTo(t.id); onDismiss() }
                    }
                }
                if (unpinnedGroups.isNotEmpty()) {
                    item { ForwardSectionHeader("Gruppen") }
                    items(unpinnedGroups, key = { "group_${it.id}" }) { t ->
                        ForwardTargetRow(t) { onForwardTo(t.id); onDismiss() }
                    }
                }
            }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = false,
                    onClick = onDismiss,
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                    label = { Text("Chats") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onDismiss,
                    icon = { Icon(Icons.Default.AutoStories, contentDescription = null) },
                    label = { Text("Status") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onDismiss,
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                    label = { Text("Dating") }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageBubble(
    message: MessageEntity,
    partnerId: String,
    bubbleColor: Color,
    partnerBubbleColor: Color = Color.White,
    bubbleColor2: Color = bubbleColor,
    partnerBubbleColor2: Color = partnerBubbleColor,
    precomputedItem: ChatListItem.Message? = null,
    onReplyJump: (() -> Unit)? = null,
    partnerName: String? = null,
    partnerAvatarUrl: String? = null,
    viewModel: MainViewModel? = null,
    reaction: String? = null,
    onReaction: (String) -> Unit = {},
    showEmojiPicker: Boolean = false,
    onHideEmojiPicker: () -> Unit = {},
    showFullEmojiPicker: Boolean = false,
    onShowFullEmojiPicker: () -> Unit = {},
    onHideFullEmojiPicker: () -> Unit = {},
    onLongClick: () -> Unit = {},
    isSelectionMode: Boolean = false,
    onOpenDocument: ((url: String, fileName: String) -> Unit)? = null,
    onNavigateTo3DViewer: ((fileUrl: String, filename: String, textureUrl: String) -> Unit)? = null,
    onNavigateToContent: ((contentId: String) -> Unit)? = null,
    onNavigateToSpark: ((sparkId: String) -> Unit)? = null,
    onNavigateToCoins: (() -> Unit)? = null,
    chatId: String = "",
    onNavigateToLiveMaps: ((chatId: String) -> Unit)? = null,
    onNavigateToGames: ((partnerId: String, partnerName: String) -> Unit)? = null,
    fontSizeMultiplier: Float = 1.0f,
    groupSenderName: String? = null,
    groupSenderIsVerified: Boolean = false,
    isGroup: Boolean = false,
    nextAudioUrl: String? = null,
    prevMusicUrl: String? = null,
    nextMusicUrl: String? = null,
    allChatMusicUrls: List<String> = emptyList(),
    activeLoadingMediaUrl: String? = null,
    isMediaApproved: (String) -> Boolean = { true },
    onMediaLoaded: (String) -> Unit = {},
    onForceLoadMedia: (String) -> Unit = {},
    onStartVoiceRecording: (() -> Unit)? = null,
    onCallBack: ((isVideo: Boolean) -> Unit)? = null,
    onDetachMusicPlayer: () -> Unit = {}
) {
    // Spiel-Ergebnis-Karte: immer zentriert, kein normales Bubble
    if (message.mediaType == "game_result") {
        val json = try { org.json.JSONObject(message.content ?: "{}") } catch (_: Exception) { null }
        if (json != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                GameResultCard(json = json, onPlayAgain = onNavigateToGames)
            }
        }
        return
    }

    // Lethe-Systemnachricht (z.B. /ranking oder /würfeln): zentriert, hellrosa
    if (message.senderId == "lethe_system") {
        val sysContent = message.content?.lowercase() ?: ""
        val sysIcon = when {
            "videoanruf" in sysContent -> Icons.Default.Videocam
            "anruf" in sysContent -> Icons.Default.Call
            "listen together" in sysContent -> Icons.Default.MusicNote
            else -> Icons.Default.Info
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp, horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFFCDD2),
                tonalElevation = 2.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = sysIcon,
                        contentDescription = null,
                        tint = Color(0xFF880E4F),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = message.content ?: "",
                        color = Color(0xFF880E4F),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        return
    }

    val scope = rememberCoroutineScope()

    // Eigenes Profilbild + userId – MUSS vor isFromMe stehen
    val myUser by remember(viewModel) {
        viewModel?.currentUser ?: kotlinx.coroutines.flow.MutableStateFlow<com.securechat.app.data.local.UserEntity?>(null)
    }.collectAsState()

    // isFromMe: Im 1:1-Chat reicht senderId != partnerId.
    // Im Gruppen-Chat ist partnerId die groupId (keine userId) → alle hätten isFromMe=true.
    // Daher: Falls myUserId bekannt, darüber bestimmen; sonst Fallback auf senderId != partnerId.
    val isFromMe = myUser?.userId?.let { message.senderId == it } ?: (message.senderId != partnerId)

    // Eigene Nachrichten IMMER links, Partner-Nachrichten IMMER rechts
    val alignment = if (isFromMe) Alignment.Start else Alignment.End

    // Upload-Fortschritt NUR für DIESE Nachricht beobachten (statt der ganzen Map): so recomposed
    // eine Bubble nicht mehr, wenn der Upload-Fortschritt einer ANDEREN Nachricht sich ändert.
    val myClientId = message.clientMessageId ?: ""
    // Video-Upload-Fortschritt: 0f..100f (Upload) | 101f (Transkodierung)
    val myVideoProgress by remember(viewModel, myClientId) {
        (viewModel?.videoUploadProgress ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, Float>()))
            .map { m -> m[myClientId] }
            .distinctUntilChanged()
    }.collectAsState(initial = null)
    // Bild-Upload-Fortschritt: 0f..100f (Upload) | -2f (fehlgeschlagen)
    val myImageProgress by remember(viewModel, myClientId) {
        (viewModel?.imageUploadProgress ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, Float>()))
            .map { m -> m[myClientId] }
            .distinctUntilChanged()
    }.collectAsState(initial = null)
    // Fehlgeschlagene Uploads für Wiederholen
    val failedUploadsMap by remember(viewModel) {
        viewModel?.failedUploads ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, com.securechat.app.ui.MainViewModel.PendingUpload>())
    }.collectAsState()
    val myAvatarUrl = myUser?.profileImageUrl?.let { url ->
        if (url.startsWith("http")) url else "https://letheapp.de$url"
    }

    val ownBubbleColor = bubbleColor
    val effectivePartnerBubbleColor = partnerBubbleColor
    val bgColor = if (isFromMe) ownBubbleColor else effectivePartnerBubbleColor
    val bgColor2 = if (isFromMe) bubbleColor2 else partnerBubbleColor2
    val isGlossyMorph = LocalAppTheme.current == AppTheme.GLOSSY_MORPH

    // Textfarbe basierend auf Blase-Luminanz (automatisch Schwarz/Weiß)
    val textColor = contrastColor(bgColor)
    val metaColor = textColor.copy(alpha = 0.65f)

    val timeText = precomputedItem?.formattedTime ?: remember(message.timestamp) {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        sdf.format(java.util.Date(message.timestamp))
    }

    // Anruf-Log-Bubble ("Keine Antwort") – auf der Seite des Anruf-Auslösers.
    // Anrufer (isFromMe) → "Sprachnachricht aufnehmen"; Empfänger → "Zurückrufen".
    if (message.mediaType == "call") {
        val callJson = remember(message.content) {
            try { org.json.JSONObject(message.content ?: "{}") } catch (_: Exception) { org.json.JSONObject() }
        }
        val isVideoCall = callJson.optString("type") == "video"
        val callLabel = if (isVideoCall) "Videoanruf" else "Sprachanruf"
        val callIcon = if (isVideoCall) Icons.Default.Videocam else Icons.Default.Call
        val actionText = if (isFromMe) "Sprachnachricht aufnehmen" else "Zurückrufen"
        val accentGreen = Color(0xFF25D366)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalAlignment = alignment
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = bgColor,
                tonalElevation = 2.dp,
                modifier = Modifier.widthIn(min = 240.dp, max = 320.dp)
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(textColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = callIcon,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = callLabel,
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Keine Antwort",
                                color = metaColor,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = timeText,
                            color = metaColor,
                            fontSize = 12.sp
                        )
                    }
                    HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (isFromMe) onStartVoiceRecording?.invoke()
                                else onCallBack?.invoke(isVideoCall)
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = actionText,
                            color = accentGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
        return
    }

    val emojiDensity = LocalDensity.current
    // Ab welcher Blasen-Oberkante (Fenster-Koordinaten) ist oberhalb kein Platz mehr für die
    // Schnell-Leiste? Berücksichtigt die opake Top-App-Bar + Statusleiste (sonst verschwindet die
    // Leiste dahinter), die App-Bar-Höhe (56dp) und die Höhe der Leiste selbst (~64dp).
    val emojiBarTopInsetPx = WindowInsets.systemBars.getTop(emojiDensity)
    val emojiBarThresholdPx = remember(emojiDensity, emojiBarTopInsetPx) {
        emojiBarTopInsetPx + with(emojiDensity) { (56.dp + 64.dp).toPx() }
    }
    var bubbleTopYPx by remember { mutableStateOf(Float.MAX_VALUE) }
    // Emoji-Schnellleiste als Lambda, um sie oben oder unten einzubetten ohne Code-Duplizierung
    val emojiQuickBar: @Composable () -> Unit = {
        Surface(
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 8.dp,
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 6.dp, top = 6.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val ctx = LocalContext.current
                    val rankedReactions = remember { EmojiUsageTracker.getRankedEmojis(ctx, MOST_USED_EMOJIS) }
                    rankedReactions.take(15).forEach { emoji ->
                        TextButton(
                            onClick = {
                                EmojiUsageTracker.recordUsage(ctx, emoji)
                                onReaction(emoji)
                                onHideEmojiPicker()
                            },
                            contentPadding = PaddingValues(4.dp),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Text(emoji, fontSize = 24.sp)
                        }
                    }
                }
                TextButton(
                    onClick = onShowFullEmojiPicker,
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Text(
                        "+",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .onGloballyPositioned { coords -> bubbleTopYPx = coords.boundsInWindow().top },
        horizontalAlignment = alignment
    ) {
        val showEmojiBelow = showEmojiPicker && bubbleTopYPx < emojiBarThresholdPx
        // Vollständiges Emoji-Raster (erscheint wenn + gedrückt wurde, unabhängig vom Auswahl-Modus)
        if (showFullEmojiPicker) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 8.dp,
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                val allEmojis = remember { (MOST_USED_EMOJIS + MORE_EMOJIS).distinct() }
                Column(modifier = Modifier.padding(4.dp)) {
                    // Kopfzeile mit Schließen-Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onHideFullEmojiPicker,
                            contentPadding = PaddingValues(4.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("✕", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        allEmojis.chunked(8).forEach { rowEmojis ->
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                rowEmojis.forEach { emoji ->
                                    TextButton(
                                        onClick = {
                                            onReaction(emoji)
                                            onHideFullEmojiPicker()
                                        },
                                        contentPadding = PaddingValues(2.dp),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Text(emoji, fontSize = 20.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Schnell-Picker: oberhalb der Blase (nur wenn Blase nicht am oberen Rand)
        if (showEmojiPicker && !showEmojiBelow) {
            emojiQuickBar()
        }

        // Blase + Reaction-Badge als Column (max. 85% der Bildschirmbreite)
        // Column statt Box: Badge hängt unterhalb der Blase statt darüber
        val baseMaxBubbleWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp * 0.815f
        val maxBubbleWidth = when {
            isGroup && isFromMe -> baseMaxBubbleWidth - 44.dp  // eigene Blase: um Avatar-Bereich der Partner-Seite kürzen
            isGroup && !isFromMe -> baseMaxBubbleWidth - 20.dp // Partner-Blase: etwas kürzer
            else -> baseMaxBubbleWidth
        }
        val bubbleDensity = LocalDensity.current
        val isGifOrSticker = message.mediaType == "gif" || message.mediaType == "sticker" || message.mediaType == "circle_video"
        Column(modifier = Modifier.widthIn(max = maxBubbleWidth)) {
        CompositionLocalProvider(
            LocalDensity provides Density(bubbleDensity.density, bubbleDensity.fontScale * fontSizeMultiplier)
        ) {
        val isGroupPartnerMsg = !isFromMe && groupSenderName != null
        val bubbleShape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = if (isGroupPartnerMsg) 4.dp else 16.dp,
            bottomStart = if (isFromMe) 4.dp else 16.dp,
            bottomEnd = if (isFromMe || isGroupPartnerMsg) 16.dp else 4.dp
        )
        Surface(
            shape = bubbleShape,
            color = if (isGifOrSticker || isGlossyMorph) Color.Transparent else bgColor,
            shadowElevation = if (isGifOrSticker) 0.dp else 1.dp
        ) {
            Box {
                // Glossy-Morph: Verlaufshintergrund + Glanz-Schimmer
                if (isGlossyMorph && !isGifOrSticker) {
                    Box(modifier = Modifier.matchParentSize().background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(bgColor, bgColor2)
                        )
                    ))
                    // Glanz-Schimmer oben
                    Box(modifier = Modifier.matchParentSize().background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f),
                                androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                    ))
                }
            Column {
                // Absender-Name im Gruppenchat (nur für fremde Nachrichten)
                if (groupSenderName != null) {
                    // Luminanz des Blasenhintergrunds prüfen: bei hellem Hintergrund
                    // dunkle Akzentfarbe verwenden damit der Name lesbar bleibt.
                    val bgLuminance = run {
                        val r = bgColor.red.toDouble()
                        val g = bgColor.green.toDouble()
                        val b = bgColor.blue.toDouble()
                        // Relative Luminanz nach WCAG (linearisiert)
                        fun linearize(c: Double) = if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
                        0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)
                    }
                    val senderNameColor = if (bgLuminance > 0.35) {
                        // Heller Hintergrund → dunkle Akzentfarbe (onPrimary-Container oder onSurface-Variante)
                        MaterialTheme.colorScheme.primary.copy(
                            red = MaterialTheme.colorScheme.primary.red * 0.55f,
                            green = MaterialTheme.colorScheme.primary.green * 0.55f,
                            blue = MaterialTheme.colorScheme.primary.blue * 0.55f,
                            alpha = 1f
                        )
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 0.dp)
                    ) {
                        Text(
                            text = groupSenderName,
                            color = senderNameColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (groupSenderIsVerified) {
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = "Verifiziert",
                                tint = Color(0xFF1DA1F2),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
                // Quote-Block (Antwort-Vorschau)
                if (message.replyToContent != null) {
                    // Im Gruppenchat vorab aufgelösten Absendernamen nutzen (partnerId ist dort die
                    // Gruppen-ID, nie der Absender → sonst stünde fälschlich immer "Du").
                    val replyerName = precomputedItem?.replyToSenderName
                        ?: if (message.replyToSenderId == partnerId) partnerName ?: partnerId else "Du"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable {
                                // Ziel-Nachricht-Sprung: Suche + Scroll laufen im Parent (Zugriff auf
                                // chatItems/listState), damit diese instabilen Listen NICHT als Bubble-
                                // Parameter durchgereicht werden müssen (sonst recomposed jede neue
                                // Nachricht ALLE Bubbles).
                                onReplyJump?.invoke()
                            }
                            .padding(6.dp)
                    ) {
                        // Effektiven Medientyp ermitteln: gespeicherter Wert oder URL-Heuristik (für ältere Nachrichten)
                        val replyMediaType = message.replyToMediaType
                            ?: when {
                                message.replyToContent?.let { url ->
                                    val l = url.lowercase()
                                    l.endsWith(".jpg") || l.endsWith(".jpeg") ||
                                    l.endsWith(".png") || l.endsWith(".webp") ||
                                    l.endsWith(".gif")
                                } == true -> "image"
                                message.replyToContent?.let { url ->
                                    val l = url.lowercase()
                                    l.endsWith(".mp4") || l.endsWith(".mov") || l.endsWith(".webm")
                                } == true -> "video"
                                else -> null
                            }
                        Column {
                            Text(replyerName, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 2.dp))
                            when (replyMediaType) {
                                "image", "video", "status" -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(
                                            model = message.replyToContent,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            when (replyMediaType) {
                                                "status" -> "Status"
                                                "video" -> "📹 Video"
                                                else -> "🖼 Foto"
                                            },
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                "audio", "audio_e2ee" -> {
                                    // Mini-Audio-Player im Zitat
                                    val replyAudioUrl = message.replyToContent ?: ""
                                    if (replyAudioUrl.isNotBlank() && viewModel != null) {
                                        val quotedPlaybackUrl by produceState(
                                            initialValue = if (replyMediaType == "audio_e2ee") "" else replyAudioUrl,
                                            replyAudioUrl, replyMediaType
                                        ) {
                                            value = viewModel.resolveAudioPlaybackSource(
                                                mediaUrl = replyAudioUrl,
                                                mediaType = replyMediaType ?: "audio",
                                                chatId = chatId,
                                                isGroup = isGroup,
                                                senderId = message.replyToSenderId ?: ""
                                            )
                                        }
                                        if (replyMediaType == "audio_e2ee" && quotedPlaybackUrl.isBlank()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text("Entschlüssele…", fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            }
                                        } else {
                                            QuotedAudioPlayer(
                                                url = quotedPlaybackUrl,
                                                viewModel = viewModel,
                                                accentColor = MaterialTheme.colorScheme.primary,
                                                metaColor = MaterialTheme.colorScheme.onSurface,
                                                isSelectionMode = isSelectionMode
                                            )
                                        }
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Mic, contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Sprachnachricht", fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        }
                                    }
                                }
                                "poll" -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Poll, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("📊 Umfrage", fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                }
                                "game_result" -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.SportsEsports, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("🎮 Spielergebnis", fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                }
                                "link" -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        val linkTitle = remember(message.replyToContent) {
                                            try {
                                                val j = org.json.JSONObject(message.replyToContent ?: "{}")
                                                j.optString("title", j.optString("url", "Link")).ifBlank { "Link" }
                                            } catch (_: Exception) { message.replyToContent ?: "Link" }
                                        }
                                        Text(
                                            linkTitle,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                else -> {
                                    Text(message.replyToContent ?: "", maxLines = 2,
                                        overflow = TextOverflow.Ellipsis, fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // Wird auf false gesetzt wenn der Zeitstempel inline gerendert wird (plain text)
                var showExternalTimestamp = true
                when (message.mediaType) {
                    "gif" -> {
                        showExternalTimestamp = false
                        val gifUrl = message.mediaUrl
                        if (gifUrl != null) {
                            var showGifInfo by remember { mutableStateOf(false) }
                            val gifCtx = LocalContext.current
                            AsyncImage(
                                model = ImageRequest.Builder(gifCtx)
                                    .data(gifUrl)
                                    .size(520, 520)
                                    .build(),
                                contentDescription = "GIF",
                                modifier = Modifier
                                    .widthIn(min = 120.dp, max = 260.dp)
                                    .heightIn(max = 260.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        onClick = { if (!isSelectionMode) showGifInfo = true },
                                        onLongClick = { onLongClick() }
                                    ),
                                contentScale = ContentScale.Crop
                            )
                            if (showGifInfo) {
                                GifStickerInfoDialog(
                                    mediaUrl = gifUrl,
                                    mediaType = "gif",
                                    viewModel = viewModel,
                                    onDismiss = { showGifInfo = false }
                                )
                            }
                        }
                    }
                    "sticker" -> {
                        showExternalTimestamp = false
                        val stickerUrl = message.mediaUrl
                        if (stickerUrl != null) {
                            var showStickerInfo by remember { mutableStateOf(false) }
                            // Seitenverhältnis aus dem geladenen Sticker übernehmen (unterstützt Quer- und Hochformate)
                            var stickerAspect by remember(stickerUrl) { mutableStateOf(1f) }
                            AsyncImage(
                                model = stickerUrl,
                                contentDescription = "Sticker",
                                onSuccess = { st ->
                                    val d = st.result.drawable
                                    if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0)
                                        stickerAspect = d.intrinsicWidth.toFloat() / d.intrinsicHeight.toFloat()
                                },
                                modifier = Modifier
                                    .then(if (stickerAspect >= 1f) Modifier.width(160.dp) else Modifier.height(160.dp))
                                    .aspectRatio(stickerAspect)
                                    .combinedClickable(
                                        onClick = { if (!isSelectionMode) showStickerInfo = true },
                                        onLongClick = { onLongClick() }
                                    ),
                                contentScale = ContentScale.Fit
                            )
                            if (showStickerInfo) {
                                GifStickerInfoDialog(
                                    mediaUrl = stickerUrl,
                                    mediaType = "sticker",
                                    viewModel = viewModel,
                                    onDismiss = { showStickerInfo = false }
                                )
                            }
                        }
                    }
                    "image" -> {
                        val imageUrl = message.mediaUrl
                        val imgUploadProgress = myImageProgress
                        val imgFailed = imgUploadProgress == -2f
                        if (imageUrl == null || imgUploadProgress != null) {
                            // Upload läuft oder fehlgeschlagen → schwarzer Platzhalter mit Fortschritt
                            val clipShape = RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp,
                                bottomStart = if (isFromMe) 4.dp else 16.dp,
                                bottomEnd = if (isFromMe) 16.dp else 4.dp
                            )
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 160.dp, max = 260.dp)
                                    .height(180.dp)
                                    .clip(clipShape)
                                    .background(Color(0xFF1C1C1C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (imgFailed) {
                                        // Fehlgeschlagen → "Wiederholen" anzeigen
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Upload fehlgeschlagen",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Wiederholen",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 14.sp,
                                            modifier = Modifier.clickable {
                                                message.clientMessageId?.let { viewModel?.retryUpload(it) }
                                            }
                                        )
                                    } else {
                                        // Upload läuft → Fortschritt anzeigen
                                        val pct = (imgUploadProgress ?: 0f).coerceIn(0f, 100f)
                                        Box(contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(
                                                progress = { pct / 100f },
                                                modifier = Modifier.size(56.dp),
                                                color = Color.White,
                                                trackColor = Color.Gray.copy(alpha = 0.3f),
                                                strokeWidth = 4.dp
                                            )
                                            Text(
                                                text = "${pct.toInt()}%",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Abbrechen",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 13.sp,
                                            modifier = Modifier.clickable {
                                                message.clientMessageId?.let { viewModel?.cancelUpload(it) }
                                            }
                                        )
                                    }
                                }
                            }
                        } else if (imageUrl != null) {
                            // Sequenzielles Laden: nur das aktuell an der Reihe befindliche Bild
                            // (activeLoadingMediaUrl) bzw. bereits fertig geladene/erzwungene Bilder
                            // werden dekodiert. Bereits lokal vorhandene Bilder (öffentlicher Galerie-
                            // Speicher) werden unabhängig von der Reihenfolge sofort angezeigt, da sie
                            // keine Netzwerk-/Dekodier-Warteschlange belasten.
                            val locallyAvailable = viewModel?.getPublicMediaUri(imageUrl, false) != null
                            val imageApproved = locallyAvailable || isMediaApproved(imageUrl)
                            val imageShape = RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp,
                                bottomStart = if (isFromMe) 4.dp else 16.dp,
                                bottomEnd = if (isFromMe) 16.dp else 4.dp
                            )
                            if (!imageApproved) {
                                // Platzhalter: wartet auf sequenzielles Laden. Per Tap kann sofort
                                // geladen werden, ohne auf die Reihenfolge zu warten.
                                Box(
                                    modifier = Modifier
                                        .widthIn(min = 120.dp, max = 260.dp)
                                        .heightIn(min = 120.dp, max = 200.dp)
                                        .clip(imageShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .combinedClickable(
                                            onClick = { if (!isSelectionMode) onForceLoadMedia(imageUrl) },
                                            onLongClick = { onLongClick() }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            } else {
                            var showFullscreen by remember { mutableStateOf(false) }
                            var showImageEditor by remember { mutableStateOf(false) }
                            var showMediaInfo by remember { mutableStateOf(false) }
                            // Verhindert, dass ein durch gedrückt-Halten ausgelöstes Markieren
                            // zusätzlich noch als Einzel-Tipp (Bild öffnen) erkannt wird.
                            var suppressNextImageClick by remember { mutableStateOf(false) }
                            val thumbnailCtx = LocalContext.current
                            // Falls das Bild bereits in den Pictures-Ordner verschoben wurde,
                            // lokal von dort laden (kein Datenvolumen), sonst von der URL.
                            val imageModel = viewModel?.getPublicMediaUri(imageUrl, false) ?: imageUrl
                            val imagePainter = rememberAsyncImagePainter(
                                ImageRequest.Builder(thumbnailCtx)
                                    .data(imageModel)
                                    .size(640, 640)
                                    .build()
                            )
                            val imagePainterState = imagePainter.state
                            val isActiveLoading = imageUrl == activeLoadingMediaUrl
                            // Fertig (Erfolg ODER Fehler) → als geladen melden, damit die Warteschlange
                            // weiterrückt (ein fehlgeschlagenes Bild darf die Sequenz nicht blockieren).
                            LaunchedEffect(imagePainterState, imageUrl) {
                                if (imagePainterState is AsyncImagePainter.State.Success ||
                                    imagePainterState is AsyncImagePainter.State.Error) {
                                    onMediaLoaded(imageUrl)
                                }
                            }
                            // Beim ersten Betrachten in den öffentlichen Pictures-Ordner verschieben.
                            LaunchedEffect(imagePainterState) {
                                if (imagePainterState is AsyncImagePainter.State.Success) {
                                    viewModel?.exportImageToPictures(imageUrl, message.chatId)
                                }
                            }
                            // Animierter Fortschritt 0 → 90 % beim Laden, Sprung auf 100 % wenn fertig
                            var progressTarget by remember { mutableFloatStateOf(0f) }
                            val animatedProgress by animateFloatAsState(
                                targetValue = progressTarget,
                                animationSpec = tween(durationMillis = 1800),
                                label = "imgLoadProgress"
                            )
                            LaunchedEffect(isActiveLoading, imagePainterState) {
                                when {
                                    isActiveLoading && imagePainterState is AsyncImagePainter.State.Loading -> progressTarget = 0.9f
                                    isActiveLoading && imagePainterState is AsyncImagePainter.State.Success -> progressTarget = 1f
                                }
                            }
                            // Seitenverhältnis aus geladener Painter-Größe ableiten (Landscape-Fix für 21:9-Fotos)
                            val intrinsicSize = (imagePainterState as? AsyncImagePainter.State.Success)?.painter?.intrinsicSize
                            val imageBoxModifier = if (intrinsicSize != null && intrinsicSize.width > 0 && intrinsicSize.height > 0) {
                                val imgW = intrinsicSize.width
                                val imgH = intrinsicSize.height
                                val maxW = 260f
                                val maxH = 300f
                                val scale = minOf(maxW / imgW, maxH / imgH)
                                val dispW = (imgW * scale).coerceAtLeast(100f)
                                val dispH = (imgH * scale).coerceAtLeast(80f)
                                Modifier.width(dispW.dp).height(dispH.dp)
                            } else {
                                Modifier.widthIn(min = 120.dp, max = 260.dp).heightIn(min = 120.dp, max = 300.dp)
                            }
                            val displayContentScale = if (intrinsicSize != null) ContentScale.Fit else ContentScale.Crop
                            Box(
                                modifier = imageBoxModifier
                                    .clip(imageShape)
                                    .combinedClickable(
                                        onClick = {
                                            if (suppressNextImageClick) {
                                                suppressNextImageClick = false
                                            } else if (!isSelectionMode) {
                                                showFullscreen = true
                                            }
                                        },
                                        onLongClick = {
                                            suppressNextImageClick = true
                                            onLongClick()
                                        }
                                    )
                            ) {
                                Image(
                                    painter = imagePainter,
                                    contentDescription = null,
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = displayContentScale
                                )
                                // Ladekreis nur für das aktuell aktive Bild
                                if (isActiveLoading && imagePainterState is AsyncImagePainter.State.Loading) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(Color.Black.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(
                                                progress = { animatedProgress },
                                                modifier = Modifier.size(60.dp),
                                                color = Color.White,
                                                strokeWidth = 4.dp
                                            )
                                            Text(
                                                text = "${(animatedProgress * 100).toInt()}%",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }
                            }
                            // Aufruf tracken wenn Bild im Gruppen-Chat geöffnet wird
                            if (showFullscreen && isGroup && !isFromMe) {
                                val msgId = message.messageId
                                LaunchedEffect(showFullscreen) {
                                    if (msgId != null) viewModel!!.recordGroupMediaView(chatId, msgId)
                                }
                            }

                            if (showFullscreen) {
                                Dialog(
                                    onDismissRequest = { showFullscreen = false },
                                    properties = DialogProperties(
                                        usePlatformDefaultWidth = false,
                                        dismissOnClickOutside = false
                                    )
                                ) {
                                    var zoomScale by remember { mutableStateOf(1f) }
                                    var zoomOffset by remember { mutableStateOf(Offset.Zero) }
                                    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
                                        val newScale = (zoomScale * zoomChange).coerceIn(1f, 6f)
                                        zoomScale = newScale
                                        zoomOffset = if (newScale > 1f) zoomOffset + offsetChange * 2.5f else Offset.Zero
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val fullResCtx = LocalContext.current
                                        Image(
                                            painter = rememberAsyncImagePainter(
                                                ImageRequest.Builder(fullResCtx)
                                                    .data(viewModel?.getPublicMediaUri(imageUrl, false) ?: imageUrl)
                                                    // Max. 2048px statt ORIGINAL: verhindert das
                                                    // Dekodieren eines 12-MP-Fotos (~48 MB) im Vollbild → OOM.
                                                    .size(2048, 2048)
                                                    // Scale.FIT (statt Coil-Default FILL): bei einem hohen
                                                    // Bild (z.B. langer Screenshot) skaliert FILL auf die
                                                    // Breite → resultierende Bitmap-Höhe überschreitet
                                                    // GL_MAX_TEXTURE_SIZE → nur der obere Teil wird als
                                                    // Textur gezeichnet, Rest schwarz. FIT cappt die
                                                    // größere Kante auf 2048 → ganzes Bild sichtbar.
                                                    .scale(coil.size.Scale.FIT)
                                                    .build()
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer {
                                                    scaleX = zoomScale
                                                    scaleY = zoomScale
                                                    translationX = zoomOffset.x
                                                    translationY = zoomOffset.y
                                                }
                                                .transformable(
                                                    state = transformableState,
                                                    canPan = { zoomScale > 1f }
                                                ),
                                            contentScale = ContentScale.Fit
                                        )
                                        // Menü + Schließen-Button oben rechts
                                        val photoCtx = LocalContext.current
                                        var showPhotoMenu by remember { mutableStateOf(false) }
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box {
                                                IconButton(onClick = { showPhotoMenu = true }) {
                                                    Icon(
                                                        Icons.Default.MoreVert,
                                                        contentDescription = "Menü",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = showPhotoMenu,
                                                    onDismissRequest = { showPhotoMenu = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("In Galerie speichern") },
                                                        leadingIcon = {
                                                            Icon(Icons.Default.Download, contentDescription = null)
                                                        },
                                                        onClick = {
                                                            showPhotoMenu = false
                                                            val name = imageUrl.substringAfterLast('/', "photo.jpg").let {
                                                                if ('.' in it) it else "$it.jpg"
                                                            }
                                                            val req = DownloadManager.Request(android.net.Uri.parse(imageUrl))
                                                                .setTitle(name)
                                                                .setDescription("Wird in Galerie gespeichert…")
                                                                .setMimeType("image/jpeg")
                                                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                                .setDestinationInExternalPublicDir(
                                                                    android.os.Environment.DIRECTORY_PICTURES,
                                                                    "Lethe/$name"
                                                                )
                                                                .setAllowedOverMetered(true)
                                                            (photoCtx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                                                        }
                                                    )
                                                    if (isGroup && message.messageId != null) {
                                                        DropdownMenuItem(
                                                            text = { Text("Info") },
                                                            leadingIcon = {
                                                                Icon(Icons.Default.Info, contentDescription = null)
                                                            },
                                                            onClick = {
                                                                showPhotoMenu = false
                                                                showMediaInfo = true
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                            IconButton(onClick = {
                                                showFullscreen = false
                                                showImageEditor = true
                                            }) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Bearbeiten",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                            IconButton(onClick = { showFullscreen = false }) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Schließen",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (showImageEditor) {
                                ImageEditorDialog(
                                    imageUrl = imageUrl,
                                    chatId = chatId,
                                    isGroup = isGroup,
                                    viewModel = viewModel,
                                    onDismiss = { showImageEditor = false }
                                )
                            }
                            if (showMediaInfo && isGroup && message.messageId != null) {
                                GroupMediaInfoDialog(
                                    groupId = chatId,
                                    messageId = message.messageId!!,
                                    viewModel = viewModel!!,
                                    onDismiss = { showMediaInfo = false }
                                )
                            }
                            } // else imageApproved
                        }
                    }
                    "multi_image" -> {
                        val urlsJson = message.mediaUrl
                        val multiImgUploadProgress = myImageProgress
                        val multiImgFailed = multiImgUploadProgress == -2f
                        if (urlsJson.isNullOrBlank() || multiImgUploadProgress != null) {
                            // Upload läuft oder fehlgeschlagen → Platzhalter
                            val clipShape = RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp,
                                bottomStart = if (isFromMe) 4.dp else 16.dp,
                                bottomEnd = if (isFromMe) 16.dp else 4.dp
                            )
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 160.dp, max = 260.dp)
                                    .height(180.dp)
                                    .clip(clipShape)
                                    .background(Color(0xFF1C1C1C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (multiImgFailed) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text("Upload fehlgeschlagen", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Wiederholen",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 14.sp,
                                            modifier = Modifier.clickable {
                                                message.clientMessageId?.let { viewModel?.retryUpload(it) }
                                            }
                                        )
                                    } else {
                                        val pct = (multiImgUploadProgress ?: 0f).coerceIn(0f, 100f)
                                        Box(contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(
                                                progress = { pct / 100f },
                                                modifier = Modifier.size(56.dp),
                                                color = Color.White,
                                                trackColor = Color.Gray.copy(alpha = 0.3f),
                                                strokeWidth = 4.dp
                                            )
                                            Text("${pct.toInt()}%", color = Color.White, style = MaterialTheme.typography.labelMedium)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Abbrechen",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 13.sp,
                                            modifier = Modifier.clickable {
                                                message.clientMessageId?.let { viewModel?.cancelUpload(it) }
                                            }
                                        )
                                    }
                                }
                            }
                        } else if (!urlsJson.isNullOrBlank()) {
                            val urls: List<String> = run {
                                // Versuche zunächst direktes JSON-Parsing (korrekte Nachrichten nach Fix)
                                fun parseUrls(json: String): List<String>? = try {
                                    val arr = org.json.JSONArray(json)
                                    List(arr.length()) { arr.getString(it) }
                                        .map { if (it.startsWith("http")) it else "https://letheapp.de$it" }
                                        .takeIf { it.isNotEmpty() }
                                } catch (_: Exception) { null }

                                // Fallback: alte korrumpierte Nachrichten hatten "https://letheapp.de" vor dem JSON-Array
                                parseUrls(urlsJson)
                                    ?: parseUrls(urlsJson.removePrefix("https://letheapp.de"))
                                    ?: emptyList()
                            }
                            if (urls.isNotEmpty()) {
                                var showFullscreenMulti by remember { mutableStateOf(false) }
                                var fullscreenStartPage by remember { mutableIntStateOf(0) }
                                // Verhindert, dass ein durch gedrückt-Halten ausgelöstes Markieren
                                // zusätzlich noch als Einzel-Tipp (Galerie öffnen) erkannt wird.
                                var suppressNextMultiImageClick by remember { mutableStateOf(false) }
                                val displayUrls = urls.take(4)
                                val extraCount = urls.size - displayUrls.size

                                // Raster-Grid mit bis zu 4 Vorschaubildern
                                val gridSize = 120.dp
                                val cornerShape = RoundedCornerShape(
                                    topStart = 16.dp, topEnd = 16.dp,
                                    bottomStart = if (isFromMe) 4.dp else 16.dp,
                                    bottomEnd = if (isFromMe) 16.dp else 4.dp
                                )
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 260.dp)
                                        .clip(cornerShape)
                                        .combinedClickable(
                                            onClick = {
                                                if (suppressNextMultiImageClick) {
                                                    suppressNextMultiImageClick = false
                                                } else if (!isSelectionMode) {
                                                    fullscreenStartPage = 0
                                                    showFullscreenMulti = true
                                                }
                                            },
                                            onLongClick = {
                                                suppressNextMultiImageClick = true
                                                onLongClick()
                                            }
                                        )
                                ) {
                                    val multiImgCtx = LocalContext.current
                                    if (displayUrls.size == 1) {
                                        Image(
                                            painter = rememberAsyncImagePainter(
                                                ImageRequest.Builder(multiImgCtx)
                                                    .data(displayUrls[0])
                                                    .size(320, 320)
                                                    .build()
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(gridSize * 2)
                                                .clip(cornerShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            val rows = displayUrls.chunked(2)
                                            rows.forEachIndexed { rowIdx, rowUrls ->
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    rowUrls.forEachIndexed { colIdx, url ->
                                                        val isLast = rowIdx == rows.size - 1 &&
                                                            colIdx == rowUrls.size - 1 &&
                                                            extraCount > 0
                                                        Box(
                                                            modifier = Modifier.size(gridSize),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Image(
                                                                painter = rememberAsyncImagePainter(
                                                                    ImageRequest.Builder(multiImgCtx)
                                                                        .data(url)
                                                                        .size(240, 240)
                                                                        .build()
                                                                ),
                                                                contentDescription = null,
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .combinedClickable(
                                                                        onClick = {
                                                                            if (suppressNextMultiImageClick) {
                                                                                suppressNextMultiImageClick = false
                                                                            } else if (!isSelectionMode) {
                                                                                val flatIdx = rowIdx * 2 + colIdx
                                                                                fullscreenStartPage = flatIdx
                                                                                showFullscreenMulti = true
                                                                            }
                                                                        },
                                                                        onLongClick = {
                                                                            suppressNextMultiImageClick = true
                                                                            onLongClick()
                                                                        }
                                                                    ),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                            if (isLast) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .background(Color.Black.copy(alpha = 0.55f)),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text(
                                                                        "+$extraCount",
                                                                        color = Color.White,
                                                                        fontSize = 22.sp,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Vollbild-Viewer mit Blättern
                                if (showFullscreenMulti) {
                                    androidx.compose.ui.window.Dialog(
                                        onDismissRequest = { showFullscreenMulti = false },
                                        properties = androidx.compose.ui.window.DialogProperties(
                                            usePlatformDefaultWidth = false,
                                            dismissOnClickOutside = false
                                        )
                                    ) {
                                        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                                            initialPage = fullscreenStartPage
                                        ) { urls.size }
                                        val dlCtx = LocalContext.current
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black)
                                        ) {
                                            androidx.compose.foundation.pager.HorizontalPager(
                                                state = pagerState,
                                                modifier = Modifier.fillMaxSize()
                                            ) { page ->
                                                var zoomScale by remember { mutableStateOf(1f) }
                                                var zoomOffset by remember { mutableStateOf(Offset.Zero) }
                                                val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
                                                    val newScale = (zoomScale * zoomChange).coerceIn(1f, 6f)
                                                    zoomScale = newScale
                                                    zoomOffset = if (newScale > 1f) zoomOffset + offsetChange * 2.5f else Offset.Zero
                                                }
                                                // Beim Seitenwechsel Zoom zurücksetzen
                                                LaunchedEffect(pagerState.currentPage) {
                                                    if (pagerState.currentPage != page) {
                                                        zoomScale = 1f
                                                        zoomOffset = Offset.Zero
                                                    }
                                                }
                                                Image(
                                                    painter = rememberAsyncImagePainter(urls[page]),
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .graphicsLayer {
                                                            scaleX = zoomScale
                                                            scaleY = zoomScale
                                                            translationX = zoomOffset.x
                                                            translationY = zoomOffset.y
                                                        }
                                                        .transformable(
                                                            state = transformableState,
                                                            canPan = { zoomScale > 1f }
                                                        ),
                                                    contentScale = ContentScale.Fit
                                                )
                                            }
                                            // Seitenanzeige + Schließen
                                            Row(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "${pagerState.currentPage + 1} / ${urls.size}",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    modifier = Modifier
                                                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                                var showPageMenu by remember { mutableStateOf(false) }
                                                Box {
                                                    IconButton(onClick = { showPageMenu = true }) {
                                                        Icon(
                                                            Icons.Default.MoreVert,
                                                            contentDescription = "Menü",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(26.dp)
                                                        )
                                                    }
                                                    DropdownMenu(
                                                        expanded = showPageMenu,
                                                        onDismissRequest = { showPageMenu = false }
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = { Text("Foto speichern") },
                                                            leadingIcon = {
                                                                Icon(Icons.Default.Download, contentDescription = null)
                                                            },
                                                            onClick = {
                                                                showPageMenu = false
                                                                val curUrl = urls.getOrNull(pagerState.currentPage) ?: return@DropdownMenuItem
                                                                val name = curUrl.substringAfterLast('/', "photo.jpg").let {
                                                                    if ('.' in it) it else "$it.jpg"
                                                                }
                                                                val req = android.app.DownloadManager.Request(android.net.Uri.parse(curUrl))
                                                                    .setTitle(name)
                                                                    .setDescription("Wird in Galerie gespeichert…")
                                                                    .setMimeType("image/jpeg")
                                                                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                                    .setDestinationInExternalPublicDir(
                                                                        android.os.Environment.DIRECTORY_PICTURES,
                                                                        "Lethe/$name"
                                                                    )
                                                                    .setAllowedOverMetered(true)
                                                                (dlCtx.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager).enqueue(req)
                                                            }
                                                        )
                                                    }
                                                }
                                                IconButton(onClick = { showFullscreenMulti = false }) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Schließen",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "audio", "audio_e2ee" -> {
                        val accentPrimary = MaterialTheme.colorScheme.primary
                        if (message.mediaUrl == null) {
                            // Upload läuft – Fortschrittsanzeige
                            val voiceProgress by viewModel!!.voiceUploadProgress.collectAsState()
                            val displayProgress = voiceProgress.coerceIn(0f, 1f)
                            Row(
                                modifier = Modifier
                                    .widthIn(min = 180.dp, max = 260.dp)
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = accentPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    LinearProgressIndicator(
                                        progress = { displayProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = accentPrimary,
                                        trackColor = accentPrimary.copy(alpha = 0.2f)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Sprachnachricht wird gesendet…",
                                        fontSize = 11.sp,
                                        color = metaColor.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        } else {
                            // audio_e2ee: Ciphertext erst herunterladen+entschlüsseln, bevor der
                            // Player eine Quelle bekommt (leerer String = noch nicht bereit).
                            val playbackUrl by produceState(
                                initialValue = if (message.mediaType == "audio_e2ee") "" else (message.mediaUrl ?: ""),
                                message.mediaUrl, message.mediaType
                            ) {
                                value = viewModel!!.resolveAudioPlaybackSource(
                                    mediaUrl = message.mediaUrl ?: "",
                                    mediaType = message.mediaType ?: "audio",
                                    chatId = chatId,
                                    isGroup = isGroup,
                                    senderId = message.senderId
                                )
                            }
                            if (message.mediaType == "audio_e2ee" && playbackUrl.isBlank()) {
                                Row(
                                    modifier = Modifier
                                        .widthIn(min = 180.dp, max = 260.dp)
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = accentPrimary
                                    )
                                    Text(
                                        "Entschlüssele Sprachnachricht…",
                                        fontSize = 12.sp,
                                        color = metaColor.copy(alpha = 0.75f)
                                    )
                                }
                            } else {
                                AudioMessagePlayer(
                                    url = playbackUrl,
                                    viewModel = viewModel!!,
                                    accentColor = accentPrimary,
                                    metaColor = metaColor,
                                    isFromMe = isFromMe,
                                    isRead = message.deliveryStatus == 3,
                                    deliveryStatus = message.deliveryStatus,
                                    sentAt = timeText,
                                    senderAvatarUrl = if (isFromMe) myAvatarUrl else partnerAvatarUrl,
                                    messageId = message.messageId,
                                    groupId = if (isGroup) chatId else null,
                                    nextAudioUrl = nextAudioUrl,
                                    isSelectionMode = isSelectionMode,
                                    canLoad = isMediaApproved(message.mediaUrl ?: ""),
                                    onLoaded = { url -> onMediaLoaded(url) }
                                )
                            }
                        }
                    }
                    "audio_music" -> {
                        if (message.mediaUrl == null) {
                            // Upload läuft – Platzhalter anzeigen
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Musik wird hochgeladen…",
                                    fontSize = 13.sp,
                                    color = metaColor
                                )
                            }
                        } else {
                            MusicMessagePlayer(
                                url = message.mediaUrl ?: "",
                                accentColor = MaterialTheme.colorScheme.primary,
                                metaColor = metaColor,
                                sentAt = timeText,
                                viewModel = viewModel!!,
                                prevMusicUrl = prevMusicUrl,
                                nextMusicUrl = nextMusicUrl,
                                allChatMusicUrls = allChatMusicUrls,
                                isSelectionMode = isSelectionMode,
                                onDetach = onDetachMusicPlayer
                            )
                        }
                    }
                    "document" -> {
                        val docAccent = MaterialTheme.colorScheme.primary
                        val docUrl = run {
                            val blob = message.content ?: ""
                            try { org.json.JSONObject(blob).optString("file_url", "").ifBlank { null } }
                            catch (_: Exception) { null }
                        } ?: message.mediaUrl ?: ""
                        DocumentMessageCard(
                            contentBlob = message.content ?: "",
                            mediaUrl = docUrl,
                            accentColor = docAccent,
                            metaColor = metaColor,
                            sentAt = timeText,
                            onOpen = {
                                if (!isSelectionMode) {
                                val blob = message.content ?: ""
                                val fileName = try {
                                    org.json.JSONObject(blob).optString("filename", "").ifBlank { null }
                                } catch (_: Exception) { null }
                                    ?: if (blob.startsWith("[document:") && blob.endsWith("]"))
                                        blob.removePrefix("[document:").removeSuffix("]")
                                    else
                                        docUrl.substringAfterLast('/').ifBlank { "Dokument" }
                                if (docUrl.isNotBlank()) onOpenDocument?.invoke(docUrl, fileName)
                                }
                            }
                        )
                    }
                    "video" -> {
                        val uploadProgress = myVideoProgress
                        val videoUrl = message.mediaUrl ?: ""
                        val context = LocalContext.current
                        var showVideoPlayer by remember { mutableStateOf(false) }
                        var showVideoMediaInfo by remember { mutableStateOf(false) }
                        // Verhindert, dass ein durch gedrückt-Halten ausgelöstes Markieren
                        // zusätzlich noch als Einzel-Tipp (Video öffnen) erkannt wird.
                        var suppressNextVideoClick by remember { mutableStateOf(false) }
                        var videoAspectRatio by remember(videoUrl) { mutableStateOf(16f / 9f) }
                        val clipShape = RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = if (isFromMe) 4.dp else 16.dp,
                            bottomEnd = if (isFromMe) 16.dp else 4.dp
                        )

                        if (uploadProgress != null || videoUrl.isEmpty()) {
                            // Ladebalken-Karte: Komprimierung oder Upload läuft
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 160.dp, max = 260.dp)
                                    .height(110.dp)
                                    .clip(clipShape)
                                    .background(Color(0xFF1C1C1C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                ) {
                                    when {
                                        // Empfangene Nachricht ohne URL: Video nicht verfügbar
                                        !isFromMe && videoUrl.isEmpty() -> {
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(30.dp)
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                "Video nicht verfügbar",
                                                color = Color.White.copy(alpha = 0.7f),
                                                fontSize = 12.sp
                                            )
                                        }
                                        uploadProgress == -2f -> {
                                            // Fehlgeschlagen → Wiederholen anzeigen
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(30.dp)
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                "Upload fehlgeschlagen",
                                                color = Color.White.copy(alpha = 0.7f),
                                                fontSize = 12.sp
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                "Wiederholen",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 14.sp,
                                                modifier = Modifier.clickable {
                                                    message.clientMessageId?.let { viewModel?.retryUpload(it) }
                                                }
                                            )
                                        }
                                        uploadProgress == -1f -> {
                                            // Lokale Transkodierung läuft
                                            CircularProgressIndicator(
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(30.dp),
                                                strokeWidth = 3.dp
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                "Wird komprimiert…",
                                                color = Color.White.copy(alpha = 0.8f),
                                                fontSize = 12.sp
                                            )
                                        }
                                        uploadProgress != null -> {
                                            // Upload läuft (0–99 %)
                                            Box(contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(
                                                    progress = { uploadProgress / 100f },
                                                    modifier = Modifier.size(48.dp),
                                                    color = Color.White,
                                                    trackColor = Color.Gray.copy(alpha = 0.3f),
                                                    strokeWidth = 3.dp
                                                )
                                                Text(
                                                    text = "${uploadProgress.toInt()}%",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                "Abbrechen",
                                                color = Color.White.copy(alpha = 0.7f),
                                                fontSize = 13.sp,
                                                modifier = Modifier.clickable {
                                                    message.clientMessageId?.let { viewModel?.cancelUpload(it) }
                                                }
                                            )
                                        }
                                        else -> {
                                            // URL noch nicht gesetzt (Fallback)
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(30.dp),
                                                strokeWidth = 3.dp
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Kartengröße dynamisch aus Seitenverhältnis berechnen
                            // Querformat: max 260dp breit, Höhe ergibt sich aus Ratio
                            // Hochformat: max 300dp hoch, Breite ergibt sich aus Ratio
                            val isPortrait = videoAspectRatio < 1f
                            val cardW = if (isPortrait) (300f * videoAspectRatio).coerceIn(80f, 260f).dp
                                        else 260.dp
                            val cardH = if (isPortrait) 300.dp
                                        else (260f / videoAspectRatio).coerceIn(80f, 300f).dp

                            Box(
                                modifier = Modifier
                                    .size(cardW, cardH)
                                    .clip(clipShape)
                                    .combinedClickable(
                                        onClick = {
                                            if (suppressNextVideoClick) {
                                                suppressNextVideoClick = false
                                            } else if (!isSelectionMode) {
                                                showVideoPlayer = true
                                            }
                                        },
                                        onLongClick = {
                                            suppressNextVideoClick = true
                                            onLongClick()
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                VideoThumbnailImage(
                                    url = videoUrl,
                                    viewModel = viewModel!!,
                                    modifier = Modifier.fillMaxSize(),
                                    onAspectRatio = { ratio -> videoAspectRatio = ratio },
                                    canLoad = isMediaApproved(videoUrl),
                                    onLoaded = { onMediaLoaded(videoUrl) }
                                )
                            }
                            // Aufruf tracken wenn Video im Gruppen-Chat geöffnet wird
                            if (showVideoPlayer && isGroup && !isFromMe) {
                                val msgId = message.messageId
                                LaunchedEffect(showVideoPlayer) {
                                    if (msgId != null) viewModel!!.recordGroupMediaView(chatId, msgId)
                                }
                            }

                            if (showVideoPlayer) {
                                Dialog(
                                    onDismissRequest = { showVideoPlayer = false },
                                    properties = DialogProperties(usePlatformDefaultWidth = false)
                                ) {
                                    val playerCtx = LocalContext.current
                                    val playChatId = message.chatId
                                    val exoPlayer = remember {
                                        val publicUri = viewModel?.getPublicMediaUri(videoUrl, true)
                                        val playUri = when {
                                            publicUri != null -> publicUri
                                            else -> {
                                                val localPath = viewModel?.getCachedVideoPath(videoUrl, playChatId)
                                                if (localPath != null) android.net.Uri.fromFile(java.io.File(localPath))
                                                else { viewModel?.ensureVideoCached(videoUrl, playChatId); android.net.Uri.parse(videoUrl) }
                                            }
                                        }
                                        ExoPlayer.Builder(playerCtx).build().apply {
                                            setMediaItem(MediaItem.fromUri(playUri))
                                            prepare()
                                            playWhenReady = true
                                            // Nach Pause/Stopp Video in den Movies-Ordner verschieben.
                                            addListener(object : androidx.media3.common.Player.Listener {
                                                override fun onIsPlayingChanged(isPlaying: Boolean) {
                                                    if (!isPlaying) viewModel?.exportVideoToMovies(videoUrl, playChatId)
                                                }
                                            })
                                        }
                                    }
                                    DisposableEffect(exoPlayer) {
                                        onDispose {
                                            viewModel?.exportVideoToMovies(videoUrl, playChatId)
                                            exoPlayer.release()
                                        }
                                    }
                                    // Andere Wiedergaben (Musik/fremde Apps) pausieren, danach fortsetzen
                                    TransientMediaFocus(exoPlayer, active = true)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AndroidView(
                                            factory = { ctx ->
                                                PlayerView(ctx).apply {
                                                    player = exoPlayer
                                                    useController = true
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        // Menü + Schließen-Button oben rechts
                                        var showVideoMenu by remember { mutableStateOf(false) }
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(onClick = {
                                                scope.launch {
                                                    quickShareMediaFile(playerCtx, videoUrl, "video")
                                                }
                                            }) {
                                                Icon(
                                                    Icons.Default.IosShare,
                                                    contentDescription = "Teilen",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                            Box {
                                                IconButton(onClick = { showVideoMenu = true }) {
                                                    Icon(
                                                        Icons.Default.MoreVert,
                                                        contentDescription = "Menü",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = showVideoMenu,
                                                    onDismissRequest = { showVideoMenu = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("In Galerie speichern") },
                                                        leadingIcon = {
                                                            Icon(Icons.Default.Download, contentDescription = null)
                                                        },
                                                        onClick = {
                                                            showVideoMenu = false
                                                            val name = videoUrl.substringAfterLast('/', "video.mp4").let {
                                                                if ('.' in it) it else "$it.mp4"
                                                            }
                                                            val req = DownloadManager.Request(android.net.Uri.parse(videoUrl))
                                                                .setTitle(name)
                                                                .setDescription("Wird in Galerie gespeichert…")
                                                                .setMimeType("video/mp4")
                                                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                                .setDestinationInExternalPublicDir(
                                                                    android.os.Environment.DIRECTORY_MOVIES,
                                                                    "Lethe/$name"
                                                                )
                                                                .setAllowedOverMetered(true)
                                                            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                                                        }
                                                    )
                                                    if (isGroup && message.messageId != null) {
                                                        DropdownMenuItem(
                                                            text = { Text("Info") },
                                                            leadingIcon = {
                                                                Icon(Icons.Default.Info, contentDescription = null)
                                                            },
                                                            onClick = {
                                                                showVideoMenu = false
                                                                showVideoMediaInfo = true
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                            IconButton(onClick = { showVideoPlayer = false }) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Schließen",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (showVideoMediaInfo && isGroup && message.messageId != null) {
                                GroupMediaInfoDialog(
                                    groupId = chatId,
                                    messageId = message.messageId!!,
                                    viewModel = viewModel!!,
                                    onDismiss = { showVideoMediaInfo = false }
                                )
                            }
                        }
                    }
                    "circle_video" -> {
                        val uploadProgress = myVideoProgress
                        val videoUrl = message.mediaUrl ?: ""
                        val context = LocalContext.current
                        var isExpanded by remember { mutableStateOf(false) }
                        // Verhindert, dass ein durch gedrückt-Halten ausgelöstes Markieren
                        // zusätzlich noch als Einzel-Tipp (Circle-Video öffnen) erkannt wird.
                        var suppressNextCircleClick by remember { mutableStateOf(false) }
                        val circleSize = 180.dp
                        val expandedSize = 260.dp
                        val animatedSize by animateDpAsState(
                            targetValue = if (isExpanded) expandedSize else circleSize,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "circleExpand"
                        )
                        val accentColor = MaterialTheme.colorScheme.primary

                        if (uploadProgress != null || videoUrl.isEmpty()) {
                            // Upload-Fortschritt
                            Box(
                                modifier = Modifier
                                    .size(circleSize)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1C1C1C)),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    uploadProgress == -2f -> {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(28.dp))
                                            Spacer(Modifier.height(4.dp))
                                            Text("Wiederholen", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp,
                                                modifier = Modifier.clickable { message.clientMessageId?.let { viewModel?.retryUpload(it) } })
                                        }
                                    }
                                    uploadProgress == -1f -> CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp),
                                        strokeWidth = 3.dp
                                    )
                                    uploadProgress != null -> {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(
                                                progress = { uploadProgress / 100f },
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(40.dp),
                                                strokeWidth = 3.dp
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text("${uploadProgress.toInt()}%", color = Color.White, fontSize = 11.sp)
                                            Spacer(Modifier.height(2.dp))
                                            Text("Abbrechen", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp,
                                                modifier = Modifier.clickable { message.clientMessageId?.let { viewModel?.cancelUpload(it) } })
                                        }
                                    }
                                    else -> CircularProgressIndicator(color = Color.White, modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                                }
                            }
                        } else {
                            val circleChatId = message.chatId
                            val circleId = remember(videoUrl) { message.clientMessageId ?: videoUrl }
                            var hasBeenViewed by remember { mutableStateOf(false) }
                            var progress by remember(videoUrl) { mutableStateOf(0f) }
                            var isDragging by remember { mutableStateOf(false) }
                            var previewDone by remember(videoUrl) { mutableStateOf(false) }

                            // ExoPlayer wird erstellt, startet aber NICHT automatisch – der
                            // CircleVideoPreviewCoordinator erlaubt max. 2 stumme Vorschauen gleichzeitig.
                            val circlePlayer = remember(videoUrl) {
                                val publicUri = viewModel?.getPublicMediaUri(videoUrl, true)
                                val playUri = when {
                                    publicUri != null -> publicUri
                                    else -> {
                                        val localPath = viewModel?.getCachedVideoPath(videoUrl, circleChatId)
                                        if (localPath != null) android.net.Uri.fromFile(java.io.File(localPath))
                                        else { viewModel?.ensureVideoCached(videoUrl, circleChatId); android.net.Uri.parse(videoUrl) }
                                    }
                                }
                                ExoPlayer.Builder(context).build().apply {
                                    setMediaItem(MediaItem.fromUri(playUri))
                                    volume = 0f            // Vorschau ohne Ton
                                    prepare()
                                    playWhenReady = false  // erst durch Coordinator gestartet
                                    repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
                                    addListener(object : androidx.media3.common.Player.Listener {
                                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                                            if (!isPlaying) viewModel?.exportVideoToMovies(videoUrl, circleChatId)
                                        }
                                    })
                                }
                            }
                            // Fortschritt live abfragen (außer während Ziehen)
                            LaunchedEffect(circlePlayer) {
                                while (true) {
                                    val dur = circlePlayer.duration
                                    if (dur > 0 && !isDragging) {
                                        progress = (circlePlayer.currentPosition.toFloat() / dur).coerceIn(0f, 1f)
                                    }
                                    kotlinx.coroutines.delay(40)
                                }
                            }
                            // Vorschau-Slot anfordern + freigeben, wenn die einmalige Vorschau endet
                            DisposableEffect(circlePlayer, circleId) {
                                val endListener = object : androidx.media3.common.Player.Listener {
                                    override fun onPlaybackStateChanged(state: Int) {
                                        if (state == androidx.media3.common.Player.STATE_ENDED) {
                                            previewDone = true
                                            CircleVideoPreviewCoordinator.release(circleId)
                                        }
                                    }
                                }
                                circlePlayer.addListener(endListener)
                                if (!previewDone && !isExpanded) {
                                    CircleVideoPreviewCoordinator.request(circleId) {
                                        if (!isExpanded && !previewDone) {
                                            circlePlayer.volume = 0f
                                            circlePlayer.seekTo(0)
                                            circlePlayer.playWhenReady = true
                                        }
                                    }
                                }
                                onDispose {
                                    circlePlayer.removeListener(endListener)
                                    CircleVideoPreviewCoordinator.release(circleId)
                                    viewModel?.exportVideoToMovies(videoUrl, circleChatId)
                                    circlePlayer.release()
                                }
                            }
                            // Nur die tonende (aufgeklappte) Wiedergabe hält Fokus: andere
                            // Wiedergaben pausieren beim Aufklappen, danach laufen sie weiter.
                            TransientMediaFocus(circlePlayer, active = isExpanded)

                            Box(
                                modifier = Modifier
                                    .size(animatedSize)
                                    .combinedClickable(
                                        onClick = {
                                            if (suppressNextCircleClick) {
                                                suppressNextCircleClick = false
                                            } else if (!isSelectionMode) {
                                                isExpanded = !isExpanded
                                                hasBeenViewed = true
                                                if (isExpanded) {
                                                    circlePlayer.volume = 1f
                                                    circlePlayer.seekTo(0)
                                                    circlePlayer.playWhenReady = true
                                                } else {
                                                    circlePlayer.volume = 0f
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            suppressNextCircleClick = true
                                            onLongClick()
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                // Video-Vorschau/-Wiedergabe (Platz für Ring lassen)
                                Box(
                                    modifier = Modifier
                                        .size(animatedSize - 10.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AndroidView(
                                        factory = { ctx ->
                                            PlayerView(ctx).apply {
                                                player = circlePlayer
                                                useController = false
                                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // Fortschrittsring: abgespielter Teil in Akzentfarbe, Rest grau; Ziehen = Springen
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(circlePlayer) {
                                            detectDragGestures(
                                                onDragStart = { isDragging = true },
                                                onDragEnd = {
                                                    val dur = circlePlayer.duration
                                                    if (dur > 0) circlePlayer.seekTo((progress * dur).toLong())
                                                    isDragging = false
                                                }
                                            ) { change, _ ->
                                                val cx = size.width / 2f
                                                val cy = size.height / 2f
                                                val dx = change.position.x - cx
                                                val dy = change.position.y - cy
                                                var deg = Math.toDegrees(
                                                    kotlin.math.atan2(dy.toDouble(), dx.toDouble())
                                                ).toFloat() + 90f
                                                if (deg < 0f) deg += 360f
                                                progress = (deg / 360f).coerceIn(0f, 1f)
                                                val dur = circlePlayer.duration
                                                if (dur > 0) circlePlayer.seekTo((progress * dur).toLong())
                                            }
                                        }
                                ) {
                                    val stroke = 4.dp.toPx()
                                    val inset = stroke / 2f
                                    // grauer Hintergrundring (noch nicht abgespielt)
                                    drawArc(
                                        color = Color.Gray.copy(alpha = 0.55f),
                                        startAngle = -90f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        topLeft = Offset(inset, inset),
                                        size = Size(size.width - stroke, size.height - stroke),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                                    )
                                    // Fortschritt (abgespielt) in Akzentfarbe
                                    drawArc(
                                        color = accentColor,
                                        startAngle = -90f,
                                        sweepAngle = 360f * progress,
                                        useCenter = false,
                                        topLeft = Offset(inset, inset),
                                        size = Size(size.width - stroke, size.height - stroke),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = stroke,
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                                        )
                                    )
                                }

                                // Gesehen-Indikator
                                if (hasBeenViewed) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                            .size(20.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    "poll" -> {
                        val pollId = try {
                            org.json.JSONObject(message.content ?: "").getString("poll_id")
                        } catch (e: Exception) { null }
                        if (pollId != null && viewModel != null) {
                            PollMessageCard(
                                pollId = pollId,
                                viewModel = viewModel,
                                textColor = textColor,
                                bubbleColor = bubbleColor
                            )
                        } else {
                            Text(
                                text = message.content ?: "",
                                color = textColor,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp)
                            )
                        }
                    }

                    "appointment", "appointment_proposal" -> {
                        val appointmentId = try {
                            org.json.JSONObject(message.content ?: "").getString("appointment_id")
                        } catch (e: Exception) { null }
                        if (appointmentId != null && viewModel != null) {
                            AppointmentMessageCard(
                                appointmentId = appointmentId,
                                isFinalized = message.mediaType == "appointment",
                                viewModel = viewModel
                            )
                        } else {
                            Text(
                                text = "📅 Termin",
                                color = textColor,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp)
                            )
                        }
                    }
                    "link" -> {
                        // Link-Vorschau-Karte (mit optionalem Embed-Support für YouTube etc.)
                        val ctx = LocalContext.current
                        val json = try { org.json.JSONObject(message.content ?: "{}") } catch (_: Exception) { null }
                        if (json != null) {
                            val lpUrl = json.optString("url", "")
                            val lpTitle = json.optString("title", lpUrl)
                            val lpDesc = json.optString("description", "").takeIf { it.isNotBlank() }
                            val lpImage = json.optString("image", "").takeIf { it.isNotBlank() }
                            val lpSite = json.optString("site_name", "").takeIf { it.isNotBlank() }
                            val embedType = json.optString("embed_type", "").takeIf { it.isNotBlank() }
                            val embedUrl = json.optString("embed_url", "").takeIf { it.isNotBlank() }
                            val lpText = json.optString("text", "").takeIf { it.isNotBlank() }

                            var showWebView by remember { mutableStateOf(false) }

                            Column(modifier = Modifier.widthIn(min = 180.dp, max = 280.dp)) {
                            // Nachrichtentext über der Vorschau-Karte (wenn vorhanden)
                            if (lpText != null) {
                                val annotated = remember(lpText) { annotateWithLinks(lpText) }
                                @Suppress("DEPRECATION")
                                ClickableText(
                                    text = annotated,
                                    style = TextStyle(color = textColor, fontSize = 15.sp),
                                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
                                    onClick = { offset ->
                                        annotated.getStringAnnotations("URL", offset, offset)
                                            .firstOrNull()?.let { ann ->
                                                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ann.item))) }
                                                catch (_: Exception) {}
                                            }
                                    }
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .widthIn(min = 180.dp, max = 280.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (embedType != null && embedUrl != null) {
                                            showWebView = true
                                        } else if (lpUrl.isNotBlank()) {
                                            try {
                                                ctx.startActivity(
                                                    android.content.Intent(
                                                        android.content.Intent.ACTION_VIEW,
                                                        android.net.Uri.parse(lpUrl)
                                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                            } catch (_: Exception) {}
                                        }
                                    }
                            ) {
                                // Thumbnail mit Play-Overlay für Embeds
                                if (embedType != null && lpImage != null) {
                                    Box {
                                        AsyncImage(
                                            model = lpImage,
                                            contentDescription = "Thumbnail",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth().height(150.dp)
                                        )
                                        // Play-Overlay
                                        Icon(
                                            Icons.Default.PlayCircle,
                                            contentDescription = "Abspielen",
                                            tint = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier.size(52.dp).align(Alignment.Center)
                                        )
                                        // YouTube-Badge oben links
                                        if (embedType == "youtube") {
                                            Box(
                                                modifier = Modifier
                                                    .padding(6.dp)
                                                    .align(Alignment.TopStart)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFFFF0000))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("YouTube", color = Color.White, fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                } else if (lpImage != null) {
                                    // eBay/Amazon-Bild-CDNs blocken Anfragen ohne Browser-
                                    // User-Agent/Referer mit 403 → Header mitschicken.
                                    AsyncImage(
                                        model = ImageRequest.Builder(ctx)
                                            .data(lpImage)
                                            .headers(
                                                okhttp3.Headers.Builder()
                                                    .add("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                                                    .add("Referer", lpUrl)
                                                    .build()
                                            )
                                            .build(),
                                        contentDescription = "Link-Bild",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(130.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(60.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    if (lpSite != null) {
                                        Text(lpSite, fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold)
                                    }
                                    Text(lpTitle, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                        color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (lpDesc != null) {
                                        Text(lpDesc, fontSize = 12.sp,
                                            color = textColor.copy(alpha = 0.65f),
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(lpUrl.take(60), fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp))
                                }
                            }

                            // Schwebender Embed-Player (YouTube etc.)
                            if (showWebView && embedUrl != null) {
                                EmbedPlayerDialog(
                                    embedUrl = embedUrl,
                                    originalUrl = lpUrl,
                                    embedType = embedType,
                                    onDismiss = { showWebView = false }
                                )
                            }
                            } // outer Column (text + preview)
                        }
                    }
                    "3dprint" -> {
                        val meta = try {
                            val j = org.json.JSONObject(message.content ?: "{}")
                            com.securechat.app.data.network.ThreeDFileMeta(
                                fileUrl = j.optString("file_url", ""),
                                previewUrl = j.optString("preview_url", ""),
                                filename = j.optString("filename", "3D-Datei"),
                                fileSize = j.optLong("file_size", 0L),
                                textureUrl = j.optString("texture_url", ""),
                                price = j.optInt("price", 0)
                            )
                        } catch (e: Exception) { null }
                        if (meta != null) {
                            ThreeDFileCard(
                                meta = meta,
                                bubbleColor = bubbleColor,
                                textColor = textColor,
                                isFromMe = isFromMe,
                                currentUserStyx = myUser?.styx ?: 0,
                                onOpen = { onNavigateTo3DViewer?.invoke(meta.fileUrl, meta.filename, meta.textureUrl) },
                                onNavigateToCoins = onNavigateToCoins,
                                onPurchase = { onPaid ->
                                    val msgId = message.messageId
                                    if (msgId != null) {
                                        viewModel?.purchase3DFile(
                                            messageId = msgId,
                                            onSuccess = { _ -> onPaid() },
                                            onError = { /* Fehler wird im Dialog gezeigt */ }
                                        )
                                    }
                                }
                            )
                        } else {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ViewInAr, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("3D-Datei", color = textColor, fontSize = 15.sp)
                            }
                        }
                    }
                    "contact_card" -> {
                        val cardJson = try { org.json.JSONObject(message.content ?: "{}") } catch (_: Exception) { null }
                        if (cardJson != null) {
                            ContactCardBubble(
                                json = cardJson,
                                textColor = textColor,
                                viewModel = viewModel
                            )
                        }
                    }
                    else -> {
                        // Defensiver Fallback für unbekannte media_type-Werte (z. B. wenn dieser
                        // Client älter ist als der Absender – etwa "audio_e2ee" auf einer noch
                        // nicht aktualisierten App): Absender kodiert unbehandelte Medientypen als
                        // "[media_type]"-Platzhalter im content-Feld (siehe sendMediaMessage/
                        // sendGroupMediaMessage). Statt diesen rohen Platzhalter anzuzeigen, wird
                        // ein verständlicher Hinweistext eingesetzt (Rest der Anzeige unverändert).
                        val rawContent = message.content ?: ""
                        val content = if (!message.mediaType.isNullOrBlank() && message.mediaType != "text" &&
                            rawContent == "[${message.mediaType}]"
                        ) {
                            "🔒 Nicht unterstützter Inhalt – bitte Lethe aktualisieren"
                        } else rawContent

                        // --- Lethe:// Deep-Link-Vorschau (sp?id=, sp?url=, li?id=, post?C=) ---
                        val letheRegex = remember { Regex("""lethe://(sp|li|post)\?(?:id|[Cc])=([a-zA-Z0-9_-]+)""") }
                        val letheMatch = remember(content) { letheRegex.find(content.trim()) }
                        // Auch lethe://sp?url=<encodedHlsUrl> erkennen
                        val sparkUrlRegex = remember { Regex("""^lethe://sp\?url=(.+)$""") }
                        val sparkUrlMatch = remember(content) { if (letheMatch == null) sparkUrlRegex.find(content.trim()) else null }
                        if (letheMatch != null || sparkUrlMatch != null) {
                            val isSparkUrlLink = sparkUrlMatch != null
                            val sparkHlsUrl = sparkUrlMatch?.groupValues?.get(1)?.let {
                                try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
                            }
                            // UUID aus HLS-Pfad /sparks/{uuid}/index.m3u8 extrahieren
                            val sparkUuidFromUrl = sparkHlsUrl?.let {
                                Regex("""/sparks/([0-9a-f-]{36})/""").find(it)?.groupValues?.get(1)
                            }
                            val linkHost = letheMatch?.groupValues?.get(1) ?: "sp"
                            val isSparkLink = isSparkUrlLink || linkHost == "sp"
                            val isLiveLink = !isSparkUrlLink && linkHost == "li"
                            val contentId = sparkUuidFromUrl ?: letheMatch?.groupValues?.get(2) ?: ""
                            var lethePreview by remember(contentId) {
                                mutableStateOf<com.securechat.app.data.network.PublicContentPreview?>(null)
                            }
                            LaunchedEffect(contentId) {
                                if (viewModel != null && contentId.isNotBlank()) {
                                    lethePreview = viewModel.fetchPublicContentPreview(contentId)
                                }
                            }
                            val ctx = LocalContext.current
                            val deepLinkUrl = when {
                                isSparkUrlLink -> "lethe://sp?url=${sparkUrlMatch!!.groupValues[1]}"
                                isLiveLink -> "lethe://li?id=$contentId"
                                isSparkLink -> "lethe://sp?id=$contentId"
                                else -> "lethe://post?C=$contentId"
                            }
                            Column(
                                modifier = Modifier
                                    .widthIn(min = 200.dp, max = 280.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        when {
                                            // sp?url= → immer als Deep-Link feuern (prependSparkUrlToFeed in MainActivity)
                                            isSparkUrlLink -> try {
                                                ctx.startActivity(
                                                    android.content.Intent(
                                                        android.content.Intent.ACTION_VIEW,
                                                        android.net.Uri.parse(deepLinkUrl)
                                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                            } catch (_: Exception) {}
                                            isSparkLink && onNavigateToSpark != null -> onNavigateToSpark(contentId)
                                            !isSparkLink && !isLiveLink && onNavigateToContent != null -> onNavigateToContent(contentId)
                                            else -> try {
                                                ctx.startActivity(
                                                    android.content.Intent(
                                                        android.content.Intent.ACTION_VIEW,
                                                        android.net.Uri.parse(deepLinkUrl)
                                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                            } catch (_: Exception) {}
                                        }
                                    }
                            ) {
                                val previewImageUrl = lethePreview?.previewImageUrl?.let {
                                    if (it.startsWith("http")) it else "https://letheapp.de$it"
                                }
                                if (isSparkLink && previewImageUrl != null) {
                                    // Spark: Vorschaubild mit Play-Overlay und Badge
                                    Box {
                                        AsyncImage(
                                            model = previewImageUrl,
                                            contentDescription = "Vorschau",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth().height(140.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .align(Alignment.TopStart)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Spark", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Icon(
                                            Icons.Default.PlayCircle,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(44.dp).align(Alignment.Center)
                                        )
                                    }
                                } else {
                                    // Livestream & Beiträge: flache Karte ohne Bild, mit Badge-Zeile
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            if (isLiveLink) Icons.Default.PlayCircle else Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = if (isLiveLink) Color(0xFFE53935) else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        if (isLiveLink) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFFE53935))
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(
                                        text = when {
                                            isLiveLink -> "Livestream"
                                            isSparkLink -> "Spark"
                                            else -> "Lethe"
                                        },
                                        fontSize = 11.sp,
                                        color = if (isLiveLink) Color(0xFFE53935) else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = lethePreview?.title ?: if (isLiveLink) "Livestream" else if (isSparkLink) "Spark" else "Beitrag",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = textColor,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (lethePreview?.creatorUsername != null || lethePreview?.creatorName != null) {
                                        Text(
                                            text = "@${lethePreview?.creatorUsername ?: lethePreview?.creatorName}",
                                            fontSize = 11.sp,
                                            color = textColor.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            showExternalTimestamp = true
                        } else {

                        val liveLocMatch = Regex("""[\uD83D\uDCCD📍]live:(\S+) https://maps\.google\.com/\?q=([-\d.]+),([-\d.]+)(?:\|preview:(\S+))?""").find(content)
                        val locMatch = if (liveLocMatch == null) Regex("""[\uD83D\uDCCD📍] https://maps\.google\.com/\?q=([-\d.]+),([-\d.]+)(?:\|preview:(\S+))?""").find(content) else null
                        // Auch Google-Maps-URLs ohne 📍-Prefix erkennen, aber nur wenn
                        // die Nachricht primär aus der URL besteht (max 20 Zeichen extra)
                        val mapsUrlCoords = if (liveLocMatch == null && locMatch == null) {
                            val mUrl = detectGoogleMapsLink(content)
                            if (mUrl != null && content.trim().length - mUrl.length <= 20) extractCoordsFromMapsUrl(mUrl) else null
                        } else null
                        val locLat = liveLocMatch?.groupValues?.get(2)?.toDoubleOrNull()
                            ?: locMatch?.groupValues?.get(1)?.toDoubleOrNull()
                            ?: mapsUrlCoords?.first
                        val locLng = liveLocMatch?.groupValues?.get(3)?.toDoubleOrNull()
                            ?: locMatch?.groupValues?.get(2)?.toDoubleOrNull()
                            ?: mapsUrlCoords?.second
                        // Fallback: live-Dauer aus Inhalt extrahieren wenn Haupt-Regex scheitert
                        // (z.B. bei abweichender Emoji-Kodierung in Gruppen-E2EE-Nachrichten)
                        val liveDuration = liveLocMatch?.groupValues?.get(1)
                            ?: if (liveLocMatch == null && content.contains("live:")) {
                                Regex("""live:(\S+?)\s""").find(content)?.groupValues?.get(1)
                            } else null
                        val locPreviewUrl = liveLocMatch?.groupValues?.get(4)?.takeIf { it.isNotEmpty() }
                            ?: locMatch?.groupValues?.get(3)?.takeIf { it.isNotEmpty() }
                        if (locLat != null && locLng != null) {
                            LocationMessageBubble(
                                lat = locLat,
                                lng = locLng,
                                isFromMe = isFromMe,
                                liveDuration = liveDuration,
                                messageTimestamp = message.timestamp,
                                previewImageUrl = locPreviewUrl,
                                onTapLive = if (liveDuration != null) {
                                    { onNavigateToLiveMaps?.invoke(chatId) }
                                } else null
                            )
                        } else {
                            // Emoji-Only Erkennung: 1-3 Emojis → doppelte Größe + Animation
                            val emojiCount = precomputedItem?.emojiOnlyCount ?: remember(content) { getEmojiOnlyCount(content) }
                            if (emojiCount != null) {
                                // Nachricht enthält nur Emojis → groß + animiert darstellen
                                AnimatedEmojiMessage(
                                    text = content,
                                    emojiCount = emojiCount,
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(
                                        start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp
                                    )
                                )
                            } else {
                                val isCodeBlock = content.length > 4 && content.startsWith("/*") && content.endsWith("*/")
                                if (isCodeBlock) {
                                    val codeText = content.drop(2).dropLast(2)
                                    val codeCtx = LocalContext.current
                                    var copyDone by remember { mutableStateOf(false) }
                                    var isEditing by remember { mutableStateOf(false) }
                                    var editedCode by remember(codeText) { mutableStateOf(codeText) }
                                    Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF1E1E2E),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFF2A2A3E))
                                                        .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    IconButton(
                                                        onClick = { isEditing = !isEditing },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isEditing) Icons.Default.Close else Icons.Default.Edit,
                                                            contentDescription = if (isEditing) "Abbrechen" else "Bearbeiten",
                                                            tint = if (isEditing) Color(0xFFFF5555) else Color(0xFFAAAAAA),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            if (isEditing) {
                                                                viewModel?.editMessage(message.localId, "/*${editedCode}*/")
                                                                isEditing = false
                                                                copyDone = false
                                                            } else {
                                                                val clipboard = codeCtx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                                clipboard.setPrimaryClip(ClipData.newPlainText("code", editedCode))
                                                                copyDone = true
                                                            }
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isEditing) Icons.Default.Save else if (copyDone) Icons.Default.Check else Icons.Default.ContentCopy,
                                                            contentDescription = if (isEditing) "Speichern" else "Kopieren",
                                                            tint = if (isEditing) Color(0xFF4CAF50) else if (copyDone) Color(0xFF4CAF50) else Color(0xFFAAAAAA),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                if (isEditing) {
                                                    BasicTextField(
                                                        value = editedCode,
                                                        onValueChange = { editedCode = it },
                                                        textStyle = TextStyle(
                                                            color = Color(0xFFD4D4D4),
                                                            fontSize = 13.sp,
                                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                            lineHeight = 19.sp
                                                        ),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                                    )
                                                } else {
                                                    val codeHScrollState = rememberScrollState()
                                                    Text(
                                                        text = remember(editedCode) { highlightCode(editedCode) },
                                                        fontSize = 13.sp,
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                        lineHeight = 19.sp,
                                                        softWrap = false,
                                                        modifier = Modifier
                                                            .horizontalScroll(codeHScrollState)
                                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                showExternalTimestamp = false
                                val detectedAddress = remember(content) { detectStreetAddress(content) }
                                if (detectedAddress != null) {
                                    AddressMessageCard(address = detectedAddress)
                                }
                                val mapsLinkUrl = remember(content) { detectGoogleMapsLink(content) }
                                if (mapsLinkUrl != null) {
                                    MapsLinkCard(url = mapsLinkUrl, isFromMe = isFromMe)
                                }
                                val needsCollapse = content.length > 400
                                var isExpanded by remember(message.localId) { mutableStateOf(false) }
                                val displayContent = if (needsCollapse && !isExpanded) content.take(400) else content
                                if (needsCollapse) {
                                    // Langes Layout: Text + Aufklappen-Button + Zeitstempel als Column
                                    Column(modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 6.dp)) {
                                        val annotatedLong = remember(displayContent) { annotateWithLinks(displayContent) }
                                        val longCtx = LocalContext.current
                                        @Suppress("DEPRECATION")
                                        ClickableText(
                                            text = annotatedLong,
                                            style = TextStyle(color = textColor, fontSize = 15.sp),
                                            onClick = { offset ->
                                                annotatedLong.getStringAnnotations("URL", offset, offset)
                                                    .firstOrNull()?.let { ann ->
                                                        try { longCtx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ann.item))) }
                                                        catch (_: Exception) {}
                                                    }
                                            }
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { isExpanded = !isExpanded }
                                                .padding(top = 6.dp, bottom = 2.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isExpanded) "Zuklappen" else "Aufklappen",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 13.sp
                                            )
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (isExpanded) "Zuklappen" else "Aufklappen",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (message.isImportant) {
                                                Icon(Icons.Default.Star, contentDescription = null,
                                                    modifier = Modifier.size(12.dp), tint = Color(0xFFFFC107))
                                                Spacer(Modifier.width(3.dp))
                                            }
                                            if (message.isEdited) {
                                                Icon(Icons.Default.Edit, contentDescription = "Bearbeitet",
                                                    modifier = Modifier.size(10.dp), tint = metaColor)
                                                Spacer(Modifier.width(3.dp))
                                            }
                                            Text(text = timeText, color = metaColor, fontSize = 11.sp)
                                            if (message.isP2pDelivered) {
                                                Spacer(Modifier.width(3.dp))
                                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                                            }
                                            if (isFromMe) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                when (message.deliveryStatus) {
                                                    0 -> Icon(Icons.Default.AccessTime, contentDescription = null,
                                                        tint = metaColor, modifier = Modifier.size(12.dp))
                                                    1 -> Icon(Icons.Default.Done, contentDescription = null,
                                                        tint = metaColor, modifier = Modifier.size(14.dp))
                                                    2 -> Icon(Icons.Default.DoneAll, contentDescription = null,
                                                        tint = metaColor, modifier = Modifier.size(14.dp))
                                                    3 -> Icon(Icons.Default.DoneAll, contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                                    else -> Icon(
                                                        imageVector = if (message.isRead) Icons.Default.DoneAll else Icons.Default.Done,
                                                        contentDescription = null,
                                                        tint = if (message.isRead) MaterialTheme.colorScheme.primary else metaColor,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                // Kompaktes einzeiliges Layout: Text + Zeitstempel in einer Row
                                Row(
                                    modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    val annotated = precomputedItem?.annotatedContent ?: remember(content) { annotateWithLinks(content) }
                                    val urlCtx = LocalContext.current
                                    @Suppress("DEPRECATION")
                                    ClickableText(
                                        text = annotated,
                                        style = TextStyle(color = textColor, fontSize = 15.sp),
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .padding(end = 4.dp),
                                        onClick = { offset ->
                                            annotated.getStringAnnotations("URL", offset, offset)
                                                .firstOrNull()?.let { ann ->
                                                    try { urlCtx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ann.item))) }
                                                    catch (_: Exception) {}
                                                }
                                        }
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (message.isImportant) {
                                            Icon(Icons.Default.Star, contentDescription = null,
                                                modifier = Modifier.size(12.dp), tint = Color(0xFFFFC107))
                                            Spacer(Modifier.width(3.dp))
                                        }
                                        if (message.isEdited) {
                                            Icon(Icons.Default.Edit, contentDescription = "Bearbeitet",
                                                modifier = Modifier.size(10.dp), tint = metaColor)
                                            Spacer(Modifier.width(3.dp))
                                        }
                                        Text(text = timeText, color = metaColor, fontSize = 11.sp)
                                        if (message.isP2pDelivered) {
                                            Spacer(Modifier.width(3.dp))
                                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                                        }
                                        if (isFromMe) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            when (message.deliveryStatus) {
                                                0 -> Icon(Icons.Default.AccessTime, contentDescription = null,
                                                    tint = metaColor, modifier = Modifier.size(12.dp))
                                                1 -> Icon(Icons.Default.Done, contentDescription = null,
                                                    tint = metaColor, modifier = Modifier.size(14.dp))
                                                2 -> Icon(Icons.Default.DoneAll, contentDescription = null,
                                                    tint = metaColor, modifier = Modifier.size(14.dp))
                                                3 -> Icon(Icons.Default.DoneAll, contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                                else -> Icon(
                                                    imageVector = if (message.isRead) Icons.Default.DoneAll else Icons.Default.Done,
                                                    contentDescription = null,
                                                    tint = if (message.isRead) MaterialTheme.colorScheme.primary else metaColor,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                } // end else (short message)
                                } // end else (not code block)
                            }
                        }
                        } // end lethe else
                    }
                }

                // Zeitstempel + Häkchen (nicht bei Sprachnachrichten/Musik oder inline-Text – dort direkt integriert)
                if (message.mediaType != "audio" && message.mediaType != "audio_music" && showExternalTimestamp) Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 6.dp)
                        .then(
                            if (message.mediaType == "circle_video")
                                Modifier
                                    .background(bgColor, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            else Modifier
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isImportant) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color(0xFFFFC107)
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    if (message.isEdited) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Bearbeitet",
                            modifier = Modifier.size(10.dp),
                            tint = metaColor
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(text = timeText, color = metaColor, fontSize = 11.sp)
                    if (message.isP2pDelivered) {
                        Spacer(Modifier.width(3.dp))
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                    }
                    if (isFromMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.deliveryStatus) {
                            0 -> Icon(Icons.Default.AccessTime, contentDescription = null,
                                tint = metaColor, modifier = Modifier.size(12.dp))
                            1 -> Icon(Icons.Default.Done, contentDescription = null,
                                tint = metaColor, modifier = Modifier.size(14.dp))
                            2 -> Icon(Icons.Default.DoneAll, contentDescription = null,
                                tint = metaColor, modifier = Modifier.size(14.dp))
                            3 -> Icon(Icons.Default.DoneAll, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            else -> Icon(
                                imageVector = if (message.isRead) Icons.Default.DoneAll else Icons.Default.Done,
                                contentDescription = null,
                                tint = if (message.isRead) MaterialTheme.colorScheme.primary else metaColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
            } // end Box (gradient wrapper)
        }
        } // end CompositionLocalProvider (fontSizeMultiplier)
        // Reaction-Badge(s) hängen unterhalb der Blase (Column-Layout)
        if (reaction != null) {
            // JSON-Format {"❤️": ["uid1","uid2"]} für Gruppen, sonst einfaches Emoji-String
            val reactionBadges: List<Pair<String, Int>> = remember(reaction) {
                if (reaction.trimStart().startsWith("{")) {
                    try {
                        val type = object : com.google.gson.reflect.TypeToken<Map<String, List<String>>>() {}.type
                        val map: Map<String, List<String>> = com.google.gson.Gson().fromJson(reaction, type)
                        map.mapNotNull { (emoji, users) -> if (users.isNotEmpty()) emoji to users.size else null }
                    } catch (e: Exception) {
                        listOf(reaction to 1)
                    }
                } else {
                    listOf(reaction to 1)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(if (isFromMe) Alignment.Start else Alignment.End)
                    .padding(
                        start = if (isFromMe) 6.dp else 0.dp,
                        end = if (isFromMe) 0.dp else 6.dp,
                        top = 2.dp
                    )
            ) {
                reactionBadges.forEach { (emoji, count) ->
                    Box(contentAlignment = Alignment.TopEnd) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 2.dp
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (count > 1) {
                            Box(
                                modifier = Modifier
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .size(15.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = count.toString(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 8.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        }
        // Schnell-Picker: unterhalb der Blase (wenn Blase zu nah am oberen Rand)
        if (showEmojiBelow) {
            emojiQuickBar()
        }
    }
}

/**
 * Flacher Sprachnachrichten-Player.
 *
 * Layout:  [Avatar+Mic] [▶]  [████▓▒░●░▒▓████]  15:32
 *                             0:45 (Gesamtdauer links unter Waveform)
 *
 * Status-Visualisierung:
 *   - Nicht gelesen  → Mic/Play/Dot in gedimmter Farbe (weiß/grau)
 *   - Gelesen (ds=3) → Mic/Play/Dot in Akzentfarbe (primary)
 */
/**
 * Kompakter Audio-Player für Zitat-Blöcke (ohne Avatar, ohne Timestamp).
 * Zeigt Waveform + Play/Pause + Gesamtzeit in kompakter Form.
 */
/**
 * Hält transienten Audio-Fokus für eine ExoPlayer-Wiedergabe (Video/Circle-Video), solange
 * [active] true ist. Effekt (AUDIOFOCUS_GAIN_TRANSIENT):
 * - Beim Start pausieren ALLE anderen Wiedergaben – andere Lethe-Player (Musik, Sprachnachricht)
 *   ebenso wie fremde Apps (Podcast/Musik).
 * - Nach dem Ende dieser Wiedergabe (onDispose oder active=false) läuft die zuvor pausierte
 *   Wiedergabe automatisch wieder weiter.
 * - Verliert dieser Player selbst den Fokus (z. B. eingehender Anruf), pausiert er; bei Rückgewinn
 *   läuft er weiter.
 */
@Composable
private fun TransientMediaFocus(player: androidx.media3.common.Player, active: Boolean) {
    val focusContext = LocalContext.current
    val focusAudioManager = remember {
        focusContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    val focusRequest = remember(player) {
        android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    android.media.AudioManager.AUDIOFOCUS_LOSS,
                    android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player.pause()
                    android.media.AudioManager.AUDIOFOCUS_GAIN -> player.play()
                }
            }
            .build()
    }
    DisposableEffect(active) {
        if (active) focusAudioManager.requestAudioFocus(focusRequest)
        onDispose { focusAudioManager.abandonAudioFocusRequest(focusRequest) }
    }
}

@Composable
fun QuotedAudioPlayer(
    url: String,
    viewModel: MainViewModel,
    accentColor: Color,
    metaColor: Color,
    isSelectionMode: Boolean = false
) {
    val mediaPlayer = remember(url) { MediaPlayer() }
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var totalSec by remember { mutableStateOf(0) }

    val waveformMap by viewModel.waveformMap.collectAsState()
    val waveformData = waveformMap[url]

    LaunchedEffect(url) { viewModel.loadWaveformForUrl(url) }

    DisposableEffect(url) {
        if (url.isNotBlank()) {
            try {
                mediaPlayer.setDataSource(url)
                mediaPlayer.prepareAsync()
                mediaPlayer.setOnPreparedListener { mp ->
                    totalSec = mp.duration / 1000
                    isPrepared = true
                }
                mediaPlayer.setOnCompletionListener {
                    isPlaying = false
                    progress = 0f
                }
            } catch (_: Exception) {}
        }
        onDispose { mediaPlayer.release() }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(80)
            val dur = mediaPlayer.duration.takeIf { it > 0 } ?: continue
            progress = mediaPlayer.currentPosition.toFloat() / dur
        }
    }

    val colorLow = Color(0xFF666666)
    val colorHigh = Color(0xFFCCCCCC)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play/Pause
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f))
                .clickable {
                    if (isSelectionMode) return@clickable
                    if (!isPrepared) return@clickable
                    if (isPlaying) { mediaPlayer.pause(); isPlaying = false }
                    else { mediaPlayer.start(); isPlaying = true }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(Modifier.width(6.dp))

        // Mini-Waveform
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(22.dp)
                .pointerInput(isPrepared) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!isPrepared) return@awaitEachGesture
                        val doSeek: (androidx.compose.ui.geometry.Offset) -> Unit = { pos ->
                            val fraction = (pos.x / size.width).coerceIn(0f, 1f)
                            mediaPlayer.seekTo((fraction * mediaPlayer.duration).toInt())
                            progress = fraction
                        }
                        doSeek(down.position)
                        drag(down.id) { change -> change.consume(); doSeek(change.position) }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val centerY = h / 2f
            val segs = waveformData?.segmentCount ?: 30
            val amps = waveformData?.amplitudes
            val freqs = waveformData?.frequencies
            val barW = (w / segs) * 0.55f
            for (i in 0 until segs) {
                val x = i * (w / segs) + (w / segs) / 2f
                val amp = amps?.get(i) ?: (0.2f + (i % 5) * 0.12f)
                val barHalf = (amp * centerY * 0.85f).coerceAtLeast(2f)
                val freq = freqs?.get(i) ?: 0.5f
                val barColor = androidx.compose.ui.graphics.lerp(colorLow, colorHigh, freq)
                val isBefore = (i.toFloat() / segs) <= progress
                val finalColor = if (isBefore) androidx.compose.ui.graphics.lerp(barColor, accentColor, 0.5f) else barColor
                drawRoundRect(
                    color = finalColor,
                    topLeft = androidx.compose.ui.geometry.Offset(x - barW / 2f, centerY - barHalf),
                    size = androidx.compose.ui.geometry.Size(barW, barHalf * 2f),
                    cornerRadius = CornerRadius(barW / 2f)
                )
            }
            // Dot
            val dotX = (progress * w).coerceIn(4f, w - 4f)
            drawCircle(color = accentColor.copy(alpha = 0.25f), radius = 5f, center = androidx.compose.ui.geometry.Offset(dotX, centerY))
            drawCircle(color = accentColor, radius = 3.5f, center = androidx.compose.ui.geometry.Offset(dotX, centerY))
        }

        Spacer(Modifier.width(6.dp))

        Text(
            text = "%d:%02d".format(totalSec / 60, totalSec % 60),
            fontSize = 10.sp,
            color = metaColor.copy(alpha = 0.7f)
        )
    }
}

/**
 * Vollständiger Audio-Player für Chat-Blasen.
 * @param url            Audio-URL oder lokaler Pfad
 * @param viewModel      ViewModel (Waveform-Cache)
 * @param accentColor    Primärfarbe der App (primary)
 * @param metaColor      Gedimmte Textfarbe (bubble contrast)
 * @param isFromMe       true = eigene Nachricht
 * @param isRead         true = deliveryStatus == 3
 * @param deliveryStatus 0=PENDING,1=SENT,2=DELIVERED,3=READ
 * @param sentAt         Formatierter Zeitstempel "HH:mm"
 * @param senderAvatarUrl Avatar des Partner-Nutzers (null = Fallback-Icon)
 */
@Composable
fun AudioMessagePlayer(
    url: String,
    viewModel: MainViewModel,
    accentColor: Color,
    metaColor: Color,
    isFromMe: Boolean = false,
    isRead: Boolean = false,
    deliveryStatus: Int = 0,
    sentAt: String = "",
    senderAvatarUrl: String? = null,
    messageId: String? = null,
    groupId: String? = null,
    nextAudioUrl: String? = null,
    isSelectionMode: Boolean = false,
    canLoad: Boolean = true,
    onLoaded: (String) -> Unit = {}
) {
    val context = LocalContext.current
    // ExoPlayer statt MediaPlayer: löst das 0:00-Dauer-Problem bei längeren Sprachnachrichten
    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            if (url.isNotBlank()) {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = false
            }
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var progress by remember { mutableStateOf(0f) }
    var currentSec by remember { mutableStateOf(0) }
    var totalSec by remember { mutableStateOf(0) }
    // Näherungssensor-Wiedergabe-Sitzung:
    // hasBeenNear     = Gerät war während dieser Wiedergabe schon einmal am Ohr
    // pausedByProximity = Wiedergabe wurde pausiert, weil das Gesicht wieder
    //                     wegbewegt wurde (bleibt pausiert bis manueller Start
    //                     oder Ohr erneut ans Display).
    var hasBeenNear by remember(url) { mutableStateOf(false) }
    var pausedByProximity by remember(url) { mutableStateOf(false) }

    val waveformMap by viewModel.waveformMap.collectAsState()
    val waveformData = waveformMap[url]

    // Vollständig-Abgespielt-Status: NUR wenn tatsächlich durchgehört – NICHT wenn Chat geöffnet
    val playedAudioUrls by viewModel.playedAudioUrls.collectAsState()
    val hasBeenPlayed = url in playedAudioUrls

    // Gegenseitiges Stoppen: wenn eine andere Sprachnachricht abgespielt wird, diese pausieren
    val currentlyPlayingAudio by viewModel.currentlyPlayingAudioUrl.collectAsState()

    // Auto-Start/Stop durch currentlyPlayingAudio-Signal
    LaunchedEffect(currentlyPlayingAudio, isPrepared) {
        when {
            currentlyPlayingAudio == url && isPrepared && !isPlaying -> {
                // Manueller (oder Auto-)Start hebt eine evtl. Proximity-Pause auf
                pausedByProximity = false
                exoPlayer.play()
                isPlaying = true
            }
            currentlyPlayingAudio != url && isPlaying -> {
                exoPlayer.pause()
                isPlaying = false
            }
        }
    }

    // Waveform-Generierung nur anstoßen wenn diese Sprachnachricht an der Reihe ist (sequenzielles
    // Laden, siehe canLoad im Aufrufer). Fertig → melden, damit die Sequenz weiterrückt.
    LaunchedEffect(url, canLoad) {
        if (canLoad) viewModel.loadWaveformForUrl(url)
    }
    LaunchedEffect(waveformData != null, url) {
        if (waveformData != null) onLoaded(url)
    }

    LaunchedEffect(playbackSpeed, isPrepared) {
        if (isPrepared) {
            exoPlayer.setPlaybackParameters(androidx.media3.common.PlaybackParameters(playbackSpeed))
        }
    }

    // ── Proximity-Sensor + WakeLock: Ohrmuschel-Routing wie WhatsApp ─────────
    // Xiaomi/POCO/Redmi: PROXIMITY_SCREEN_OFF_WAKE_LOCK und MODE_IN_COMMUNICATION
    // verursachen auf MIUI Audio-Ausfall oder dauerhaftes Routing-Klemmung →
    // für Xiaomi wird das gesamte Proximity-Routing übersprungen.
    val isXiaomiDevice = remember {
        android.os.Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
        android.os.Build.MANUFACTURER.equals("POCO",   ignoreCase = true) ||
        android.os.Build.MANUFACTURER.equals("Redmi",  ignoreCase = true)
    }
    val sensorManager  = remember { context.getSystemService(Context.SENSOR_SERVICE)  as SensorManager }
    val audioManager   = remember { context.getSystemService(Context.AUDIO_SERVICE)   as android.media.AudioManager }
    val powerManager   = remember { context.getSystemService(Context.POWER_SERVICE)   as PowerManager }
    val proximitySensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) }
    // PROXIMITY_SCREEN_OFF_WAKE_LOCK schaltet Display ab und blockiert Touch wenn Sensor auslöst
    // Auf Xiaomi wird dieser WakeLock-Typ nicht erworben (MIUI-Bug: Screen bleibt permanent aus)
    val proximityWakeLock = remember {
        if (isXiaomiDevice) null
        else powerManager.newWakeLock(
            0x00000020, // PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK
            "lethe:audioProximity"
        ).apply { setReferenceCounted(false) }
    }

    // Sensor + WakeLock aktiv solange die Sitzung läuft – also auch während die
    // Wiedergabe wegen Wegbewegen pausiert ist, damit "Ohr wieder ans Display"
    // erkannt wird und fortgesetzt werden kann.
    val proximitySessionActive = isPlaying || pausedByProximity
    @Suppress("DEPRECATION")
    DisposableEffect(proximitySessionActive) {
        if (!proximitySessionActive || proximitySensor == null) return@DisposableEffect onDispose {}

        if (!isXiaomiDevice) {
            if (proximityWakeLock?.isHeld == false) proximityWakeLock.acquire(10 * 60 * 1000L)
        }

        val proximityListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val near = event.values[0] < (proximitySensor.maximumRange / 2f)
                if (near) {
                    hasBeenNear = true
                    // Touch-Eingaben im gesamten Chat sperren, solange das Gerät am Ohr liegt
                    viewModel.setAudioProximityNear(true)
                    if (!isXiaomiDevice) {
                        // Standard-Geräte: Modus auf Sprachkommunikation umschalten
                        audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
                        audioManager.isSpeakerphoneOn = false
                        exoPlayer.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(C.USAGE_VOICE_COMMUNICATION)
                                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                                .build(),
                            false
                        )
                    } else {
                        // Xiaomi-Fix: Nur Lautsprecher deaktivieren, MODE_NORMAL beibehalten
                        audioManager.isSpeakerphoneOn = false
                    }
                    // Ohr wieder ans Display → zuvor wegbewegungs-pausierte Wiedergabe fortsetzen
                    if (pausedByProximity) {
                        pausedByProximity = false
                        exoPlayer.play()
                        isPlaying = true
                        viewModel.requestPlayAudio(url)
                    }
                } else {
                    // Touch-Sperre aufheben sobald das Gesicht weg ist
                    viewModel.setAudioProximityNear(false)
                    // War das Gerät schon am Ohr und läuft die Wiedergabe → pausieren
                    // und pausiert lassen (kein automatisches Umschalten auf Lautsprecher).
                    if (hasBeenNear && isPlaying) {
                        exoPlayer.pause()
                        isPlaying = false
                        pausedByProximity = true
                        viewModel.requestPlayAudio(null)
                    }
                    if (!isXiaomiDevice) {
                        audioManager.mode = android.media.AudioManager.MODE_NORMAL
                        exoPlayer.setAudioAttributes(AudioAttributes.DEFAULT, false)
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        sensorManager.registerListener(
            proximityListener, proximitySensor, SensorManager.SENSOR_DELAY_FASTEST
        )

        onDispose {
            sensorManager.unregisterListener(proximityListener)
            // Sitzung beendet → Touch-Sperre & Proximity-Status zurücksetzen
            viewModel.setAudioProximityNear(false)
            hasBeenNear = false
            pausedByProximity = false
            if (!isXiaomiDevice) {
                if (proximityWakeLock?.isHeld == true) proximityWakeLock.release()
                // AudioManager auf Lautsprecher zurückschalten
                audioManager.mode = android.media.AudioManager.MODE_NORMAL
                exoPlayer.setAudioAttributes(AudioAttributes.DEFAULT, false)
            }
            audioManager.isSpeakerphoneOn = false
        }
    }

    // AudioFocus: Musik pausieren wenn Sprachnachricht abgespielt wird, danach wieder fortsetzen
    val audioFocusRequest = remember {
        android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setOnAudioFocusChangeListener {}
            .build()
    }
    DisposableEffect(isPlaying) {
        if (isPlaying) {
            audioManager.requestAudioFocus(audioFocusRequest)
            onDispose {
                audioManager.abandonAudioFocusRequest(audioFocusRequest)
            }
        } else {
            onDispose {}
        }
    }

    // ExoPlayer-Listener: Dauer zuverlässig nach READY-State lesen
    DisposableEffect(url) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_READY) {
                    val dur = exoPlayer.duration
                    if (dur > 0L) totalSec = (dur / 1000L).toInt()
                    isPrepared = true
                }
                if (state == androidx.media3.common.Player.STATE_ENDED) {
                    isPlaying = false
                    playbackSpeed = 1f
                    exoPlayer.pause()
                    progress = 0f
                    currentSec = 0
                    exoPlayer.seekTo(0)
                    viewModel.markAudioPlayed(url, messageId, groupId)
                    // Nächste ungehörte Sprachnachricht automatisch abspielen (oder stoppen)
                    viewModel.requestPlayAudio(nextAudioUrl)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Fortschritts-Polling (80ms Intervall während Wiedergabe)
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(80)
            val dur = exoPlayer.duration.takeIf { it > 0L } ?: continue
            val pos = exoPlayer.currentPosition
            progress = pos.toFloat() / dur
            currentSec = (pos / 1000L).toInt()
        }
    }

    // Waveform-Farben (aufgehellt für bessere Sichtbarkeit)
    val colorLow  = Color(0xFF707070)
    val colorHigh = Color(0xFFDCDCDC)

    // Gelesen-Indikator: NUR durch tatsächliches Abspielen – isRead wird ignoriert
    val audioIsPlayed = hasBeenPlayed
    val statusColor = if (audioIsPlayed) accentColor else metaColor.copy(alpha = 0.7f)
    val dotColor    = if (audioIsPlayed) accentColor else metaColor.copy(alpha = 0.55f)

    val avatarMult = viewModel.userPrefs.collectAsState().value.avatarSizeMultiplier

    // Avatar + Mikrofon-Badge als wiederverwendbare Lambda
    val avatarBadge: @Composable () -> Unit = {
        Box(
            modifier = Modifier.size((38f * avatarMult).dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Box(
                modifier = Modifier
                    .size((36f * avatarMult).dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(metaColor.copy(alpha = 0.15f))
                    .align(Alignment.TopStart)
                    .clickable(enabled = isPlaying) {
                        playbackSpeed = when (playbackSpeed) {
                            1f    -> 1.25f
                            1.25f -> 1.6f
                            else  -> 1f
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!senderAvatarUrl.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = senderAvatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = metaColor.copy(alpha = 0.6f),
                        modifier = Modifier.size((20f * avatarMult).dp)
                    )
                }
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (playbackSpeed) {
                                1.25f -> "1.25x"
                                1.6f  -> "1.6x"
                                else  -> "1x"
                            },
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(9.dp)
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .widthIn(min = 210.dp, max = 310.dp)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar links nur bei eigenen Nachrichten
        if (isFromMe) {
            avatarBadge()
            Spacer(Modifier.width(6.dp))
        }

        // ── Play / Pause / Laden ─────────────────────────────────────
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.14f))
                .clickable {
                    if (isSelectionMode) return@clickable
                    if (!isPrepared) return@clickable
                    if (isPlaying) {
                        exoPlayer.pause()
                        isPlaying = false
                        viewModel.requestPlayAudio(null)
                    } else {
                        viewModel.requestPlayAudio(url)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (!isPrepared) {
                CircularProgressIndicator(
                    color = statusColor,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Abspielen",
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        // ── Waveform + Gesamtzeit ─────────────────────────────
        val dotRadius = 4.5.dp
        Column(modifier = Modifier.weight(1f)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .pointerInput(isPrepared) {
                        // Tap UND Drag zum Seekbar-Scrubbing
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (!isPrepared) return@awaitEachGesture
                            val dur = exoPlayer.duration.takeIf { it > 0L } ?: return@awaitEachGesture
                            val doSeek: (androidx.compose.ui.geometry.Offset) -> Unit = { pos ->
                                val fraction = (pos.x / size.width).coerceIn(0f, 1f)
                                val seekMs = (fraction * dur).toLong()
                                exoPlayer.seekTo(seekMs)
                                progress = fraction
                                currentSec = (seekMs / 1000L).toInt()
                            }
                            doSeek(down.position)
                            drag(down.id) { change ->
                                change.consume()
                                doSeek(change.position)
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val centerY = h / 2f
                val dotRadiusPx = dotRadius.toPx()

                val segs = waveformData?.segmentCount ?: 40
                val amps = waveformData?.amplitudes
                val freqs = waveformData?.frequencies
                val barWidth = (w / segs) * 0.55f

                for (i in 0 until segs) {
                    val x = i * (w / segs) + (w / segs) / 2f
                    val amp = amps?.get(i) ?: (0.2f + (i % 5) * 0.15f)
                    val barHalf = (amp * centerY * 0.85f).coerceAtLeast(2.5f)
                    val freq = freqs?.get(i) ?: 0.5f
                    val barColor = lerpColor(colorLow, colorHigh, freq)
                    val isBefore = (i.toFloat() / segs) <= progress
                    val finalColor = if (isBefore)
                        lerpColor(barColor, if (audioIsPlayed) accentColor else metaColor, 0.4f)
                    else barColor

                    drawRoundRect(
                        color = finalColor,
                        topLeft = Offset(x - barWidth / 2f, centerY - barHalf),
                        size = Size(barWidth, barHalf * 2f),
                        cornerRadius = CornerRadius(barWidth / 2f)
                    )
                }

                // Positions-Dot
                val dotX = (progress * w).coerceIn(dotRadiusPx, w - dotRadiusPx)
                drawCircle(color = dotColor.copy(alpha = 0.22f), radius = dotRadiusPx + 2f, center = Offset(dotX, centerY))
                drawCircle(color = dotColor, radius = dotRadiusPx, center = Offset(dotX, centerY))
            }

            // Gesamtdauer links
            Text(
                text = "%d:%02d".format(totalSec / 60, totalSec % 60),
                fontSize = 10.sp,
                color = metaColor,
                modifier = Modifier.padding(top = 1.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        // ── Uhrzeit (kein Häkchen) ────────────────────────────
        Text(
            text = sentAt,
            fontSize = 10.sp,
            color = metaColor
        )

        // Avatar rechts bei Partner-Nachrichten
        if (!isFromMe) {
            Spacer(Modifier.width(6.dp))
            avatarBadge()
        }
    }
}

/**
 * Karte für Dokument-Nachrichten (PDF / DOCX / DOC).
 * Zeigt Datei-Icon, Dateiname und einen „Öffnen"-Button.
 */
@Composable
fun DocumentMessageCard(
    contentBlob: String,
    mediaUrl: String,
    accentColor: Color,
    metaColor: Color,
    sentAt: String = "",
    onOpen: () -> Unit
) {
    // Dateiname aus content_blob extrahieren (JSON-Format oder Legacy "[document:name]")
    val fileName = run {
        try { org.json.JSONObject(contentBlob).optString("filename", "").ifBlank { null } }
        catch (_: Exception) { null }
    } ?: if (contentBlob.startsWith("[document:") && contentBlob.endsWith("]"))
        contentBlob.removePrefix("[document:").removeSuffix("]")
    else
        mediaUrl.substringAfterLast('/').ifBlank { "Dokument" }

    val ext = fileName.substringAfterLast('.', "").lowercase()
    val (fileIcon, fileColor) = when (ext) {
        "pdf"  -> Icons.Default.PictureAsPdf to Color(0xFFE53935)
        "docx", "doc" -> Icons.AutoMirrored.Filled.Article to Color(0xFF1565C0)
        "txt", "md"   -> Icons.AutoMirrored.Filled.TextSnippet to Color(0xFF558B2F)
        "kt", "java", "py", "js", "ts", "php", "c", "cpp", "h", "cs", "go", "rs", "html", "htm", "css", "xml", "json" ->
            Icons.Default.Code to Color(0xFF7B1FA2)
        "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip to Color(0xFF6D4C41)
        else   -> Icons.Default.Description to accentColor
    }

    Row(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 310.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Datei-Icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(fileColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(fileIcon, contentDescription = null, tint = fileColor, modifier = Modifier.size(26.dp))
        }

        Spacer(Modifier.width(10.dp))

        // Dateiname + Timestamp
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = metaColor.copy(alpha = 0.95f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sentAt,
                fontSize = 10.sp,
                color = metaColor.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        // Öffnen-Button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.13f))
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Öffnen",
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Music-Player für Audio-Dateien mit ID3-Metadaten (Titel, Artist, Cover).
 * Layout: Cover links | Titel + Artist + Dauer rechts | Seekbar | Play/Stop
 */
@Composable
fun MusicMessagePlayer(
    url: String,
    accentColor: Color,
    metaColor: Color,
    sentAt: String = "",
    viewModel: MainViewModel,
    prevMusicUrl: String? = null,
    nextMusicUrl: String? = null,
    allChatMusicUrls: List<String> = emptyList(),
    isSelectionMode: Boolean = false,
    onDetach: () -> Unit = {}
) {
    val context = LocalContext.current

    // ── Zustand aus ViewModel (überlebt Scroll-Entfernung aus Komposition) ──
    val currentMusicUrl   by viewModel.currentMusicUrl.collectAsState()
    val musicIsPlaying    by viewModel.musicIsPlaying.collectAsState()
    val musicIsPrepared   by viewModel.musicIsPrepared.collectAsState()
    val musicProgress     by viewModel.musicProgress.collectAsState()
    val musicCurrentMs    by viewModel.musicCurrentMs.collectAsState()
    val musicTotalMs      by viewModel.musicTotalMs.collectAsState()
    val musicTitle        by viewModel.musicTitle.collectAsState()
    val musicArtist       by viewModel.musicArtist.collectAsState()
    val musicCoverBitmap  by viewModel.musicCoverBitmap.collectAsState()

    // ── Cast-Verfügbarkeit & Gerätewahl ────────────────────────────────────────
    val castAvailable    by viewModel.castDiscoveryManager.castAvailable.collectAsState()
    val isCasting        by viewModel.castDiscoveryManager.isCasting.collectAsState()
    val castCurrentUrl   by viewModel.castDiscoveryManager.castCurrentUrl.collectAsState()
    val castIsPlaying    by viewModel.castDiscoveryManager.castIsPlaying.collectAsState()
    val castCurrentMs    by viewModel.castDiscoveryManager.castCurrentMs.collectAsState()
    val castTotalMs      by viewModel.castDiscoveryManager.castTotalMs.collectAsState()
    val castUrlTransformed = viewModel.toCastUrl(url)
    val isActivelyCasting = isCasting && (castCurrentUrl == url || castCurrentUrl == castUrlTransformed)

    var showCastStatusDialog by remember { mutableStateOf(false) }
    var showMediaPlayerInstallDialog by remember { mutableStateOf(false) }

    // ── Favorit + Playlist (persoenliche Musikbibliothek) ──────────────────────
    val favoriteMusicUrls by viewModel.favoriteMusicUrls.collectAsState()
    val isFavorite = favoriteMusicUrls.contains(url)
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    var showPlaylistDialog by remember { mutableStateOf(false) }

    // Jedes gesendete/empfangene Musikstueck landet in der eigenen Bibliothek (serverseitig dedupliziert)
    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        val (cachedTitle, cachedArtist) = viewModel.getCachedTitleArtist(url) ?: Pair(null, null)
        viewModel.saveMusicToLibrary(url, cachedTitle, cachedArtist, null)
    }

    // Ist DIESER Player der aktive?
    val isActive = currentMusicUrl == url
    val isPlaying = isActive && musicIsPlaying
    val isPrepared = isActive && musicIsPrepared
    val progress  = if (isActive) musicProgress  else 0f
    val currentMs = if (isActive) musicCurrentMs else 0L
    val totalMs   = if (isActive) musicTotalMs   else 0L

    // Beim Cast-Streaming: effektive Werte vom Chromecast-Receiver verwenden
    val effectiveTotalMs   = if (isActivelyCasting && castTotalMs > 0L) castTotalMs else totalMs
    val effectiveCurrentMs = if (isActivelyCasting) castCurrentMs else currentMs
    val effectiveProgress  = if (effectiveTotalMs > 0L) effectiveCurrentMs.toFloat() / effectiveTotalMs else progress
    val cachedTitleArtist = viewModel.getCachedTitleArtist(url)
    val title     = if (isActive) musicTitle     else cachedTitleArtist?.first?.takeIf { it.isNotBlank() } ?: "Musik"
    val artist    = if (isActive) musicArtist    else cachedTitleArtist?.second?.takeIf { it.isNotBlank() } ?: ""
    val coverBitmap = if (isActive) musicCoverBitmap else viewModel.getCachedMusicCover(url)

    // Waveform (lokal geladen, überlebt nicht den Scroll-Away – daher nur Ästhetik)
    var waveformData by remember(url) {
        mutableStateOf<com.securechat.app.data.local.WaveformData?>(null)
    }
    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        waveformData = withContext(Dispatchers.IO) {
            com.securechat.app.data.local.AudioWaveformAnalyzer.analyze(url, 60)
        }
    }

    fun fmtMs(ms: Long): String {
        val sec = (ms / 1000L).toInt()
        return "%d:%02d".format(sec / 60, sec % 60)
    }

    // Farben für Waveform-Gradient
    val pinkColor = Color(0xFFE91E8C)

    // ── Cast-Status-Dialog ────────────────────────────────────────────────────
    if (showCastStatusDialog) {
        AlertDialog(
            onDismissRequest = { showCastStatusDialog = false },
            icon = { Icon(Icons.Default.Cast, contentDescription = null, tint = accentColor) },
            title = { Text("Stream aktiv", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (castIsPlaying) "▶  Wird gestreamt" else "⏸  Gestreamt · Pause",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (castTotalMs > 0L) {
                        Text(
                            "${fmtMs(castCurrentMs)} / ${fmtMs(castTotalMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCastStatusDialog = false }) {
                    Text("Schließen")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCastStatusDialog = false
                        viewModel.castDiscoveryManager.stopCasting()
                    }
                ) {
                    Text(
                        "Stream beenden",
                        color = accentColor,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        )
    }

    // ── Media-Player nicht installiert: Download-Hinweis ──────────────────────
    if (showMediaPlayerInstallDialog) {
        com.securechat.app.MediaPlayerInstallDialog(
            onDismiss = { showMediaPlayerInstallDialog = false }
        )
    }

    // ── Playlist-Dialog: bestehende Playlist waehlen oder neue anlegen ─────────
    if (showPlaylistDialog) {
        var newPlaylistName by remember { mutableStateOf("") }
        val playTimeSec = (effectiveTotalMs / 1000L).toInt().takeIf { it > 0 }
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            icon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = accentColor) },
            title = { Text("Zu Playlist hinzufügen", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (userPlaylists.isNotEmpty()) {
                        Text(
                            "Vorhandene Playlists",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        userPlaylists.forEach { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.addMusicToPlaylist(url, title, artist, playTimeSec, pl.playlistId, pl.playlistName)
                                        showPlaylistDialog = false
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(pl.playlistName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text("${pl.trackCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        "Neue Playlist",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("Name der Playlist") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newPlaylistName.isNotBlank(),
                    onClick = {
                        viewModel.addMusicToPlaylist(url, title, artist, playTimeSec, null, newPlaylistName.trim())
                        showPlaylistDialog = false
                    }
                ) { Text("Anlegen") }
            },
            dismissButton = {
                TextButton(onClick = { showPlaylistDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    // ── Karte ────────────────────────────────────────────────────────────────
    Card(
        modifier = Modifier.widthIn(min = 240.dp, max = 310.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box {
            Column(
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 2.dp)
            ) {
                // ── Obere Zeile: Cover + Titel/Artist + Zeitstempel ──────────
                Row(verticalAlignment = Alignment.CenterVertically) {

                    // Cover = Play/Pause-Button (lokal oder Cast)
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2A2A4A))
                            .clickable {
                                if (isSelectionMode) return@clickable
                                if (isActivelyCasting) {
                                    viewModel.castDiscoveryManager.castPlayPause()
                                } else {
                                    viewModel.toggleMusicPlayback(url, allChatMusicUrls)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val bm = coverBitmap
                        if (bm != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bm.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        // Overlay: bei aktivem Cast Cast-Icon, sonst Play wenn pausiert
                        if (isActivelyCasting) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (castIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (castIsPlaying) "Cast pausieren" else "Cast abspielen",
                                    tint = Color(0xFF4FC3F7),
                                    modifier = Modifier.size(28.dp)
                                )
                                // Cast-Indikator unten rechts
                                Icon(
                                    Icons.Default.Cast,
                                    contentDescription = null,
                                    tint = Color(0xFF4FC3F7).copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .size(14.dp)
                                        .align(Alignment.BottomEnd)
                                        .padding(2.dp)
                                )
                            }
                        } else if (!isPlaying) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Abspielen",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // Titel + Artist-Zeile mit Zeitstempel
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        // Cast-Status-Badge wenn aktiv gecastet
                        if (isActivelyCasting) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Cast,
                                    contentDescription = null,
                                    tint = Color(0xFF4FC3F7),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = if (castIsPlaying) "Wird gestreamt" else "Gestreamt · Pause",
                                    fontSize = 10.sp,
                                    color = Color(0xFF4FC3F7),
                                    maxLines = 1
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                        }
                        // Artist + Zeitstempel auf gleicher Höhe
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = artist,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.55f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (sentAt.isNotBlank()) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = sentAt,
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.40f)
                                )
                            }
                        }
                    }

                    // ── Herz (Favorit) + Plus (zu Playlist) ──────────────────
                    if (!isSelectionMode) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            // Wenn ein Cast-Gerät verfügbar ist, liegt oben rechts das Cast-Symbol –
                            // die Icon-Spalte nach unten rücken, damit das Herz es nicht verdeckt.
                            modifier = Modifier
                                .align(Alignment.Top)
                                .padding(top = if (castAvailable) 20.dp else 0.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    viewModel.toggleMusicFavorite(url, title, artist, (effectiveTotalMs / 1000L).toInt().takeIf { it > 0 })
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (isFavorite) "Favorit entfernen" else "Favorisieren",
                                    tint = if (isFavorite) Color(0xFFE91E8C) else Color.White.copy(alpha = 0.70f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.loadUserPlaylists()
                                    showPlaylistDialog = true
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlaylistAdd,
                                    contentDescription = "Zu Playlist hinzufügen",
                                    tint = Color.White.copy(alpha = 0.70f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Waveform + Zeit + Skip-Buttons ───────────────────────────
                val wf = waveformData
                val barCount = wf?.segmentCount ?: 60

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // ◀ Zurück-Button (links von der aktuellen Zeit)
                    IconButton(
                        onClick = {
                            if (isActivelyCasting) {
                                viewModel.castDiscoveryManager.castQueuePrev()
                            } else {
                                if (prevMusicUrl != null) viewModel.skipToPrevMusic()
                            }
                        },
                        modifier = Modifier.size(28.dp),
                        enabled = prevMusicUrl != null || (!isActivelyCasting && isActive && currentMs > 3_000L)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Vorheriges Lied",
                            tint = if (prevMusicUrl != null || (!isActivelyCasting && isActive && currentMs > 3_000L))
                                Color.White.copy(alpha = 0.80f)
                            else Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Aktuelle Position
                    Text(
                        text = fmtMs(effectiveCurrentMs),
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )

                    Spacer(Modifier.width(4.dp))

                    // Waveform-Canvas
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .pointerInput(isPrepared, isActivelyCasting, wf) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    if (!isPrepared && !isActivelyCasting) return@awaitEachGesture
                                    val doSeek: (Offset) -> Unit = { pos ->
                                        val fraction = (pos.x / size.width).coerceIn(0f, 1f)
                                        val seekMs = (fraction * effectiveTotalMs).toLong()
                                        if (isActivelyCasting) {
                                            viewModel.castDiscoveryManager.castSeekTo(seekMs)
                                        } else {
                                            viewModel.seekMusicTo(seekMs)
                                        }
                                    }
                                    doSeek(down.position)
                                    drag(down.id) { change ->
                                        change.consume()
                                        doSeek(change.position)
                                    }
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val totalW  = size.width
                            val totalH  = size.height
                            val gap     = 2.dp.toPx()
                            val barW    = ((totalW - gap * (barCount - 1)) / barCount).coerceAtLeast(1f)
                            val centerY = totalH / 2f
                            val playedIdx = (effectiveProgress * barCount).toInt().coerceIn(0, barCount)

                            for (i in 0 until barCount) {
                                val amp    = if (wf != null) wf.amplitudes[i] else 0.3f
                                val barH   = (totalH * amp.coerceIn(0.05f, 1f)).coerceAtLeast(4f)
                                val left   = i * (barW + gap)
                                val top    = centerY - barH / 2f
                                val played = i < playedIdx

                                val t = i.toFloat() / (barCount - 1).coerceAtLeast(1)
                                val baseColor = if (t < 0.5f) {
                                    lerpColor(pinkColor, accentColor, t * 2f)
                                } else {
                                    lerpColor(accentColor, pinkColor, (t - 0.5f) * 2f)
                                }
                                val barColor = if (played) baseColor else baseColor.copy(alpha = 0.28f)

                                drawRoundRect(
                                    color = barColor,
                                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                    size   = Size(barW, barH),
                                    cornerRadius = CornerRadius(barW / 2f)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(4.dp))

                    // Gesamtdauer
                    Text(
                        text = fmtMs(effectiveTotalMs),
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )

                    // ▶ Weiter-Button (rechts von der Gesamtzeit)
                    IconButton(
                        onClick = {
                            if (isActivelyCasting) {
                                viewModel.castDiscoveryManager.castQueueNext()
                            } else {
                                viewModel.skipToNextMusic()
                            }
                        },
                        modifier = Modifier.size(28.dp),
                        enabled = nextMusicUrl != null
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Nächstes Lied",
                            tint = if (nextMusicUrl != null)
                                Color.White.copy(alpha = 0.80f)
                            else Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ── Chatblasenfarbe unten-links durchscheinend ──────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(72.dp)
                    .height(38.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(accentColor.copy(alpha = 0.22f), Color.Transparent),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(220f, 0f)
                        )
                    )
            )

            // ── Pfeil-nach-oben oben links: Musikplayer ablösen (Mini-Player) ───
            if (!isSelectionMode) {
                IconButton(
                    onClick = { onDetach() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(32.dp)
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = "Musikplayer ablösen",
                        tint = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // ── Cast-Button oben rechts: öffnet den Lethe Media Player mit der vollständigen
            //    Stream-URL, damit dieser das Lied streamt (ID3-Tags) und von dort gecastet wird ──
            IconButton(
                onClick = {
                    if (!com.securechat.app.MediaPlayerLauncher.openWithStreamUrl(context, viewModel.toCastUrl(url))) {
                        showMediaPlayerInstallDialog = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
                    .padding(4.dp)
            ) {
                Icon(
                    Icons.Default.Cast,
                    contentDescription = "Im Lethe Media Player casten",
                    tint = if (isActivelyCasting) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun PollMessageCard(
    pollId: String,
    viewModel: MainViewModel,
    textColor: Color,
    bubbleColor: Color
) {
    val currentPoll by viewModel.currentPoll.collectAsState()

    LaunchedEffect(pollId) {
        viewModel.loadPoll(pollId)
    }

    val poll = if (currentPoll?.pollId == pollId) currentPoll else null

    Column(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 280.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (poll == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Poll, contentDescription = null,
                    tint = textColor.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Umfrage wird geladen…", color = textColor.copy(alpha = 0.7f), fontSize = 13.sp)
            }
            return@Column
        }

        // Frage
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Poll, contentDescription = null,
                tint = bubbleColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(poll.question, fontWeight = FontWeight.Bold, color = textColor, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))

        // Optionen mit Ergebnisbalken
        val options = try {
            val arr = org.json.JSONArray(poll.optionsJson)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { emptyList() }

        val results = try {
            val arr = org.json.JSONArray(poll.resultsJson)
            (0 until arr.length()).associate { i ->
                val obj = arr.getJSONObject(i)
                obj.getInt("index") to obj.getInt("vote_count")
            }
        } catch (e: Exception) { emptyMap() }

        val totalVotes = results.values.sum().coerceAtLeast(1)

        options.forEachIndexed { index, optionText ->
            val votes = results[index] ?: 0
            val fraction = votes.toFloat() / totalVotes
            val isUserVote = poll.userVote == index

            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUserVote) "✓ $optionText" else optionText,
                        fontSize = 13.sp,
                        color = if (isUserVote) bubbleColor else textColor,
                        fontWeight = if (isUserVote) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    Text("$votes", fontSize = 11.sp, color = textColor.copy(alpha = 0.6f))
                }
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = if (isUserVote) bubbleColor else textColor.copy(alpha = 0.4f),
                    trackColor = textColor.copy(alpha = 0.12f)
                )
            }
        }

        // Abstimmen wenn noch nicht getan
        if (poll.userVote < 0 && options.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tippe auf eine Option zum Abstimmen",
                fontSize = 11.sp,
                color = textColor.copy(alpha = 0.55f)
            )
            options.forEachIndexed { index, optionText ->
                TextButton(
                    onClick = { viewModel.voteOnPoll(pollId, index) },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                ) {
                    Text(optionText, fontSize = 13.sp, color = bubbleColor)
                }
            }
        }
    }
}

/**
 * Zeigt ein Video-Vorschaubild an.
 *
 * Strategie (Priorität):
 * 1. ViewModel-Cache (by URL) – für eigene Videos nach Transkodierung sofort verfügbar
 * 2. Netzwerk-Laden via MediaMetadataRetriever (für empfangene Videos)
 *
 * @param url        Server-URL des Videos
 * @param viewModel  Zugang zum Thumbnail-Cache
 * @param modifier   Compose Modifier
 * @param onAspectRatio Callback wenn das Seitenverhältnis bekannt ist
 */
@Composable
fun VideoThumbnailImage(
    url: String,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onAspectRatio: (Float) -> Unit = {},
    canLoad: Boolean = true,
    onLoaded: (String) -> Unit = {}
) {
    val thumbnailMap by viewModel.videoThumbnailMap.collectAsState()
    val failedUrls by viewModel.failedThumbnailUrls.collectAsState()

    val isFailed = url in failedUrls

    // Netzwerk-Laden nur anstoßen wenn noch kein Cache-Treffer, nicht bereits fehlgeschlagen und
    // dieses Video an der Reihe ist (sequenzielles Laden, siehe canLoad im Aufrufer).
    LaunchedEffect(url, isFailed, canLoad) {
        if (url.isNotEmpty() && thumbnailMap[url] == null && !isFailed && canLoad) {
            viewModel.loadVideoThumbnailFromUrl(url)
        }
    }

    // displayBitmap reaktiv: aktualisiert sich wenn loadVideoThumbnailFromUrl fertig ist
    val displayBitmap = thumbnailMap[url]
    val isLoading = displayBitmap == null && url.isNotEmpty() && !isFailed

    // Fertig (Erfolg oder Fehlschlag) → melden, damit die Sequenz weiterrückt
    LaunchedEffect(displayBitmap != null, isFailed, url) {
        if (displayBitmap != null || isFailed) onLoaded(url)
    }

    // Seitenverhältnis melden sobald Bitmap da
    LaunchedEffect(displayBitmap) {
        val bmp = displayBitmap ?: return@LaunchedEffect
        if (bmp.width > 0 && bmp.height > 0) {
            onAspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
        }
    }

    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (displayBitmap != null) {
            Image(
                bitmap = displayBitmap.asImageBitmap(),
                contentDescription = "Video-Vorschau",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Play-Icon nur sichtbar wenn Thumbnail geladen ist
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "Video abspielen",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(52.dp)
                )
            }
        } else if (isFailed) {
            // Thumbnail fehlgeschlagen → Play-Icon anzeigen damit Video trotzdem abspielbar ist
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1C1C1C)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "Video abspielen",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(52.dp)
                )
            }
        } else if (isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tippe zum Abspielen",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

// ======================================================================
// Medien-Galerie Dialog
// ======================================================================

// Parst die JSON-URL-Liste einer "multi_image"-Nachricht (mediaType == "multi_image").
// Fallback für alte/korrumpierte Nachrichten deckt einen früheren Bug ab, bei dem
// "https://letheapp.de" versehentlich vor das JSON-Array geschrieben wurde.
private fun parseMultiImageUrls(json: String): List<String> {
    fun parseUrls(str: String): List<String>? = try {
        val arr = org.json.JSONArray(str)
        List(arr.length()) { arr.getString(it) }
            .map { if (it.startsWith("http")) it else "https://letheapp.de$it" }
            .takeIf { it.isNotEmpty() }
    } catch (_: Exception) { null }
    return parseUrls(json) ?: parseUrls(json.removePrefix("https://letheapp.de")) ?: emptyList()
}

// Ein einzelnes Foto/Video-Kachel-Eintrag in der Medien-Galerie. Bei "multi_image"-
// Nachrichten wird EINE MessageEntity in mehrere GalleryEntry (eins pro enthaltenem
// Bild) aufgeteilt, statt die rohe JSON-URL-Liste als ein einzelnes (nicht render-
// bares) Bild anzuzeigen.
private data class GalleryEntry(val message: MessageEntity, val url: String?, val entryKey: String)

private fun buildGalleryEntries(items: List<MessageEntity>): List<GalleryEntry> =
    items.flatMap { m ->
        if (m.mediaType == "multi_image") {
            val urls = m.mediaUrl?.let { parseMultiImageUrls(it) } ?: emptyList()
            if (urls.isNotEmpty()) {
                urls.mapIndexed { idx, u -> GalleryEntry(m, u, "${m.localId}_$idx") }
            } else {
                listOf(GalleryEntry(m, null, "${m.localId}_0"))
            }
        } else {
            listOf(GalleryEntry(m, m.mediaUrl, m.localId.toString()))
        }
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ChatMediaDialog(
    messages: List<MessageEntity>,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val groupedMedia = remember(messages) {
        val map = linkedMapOf(
            "Fotos & Videos" to messages.filter { it.mediaType in listOf("image", "multi_image", "video") },
            "Sticker/GIFs"   to messages.filter { it.mediaType in listOf("sticker", "gif") },
            "Audios"         to messages.filter { it.mediaType in listOf("audio", "audio_music") },
            "Dokumente"      to messages.filter { it.mediaType in listOf("document", "file", "pdf") },
            "3D Dateien"     to messages.filter { it.mediaType in listOf("3dprint", "3d") }
        )
        map.filter { (_, items) -> items.isNotEmpty() }
    }
    val tabs = groupedMedia.keys.toList()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { tabs.size }
    val tabScope = rememberCoroutineScope()
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var fullscreenVideoUrl by remember { mutableStateOf<String?>(null) }
    var mediaActionMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var mediaActionUrl by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text("Medien", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = topBarTitleColor(),
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    if (tabs.size > 1) {
                        ScrollableTabRow(
                            selectedTabIndex = pagerState.currentPage,
                            edgePadding = 0.dp,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            tabs.forEachIndexed { index, tabName ->
                                val count = groupedMedia[tabName]?.size ?: 0
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = { tabScope.launch { pagerState.animateScrollToPage(index) } },
                                    text = {
                                        Text(
                                            "$tabName ($count)",
                                            maxLines = 1,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PermMedia,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = Color.Gray.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Keine Medien in diesem Chat", color = Color.Gray)
                    }
                }
            } else if (tabs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Keine Medien in diesem Chat", color = Color.Gray)
                }
            } else {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) { page ->
                    val tabName = tabs.getOrNull(page) ?: return@HorizontalPager
                    val items = groupedMedia[tabName] ?: emptyList()

                    if (tabName == "Fotos & Videos" || tabName == "Sticker/GIFs") {
                        // 3-spaltiges Grid. "multi_image"-Nachrichten werden in einzelne
                        // GalleryEntry (ein Eintrag pro enthaltenem Bild) aufgesplittet.
                        val galleryEntries = remember(items) { buildGalleryEntries(items) }
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            lazyGridItems(items = galleryEntries, key = { it.entryKey }) { entry ->
                                val msg = entry.message
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            mediaActionMessage = msg
                                            mediaActionUrl = entry.url
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (msg.mediaType) {
                                        "image", "multi_image", "sticker", "gif" -> Image(
                                            painter = rememberAsyncImagePainter(entry.url),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        "video" -> {
                                            VideoThumbnailImage(
                                                url = msg.mediaUrl ?: "",
                                                viewModel = viewModel,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Icon(
                                                Icons.Default.PlayCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(32.dp).align(Alignment.Center),
                                                tint = Color.White.copy(alpha = 0.85f)
                                            )
                                        }
                                        else -> Icon(
                                            Icons.Default.PermMedia,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Audio, Musik, Dokumente, 3D: als scrollbare Liste
                        val musicItems = if (tabName == "Audios") items.filter { it.mediaType == "audio_music" } else emptyList()
                        var allMusicDownloadStarted by remember { mutableStateOf(false) }
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (musicItems.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            val dm = context.getSystemService(android.app.DownloadManager::class.java)
                                            musicItems.forEach { msg ->
                                                val url = msg.mediaUrl ?: return@forEach
                                                val fileName = url.substringAfterLast("/").ifBlank { "musik_${msg.timestamp}.mp3" }
                                                val ext = if (!fileName.contains('.')) ".mp3" else ""
                                                val req = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                                                    .setTitle(fileName)
                                                    .setDescription("Lethe-Musik-Download")
                                                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MUSIC, "Lethe/${fileName}${ext}")
                                                    .setAllowedOverMetered(true)
                                                dm.enqueue(req)
                                            }
                                            allMusicDownloadStarted = true
                                        }
                                    ) {
                                        Icon(
                                            if (allMusicDownloadStarted) Icons.Default.CheckCircle else Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = if (allMusicDownloadStarted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            if (allMusicDownloadStarted) "Alle werden geladen…" else "Alle ${musicItems.size} Musikdateien speichern",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(items = items, key = { it.localId }) { msg ->
                                    MediaGalleryItem(message = msg, viewModel = viewModel)
                                    HorizontalDivider(Modifier.padding(start = 80.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Fullscreen-Bildbetrachter
    val fsImageUrl = fullscreenImageUrl
    if (fsImageUrl != null) {
        Dialog(
            onDismissRequest = { fullscreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
        ) {
            var zoomScale by remember { mutableStateOf(1f) }
            var zoomOffset by remember { mutableStateOf(Offset.Zero) }
            val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
                val newScale = (zoomScale * zoomChange).coerceIn(1f, 6f)
                zoomScale = newScale
                zoomOffset = if (newScale > 1f) zoomOffset + offsetChange * 2.5f else Offset.Zero
            }
            val fsImgChatId = messages.firstOrNull {
                it.mediaUrl == fsImageUrl ||
                    (it.mediaType == "multi_image" && it.mediaUrl?.let { j -> fsImageUrl in parseMultiImageUrls(j) } == true)
            }?.chatId ?: ""
            LaunchedEffect(fsImageUrl) { viewModel.exportImageToPictures(fsImageUrl, fsImgChatId) }
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val fsImgCtx = LocalContext.current
                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(fsImgCtx)
                            .data(viewModel.getPublicMediaUri(fsImageUrl, false) ?: fsImageUrl)
                            // Max. 2048px statt ORIGINAL: verhindert das Dekodieren
                            // eines 12-MP-Fotos (~48 MB) im Vollbild → OOM.
                            .size(2048, 2048)
                            // Scale.FIT (statt Coil-Default FILL): bei einem hohen Bild
                            // (z.B. langer Screenshot) skaliert FILL auf die Breite →
                            // resultierende Bitmap-Höhe überschreitet GL_MAX_TEXTURE_SIZE →
                            // nur der obere Teil wird als Textur gezeichnet, Rest schwarz.
                            // FIT cappt die größere Kante auf 2048 → ganzes Bild sichtbar.
                            .scale(coil.size.Scale.FIT)
                            .build()
                    ),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = zoomOffset.x
                            translationY = zoomOffset.y
                        }
                        .transformable(
                            state = transformableState,
                            canPan = { zoomScale > 1f }
                        ),
                    contentScale = ContentScale.Fit
                )
                var showPhotoMenu by remember { mutableStateOf(false) }
                val fsShareScope = rememberCoroutineScope()
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        android.widget.Toast.makeText(context, "Wird vorbereitet…", android.widget.Toast.LENGTH_SHORT).show()
                        fsShareScope.launch {
                            val ok = quickShareMediaFile(context, fsImageUrl, "image")
                            if (!ok) android.widget.Toast.makeText(context, "Teilen fehlgeschlagen", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.IosShare, contentDescription = "Teilen", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Box {
                        IconButton(onClick = { showPhotoMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menü", tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        DropdownMenu(expanded = showPhotoMenu, onDismissRequest = { showPhotoMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("In Galerie speichern") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    showPhotoMenu = false
                                    val name = fsImageUrl.substringAfterLast('/', "photo.jpg").let {
                                        if ('.' in it) it else "$it.jpg"
                                    }
                                    val req = DownloadManager.Request(android.net.Uri.parse(fsImageUrl))
                                        .setTitle(name)
                                        .setDescription("Wird in Galerie gespeichert…")
                                        .setMimeType("image/jpeg")
                                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_PICTURES, "Lethe/$name")
                                        .setAllowedOverMetered(true)
                                    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                                }
                            )
                        }
                    }
                    IconButton(onClick = { fullscreenImageUrl = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }

    // In-App-Videoplayer
    val fsVideoUrl = fullscreenVideoUrl
    if (fsVideoUrl != null) {
        Dialog(
            onDismissRequest = { fullscreenVideoUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val fsChatId = messages.firstOrNull { it.mediaUrl == fsVideoUrl }?.chatId ?: ""
            val exoPlayer = remember {
                val publicUri = viewModel.getPublicMediaUri(fsVideoUrl, true)
                val playUri = when {
                    publicUri != null -> publicUri
                    else -> {
                        val localPath = viewModel.getCachedVideoPath(fsVideoUrl, fsChatId)
                        if (localPath != null) android.net.Uri.fromFile(java.io.File(localPath))
                        else { viewModel.ensureVideoCached(fsVideoUrl, fsChatId); android.net.Uri.parse(fsVideoUrl) }
                    }
                }
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(playUri))
                    prepare()
                    playWhenReady = true
                    addListener(object : androidx.media3.common.Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (!isPlaying) viewModel.exportVideoToMovies(fsVideoUrl, fsChatId)
                        }
                    })
                }
            }
            DisposableEffect(exoPlayer) {
                onDispose {
                    viewModel.exportVideoToMovies(fsVideoUrl, fsChatId)
                    exoPlayer.release()
                }
            }
            // Andere Wiedergaben (Musik/fremde Apps) pausieren, danach fortsetzen
            TransientMediaFocus(exoPlayer, active = true)
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                var showVideoMenu by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconButton(onClick = { showVideoMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menü", tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        DropdownMenu(expanded = showVideoMenu, onDismissRequest = { showVideoMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("In Galerie speichern") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    showVideoMenu = false
                                    val name = fsVideoUrl.substringAfterLast('/', "video.mp4").let {
                                        if ('.' in it) it else "$it.mp4"
                                    }
                                    val req = DownloadManager.Request(android.net.Uri.parse(fsVideoUrl))
                                        .setTitle(name)
                                        .setDescription("Wird in Galerie gespeichert…")
                                        .setMimeType("video/mp4")
                                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MOVIES, "Lethe/$name")
                                        .setAllowedOverMetered(true)
                                    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                                }
                            )
                        }
                    }
                    IconButton(onClick = { fullscreenVideoUrl = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }

    // Medien-Aktions-Sheet: Speichern / Teilen / Weiterleiten
    val actionMsg = mediaActionMessage
    if (actionMsg != null) {
        MediaActionSheet(
            message = actionMsg,
            viewModel = viewModel,
            onDismiss = { mediaActionMessage = null },
            onViewFullscreen = { msg ->
                mediaActionMessage = null
                when (msg.mediaType) {
                    "image", "sticker", "gif" -> fullscreenImageUrl = msg.mediaUrl
                    "multi_image" -> fullscreenImageUrl = mediaActionUrl
                        ?: msg.mediaUrl?.let { parseMultiImageUrls(it).firstOrNull() }
                    "video" -> fullscreenVideoUrl = msg.mediaUrl
                }
            }
        )
    }
}

// ======================================================================
// Medien-Aktions-Sheet: Speichern / Teilen / Weiterleiten
// ======================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaActionSheet(
    message: MessageEntity,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onViewFullscreen: (MessageEntity) -> Unit = {}
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val contacts by viewModel.contacts.collectAsState(initial = emptyList())
    var showForwardSheet by remember { mutableStateOf(false) }
    val quickShareScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Vorschau-Zeile
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onViewFullscreen(message) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val previewIcon = when (message.mediaType) {
                    "image", "multi_image", "sticker", "gif" -> Icons.Default.Image
                    "video"                                   -> Icons.Default.PlayCircle
                    "audio", "audio_music"                   -> Icons.Default.Mic
                    "document", "file", "pdf"                -> Icons.Default.Description
                    "3dprint", "3d"                           -> Icons.Default.ViewInAr
                    else                                      -> Icons.Default.PermMedia
                }
                Icon(previewIcon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                val label = when (message.mediaType) {
                    "image", "multi_image" -> "Foto"
                    "video"               -> "Video"
                    "audio"               -> "Sprachnachricht"
                    "audio_music"         -> "Musik"
                    "sticker"             -> "Sticker"
                    "gif"                 -> "GIF"
                    "document", "file", "pdf" -> "Dokument"
                    "3dprint", "3d"       -> "3D-Datei"
                    else                  -> "Datei"
                }
                Text(label, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }

            Spacer(Modifier.height(16.dp))

            // Speichern
            val url = message.mediaUrl
            if (url != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val fileName = url.substringAfterLast('/').let {
                                if ('.' in it) it else "$it.bin"
                            }
                            val req = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                                .setTitle(fileName)
                                .setDescription("Wird gespeichert…")
                                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setDestinationInExternalPublicDir(
                                    android.os.Environment.DIRECTORY_DOWNLOADS, "Lethe/$fileName"
                                )
                                .setAllowedOverMetered(true)
                            (context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager).enqueue(req)
                            onDismiss()
                        }
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Download, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Speichern", fontSize = 16.sp)
                }

                HorizontalDivider(modifier = Modifier.padding(start = 48.dp))

                // Quick Share – teilt die echte Mediendatei (statt nur den Link)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            android.widget.Toast.makeText(
                                context, "Wird vorbereitet…", android.widget.Toast.LENGTH_SHORT
                            ).show()
                            quickShareScope.launch {
                                val ok = quickShareMediaFile(context, url, message.mediaType)
                                if (!ok) android.widget.Toast.makeText(
                                    context, "Teilen fehlgeschlagen", android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                            onDismiss()
                        }
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.IosShare, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Quick Share", fontSize = 16.sp)
                }

                HorizontalDivider(modifier = Modifier.padding(start = 48.dp))

                // Teilen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            // Audio/Musik als echte Datei teilen (audio/* + EXTRA_STREAM),
                            // damit Upload-Ziele wie SoundCloud im Share-Sheet erscheinen.
                            // Sonstige Medien: nur den Link teilen.
                            if (message.mediaType == "audio_music" || message.mediaType == "audio") {
                                android.widget.Toast.makeText(
                                    context, "Wird vorbereitet…", android.widget.Toast.LENGTH_SHORT
                                ).show()
                                quickShareScope.launch {
                                    val ok = quickShareMediaFile(context, url, message.mediaType)
                                    if (!ok) android.widget.Toast.makeText(
                                        context, "Teilen fehlgeschlagen", android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, url)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Teilen"))
                            }
                            onDismiss()
                        }
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Share, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Teilen", fontSize = 16.sp)
                }

                HorizontalDivider(modifier = Modifier.padding(start = 48.dp))
            }

            // Weiterleiten
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showForwardSheet = true }
                    .padding(vertical = 14.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text("Weiterleiten", fontSize = 16.sp)
            }
        }
    }

    if (showForwardSheet) {
        val groups by viewModel.groups.collectAsState(initial = emptyList())
        val frequencyOrder by viewModel.chatIdsSortedByFrequency.collectAsState(initial = emptyList())
        val pinnedContactIds by viewModel.pinnedContactIds.collectAsState()
        val pinnedGroupIds by viewModel.pinnedGroupIds.collectAsState()
        ForwardSheet(
            contacts = contacts,
            groups = groups,
            frequencyOrder = frequencyOrder,
            pinnedContactIds = pinnedContactIds,
            pinnedGroupIds = pinnedGroupIds,
            onForwardTo = { targetId ->
                message.mediaUrl?.let { url ->
                    viewModel.forwardMediaMessage(targetId, url, message.mediaType)
                }
                showForwardSheet = false
                onDismiss()
            },
            onDismiss = { showForwardSheet = false }
        )
    }
}

/**
 * Lädt die Mediendatei von [url] herunter und teilt sie als echte Datei (Quick Share /
 * System-Share-Sheet) – nicht nur den Link. Funktioniert für Bilder, Videos, Musik und
 * sonstige Dateien. Gibt true bei Erfolg zurück.
 */
private suspend fun quickShareMediaFile(
    context: Context,
    url: String,
    mediaType: String?
): Boolean = withContext(Dispatchers.IO) {
    try {
        val rawName = url.substringAfterLast('/').substringBefore('?')
        val ext = rawName.substringAfterLast('.', "").lowercase()
        val mime = (if (ext.isNotEmpty())
            android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) else null)
            ?: when (mediaType) {
                "image", "multi_image", "sticker" -> "image/*"
                "gif"                              -> "image/gif"
                "video"                            -> "video/*"
                "audio", "audio_music"            -> "audio/*"
                "document", "file", "pdf"         -> "application/pdf"
                else                               -> "application/octet-stream"
            }
        val fileName = if ('.' in rawName && rawName.isNotBlank()) {
            rawName
        } else {
            val guessExt = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            val base = rawName.ifBlank { "media_${System.currentTimeMillis()}" }
            if (guessExt != null) "$base.$guessExt" else "$base.bin"
        }
        val shareDir = java.io.File(context.cacheDir, "quickshare").apply { mkdirs() }
        val outFile = java.io.File(shareDir, fileName)
        val fullUrl = if (url.startsWith("http")) url else "https://letheapp.de$url"
        val conn = (java.net.URL(fullUrl).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
        }
        conn.inputStream.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        conn.disconnect()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Quick Share"))
        }
        true
    } catch (e: Exception) {
        Timber.e(e, "quickShareMediaFile failed")
        false
    }
}

// ======================================================================
// Kontakt-Profilanzeige (Avatar + Social Media + Medien-Tabs)
// ======================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ContactProfileDialog(
    contact: com.securechat.app.data.local.ContactEntity,
    messages: List<MessageEntity>,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onForwardMessage: (MessageEntity) -> Unit = {}
) {
    val context = LocalContext.current

    var remoteLinks by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(contact.userId) {
        remoteLinks = viewModel.getUserProfile(contact.userId)?.links
    }

    val groupedMedia = remember(messages) {
        val map = linkedMapOf(
            "Fotos & Videos" to messages.filter { it.mediaType in listOf("image", "multi_image", "video") },
            "Sticker/GIFs"   to messages.filter { it.mediaType in listOf("sticker", "gif") },
            "Audios"         to messages.filter { it.mediaType in listOf("audio", "audio_music") },
            "Dokumente"      to messages.filter { it.mediaType in listOf("document", "file", "pdf") },
            "3D Dateien"     to messages.filter { it.mediaType in listOf("3dprint", "3d") }
        )
        map.filter { (_, items) -> items.isNotEmpty() }
    }
    val tabs = groupedMedia.keys.toList()
    var selectedTab by remember { mutableIntStateOf(0) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { tabs.size }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var fullscreenVideoUrl by remember { mutableStateOf<String?>(null) }
    var mediaActionMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var mediaActionUrl by remember { mutableStateOf<String?>(null) }
    var showEnlargedAvatar by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        if (selectedTab != pagerState.currentPage) selectedTab = pagerState.currentPage
    }
    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) pagerState.animateScrollToPage(selectedTab)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(contact.customAlias ?: contact.username ?: contact.fakeNumber, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = topBarTitleColor(),
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Profil-Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar
                    val contactAvatarUrl = contact.profileImageUrl
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .then(if (contactAvatarUrl != null) Modifier.clickable { showEnlargedAvatar = true } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (contactAvatarUrl != null) {
                            AsyncImage(
                                model = if (contactAvatarUrl.startsWith("http")) contactAvatarUrl else "https://letheapp.de$contactAvatarUrl",
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    if (showEnlargedAvatar && contactAvatarUrl != null) {
                        val enlargedUrl = if (contactAvatarUrl.startsWith("http")) contactAvatarUrl else "https://letheapp.de$contactAvatarUrl"
                        Dialog(
                            onDismissRequest = { showEnlargedAvatar = false },
                            properties = DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.9f))
                                    .clickable { showEnlargedAvatar = false },
                                contentAlignment = Alignment.Center
                            ) {
                                val maxH = (LocalConfiguration.current.screenHeightDp / 2).dp
                                AsyncImage(
                                    model = enlargedUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = maxH),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = contact.customAlias ?: contact.username ?: contact.fakeNumber,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )

                    if (!contact.isAnonymous) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = contact.fakeNumber,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    if (!contact.info.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = contact.info,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    if (!remoteLinks.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = remoteLinks!!,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .clickable {
                                    val url = if (remoteLinks!!.startsWith("http")) remoteLinks!!
                                              else "https://${remoteLinks!!}"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                }
                        )
                    }

                    // Soziale-Medien-Links
                    val hasSocial = !contact.instagram.isNullOrBlank() ||
                                    !contact.tiktok.isNullOrBlank() ||
                                    !contact.youtube.isNullOrBlank()
                    if (hasSocial) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!contact.instagram.isNullOrBlank()) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable {
                                        val intent = Intent(Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://instagram.com/${contact.instagram}"))
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = "Instagram",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.height(2.dp))
                                    Text("@${contact.instagram}", fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                }
                            }
                            if (!contact.tiktok.isNullOrBlank()) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable {
                                        val intent = Intent(Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://tiktok.com/@${contact.tiktok}"))
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = "TikTok",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.height(2.dp))
                                    Text("@${contact.tiktok}", fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                }
                            }
                            if (!contact.youtube.isNullOrBlank()) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable {
                                        val intent = Intent(Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://youtube.com/@${contact.youtube}"))
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Icon(Icons.Default.PlayCircle, contentDescription = "YouTube",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.height(2.dp))
                                    Text(contact.youtube!!, fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }

                // Medien-Tabs
                if (tabs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PermMedia, contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                            Spacer(Modifier.height(8.dp))
                            Text("Keine Medien in diesem Chat",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                } else {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 0.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        tabs.forEachIndexed { index, tabName ->
                            val count = groupedMedia[tabName]?.size ?: 0
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text("$tabName ($count)", maxLines = 1,
                                        style = MaterialTheme.typography.labelMedium)
                                }
                            )
                        }
                    }

                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val tabName = tabs.getOrNull(page) ?: return@HorizontalPager
                        val items = groupedMedia[tabName] ?: emptyList()

                        if (tabName == "Fotos & Videos" || tabName == "Sticker/GIFs") {
                            val galleryEntries = remember(items) { buildGalleryEntries(items) }
                            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                lazyGridItems(items = galleryEntries, key = { it.entryKey }) { entry ->
                                    val msg = entry.message
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                mediaActionMessage = msg
                                                mediaActionUrl = entry.url
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when (msg.mediaType) {
                                            "image", "multi_image", "sticker", "gif" -> Image(
                                                painter = rememberAsyncImagePainter(entry.url),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            "video" -> {
                                                VideoThumbnailImage(
                                                    url = msg.mediaUrl ?: "",
                                                    viewModel = viewModel,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                Icon(Icons.Default.PlayCircle, contentDescription = null,
                                                    modifier = Modifier.size(32.dp).align(Alignment.Center),
                                                    tint = Color.White.copy(alpha = 0.85f))
                                            }
                                            else -> Icon(Icons.Default.PermMedia, contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(32.dp))
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(items = items, key = { it.localId }) { msg ->
                                    Box(modifier = Modifier.clickable { mediaActionMessage = msg }) {
                                        MediaGalleryItem(message = msg, viewModel = viewModel)
                                    }
                                    HorizontalDivider(Modifier.padding(start = 80.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Aktions-Sheet für angeklickte Medien
    val actionMsg = mediaActionMessage
    if (actionMsg != null) {
        MediaActionSheet(
            message = actionMsg,
            viewModel = viewModel,
            onDismiss = { mediaActionMessage = null },
            onViewFullscreen = { msg ->
                mediaActionMessage = null
                when (msg.mediaType) {
                    "image", "sticker", "gif" -> fullscreenImageUrl = msg.mediaUrl
                    "multi_image" -> fullscreenImageUrl = mediaActionUrl
                        ?: msg.mediaUrl?.let { parseMultiImageUrls(it).firstOrNull() }
                    "video" -> fullscreenVideoUrl = msg.mediaUrl
                }
            }
        )
    }

    // Fullscreen-Bildbetrachter
    val fsImageUrl = fullscreenImageUrl
    if (fsImageUrl != null) {
        Dialog(
            onDismissRequest = { fullscreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
        ) {
            val fsImgShareScope = rememberCoroutineScope()
            val fsImgCtx2 = LocalContext.current
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(fsImgCtx2)
                            .data(fsImageUrl)
                            // Max. 2048px + Scale.FIT: verhindert OOM bei großen Fotos und
                            // dass ein hohes Bild (langer Screenshot) via FILL die
                            // GL_MAX_TEXTURE_SIZE überschreitet → nur oberer Teil sichtbar.
                            .size(2048, 2048)
                            .scale(coil.size.Scale.FIT)
                            .build()
                    ),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        android.widget.Toast.makeText(context, "Wird vorbereitet…", android.widget.Toast.LENGTH_SHORT).show()
                        fsImgShareScope.launch {
                            val ok = quickShareMediaFile(context, fsImageUrl, "image")
                            if (!ok) android.widget.Toast.makeText(context, "Teilen fehlgeschlagen", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.IosShare, contentDescription = "Teilen", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    IconButton(onClick = { fullscreenImageUrl = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen",
                            tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }

    // Fullscreen-Video
    val fsVideoUrl = fullscreenVideoUrl
    if (fsVideoUrl != null) {
        Dialog(
            onDismissRequest = { fullscreenVideoUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val fsChatId = messages.firstOrNull { it.mediaUrl == fsVideoUrl }?.chatId ?: ""
            val exoPlayer = remember {
                val publicUri = viewModel.getPublicMediaUri(fsVideoUrl, true)
                val playUri = when {
                    publicUri != null -> publicUri
                    else -> {
                        val localPath = viewModel.getCachedVideoPath(fsVideoUrl, fsChatId)
                        if (localPath != null) android.net.Uri.fromFile(java.io.File(localPath))
                        else { viewModel.ensureVideoCached(fsVideoUrl, fsChatId); android.net.Uri.parse(fsVideoUrl) }
                    }
                }
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(playUri))
                    prepare()
                    playWhenReady = true
                    addListener(object : androidx.media3.common.Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (!isPlaying) viewModel.exportVideoToMovies(fsVideoUrl, fsChatId)
                        }
                    })
                }
            }
            DisposableEffect(exoPlayer) {
                onDispose {
                    viewModel.exportVideoToMovies(fsVideoUrl, fsChatId)
                    exoPlayer.release()
                }
            }
            // Andere Wiedergaben (Musik/fremde Apps) pausieren, danach fortsetzen
            TransientMediaFocus(exoPlayer, active = true)
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = true } },
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { fullscreenVideoUrl = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Schließen",
                        tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

@Composable
private fun MediaGalleryItem(message: MessageEntity, viewModel: MainViewModel) {
    val context = LocalContext.current
    val timeText = remember(message.timestamp) {
        java.text.SimpleDateFormat("dd.MM.yyyy · HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(message.timestamp))
    }
    val (icon, label) = when (message.mediaType) {
        "image"                   -> Icons.Default.Image to "Foto"
        "video"                   -> Icons.Default.PlayCircle to "Video"
        "audio"                   -> Icons.Default.Mic to "Sprachnachricht"
        "audio_music"             -> Icons.Default.MusicNote to "Musik"
        "document", "file", "pdf" -> Icons.Default.Description to "Dokument"
        "3dprint", "3d"           -> Icons.Default.ViewInAr to "3D-Datei"
        else                      -> Icons.Default.Mic to "Sprachnachricht"
    }

    var downloadDone by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf(false) }

    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                when (message.mediaType) {
                    "image" -> Image(
                        painter = rememberAsyncImagePainter(message.mediaUrl),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    "video" -> VideoThumbnailImage(
                        url = message.mediaUrl ?: "",
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> Icon(icon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp))
                }
            }
        },
        headlineContent = { Text(label, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(timeText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) },
        trailingContent = {
            // Download-Button: speichert in passenden Android-Ordner/Lethe
            val url = message.mediaUrl
            if (!url.isNullOrBlank()) {
                IconButton(
                    onClick = {
                        downloadError = false
                        try {
                            val dm = context.getSystemService(android.app.DownloadManager::class.java)
                            val dirType = when (message.mediaType) {
                                "image"                   -> android.os.Environment.DIRECTORY_PICTURES
                                "video"                   -> android.os.Environment.DIRECTORY_MOVIES
                                "audio", "audio_music"    -> android.os.Environment.DIRECTORY_MUSIC
                                "document", "file", "pdf",
                                "3dprint", "3d"           -> android.os.Environment.DIRECTORY_DOWNLOADS
                                else                      -> android.os.Environment.DIRECTORY_DOWNLOADS
                            }
                            val fileName = url.substringAfterLast("/").ifBlank {
                                "${message.mediaType}_${message.timestamp}"
                            }
                            val ext = when (message.mediaType) {
                                "image"        -> if (!fileName.contains('.')) ".jpg" else ""
                                "video"        -> if (!fileName.contains('.')) ".mp4" else ""
                                "audio"        -> if (!fileName.contains('.')) ".m4a" else ""
                                "audio_music"  -> if (!fileName.contains('.')) ".mp3" else ""
                                else           -> ""
                            }
                            val req = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                                .setTitle(fileName)
                                .setDescription("Lethe-Download")
                                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setDestinationInExternalPublicDir(dirType, "Lethe/${fileName}${ext}")
                                .setAllowedOverMetered(true)
                            dm.enqueue(req)
                            downloadDone = true
                        } catch (_: Exception) {
                            downloadError = true
                        }
                    }
                ) {
                    Icon(
                        if (downloadDone) Icons.Default.CheckCircle else Icons.Default.Download,
                        contentDescription = "Herunterladen",
                        tint = if (downloadError) MaterialTheme.colorScheme.error
                               else if (downloadDone) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

private data class ThreeDPending(
    val uri: Uri? = null,
    val textureUri: Uri? = null,
    val filename: String = "",
    val show: Boolean = false
)

/** Dialog zur Eingabe des Styx-Preises vor dem Versand einer 3D-Datei. */
@Composable
private fun ThreeDPriceDialog(
    filename: String,
    onConfirm: (price: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var priceText by remember { mutableStateOf("0") }
    val price = priceText.toIntOrNull() ?: 0

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "3D-Datei versenden",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    filename.ifBlank { "3D-Datei" },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Preis zum Herunterladen (Styx)",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { v ->
                        if (v.all { it.isDigit() } && v.length <= 6) priceText = v
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Styx (0 = kostenlos)") },
                    leadingIcon = {
                        Icon(Icons.Default.MonetizationOn, contentDescription = null,
                            tint = Color(0xFFA8A800))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (price > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Empfänger muss $price Styx zahlen, um die Datei herunterladen zu können.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onConfirm(price) }) {
                        Text(if (price > 0) "Kostenpflichtig senden" else "Kostenlos senden")
                    }
                }
            }
        }
    }
}

@Composable
fun ThreeDFileCard(
    meta: com.securechat.app.data.network.ThreeDFileMeta,
    bubbleColor: Color,
    textColor: Color,
    isFromMe: Boolean = false,
    currentUserStyx: Int = 0,
    onOpen: () -> Unit = {},
    onNavigateToCoins: (() -> Unit)? = null,
    onPurchase: ((onPaid: () -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val fileSizeText = when {
        meta.fileSize >= 1_048_576 -> "%.1f MB".format(meta.fileSize / 1_048_576.0)
        meta.fileSize >= 1_024     -> "%.0f KB".format(meta.fileSize / 1_024.0)
        else                       -> "${meta.fileSize} B"
    }
    val ext = meta.filename.substringAfterLast('.', "stl").uppercase()
    val isPaid = meta.price > 0 && !isFromMe

    var showPayDialog by remember { mutableStateOf(false) }
    var payError by remember { mutableStateOf<String?>(null) }
    var isPaying by remember { mutableStateOf(false) }

    fun triggerDownload() {
        val downloadUri = android.net.Uri.parse(meta.fileUrl)
        val mimeType = when (meta.filename.substringAfterLast('.', "stl").lowercase()) {
            "stl" -> "model/stl"
            "obj" -> "model/obj"
            "3mf" -> "model/3mf"
            else  -> "application/octet-stream"
        }
        val request = DownloadManager.Request(downloadUri)
            .setTitle(meta.filename)
            .setDescription("3D-Datei wird heruntergeladen…")
            .setMimeType(mimeType)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, meta.filename)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }

    // Zahlungs-Dialog
    if (showPayDialog) {
        val hasEnough = currentUserStyx >= meta.price
        Dialog(onDismissRequest = { showPayDialog = false; payError = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "3D-Datei herunterladen",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        meta.filename,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MonetizationOn, contentDescription = null,
                            tint = Color(0xFFA8A800), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Preis: ${meta.price} Styx", fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null,
                            tint = if (hasEnough) Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Dein Guthaben: $currentUserStyx Styx", fontSize = 13.sp,
                            color = if (hasEnough) MaterialTheme.colorScheme.onSurface
                                    else Color(0xFFF44336))
                    }
                    if (payError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(payError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showPayDialog = false; payError = null }) {
                            Text("Abbrechen")
                        }
                        Spacer(Modifier.width(8.dp))
                        if (hasEnough) {
                            Button(
                                onClick = {
                                    isPaying = true
                                    payError = null
                                    onPurchase?.invoke {
                                        isPaying = false
                                        showPayDialog = false
                                        triggerDownload()
                                    } ?: run {
                                        isPaying = false
                                        showPayDialog = false
                                        triggerDownload()
                                    }
                                },
                                enabled = !isPaying
                            ) {
                                if (isPaying) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp, color = Color.White)
                                } else {
                                    Text("Bezahlen")
                                }
                            }
                        } else {
                            Button(
                                onClick = { showPayDialog = false; onNavigateToCoins?.invoke() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA8A800))
                            ) {
                                Text("Styx aufladen", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    // Äußere klickbare Box (Glassmorphismus-Hintergrund)
    Box(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 270.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.04f)
                    )
                )
            )
            .then(
                Modifier.background(
                    bubbleColor.copy(alpha = 0.06f),
                    RoundedCornerShape(14.dp)
                )
            )
            .clickable { onOpen() }
    ) {
        Column {
            // Preview-Bild / Fallback-Visualisierung
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1A2845),
                                Color(0xFF0A0F1E)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Server-Preview-Bild falls vorhanden
                if (meta.previewUrl.isNotEmpty()) {
                    AsyncImage(
                        model = meta.previewUrl,
                        contentDescription = "3D-Vorschau",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(0.75f)
                    )
                }
                // Dekoratives 3D-Icon-Overlay
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFA8A800).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ViewInAr,
                            contentDescription = null,
                            tint = Color(0xFFA8A800),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    // Dateityp-Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFA8A800).copy(alpha = 0.25f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = ext,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA8A800)
                        )
                    }
                }
                // "Öffnen"-Hint unten rechts
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Öffnen",
                    tint = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(16.dp)
                )
                // Preis-Badge oben links (nur wenn Preis > 0)
                if (meta.price > 0) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MonetizationOn, contentDescription = null,
                            tint = Color(0xFFA8A800), modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "${meta.price} Styx",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Dateiname + Größe + Download-Icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = meta.filename,
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = fileSizeText,
                        color = textColor.copy(alpha = 0.55f),
                        fontSize = 11.sp
                    )
                }
                // Download-Button (sekundäre Aktion)
                IconButton(
                    onClick = {
                        if (isPaid) {
                            showPayDialog = true
                        } else {
                            triggerDownload()
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (isPaid) Icons.Default.Lock else Icons.Default.Download,
                        contentDescription = if (isPaid) "Kaufen" else "Herunterladen",
                        tint = Color(0xFFA8A800),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// --- Schwebender Video-Embed-Player (YouTube, TikTok, Instagram) ---
@Composable
fun EmbedPlayerDialog(
    embedUrl: String,
    originalUrl: String,
    embedType: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Autoplay-URL aufbauen
    val playUrl = remember(embedUrl) {
        if (embedUrl.contains("youtube.com/embed") && !embedUrl.contains("autoplay")) {
            embedUrl + if (embedUrl.contains("?")) "&autoplay=1&playsinline=1" else "?autoplay=1&playsinline=1"
        } else embedUrl
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // Abgedunkelter Hintergrund – Tippen schließt
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            // Player-Karte (~80% Breite, nicht auf Hintergrund-Tap reagieren)
            Card(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(0.88f)
                    .clickable { /* schluckt Tap */ },
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Column {
                    // Video-Bereich: 16:9
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                android.webkit.WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    settings.domStorageEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    // Mobiler Chrome-UA → YouTube liefert einbettbaren Player
                                    settings.userAgentString =
                                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                        "Chrome/120.0.6099.144 Mobile Safari/537.36"
                                    // Verhindert, dass YouTube externe App öffnet
                                    webViewClient = android.webkit.WebViewClient()
                                    webChromeClient = android.webkit.WebChromeClient()
                                    loadUrl(playUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Kontrollleiste
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111111))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Label
                        Text(
                            text = when (embedType) {
                                "youtube"   -> "YouTube"
                                "tiktok"    -> "TikTok"
                                "instagram" -> "Instagram"
                                else        -> "Video"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when (embedType) {
                                "youtube" -> Color(0xFFFF0000)
                                else      -> Color.White
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        )

                        Row {
                            // Vollbild: öffnet in Browser / YouTube-App
                            IconButton(onClick = {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(originalUrl))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) {}
                            }) {
                                Icon(Icons.Default.Fullscreen,
                                    contentDescription = "Vollbild",
                                    tint = Color.White)
                            }
                            // Schließen
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close,
                                    contentDescription = "Schließen",
                                    tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── SPIEL-ERGEBNIS-KARTE ──────────────────────────────────────────────────────

@Composable
internal fun GameResultCard(
    json: org.json.JSONObject,
    onPlayAgain: ((partnerId: String, partnerName: String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val game        = json.optString("game", "jump_run")
    val gameName    = json.optString("gameName", "Spiel")
    val won         = json.optBoolean("won", false)
    val isDraw      = json.optBoolean("isDraw", false)
    val partnerName = json.optString("partnerName", "")
    val partnerId   = json.optString("partnerId", "")
    val myName      = json.optString("myName", "Du")
    val myScore     = json.optString("myScore", "")
    val partnerScore = json.optString("partnerScore", "")
    val myCoins      = json.optInt("myCoins", -1)
    val partnerCoins = json.optInt("partnerCoins", -1)

    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val graphicsContext = LocalGraphicsContext.current
    val graphicsLayer = remember(graphicsContext) { graphicsContext.createGraphicsLayer() }
    DisposableEffect(graphicsLayer) {
        onDispose { graphicsContext.releaseGraphicsLayer(graphicsLayer) }
    }

    val resultText = when {
        isDraw -> "Unentschieden!"
        won    -> "Du hast gewonnen! \uD83C\uDFC6"
        else   -> "Du hast verloren!"
    }
    val resultColor = when {
        isDraw -> Color(0xFFFF9800)
        won    -> Color(0xFF4CAF50)
        else   -> Color(0xFFEF5350)
    }
    val gameColor = when (game) {
        "jump_run"      -> Color(0xFF1565C0)
        "tictactoe"     -> Color(0xFF6A1B9A)
        "sketch_n_check" -> Color(0xFF00695C)
        else            -> Color(0xFF1E2A3A)
    }
    val gameEmoji = when (game) {
        "jump_run"       -> "\uD83C\uDFC3"
        "tictactoe"      -> "\u274C"
        "sketch_n_check" -> "\uD83C\uDFA8"
        else             -> "\uD83C\uDFAE"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        modifier = modifier.widthIn(min = 240.dp, max = 300.dp)
    ) {
        Column {
            // Shareable Content – wird als PNG erfasst
            Column(
                modifier = Modifier
                    .background(Color(0xFF1C1C1E))
                    .drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    }
            ) {
                // Header: Spielbild + Name + Ergebnis
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(gameColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(gameEmoji, fontSize = 28.sp)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(gameName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp)
                        Text(resultText, color = resultColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // Spieler-Zeilen
                Surface(
                    color = Color(0xFF2A2A2E),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (won || isDraw) {
                            GamePlayerRow(rank = 1, name = myName, score = myScore, coins = if (myCoins >= 0) myCoins else -1, isWinner = won && !isDraw)
                            if (partnerName.isNotBlank()) {
                                GamePlayerRow(rank = if (isDraw) 1 else 2, name = partnerName, score = partnerScore, coins = if (partnerCoins >= 0) partnerCoins else -1, isWinner = false)
                            }
                        } else {
                            if (partnerName.isNotBlank()) {
                                GamePlayerRow(rank = 1, name = partnerName, score = partnerScore, coins = if (partnerCoins >= 0) partnerCoins else -1, isWinner = true)
                            }
                            GamePlayerRow(rank = 2, name = myName, score = myScore, coins = if (myCoins >= 0) myCoins else -1, isWinner = false)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
            }

            // Buttons
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Teilen als PNG
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            val imageBitmap = graphicsLayer.toImageBitmap()
                            val bitmap = imageBitmap.asAndroidBitmap()
                            val file = withContext(Dispatchers.IO) {
                                val shareDir = File(ctx.cacheDir, "share").also { it.mkdirs() }
                                val f = File(shareDir, "game_result_${System.currentTimeMillis()}.png")
                                f.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                                f
                            }
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                ctx, "${ctx.packageName}.fileprovider", file
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(Intent.createChooser(intent, "Teilen"))
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2A2E))
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Teilen", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                // Erneut spielen
                Button(
                    onClick = { onPlayAgain?.invoke(partnerId, partnerName) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Erneut spielen", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun GamePlayerRow(rank: Int, name: String, score: String, coins: Int, isWinner: Boolean) {
    Surface(
        color = Color(0xFF1C1C1E),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (isWinner) "\uD83D\uDC51" else rank.toString(),
                color = if (isWinner) Color.Unspecified else Color.White.copy(alpha = 0.55f),
                fontSize = if (isWinner) 16.sp else 13.sp,
                modifier = Modifier.width(22.dp)
            )
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF444448)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(name, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val scoreDisplay = buildString {
                if (score.isNotBlank()) append(score)
                if (coins >= 0 && score.isNotBlank()) append(" \uD83E\uDE99$coins")
                else if (coins >= 0) append("\uD83E\uDE99$coins")
            }
            if (scoreDisplay.isNotBlank()) {
                Text(scoreDisplay, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── TERMIN-SYSTEM ─────────────────────────────────────────────────────────────

/**
 * Chat-Karte für Gruppen-Termine.
 * - media_type="appointment": Finalisierter Termin mit Live-RSVP, Avatar-Gruppe und Kalender-Button.
 * - media_type="appointment_proposal": Noch nicht finalisierter Vorschlag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppointmentMessageCard(
    appointmentId: String,
    isFinalized: Boolean,
    viewModel: MainViewModel
) {
    val appointments by viewModel.appointments.collectAsState()
    val appointment = appointments[appointmentId]
    val context = LocalContext.current

    LaunchedEffect(appointmentId) {
        if (appointment == null) viewModel.loadAppointment(appointmentId)
    }

    if (appointment == null) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Termin wird geladen…", fontSize = 13.sp)
        }
        return
    }

    Column(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 300.dp)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Titel
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = appointment.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Ort
        if (!appointment.location.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(appointment.location, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }

        if (isFinalized && !appointment.finalDate.isNullOrBlank()) {
            // Formatiertes Datum
            val formatted = remember(appointment.finalDate) {
                runCatching {
                    val parts = appointment.finalDate!!.split("T")
                    val d = parts[0].split("-")
                    val t = parts.getOrNull(1)?.take(5) ?: ""
                    "${d[2]}.${d[1]}.${d[0]}" + if (t.isNotBlank()) " um $t Uhr" else ""
                }.getOrNull() ?: appointment.finalDate!!
            }
            Text("📅 $formatted", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // Avatar-Gruppe
            val going = appointment.going
            Text(
                text = if (going.isEmpty()) "Noch keine Zusagen" else "Dabei: ${going.size}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (going.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    items(going.take(6)) { attendee ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!attendee.profileImageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = attendee.profileImageUrl,
                                    contentDescription = attendee.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    (attendee.name?.take(1) ?: "?").uppercase(),
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    if (going.size > 6) {
                        item {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+${going.size - 6}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // RSVP-Buttons
            val myStatus = appointment.myStatus
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { viewModel.rsvpAppointment(appointmentId, "going") { _, _ -> } },
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (myStatus == "going") Color(0xFF388E3C) else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(if (myStatus == "going") "✓ Dabei" else "Ich bin dabei", fontSize = 11.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { viewModel.rsvpAppointment(appointmentId, "declined") { _, _ -> } },
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (myStatus == "declined") MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(if (myStatus == "declined") "✗ Abgesagt" else "Absagen", fontSize = 11.sp, maxLines = 1)
                }
            }

            // Kalender-Button unten rechts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { exportAppointmentAsIcs(context, appointment.title, appointment.description, appointment.location, appointment.finalDate) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("ICS", fontSize = 11.sp)
                }
                Button(
                    onClick = {
                        runCatching {
                            val startMs = runCatching {
                                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                                    .parse(appointment.finalDate!!)?.time ?: System.currentTimeMillis()
                            }.getOrElse { System.currentTimeMillis() }
                            val intent = Intent(Intent.ACTION_INSERT).apply {
                                data = CalendarContract.Events.CONTENT_URI
                                putExtra(CalendarContract.Events.TITLE, appointment.title)
                                putExtra(CalendarContract.Events.DESCRIPTION, appointment.description ?: "")
                                putExtra(CalendarContract.Events.EVENT_LOCATION, appointment.location ?: "")
                                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
                                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMs + 3_600_000L)
                            }
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("In Kalender", fontSize = 11.sp)
                }
            }
        } else {
            when (appointment.status) {
                "confirming" -> {
                    // Bestätigungsphase: Admin hat einen Termin gewählt, alle sollen abstimmen
                    AppointmentConfirmingRsvpCard(
                        appointment = appointment,
                        appointmentId = appointmentId,
                        viewModel = viewModel
                    )
                }
                else -> {
                    // "suggesting": Abstimmungsphase – Karte anklicken öffnet Kalender
                    var showCalendarSheet by remember { mutableStateOf(false) }
                    if (showCalendarSheet) {
                        GroupAppointmentCalendarSheet(
                            groupId = appointment.groupId,
                            viewModel = viewModel,
                            onDismiss = { showCalendarSheet = false }
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showCalendarSheet = true }
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "📋 Terminvorschlag – Admin legt Datum fest",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                        if (!appointment.proposedDates.isNullOrEmpty()) {
                            Text(
                                "Vorschläge: ${appointment.proposedDates.size}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Text(
                            "Tippen zum Öffnen →",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        if (!appointment.description.isNullOrBlank()) {
            Text(
                appointment.description!!,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AppointmentConfirmingRsvpCard(
    appointment: com.securechat.app.data.network.GroupAppointmentResponse,
    appointmentId: String,
    viewModel: MainViewModel
) {
    val groupMembers by viewModel.groupMembers.collectAsState()

    LaunchedEffect(appointment.groupId) {
        viewModel.loadGroupMembers(appointment.groupId)
    }

    // Expandiert sich automatisch sobald der User abgestimmt hat
    var expanded by remember(appointmentId) { mutableStateOf(appointment.myStatus != null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Datum anzeigen wenn vorhanden
        if (!appointment.finalDate.isNullOrBlank()) {
            val formatted = remember(appointment.finalDate) {
                runCatching {
                    val parts = appointment.finalDate!!.split("T")
                    val d = parts[0].split("-")
                    val t = parts.getOrNull(1)?.take(5) ?: ""
                    "${d[2]}.${d[1]}.${d[0]}" + if (t.isNotBlank()) " um $t Uhr" else ""
                }.getOrNull() ?: appointment.finalDate!!
            }
            Text(
                "📅 $formatted",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Frage
        Text(
            "Passt dieser Termin für dich?",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )

        // Ja/Nein Buttons
        val myStatus = appointment.myStatus
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    viewModel.rsvpAppointment(appointmentId, "going") { _, _ -> }
                    expanded = true
                },
                modifier = Modifier.weight(1f).height(36.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (myStatus == "going") Color(0xFF388E3C) else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text(if (myStatus == "going") "✓ Zugestimmt" else "✓ Ja, passt", fontSize = 12.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = {
                    viewModel.rsvpAppointment(appointmentId, "declined") { _, _ -> }
                    expanded = true
                },
                modifier = Modifier.weight(1f).height(36.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (myStatus == "declined") MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text(if (myStatus == "declined") "✗ Abgelehnt" else "✗ Nein", fontSize = 12.sp, maxLines = 1)
            }
        }

        // Expandierbare Abstimmungs-Anzeige
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            val members = groupMembers[appointment.groupId] ?: emptyList()
            val goingIds = appointment.going.map { it.userId }.toSet()
            val declinedIds = appointment.declined.map { it.userId }.toSet()
            val pending = members.filter { it.userId !in goingIds && it.userId !in declinedIds }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Zwei Spalten: Zugestimmt | Abgelehnt
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Spalte Zugestimmt
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "✓ Zugestimmt (${appointment.going.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF388E3C)
                        )
                        appointment.going.forEach { attendee ->
                            Text(
                                attendee.name ?: "?",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                        if (appointment.going.isEmpty()) {
                            Text("Noch niemand", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                    // Trennlinie
                    VerticalDivider(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    // Spalte Abgelehnt
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "✗ Abgelehnt (${appointment.declined.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        appointment.declined.forEach { attendee ->
                            Text(
                                attendee.name ?: "?",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                        if (appointment.declined.isEmpty()) {
                            Text("Noch niemand", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                }

                // Ausstehend (mittig)
                if (pending.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        "Noch nicht abgestimmt (${pending.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        pending.forEach { member ->
                            Text(
                                member.name ?: member.fakeNumber ?: "?",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupAppointmentCalendarSheet(
    groupId: String,
    viewModel: com.securechat.app.ui.MainViewModel,
    onDismiss: () -> Unit
) {
    val appointments by viewModel.appointments.collectAsState()
    val groupAppointments = remember(appointments, groupId) {
        appointments.values.filter { it.groupId == groupId }.sortedByDescending { it.createdAt ?: "" }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCreate by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Termin Kalender",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Termin erstellen")
                }
            }
            Spacer(Modifier.height(8.dp))

            if (groupAppointments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Keine Termine vorhanden",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(groupAppointments) { apt ->
                        GroupCalendarAppointmentCard(apt, viewModel)
                    }
                }
            }
        }
    }

    if (showCreate) {
        GroupAppointmentCreateSheet(
            groupId = groupId,
            viewModel = viewModel,
            onDismiss = { showCreate = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupAppointmentCreateSheet(
    groupId: String,
    viewModel: com.securechat.app.ui.MainViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    val selectedDates = remember { mutableStateListOf<LocalDate>() }
    var currentMonth by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var isSending by remember { mutableStateOf(false) }
    val canSend = title.isNotBlank() && selectedDates.isNotEmpty() && !isSending

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            Text("Neuer Termin", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))

            // Monatsnavigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Vorheriger Monat")
                }
                Text(
                    currentMonth.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Nächster Monat")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Wochentag-Header
            val weekDays = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
            Row(modifier = Modifier.fillMaxWidth()) {
                weekDays.forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(day, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            // Kalender-Grid
            val today = LocalDate.now()
            val firstDay = currentMonth
            val startOffset = (firstDay.dayOfWeek.value - 1)
            val daysInMonth = currentMonth.lengthOfMonth()
            val totalCells = startOffset + daysInMonth
            val rows = (totalCells + 6) / 7

            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        val dayNum = cellIndex - startOffset + 1
                        if (dayNum < 1 || dayNum > daysInMonth) {
                            Box(modifier = Modifier.weight(1f).height(40.dp))
                        } else {
                            val date = currentMonth.withDayOfMonth(dayNum)
                            val isPast = date.isBefore(today)
                            val isSelected = selectedDates.contains(date)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            else -> Color.Transparent
                                        }
                                    )
                                    .then(
                                        if (!isPast) Modifier.clickable {
                                            if (isSelected) selectedDates.remove(date)
                                            else selectedDates.add(date)
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$dayNum",
                                    fontSize = 13.sp,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        isPast -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (selectedDates.isNotEmpty()) {
                val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                Text(
                    "Ausgewählt: ${selectedDates.sorted().joinToString(", ") { it.format(fmt) }}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Titel *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Beschreibung (optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Ort (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    isSending = true
                    viewModel.createGroupAppointment(
                        groupId = groupId,
                        title = title,
                        description = description,
                        location = location,
                        proposedDates = selectedDates.sorted().map { it.toString() }
                    ) { success ->
                        isSending = false
                        if (success) onDismiss()
                    }
                },
                enabled = canSend,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Termin senden")
                }
            }
        }
    }
}

@Composable
private fun GroupCalendarAppointmentCard(
    apt: com.securechat.app.data.network.GroupAppointmentResponse,
    viewModel: com.securechat.app.ui.MainViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val myId = viewModel.currentUser.collectAsState().value?.userId ?: ""
    val isGroupCreator = apt.createdBy == myId
    val gold = Color(0xFFD4A017)

    when (apt.status) {

        // ══ STATUS: SUGGESTING ══════════════════════════════════════════════
        "suggesting" -> {
            val maxVotes = apt.options.maxOfOrNull { it.voteCount } ?: 0
            val selectedOptionIds = remember { mutableStateListOf<String>() }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HowToVote, null, modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(apt.title, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Surface(shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)) {
                            Text("Abstimmung", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    if (!apt.description.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(apt.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(10.dp))

                    // Optionen-Liste
                    apt.options.forEach { opt ->
                        val isSelected = opt.id in selectedOptionIds || opt.myVote
                        val isLeader = maxVotes > 0 && opt.voteCount == maxVotes
                        val borderColor = when {
                            isLeader -> gold
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        }
                        val bgColor = when {
                            isLeader -> gold.copy(alpha = 0.08f)
                            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                        val formattedDt = formatIsoDateTime(opt.dateTime)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .border(
                                    width = if (isLeader || isSelected) 1.5.dp else 1.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    if (opt.id in selectedOptionIds) selectedOptionIds.remove(opt.id)
                                    else selectedOptionIds.add(opt.id)
                                },
                            colors = CardDefaults.cardColors(containerColor = bgColor),
                            shape = RoundedCornerShape(10.dp),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(formattedDt, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                            color = if (isLeader) gold else MaterialTheme.colorScheme.onSurface)
                                        if (isLeader) {
                                            Text("⭐ Führend", fontSize = 10.sp, color = gold)
                                        }
                                    }
                                    // Vote-Zähler
                                    Surface(shape = RoundedCornerShape(6.dp),
                                        color = if (isLeader) gold.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                                        Text("${opt.voteCount} ✓",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isLeader) gold else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                    }
                                }
                                // Avatar-Stack der Wähler
                                if (opt.voterIds.isNotEmpty()) {
                                    Spacer(Modifier.height(5.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                                        opt.voterImages.take(5).forEachIndexed { i, imgUrl ->
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (!imgUrl.isNullOrBlank()) {
                                                    AsyncImage(imgUrl, null,
                                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                        contentScale = ContentScale.Crop)
                                                } else {
                                                    Text(
                                                        opt.voterNames.getOrNull(i)?.firstOrNull()?.uppercase() ?: "?",
                                                        fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                        if (opt.voterIds.size > 5) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("+${opt.voterIds.size - 5}", fontSize = 8.sp,
                                                    color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Abstimmen-Button
                        Button(
                            onClick = {
                                val ids = selectedOptionIds.toList().ifEmpty {
                                    apt.options.filter { it.myVote }.map { it.id }
                                }
                                if (ids.isNotEmpty()) {
                                    viewModel.voteForAppointmentOptions(apt.id, ids) {}
                                }
                            },
                            modifier = Modifier.weight(1f).height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Abstimmen", fontSize = 12.sp)
                        }
                        // Admin: Bestbewerteten Termin bestätigen
                        if (isGroupCreator && apt.options.isNotEmpty()) {
                            val leadingOpt = apt.options.maxByOrNull { it.voteCount }
                            OutlinedButton(
                                onClick = {
                                    leadingOpt?.let {
                                        viewModel.moveAppointmentToConfirmation(apt.id, it.id) {}
                                    }
                                },
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Bestätigen →", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // ══ STATUS: CONFIRMING ══════════════════════════════════════════════
        "confirming" -> {
            val formattedFinalDate = formatIsoDateTime(apt.finalDate ?: "")
            val goingCount = apt.going.size

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ThumbsUpDown, null, modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(6.dp))
                        Text(apt.title, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Surface(shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)) {
                            Text("Einverständnis", fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Ist der Termin für alle okay?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(formattedFinalDate, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary)
                            if (!apt.location.isNullOrBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text("📍 ${apt.location}", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("$goingCount haben zugestimmt", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val myStatus = apt.myStatus
                        Button(
                            onClick = { viewModel.rsvpAppointment(apt.id, "going") { _, _ -> } },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (myStatus == "going") MaterialTheme.colorScheme.secondary
                                                 else MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                            )
                        ) {
                            Text("✓ Ja, passt", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.rsvpAppointment(apt.id, "declined") { _, _ -> } },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = if (myStatus == "declined") ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            ) else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text("✗ Nein", fontSize = 13.sp)
                        }
                    }
                    if (isGroupCreator) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { viewModel.finalApproveAppointment(apt.id) {} },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Termin finalisieren", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // ══ STATUS: FINALIZED (und Legacy ohne status) ═══════════════════════
        else -> {
            val formattedDate = formatIsoDateTime(apt.finalDate ?: apt.proposedDates?.firstOrNull() ?: "")
            val goingCount = apt.going.size
            val declinedCount = apt.declined.size
            val myStatus = apt.myStatus

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = gold.copy(alpha = 0.06f)
                ),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, gold.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Event, null, modifier = Modifier.size(18.dp), tint = gold)
                        Spacer(Modifier.width(6.dp))
                        Text(apt.title, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Surface(shape = RoundedCornerShape(6.dp), color = gold.copy(alpha = 0.15f)) {
                            Text("Fixiert", fontSize = 10.sp, color = gold, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    if (formattedDate.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, null, modifier = Modifier.size(14.dp), tint = gold)
                            Spacer(Modifier.width(4.dp))
                            Text(formattedDate, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = gold)
                        }
                    }
                    if (!apt.location.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text(apt.location, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    // Avatar-Stack Zusagen
                    if (apt.going.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                                apt.going.take(5).forEach { attendee ->
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4CAF50).copy(alpha = 0.2f))
                                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!attendee.profileImageUrl.isNullOrBlank()) {
                                            AsyncImage(attendee.profileImageUrl, null,
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                contentScale = ContentScale.Crop)
                                        } else {
                                            Text(attendee.name?.firstOrNull()?.uppercase() ?: "?",
                                                fontSize = 10.sp, color = Color(0xFF4CAF50))
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("$goingCount dabei · $declinedCount abgesagt",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // RSVP Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.rsvpAppointment(apt.id, "going") { _, _ -> } },
                            modifier = Modifier.weight(1f).height(36.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (myStatus == "going") Color(0xFF4CAF50)
                                                 else Color(0xFF4CAF50).copy(alpha = 0.6f)
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("✓ Dabei", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.rsvpAppointment(apt.id, "declined") { _, _ -> } },
                            modifier = Modifier.weight(1f).height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = if (myStatus == "declined") ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            ) else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text("✗ Absagen", fontSize = 12.sp)
                        }
                        // Kalender-Export: Dropdown mit Optionen
                        var showCalExportMenu by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { showCalExportMenu = true },
                                modifier = Modifier.size(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.EditCalendar, null, modifier = Modifier.size(16.dp), tint = gold)
                            }
                            DropdownMenu(
                                expanded = showCalExportMenu,
                                onDismissRequest = { showCalExportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Kalender-App öffnen", fontSize = 13.sp) },
                                    onClick = {
                                        showCalExportMenu = false
                                        val dateStr = apt.finalDate ?: return@DropdownMenuItem
                                        try {
                                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                                            val date = sdf.parse(dateStr)
                                            val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                                                data = android.provider.CalendarContract.Events.CONTENT_URI
                                                putExtra(android.provider.CalendarContract.Events.TITLE, apt.title)
                                                putExtra(android.provider.CalendarContract.Events.DESCRIPTION, apt.description ?: "")
                                                putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, apt.location ?: "")
                                                putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, date?.time ?: System.currentTimeMillis())
                                                putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, (date?.time ?: System.currentTimeMillis()) + 3600_000L)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("ICS-Datei exportieren", fontSize = 13.sp) },
                                    onClick = {
                                        showCalExportMenu = false
                                        exportAppointmentAsIcs(context, apt.title, apt.description, apt.location, apt.finalDate)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatIsoDateTime(raw: String): String {
    if (raw.isBlank()) return ""
    return runCatching {
        val parts = raw.split("T")
        val datePart = parts[0].split("-")
        "${datePart[2]}.${datePart[1]}.${datePart[0]}" +
            if (parts.size > 1) " ${parts[1].take(5)} Uhr" else ""
    }.getOrDefault(raw)
}

private fun exportAppointmentAsIcs(
    context: android.content.Context,
    title: String,
    description: String?,
    location: String?,
    finalDate: String?
) {
    try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        val startMs = try { sdf.parse(finalDate ?: "")?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
        val endMs = startMs + 3_600_000L

        val dtFormat = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.getDefault())
        dtFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val dtStart = dtFormat.format(java.util.Date(startMs))
        val dtEnd = dtFormat.format(java.util.Date(endMs))

        val icsContent = buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//Lethe//Lethe App//DE")
            appendLine("CALSCALE:GREGORIAN")
            appendLine("METHOD:PUBLISH")
            appendLine("BEGIN:VEVENT")
            appendLine("UID:lethe-${System.currentTimeMillis()}@letheapp.de")
            appendLine("DTSTART:$dtStart")
            appendLine("DTEND:$dtEnd")
            appendLine("SUMMARY:${title.replace("\n", "\\n")}")
            if (!description.isNullOrBlank()) appendLine("DESCRIPTION:${description.replace("\n", "\\n")}")
            if (!location.isNullOrBlank()) appendLine("LOCATION:${location.replace("\n", "\\n")}")
            appendLine("END:VEVENT")
            appendLine("END:VCALENDAR")
        }

        val icsDir = java.io.File(context.cacheDir, "ics")
        icsDir.mkdirs()
        val icsFile = java.io.File(icsDir, "termin_${System.currentTimeMillis()}.ics")
        icsFile.writeText(icsContent)

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            icsFile
        )

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "ICS exportieren"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Export fehlgeschlagen: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GroupEditScreen — Vollbild: Gruppenbild, Name, Mitglieder verwalten
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupEditScreen(
    group: com.securechat.app.data.local.GroupEntity?,
    groupId: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val groups by viewModel.groups.collectAsState(initial = emptyList())
    val currentGroup = groups.find { it.groupId == groupId } ?: group
    val currentUser by viewModel.currentUser.collectAsState()

    var showMembersSheet by remember { mutableStateOf(false) }
    var showNameEditDialog by remember { mutableStateOf(false) }
    var showDescEditDialog by remember { mutableStateOf(false) }
    var editedGroupName by remember(currentGroup?.name) { mutableStateOf(currentGroup?.name ?: "") }
    var editedGroupDesc by remember(currentGroup?.description) { mutableStateOf(currentGroup?.description ?: "") }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.uploadGroupImage(groupId, it) }
    }
    var groupEditFullscreenImageUrl by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Gruppe bearbeiten",
                            fontWeight = FontWeight.Bold,
                            color = topBarTitleColor()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Zurück",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Gruppenbild mit Bleistift
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        val groupEditImgUrl = currentGroup?.groupImageUrl?.let { url ->
                            if (url.startsWith("http")) url else "https://letheapp.de$url"
                        }
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .then(if (groupEditImgUrl != null) Modifier.clickable { groupEditFullscreenImageUrl = groupEditImgUrl } else Modifier.clickable { imagePickerLauncher.launch("image/*") }),
                            contentAlignment = Alignment.Center
                        ) {
                            if (groupEditImgUrl != null) {
                                AsyncImage(
                                    model = groupEditImgUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(20.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        // Bleistift-Badge
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { imagePickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Bild ändern",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Gruppenname mit Bleistift
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showNameEditDialog = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Gruppenname",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            currentGroup?.name ?: "",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Name bearbeiten",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                HorizontalDivider()

                // Gruppen-Info mit Bleistift
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDescEditDialog = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Gruppen-Info",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            currentGroup?.description?.ifBlank { null } ?: "Noch keine Info",
                            fontSize = 16.sp,
                            color = if (currentGroup?.description.isNullOrBlank())
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Info bearbeiten",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                HorizontalDivider()

                // Mitglieder verwalten
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.loadGroupMembers(groupId)
                            showMembersSheet = true
                        }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ManageAccounts,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "Mitglieder verwalten",
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                HorizontalDivider()
            }
        }
    }

    // Name-Bearbeiten-Dialog
    if (showNameEditDialog) {
        AlertDialog(
            onDismissRequest = { showNameEditDialog = false },
            title = { Text("Gruppenname ändern") },
            text = {
                OutlinedTextField(
                    value = editedGroupName,
                    onValueChange = { editedGroupName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editedGroupName.isNotBlank()) {
                        viewModel.updateGroup(groupId, editedGroupName, currentGroup?.description)
                    }
                    showNameEditDialog = false
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { showNameEditDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    // Gruppen-Info-Dialog
    if (showDescEditDialog) {
        AlertDialog(
            onDismissRequest = { showDescEditDialog = false },
            title = { Text("Gruppen-Info") },
            text = {
                OutlinedTextField(
                    value = editedGroupDesc,
                    onValueChange = { editedGroupDesc = it },
                    label = { Text("Info") },
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateGroup(
                        groupId,
                        currentGroup?.name ?: editedGroupName,
                        editedGroupDesc.trim().ifBlank { null }
                    )
                    showDescEditDialog = false
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { showDescEditDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    // Mitgliederverwaltungs-Sheet
    if (showMembersSheet) {
        val grpList by viewModel.groups.collectAsState(initial = emptyList())
        val grp = grpList.find { it.groupId == groupId }
        GroupMembersManagementSheet(
            groupId = groupId,
            isCreator = grp?.createdBy == currentUser?.userId,
            viewModel = viewModel,
            onDismiss = { showMembersSheet = false }
        )
    }
    val fsEditImg = groupEditFullscreenImageUrl
    if (fsEditImg != null) {
        Dialog(
            onDismissRequest = { groupEditFullscreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
        ) {
            var zoomScale by remember { mutableStateOf(1f) }
            var zoomOffset by remember { mutableStateOf(Offset.Zero) }
            val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
                val newScale = (zoomScale * zoomChange).coerceIn(1f, 6f)
                zoomScale = newScale
                zoomOffset = if (newScale > 1f) zoomOffset + offsetChange * 2.5f else Offset.Zero
            }
            val fsEditImgCtx = LocalContext.current
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(fsEditImgCtx)
                            .data(fsEditImg)
                            // Max. 2048px + Scale.FIT: verhindert OOM bei großen Fotos und
                            // dass ein hohes Bild (langer Screenshot) via FILL die
                            // GL_MAX_TEXTURE_SIZE überschreitet → nur oberer Teil sichtbar.
                            .size(2048, 2048)
                            .scale(coil.size.Scale.FIT)
                            .build()
                    ),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = zoomOffset.x
                            translationY = zoomOffset.y
                        }
                        .transformable(state = transformableState, canPan = { zoomScale > 1f }),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { groupEditFullscreenImageUrl = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Schließen", tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GroupMembersManagementSheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupMembersManagementSheet(
    groupId: String,
    isCreator: Boolean,
    viewModel: com.securechat.app.ui.MainViewModel,
    onDismiss: () -> Unit
) {
    val groupMembersMap by viewModel.groupMembers.collectAsState()
    val members = groupMembersMap[groupId] ?: emptyList()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showInviteSheet by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Mitglieder verwalten",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "${members.size} Mitglieder",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Kontakt einladen Button
            OutlinedButton(
                onClick = { showInviteSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Kontakt einladen")
            }

            if (members.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(members, key = { it.userId }) { member ->
                        GroupMemberRow(
                            member = member,
                            isCreator = isCreator,
                            onRoleChange = { newRole ->
                                viewModel.setGroupMemberRole(groupId, member.userId, newRole) {}
                            }
                        )
                    }
                }
            }
        }
    }

    if (showInviteSheet) {
        GroupInviteContactsSheet(
            groupId = groupId,
            currentMemberIds = members.map { it.userId }.toSet(),
            viewModel = viewModel,
            onDismiss = { showInviteSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupInviteContactsSheet(
    groupId: String,
    currentMemberIds: Set<String>,
    viewModel: com.securechat.app.ui.MainViewModel,
    onDismiss: () -> Unit
) {
    val allContacts by viewModel.contacts.collectAsState(initial = emptyList())
    val invitableContacts = remember(allContacts, currentMemberIds) {
        allContacts.filter { it.status == "accepted" && it.userId !in currentMemberIds }
    }
    val selected = remember { mutableStateListOf<String>() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Kontakt einladen",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "${selected.size} ausgewählt",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (invitableContacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Alle Kontakte sind bereits in der Gruppe.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    items(invitableContacts, key = { it.userId }) { contact ->
                        val isChecked = contact.userId in selected
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) selected.remove(contact.userId)
                                    else selected.add(contact.userId)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChecked)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!contact.profileImageUrl.isNullOrBlank()) {
                                        val imgUrl = contact.profileImageUrl.let {
                                            if (it.startsWith("http")) it else "https://letheapp.de$it"
                                        }
                                        AsyncImage(
                                            model = imgUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            (contact.username ?: contact.fakeNumber)
                                                ?.firstOrNull()?.uppercase() ?: "?",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        contact.username ?: contact.fakeNumber,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    if (!contact.username.isNullOrBlank()) {
                                        Text(
                                            contact.fakeNumber,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        if (isChecked) selected.remove(contact.userId)
                                        else selected.add(contact.userId)
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (selected.isNotEmpty()) {
                            viewModel.addGroupMembers(groupId, selected.toList())
                            coroutineScope.launch { sheetState.hide() }
                            onDismiss()
                        }
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (selected.isEmpty()) "Einladen" else "${selected.size} einladen")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupMemberRow(
    member: com.securechat.app.data.network.GroupMemberInfo,
    isCreator: Boolean,
    onRoleChange: (String) -> Unit
) {
    val roleColor = when (member.role) {
        "admin"     -> Color(0xFFE65100)
        "moderator" -> Color(0xFF1565C0)
        else        -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val roleLabel = when (member.role) {
        "admin"     -> "Admin"
        "moderator" -> "Moderator"
        else        -> "Mitglied"
    }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (!member.profileImageUrl.isNullOrBlank()) {
                    val memberImgUrl = member.profileImageUrl.let {
                        if (it.startsWith("http")) it else "https://letheapp.de$it"
                    }
                    AsyncImage(
                        model = memberImgUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        member.name?.firstOrNull()?.uppercase() ?: "?",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(member.name ?: member.fakeNumber ?: "Unbekannt", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(member.fakeNumber ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Rollen-Badge / Dropdown (nur Ersteller darf ändern, außer eigene Admin-Rolle)
            if (isCreator && member.role != "admin") {
                Box {
                    Surface(
                        onClick = { expanded = true },
                        shape = RoundedCornerShape(6.dp),
                        color = roleColor.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(roleLabel, fontSize = 11.sp, color = roleColor, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(2.dp))
                            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(14.dp), tint = roleColor)
                        }
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("member" to "Mitglied", "moderator" to "Moderator", "admin" to "Admin").forEach { (role, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { expanded = false; onRoleChange(role) },
                                leadingIcon = if (role == member.role) {
                                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = roleColor.copy(alpha = 0.12f)
                ) {
                    Text(roleLabel, fontSize = 11.sp, color = roleColor, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AudioWaveformVisualizer
// Zeigt die Live-Amplituden (AGC-normiert) während einer Sprachaufnahme als
// von-rechts-nach-links scrollende Balken-Waveform.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Live-Waveform während einer Sprachaufnahme.
 *
 * @param viewModel  Quelle des AGC-normierten [MainViewModel.liveRecordingAmplitude] StateFlow.
 * @param isLocked   true = Aufnahme gesperrt (Lock-Modus), zeigt dann einen Hinweis-Text.
 * @param modifier   Äußerer Modifier (sollte `weight(1f)` + `height` enthalten).
 */
@Composable
private fun AudioWaveformVisualizer(
    viewModel: com.securechat.app.ui.MainViewModel,
    isLocked: Boolean,
    modifier: Modifier = Modifier
) {
    // AGC-normierten Amplitudenwert aus ViewModel beobachten
    val liveAmplitude by viewModel.liveRecordingAmplitude.collectAsState()

    // Rollender Puffer: letzte MAX_BARS Amplitudenwerte (älteste links, neueste rechts)
    val maxBars = 50
    val bars = remember { mutableStateListOf<Float>() }

    // Jedes neue Datum vom StateFlow in den rollenden Puffer schreiben
    LaunchedEffect(Unit) {
        viewModel.liveRecordingAmplitude.collect { amp ->
            bars.add(amp)
            if (bars.size > maxBars) bars.removeAt(0)
        }
    }

    // Pop-in Animation: Composable erscheint mit federnder Scale-Animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val barColor      = MaterialTheme.colorScheme.primary
    val barColorError = MaterialTheme.colorScheme.error

    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(24.dp)
            )
            .clip(RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(
                initialScale = 0.4f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium
                )
            ) + fadeIn(animationSpec = tween(180))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barCount = bars.size
                if (barCount == 0) return@Canvas

                val totalWidth  = size.width
                val totalHeight = size.height

                // Balkenbreite + Lücke so wählen, dass MAX_BARS Balken genau die Breite füllen
                val barWidth    = (totalWidth / maxBars) * 0.60f
                val barGap      = (totalWidth / maxBars) * 0.40f
                val minBarH     = 4.dp.toPx()
                val maxBarH     = totalHeight * 0.88f
                val cornerRad   = CornerRadius(barWidth / 2f)

                // Älteste Balken links ausrichten — neuester Balken ist immer ganz rechts
                val startX = totalWidth - barCount * (barWidth + barGap)

                bars.forEachIndexed { index, amplitude ->
                    val x = startX + index * (barWidth + barGap)

                    // Höhe: lineare Interpolation zwischen minBarH und maxBarH
                    val barH = (minBarH + amplitude * (maxBarH - minBarH)).coerceAtLeast(minBarH)
                    val top  = (totalHeight - barH) / 2f

                    // Neuester Balken (Index = letzter) leuchtet in Vollfarbe;
                    // ältere Balken fade graduell aus (von 0.25 bis 0.85 Alpha)
                    val ageFraction = if (barCount > 1) index.toFloat() / (barCount - 1) else 1f
                    val alpha       = 0.25f + ageFraction * 0.60f
                    // Im Lock-Modus: Primärfarbe → Fehlerfarbe für visuelles Feedback
                    val color = if (isLocked) barColorError.copy(alpha = alpha)
                                else          barColor.copy(alpha = alpha)

                    drawRoundRect(
                        color        = color,
                        topLeft      = Offset(x, top),
                        size         = Size(barWidth, barH),
                        cornerRadius = cornerRad
                    )
                }
            }
        }

        // Hilfe-Text: Geste-Anleitung unter der Waveform (kleiner, gedimmt)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = if (isLocked) "← Abbrechen" else "↑ Sperren  ← Abbrechen",
                color = if (isLocked) barColorError.copy(alpha = 0.7f)
                        else          MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FloatingSharedMusicPlayer
// Schwebendes Mini-Player-Overlay für Listen-Together-Sessions.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FloatingSharedMusicPlayer(
    track: MainViewModel.ListenTogetherTrack?,
    isPlaying: Boolean,
    positionMs: Long,
    pendingAction: String?,
    trackIndex: Int = 0,
    playlistSize: Int = 1,
    shuffleActive: Boolean = false,
    castAvailableBar: Boolean = false,
    onCastClick: () -> Unit = {},
    onRequestAction: (String) -> Unit,
    onPositionSync: (Long) -> Unit,
    onShuffle: () -> Unit = {},
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // ExoPlayer für die lokale Wiedergabe
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
        }
    }
    // Release nur wenn Composable verlassen wird – NICHT bei Track-Wechsel
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }
    // Track-URL wechseln ohne Player zu zerstören
    LaunchedEffect(track?.url) {
        if (track != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(track.url))
            exoPlayer.prepare()
        }
    }

    // Beide Nutzer synchronisieren ihren ExoPlayer mit dem ViewModel-Zustand
    LaunchedEffect(isPlaying, positionMs) {
        val diff = kotlin.math.abs(exoPlayer.currentPosition - positionMs)
        if (diff > 1500L) exoPlayer.seekTo(positionMs)
        if (isPlaying && !exoPlayer.isPlaying) exoPlayer.play()
        else if (!isPlaying && exoPlayer.isPlaying) exoPlayer.pause()
    }

    // Positions-Tracking und regelmäßige Synchronisation
    var currentPos by remember { mutableStateOf(positionMs) }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPos = exoPlayer.currentPosition
            onPositionSync(currentPos)
            delay(2000)
        }
    }

    val durationMs = track?.durationMs?.takeIf { it > 0 } ?: exoPlayer.duration.takeIf { it > 0 } ?: 1L
    val progress   = (currentPos.toFloat() / durationMs).coerceIn(0f, 1f)

    fun formatMs(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }

    AnimatedVisibility(
        visible = true,
        enter   = androidx.compose.animation.slideInVertically { it },
        exit    = androidx.compose.animation.slideOutVertically { it },
        modifier = modifier
    ) {
        Card(
            shape  = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier  = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {

                // Titelzeile + Einklappen-Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint   = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track?.title?.ifBlank { "Unbekannter Titel" } ?: "–",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 14.sp,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        if (!track?.artist.isNullOrBlank()) {
                            Text(
                                text  = track!!.artist,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // Playlist-Position (z. B. "3 / 12")
                    if (playlistSize > 1) {
                        Text(
                            text  = "${trackIndex + 1} / $playlistSize",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    // Einklappen
                    IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Einklappen",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // Session beenden
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Beenden",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Waveform-ähnliche Fortschrittsanzeige (kein Seeking)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                ) {
                    val primary  = MaterialTheme.colorScheme.primary
                    val surface2 = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val bars   = 40
                        val bw     = size.width / (bars * 2 - 1)
                        val center = size.height / 2f
                        val rnd    = java.util.Random(42)
                        for (i in 0 until bars) {
                            val x      = i * bw * 2
                            val barH   = (0.25f + rnd.nextFloat() * 0.75f) * size.height
                            val filled = (i.toFloat() / bars) < progress
                            val color  = if (filled) primary else surface2
                            drawRoundRect(
                                color        = color,
                                topLeft      = androidx.compose.ui.geometry.Offset(x, center - barH / 2),
                                size         = Size(bw, barH),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 2)
                            )
                        }
                    }
                }

                // Zeitanzeige
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatMs(currentPos), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text(formatMs(durationMs), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }

                Spacer(Modifier.height(4.dp))

                // Steuerzeile – beide Nutzer können Aktionen anfordern
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Zurück
                    IconButton(
                        onClick  = { onRequestAction("prev") },
                        enabled  = pendingAction == null,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Zurück", modifier = Modifier.size(28.dp))
                    }
                    // Play / Pause
                    FilledIconButton(
                        onClick  = { onRequestAction(if (isPlaying) "pause" else "play") },
                        enabled  = pendingAction == null,
                        modifier = Modifier.size(48.dp),
                        colors   = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (pendingAction != null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color    = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    // Weiter
                    IconButton(
                        onClick  = { onRequestAction("next") },
                        enabled  = pendingAction == null,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Weiter", modifier = Modifier.size(28.dp))
                    }
                    // Cast – nur anzeigen wenn Cast-Gerät verfügbar
                    if (castAvailableBar) {
                        IconButton(
                            onClick  = onCastClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Cast,
                                contentDescription = "Audio-Ausgabe wählen",
                                modifier = Modifier.size(22.dp),
                                tint     = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    // Shuffle
                    if (playlistSize > 1) {
                        IconButton(
                            onClick  = onShuffle,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "Playlist mischen",
                                modifier = Modifier.size(22.dp),
                                tint = if (shuffleActive)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ListenTogetherSetupScreen
// Vollbild-Overlay zum Zusammenstellen einer Playlist für eine Listen-Together-Session.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ListenTogetherSetupScreen(
    chatId: String,
    availableTracks: List<MainViewModel.ListenTogetherTrack>,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onStart: (playlist: List<MainViewModel.ListenTogetherTrack>, name: String) -> Unit,
) {
    var playlistName by remember { mutableStateOf("") }
    var playlist by remember { mutableStateOf<List<MainViewModel.ListenTogetherTrack>>(emptyList()) }
    val isUploading by viewModel.listenTogetherUploading.collectAsState()
    val savedPlaylist by viewModel.savedListenTogetherPlaylist.collectAsState()

    val setupMusicLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri -> viewModel.uploadListenTogetherTrack(chatId, uri) }
    }

    LaunchedEffect(chatId) {
        viewModel.loadListenTogetherTracks(chatId)
        viewModel.loadSavedListenTogetherPlaylist(chatId)
    }

    // Gespeicherte Playlist vorausfüllen, wenn noch keine lokale Auswahl vorhanden
    LaunchedEffect(savedPlaylist) {
        if (playlist.isEmpty() && savedPlaylist.isNotEmpty()) {
            playlist = savedPlaylist
        }
    }

    val available = availableTracks.filter { track -> playlist.none { it.url == track.url } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                }
                Text(
                    text = "Playlist einrichten",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            }

            // Upload-Statusanzeige
            if (isUploading) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Wird hochgeladen…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Playlist name field
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Lege eine neue Playlist an:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    placeholder = { Text("Playlist-Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Playlist (selected tracks)
            Text(
                text = "Deine Playlist (${playlist.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                itemsIndexed(playlist) { index, track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                playlist = playlist.toMutableList().also { it.removeAt(index) }
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.bodyMedium)
                            if (track.artist.isNotBlank()) {
                                Text(
                                    track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Entfernen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (playlist.isEmpty()) {
                    item {
                        Text(
                            text = "Noch keine Titel hinzugefügt",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            HorizontalDivider()

            // Available tracks
            Text(
                text = "Verfügbare Titel (${available.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                items(available) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { playlist = playlist + track }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AudioFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.bodyMedium)
                            if (track.artist.isNotBlank()) {
                                Text(
                                    track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Hinzufügen",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (available.isEmpty()) {
                    item {
                        Text(
                            text = "Keine Titel vorhanden",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            HorizontalDivider()

            // Bottom bar: picker + save
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !isUploading) { setupMusicLauncher.launch("audio/*") },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.LibraryMusic,
                            contentDescription = "Musik hinzufügen",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isUploading) "Wird hochgeladen…" else "füge nun deine Musik hinzu",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUploading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { onStart(playlist, playlistName) },
                    enabled = playlist.isNotEmpty() && !isUploading
                ) {
                    Text("Speichern")
                }
            }
        }
    }
}

/**
 * Info-Dialog für ein GIF oder Sticker.
 * Zeigt: Vorschau (oben mittig), Auflösung, Ersteller, anonymisierten Share-Link.
 */
@Composable
private fun GifStickerInfoDialog(
    mediaUrl: String,
    mediaType: String, // "gif" oder "sticker"
    viewModel: MainViewModel?,
    onDismiss: () -> Unit
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Anonymisierter Share-Link: https://letheapp.de/stickys={base64}
    val shareUrl = remember(mediaUrl) {
        val encoded = android.util.Base64.encodeToString(
            mediaUrl.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )
        "https://letheapp.de/stickys?id=$encoded"
    }

    // Auflösung via Coil painter
    val painter = rememberAsyncImagePainter(model = mediaUrl)
    val painterState = painter.state
    val resolution = remember(painterState) {
        when (val s = painterState) {
            is AsyncImagePainter.State.Success -> {
                val w = s.result.drawable.intrinsicWidth
                val h = s.result.drawable.intrinsicHeight
                if (w > 0 && h > 0) "${w}×${h}" else null
            }
            else -> null
        }
    }

    // Creator laden (Sticker: vom Server, GIF: "GIPHY")
    var creator by remember { mutableStateOf<String?>(null) }
    var creatorLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(mediaUrl) {
        if (mediaType == "gif") {
            creator = "GIPHY"
            creatorLoaded = true
        } else if (viewModel != null && mediaUrl.contains("/uploads/stickers/")) {
            // Relativen Pfad für die API extrahieren
            val relUrl = if (mediaUrl.startsWith("http")) {
                try { java.net.URL(mediaUrl).path } catch (_: Exception) { mediaUrl }
            } else mediaUrl
            viewModel.loadStickerInfo(relUrl) { name ->
                creator = name
                creatorLoaded = true
            }
        } else {
            creatorLoaded = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Schließen-Button oben rechts
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (mediaType == "gif") "GIF" else "Sticker",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd).size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen", modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Vorschau zentriert
                AsyncImage(
                    model = mediaUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = if (mediaType == "sticker") ContentScale.Fit else ContentScale.Crop
                )

                Spacer(Modifier.height(16.dp))

                // Info-Zeilen
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Auflösung
                    if (resolution != null) {
                        InfoRow(label = "Auflösung", value = resolution)
                    }

                    // Ersteller
                    if (creatorLoaded) {
                        InfoRow(label = "Ersteller", value = creator ?: "Unbekannt")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Ersteller", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(80.dp))
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        }
                    }

                    // Share-Link
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "Teilen",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = shareUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(shareUrl))
                                    android.widget.Toast.makeText(context, "Link kopiert", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Kopieren", modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, shareUrl)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Teilen"))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Teilen", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GroupInfoScreen — Vollbild-Ansicht: Gruppeninfo, Medien, Mitglieder
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupInfoScreen(
    group: com.securechat.app.data.local.GroupEntity?,
    groupId: String,
    messages: List<MessageEntity>,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val groupMembersMap by viewModel.groupMembers.collectAsState()
    val members = groupMembersMap[groupId] ?: emptyList()
    val myUser by viewModel.currentUser.collectAsState()
    val allAppointments by viewModel.appointments.collectAsState()
    val upcomingAppointments = remember(allAppointments, groupId) {
        val now = System.currentTimeMillis()
        allAppointments.values.filter { apt ->
            apt.groupId == groupId && apt.status == "finalized" && !apt.finalDate.isNullOrBlank() &&
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                sdf.isLenient = true
                (sdf.parse(apt.finalDate!!)?.time ?: 0L) >= now
            } catch (e: Exception) { false }
        }.sortedBy { apt ->
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                sdf.isLenient = true
                sdf.parse(apt.finalDate!!)?.time ?: Long.MAX_VALUE
            } catch (e: Exception) { Long.MAX_VALUE }
        }
    }

    LaunchedEffect(groupId) { viewModel.loadGroupAppointments(groupId) }

    val groupedMedia = remember(messages) {
        val map = linkedMapOf(
            "Fotos & Videos" to messages.filter { it.mediaType in listOf("image", "multi_image", "video") },
            "Sticker/GIFs"   to messages.filter { it.mediaType in listOf("sticker", "gif") },
            "Audios"         to messages.filter { it.mediaType in listOf("audio", "audio_music") },
            "Dokumente"      to messages.filter { it.mediaType in listOf("document", "file", "pdf") },
            "3D Dateien"     to messages.filter { it.mediaType in listOf("3dprint", "3d") }
        )
        map.filter { (_, items) -> items.isNotEmpty() }
    }

    var mainTab by remember { mutableIntStateOf(0) }
    val mainTabs = listOf("Medien", "Mitglieder")
    val mediaTabs = groupedMedia.keys.toList()
    var selectedMediaTab by remember { mutableIntStateOf(0) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { mediaTabs.size }
    var mediaActionMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var mediaActionUrl by remember { mutableStateOf<String?>(null) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var fullscreenVideoUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pagerState.currentPage) { if (selectedMediaTab != pagerState.currentPage) selectedMediaTab = pagerState.currentPage }
    LaunchedEffect(selectedMediaTab) { if (pagerState.currentPage != selectedMediaTab) pagerState.animateScrollToPage(selectedMediaTab) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            group?.name ?: groupId,
                            fontWeight = FontWeight.Bold,
                            color = topBarTitleColor()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück",
                                tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Gruppen-Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val groupInfoImgUrl = group?.groupImageUrl?.let { url ->
                        if (url.startsWith("http")) url else "https://letheapp.de$url"
                    }
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .then(if (groupInfoImgUrl != null) Modifier.clickable { fullscreenImageUrl = groupInfoImgUrl } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (groupInfoImgUrl != null) {
                            AsyncImage(
                                model = groupInfoImgUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Group, contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(group?.name ?: groupId, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (!group?.description.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            group!!.description!!,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${members.size} Mitglieder", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (upcomingAppointments.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Anstehende Termine",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start
                        )
                        Spacer(Modifier.height(4.dp))
                        upcomingAppointments.forEach { apt ->
                            val formattedDate = remember(apt.finalDate) {
                                try {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                                    val out = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                                    sdf.parse(apt.finalDate!!)?.let { out.format(it) } ?: apt.finalDate!!
                                } catch (e: Exception) { apt.finalDate ?: "" }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Event,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(apt.title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(formattedDate, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    "${apt.going.size} zusagen",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Haupt-Tabs: Medien | Mitglieder
                TabRow(
                    selectedTabIndex = mainTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    mainTabs.forEachIndexed { index, name ->
                        Tab(
                            selected = mainTab == index,
                            onClick = { mainTab = index },
                            text = { Text(name, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }

                when (mainTab) {
                    0 -> {
                        // Medien-Tab
                        if (mediaTabs.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.PermMedia, contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                    Spacer(Modifier.height(8.dp))
                                    Text("Keine Medien in dieser Gruppe",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                        } else {
                            ScrollableTabRow(
                                selectedTabIndex = selectedMediaTab,
                                edgePadding = 0.dp,
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                mediaTabs.forEachIndexed { index, tabName ->
                                    val count = groupedMedia[tabName]?.size ?: 0
                                    Tab(
                                        selected = selectedMediaTab == index,
                                        onClick = { selectedMediaTab = index },
                                        text = { Text("$tabName ($count)", maxLines = 1,
                                            style = MaterialTheme.typography.labelMedium) }
                                    )
                                }
                            }
                            androidx.compose.foundation.pager.HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                val tabName = mediaTabs.getOrNull(page) ?: return@HorizontalPager
                                val items = groupedMedia[tabName] ?: emptyList()
                                if (tabName == "Fotos & Videos" || tabName == "Sticker/GIFs") {
                                    val galleryEntries = remember(items) { buildGalleryEntries(items) }
                                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        lazyGridItems(items = galleryEntries, key = { it.entryKey }) { entry ->
                                            val msg = entry.message
                                            Box(
                                                modifier = Modifier
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .clickable {
                                                        mediaActionMessage = msg
                                                        mediaActionUrl = entry.url
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                when (msg.mediaType) {
                                                    "image", "multi_image", "sticker", "gif" -> Image(
                                                        painter = rememberAsyncImagePainter(entry.url),
                                                        contentDescription = null,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    "video" -> {
                                                        VideoThumbnailImage(url = msg.mediaUrl ?: "", viewModel = viewModel, modifier = Modifier.fillMaxSize())
                                                        Icon(Icons.Default.PlayCircle, null,
                                                            modifier = Modifier.size(32.dp).align(Alignment.Center),
                                                            tint = Color.White.copy(alpha = 0.85f))
                                                    }
                                                    else -> Icon(Icons.Default.PermMedia, null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(items = items, key = { it.localId }) { msg ->
                                            Box(modifier = Modifier.clickable { mediaActionMessage = msg }) {
                                                MediaGalleryItem(message = msg, viewModel = viewModel)
                                            }
                                            HorizontalDivider(Modifier.padding(start = 80.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Mitglieder-Tab
                        val isCreator = group?.createdBy == myUser?.userId
                        if (members.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(members, key = { it.userId }) { member ->
                                    GroupMemberRow(
                                        member = member,
                                        isCreator = isCreator,
                                        onRoleChange = { newRole ->
                                            viewModel.setGroupMemberRole(groupId, member.userId, newRole) {}
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Aktions-Sheet
    val actionMsg = mediaActionMessage
    if (actionMsg != null) {
        MediaActionSheet(
            message = actionMsg,
            viewModel = viewModel,
            onDismiss = { mediaActionMessage = null },
            onViewFullscreen = { msg ->
                mediaActionMessage = null
                when (msg.mediaType) {
                    "image", "sticker", "gif" -> fullscreenImageUrl = msg.mediaUrl
                    "multi_image" -> fullscreenImageUrl = mediaActionUrl
                        ?: msg.mediaUrl?.let { parseMultiImageUrls(it).firstOrNull() }
                    "video" -> fullscreenVideoUrl = msg.mediaUrl
                }
            }
        )
    }
    val fsImg = fullscreenImageUrl
    if (fsImg != null) {
        Dialog(onDismissRequest = { fullscreenImageUrl = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val fsImgScope = rememberCoroutineScope()
            val context = androidx.compose.ui.platform.LocalContext.current
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { fullscreenImageUrl = null }, contentAlignment = Alignment.Center) {
                AsyncImage(model = ImageRequest.Builder(context).data(fsImg).size(2048, 2048).scale(coil.size.Scale.FIT).build(), contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        android.widget.Toast.makeText(context, "Wird vorbereitet…", android.widget.Toast.LENGTH_SHORT).show()
                        fsImgScope.launch {
                            val ok = quickShareMediaFile(context, fsImg, "image")
                            if (!ok) android.widget.Toast.makeText(context, "Teilen fehlgeschlagen", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.IosShare, contentDescription = "Teilen", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    IconButton(onClick = { fullscreenImageUrl = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GroupMemberProfileDialog — Profil eines Gruppen-Mitglieds + dessen Medien
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupMemberProfileDialog(
    memberInfo: com.securechat.app.data.network.GroupMemberInfo?,
    memberId: String,
    messages: List<MessageEntity>,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val displayName = memberInfo?.name ?: memberInfo?.fakeNumber ?: memberId
    val avatarUrl = memberInfo?.profileImageUrl?.let {
        if (it.startsWith("http")) it else "https://letheapp.de$it"
    }

    val groupedMedia = remember(messages) {
        val map = linkedMapOf(
            "Fotos & Videos" to messages.filter { it.mediaType in listOf("image", "multi_image", "video") },
            "Sticker/GIFs"   to messages.filter { it.mediaType in listOf("sticker", "gif") },
            "Audios"         to messages.filter { it.mediaType in listOf("audio", "audio_music") },
            "Dokumente"      to messages.filter { it.mediaType in listOf("document", "file", "pdf") },
            "3D Dateien"     to messages.filter { it.mediaType in listOf("3dprint", "3d") }
        )
        map.filter { (_, items) -> items.isNotEmpty() }
    }
    val tabs = groupedMedia.keys.toList()
    var selectedTab by remember { mutableIntStateOf(0) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { tabs.size }
    var mediaActionMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var mediaActionUrl by remember { mutableStateOf<String?>(null) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var showEnlargedGroupAvatar by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) { if (selectedTab != pagerState.currentPage) selectedTab = pagerState.currentPage }
    LaunchedEffect(selectedTab) { if (pagerState.currentPage != selectedTab) pagerState.animateScrollToPage(selectedTab) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(displayName, fontWeight = FontWeight.Bold, color = topBarTitleColor()) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück",
                                tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Profil-Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .then(if (avatarUrl != null) Modifier.clickable { showEnlargedGroupAvatar = true } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarUrl != null) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Person, null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    if (showEnlargedGroupAvatar && avatarUrl != null) {
                        Dialog(
                            onDismissRequest = { showEnlargedGroupAvatar = false },
                            properties = DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.9f))
                                    .clickable { showEnlargedGroupAvatar = false },
                                contentAlignment = Alignment.Center
                            ) {
                                val maxH = (LocalConfiguration.current.screenHeightDp / 2).dp
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = maxH),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(displayName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (!memberInfo?.fakeNumber.isNullOrBlank() && memberInfo?.fakeNumber != displayName) {
                        Spacer(Modifier.height(2.dp))
                        Text(memberInfo!!.fakeNumber!!, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Medien-Tabs (nur Medien die dieser User in die Gruppe gesendet hat)
                if (tabs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PermMedia, null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                            Spacer(Modifier.height(8.dp))
                            Text("Keine Medien von diesem Mitglied",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                } else {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 0.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        tabs.forEachIndexed { index, tabName ->
                            val count = groupedMedia[tabName]?.size ?: 0
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text("$tabName ($count)", maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val tabName = tabs.getOrNull(page) ?: return@HorizontalPager
                        val items = groupedMedia[tabName] ?: emptyList()
                        if (tabName == "Fotos & Videos" || tabName == "Sticker/GIFs") {
                            val galleryEntries = remember(items) { buildGalleryEntries(items) }
                            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                lazyGridItems(items = galleryEntries, key = { it.entryKey }) { entry ->
                                    val msg = entry.message
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                mediaActionMessage = msg
                                                mediaActionUrl = entry.url
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when (msg.mediaType) {
                                            "image", "multi_image", "sticker", "gif" -> Image(
                                                painter = rememberAsyncImagePainter(entry.url),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            "video" -> {
                                                VideoThumbnailImage(url = msg.mediaUrl ?: "", viewModel = viewModel, modifier = Modifier.fillMaxSize())
                                                Icon(Icons.Default.PlayCircle, null,
                                                    modifier = Modifier.size(32.dp).align(Alignment.Center),
                                                    tint = Color.White.copy(alpha = 0.85f))
                                            }
                                            else -> Icon(Icons.Default.PermMedia, null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(items = items, key = { it.localId }) { msg ->
                                    Box(modifier = Modifier.clickable { mediaActionMessage = msg }) {
                                        MediaGalleryItem(message = msg, viewModel = viewModel)
                                    }
                                    HorizontalDivider(Modifier.padding(start = 80.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val actionMsg = mediaActionMessage
    if (actionMsg != null) {
        MediaActionSheet(
            message = actionMsg,
            viewModel = viewModel,
            onDismiss = { mediaActionMessage = null },
            onViewFullscreen = { msg ->
                mediaActionMessage = null
                when (msg.mediaType) {
                    "image", "sticker", "gif" -> fullscreenImageUrl = msg.mediaUrl
                    "multi_image" -> fullscreenImageUrl = mediaActionUrl
                        ?: msg.mediaUrl?.let { parseMultiImageUrls(it).firstOrNull() }
                }
            }
        )
    }
    val fsImg = fullscreenImageUrl
    if (fsImg != null) {
        Dialog(onDismissRequest = { fullscreenImageUrl = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val fsImgScope = rememberCoroutineScope()
            val context = androidx.compose.ui.platform.LocalContext.current
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { fullscreenImageUrl = null }, contentAlignment = Alignment.Center) {
                AsyncImage(model = ImageRequest.Builder(context).data(fsImg).size(2048, 2048).scale(coil.size.Scale.FIT).build(), contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        android.widget.Toast.makeText(context, "Wird vorbereitet…", android.widget.Toast.LENGTH_SHORT).show()
                        fsImgScope.launch {
                            val ok = quickShareMediaFile(context, fsImg, "image")
                            if (!ok) android.widget.Toast.makeText(context, "Teilen fehlgeschlagen", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.IosShare, contentDescription = "Teilen", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    IconButton(onClick = { fullscreenImageUrl = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }
}

// ======================================================================
// Gruppen-Medien-Info Dialog
// ======================================================================

@Composable
private fun GroupMediaInfoDialog(
    groupId: String,
    messageId: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    LaunchedEffect(messageId) { viewModel.loadGroupMediaViews(groupId, messageId) }
    val views by viewModel.groupMediaViews.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Angesehen von",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))
                if (views.isEmpty()) {
                    Text(
                        "Noch niemand hat dieses Medium angesehen.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                } else {
                    views.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!entry.profileImageUrl.isNullOrBlank()) {
                                    coil.compose.AsyncImage(
                                        model = entry.profileImageUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    entry.name ?: entry.fakeNumber ?: "Unbekannt",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                if (!entry.fakeNumber.isNullOrBlank()) {
                                    Text(
                                        entry.fakeNumber,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                    )
                                }
                            }
                            if (!entry.viewedAt.isNullOrBlank()) {
                                val timeStr = remember(entry.viewedAt) {
                                    try {
                                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                                        val date = sdf.parse(entry.viewedAt.take(19))
                                        java.text.SimpleDateFormat("dd.MM. HH:mm", java.util.Locale.getDefault()).format(date!!)
                                    } catch (_: Exception) { "" }
                                }
                                Text(
                                    timeStr,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Schließen") }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Kontakt-Auswahl-Dialog: Liste aller Kontakte mit Bild, Username, LetheID, +
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ContactPickerDialog(
    contacts: List<com.securechat.app.data.local.ContactEntity>,
    onDismiss: () -> Unit,
    onSendContact: (com.securechat.app.data.local.ContactEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(contacts, query) {
        if (query.isBlank()) contacts
        else contacts.filter { c ->
            val name = c.customAlias ?: c.username ?: c.fakeNumber
            name.contains(query, ignoreCase = true) || c.fakeNumber.contains(query, ignoreCase = true)
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Kontakt senden",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Suchen…") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered) { contact ->
                        val displayName = contact.customAlias ?: contact.username ?: contact.fakeNumber
                        val avatarUrl = contact.profileImageUrl
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (avatarUrl != null && !contact.isAnonymous) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (contact.isAnonymous) "Anonym" else displayName,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    contact.letheId ?: contact.fakeNumber,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onSendContact(contact) }) {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    contentDescription = "Senden",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(thickness = 0.5.dp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Abbrechen") }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Kontaktkarten-Blase: zeigt Profilbild, Username, LetheID/FakeNummer,
// sowie einen "Hinzufügen"-Button für den Empfänger.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ContactCardBubble(
    json: org.json.JSONObject,
    textColor: Color,
    viewModel: MainViewModel?
) {
    val username = json.optString("username", "Unbekannt")
    val fakeNumber = json.optString("fake_number", "")
    // lethe_id bevorzugen (neues Format) – Fallback auf fakeNumber
    val letheId = json.optString("lethe_id", "").ifBlank { fakeNumber }
    val profileImage = json.optString("profile_image", "").takeIf { it.isNotBlank() }
    val isAnonymous = json.optBoolean("is_anonymous", false)

    val currentUser by remember(viewModel) {
        viewModel?.currentUser ?: kotlinx.coroutines.flow.MutableStateFlow<com.securechat.app.data.local.UserEntity?>(null)
    }.collectAsState()
    val existingContacts by remember(viewModel) {
        viewModel?.contacts ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    }.collectAsState(initial = emptyList())
    val userId = json.optString("user_id", "")
    val alreadyContact = existingContacts.any { it.userId == userId } || userId == currentUser?.userId
    var added by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier = Modifier
            .widthIn(min = 200.dp, max = 260.dp)
            .padding(4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (profileImage != null && !isAnonymous) {
                    AsyncImage(
                        model = profileImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isAnonymous) "Anonym" else username,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!isAnonymous) {
                        Text(
                            letheId,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.HorizontalDivider(thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (!alreadyContact && !added && viewModel != null && letheId.isNotBlank()) {
                        viewModel.addContact(letheId, username)
                        added = true
                    }
                },
                enabled = !alreadyContact && !added,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = if (alreadyContact || added) Icons.Default.Check else Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (alreadyContact) "Bereits Kontakt" else if (added) "Hinzugefügt" else "Hinzufügen",
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DetachedMusicPlayerOverlay
// Schwebender Mini-Player unterhalb der TopBar für schnellen Zugriff auf den
// zuletzt gesendeten Musik-Titel im Chat, ohne weit scrollen zu müssen.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DetachedMusicPlayerOverlay(
    viewModel: MainViewModel,
    allChatMusicUrls: List<String>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Welchen Track zeigt dieser Player an?
    // Startet mit dem letzten Track; wechselt beim Skippen über das ViewModel.
    val currentMusicUrl  by viewModel.currentMusicUrl.collectAsState()
    val musicIsPlaying   by viewModel.musicIsPlaying.collectAsState()
    val musicIsPrepared  by viewModel.musicIsPrepared.collectAsState()
    val musicProgress    by viewModel.musicProgress.collectAsState()
    val musicCurrentMs   by viewModel.musicCurrentMs.collectAsState()
    val musicTotalMs     by viewModel.musicTotalMs.collectAsState()
    val musicTitle       by viewModel.musicTitle.collectAsState()
    val musicArtist      by viewModel.musicArtist.collectAsState()
    val musicCoverBitmap by viewModel.musicCoverBitmap.collectAsState()

    // Cast-Zustand
    val castAvailable  by viewModel.castDiscoveryManager.castAvailable.collectAsState()
    val isCasting      by viewModel.castDiscoveryManager.isCasting.collectAsState()
    val castCurrentUrl by viewModel.castDiscoveryManager.castCurrentUrl.collectAsState()
    val castIsPlaying  by viewModel.castDiscoveryManager.castIsPlaying.collectAsState()
    val castCurrentMs  by viewModel.castDiscoveryManager.castCurrentMs.collectAsState()
    val castTotalMs    by viewModel.castDiscoveryManager.castTotalMs.collectAsState()

    var showCastDeviceDialog by remember { mutableStateOf(false) }
    var showMediaPlayerInstallDialog by remember { mutableStateOf(false) }

    // Welche URL wird angezeigt: aktuelle ViewModel-URL wenn im Playlist-Bereich, sonst letzter Track
    val displayUrl = remember(currentMusicUrl, allChatMusicUrls) {
        if (currentMusicUrl != null && allChatMusicUrls.contains(currentMusicUrl)) {
            currentMusicUrl!!
        } else {
            allChatMusicUrls.last()
        }
    }
    val isActive          = currentMusicUrl == displayUrl
    val isActivelyCasting = isCasting && (castCurrentUrl == displayUrl || castCurrentUrl == viewModel.toCastUrl(displayUrl))
    val effectiveTotalMs  = if (isActivelyCasting && castTotalMs > 0L) castTotalMs else if (isActive) musicTotalMs else 0L
    val effectiveCurrentMs = if (isActivelyCasting) castCurrentMs else if (isActive) musicCurrentMs else 0L
    val effectiveProgress  = if (effectiveTotalMs > 0L) effectiveCurrentMs.toFloat() / effectiveTotalMs else if (isActive) musicProgress else 0f

    val cachedMeta    = remember(displayUrl, isActive) { viewModel.getCachedTitleArtist(displayUrl) }
    val displayTitle  = if (isActive) musicTitle  else cachedMeta?.first  ?: "Musik"
    val displayArtist = if (isActive) musicArtist else cachedMeta?.second ?: ""
    val displayCover  = if (isActive) musicCoverBitmap else viewModel.getCachedMusicCover(displayUrl)

    // Playlist-Index für Prev/Next
    val currentIndex = allChatMusicUrls.indexOf(displayUrl)
    val hasPrev = currentIndex > 0
    val hasNext = currentIndex < allChatMusicUrls.size - 1

    fun fmtMs(ms: Long): String {
        val s = (ms / 1000L).coerceAtLeast(0L).toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }

    Card(
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier  = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {

            // ── Titelzeile ──────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // Cover-Bild oder Platzhalter
                if (displayCover != null) {
                    Image(
                        bitmap = displayCover.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text     = displayTitle.ifBlank { "Musik" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (displayArtist.isNotBlank()) {
                        Text(
                            text     = displayArtist,
                            fontSize = 11.sp,
                            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (allChatMusicUrls.size > 1) {
                    Text(
                        text     = "${currentIndex + 1} / ${allChatMusicUrls.size}",
                        fontSize = 11.sp,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                // Vollbild – öffnet die eigenständige Lethe-Media-Player-App
                IconButton(
                    onClick  = {
                        if (!com.securechat.app.MediaPlayerLauncher.open(context)) {
                            showMediaPlayerInstallDialog = true
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Fullscreen,
                        contentDescription = "Im Lethe Media Player öffnen",
                        modifier = Modifier.size(20.dp),
                        tint     = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── Fortschrittsbalken ──────────────────────────────────────────
            val primaryColor  = MaterialTheme.colorScheme.primary
            val trackColor    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(effectiveTotalMs, isActivelyCasting) {
                        detectTapGestures(onTap = { pos ->
                            if (effectiveTotalMs > 0L) {
                                val fraction = (pos.x / size.width).coerceIn(0f, 1f)
                                val seekMs = (fraction * effectiveTotalMs).toLong()
                                if (isActivelyCasting) {
                                    viewModel.castDiscoveryManager.castSeekTo(seekMs)
                                } else {
                                    viewModel.seekMusicTo(seekMs)
                                }
                            }
                        })
                    }
            ) {
                val bars   = 36
                val bw     = size.width / (bars * 2 - 1)
                val center = size.height / 2f
                val rnd    = java.util.Random(42)
                for (i in 0 until bars) {
                    val x      = i * bw * 2
                    val barH   = (0.25f + rnd.nextFloat() * 0.75f) * size.height
                    val filled = (i.toFloat() / bars) < effectiveProgress
                    drawRoundRect(
                        color        = if (filled) primaryColor else trackColor,
                        topLeft      = androidx.compose.ui.geometry.Offset(x, center - barH / 2),
                        size         = Size(bw, barH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 2)
                    )
                }
            }

            // ── Zeitzeile + Einklappen-Pfeil ────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Einklappen-Button links unter Spieldauer
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtMs(effectiveCurrentMs), fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Einklappen",
                            modifier = Modifier.size(16.dp),
                            tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }
                Text(fmtMs(effectiveTotalMs), fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }

            // ── Steuerleiste ────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Zurück
                IconButton(
                    onClick  = {
                        if (hasPrev) viewModel.toggleMusicPlayback(allChatMusicUrls[currentIndex - 1], allChatMusicUrls)
                    },
                    enabled  = hasPrev,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Zurück",
                        modifier = Modifier.size(24.dp))
                }
                // Play / Pause
                val effectiveIsPlaying = if (isActivelyCasting) castIsPlaying else (isActive && musicIsPlaying)
                FilledIconButton(
                    onClick  = {
                        if (isActivelyCasting) viewModel.castDiscoveryManager.castPlayPause()
                        else viewModel.toggleMusicPlayback(displayUrl, allChatMusicUrls)
                    },
                    modifier = Modifier.size(44.dp),
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (effectiveIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (effectiveIsPlaying) "Pause" else "Play",
                        modifier = Modifier.size(24.dp)
                    )
                }
                // Weiter
                IconButton(
                    onClick  = {
                        if (hasNext) viewModel.toggleMusicPlayback(allChatMusicUrls[currentIndex + 1], allChatMusicUrls)
                    },
                    enabled  = hasNext,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Weiter",
                        modifier = Modifier.size(24.dp))
                }
                // Cast – öffnet den Lethe Media Player mit der vollständigen Stream-URL
                IconButton(
                    onClick  = {
                        if (!com.securechat.app.MediaPlayerLauncher.openWithStreamUrl(context, viewModel.toCastUrl(displayUrl))) {
                            showMediaPlayerInstallDialog = true
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Cast,
                        contentDescription = "Im Lethe Media Player casten",
                        modifier = Modifier.size(20.dp),
                        tint     = if (isActivelyCasting) Color(0xFF4FC3F7) else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // ── Cast-Geräte-Dialog ──────────────────────────────────────────────────
    if (showCastDeviceDialog) {
        val castDevices by viewModel.castDiscoveryManager.devices.collectAsState()

        AlertDialog(
            onDismissRequest = { showCastDeviceDialog = false },
            icon = { Icon(Icons.Default.Cast, contentDescription = null, tint = Color(0xFF4FC3F7)) },
            title = { Text("Audio-Ausgabe", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Aktiver Stream-Status
                    Text(
                        if (castIsPlaying) "▶  Wird gestreamt" else "⏸  Gestreamt · Pause",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (castTotalMs > 0L) {
                        Text(
                            "${fmtMs(castCurrentMs)} / ${fmtMs(castTotalMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Geräte", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))

                    // Geräteliste – Tippen wechselt/verbindet erneut
                    castDevices.forEach { device ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    showCastDeviceDialog = false
                                    viewModel.castDiscoveryManager.connectToDevice(device)
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                Icons.Default.Cast,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (castDevices.isEmpty()) {
                        Text("Keine Geräte gefunden",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCastDeviceDialog = false }) {
                    Text("Schließen")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCastDeviceDialog = false
                        viewModel.castDiscoveryManager.stopCasting()
                    }
                ) {
                    Text("Stream beenden", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    // ── Hinweis, falls die Lethe-Media-Player-App fehlt ─────────────────────
    if (showMediaPlayerInstallDialog) {
        com.securechat.app.MediaPlayerInstallDialog(
            onDismiss = { showMediaPlayerInstallDialog = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Bild-Editor: Zeichnen auf empfangenen Fotos und direkt in den Chat senden
// ---------------------------------------------------------------------------

private data class DrawnPath(
    val path: android.graphics.Path,
    val color: androidx.compose.ui.graphics.Color,
    val strokeWidthPx: Float
)

private fun exportEditedImageToFile(
    context: android.content.Context,
    source: android.graphics.Bitmap,
    paths: List<DrawnPath>,
    canvasWidthPx: Int,
    canvasHeightPx: Int
): java.io.File? {
    return try {
        val w = canvasWidthPx.coerceAtLeast(1)
        val h = canvasHeightPx.coerceAtLeast(1)
        val result = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        canvas.drawColor(android.graphics.Color.BLACK)

        // Bild passend skaliert und zentriert zeichnen
        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()
        val scale = minOf(w / srcW, h / srcH)
        val drawW = srcW * scale
        val drawH = srcH * scale
        val offsetX = (w - drawW) / 2f
        val offsetY = (h - drawH) / 2f
        canvas.drawBitmap(
            source,
            null,
            android.graphics.RectF(offsetX, offsetY, offsetX + drawW, offsetY + drawH),
            null
        )

        // Alle Zeichenpfade rendern
        paths.forEach { dp ->
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(
                    (dp.color.alpha * 255).toInt(),
                    (dp.color.red * 255).toInt(),
                    (dp.color.green * 255).toInt(),
                    (dp.color.blue * 255).toInt()
                )
                strokeWidth = dp.strokeWidthPx
                style = android.graphics.Paint.Style.STROKE
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
                isAntiAlias = true
            }
            canvas.drawPath(dp.path, paint)
        }

        val file = java.io.File(context.cacheDir, "lethe_edited_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { result.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
        result.recycle()
        file
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun ImageEditorDialog(
    imageUrl: String,
    chatId: String,
    isGroup: Boolean,
    viewModel: MainViewModel?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Quell-Bitmap aus URL laden
    var sourceBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(imageUrl) {
        withContext(Dispatchers.IO) {
            try {
                val loader = coil.ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                val drawable = (result as? coil.request.SuccessResult)?.drawable
                sourceBitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            } catch (_: Exception) {}
        }
    }

    // Zeichen-State
    val drawnPaths = remember { mutableStateListOf<DrawnPath>() }
    var currentAndroidPath by remember { mutableStateOf<android.graphics.Path?>(null) }
    var currentColor by remember { mutableStateOf(androidx.compose.ui.graphics.Color.Red) }
    var currentStrokeWidth by remember { mutableFloatStateOf(14f) }
    var canvasWidthPx by remember { mutableIntStateOf(0) }
    var canvasHeightPx by remember { mutableIntStateOf(0) }
    var isSending by remember { mutableStateOf(false) }

    val palette = listOf(
        androidx.compose.ui.graphics.Color.Red,
        androidx.compose.ui.graphics.Color(0xFFFF6D00),
        androidx.compose.ui.graphics.Color.Yellow,
        androidx.compose.ui.graphics.Color(0xFF00E676),
        androidx.compose.ui.graphics.Color.Cyan,
        androidx.compose.ui.graphics.Color.White,
        androidx.compose.ui.graphics.Color.Black
    )
    val strokeSizes = listOf(6f, 14f, 24f, 36f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black)
        ) {
            val bmp = sourceBitmap

            // Zeichenfläche
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        canvasWidthPx = coords.size.width
                        canvasHeightPx = coords.size.height
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val path = android.graphics.Path()
                                path.moveTo(offset.x, offset.y)
                                currentAndroidPath = path
                            },
                            onDrag = { change, _ ->
                                currentAndroidPath?.lineTo(change.position.x, change.position.y)
                            },
                            onDragEnd = {
                                currentAndroidPath?.let { p ->
                                    drawnPaths.add(DrawnPath(p, currentColor, currentStrokeWidth))
                                }
                                currentAndroidPath = null
                            },
                            onDragCancel = { currentAndroidPath = null }
                        )
                    }
            ) {
                // Bild zeichnen
                if (bmp != null) {
                    val imgBitmap = bmp.asImageBitmap()
                    val bmpW = bmp.width.toFloat()
                    val bmpH = bmp.height.toFloat()
                    val sc = minOf(size.width / bmpW, size.height / bmpH)
                    val dw = (bmpW * sc).toInt()
                    val dh = (bmpH * sc).toInt()
                    val ox = ((size.width - dw) / 2f).toInt()
                    val oy = ((size.height - dh) / 2f).toInt()
                    drawImage(
                        image = imgBitmap,
                        dstOffset = androidx.compose.ui.unit.IntOffset(ox, oy),
                        dstSize = androidx.compose.ui.unit.IntSize(dw, dh)
                    )
                }
                // Bestätigte Pfade zeichnen
                drawIntoCanvas { composeCanvas ->
                    val nativeCanvas = composeCanvas.nativeCanvas
                    drawnPaths.forEach { dp ->
                        nativeCanvas.drawPath(dp.path, android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(
                                (dp.color.alpha * 255).toInt(),
                                (dp.color.red * 255).toInt(),
                                (dp.color.green * 255).toInt(),
                                (dp.color.blue * 255).toInt()
                            )
                            strokeWidth = dp.strokeWidthPx
                            style = android.graphics.Paint.Style.STROKE
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            strokeJoin = android.graphics.Paint.Join.ROUND
                            isAntiAlias = true
                        })
                    }
                    // Aktuellen Pfad zeichnen
                    currentAndroidPath?.let { path ->
                        nativeCanvas.drawPath(path, android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(
                                (currentColor.alpha * 255).toInt(),
                                (currentColor.red * 255).toInt(),
                                (currentColor.green * 255).toInt(),
                                (currentColor.blue * 255).toInt()
                            )
                            strokeWidth = currentStrokeWidth
                            style = android.graphics.Paint.Style.STROKE
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            strokeJoin = android.graphics.Paint.Join.ROUND
                            isAntiAlias = true
                        })
                    }
                }
            }

            // Obere Leiste: Rückgängig | Senden | Schließen
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (drawnPaths.isNotEmpty()) {
                    IconButton(onClick = { if (drawnPaths.isNotEmpty()) drawnPaths.removeAt(drawnPaths.lastIndex) }) {
                        Icon(Icons.Default.Undo, contentDescription = "Rückgängig", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(26.dp))
                    }
                }
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp).padding(4.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = {
                        isSending = true
                        val pathsSnapshot = drawnPaths.toList()
                        val cw = canvasWidthPx
                        val ch = canvasHeightPx
                        coroutineScope.launch {
                            val bmp2 = sourceBitmap
                            if (bmp2 != null && cw > 0 && ch > 0) {
                                val file = withContext(Dispatchers.IO) {
                                    exportEditedImageToFile(context, bmp2, pathsSnapshot, cw, ch)
                                }
                                if (file != null) {
                                    val uri = Uri.fromFile(file)
                                    if (isGroup) viewModel?.sendGroupMediaMessage(chatId, uri, "image")
                                    else viewModel?.sendMediaMessage(chatId, uri, "image")
                                }
                            }
                            onDismiss()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Senden",
                            tint = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Schließen", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(26.dp))
                }
            }

            // Untere Werkzeug-Leiste: Strichstärke + Farbpalette
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Strichstärke-Auswahl
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    strokeSizes.forEach { sz ->
                        val selected = currentStrokeWidth == sz
                        Box(
                            modifier = Modifier
                                .size(if (selected) 38.dp else 32.dp)
                                .clip(CircleShape)
                                .background(if (selected) currentColor.copy(alpha = 0.25f) else androidx.compose.ui.graphics.Color.Transparent)
                                .border(if (selected) 2.dp else 1.dp, androidx.compose.ui.graphics.Color.White, CircleShape)
                                .clickable { currentStrokeWidth = sz },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size((sz / 3f).dp.coerceIn(3.dp, 13.dp))
                                    .clip(CircleShape)
                                    .background(currentColor)
                            )
                        }
                    }
                }
                // Farbpalette
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    palette.forEach { c ->
                        val selected = currentColor == c
                        Box(
                            modifier = Modifier
                                .size(if (selected) 38.dp else 30.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(if (selected) Modifier.border(3.dp, androidx.compose.ui.graphics.Color.White, CircleShape) else Modifier)
                                .clickable { currentColor = c }
                        )
                    }
                }
            }

            // Hinweis wenn Bild noch lädt
            if (bmp == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = androidx.compose.ui.graphics.Color.White
                )
            }
        }
    }
}
