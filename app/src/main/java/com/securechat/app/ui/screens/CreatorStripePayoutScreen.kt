package com.securechat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.app.R
import com.securechat.app.ui.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorStripePayoutScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Auszahlung", "Styx → Diamanten")

    LaunchedEffect(Unit) {
        viewModel.loadCreatorProfile()
        viewModel.loadDiamondRate()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Auszahlung",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = { Box(Modifier.size(48.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.Medium) }
                    )
                }
            }

            when (selectedTab) {
                0 -> MuenzTauschTab(viewModel)
                1 -> StyxToDiamondsTab(viewModel)
            }
        }
    }
}

@Composable
private fun StyxToDiamondsTab(viewModel: MainViewModel) {
    val styxReceived by viewModel.creatorStyxReceived.collectAsState()
    val diamonds by viewModel.creatorDiamonds.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val userStyx = currentUser?.styx ?: 0

    val maxStyx = (styxReceived / 1000) * 1000
    var sliderValue by remember(maxStyx) { mutableFloatStateOf(if (maxStyx >= 1000) 1000f else 0f) }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Transfer state
    var transferUserToCreator by remember { mutableStateOf(true) } // true = User→Creator, false = Creator→User
    var transferSliderValue by remember { mutableFloatStateOf(0f) }
    var isTransferLoading by remember { mutableStateOf(false) }
    var transferMessage by remember { mutableStateOf<String?>(null) }
    var isTransferError by remember { mutableStateOf(false) }

    val transferMax = if (transferUserToCreator) userStyx else styxReceived
    val transferSteps = maxOf(0, (transferMax / 100) - 1)

    LaunchedEffect(transferUserToCreator) { transferSliderValue = 0f }
    LaunchedEffect(transferMessage) {
        if (transferMessage != null) { delay(5000); transferMessage = null }
    }

    LaunchedEffect(message) {
        if (message != null) { delay(5000); message = null }
    }

    val amount = sliderValue.toInt()
    val canConvert = maxStyx >= 1000 && amount >= 1000 && !isLoading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        // ── Styx-Einnahmen Kontostand ─────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Empfangene Styx",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "⚡ $styxReceived stX",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Diamond,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "$diamonds Diamanten",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFFD700)
                    )
                }
                Text(
                    text = "aktuelles Diamanten-Guthaben",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Styx übertragen ───────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Styx übertragen",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(12.dp))

                // Balance-Anzeige: User Styx | Pfeil | Creator Styx
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // User-Konto
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (transferUserToCreator)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("User", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Spacer(Modifier.height(4.dp))
                            Text("⚡ $userStyx", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Styx", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }

                    // Richtungs-Toggle
                    IconButton(
                        onClick = { transferUserToCreator = !transferUserToCreator }
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = "Richtung wechseln",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Creator-Konto
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!transferUserToCreator)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Creator", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Spacer(Modifier.height(4.dp))
                            Text("⚡ $styxReceived", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Styx", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (transferUserToCreator) "User → Creator" else "Creator → User",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(12.dp))

                if (transferMax <= 0) {
                    Text(
                        text = "Kein übertragbares Guthaben vorhanden.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "⚡ ${transferSliderValue.toInt()} Styx",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Slider(
                        value = transferSliderValue,
                        onValueChange = { transferSliderValue = it },
                        valueRange = 0f..transferMax.toFloat(),
                        steps = transferSteps,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text("${"%,d".format(transferMax)} Styx", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }

                Spacer(Modifier.height(8.dp))

                transferMessage?.let { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isTransferError) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(10.dp),
                            color = if (isTransferError) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        scope.launch {
                            isTransferLoading = true
                            transferMessage = null
                            val direction = if (transferUserToCreator) "user_to_creator" else "creator_to_user"
                            viewModel.transferStyx(transferSliderValue.toInt(), direction) { success, msg ->
                                isTransferLoading = false
                                isTransferError = !success
                                transferMessage = msg
                                if (success) transferSliderValue = 0f
                            }
                        }
                    },
                    enabled = transferSliderValue >= 1f && !isTransferLoading && transferMax > 0,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isTransferLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Übertragen", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Styx zu Diamanten tauschen",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tausche deine empfangenen Styx in Diamanten um. Kurs: 1000 stX = 1 €. Diamanten kannst du anschließend im Tab \"Auszahlung\" auszahlen.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (maxStyx < 1000) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = "Du benötigst mindestens 1.000 stX auf deinem Konto für einen Tausch.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val stepsCount = (maxStyx / 1000) - 1
            Text(
                text = "⚡ ${amount} stX",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                valueRange = 1000f..maxStyx.toFloat(),
                steps = stepsCount,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("1.000 stX", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text("${"%,d".format(maxStyx)} stX", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            Spacer(Modifier.height(8.dp))
            val euroValue = "%.2f".format(amount / 1000.0).replace('.', ',')
            Text(
                text = "Du erhältst: $euroValue € (${amount} stX × 0,001)",
                fontSize = 13.sp,
                color = Color(0xFFFFD700),
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(16.dp))

        message?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = msg,
                    modifier = Modifier.padding(12.dp),
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    message = null
                    viewModel.convertStyxToDiamonds(amount) { success, msg ->
                        isLoading = false
                        isError = !success
                        message = msg
                        if (success) sliderValue = if (maxStyx >= 1000) 1000f else 0f
                    }
                }
            },
            enabled = canConvert,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Tauschen", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun MuenzTauschTab(viewModel: MainViewModel) {
    val diamonds by viewModel.creatorDiamonds.collectAsState()
    val diamondRate by viewModel.diamondToEuroRate.collectAsState()

    val euroValue = diamonds * diamondRate
    val minPayoutEuro = 20.0
    val canPayout = euroValue >= minPayoutEuro

    var iban by remember { mutableStateOf("") }
    var holderName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(message) {
        if (message != null) { delay(5000); message = null }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        // ── Diamant-Kontostand ────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Diamond,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$diamonds",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = stringResource(R.string.creator_stripe_diamonds_label),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Euro,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "%.2f €".format(euroValue),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                }
                Text(
                    text = stringResource(R.string.creator_stripe_payout_value).format(diamondRate),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Mindestbetrag-Hinweis ─────────────────────────────────────────────
        if (!canPayout) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                val stillNeeded = ((minPayoutEuro - euroValue) / diamondRate).toInt() + 1
                Text(
                    text = stringResource(R.string.creator_stripe_min_payout_hint).format(minPayoutEuro, stillNeeded),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Bankverbindung ────────────────────────────────────────────────────
        Text(
            text = stringResource(R.string.creator_stripe_bank_details_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = iban,
            onValueChange = { iban = it.take(34).uppercase().replace(" ", "") },
            label = { Text(stringResource(R.string.creator_stripe_iban_label)) },
            placeholder = { Text(stringResource(R.string.creator_stripe_iban_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.Characters
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = holderName,
            onValueChange = { holderName = it.take(100) },
            label = { Text(stringResource(R.string.creator_stripe_holder_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.take(100) },
            label = { Text(stringResource(R.string.creator_stripe_email_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        // ── Status-Meldung ────────────────────────────────────────────────────
        message?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = msg,
                    modifier = Modifier.padding(12.dp),
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── Auszahlen-Button ──────────────────────────────────────────────────
        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    message = null
                    viewModel.requestCreatorPayout(
                        iban = iban,
                        name = holderName,
                        email = email
                    ) { success, msg ->
                        isLoading = false
                        isError = !success
                        message = msg
                    }
                }
            },
            enabled = canPayout && iban.length >= 15 && holderName.isNotBlank() && email.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    stringResource(R.string.creator_stripe_request_payout).format(euroValue),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Die Auszahlung erfolgt per SEPA-Banküberweisung innerhalb von 3–5 Werktagen. " +
                    "Die Diamanten werden nach Beantragung sofort von deinem Konto abgezogen.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))
    }
}
