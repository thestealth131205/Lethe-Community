package com.securechat.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.securechat.app.R
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.components.PhoneInputField
import com.securechat.app.ui.components.buildE164
import com.securechat.app.ui.utils.PhoneNumberExtractor
import com.securechat.app.data.network.NewDeviceState

/**
 * EPIC 2 – Login-/Registrierungsscreen mit:
 *  - Telefonnummer-Eingabe (Ländervorwahl + Nummer, PhoneInputField)
 *  - Automatisches Auslesen der SIM-Nummer bei Registrierung
 *  - Auto-Login-Schalter (speichert Zugangsdaten + automatischer Login)
 *  - "Passwort vergessen" → SMS-Reset-Flow
 *  - Erklärtext über Passwort-Anforderungen
 */
@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val prefs by viewModel.userPrefs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val biometricAvailable = remember {
        com.securechat.app.ui.utils.BiometricHelper(context).canAuthenticate()
    }

    // ── Eingabe-State ───────────────────────────────────────────────────────
    var countryCode by remember { mutableStateOf("+49") }
    var localNumber by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isRegisterMode by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }
    var autoLogin by remember { mutableStateOf(false) }

    // ── Passwort-vergessen-Dialog ───────────────────────────────────────────
    var showForgotDialog by remember { mutableStateOf(false) }
    var forgotPhone by remember { mutableStateOf("") }
    var forgotCountryCode by remember { mutableStateOf("+49") }
    var forgotLocalNumber by remember { mutableStateOf("") }
    // ── Passwort-Reset: Schritt 2 – Code eingeben ────────────────────────────
    var showForgotCodeDialog by remember { mutableStateOf(false) }
    var forgotEnteredCode by remember { mutableStateOf("") }
    var forgotResetPhoneE164 by remember { mutableStateOf("") }  // gespeichert aus Schritt 1

    // ── Passwort-Reset: Schritt 3 – Neues Passwort ───────────────────────────
    var showForgotNewPasswordDialog by remember { mutableStateOf(false) }
    var forgotResetToken by remember { mutableStateOf("") }
    var forgotNewPassword by remember { mutableStateOf("") }
    var forgotNewPasswordConfirm by remember { mutableStateOf("") }
    var forgotNewPasswordVisible by remember { mutableStateOf(false) }
    var forgotNewPasswordConfirmVisible by remember { mutableStateOf(false) }
    var forgotPasswordError by remember { mutableStateOf<String?>(null) }

    // ── SMS-OTP-Dialog für Registrierung ────────────────────────────────────
    var showOtpDialog by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }
    var isSendingOtp by remember { mutableStateOf(false) }
    var otpError by remember { mutableStateOf<String?>(null) }

    // ── Kind-Konto-Registrierung ─────────────────────────────────────────────
    var isChildAccount by remember { mutableStateOf(false) }
    var birthDate by remember { mutableStateOf("") }
    var childInviteToken by remember { mutableStateOf("") }

    // Passwort-Validierung (Min 12 Zeichen + Sonderzeichen)
    val isPasswordValid = password.length >= 12 && password.any { !it.isLetterOrDigit() }

    // Vollständige E.164-Nummer
    val fullPhoneNumber = buildE164(countryCode, localNumber)

    // Localized success strings for navigation trigger comparison
    val loginSuccessMsg = stringResource(R.string.vm_login_success)

    // Beim ersten Start: Zugangsdaten aus DataStore laden
    LaunchedEffect(prefs) {
        if (localNumber.isEmpty() && prefs.fakeNumber.isNotEmpty()) {
            val (cc, local) = PhoneNumberExtractor.parseForDisplay(prefs.fakeNumber)
            countryCode = cc
            localNumber = local
        }
        if (password.isEmpty()) password = prefs.password
        rememberMe = prefs.rememberMe
    }

    // Auf Login-Erfolg reagieren
    LaunchedEffect(statusMessage) {
        if (statusMessage == loginSuccessMsg || statusMessage == "Login erfolgreich" || statusMessage == "Registrierung erfolgreich") {
            onLoginSuccess()
            viewModel.clearStatus()
        }
    }

    // Bei Registrierung: SIM-Nummer versuchen auszulesen
    LaunchedEffect(isRegisterMode) {
        if (isRegisterMode && localNumber.isEmpty()) {
            val simNumber = PhoneNumberExtractor.getOwnPhoneNumber(context)
            if (simNumber != null) {
                val (cc, local) = PhoneNumberExtractor.parseForDisplay(simNumber)
                countryCode = cc
                localNumber = local
            }
        }
    }

    // ── Passwort-vergessen-Dialog ───────────────────────────────────────────
    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.login_password_reset_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    // Warnung: alte Nachrichten bleiben verschlüsselt
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.login_forgot_encrypted_warning),
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    // Telefonnummer
                    PhoneInputField(
                        countryCode = forgotCountryCode,
                        localNumber = forgotLocalNumber,
                        onCountryCodeChange = { forgotCountryCode = it },
                        onLocalNumberChange = { forgotLocalNumber = it },
                        label = stringResource(R.string.login_password_reset_label)
                    )
                    if (forgotPasswordError != null) {
                        Text(
                            forgotPasswordError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        forgotPasswordError = null
                        val phone = buildE164(forgotCountryCode, forgotLocalNumber)
                        viewModel.requestPasswordReset(phone) { success, msg ->
                            if (success) {
                                forgotResetPhoneE164 = phone
                                showForgotDialog = false
                                showForgotCodeDialog = true
                            } else {
                                forgotPasswordError = msg
                            }
                        }
                    },
                    enabled = forgotLocalNumber.isNotBlank()
                ) {
                    Text(stringResource(R.string.login_password_reset_send))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showForgotDialog = false
                    forgotPasswordError = null
                }) {
                    Text(stringResource(R.string.login_cancel))
                }
            }
        )
    }

    // ── Passwort-Reset: Schritt 2 – SMS-Code eingeben ──────────────────────
    if (showForgotCodeDialog) {
        AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.login_password_reset_code_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.login_password_reset_code_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = forgotEnteredCode,
                        onValueChange = { forgotEnteredCode = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text(stringResource(R.string.login_password_reset_code_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.verifyResetCode(forgotResetPhoneE164, forgotEnteredCode) { success, result ->
                            if (success) {
                                forgotResetToken = result
                                showForgotCodeDialog = false
                                showForgotNewPasswordDialog = true
                            } else {
                                forgotPasswordError = result
                            }
                        }
                    },
                    enabled = forgotEnteredCode.length == 6
                ) { Text(stringResource(R.string.login_password_reset_code_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showForgotCodeDialog = false }) {
                    Text(stringResource(R.string.login_cancel))
                }
            }
        )
    }

    // ── Passwort-Reset: Schritt 3 – Neues Passwort ─────────────────────────
    if (showForgotNewPasswordDialog) {
        val newPwValid = forgotNewPassword.length >= 12 && forgotNewPassword.any { !it.isLetterOrDigit() }
        val pwMatch = forgotNewPassword == forgotNewPasswordConfirm
        AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.login_new_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.login_new_password_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (forgotPasswordError != null) {
                        Text(
                            forgotPasswordError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    OutlinedTextField(
                        value = forgotNewPassword,
                        onValueChange = { forgotNewPassword = it },
                        label = { Text(stringResource(R.string.login_new_password_label)) },
                        visualTransformation = if (forgotNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { forgotNewPasswordVisible = !forgotNewPasswordVisible }) {
                                Icon(
                                    if (forgotNewPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = forgotNewPasswordConfirm,
                        onValueChange = { forgotNewPasswordConfirm = it },
                        label = { Text(stringResource(R.string.login_new_password_confirm_label)) },
                        visualTransformation = if (forgotNewPasswordConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { forgotNewPasswordConfirmVisible = !forgotNewPasswordConfirmVisible }) {
                                Icon(
                                    if (forgotNewPasswordConfirmVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        isError = forgotNewPasswordConfirm.isNotEmpty() && !pwMatch,
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        forgotPasswordError = null
                        viewModel.resetPasswordWithToken(forgotResetToken, forgotNewPassword) { success, msg ->
                            if (success) {
                                password = forgotNewPassword
                                showForgotNewPasswordDialog = false
                                forgotNewPassword = ""
                                forgotNewPasswordConfirm = ""
                                forgotResetToken = ""
                            } else {
                                forgotPasswordError = msg
                            }
                        }
                    },
                    enabled = newPwValid && pwMatch
                ) { Text(stringResource(R.string.login_new_password_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showForgotNewPasswordDialog = false }) {
                    Text(stringResource(R.string.login_cancel))
                }
            }
        )
    }

    // ── Hauptinhalt ─────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp)
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App-Icon
            Surface(
                modifier = Modifier.size(100.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isRegisterMode) Icons.Default.PersonAdd else Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(50.dp),
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (isRegisterMode) stringResource(R.string.login_create_account) else stringResource(R.string.login_welcome_back),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isRegisterMode)
                    stringResource(R.string.login_privacy_subtitle)
                else
                    stringResource(R.string.login_login_subtitle),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Telefonnummer ──────────────────────────────────────────────
            PhoneInputField(
                countryCode = countryCode,
                localNumber = localNumber,
                onCountryCodeChange = { countryCode = it },
                onLocalNumberChange = { localNumber = it },
                modifier = Modifier.fillMaxWidth(),
                label = if (isRegisterMode) stringResource(R.string.login_phone_label_register) else stringResource(R.string.login_phone_label_login)
            )

            if (isRegisterMode) {
                if (localNumber.isNotBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Deine Nummer wird intern gespeichert als:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = fullPhoneNumber,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.login_phone_info),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // ── Anzeigename (nur Registrierung) ───────────────────────────
            if (isRegisterMode) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.login_display_name_label)) },
                    placeholder = { Text(stringResource(R.string.login_display_name_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Passwort ───────────────────────────────────────────────────
            if (isRegisterMode) {
                Text(
                    text = stringResource(R.string.login_password_requirements),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.login_password_label)) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(R.string.login_password_show)
                        )
                    }
                },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = if (isRegisterMode && password.isNotEmpty()) {
                    {
                        Column {
                            Text(
                                text = if (password.length >= 12) "✓ ${stringResource(R.string.login_password_length)}" else "✗ ${stringResource(R.string.login_password_length_required)}",
                                color = if (password.length >= 12) Color(0xFF4CAF50) else Color(0xFFE91E63),
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (password.any { !it.isLetterOrDigit() }) "✓ ${stringResource(R.string.login_password_special_char)}" else "✗ ${stringResource(R.string.login_password_special_char_required)}",
                                color = if (password.any { !it.isLetterOrDigit() }) Color(0xFF4CAF50) else Color(0xFFE91E63),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else null,
                isError = isRegisterMode && password.isNotEmpty() && !isPasswordValid
            )

            // ── Kind-Konto-Option (nur Registrierung) ─────────────────────
            if (isRegisterMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isChildAccount)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChildCare,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Kind-Konto",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Für Minderjährige – Elternteil muss Einladungstoken bereitstellen",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isChildAccount,
                            onCheckedChange = { isChildAccount = it }
                        )
                    }
                }

                if (isChildAccount) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = birthDate,
                        onValueChange = { if (it.length <= 10) birthDate = it },
                        label = { Text("Geburtsdatum (JJJJ-MM-TT)") },
                        placeholder = { Text("z. B. 2010-05-14") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = childInviteToken,
                        onValueChange = { childInviteToken = it.trim() },
                        label = { Text("Einladungstoken des Elternteils") },
                        placeholder = { Text("Token aus der Eltern-App") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )
                }
            }

            // ── Optionen (Login) ───────────────────────────────────────────
            if (!isRegisterMode) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it }
                        )
                        Text(
                            text = stringResource(R.string.login_remember_credentials),
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { rememberMe = !rememberMe }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = autoLogin,
                            onCheckedChange = { autoLogin = it }
                        )
                        Column(modifier = Modifier.clickable { autoLogin = !autoLogin }) {
                            Text(stringResource(R.string.login_auto_login), fontSize = 14.sp)
                            Text(
                                stringResource(R.string.login_auto_login_subtitle),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Aktionen ───────────────────────────────────────────────────
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (isRegisterMode) {
                                val childReady = !isChildAccount ||
                                    (birthDate.matches(Regex("""\d{4}-\d{2}-\d{2}""")) && childInviteToken.length == 36)
                                if (isPasswordValid && name.isNotBlank() && localNumber.isNotBlank() && childReady) {
                                    isSendingOtp = true
                                    otpError = null
                                    otpCode = ""
                                    viewModel.sendRegistrationOtp(fullPhoneNumber) { success, msg ->
                                        isSendingOtp = false
                                        if (success) {
                                            showOtpDialog = true
                                        } else {
                                            otpError = msg
                                        }
                                    }
                                }
                            } else {
                                viewModel.login(fullPhoneNumber, password, rememberMe, autoLogin)
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(4.dp),
                        enabled = !isSendingOtp && localNumber.isNotBlank() && password.isNotBlank() &&
                                (!isRegisterMode || (isPasswordValid && name.isNotBlank() &&
                                        (!isChildAccount || (birthDate.matches(Regex("""\d{4}-\d{2}-\d{2}""")) && childInviteToken.length == 36))))
                    ) {
                        if (isSendingOtp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                text = if (isRegisterMode) stringResource(R.string.login_button_register) else stringResource(R.string.login_button_login),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Biometrie-Login (wenn verfügbar)
                    if (!isRegisterMode && biometricAvailable && prefs.fakeNumber.isNotEmpty()) {
                        FilledIconButton(
                            onClick = {
                                val activity = context as? FragmentActivity
                                activity?.let { viewModel.performBiometricLogin(it) }
                            },
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Fingerprint Login",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Modus wechseln
                TextButton(onClick = {
                    isRegisterMode = !isRegisterMode
                    viewModel.clearStatus()
                }) {
                    Text(
                        text = if (isRegisterMode)
                            stringResource(R.string.login_switch_mode_register)
                        else
                            stringResource(R.string.login_switch_mode_login),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Passwort vergessen
                if (!isRegisterMode) {
                    TextButton(onClick = { showForgotDialog = true }) {
                        Text(
                            stringResource(R.string.login_forgot_password),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // ── OTP-Fehler (SMS konnte nicht gesendet werden) ───────────────
            otpError?.let { err ->
                Card(
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(12.dp))
                        Text(err, fontSize = 14.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // ── Status-Meldung ─────────────────────────────────────────────
            AnimatedVisibility(
                visible = statusMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                statusMessage?.let { msg ->
                    val isSuccess = msg == loginSuccessMsg || msg.contains("erfolgreich", ignoreCase = true)
                    Card(
                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSuccess)
                                Color(0xFFE8F5E9) else MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = msg,
                                fontSize = 14.sp,
                                color = if (isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Neues-Gerät-Verifikations-Dialog ────────────────────────────────────
    val newDeviceState by viewModel.newDeviceState.collectAsState()
    var newDeviceSmsCode by remember { mutableStateOf("") }
    if (newDeviceState != null) {
        AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
            icon = {
                Icon(
                    Icons.Default.PhonelinkLock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Neues Gerät erkannt", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Du meldest dich von einem neuen Gerät an. Ein Verifizierungscode wurde per SMS an deine hinterlegte Nummer gesendet.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newDeviceSmsCode,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) newDeviceSmsCode = it },
                        label = { Text("SMS-Code (6 Stellen)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.verifyNewDevice(newDeviceSmsCode) },
                    enabled = newDeviceSmsCode.length == 6
                ) { Text("Bestätigen") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissNewDeviceVerification()
                    newDeviceSmsCode = ""
                }) { Text("Abbrechen") }
            }
        )
    }

    // ── SMS-OTP-Dialog ──────────────────────────────────────────────────────
    if (showOtpDialog) {
        AlertDialog(
            onDismissRequest = {
                showOtpDialog = false
                otpCode = ""
            },
            icon = {
                Icon(
                    Icons.Default.Sms,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text(stringResource(R.string.login_otp_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.login_otp_message, fullPhoneNumber),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpCode = it },
                        label = { Text(stringResource(R.string.login_otp_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOtpDialog = false
                        viewModel.register(
                            phoneNumber = fullPhoneNumber,
                            name = name,
                            password = password,
                            smsCode = otpCode,
                            isChildAccount = isChildAccount,
                            birthDate = if (isChildAccount && birthDate.isNotBlank()) birthDate else null,
                            inviteToken = if (isChildAccount && childInviteToken.isNotBlank()) childInviteToken else null
                        )
                    },
                    enabled = otpCode.length == 6
                ) {
                    Text(stringResource(R.string.login_otp_button))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOtpDialog = false
                    otpCode = ""
                }) {
                    Text(stringResource(R.string.login_cancel))
                }
            }
        )
    }
}
