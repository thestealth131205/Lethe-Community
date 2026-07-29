package com.securechat.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.securechat.app.R
import com.securechat.app.ui.theme.topBarTitleColor

/** Generiert eine QR-Code-Bitmap aus einem String. */
private fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
    if (content.isBlank()) return null
    return try {
        val writer = QRCodeWriter()
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (_: Exception) { null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onNavigateBack: () -> Unit,
    userName: String = "",
    fakeNumber: String = "",
    profileImageUrl: String? = null,
    inviteLinkUrl: String? = null,
    inviteLinkError: String? = null,
    inviteLinkLoading: Boolean = false,
    userInfo: String? = null,
    userLinks: String? = null,
    onUpdateName: (String) -> Unit = {},
    onUpdateInfo: (String) -> Unit = {},
    onUpdateLinks: (String) -> Unit = {},
    userInstagram: String? = null,
    userTiktok: String? = null,
    userYoutube: String? = null,
    onUpdateInstagram: (String) -> Unit = {},
    onUpdateTiktok: (String) -> Unit = {},
    onUpdateYoutube: (String) -> Unit = {},
    onUploadProfileImage: (Uri) -> Unit = {},
    onShareInvite: (() -> Unit)? = null,
    onGenerateInvite: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    ageVerified: Boolean = false,
    nearbyUsername: String? = null,
    letheId: String? = null,
    onNavigateToNotifications: (() -> Unit)? = null,
    onNavigateToFriendeWerben: (() -> Unit)? = null,
    /** Tutorial neu starten */
    onResetOnboarding: (() -> Unit)? = null,
    isVerified: Boolean = false,
    currentStyx: Int = 0,
    onBuyVerification: ((onResult: (Boolean, String) -> Unit) -> Unit)? = null,
    isAdmin: Boolean = false,
    isModerator: Boolean = false,
    adminPanelPasswordSet: Boolean? = null,
    onLoadAdminPanelPasswordStatus: (() -> Unit)? = null,
    onSetAdminPanelPassword: ((String) -> Unit)? = null,
    adminPanelPasswordMessage: String? = null,
    onClearAdminPanelPasswordMessage: (() -> Unit)? = null
) {
    var showNameDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showLinksDialog by remember { mutableStateOf(false) }
    var showInstagramDialog by remember { mutableStateOf(false) }
    var showTiktokDialog by remember { mutableStateOf(false) }
    var showYoutubeDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAdminPanelPasswordDialog by remember { mutableStateOf(false) }
    var copiedNumber by remember { mutableStateOf(false) }
    var verificationMessage by remember { mutableStateOf<String?>(null) }
    var verificationLoading by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    // QR-Code-Bitmap aus Invite-Link generieren (nur wenn Link vorhanden)
    val qrBitmap by remember(inviteLinkUrl) {
        derivedStateOf { inviteLinkUrl?.let { generateQrBitmap(it) } }
    }

    // Invite-Link automatisch generieren wenn noch nicht vorhanden
    LaunchedEffect(Unit) {
        if (inviteLinkUrl == null) onGenerateInvite?.invoke()
        if (isAdmin || isModerator) onLoadAdminPanelPasswordStatus?.invoke()
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { onUploadProfileImage(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.account_back))
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
            SettingsSection(title = stringResource(R.string.account_section_profile)) {
                // Avatar + Name
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { imageLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (profileImageUrl != null) {
                            AsyncImage(
                                model = profileImageUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(22.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt, contentDescription = null,
                                modifier = Modifier.size(13.dp), tint = Color.White
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = userName.ifBlank { stringResource(R.string.account_no_name) },
                                fontSize = 18.sp, fontWeight = FontWeight.Bold
                            )
                            if (isVerified) {
                                Spacer(Modifier.width(4.dp))
                                VerifiedBadge()
                            }
                        }
                        Text(
                            text = stringResource(R.string.account_tap_image),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        if (ageVerified) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.account_age_verified),
                                    fontSize = 12.sp,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showNameDialog = true }) {
                        Icon(
                            Icons.Default.Edit, contentDescription = stringResource(R.string.account_info_edit),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider()

                // Verifizierungs-Haken kaufen / anzeigen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color(0xFF1DA1F2), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.account_verification_badge), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                        if (isVerified) {
                            Text(stringResource(R.string.account_verification_active), fontSize = 15.sp, color = Color(0xFF1DA1F2), fontWeight = FontWeight.Medium)
                        } else {
                            Text(stringResource(R.string.account_verification_inactive), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (!isVerified && onBuyVerification != null) {
                        Button(
                            onClick = {
                                if (currentStyx < 5000) {
                                    verificationMessage = "Du benötigst 5.000 Styx für den Verifizierungshaken."
                                } else {
                                    verificationLoading = true
                                    onBuyVerification { success, msg ->
                                        verificationLoading = false
                                        verificationMessage = msg
                                    }
                                }
                            },
                            enabled = !verificationLoading,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (verificationLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text(stringResource(R.string.account_verification_buy), fontSize = 13.sp)
                            }
                        }
                    }
                }

                verificationMessage?.let { msg ->
                    Text(
                        text = msg,
                        fontSize = 12.sp,
                        color = if (msg.startsWith("✓") || msg.contains("erfolgreich")) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                }

                HorizontalDivider()

                // Info-Feld
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.account_info_label), fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                        Text(
                            text = userInfo?.ifBlank { null } ?: stringResource(R.string.account_info_placeholder),
                            fontSize = 15.sp,
                            color = if (userInfo.isNullOrBlank())
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.account_info_edit),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }

                HorizontalDivider()

                // Link-Feld
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Link, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.account_link_label), fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                        Text(
                            text = userLinks?.ifBlank { null } ?: stringResource(R.string.account_link_placeholder),
                            fontSize = 15.sp,
                            color = if (userLinks.isNullOrBlank())
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showLinksDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.account_link_edit),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }

                HorizontalDivider()

                // Instagram
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CameraAlt, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Instagram", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                        Text(
                            text = userInstagram?.ifBlank { null } ?: "Benutzername hinzufügen",
                            fontSize = 15.sp,
                            color = if (userInstagram.isNullOrBlank())
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showInstagramDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Instagram bearbeiten",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }

                HorizontalDivider()

                // TikTok
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.MusicNote, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "TikTok", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                        Text(
                            text = userTiktok?.ifBlank { null } ?: "Benutzername hinzufügen",
                            fontSize = 15.sp,
                            color = if (userTiktok.isNullOrBlank())
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showTiktokDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "TikTok bearbeiten",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }

                HorizontalDivider()

                // YouTube
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "YouTube", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                        Text(
                            text = userYoutube?.ifBlank { null } ?: "Kanal-Name hinzufügen",
                            fontSize = 15.sp,
                            color = if (userYoutube.isNullOrBlank())
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showYoutubeDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "YouTube bearbeiten",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }

                HorizontalDivider()

                // Lethe-Nummer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Tag, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.account_lethe_number), fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                        Text(
                            text = if (fakeNumber.isNotBlank()) fakeNumber else "–",
                            fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = {
                        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Lethe-Nummer", fakeNumber))
                        copiedNumber = true
                    }) {
                        Icon(
                            if (copiedNumber) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.account_lethe_number_copy),
                            tint = if (copiedNumber) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                if (letheId != null) {
                    HorizontalDivider()

                    var copiedLethe by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Key, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "LetheID", fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                            Text(
                                text = letheId,
                                fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(onClick = {
                            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("LetheID", letheId))
                            copiedLethe = true
                        }) {
                            Icon(
                                if (copiedLethe) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "LetheID kopieren",
                                tint = if (copiedLethe) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                if (nearbyUsername != null) {
                    HorizontalDivider()

                    var copiedNearby by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Favorite, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "NearbyID (Nearby Benutzername)", fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                            Text(
                                text = nearbyUsername,
                                fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(onClick = {
                            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("NearbyID", nearbyUsername))
                            copiedNearby = true
                        }) {
                            Icon(
                                if (copiedNearby) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "NearbyID kopieren",
                                tint = if (copiedNearby) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            SettingsSection(title = stringResource(R.string.account_section_invitation)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.account_qr_title), fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // QR-Code oder Ladeindikator
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap!!.asImageBitmap(),
                                contentDescription = stringResource(R.string.account_qr_title),
                                modifier = Modifier
                                    .size(164.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else if (inviteLinkError != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.QrCode2, contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                                Spacer(Modifier.height(6.dp))
                                Text(inviteLinkError, fontSize = 10.sp, textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(6.dp))
                                TextButton(
                                    onClick = { onGenerateInvite?.invoke() },
                                    enabled = !inviteLinkLoading
                                ) {
                                    Text(stringResource(R.string.account_qr_retry), fontSize = 12.sp)
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (inviteLinkUrl == null) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(stringResource(R.string.account_qr_loading), fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                } else {
                                    Icon(Icons.Default.QrCode2, contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Invite-Link anzeigen + kopieren
                    if (inviteLinkUrl != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = inviteLinkUrl,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.weight(1f),
                                maxLines = 2
                            )
                            Spacer(Modifier.width(8.dp))
                            var copiedLink by remember { mutableStateOf(false) }
                            IconButton(onClick = {
                                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Einladungslink", inviteLinkUrl))
                                copiedLink = true
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    if (copiedLink) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.account_invite_link_copy),
                                    modifier = Modifier.size(16.dp),
                                    tint = if (copiedLink) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    if (onShareInvite != null) {
                        OutlinedButton(
                            onClick = onShareInvite,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.account_invite_share))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Benachrichtigungen, Freunde Werben
            if (onNavigateToNotifications != null || onNavigateToFriendeWerben != null) {
                SettingsSection(title = stringResource(R.string.account_section_settings)) {
                    if (onNavigateToNotifications != null) {
                        SettingsItem(
                            icon = Icons.Default.Notifications,
                            title = stringResource(R.string.account_notifications),
                            subtitle = stringResource(R.string.account_notifications_subtitle),
                            onClick = onNavigateToNotifications
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                        if (onNavigateToFriendeWerben != null) HorizontalDivider()
                    }
                    if (onNavigateToFriendeWerben != null) {
                        SettingsItem(
                            icon = Icons.Default.CardGiftcard,
                            title = stringResource(R.string.contacts_menu_invite),
                            subtitle = "Freunde einladen und gemeinsam chatten",
                            onClick = onNavigateToFriendeWerben
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Tutorial neu starten
            if (onResetOnboarding != null) {
                SettingsSection(title = stringResource(R.string.account_tutorial_section)) {
                    SettingsItem(
                        icon = Icons.Default.Lightbulb,
                        title = stringResource(R.string.account_tutorial_reset_title),
                        subtitle = stringResource(R.string.account_tutorial_reset_subtitle),
                        onClick = onResetOnboarding
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Backend-Passwort (2. Faktor fürs Admin-/Mod-Panel, nur für Admins/Moderatoren)
            if (isAdmin || isModerator) {
                SettingsSection(title = "Backend-Sicherheit") {
                    SettingsItem(
                        icon = Icons.Default.AdminPanelSettings,
                        title = "Backend-Passwort",
                        subtitle = when (adminPanelPasswordSet) {
                            true -> "Gesetzt – schützt den Zugriff aufs Backend-Panel"
                            false -> "Noch nicht gesetzt – Backend-Panel ist gesperrt"
                            null -> "Wird geladen…"
                        },
                        onClick = { showAdminPanelPasswordDialog = true }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Abmelden-Sektion
            if (onLogout != null) {
                SettingsSection(title = stringResource(R.string.account_section_session)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLogoutDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.account_logout),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Name-Dialog
    if (showNameDialog) {
        var newName by remember { mutableStateOf(userName) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            icon = { Icon(Icons.Default.Edit, contentDescription = null) },
            title = { Text(stringResource(R.string.account_name_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.account_name_dialog_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onUpdateName(newName.trim())
                            showNameDialog = false
                        }
                    },
                    enabled = newName.isNotBlank()
                ) { Text(stringResource(R.string.account_name_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text(stringResource(R.string.account_name_cancel))
                }
            }
        )
    }

    // Backend-Passwort-Dialog (2. Faktor fürs Admin-/Mod-Panel)
    if (showAdminPanelPasswordDialog) {
        var newPw by remember { mutableStateOf("") }
        var confirmPw by remember { mutableStateOf("") }
        val pwValid = newPw.length >= 12 && newPw.any { !it.isLetterOrDigit() }
        AlertDialog(
            onDismissRequest = {
                showAdminPanelPasswordDialog = false
                onClearAdminPanelPasswordMessage?.invoke()
            },
            icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
            title = { Text("Backend-Passwort") },
            text = {
                Column {
                    Text(
                        "Dieses Passwort wird zusätzlich zu deiner Admin-/Moderator-Rolle abgefragt, bevor das Backend-Panel angezeigt wird.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPw,
                        onValueChange = { newPw = it },
                        label = { Text("Neues Backend-Passwort") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPw,
                        onValueChange = { confirmPw = it },
                        label = { Text("Wiederholen") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (newPw.isNotEmpty() && !pwValid) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Mind. 12 Zeichen + mind. ein Sonderzeichen.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (adminPanelPasswordMessage != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            adminPanelPasswordMessage,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onSetAdminPanelPassword?.invoke(newPw) },
                    enabled = pwValid && newPw == confirmPw
                ) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAdminPanelPasswordDialog = false
                    onClearAdminPanelPasswordMessage?.invoke()
                }) { Text(stringResource(R.string.account_name_cancel)) }
            }
        )
    }

    // Info-Dialog
    if (showInfoDialog) {
        var newInfo by remember { mutableStateOf(userInfo ?: "") }
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text(stringResource(R.string.account_info_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = newInfo,
                    onValueChange = { newInfo = it },
                    label = { Text(stringResource(R.string.account_info_dialog_label)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateInfo(newInfo.trim())
                    showInfoDialog = false
                }) { Text(stringResource(R.string.account_name_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.account_name_cancel))
                }
            }
        )
    }

    // Logout-Bestätigungs-Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.account_logout_dialog_title)) },
            text = { Text(stringResource(R.string.account_logout_dialog_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.account_logout_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.account_name_cancel))
                }
            }
        )
    }

    // Link-Dialog
    if (showLinksDialog) {
        var newLink by remember { mutableStateOf(userLinks ?: "") }
        AlertDialog(
            onDismissRequest = { showLinksDialog = false },
            icon = { Icon(Icons.Default.Link, contentDescription = null) },
            title = { Text(stringResource(R.string.account_link_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = newLink,
                    onValueChange = { newLink = it },
                    label = { Text(stringResource(R.string.account_link_url_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateLinks(newLink.trim())
                    showLinksDialog = false
                }) { Text(stringResource(R.string.account_name_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showLinksDialog = false }) {
                    Text(stringResource(R.string.account_name_cancel))
                }
            }
        )
    }

    // Instagram-Dialog
    if (showInstagramDialog) {
        var newVal by remember { mutableStateOf(userInstagram ?: "") }
        AlertDialog(
            onDismissRequest = { showInstagramDialog = false },
            icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
            title = { Text(stringResource(R.string.account_instagram_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = newVal,
                    onValueChange = { newVal = it },
                    label = { Text(stringResource(R.string.account_social_username_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateInstagram(newVal.trim().trimStart('@'))
                    showInstagramDialog = false
                }) { Text(stringResource(R.string.account_name_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showInstagramDialog = false }) {
                    Text(stringResource(R.string.account_name_cancel))
                }
            }
        )
    }

    // TikTok-Dialog
    if (showTiktokDialog) {
        var newVal by remember { mutableStateOf(userTiktok ?: "") }
        AlertDialog(
            onDismissRequest = { showTiktokDialog = false },
            icon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
            title = { Text(stringResource(R.string.account_tiktok_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = newVal,
                    onValueChange = { newVal = it },
                    label = { Text(stringResource(R.string.account_social_username_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateTiktok(newVal.trim().trimStart('@'))
                    showTiktokDialog = false
                }) { Text(stringResource(R.string.account_name_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showTiktokDialog = false }) {
                    Text(stringResource(R.string.account_name_cancel))
                }
            }
        )
    }

    // YouTube-Dialog
    if (showYoutubeDialog) {
        var newVal by remember { mutableStateOf(userYoutube ?: "") }
        AlertDialog(
            onDismissRequest = { showYoutubeDialog = false },
            icon = { Icon(Icons.Default.PlayCircle, contentDescription = null) },
            title = { Text(stringResource(R.string.account_youtube_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = newVal,
                    onValueChange = { newVal = it },
                    label = { Text(stringResource(R.string.account_youtube_channel_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateYoutube(newVal.trim())
                    showYoutubeDialog = false
                }) { Text(stringResource(R.string.account_name_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showYoutubeDialog = false }) {
                    Text(stringResource(R.string.account_name_cancel))
                }
            }
        )
    }
}

/** Hellblaue Blase mit weißem Haken – erscheint hinter dem Username wenn is_verified=true. */
@Composable
fun VerifiedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(16.dp)
            .background(Color(0xFF1DA1F2), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Verifiziert",
            tint = Color.White,
            modifier = Modifier.size(10.dp)
        )
    }
}
