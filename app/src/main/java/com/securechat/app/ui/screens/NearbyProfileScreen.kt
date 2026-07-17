package com.securechat.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.compose.ui.res.stringResource
import com.securechat.app.R
import com.securechat.app.ui.MainViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyProfileScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    var isVisible by remember { mutableStateOf(true) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var username by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Männlich") }
    var height by remember { mutableStateOf("") }
    var lookingFor by remember { mutableStateOf("Freundschaft") }
    var bio by remember { mutableStateOf("") }
    var showGenderMenu by remember { mutableStateOf(false) }
    var showLookingForMenu by remember { mutableStateOf(false) }
    var showHasChildrenMenu by remember { mutableStateOf(false) }
    var showWantsChildrenMenu by remember { mutableStateOf(false) }
    var hasChildren by remember { mutableStateOf("") }
    var wantsChildren by remember { mutableStateOf("") }
    var galleryUri1 by remember { mutableStateOf<Uri?>(null) }
    var galleryUri2 by remember { mutableStateOf<Uri?>(null) }
    var galleryUri3 by remember { mutableStateOf<Uri?>(null) }
    var deleteGallery1 by remember { mutableStateOf(false) }
    var deleteGallery2 by remember { mutableStateOf(false) }
    var deleteGallery3 by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var age by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf(0.0) }
    var lng by remember { mutableStateOf(0.0) }
    val noLocationStr = stringResource(R.string.nearby_profile_location_none)
    var locationLabel by remember { mutableStateOf(noLocationStr) }

    val statusMessage by viewModel.statusMessage.collectAsState()
    val myNearbyProfile by viewModel.myDatingProfile.collectAsState()

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { loc ->
                    loc?.let {
                        lat = it.latitude
                        lng = it.longitude
                        locationLabel = "📍 Standort gesetzt"
                    }
                }
        }
    }

    fun fetchLocation() {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { loc ->
                    loc?.let {
                        lat = it.latitude
                        lng = it.longitude
                        locationLabel = "📍 Standort gesetzt"
                    }
                }
        } else {
            locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(Unit) {
        // Meldungen von anderen Screens nicht auf diesem Screen anzeigen
        viewModel.clearStatus()
        viewModel.loadMyDatingProfile()
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { loc ->
                    if (loc != null && lat == 0.0) {
                        lat = loc.latitude
                        lng = loc.longitude
                        locationLabel = "📍 Standort gesetzt"
                    }
                }
        }
    }

    LaunchedEffect(myNearbyProfile) {
        myNearbyProfile?.let { profile ->
            if (username.isEmpty()) username = profile.username
            if (bio.isEmpty()) bio = profile.description ?: ""
            if (height.isEmpty()) height = profile.height?.toString() ?: ""
            if (age.isEmpty()) age = profile.age.toString()
            gender = profile.gender
            isVisible = profile.isActive
            if (hasChildren.isEmpty()) hasChildren = profile.hasChildren ?: ""
            if (wantsChildren.isEmpty()) wantsChildren = profile.wantsChildren ?: ""
            if (lat == 0.0 && profile.latitude != 0.0) {
                lat = profile.latitude ?: 0.0
                lng = profile.longitude ?: 0.0
                locationLabel = "📍 %.5f, %.5f".format(lat, lng)
            }
        }
    }

    LaunchedEffect(statusMessage) {
        if (isSaving && statusMessage != null) isSaving = false
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedImageUri = uri }

    val galleryPicker1 = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { galleryUri1 = uri; deleteGallery1 = false }
    }
    val galleryPicker2 = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { galleryUri2 = uri; deleteGallery2 = false }
    }
    val galleryPicker3 = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { galleryUri3 = uri; deleteGallery3 = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nearby_profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
        ) {
            // === SICHTBARKEIT ===
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp,
                color = if (isVisible)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = if (isVisible)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.nearby_profile_visibility_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isVisible) stringResource(R.string.nearby_profile_visibility_visible) else stringResource(R.string.nearby_profile_visibility_hidden),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Switch(checked = isVisible, onCheckedChange = { isVisible = it })
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // === PROFILFOTO ===
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.nearby_profile_photo_label),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(bottom = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    val existingImageUrl = myNearbyProfile?.nearbyImageUrl?.let {
                        if (it.startsWith("http")) it else "https://letheapp.de$it"
                    }
                    when {
                        selectedImageUri != null -> {
                            Image(
                                painter = rememberAsyncImagePainter(selectedImageUri),
                                contentDescription = stringResource(R.string.nearby_profile_photo_label),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        existingImageUrl != null -> {
                            Image(
                                painter = rememberAsyncImagePainter(existingImageUrl),
                                contentDescription = stringResource(R.string.nearby_profile_photo_label),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        else -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.AddAPhoto,
                                    contentDescription = stringResource(R.string.nearby_profile_photo_add_cd),
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.nearby_profile_photo_choose),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // === PROFIL DATEN ===
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.nearby_profile_info_label),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.nearby_profile_username_label)) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = showGenderMenu,
                    onExpandedChange = { showGenderMenu = it }
                ) {
                    OutlinedTextField(
                        value = gender,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.nearby_profile_gender_label)) },
                        leadingIcon = { Icon(Icons.Default.Wc, contentDescription = null) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showGenderMenu)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = showGenderMenu,
                        onDismissRequest = { showGenderMenu = false }
                    ) {
                        listOf("Männlich", "Weiblich", "Divers").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = { gender = option; showGenderMenu = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = height,
                    onValueChange = { if (it.length <= 3) height = it },
                    label = { Text(stringResource(R.string.nearby_profile_height_label)) },
                    leadingIcon = { Icon(Icons.Default.Height, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text(stringResource(R.string.nearby_profile_height_placeholder)) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = age,
                    onValueChange = { if (it.length <= 2 && (it.isEmpty() || it.all(Char::isDigit))) age = it },
                    label = { Text(stringResource(R.string.nearby_profile_age_label)) },
                    leadingIcon = { Icon(Icons.Default.Cake, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text(stringResource(R.string.nearby_profile_age_placeholder)) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = showLookingForMenu,
                    onExpandedChange = { showLookingForMenu = it }
                ) {
                    OutlinedTextField(
                        value = lookingFor,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.nearby_profile_looking_for_label)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showLookingForMenu)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = showLookingForMenu,
                        onDismissRequest = { showLookingForMenu = false }
                    ) {
                        listOf(
                            "Freundschaft",
                            "Beziehung",
                            "Dates",
                            "Freizeitpartner",
                            "Etwas Lockeres",
                            "Noch offen"
                        ).forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = { lookingFor = option; showLookingForMenu = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 250) bio = it },
                    label = { Text(stringResource(R.string.nearby_profile_bio_label)) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text(stringResource(R.string.nearby_profile_bio_placeholder)) },
                    supportingText = { Text(stringResource(R.string.nearby_profile_bio_count, bio.length)) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // === KINDER ===
                ExposedDropdownMenuBox(
                    expanded = showHasChildrenMenu,
                    onExpandedChange = { showHasChildrenMenu = it }
                ) {
                    OutlinedTextField(
                        value = hasChildren,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kinder") },
                        leadingIcon = { Icon(Icons.Default.ChildCare, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showHasChildrenMenu) },
                        placeholder = { Text("Keine Angabe") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = showHasChildrenMenu,
                        onDismissRequest = { showHasChildrenMenu = false }
                    ) {
                        listOf("", "Keine Kinder", "Habe Kinder").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(if (option.isEmpty()) "Keine Angabe" else option) },
                                onClick = { hasChildren = option; showHasChildrenMenu = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = showWantsChildrenMenu,
                    onExpandedChange = { showWantsChildrenMenu = it }
                ) {
                    OutlinedTextField(
                        value = wantsChildren,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kinderwunsch") },
                        leadingIcon = { Icon(Icons.Default.FamilyRestroom, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showWantsChildrenMenu) },
                        placeholder = { Text("Keine Angabe") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = showWantsChildrenMenu,
                        onDismissRequest = { showWantsChildrenMenu = false }
                    ) {
                        listOf("", "Ja", "Nein", "Vielleicht").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(if (option.isEmpty()) "Keine Angabe" else option) },
                                onClick = { wantsChildren = option; showWantsChildrenMenu = false }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // === GALERIE-FOTOS ===
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Galerie (bis zu 3 weitere Fotos)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Besucher können durch deine Fotos wischen.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val gallerySlots = listOf(
                        Triple(1, galleryUri1, myNearbyProfile?.galleryPhoto1?.let { if (it.startsWith("http")) it else "https://letheapp.de$it" }),
                        Triple(2, galleryUri2, myNearbyProfile?.galleryPhoto2?.let { if (it.startsWith("http")) it else "https://letheapp.de$it" }),
                        Triple(3, galleryUri3, myNearbyProfile?.galleryPhoto3?.let { if (it.startsWith("http")) it else "https://letheapp.de$it" })
                    )
                    val deletedSlots = listOf(deleteGallery1, deleteGallery2, deleteGallery3)
                    val pickers = listOf(galleryPicker1, galleryPicker2, galleryPicker3)

                    gallerySlots.forEachIndexed { idx, (slot, localUri, serverUrl) ->
                        val isDeleted = deletedSlots[idx]
                        val displayUri = if (isDeleted) null else localUri
                        val displayUrl = if (isDeleted || localUri != null) null else serverUrl

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .clickable { pickers[idx].launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                displayUri != null -> AsyncImage(
                                    model = displayUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                displayUrl != null -> AsyncImage(
                                    model = displayUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text("Foto $slot", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            // Löschen-Button wenn Foto vorhanden
                            if (displayUri != null || displayUrl != null) {
                                IconButton(
                                    onClick = {
                                        when (slot) {
                                            1 -> { galleryUri1 = null; if (serverUrl != null) deleteGallery1 = true }
                                            2 -> { galleryUri2 = null; if (serverUrl != null) deleteGallery2 = true }
                                            3 -> { galleryUri3 = null; if (serverUrl != null) deleteGallery3 = true }
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(28.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Löschen",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // === STANDORT ===
            Text(
                text = "ℹ️ Dein Standort wird absichtlich leicht verfälscht weitergegeben, damit andere Nutzer deinen genauen Aufenthaltsort nicht ermitteln können.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = if (lat != 0.0) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.nearby_profile_location_label), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = locationLabel,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    TextButton(onClick = { fetchLocation() }) {
                        Text(if (lat != 0.0) stringResource(R.string.nearby_profile_location_update_btn) else stringResource(R.string.nearby_profile_location_set_btn))
                    }
                }
            }

            // === KARTEN-VORSCHAU ===
            if (lat != 0.0) {
                Spacer(modifier = Modifier.height(12.dp))
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(LatLng(lat, lng), 13f)
                }
                LaunchedEffect(lat, lng) {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(lat, lng), 13f)
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(180.dp),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 2.dp
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(
                            scrollGesturesEnabled = false,
                            zoomGesturesEnabled = false,
                            tiltGesturesEnabled = false,
                            rotationGesturesEnabled = false,
                            zoomControlsEnabled = false,
                            mapToolbarEnabled = false,
                            compassEnabled = false,
                            myLocationButtonEnabled = false
                        ),
                        properties = MapProperties(isMyLocationEnabled = false)
                    ) {
                        Marker(
                            state = MarkerState(position = LatLng(lat, lng)),
                            title = "Mein Standort"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // === SPEICHERN ===
            Button(
                onClick = {
                    isSaving = true
                    val roundedLat = (lat * 1000).roundToInt() / 1000.0
                    val roundedLng = (lng * 1000).roundToInt() / 1000.0
                    viewModel.updateDatingProfile(
                        username = username,
                        description = bio,
                        age = age.toIntOrNull() ?: 18,
                        gender = gender,
                        lookingFor = lookingFor,
                        lat = roundedLat,
                        lng = roundedLng,
                        height = height.toIntOrNull() ?: 0,
                        isVisible = isVisible,
                        imageUri = selectedImageUri,
                        hasChildren = hasChildren.ifEmpty { null },
                        wantsChildren = wantsChildren.ifEmpty { null },
                        galleryUri1 = galleryUri1,
                        galleryUri2 = galleryUri2,
                        galleryUri3 = galleryUri3,
                        deleteGallery1 = deleteGallery1,
                        deleteGallery2 = deleteGallery2,
                        deleteGallery3 = deleteGallery3
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                enabled = !isSaving && username.isNotBlank() && age.isNotBlank(),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.nearby_profile_save_button), fontSize = 16.sp)
                }
            }

            statusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
