package com.securechat.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Play Billing – In-App-Käufe für Styx-Coins. Nur im `playstore`-Flavor
 * (Play Billing ist proprietär und für F-Droid nicht zulässig; siehe [FossBillingProvider]).
 *
 * Verwaltete Produkte (in Play Console als verbrauchbare In-App-Produkte anlegen):
 *   styx_1000_basic   →  1.000 Styx
 *   styx_2200_bonus   →  2.200 Styx  (+200 Bonus)
 *   styx_5750_bonus   →  5.750 Styx  (+750 Bonus)
 *
 * Ablauf:
 *   1. connect()                    – BillingClient verbinden & Produkte laden
 *   2. buyProduct(activity, id)     – Kauf-Flow starten
 *   3. onPurchaseReady              – Server-Gutschrift-Callback (von MainViewModel gesetzt)
 *   4. consumeAsync                 – Automatisch nach Server-Bestätigung
 *   5. disconnect()                 – beim Verlassen des Screens
 */
@Singleton
class PlaystoreBillingProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : BillingProvider {
    companion object {
        val PRODUCT_IDS = listOf("styx_1000_basic", "styx_2200_bonus", "styx_5750_bonus")
        private const val TAG = "LETHE_BILLING"

        /**
         * Öffentlicher RSA-Schlüssel aus der Google Play-Konsole.
         * → Play Console → App → Monetarisierung einrichten → Lizenzschlüssel
         */
        const val GOOGLE_PLAY_PUBLIC_KEY =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAo/zjgvk8wga3TJUNmQaV" +
            "B7WzFiSIiqjKSm5ZDchON4ERmSu6NeYFctQKJYrDgPSdspSCIZ08yGF+rxmV4bWB" +
            "sKcyLc+QHPvpnbV4WnCfiEUnUyH5Jv/4F3MZO3FgkrHhWWsD2HU4Kvqd724vu0k9" +
            "6XSI7wthh8J60wP1LZzcC3GvJRLZj7DQ20UJEMAE3Nww39JCyKXIH4j0ItMl5TFZ" +
            "5xRJKFjFa7jHRzc0EYxQ+BWaaLp4nJx6TtVhd3x4aEYPcfdR5xnyPaZL/hA0S67O" +
            "RQxCbkVPw8NlJsLa347XDK0QersRzgUx7GFD6Io0FnTQVHSytDjOyeEAbxKNDHOy" +
            "5QIDAQAB"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** ProductDetails-Cache (productId → ProductDetails), damit buyProduct(id) den Flow starten kann. */
    private val productDetailsById = mutableMapOf<String, ProductDetails>()

    // ── BillingProvider-Implementierung ─────────────────────────────────────

    private val _products = MutableStateFlow<List<StyxProduct>>(emptyList())
    override val products: StateFlow<List<StyxProduct>> = _products.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    override var onPurchaseReady: (suspend (CoinPurchaseInfo) -> Int)? = null

    // ── BillingClient ────────────────────────────────────────────────────────

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener { billingResult, purchases ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    purchases?.forEach { purchase ->
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            scope.launch {
                                // ── Schritt 1: Lokale RSA-Signaturprüfung ──────────────────
                                val signatureValid = Security.verifyPurchase(
                                    base64PublicKey = GOOGLE_PLAY_PUBLIC_KEY,
                                    signedData      = purchase.originalJson,
                                    signature       = purchase.signature
                                )
                                if (!signatureValid) {
                                    Timber.tag(TAG).e("RSA-Prüfung fehlgeschlagen – Kauf wird NICHT gutgeschrieben!")
                                    _errorMessage.value = "Kauf konnte nicht verifiziert werden."
                                    return@launch
                                }
                                Timber.tag(TAG).d("RSA-Signatur OK – sende an Server zur Bestätigung")

                                // ── Schritt 2: Server-Bestätigung + Gutschrift ─────────────
                                val productId = purchase.products.firstOrNull() ?: return@launch
                                val info = CoinPurchaseInfo(
                                    productId = productId,
                                    purchaseToken = purchase.purchaseToken,
                                    signedData = purchase.originalJson,
                                    signature = purchase.signature
                                )
                                val added = onPurchaseReady?.invoke(info) ?: 0
                                if (added > 0) {
                                    Timber.tag(TAG).d("+$added Styx gutgeschrieben – konsumiere Kauf")
                                    // ── Schritt 3: Kauf erst nach Server-OK konsumieren ─────
                                    consumePurchase(purchase)
                                } else {
                                    Timber.tag(TAG).w("Server-Bestätigung fehlgeschlagen – Kauf NICHT konsumiert")
                                }
                            }
                        }
                    }
                }
                BillingClient.BillingResponseCode.USER_CANCELED ->
                    Timber.tag(TAG).d("Kauf vom Nutzer abgebrochen")
                else -> {
                    _errorMessage.value = "Kauf fehlgeschlagen (Code: ${billingResult.responseCode})"
                    Timber.tag(TAG).w("PurchasesUpdated error: ${billingResult.debugMessage}")
                }
            }
        }
        .enablePendingPurchases()
        .build()

    // ── Öffentliche Funktionen ───────────────────────────────────────────────

    /** Verbindet den BillingClient und lädt verfügbare Produkte. */
    override fun connect() {
        _isLoading.value = true
        _errorMessage.value = null
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    loadProducts()
                } else {
                    _isLoading.value = false
                    _errorMessage.value = "Play Store nicht verfügbar"
                    Timber.tag(TAG).w("Billing setup failed: ${result.responseCode}")
                }
            }

            override fun onBillingServiceDisconnected() {
                _isLoading.value = false
                _errorMessage.value = "Verbindung zum Play Store getrennt"
            }
        })
    }

    /** Startet den Google-Play-Kauf-Dialog für [productId]. */
    override fun buyProduct(activity: Activity, productId: String) {
        val product = productDetailsById[productId] ?: run {
            Timber.tag(TAG).w("buyProduct: ProductDetails für $productId nicht gefunden")
            return
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .build()
                )
            )
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    /** Trennt den BillingClient – beim Verlassen des Screens aufrufen. */
    override fun disconnect() {
        billingClient.endConnection()
    }

    // ── Private Hilfsfunktionen ──────────────────────────────────────────────

    private fun loadProducts() {
        val productList = PRODUCT_IDS.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        billingClient.queryProductDetailsAsync(params) { result, details ->
            _isLoading.value = false
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsById.clear()
                details.forEach { productDetailsById[it.productId] = it }
                _products.value = details
                    .sortedBy { it.oneTimePurchaseOfferDetails?.priceAmountMicros ?: 0L }
                    .map { pd ->
                        StyxProduct(
                            productId = pd.productId,
                            displayName = pd.name,
                            formattedPrice = pd.oneTimePurchaseOfferDetails?.formattedPrice ?: "–",
                            priceAmountMicros = pd.oneTimePurchaseOfferDetails?.priceAmountMicros ?: 0L
                        )
                    }
                Timber.tag(TAG).d("${details.size} Styx-Pakete geladen")
            } else {
                _errorMessage.value = "Pakete konnten nicht geladen werden"
                Timber.tag(TAG).w("queryProductDetails failed: ${result.responseCode}")
            }
        }
    }

    private suspend fun consumePurchase(purchase: Purchase) {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.consumeAsync(params) { result, _ ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Timber.tag(TAG).w("consumeAsync fehlgeschlagen: ${result.debugMessage}")
            } else {
                Timber.tag(TAG).d("Kauf verbraucht: ${purchase.products.firstOrNull()}")
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingProviderModule {
    @Binds
    @Singleton
    abstract fun bindBillingProvider(impl: PlaystoreBillingProvider): BillingProvider
}
