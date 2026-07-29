package com.securechat.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.securechat.app.R
import com.securechat.app.avatarMarkerBitmap
import com.securechat.app.configureOsmdroid
import com.securechat.app.data.local.ContactEntity
import com.securechat.app.getCurrentLocationOnce
import com.securechat.app.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private val liveLocRegex = Regex("""[\uD83D\uDCCD📍]live:(\S+) https://maps\.google\.com/\?q=([-\d.]+),([-\d.]+)""")

private data class LiveLocationPin(
    val senderId: String,
    val senderName: String,
    val lat: Double,
    val lng: Double,
    val durationKey: String,
    val profileImageUrl: String? = null
)

/** Lädt ein Bild als Bitmap über Coil (Hardware-Bitmaps deaktiviert, damit die Pixel
 *  für das runde Marker-Icon gelesen werden können). */
private suspend fun loadAvatarBitmap(context: Context, loader: ImageLoader, url: String?): android.graphics.Bitmap? {
    if (url.isNullOrBlank()) return null
    return try {
        val req = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        val result = loader.execute(req)
        (result.drawable as? BitmapDrawable)?.bitmap
    } catch (_: Exception) { null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMapsScreen(
    chatId: String,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val messages by viewModel.getMessagesForChat(chatId).collectAsState(initial = emptyList())
    val contacts by viewModel.contacts.collectAsState(initial = emptyList())
    val currentUser by viewModel.currentUser.collectAsState()

    var myLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var locationLoading by remember { mutableStateOf(false) }

    // osmdroid einmalig konfigurieren (User-Agent + Cache) und MapView anlegen
    val mapView = remember {
        configureOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(13.0)
        }
    }
    val imageLoader = remember { ImageLoader(context) }
    val avatarCache = remember { mutableMapOf<String, android.graphics.Bitmap>() }

    // osmdroid-Lifecycle an die Composition koppeln
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Build contact lookup map
    val contactMap: Map<String, ContactEntity> = remember(contacts) {
        contacts.associateBy { it.userId }
    }

    val liveMapsMeStr = stringResource(R.string.live_maps_me)

    // Extract the most recent live location per sender from chat messages (only if not expired)
    val now = System.currentTimeMillis()
    val livePins: List<LiveLocationPin> = remember(messages, contactMap, currentUser, liveMapsMeStr, now / 60_000) {
        val latestPerSender = mutableMapOf<String, LiveLocationPin>()
        messages
            .sortedByDescending { it.timestamp }
            .forEach { msg ->
                val content = msg.content ?: return@forEach
                val match = liveLocRegex.find(content) ?: return@forEach
                val durationKey = match.groupValues[1]
                val lat = match.groupValues[2].toDoubleOrNull() ?: return@forEach
                val lng = match.groupValues[3].toDoubleOrNull() ?: return@forEach
                // Check if this live location has expired
                val durationMs = durationKeyToMs(durationKey)
                if (durationMs > 0 && System.currentTimeMillis() - msg.timestamp > durationMs) return@forEach
                if (!latestPerSender.containsKey(msg.senderId)) {
                    val isMe = msg.senderId == currentUser?.userId
                    val name = if (isMe) {
                        currentUser?.name?.takeIf { it.isNotBlank() } ?: currentUser?.fakeNumber ?: liveMapsMeStr
                    } else {
                        contactMap[msg.senderId]?.username
                            ?: contactMap[msg.senderId]?.fakeNumber
                            ?: msg.senderId.take(8)
                    }
                    val avatarUrl = if (isMe) {
                        currentUser?.profileImageUrl
                    } else {
                        contactMap[msg.senderId]?.profileImageUrl
                    }
                    latestPerSender[msg.senderId] = LiveLocationPin(
                        senderId = msg.senderId,
                        senderName = name,
                        lat = lat,
                        lng = lng,
                        durationKey = durationKey,
                        profileImageUrl = avatarUrl
                    )
                }
            }
        latestPerSender.values.toList()
    }

    val realtimePins by viewModel.liveLocationPins.collectAsState()

    // Merge: Echtzeit-Updates überschreiben die Nachrichtenposition für aktive Sender
    val mergedPins = remember(livePins, realtimePins) {
        livePins.map { pin ->
            realtimePins[pin.senderId]?.let { rt ->
                if (System.currentTimeMillis() - rt.updatedAt < 60_000L) {
                    pin.copy(lat = rt.lat, lng = rt.lng)
                } else pin
            } ?: pin
        }
    }

    val selfInLivePins = remember(mergedPins, currentUser?.userId) {
        mergedPins.any { it.senderId == currentUser?.userId }
    }

    fun fetchMyLocation() {
        locationLoading = true
        getCurrentLocationOnce(context) { loc ->
            locationLoading = false
            if (loc != null) myLocation = GeoPoint(loc.latitude, loc.longitude)
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) fetchMyLocation() }

    // Initial location fetch
    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fetchMyLocation()
        } else {
            permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Auto-refresh every 20 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000L)
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                fetchMyLocation()
            }
        }
    }

    // Marker neu aufbauen, sobald sich Pins oder eigener Standort ändern.
    // Avatare werden asynchron geladen und gecacht; danach Auto-Zoom auf alle Punkte.
    LaunchedEffect(mergedPins, myLocation, selfInLivePins) {
        // Versatz bei identischen Positionen, damit mehrere Marker sichtbar bleiben
        val groups = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()
        mergedPins.forEachIndexed { i, pin ->
            val keyPos = Pair((pin.lat * 100000).roundToInt(), (pin.lng * 100000).roundToInt())
            groups.getOrPut(keyPos) { mutableListOf() }.add(i)
        }

        val newOverlays = mutableListOf<Marker>()
        val allPoints = mutableListOf<GeoPoint>()

        // Eigener aktueller Standort — nur wenn eigene userId NICHT in livePins
        if (!selfInLivePins) {
            myLocation?.let { pos ->
                val meName = currentUser?.name?.takeIf { it.isNotBlank() }
                    ?: currentUser?.fakeNumber ?: liveMapsMeStr
                val url = currentUser?.profileImageUrl
                val bmp = url?.let { avatarCache[it] ?: loadAvatarBitmap(context, imageLoader, it)?.also { b -> avatarCache[it] = b } }
                newOverlays.add(makeMarker(mapView, pos, meName, "Live · Mein Standort", bmp) {
                    openMapsNavigation(context, pos.latitude, pos.longitude)
                })
                allPoints.add(pos)
            }
        }

        // Live-Standort-Pins
        mergedPins.forEachIndexed { i, pin ->
            val keyPos = Pair((pin.lat * 100000).roundToInt(), (pin.lng * 100000).roundToInt())
            val group = groups[keyPos] ?: listOf(i)
            val idxInGroup = group.indexOf(i)
            val spread = if (group.size > 1) 0.00006 else 0.0
            val angle = (2.0 * Math.PI * idxInGroup) / group.size
            val pos = GeoPoint(pin.lat + spread * sin(angle), pin.lng + spread * cos(angle))
            val bmp = pin.profileImageUrl?.let {
                avatarCache[it] ?: loadAvatarBitmap(context, imageLoader, it)?.also { b -> avatarCache[it] = b }
            }
            newOverlays.add(makeMarker(mapView, pos, pin.senderName, "Live · ${durationLabel(pin.durationKey)}", bmp) {
                openMapsNavigation(context, pos.latitude, pos.longitude)
            })
            allPoints.add(pos)
        }

        mapView.overlays.clear()
        mapView.overlays.addAll(newOverlays)
        mapView.invalidate()

        // Auto-Zoom auf alle Punkte (nach Layout, damit Breite/Höhe bekannt sind)
        if (allPoints.isNotEmpty()) {
            mapView.post {
                if (allPoints.size >= 2) {
                    val bb = BoundingBox.fromGeoPoints(allPoints)
                    try {
                        mapView.zoomToBoundingBox(bb, true, 120)
                    } catch (_: Exception) {
                        mapView.controller.animateTo(allPoints[0])
                    }
                } else {
                    mapView.controller.setZoom(15.0)
                    mapView.controller.animateTo(allPoints[0])
                }
            }
        }
    }

    // Pulsing animation for live badge
    val infiniteTransition = rememberInfiniteTransition(label = "liveMapsScreenPulse")
    val liveScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "liveMapsScale"
    )
    val liveAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "liveMapsAlpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.live_maps_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
                    }
                },
                actions = {
                    if (mergedPins.isNotEmpty()) {
                        // Live indicator in toolbar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Box(modifier = Modifier.size(10.dp), contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .scale(liveScale)
                                        .alpha(liveAlpha)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE53935))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE53935))
                                )
                            }
                            Text("LIVE", color = Color(0xFFE53935), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Map
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize()
                )

                if (locationLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                // Re-center button
                FloatingActionButton(
                    onClick = {
                        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            fetchMyLocation()
                            myLocation?.let {
                                mapView.controller.setZoom(15.0)
                                mapView.controller.animateTo(it)
                            }
                        } else {
                            permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.live_maps_center_cd))
                }
            }

            // User list panel
            if (mergedPins.isNotEmpty()) {
                Surface(tonalElevation = 4.dp, shadowElevation = 8.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            stringResource(R.string.live_maps_sharing_now),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        mergedPins.forEach { pin ->
                            LiveUserRow(pin = pin, liveScale = liveScale, liveAlpha = liveAlpha)
                        }
                    }
                }
            } else {
                Surface(tonalElevation = 2.dp) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.live_maps_nobody_sharing),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Baut einen osmdroid-Marker mit rundem Avatar-Icon; Tap öffnet die Navigation. */
private fun makeMarker(
    mapView: MapView,
    pos: GeoPoint,
    name: String,
    snippet: String,
    avatar: android.graphics.Bitmap?,
    onTap: () -> Unit
): Marker {
    return Marker(mapView).apply {
        position = pos
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        title = name
        this.snippet = snippet
        icon = BitmapDrawable(mapView.context.resources, avatarMarkerBitmap(avatar, name))
        setOnMarkerClickListener { _, _ ->
            onTap()
            true
        }
    }
}

// Öffnet die Position in einer beliebigen installierten Karten-App via geo:-URI
// (FOSS-freundlich, funktioniert mit OsmAnd, Organic Maps, Google Maps …);
// fällt auf eine OpenStreetMap-Web-URL zurück, falls keine Karten-App vorhanden ist.
private fun openMapsNavigation(context: Context, lat: Double, lng: Double) {
    try {
        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng"))
        if (geoIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(geoIntent)
            return
        }
    } catch (_: Exception) {}
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=16/$lat/$lng"))
        )
    } catch (_: Exception) {}
}

private fun durationKeyToMs(key: String): Long = when (key) {
    "30m" -> 30L * 60 * 1000
    "1h"  -> 60L * 60 * 1000
    "2h"  -> 2L * 60 * 60 * 1000
    "4h"  -> 4L * 60 * 60 * 1000
    "8h"  -> 8L * 60 * 60 * 1000
    else  -> 0L
}

private fun durationLabel(key: String) = when (key) {
    "30m" -> "30 Min"
    "1h"  -> "1 Std"
    "2h"  -> "2 Std"
    "4h"  -> "4 Std"
    "8h"  -> "8 Std"
    else  -> key
}

@Composable
private fun LiveUserRow(pin: LiveLocationPin, liveScale: Float, liveAlpha: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (!pin.profileImageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = pin.profileImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(pin.senderName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(
                "Live · ${durationLabel(pin.durationKey)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Live dot
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(modifier = Modifier.size(10.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .scale(liveScale)
                        .alpha(liveAlpha)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                )
            }
            Text("LIVE", color = Color(0xFFE53935), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
