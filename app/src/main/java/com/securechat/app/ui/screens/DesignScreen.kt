package com.securechat.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import coil.compose.AsyncImage
import com.securechat.app.R
import com.securechat.app.data.local.AppTheme
import com.securechat.app.data.local.ThemeMode
import com.securechat.app.ui.theme.topBarTitleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignScreen(
    onNavigateBack: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    primaryColor: Color,
    onPrimaryColorChange: (Color) -> Unit,
    accentColor: Color,
    onAccentColorChange: (Color) -> Unit,
    bubbleColor: Color,
    onBubbleColorChange: (Color) -> Unit,
    partnerBubbleColor: Color = Color.White,
    onPartnerBubbleColorChange: (Color) -> Unit = {},
    onResetToWhatsAppColors: () -> Unit = {},
    onApplyPresetDark: () -> Unit = {},
    onApplyPresetBlue: () -> Unit = {},
    barColor: Color = Color(0xFF0F1E35),
    onBarColorChange: (Color) -> Unit = {},
    onBarColorReset: () -> Unit = {},
    backgroundColor: Color = Color(0xFF07131F),
    onBackgroundColorChange: (Color) -> Unit = {},
    onBackgroundColorReset: () -> Unit = {},
    isDarkTheme: Boolean = true,
    fontSizeMultiplier: Float = 1.0f,
    onFontSizeChange: (Float) -> Unit = {},
    appTheme: AppTheme = AppTheme.MATERIAL,
    onAppThemeChange: (AppTheme) -> Unit = {},
    bubbleColor2: Color = Color(0xFFC0DCF0),
    onBubbleColor2Change: (Color) -> Unit = {},
    partnerBubbleColor2: Color = Color.White,
    onPartnerBubbleColor2Change: (Color) -> Unit = {},
    focusBorderColor: Color = Color(0xFFC0DCF0),
    onFocusBorderColorChange: (Color) -> Unit = {},
    focusBorderColor2: Color = Color(0xFFC0DCF0),
    onFocusBorderColor2Change: (Color) -> Unit = {},
    avatarSizeMultiplier: Float = 1.0f,
    onAvatarSizeChange: (Float) -> Unit = {},
    chatBackgroundUri: String = "",
    onChatBackgroundUriChange: (String) -> Unit = {},
    onPickCustomChatBackground: () -> Unit = {}
) {
    var showPrimaryColorPicker by remember { mutableStateOf(false) }
    var showAccentColorPicker by remember { mutableStateOf(false) }
    var showBubbleColorPicker by remember { mutableStateOf(false) }
    var showPartnerBubbleColorPicker by remember { mutableStateOf(false) }
    var showBarColorPicker by remember { mutableStateOf(false) }
    var showBackgroundColorPicker by remember { mutableStateOf(false) }
    var showThemeModeDialog by remember { mutableStateOf(false) }
    var showBubbleColor2Picker by remember { mutableStateOf(false) }
    var showPartnerBubbleColor2Picker by remember { mutableStateOf(false) }
    var showFocusBorderColorPicker by remember { mutableStateOf(false) }
    var showFocusBorderColor2Picker by remember { mutableStateOf(false) }
    val isGlossy = appTheme == AppTheme.GLOSSY_MORPH

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.design_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.design_back))
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
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ── Themen ────────────────────────────────────────────────────────────
            SettingsSection(title = "Themen") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Material-Karte
                        ThemeCard(
                            label = "Material",
                            description = "Flach & klar",
                            isSelected = appTheme == AppTheme.MATERIAL,
                            modifier = Modifier.weight(1f),
                            preview = {
                                // Vorschau: zwei flache Blasen
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 48.dp, height = 22.dp)
                                            .background(bubbleColor, RoundedCornerShape(8.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(width = 48.dp, height = 22.dp)
                                            .background(partnerBubbleColor, RoundedCornerShape(8.dp))
                                    )
                                }
                            },
                            onClick = { onAppThemeChange(AppTheme.MATERIAL) }
                        )
                        // Glossy-Morph-Karte
                        ThemeCard(
                            label = "Glossy Morph",
                            description = "Transparent & glänzend",
                            isSelected = appTheme == AppTheme.GLOSSY_MORPH,
                            modifier = Modifier.weight(1f),
                            preview = {
                                // Vorschau: zwei Verlaufsblasen
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 48.dp, height = 22.dp)
                                            .background(
                                                Brush.linearGradient(listOf(bubbleColor, bubbleColor2)),
                                                RoundedCornerShape(8.dp)
                                            )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(width = 48.dp, height = 22.dp)
                                            .background(
                                                Brush.linearGradient(listOf(partnerBubbleColor, partnerBubbleColor2)),
                                                RoundedCornerShape(8.dp)
                                            )
                                    )
                                }
                            },
                            onClick = { onAppThemeChange(AppTheme.GLOSSY_MORPH) }
                        )
                    }
                }
            }

            // ── Chat-Hintergrundbild ──────────────────────────────────────────────
            SettingsSection(title = "Hintergrundbild") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Wähle ein Hintergrundbild für alle Chats",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    // Vorschau-Grid: "Kein Hintergrund" + 8 Presets + "Eigenes hochladen"
                    val presets = listOf(
                        "preset:1" to R.drawable.chat_bg_preset_1,
                        "preset:2" to R.drawable.chat_bg_preset_2,
                        "preset:3" to R.drawable.chat_bg_preset_3,
                        "preset:4" to R.drawable.chat_bg_preset_4,
                        "preset:5" to R.drawable.chat_bg_preset_5,
                        "preset:6" to R.drawable.chat_bg_preset_6,
                        "preset:7" to R.drawable.chat_bg_preset_7,
                        "preset:default_dark" to R.drawable.chat_bg_preset_default_dark
                    )
                    val itemsPerRow = 3
                    val allItems: List<Any> = listOf("none") + presets.map { it.first } + listOf("custom")
                    val rows = allItems.chunked(itemsPerRow)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { item ->
                                    val isSelected = when (item) {
                                        "none" -> chatBackgroundUri.isEmpty()
                                        "custom" -> chatBackgroundUri.startsWith("content://")
                                        else -> chatBackgroundUri == item
                                    }
                                    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    val borderWidth = if (isSelected) 2.dp else 1.dp
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(0.56f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
                                            .clickable {
                                                when (item) {
                                                    "none" -> onChatBackgroundUriChange("")
                                                    "custom" -> onPickCustomChatBackground()
                                                    else -> onChatBackgroundUriChange(item as String)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when (item) {
                                            "none" -> {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.HideImage,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(
                                                            "Keiner",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                        )
                                                    }
                                                }
                                            }
                                            "custom" -> {
                                                if (chatBackgroundUri.startsWith("content://")) {
                                                    AsyncImage(
                                                        model = Uri.parse(chatBackgroundUri),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.Center
                                                        ) {
                                                            Icon(
                                                                Icons.Default.AddPhotoAlternate,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                            Spacer(Modifier.height(4.dp))
                                                            Text(
                                                                "Eigenes",
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            else -> {
                                                val drawableRes = presets.find { it.first == item }?.second
                                                if (drawableRes != null) {
                                                    Image(
                                                        painter = painterResource(drawableRes),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                        }
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .size(18.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                // Leere Platzhalter für unvollständige letzte Zeile
                                repeat(itemsPerRow - row.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // ── Design ────────────────────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.design_section)) {
                SettingsItem(
                    icon = Icons.Default.DarkMode,
                    title = stringResource(R.string.design_mode_title),
                    subtitle = when (themeMode) {
                        ThemeMode.LIGHT -> stringResource(R.string.design_mode_light)
                        ThemeMode.DARK -> stringResource(R.string.design_mode_dark)
                        ThemeMode.SYSTEM -> stringResource(R.string.design_mode_system)
                    }
                ) {
                    IconButton(onClick = { showThemeModeDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }

                HorizontalDivider()

                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.design_primary_color),
                    subtitle = stringResource(R.string.design_primary_subtitle)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp).clickable { showPrimaryColorPicker = true },
                        shape = RoundedCornerShape(8.dp),
                        color = primaryColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {}
                }

                HorizontalDivider()

                SettingsItem(
                    icon = Icons.Default.Brush,
                    title = stringResource(R.string.design_accent_color),
                    subtitle = stringResource(R.string.design_accent_subtitle)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp).clickable { showAccentColorPicker = true },
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {}
                }

                HorizontalDivider()

                SettingsItem(
                    icon = Icons.Default.ChatBubble,
                    title = stringResource(R.string.design_bubble_color_own),
                    subtitle = stringResource(R.string.design_bubble_subtitle_own)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(40.dp).clickable { showBubbleColorPicker = true },
                            shape = RoundedCornerShape(8.dp),
                            color = bubbleColor,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {}
                        if (isGlossy) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            Surface(
                                modifier = Modifier.size(40.dp).clickable { showBubbleColor2Picker = true },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Box(modifier = Modifier.fillMaxSize().background(
                                    Brush.linearGradient(listOf(bubbleColor, bubbleColor2))
                                ))
                            }
                        }
                    }
                }

                HorizontalDivider()

                SettingsItem(
                    icon = Icons.Default.ChatBubbleOutline,
                    title = stringResource(R.string.design_bubble_color_partner),
                    subtitle = stringResource(R.string.design_bubble_subtitle_partner)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(40.dp).clickable { showPartnerBubbleColorPicker = true },
                            shape = RoundedCornerShape(8.dp),
                            color = partnerBubbleColor,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {}
                        if (isGlossy) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            Surface(
                                modifier = Modifier.size(40.dp).clickable { showPartnerBubbleColor2Picker = true },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Box(modifier = Modifier.fillMaxSize().background(
                                    Brush.linearGradient(listOf(partnerBubbleColor, partnerBubbleColor2))
                                ))
                            }
                        }
                    }
                }

                HorizontalDivider()

                SettingsItem(
                    icon = Icons.Default.FormatPaint,
                    title = stringResource(R.string.design_bar_color),
                    subtitle = stringResource(R.string.design_bar_subtitle)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp).clickable { showBarColorPicker = true },
                        shape = RoundedCornerShape(8.dp),
                        color = barColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {}
                }

                // Zurücksetzen-Zeile für Bar-Farbe
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(36.dp))
                    Text(
                        stringResource(R.string.design_reset_default),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onBarColorReset() }
                    )
                    Spacer(Modifier.width(8.dp))
                    // Preview-Dots für Dark (#0F1E35) und Light (#EFEFEF) Standard
                    listOf(Color(0xFF0F1E35), Color(0xFFEFEFEF)).forEach { c ->
                        Surface(
                            modifier = Modifier.size(14.dp),
                            shape = CircleShape,
                            color = c,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                        ) {}
                        Spacer(Modifier.width(4.dp))
                    }
                }

                HorizontalDivider()

                SettingsItem(
                    icon = Icons.Default.Wallpaper,
                    title = stringResource(R.string.design_background_color),
                    subtitle = stringResource(R.string.design_background_subtitle)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp).clickable { showBackgroundColorPicker = true },
                        shape = RoundedCornerShape(8.dp),
                        color = backgroundColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {}
                }

                // Zurücksetzen-Zeile für Hintergrundfarbe
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(36.dp))
                    Text(
                        stringResource(R.string.design_reset_default),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onBackgroundColorReset() }
                    )
                    Spacer(Modifier.width(8.dp))
                    listOf(Color(0xFF07131F), Color(0xFFEFEFEF)).forEach { c ->
                        Surface(
                            modifier = Modifier.size(14.dp),
                            shape = CircleShape,
                            color = c,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                        ) {}
                        Spacer(Modifier.width(4.dp))
                    }
                }

                HorizontalDivider()

                SettingsItem(
                    icon = Icons.Default.BorderColor,
                    title = "Textfeld-Fokusrahmen",
                    subtitle = "Rahmenfarbe beim Fokus des Eingabefelds"
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(40.dp).clickable { showFocusBorderColorPicker = true },
                            shape = RoundedCornerShape(8.dp),
                            color = focusBorderColor,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {}
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        Surface(
                            modifier = Modifier.size(40.dp).clickable { showFocusBorderColor2Picker = true },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Box(modifier = Modifier.fillMaxSize().background(
                                Brush.linearGradient(listOf(focusBorderColor, focusBorderColor2))
                            ))
                        }
                    }
                }

                HorizontalDivider()

                // Schriftgröße
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Icon(
                            Icons.Default.TextFields, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Schriftgröße", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "Chat, Creator-Beiträge & Sparks",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val options = listOf("Kleiner" to 0.85f, "Standard" to 1.0f, "Größer" to 1.10f, "Groß" to 1.22f)
                        options.forEachIndexed { idx, (label, value) ->
                            SegmentedButton(
                                selected = kotlin.math.abs(fontSizeMultiplier - value) < 0.01f,
                                onClick = { onFontSizeChange(value) },
                                shape = SegmentedButtonDefaults.itemShape(index = idx, count = options.size),
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Profilbild-Größe
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Icon(
                            Icons.Default.AccountCircle, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Profilbild-Größe", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "Kontaktliste, Gruppen-Chat & Sprachnachrichten",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val options = listOf("Standard" to 1.0f, "Größer" to 1.03f, "Groß" to 1.05f)
                        options.forEachIndexed { idx, (label, value) ->
                            SegmentedButton(
                                selected = kotlin.math.abs(avatarSizeMultiplier - value) < 0.01f,
                                onClick = { onAvatarSizeChange(value) },
                                shape = SegmentedButtonDefaults.itemShape(index = idx, count = options.size),
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                HorizontalDivider()

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Icon(
                            Icons.Default.RestartAlt, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.design_reset_section), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.design_reset_subtitle),
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(Color(0xFF0F1E35), Color(0xFF4DA3FF), Color(0xFFC0DCF0)).forEach { c ->
                                Surface(
                                    modifier = Modifier.size(18.dp), shape = CircleShape, color = c,
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                                ) {}
                            }
                        }
                    }
                    // ── Farbthemen-Presets ──────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Preset: Standard (dunkel)
                        OutlinedCard(
                            modifier = Modifier.weight(1f).clickable(onClick = onApplyPresetDark),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    listOf(Color(0xFF0F1E35), Color(0xFF4DA3FF), Color(0xFF1B3F8B), Color(0xFFC0DCF0)).forEach { c ->
                                        Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(c))
                                    }
                                }
                                Spacer(Modifier.height(5.dp))
                                Text("Standard", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("Dunkles Lethe-Design", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                        // Preset: Lethe Blau (hell)
                        OutlinedCard(
                            modifier = Modifier.weight(1f).clickable(onClick = onApplyPresetBlue),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    listOf(Color(0xFF0D1F3C), Color(0xFF1565C0), Color(0xFFFFFDE7), Color(0xFFBBDEFB)).forEach { c ->
                                        Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(c))
                                    }
                                }
                                Spacer(Modifier.height(5.dp))
                                Text("Lethe Blau", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("Helles Blau-Design", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }
                    OutlinedButton(onClick = onResetToWhatsAppColors, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.design_reset_button))
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showPrimaryColorPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.design_primary_color_dialog),
            currentColor = primaryColor,
            onDismiss = { showPrimaryColorPicker = false },
            onColorSelected = { onPrimaryColorChange(it); showPrimaryColorPicker = false }
        )
    }
    if (showAccentColorPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.design_accent_color_dialog),
            currentColor = accentColor,
            onDismiss = { showAccentColorPicker = false },
            onColorSelected = { onAccentColorChange(it); showAccentColorPicker = false }
        )
    }
    if (showBubbleColorPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.design_bubble_color_dialog_own),
            currentColor = bubbleColor,
            showBubblePalette = true,
            onDismiss = { showBubbleColorPicker = false },
            onColorSelected = { onBubbleColorChange(it); showBubbleColorPicker = false }
        )
    }
    if (showPartnerBubbleColorPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.design_bubble_color_dialog_partner),
            currentColor = partnerBubbleColor,
            showBubblePalette = true,
            onDismiss = { showPartnerBubbleColorPicker = false },
            onColorSelected = { onPartnerBubbleColorChange(it); showPartnerBubbleColorPicker = false }
        )
    }
    if (showThemeModeDialog) {
        ThemeModeDialog(
            currentMode = themeMode,
            onDismiss = { showThemeModeDialog = false },
            onModeSelected = { onThemeModeChange(it); showThemeModeDialog = false }
        )
    }
    if (showBarColorPicker) {
        BarColorPickerDialog(
            currentColor = barColor,
            onDismiss = { showBarColorPicker = false },
            onColorSelected = { onBarColorChange(it); showBarColorPicker = false }
        )
    }
    if (showBackgroundColorPicker) {
        BackgroundColorPickerDialog(
            currentColor = backgroundColor,
            onDismiss = { showBackgroundColorPicker = false },
            onColorSelected = { onBackgroundColorChange(it); showBackgroundColorPicker = false }
        )
    }
    if (showBubbleColor2Picker) {
        ColorPickerDialog(
            title = "Verlaufsfarbe Du wählen",
            currentColor = bubbleColor2,
            showBubblePalette = true,
            onDismiss = { showBubbleColor2Picker = false },
            onColorSelected = { onBubbleColor2Change(it); showBubbleColor2Picker = false }
        )
    }
    if (showPartnerBubbleColor2Picker) {
        ColorPickerDialog(
            title = "Verlaufsfarbe Chatpartner wählen",
            currentColor = partnerBubbleColor2,
            showBubblePalette = true,
            onDismiss = { showPartnerBubbleColor2Picker = false },
            onColorSelected = { onPartnerBubbleColor2Change(it); showPartnerBubbleColor2Picker = false }
        )
    }
    if (showFocusBorderColorPicker) {
        ColorPickerDialog(
            title = "Fokusrahmen Farbe 1",
            currentColor = focusBorderColor,
            showBubblePalette = true,
            onDismiss = { showFocusBorderColorPicker = false },
            onColorSelected = { onFocusBorderColorChange(it); showFocusBorderColorPicker = false }
        )
    }
    if (showFocusBorderColor2Picker) {
        ColorPickerDialog(
            title = "Fokusrahmen Farbe 2 (Verlauf)",
            currentColor = focusBorderColor2,
            showBubblePalette = true,
            onDismiss = { showFocusBorderColor2Picker = false },
            onColorSelected = { onFocusBorderColor2Change(it); showFocusBorderColor2Picker = false }
        )
    }
}

@Composable
private fun ThemeCard(
    label: String,
    description: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val borderWidth = if (isSelected) 2.dp else 1.dp
    Surface(
        modifier = modifier
            .clickable(onClick = onClick)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            preview()
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(description, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            if (isSelected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.design_active), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun BarColorPickerDialog(
    currentColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    val barPalette = listOf(
        // Reihe 1 – Standard
        Color(0xFF0F1E35), Color(0xFFEFEFEF), Color(0xFF000000), Color(0xFFFFFFFF),
        // Reihe 2 – Dunkel
        Color(0xFF1C2B3A), Color(0xFF0D2137), Color(0xFF1A1A1A), Color(0xFF2D2D2D),
        // Reihe 3 – Dunkelblau
        Color(0xFF162032), Color(0xFF0A1628), Color(0xFF0D1B2A), Color(0xFF263238),
        // Reihe 4 – Dunkelgrün
        Color(0xFF1B5E20), Color(0xFF004D40), Color(0xFF003300), Color(0xFF0D3B1E),
        // Reihe 5 – Dunkelrot
        Color(0xFF8B0000), Color(0xFF4A0000), Color(0xFF6B1111), Color(0xFF550000),
        // Reihe 6 – Dunkelviolett
        Color(0xFF1A0D2E), Color(0xFF2D0A4E), Color(0xFF33005C), Color(0xFF4A1F7C),
        // Reihe 7 – Blaugrau
        Color(0xFF37474F), Color(0xFF455A64), Color(0xFF546E7A), Color(0xFF607D8B),
        // Reihe 8 – Hell
        Color(0xFFE0E0E0), Color(0xFFD0D0D0), Color(0xFFCFD8DC), Color(0xFFB0BEC5),
        // Reihe 9 – Grün/Teal
        Color(0xFF003737), Color(0xFF00515A), Color(0xFF004B4B), Color(0xFF006064),
        // Reihe 10 – Pastell
        Color(0xFFECEFF1), Color(0xFFFAFAFA), Color(0xFFF5F5F5), Color(0xFFE8EAF6),
        // Aus Chatblasen-Palette
        Color(0xFFFFCDD2), Color(0xFFFF8FA3), Color(0xFFE91E63), Color(0xFF880E4F),
        Color(0xFFFFDFBA), Color(0xFFFFAB40), Color(0xFFFF6D00), Color(0xFFBF360C),
        Color(0xFFFFF9C4), Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFF57F17),
        Color(0xFFCCFF90), Color(0xFF69F0AE), Color(0xFF4CAF50), Color(0xFFF9FBE7),
        Color(0xFFCDDC39), Color(0xFF8BC34A), Color(0xFF33691E), Color(0xFFBAE1FF),
        Color(0xFF40C4FF), Color(0xFF2196F3), Color(0xFF0D47A1), Color(0xFFE1F5FE),
        Color(0xFF87CEEB), Color(0xFF03A9F4), Color(0xFF01579B), Color(0xFFC5CAE9),
        Color(0xFF7986CB), Color(0xFF3949AB), Color(0xFF1A237E), Color(0xFFE0BBE4),
        Color(0xFFCE93D8), Color(0xFF9C27B0), Color(0xFF4A148C), Color(0xFFC7CEEA),
        Color(0xFF9FA8DA), Color(0xFF5C6BC0), Color(0xFF283593), Color(0xFFA8EFED),
        Color(0xFF80DEEA), Color(0xFF00BCD4), Color(0xFFB5EAD7), Color(0xFF80CBC4),
        Color(0xFF009688), Color(0xFFD7CCC8), Color(0xFFA1887F), Color(0xFF795548),
        Color(0xFF4E342E), Color(0xFFFFB3C6), Color(0xFFFFD6A5), Color(0xFFFFF1A8),
        Color(0xFFD4AAFF), Color(0xFFFFFF00), Color(0xFFFF1493), Color(0xFF00FF7F),
        Color(0xFF7FDBFF), Color(0xFFC0C0C0), Color(0xFF9E9E9E), Color(0xFF757575),
        Color(0xFF505050), Color(0xFF303030)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.design_bar_color_dialog_title)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.heightIn(max = 400.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(barPalette) { color ->
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { onColorSelected(color) },
                        shape = RoundedCornerShape(8.dp),
                        color = color,
                        border = androidx.compose.foundation.BorderStroke(
                            if (color == currentColor) 2.dp else 0.5.dp,
                            if (color == currentColor) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                    ) {}
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) }
        }
    )
}

@Composable
fun BackgroundColorPickerDialog(
    currentColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    val palette = listOf(
        // Dunkel
        Color(0xFF07131F), Color(0xFF000000), Color(0xFF0A0A0A), Color(0xFF111111),
        Color(0xFF1A1A1A), Color(0xFF222222), Color(0xFF2D2D2D), Color(0xFF333333),
        // Dunkelblau
        Color(0xFF0D1B2A), Color(0xFF0F1E35), Color(0xFF162032), Color(0xFF1C2B3A),
        Color(0xFF1A2F4A), Color(0xFF0A1628), Color(0xFF0D2137), Color(0xFF263238),
        // Dunkelgrün
        Color(0xFF003300), Color(0xFF0D3B1E), Color(0xFF1B5E20), Color(0xFF004D40),
        // Dunkelrot/Violett
        Color(0xFF1A0D2E), Color(0xFF2D0A4E), Color(0xFF4A0000), Color(0xFF8B0000),
        // Mittel
        Color(0xFF37474F), Color(0xFF455A64), Color(0xFF546E7A), Color(0xFF607D8B),
        // Hell
        Color(0xFFEFEFEF), Color(0xFFFFFFFF), Color(0xFFFAFAFA), Color(0xFFE8EAF6),
        Color(0xFFE0E0E0), Color(0xFFCFD8DC), Color(0xFFB0BEC5), Color(0xFFECEFF1),
        // Warm
        Color(0xFFFAF5E8), Color(0xFFFFF8E1), Color(0xFFFFF3E0), Color(0xFFFFECB3),
        // Aus Chatblasen-Palette
        Color(0xFFFFCDD2), Color(0xFFFF8FA3), Color(0xFFE91E63), Color(0xFF880E4F),
        Color(0xFFFFDFBA), Color(0xFFFFAB40), Color(0xFFFF6D00), Color(0xFFBF360C),
        Color(0xFFFFF9C4), Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFF57F17),
        Color(0xFFCCFF90), Color(0xFF69F0AE), Color(0xFF4CAF50), Color(0xFFF9FBE7),
        Color(0xFFCDDC39), Color(0xFF8BC34A), Color(0xFF33691E), Color(0xFFBAE1FF),
        Color(0xFF40C4FF), Color(0xFF2196F3), Color(0xFF0D47A1), Color(0xFFE1F5FE),
        Color(0xFF87CEEB), Color(0xFF03A9F4), Color(0xFF01579B), Color(0xFFC5CAE9),
        Color(0xFF7986CB), Color(0xFF3949AB), Color(0xFF1A237E), Color(0xFFE0BBE4),
        Color(0xFFCE93D8), Color(0xFF9C27B0), Color(0xFF4A148C), Color(0xFFC7CEEA),
        Color(0xFF9FA8DA), Color(0xFF5C6BC0), Color(0xFF283593), Color(0xFFA8EFED),
        Color(0xFF80DEEA), Color(0xFF00BCD4), Color(0xFF006064), Color(0xFFB5EAD7),
        Color(0xFF80CBC4), Color(0xFF009688), Color(0xFF90A4AE), Color(0xFFD7CCC8),
        Color(0xFFA1887F), Color(0xFF795548), Color(0xFF4E342E), Color(0xFFFFB3C6),
        Color(0xFFFFD6A5), Color(0xFFFFF1A8), Color(0xFFD4AAFF), Color(0xFFFFFF00),
        Color(0xFFFF1493), Color(0xFF00FF7F), Color(0xFF7FDBFF), Color(0xFFC0C0C0),
        Color(0xFF9E9E9E), Color(0xFF757575), Color(0xFF505050), Color(0xFF303030),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.design_background_color_dialog_title)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.heightIn(max = 400.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(palette) { color ->
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { onColorSelected(color) },
                        shape = RoundedCornerShape(8.dp),
                        color = color,
                        border = androidx.compose.foundation.BorderStroke(
                            if (color == currentColor) 2.dp else 0.5.dp,
                            if (color == currentColor) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                    ) {}
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) }
        }
    )
}
