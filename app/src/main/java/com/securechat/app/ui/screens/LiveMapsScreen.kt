package com.securechat.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.google.maps.android.compose.rememberMarkerState
import com.securechat.app.R
import com.securechat.app.data.local.ContactEntity
import com.securechat.app.ui.MainViewModel
import kotlinx.coroutines.delay

private val liveLocRegex = Regex("""[\uD83D\uDCCD📍]live:(\S+) https://maps\.google\.com/\?q=([-\d.]+),([-\d.]+)""")

private data class LiveLocationPin(
    val senderId: String,
    val senderName: String,
    val lat: Double,
    val lng: Double,
    val durationKey: String,
    val profileImageUrl: String? = null
)

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

    var myLocation by remember { mutableStateOf<LatLng?>(null) }
    var locationLoading by remember { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState()
    var mapLoaded by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf(MapType.SATELLITE) }

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
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { loc ->
                locationLoading = false
                loc?.let {
                    myLocation = LatLng(it.latitude, it.longitude)
                }
            }
            .addOnFailureListener { locationLoading = false }
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

    // Auto-zoom to fit all pins — live: bei jeder Standort-Aktualisierung animiert neu ausrichten
    LaunchedEffect(myLocation, mergedPins, mapLoaded) {
        if (!mapLoaded) return@LaunchedEffect
        val allPoints = buildList {
            myLocation?.let { add(it) }
            mergedPins.forEach { add(LatLng(it.lat, it.lng)) }
        }
        if (allPoints.isEmpty()) return@LaunchedEffect
        if (allPoints.size >= 2) {
            val boundsBuilder = LatLngBounds.builder()
            allPoints.forEach { boundsBuilder.include(it) }
            val bounds = boundsBuilder.build()
            // Padding 120 dp damit Marker nicht am Rand abgeschnitten werden
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngBounds(bounds, 120),
                durationMs = 800
            )
        } else {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(allPoints[0], 13f),
                durationMs = 800
            )
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
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(mapType = mapType),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = true,
                        scrollGesturesEnabled = true,
                        zoomGesturesEnabled = true,
                        tiltGesturesEnabled = false,
                        rotationGesturesEnabled = false,
                        mapToolbarEnabled = false,
                        compassEnabled = true,
                        myLocationButtonEnabled = false
                    ),
                    onMapLoaded = { mapLoaded = true }
                ) {
                    if (mapLoaded) {
                        // Own current location — nur anzeigen wenn eigene userId NICHT in livePins
                        if (!selfInLivePins) {
                            myLocation?.let { pos ->
                                val myMarkerState = rememberMarkerState(position = pos)
                                LaunchedEffect(pos) {
                                    myMarkerState.position = pos
                                }
                                ProfileMapMarker(
                                    state = myMarkerState,
                                    profileImageUrl = currentUser?.profileImageUrl,
                                    name = currentUser?.name?.takeIf { it.isNotBlank() }
                                        ?: currentUser?.fakeNumber ?: stringResource(R.string.live_maps_me),
                                    title = stringResource(R.string.live_maps_my_location_title),
                                    snippet = stringResource(R.string.live_maps_current_snippet)
                                )
                            }
                        }
                        // Live location pins — Versatz bei identischen Positionen damit beide sichtbar sind
                        val pinPositions: List<Pair<LiveLocationPin, LatLng>> = remember(mergedPins) {
                            // Gruppiere nach gerundeter Position (5 Dezimalstellen ≈ 1 m Genauigkeit)
                            val groups = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()
                            mergedPins.forEachIndexed { i, pin ->
                                val key = Pair(
                                    (pin.lat * 100000).roundToInt(),
                                    (pin.lng * 100000).roundToInt()
                                )
                                groups.getOrPut(key) { mutableListOf() }.add(i)
                            }
                            mergedPins.mapIndexed { i, pin ->
                                val key = Pair(
                                    (pin.lat * 100000).roundToInt(),
                                    (pin.lng * 100000).roundToInt()
                                )
                                val group = groups[key] ?: listOf(i)
                                val idxInGroup = group.indexOf(i)
                                val spread = if (group.size > 1) 0.00006 else 0.0
                                val angle = (2.0 * Math.PI * idxInGroup) / group.size
                                val offsetLat = pin.lat + spread * sin(angle)
                                val offsetLng = pin.lng + spread * cos(angle)
                                Pair(pin, LatLng(offsetLat, offsetLng))
                            }
                        }
                        pinPositions.forEach { (pin, pos) ->
                            key(pin.senderId) {
                                val markerState = rememberMarkerState(position = pos)
                                LaunchedEffect(pos) {
                                    markerState.position = pos
                                }
                                ProfileMapMarker(
                                    state = markerState,
                                    profileImageUrl = pin.profileImageUrl,
                                    name = pin.senderName,
                                    snippet = "Live · ${durationLabel(pin.durationKey)} · In Maps öffnen",
                                    onInfoWindowClick = {
                                        openMapsNavigation(context, pos.latitude, pos.longitude)
                                    }
                                )
                            }
                        }
                    }
                }

                if (locationLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                // Kartentyp-Toggle oben rechts
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 4.dp,
                    onClick = {
                        mapType = if (mapType == MapType.SATELLITE) MapType.NORMAL else MapType.SATELLITE
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (mapType == MapType.SATELLITE) Icons.Default.LocationOn else Icons.Filled.MyLocation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (mapType == MapType.SATELLITE) "Normal" else "Satellit",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Re-center button
                FloatingActionButton(
                    onClick = {
                        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            fetchMyLocation()
                            myLocation?.let {
                                cameraPositionState.position = CameraPosition.fromLatLngZoom(it, 14f)
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

@Composable
private fun ProfileMapMarker(
    state: com.google.maps.android.compose.MarkerState,
    profileImageUrl: String?,
    name: String,
    title: String? = name,
    snippet: String? = null,
    onInfoWindowClick: (() -> Unit)? = null
) {
    // Bild außerhalb von MarkerComposable laden, damit es beim Render bereits im Cache ist
    val painter = if (!profileImageUrl.isNullOrBlank()) {
        rememberAsyncImagePainter(model = profileImageUrl)
    } else null
    val imageReady = painter != null && painter.state is AsyncImagePainter.State.Success

    MarkerComposable(
        keys = arrayOf(profileImageUrl ?: "", name, imageReady),
        state = state,
        title = title,
        snippet = snippet,
        anchor = androidx.compose.ui.geometry.Offset(0.5f, 1f),
        onInfoWindowClick = { onInfoWindowClick?.invoke() }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xFF1565C0))
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (painter != null && imageReady) {
                    Image(
                        painter = painter,
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            // Kleines Dreieck als Zeiger nach unten
            val trianglePath = remember { androidx.compose.ui.graphics.Path() }
            Box(
                modifier = Modifier
                    .size(width = 12.dp, height = 7.dp)
                    .drawBehind {
                        trianglePath.apply {
                            reset()
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2f, size.height)
                            close()
                        }
                        drawPath(trianglePath, color = Color.White)
                    }
            )
            // Username-Label
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = name,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
    }
}

// Öffnet die Position in Google Maps mit Turn-by-Turn-Navigation; fällt
// auf eine generische Maps-/Browser-URL zurück, falls die App fehlt.
private fun openMapsNavigation(context: Context, lat: Double, lng: Double) {
    try {
        val navIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng")).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (navIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(navIntent)
            return
        }
    } catch (_: Exception) {}
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$lat,$lng"))
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
