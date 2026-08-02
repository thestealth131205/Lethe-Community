package com.securechat.app.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.securechat.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.app.BuildConfig
import com.securechat.app.billing.StyxProduct
import com.securechat.app.ui.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Guthaben-Screen: Zeigt aktuelles Styx-Guthaben und ermöglicht das Aufladen
 * via Google Play In-App-Käufe oder Stripe sowie den Münz-Tausch.
 *
 * Play-Store-Produkte (verbrauchbar):
 *   styx_1000_basic   →  1.000 Styx
 *   styx_2200_bonus   →  2.200 Styx  (+200 Bonus)
 *   styx_5750_bonus   →  5.750 Styx  (+750 Bonus)
 *
 * Stripe-Produkt:
 *   prod_UPF0iOcDcR0y9I  →  Kleiner Styx-Beutel (5000 Styx) für 6,50 €
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val products by viewModel.styxProducts.collectAsState()
    val isLoading by viewModel.billingLoading.collectAsState()
    val billingError by viewModel.billingError.collectAsState()
    val billingMessage by viewModel.billingMessage.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Aufladen", "Münz-Tausch")

    // Gaming-Münzen für Münz-Tausch Tab
    var myCoins by remember { mutableIntStateOf(0) }
    var coinsLoading by remember { mutableStateOf(false) }
    var exchangeLoading by remember { mutableStateOf(false) }
    var exchangeMessage by remember { mutableStateOf<String?>(null) }
    var exchangeError by remember { mutableStateOf<String?>(null) }

    // Billing initialisieren beim Betreten, trennen beim Verlassen
    LaunchedEffect(Unit) { viewModel.initBilling() }
    DisposableEffect(Unit) {
        onDispose { viewModel.disposeBilling() }
    }

    // Gaming-Münzen laden
    LaunchedEffect(Unit) {
        coinsLoading = true
        try {
            val r = viewModel.getMyGamingStats()
            if (r.isSuccessful) myCoins = r.body()?.totalCoins ?: 0
        } catch (_: Exception) {}
        coinsLoading = false
    }

    // Erfolgsmeldungen nach 3 Sekunden ausblenden
    LaunchedEffect(billingMessage) {
        if (billingMessage != null) { delay(3000); viewModel.clearBillingMessage() }
    }
    LaunchedEffect(exchangeMessage) {
        if (exchangeMessage != null) { delay(3000); exchangeMessage = null }
    }
    LaunchedEffect(exchangeError) {
        if (exchangeError != null) { delay(3000); exchangeError = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Guthaben",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = { Box(modifier = Modifier.size(48.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = {
            val msg = billingMessage ?: exchangeMessage ?: exchangeError
            AnimatedVisibility(visible = msg != null, enter = fadeIn(), exit = fadeOut()) {
                msg?.let { m ->
                    Snackbar(
                        modifier = Modifier.padding(16.dp),
                        containerColor = if (exchangeError != null)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (exchangeError != null)
                            MaterialTheme.colorScheme.onErrorContainer
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer
                    ) { Text(m, fontWeight = FontWeight.Bold) }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ── Kontostand ────────────────────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                Icon(
                    imageVector = Icons.Default.MonetizationOn,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${currentUser?.styx ?: 0}",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Styx-Coins",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(16.dp))
            }

            // ── Tabs ──────────────────────────────────────────────────────────────
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
                0 -> AufladenTab(
                    viewModel = viewModel,
                    activity = activity,
                    products = products,
                    isLoading = isLoading,
                    billingError = billingError,
                    onBuyPlayStore = { product ->
                        if (activity != null) viewModel.buyStyx(activity, product.productId)
                    },
                    onOpenWebTopup = {
                        // foss-Build: kein Play Billing – FossBillingProvider öffnet
                        // stattdessen https://letheapp.de/coins per Chrome Custom Tabs.
                        if (activity != null) viewModel.buyStyx(activity, "")
                    }
                )
                1 -> MuenzTauschTab(
                    myCoins = myCoins,
                    coinsLoading = coinsLoading,
                    exchangeLoading = exchangeLoading,
                    onExchange = {
                        scope.launch {
                            exchangeLoading = true
                            exchangeError = null
                            val result = viewModel.exchangeCoinsForStyx()
                            exchangeLoading = false
                            if (result != null) {
                                myCoins = result.newCoins
                                exchangeMessage = "Tausch erfolgreich! +100 Styx erhalten."
                            } else {
                                exchangeError = "Nicht genug Münzen oder Fehler beim Tausch."
                            }
                        }
                    }
                )
            }
        }
    }
}

// ─── Tab: Aufladen ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AufladenTab(
    viewModel: MainViewModel,
    activity: Activity?,
    products: List<StyxProduct>,
    isLoading: Boolean,
    billingError: String?,
    onBuyPlayStore: (StyxProduct) -> Unit,
    onOpenWebTopup: () -> Unit
) {
    // Zahlungsmethode: "playstore" | "stripe"
    var paymentMethod by remember { mutableStateOf("playstore") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Aufladen",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                textAlign = TextAlign.Start
            )
            Spacer(Modifier.height(12.dp))

            // ── Zahlungsmethode Dropdown ──────────────────────────────────────
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                OutlinedTextField(
                    value = if (paymentMethod == "playstore") stringResource(R.string.coins_payment_playstore_value) else stringResource(R.string.coins_payment_stripe_value),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.coins_payment_method_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.coins_payment_playstore_value)) },
                        leadingIcon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                        onClick = { paymentMethod = "playstore"; dropdownExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.coins_payment_stripe_value)) },
                        leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null) },
                        onClick = { paymentMethod = "stripe"; dropdownExpanded = false }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (paymentMethod == "playstore") {
            // ── Play-Store-Pakete ────────────────────────────────────────────
            when {
                isLoading -> item {
                    CircularProgressIndicator(modifier = Modifier.padding(40.dp), color = MaterialTheme.colorScheme.primary)
                }
                billingError != null -> item {
                    Text(
                        text = billingError,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        textAlign = TextAlign.Center
                    )
                }
                products.isEmpty() && BuildConfig.IS_FOSS -> item {
                    // foss-Build: kein Google Play Billing verfügbar (F-Droid-Auflage) –
                    // stattdessen Web-Aufladeseite (Stripe) per Chrome Custom Tabs anbieten.
                    WebTopupCard(onClick = onOpenWebTopup)
                }
                products.isEmpty() -> item {
                    Text(
                        text = "Keine Pakete verfügbar",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(24.dp),
                        textAlign = TextAlign.Center
                    )
                }
                else -> items(products) { product ->
                    StyxProductCard(product = product, onBuy = { onBuyPlayStore(product) })
                }
            }
        } else {
            // ── Stripe-Produkt ───────────────────────────────────────────────
            // Flavor-spezifisch: playstore nutzt das native Stripe-PaymentSheet-SDK
            // (bindet transitiv Google Pay/Play-Services-Wallet ein), foss leitet auf
            // dieselbe Web-Aufladeseite wie beim Play-Billing-Fallback weiter
            // (siehe StripeTopupTab.kt in den jeweiligen Flavor-Sourcesets).
            item {
                StripeTopupTab(viewModel = viewModel, activity = activity)
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ─── Web-Aufladung (foss-Build ohne Google Play Billing) ─────────────────────

@Composable
internal fun WebTopupCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.MonetizationOn,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "In dieser Version keine In-App-Käufe verfügbar.",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onClick, shape = RoundedCornerShape(12.dp)) {
                Text("Im Browser aufladen", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Tab: Münz-Tausch ────────────────────────────────────────────────────────

@Composable
private fun MuenzTauschTab(
    myCoins: Int,
    coinsLoading: Boolean,
    exchangeLoading: Boolean,
    onExchange: () -> Unit
) {
    val canExchange = myCoins >= 500

    LazyColumn(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Deine Gaming-Münzen",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(6.dp))
                    if (coinsLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    } else {
                        Text(
                            text = "$myCoins",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Münzen",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(text = "Münz-Tausch", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "500",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Münzen",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "100",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Styx",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (!canExchange && !coinsLoading) {
                        Text(
                            text = "Du brauchst noch ${500 - myCoins} Münzen",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = onExchange,
                        enabled = canExchange && !exchangeLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (exchangeLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Jetzt tauschen", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Gaming-Münzen sammelst du in Lethe-Games\n(Jump & Run, Tic Tac Toe, Sketch n Check)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── Kauf-Karte für ein Play-Store-Styx-Paket ────────────────────────────────

private data class StyxPackageInfo(val amount: Int, val isBonus: Boolean, val bonusLabel: String?)

private fun parseStyxPackage(productId: String): StyxPackageInfo = when (productId) {
    "styx_1000_basic" -> StyxPackageInfo(amount = 1_000, isBonus = false, bonusLabel = null)
    "styx_2200_bonus" -> StyxPackageInfo(amount = 2_200, isBonus = true, bonusLabel = "+200 Bonus")
    "styx_5750_bonus" -> StyxPackageInfo(amount = 5_750, isBonus = true, bonusLabel = "+750 Bonus")
    else -> StyxPackageInfo(
        amount = productId.filter { it.isDigit() }.toIntOrNull() ?: 0,
        isBonus = productId.contains("bonus"),
        bonusLabel = null
    )
}

@Composable
private fun StyxProductCard(product: StyxProduct, onBuy: () -> Unit) {
    val info = parseStyxPackage(product.productId)
    val price = product.formattedPrice

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (info.isBonus) Icons.Default.Stars else Icons.Default.MonetizationOn,
                    contentDescription = null,
                    tint = if (info.isBonus) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${"%,d".format(info.amount)} Styx",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        if (info.bonusLabel != null) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = info.bonusLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                    if (product.displayName.isNotBlank() && product.displayName != product.productId) {
                        Text(
                            text = product.displayName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }
            }
            Button(onClick = onBuy, shape = RoundedCornerShape(12.dp)) {
                Text(price, fontWeight = FontWeight.Bold)
            }
        }
    }
}
