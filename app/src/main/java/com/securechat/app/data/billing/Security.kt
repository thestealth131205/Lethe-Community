package com.securechat.app.data.billing

import android.util.Base64
import timber.log.Timber
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Verifiziert Google-Play-Kaufsignaturen kryptografisch gegen den öffentlichen RSA-Schlüssel.
 *
 * Google signiert die Purchase-Daten mit SHA1withRSA. Wir prüfen lokal, ob die Signatur
 * mathematisch zum Schlüssel passt – bevor wir irgendetwas an den Server schicken.
 */
object Security {

    private const val TAG = "LETHE_SECURITY"
    private const val KEY_FACTORY_ALGORITHM = "RSA"
    private const val SIGNATURE_ALGORITHM = "SHA1withRSA"

    /**
     * Prüft, ob [signature] eine gültige RSA-Signatur von [signedData] ist,
     * verifiziert gegen [base64PublicKey] (Google-Play-Konsole → Base64 X.509 DER).
     *
     * @param base64PublicKey  Öffentlicher Schlüssel aus der Play-Konsole (Base64)
     * @param signedData       purchase.originalJson aus dem Billing SDK
     * @param signature        purchase.signature aus dem Billing SDK (Base64)
     * @return true  → Signatur gültig, Kauf ist echt
     *         false → Signatur ungültig oder Exception, Kauf ABLEHNEN
     */
    fun verifyPurchase(
        base64PublicKey: String,
        signedData: String,
        signature: String
    ): Boolean {
        if (base64PublicKey.isEmpty() || signedData.isEmpty() || signature.isEmpty()) {
            Timber.tag(TAG).w("verifyPurchase: Leere Parameter – Kauf abgelehnt")
            return false
        }
        return try {
            val publicKey = decodePublicKey(base64PublicKey)
            verifySignature(publicKey, signedData, signature)
        } catch (e: Exception) {
            Timber.tag(TAG).e("verifyPurchase Fehler: ${e.message}")
            false
        }
    }

    private fun decodePublicKey(base64Key: String): PublicKey {
        val keyBytes = Base64.decode(base64Key, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance(KEY_FACTORY_ALGORITHM).generatePublic(keySpec)
    }

    private fun verifySignature(publicKey: PublicKey, signedData: String, signature: String): Boolean {
        val signatureBytes = Base64.decode(signature, Base64.DEFAULT)
        val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
        sig.initVerify(publicKey)
        sig.update(signedData.toByteArray(Charsets.UTF_8))
        val valid = sig.verify(signatureBytes)
        if (!valid) Timber.tag(TAG).w("RSA-Signatur ungültig – möglicher Betrugsversuch!")
        return valid
    }
}
