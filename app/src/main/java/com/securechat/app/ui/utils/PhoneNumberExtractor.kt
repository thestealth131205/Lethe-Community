package com.securechat.app.ui.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * EPIC 3 – Liest die eigene Telefonnummer direkt vom SIM.
 *
 * API 33+: Nutzt SubscriptionManager.getPhoneNumber() (genaueste Methode).
 * API 26-32: Nutzt TelephonyManager.getLine1Number() (erfordert READ_PHONE_STATE).
 *
 * Benötigte Permissions (müssen zur Laufzeit angefragt werden):
 *   - android.permission.READ_PHONE_STATE
 *   - android.permission.READ_PHONE_NUMBERS (API 26+)
 *
 * Gibt null zurück falls:
 *   - Keine SIM vorhanden
 *   - Permission verweigert
 *   - Carrier hat die Nummer nicht bereitgestellt (leider häufig bei deutschen Providern)
 */
object PhoneNumberExtractor {

    /**
     * Versucht die eigene Rufnummer aus dem SIM zu lesen.
     * @return Rufnummer als String (möglicherweise ohne Ländervorwahl) oder null.
     */
    fun getOwnPhoneNumber(context: Context): String? {
        val hasPhoneState = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val hasPhoneNumbers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_NUMBERS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasPhoneState || !hasPhoneNumbers) return null

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+: SubscriptionManager mit getPhoneNumber()
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as? SubscriptionManager ?: return null
                val subId = SubscriptionManager.getDefaultSubscriptionId()
                if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return null
                val number = sm.getPhoneNumber(subId)
                number.ifBlank { null }
            } else {
                // API 26-32: TelephonyManager
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    ?: return null
                @SuppressLint("HardwareIds")
                val number = tm.line1Number
                if (number.isNullOrBlank()) null else number
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Normalisiert eine Rufnummer für die Anzeige im PhoneInputField.
     * Entfernt Leerzeichen, Klammern und Bindestriche.
     * Gibt (countryCode, localNumber) zurück.
     * Standardmäßig wird +49 als Ländervorwahl angenommen.
     */
    fun parseForDisplay(raw: String?): Pair<String, String> {
        if (raw.isNullOrBlank()) return Pair("+49", "")
        val cleaned = raw.replace(Regex("[\\s()\\-]"), "")
        return when {
            cleaned.startsWith("+49") -> Pair("+49", cleaned.removePrefix("+49"))
            cleaned.startsWith("+43") -> Pair("+43", cleaned.removePrefix("+43"))
            cleaned.startsWith("+41") -> Pair("+41", cleaned.removePrefix("+41"))
            cleaned.startsWith("+") -> {
                // Unbekannte Vorwahl: versuche 2-3 Ziffern zu extrahieren
                val match = Regex("^\\+(\\d{1,3})(.*)$").find(cleaned)
                if (match != null) {
                    Pair("+${match.groupValues[1]}", match.groupValues[2])
                } else Pair("+49", cleaned)
            }
            cleaned.startsWith("0") -> Pair("+49", cleaned.removePrefix("0"))
            else -> Pair("+49", cleaned)
        }
    }
}
