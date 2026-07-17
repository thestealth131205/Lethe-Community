package com.securechat.app.ui.screens

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.securechat.app.R
import com.securechat.app.ui.MainViewModel

private val ARTICLE_CATEGORIES = listOf(
    "Handwerklich", "Hobby", "Fotografie", "Beauty", "Musik",
    "Gaming", "Lifestyle", "Fitness", "Kochen", "3D-Modelle", "Sonstiges"
)

private val ARTICLE_LICENSES = listOf(
    "Persönliche Nutzung",
    "Kommerzielle Nutzung erlaubt",
    "Kommerzielle Nutzung verboten",
    "Creative Commons CC BY",
    "Creative Commons CC BY-NC",
    "Alle Rechte vorbehalten"
)

/**
 * CreatorAddArticleScreen: Erstellt einen neuen digitalen Artikel (Bilder, Musik, 3D-Modelle).
 * Route: "creator_add_article"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorAddArticleScreen(
    viewModel: MainViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // --- State ---
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("0") }
    var category by remember { mutableStateOf(ARTICLE_CATEGORIES[0]) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var license by remember { mutableStateOf("") }
    var licenseExpanded by remember { mutableStateOf(false) }

    var isPhotosVideos by remember { mutableStateOf(false) }
    var isMusic by remember { mutableStateOf(false) }
    var is3dModel by remember { mutableStateOf(false) }
    var is18plus by remember { mutableStateOf(false) }
    var show18plusDialog by remember { mutableStateOf(false) }

    val imageUris = remember { mutableStateListOf<Uri>() }
    val paidPhotoUris = remember { mutableStateListOf<Uri>() }
    val videoFiles = remember { mutableStateListOf<Pair<Uri, String>>() } // uri + filename
    val musicFiles = remember { mutableStateListOf<Pair<Uri, String>>() } // uri + filename
    val modelFiles = remember { mutableStateListOf<Pair<Uri, String>>() } // uri + filename

    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var isUploadingFile by remember { mutableStateOf(false) }

    // Created article ID (after initial save)
    var createdArticleId by remember { mutableStateOf<Int?>(null) }

    // Image picker (max 8)
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (imageUris.size < 8) imageUris.add(it)
        }
    }

    // Paid photo picker
    val paidPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { paidPhotoUris.add(it) }
    }

    // Video file picker
    val videoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = it.lastPathSegment ?: "video"
            videoFiles.add(Pair(it, name))
        }
    }

    // Music file picker
    val musicLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = it.lastPathSegment ?: "audio"
            musicFiles.add(Pair(it, name))
        }
    }

    // 3D model file picker
    val modelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = it.lastPathSegment ?: "model"
            modelFiles.add(Pair(it, name))
        }
    }

    // 18+ FLAG_SECURE: Screenshot/Aufnahme sperren sobald 18+ aktiviert
    DisposableEffect(is18plus) {
        if (is18plus) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (is18plus) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    // 18+ Bestätigungs-Dialog
    if (show18plusDialog) {
        AlertDialog(
            onDismissRequest = {
                show18plusDialog = false
                is18plus = false
            },
            title = { Text(stringResource(R.string.creator_article_18plus_dialog_title)) },
            text = {
                Text(
                    "Dieser Inhalt wird als 18+ markiert. Screenshot und Aufnahme werden für " +
                    "Nutzer ohne Altersverifikation gesperrt. Käufer müssen altersverifiziert sein."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    show18plusDialog = false
                    // is18plus bleibt true
                }) { Text(stringResource(R.string.creator_article_18plus_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    show18plusDialog = false
                    is18plus = false
                }) { Text(stringResource(R.string.creator_article_18plus_dialog_cancel)) }
            }
        )
    }

    fun doSave() {
        if (title.isBlank()) {
            saveError = "Bitte einen Titel eingeben."
            return
        }
        isSaving = true
        saveError = null
        val price = priceInput.toDoubleOrNull() ?: 0.0

        viewModel.createArticle(
            title = title,
            description = description,
            priceStyx = price,
            category = category,
            license = license.ifBlank { null },
            isMusic = isMusic,
            is3dModel = is3dModel,
            is18plus = is18plus
        ) { article, error ->
            if (article != null) {
                createdArticleId = article.id
                // Upload all image files
                if (imageUris.isEmpty() && paidPhotoUris.isEmpty() && videoFiles.isEmpty() && musicFiles.isEmpty() && modelFiles.isEmpty()) {
                    isSaving = false
                    navController.popBackStack()
                    return@createArticle
                }
                // Upload queued files
                val allUploads = mutableListOf<Pair<Uri, String>>()
                imageUris.forEach { allUploads.add(Pair(it, "image")) }
                paidPhotoUris.forEach { allUploads.add(Pair(it, "image")) }
                videoFiles.forEach { allUploads.add(Pair(it.first, "video")) }
                musicFiles.forEach { allUploads.add(Pair(it.first, "music")) }
                modelFiles.forEach { allUploads.add(Pair(it.first, "3d_model")) }

                var remaining = allUploads.size
                isUploadingFile = true
                allUploads.forEach { (uri, ftype) ->
                    viewModel.uploadArticleFile(article.id, ftype, uri) { _, _ ->
                        remaining--
                        if (remaining == 0) {
                            isSaving = false
                            isUploadingFile = false
                            navController.popBackStack()
                        }
                    }
                }
            } else {
                isSaving = false
                saveError = error ?: "Unbekannter Fehler"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.creator_article_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ─── Bilder-Upload ──────────────────────────────────────────────
            Text("Bilder (max. 8)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(imageUris) { uri ->
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = { imageUris.remove(uri) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Entfernen",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                if (imageUris.size < 8) {
                    item {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .clickable { imageLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Bild hinzufügen")
                        }
                    }
                }
            }

            HorizontalDivider()

            // ─── Titel ──────────────────────────────────────────────────────
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.creator_article_title_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // ─── Beschreibung ────────────────────────────────────────────────
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.creator_article_description_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 10
            )

            // ─── Preis in Styx ───────────────────────────────────────────────
            OutlinedTextField(
                value = priceInput,
                onValueChange = { priceInput = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text(stringResource(R.string.creator_article_price_label)) },
                suffix = { Text(stringResource(R.string.creator_article_price_suffix)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // ─── Kategorie ───────────────────────────────────────────────────
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.creator_article_category_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    ARTICLE_CATEGORIES.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = { category = cat; categoryExpanded = false }
                        )
                    }
                }
            }

            // ─── Lizenz ──────────────────────────────────────────────────────
            ExposedDropdownMenuBox(
                expanded = licenseExpanded,
                onExpandedChange = { licenseExpanded = it }
            ) {
                OutlinedTextField(
                    value = license,
                    onValueChange = { license = it },
                    label = { Text(stringResource(R.string.creator_article_license_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(licenseExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = licenseExpanded,
                    onDismissRequest = { licenseExpanded = false }
                ) {
                    ARTICLE_LICENSES.forEach { lic ->
                        DropdownMenuItem(
                            text = { Text(lic) },
                            onClick = { license = lic; licenseExpanded = false }
                        )
                    }
                }
            }

            HorizontalDivider()

            // ─── Typ-Auswahl: Fotos & Videos / Musik / 3D-Modell ────────────
            Text(stringResource(R.string.creator_article_content_type_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        isPhotosVideos = !isPhotosVideos
                        if (isPhotosVideos) { isMusic = false; is3dModel = false }
                    }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = isPhotosVideos,
                    onCheckedChange = { checked ->
                        isPhotosVideos = checked
                        if (checked) { isMusic = false; is3dModel = false }
                    }
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.PermMedia, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.creator_article_type_photos_videos))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        isMusic = !isMusic
                        if (isMusic) { is3dModel = false; isPhotosVideos = false }
                    }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = isMusic,
                    onCheckedChange = { checked ->
                        isMusic = checked
                        if (checked) { is3dModel = false; isPhotosVideos = false }
                    }
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.creator_article_type_music))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        is3dModel = !is3dModel
                        if (is3dModel) { isMusic = false; isPhotosVideos = false }
                    }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = is3dModel,
                    onCheckedChange = { checked ->
                        is3dModel = checked
                        if (checked) { isMusic = false; isPhotosVideos = false }
                    }
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ViewInAr, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.creator_article_type_3d_model))
            }

            // ─── Fotos & Videos-Sektion ──────────────────────────────────────
            if (isPhotosVideos) {
                HorizontalDivider()
                Text(stringResource(R.string.creator_article_photos_paid_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.creator_article_photos_paid_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(paidPhotoUris) { uri ->
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { paidPhotoUris.remove(uri) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Entfernen",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .clickable { paidPhotoLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Foto hinzufügen")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.creator_article_videos_paid_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Akzeptierte Formate: .mp4, .mov, .avi, .mkv, .webm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                videoFiles.toList().forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(entry.second, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        IconButton(
                            onClick = { videoFiles.remove(entry) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Entfernen", modifier = Modifier.size(16.dp))
                        }
                    }
                }
                OutlinedButton(
                    onClick = { videoLauncher.launch("video/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.creator_article_add_video))
                }
            }

            // ─── Musik-Sektion ───────────────────────────────────────────────
            if (isMusic) {
                HorizontalDivider()
                Text(stringResource(R.string.creator_article_music_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Akzeptierte Formate: .mp3, .flac, .wav, .ogg, .m4a",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                musicFiles.forEach { (_, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(name, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    }
                }
                OutlinedButton(
                    onClick = { musicLauncher.launch("audio/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.creator_article_add_audio))
                }
            }

            // ─── 3D-Modell-Sektion ───────────────────────────────────────────
            if (is3dModel) {
                HorizontalDivider()
                Text(stringResource(R.string.creator_article_3d_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Akzeptierte Formate: .stl, .obj, .3mf, .fbx, .ply, .glb, .gltf",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                modelFiles.forEach { (_, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ViewInAr, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(name, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    }
                }
                OutlinedButton(
                    onClick = { modelLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.creator_article_add_3d))
                }
            }

            HorizontalDivider()

            // ─── 18+ Checkbox ────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!is18plus) {
                            is18plus = true
                            show18plusDialog = true
                        } else {
                            is18plus = false
                        }
                    }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = is18plus,
                    onCheckedChange = { checked ->
                        if (checked) {
                            is18plus = true
                            show18plusDialog = true
                        } else {
                            is18plus = false
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(stringResource(R.string.creator_article_18plus_label), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.creator_article_18plus_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ─── Fehler ──────────────────────────────────────────────────────
            saveError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // ─── Upload-Fortschritt ──────────────────────────────────────────
            if (isUploadingFile) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.creator_article_uploading), style = MaterialTheme.typography.bodySmall)
                }
            }

            // ─── Speichern ───────────────────────────────────────────────────
            Button(
                onClick = { doSave() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving && !isUploadingFile && title.isNotBlank()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text(stringResource(R.string.creator_article_save))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
