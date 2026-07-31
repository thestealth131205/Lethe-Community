package com.securechat.app.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * Transport-unabhängiges Bezahl-Produkt (Styx-Coin-Paket), damit `src/main`
 * (MainViewModel, CoinsScreen) frei von jeder `com.android.billingclient.api.*`-Abhängigkeit
 * bleibt (Play Billing liegt ausschließlich im `playstoreImplementation`-Scope).
 */
data class StyxProduct(
    val productId: String,
    val displayName: String,
    val formattedPrice: String,
    val priceAmountMicros: Long
)

/**
 * Daten eines abgeschlossenen, lokal (RSA) bereits verifizierten Kaufs – wird an
 * [MainViewModel.handleCoinPurchase] weitergereicht, das die serverseitige Prüfung
 * (POST /coins/verify-purchase) und Gutschrift (POST /coins/purchase) übernimmt.
 */
data class CoinPurchaseInfo(
    val productId: String,
    val purchaseToken: String,
    val signedData: String,
    val signature: String
)

/**
 * Bezahl-Anbindung für Styx-Coin-Käufe (F-Droid-Umbau, Phase F4).
 *
 * Der `playstore`-Flavor implementiert das über Google Play Billing
 * ([com.securechat.app.billing.PlaystoreBillingProvider]). Der `foss`/F-Droid-Flavor
 * kennt keine In-App-Käufe (F-Droid erlaubt keine proprietären Payment-SDKs) und leitet
 * stattdessen über Chrome Custom Tabs auf die Web-Aufladeseite `https://letheapp.de/coins`
 * (Stripe-basiert) weiter ([com.securechat.app.billing.FossBillingProvider]).
 *
 * MainViewModel ruft nur diese Schnittstelle auf, damit `src/main` frei von jeder
 * Billing-Client-Abhängigkeit bleibt.
 */
interface BillingProvider {
    /** Verfügbare Styx-Coin-Pakete (leer im foss-Build). */
    val products: StateFlow<List<StyxProduct>>

    /** true solange Produkte geladen werden. */
    val isLoading: StateFlow<Boolean>

    /** Fehler-/Hinweistext aus dem Billing-Flow (null = kein Fehler). */
    val errorMessage: StateFlow<String?>

    /**
     * Wird aufgerufen, sobald ein Kauf lokal verifiziert und bestätigt ist.
     * MainViewModel setzt diesen Callback, meldet den Kauf an den Server
     * und liefert die Anzahl gutgeschriebener Styx zurück (0 bei Fehler).
     * Erst bei Rückgabe > 0 wird der Kauf clientseitig konsumiert.
     */
    var onPurchaseReady: (suspend (CoinPurchaseInfo) -> Int)?

    /** Verbindet den Billing-Client (falls vorhanden) und lädt verfügbare Produkte. */
    fun connect()

    /** Trennt den Billing-Client – beim Verlassen des Screens aufrufen. */
    fun disconnect()

    /** Startet den Kauf-Flow für [productId]. Im foss-Build: öffnet die Web-Aufladeseite. */
    fun buyProduct(activity: Activity, productId: String)
}
