package com.securechat.app.billing

import android.app.Activity
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FOSS/F-Droid-Bezahl-Anbindung – ohne Google Play Billing.
 *
 * F-Droid lässt keine proprietären In-App-Kauf-SDKs zu. Der `foss`-Build bietet daher
 * keine In-App-Käufe an (Produktliste bleibt immer leer); [buyProduct] öffnet stattdessen
 * die Stripe-basierte Web-Aufladeseite `https://letheapp.de/coins` per Chrome Custom Tabs
 * (dieselbe Seite, die auch der bereits vorhandene Stripe-Zahlungsweg in CoinsScreen nutzt).
 */
class FossBillingProvider @Inject constructor() : BillingProvider {

    companion object {
        private const val TAG = "LETHE_BILLING"
        const val WEB_TOPUP_URL = "https://letheapp.de/coins"
    }

    private val _products = MutableStateFlow<List<StyxProduct>>(emptyList())
    override val products: StateFlow<List<StyxProduct>> = _products.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    override var onPurchaseReady: (suspend (CoinPurchaseInfo) -> Int)? = null

    override fun connect() {
        // Keine In-App-Käufe in dieser Version – Produktliste bleibt bewusst leer.
        _isLoading.value = false
        _errorMessage.value = null
    }

    override fun disconnect() {
        // no-op – kein Billing-Client vorhanden.
    }

    /** Öffnet die Web-Aufladeseite statt eines In-App-Kaufs (Play Billing entfällt im foss-Build). */
    override fun buyProduct(activity: Activity, productId: String) {
        try {
            CustomTabsIntent.Builder().build().launchUrl(activity, Uri.parse(WEB_TOPUP_URL))
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Web-Aufladeseite konnte nicht geöffnet werden")
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingProviderModule {
    @Binds
    @Singleton
    abstract fun bindBillingProvider(impl: FossBillingProvider): BillingProvider
}
