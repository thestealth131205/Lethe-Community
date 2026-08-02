package com.securechat.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.app.ui.MainViewModel
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.rememberPaymentSheet
import kotlinx.coroutines.delay

/**
 * Stripe-Zahlungssektion des Guthaben-Screens – nur playstore-Flavor.
 *
 * Das native Stripe-PaymentSheet-SDK bietet Google Pay als Zahlungsmethode an und bindet
 * dafür transitiv `play-services-wallet` ein – proprietär und für F-Droid nicht zulässig.
 * Siehe die foss-Variante dieser Datei (Web-Checkout via Chrome Custom Tabs) im
 * `src/foss`-Sourceset.
 */
@Composable
fun StripeTopupTab(viewModel: MainViewModel, activity: Activity?) {
    val context = LocalContext.current
    var stripeLoading by remember { mutableStateOf(false) }
    var stripeError by remember { mutableStateOf<String?>(null) }

    val paymentSheet = rememberPaymentSheet { result ->
        stripeLoading = false
        when (result) {
            is PaymentSheetResult.Completed -> {
                // Extrahiere PI-ID aus dem gespeicherten clientSecret
                val piId = context.getSharedPreferences("stripe_tmp", android.content.Context.MODE_PRIVATE)
                    .getString("last_pi_id", null)
                if (piId != null) {
                    viewModel.onStripePaymentCompleted(piId)
                }
            }
            is PaymentSheetResult.Canceled -> {}
            is PaymentSheetResult.Failed -> {
                stripeError = result.error.localizedMessage ?: "Zahlung fehlgeschlagen."
            }
        }
    }

    LaunchedEffect(stripeError) {
        if (stripeError != null) { delay(4000); stripeError = null }
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        StripeProductCard(
            loading = stripeLoading,
            onBuy = {
                stripeLoading = true
                stripeError = null
                viewModel.createStripePaymentIntent { clientSecret, publishableKey ->
                    if (clientSecret != null && publishableKey != null) {
                        // PI-ID für spätere Gutschrift zwischenspeichern
                        val piId = clientSecret.substringBefore("_secret_")
                        context.getSharedPreferences("stripe_tmp", android.content.Context.MODE_PRIVATE)
                            .edit().putString("last_pi_id", piId).apply()
                        PaymentConfiguration.init(context, publishableKey)
                        paymentSheet.presentWithPaymentIntent(
                            clientSecret,
                            PaymentSheet.Configuration(merchantDisplayName = "Lethe")
                        )
                    } else {
                        stripeLoading = false
                        stripeError = "Stripe-Zahlung konnte nicht gestartet werden."
                    }
                }
            }
        )
        stripeError?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
private fun StripeProductCard(loading: Boolean, onBuy: () -> Unit) {
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
                    imageVector = Icons.Default.MonetizationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(text = "5000 Styx", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        text = "Kleiner Styx-Beutel",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
            Button(
                onClick = onBuy,
                enabled = !loading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("6,50 €", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Sichere Zahlung über Stripe. Kreditkarte, SEPA-Lastschrift u. v. m.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}
