package com.securechat.app.ui.screens

import android.app.Activity
import androidx.compose.runtime.Composable
import com.securechat.app.ui.MainViewModel

/**
 * Stripe-Zahlungssektion des Guthaben-Screens – foss/F-Droid-Build.
 *
 * Das native Stripe-PaymentSheet-SDK bietet Google Pay als Zahlungsmethode an und bindet
 * dafür transitiv `play-services-wallet` ein – proprietär und für F-Droid nicht zulässig
 * (siehe die playstore-Variante dieser Datei im `src/playstore`-Sourceset). Der foss-Build
 * nutzt stattdessen dieselbe Stripe-Web-Aufladeseite wie der Play-Billing-Fallback
 * (FossBillingProvider → Chrome Custom Tabs, kein natives SDK nötig).
 */
@Composable
fun StripeTopupTab(viewModel: MainViewModel, activity: Activity?) {
    WebTopupCard(onClick = { if (activity != null) viewModel.buyStyx(activity, "") })
}
