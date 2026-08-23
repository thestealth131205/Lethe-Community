package com.securechat.app.data.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * EPIC 1 – Hybrid E2EE: ECDH P-384 + AES-256-GCM
 * EPIC 5 – Multi-Device: Web-Device-Schlüssel + ECDSA-Signierung für Anti-MITM
 *
 * Schlüsselarten:
 *   KEY_ALIAS         (EC P-384, PURPOSE_AGREE_KEY)  – ECDH für Nachrichtenverschlüsselung
 *   SIGNING_KEY_ALIAS (EC P-256, PURPOSE_SIGN)       – ECDSA zum Signieren des Web-Keys
 *
 * API 31+: Beide Schlüssel hardware-backed im AndroidKeyStore.
 * API 26-30: Software-Schlüssel im JVM-Speicher; ECDH-Private-Key in UserEntity.privateKey persistiert.
 *            Der Signing-Key wird per Session neu generiert (ok da nur Handshake-Signierung).
 *
 * Nachrichtenformat auf dem Draht:
 *   "v2:<Base64(12-Byte-IV | AES-256-GCM-Ciphertext+Tag)>"
 *
 * Web-Gerät-Public-Key: SPKI/DER-Format (SubjectPublicKeyInfo, X.509) Base64-kodiert.
 *   WebCrypto exportKey("spki") liefert exakt dieses Format → direkt mit X509EncodedKeySpec verwendbar.
 */
object CryptoManager {

    private const val ANDROID_KEYSTORE    = "AndroidKeyStore"
    private const val KEY_ALIAS           = "lethe_ecdh_key_v1"
    private const val SIGNING_KEY_ALIAS   = "lethe_signing_key_v1"
    private const val EC_CURVE            = "secp384r1"
    private const val SIGNING_CURVE       = "secp256r1"
    private const val KEY_ALGO            = "EC"
    private const val AES_TRANSFORM       = "AES/GCM/NoPadding"
    private const val GCM_IV_SIZE         = 12
    private const val GCM_TAG_BITS        = 128
    private const val MSG_PREFIX          = "v2:"
    private const val MSG_PREFIX_V3       = "v3:"
    private const val HKDF_INFO           = "LETHE_V2_AES256"
    private const val HKDF_INFO_CK       = "LETHE_CONVERSATION_KEY_V1"
    private const val BACKUP_PREFIX       = "kbk_v1:"
    private const val UMK_PREFIX          = "umk_v1:"
    private const val BACKUP_PBKDF2_ITER  = 100_000
    private const val UMK_PBKDF2_ITER    = 200_000

    /** Cache: contactId → AES-256-Schlüssel (abgeleitet vom Android-Gerät-Key des Kontakts) */
    private val sharedSecretCache = mutableMapOf<String, ByteArray>()

    /** Cache: "contactId::web" → AES-256-Schlüssel (abgeleitet vom Web-Gerät-Key des Kontakts) */
    private val webSharedSecretCache = mutableMapOf<String, ByteArray>()

    // ──────────────────────────────────────────────────────────────────────────
    // UMK (User Master Key) – Universelle Multi-Device Verschlüsselung
    // ─���────────────────────────────────────────────────────────────────────────

    /** Der rohe AES-256 User Master Key (32 Byte), null wenn noch nicht geladen */
    private var umkBytes: ByteArray? = null

    /** Cache: partnerId → Conversation Key (AES-256, abgeleitet aus UMK_A XOR UMK_B) */
    private val conversationKeyCache = mutableMapOf<String, ByteArray>()

    /** Partner-UMKs die vom Server geholt wurden: partnerId → UMK-Bytes */
    private val partnerUmkCache = mutableMapOf<String, ByteArray>()

    /** Soft ECDH Key (API 26-30): im Speicher gehalten während der Session */
    private var softKeyPair: java.security.KeyPair? = null

    /** Soft Signing Key: EC P-256 (alle API-Level als Software-Schlüssel, da nur Handshake) */
    private var softSigningKeyPair: java.security.KeyPair? = null

    // ──────────────────────────────────────────────────────────────────────────
    // ECDH-Schlüsselverwaltung
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Stellt sicher dass ein ECDH P-384-Schlüsselpaar existiert.
     * Gibt den Public Key als Base64-kodierten X.509-DER-String zurück.
     */
    fun ensureKeyPair(context: Context, storedSoftPrivKey: String? = null, storedSoftPubKey: String? = null): String {
        // Soft Key hat Vorrang: nach Backup-Restore oder auf API 26-30
        softKeyPair?.let { kp ->
            return Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ensureHardwareKeyPair()
        } else {
            ensureSoftKeyPair(storedSoftPrivKey, storedSoftPubKey)
        }
    }

    fun exportSoftPrivateKey(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return null
        val priv = softKeyPair?.private ?: return null
        return Base64.encodeToString(priv.encoded, Base64.NO_WRAP)
    }

    private fun ensureHardwareKeyPair(): String {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!ks.containsAlias(KEY_ALIAS)) {
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_AGREE_KEY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
                .build()
            KeyPairGenerator.getInstance(KEY_ALGO, ANDROID_KEYSTORE).apply {
                initialize(spec)
                generateKeyPair()
            }
        }
        val cert = ks.getCertificate(KEY_ALIAS) as java.security.cert.X509Certificate
        return Base64.encodeToString(cert.publicKey.encoded, Base64.NO_WRAP)
    }

    private fun ensureSoftKeyPair(storedPrivKey: String?, storedPubKey: String?): String {
        softKeyPair?.let { kp ->
            return Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
        }
        if (storedPrivKey != null) {
            try {
                val privBytes = Base64.decode(storedPrivKey, Base64.NO_WRAP)
                val kf = KeyFactory.getInstance(KEY_ALGO)
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                // Gespeicherten Public Key verwenden (>100 Zeichen = ECDH P-384, kein SHA256-Fallback)
                if (!storedPubKey.isNullOrBlank() && storedPubKey.length > 100) {
                    try {
                        val pubBytes = Base64.decode(storedPubKey, Base64.NO_WRAP)
                        val pub = kf.generatePublic(X509EncodedKeySpec(pubBytes))
                        softKeyPair = java.security.KeyPair(pub, priv)
                        return Base64.encodeToString(pub.encoded, Base64.NO_WRAP)
                    } catch (_: Exception) {}
                }
                // Fallback: neues Schlüsselpaar generieren und hochladen
                val kpg = KeyPairGenerator.getInstance(KEY_ALGO)
                kpg.initialize(ECGenParameterSpec(EC_CURVE))
                val freshPair = kpg.generateKeyPair()
                softKeyPair = java.security.KeyPair(freshPair.public, priv)
                return Base64.encodeToString(freshPair.public.encoded, Base64.NO_WRAP)
            } catch (_: Exception) {}
        }
        val kpg = KeyPairGenerator.getInstance(KEY_ALGO)
        kpg.initialize(ECGenParameterSpec(EC_CURVE))
        val kp = kpg.generateKeyPair()
        softKeyPair = kp
        return Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ECDSA Signing-Schlüssel (P-256, Anti-MITM beim QR-Handshake)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Stellt sicher dass ein ECDSA P-256 Signing-Schlüsselpaar existiert.
     * API 31+: Hardware-backed im AndroidKeyStore (PURPOSE_SIGN).
     * API 26-30: Software-Schlüssel (wird per Session neu generiert; ausreichend für Handshake-Signierung).
     *
     * @return Base64-kodierter X.509-DER Public Key des Signing-Schlüssels.
     */
    fun ensureSigningKeyPair(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ensureHardwareSigningKey()
        } else {
            ensureSoftSigningKey()
        }
    }

    private fun ensureHardwareSigningKey(): String {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!ks.containsAlias(SIGNING_KEY_ALIAS)) {
            val spec = KeyGenParameterSpec.Builder(
                SIGNING_KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec(SIGNING_CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
            KeyPairGenerator.getInstance(KEY_ALGO, ANDROID_KEYSTORE).apply {
                initialize(spec)
                generateKeyPair()
            }
        }
        val cert = ks.getCertificate(SIGNING_KEY_ALIAS) as java.security.cert.X509Certificate
        return Base64.encodeToString(cert.publicKey.encoded, Base64.NO_WRAP)
    }

    private fun ensureSoftSigningKey(): String {
        softSigningKeyPair?.let {
            return Base64.encodeToString(it.public.encoded, Base64.NO_WRAP)
        }
        val kpg = KeyPairGenerator.getInstance(KEY_ALGO)
        kpg.initialize(ECGenParameterSpec(SIGNING_CURVE))
        val kp = kpg.generateKeyPair()
        softSigningKeyPair = kp
        return Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
    }

    /**
     * Signiert Byte-Daten mit dem ECDSA P-256 Signing-Schlüssel.
     * @return Base64-kodierte DER-Signatur, oder null bei Fehler.
     */
    fun signBytes(data: ByteArray): String? {
        return try {
            val sig = Signature.getInstance("SHA256withECDSA")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                if (!ks.containsAlias(SIGNING_KEY_ALIAS)) ensureHardwareSigningKey()
                val key = ks.getKey(SIGNING_KEY_ALIAS, null) as PrivateKey
                sig.initSign(key)
            } else {
                val key = softSigningKeyPair?.private
                    ?: run { ensureSoftSigningKey(); softSigningKeyPair!!.private }
                sig.initSign(key)
            }
            sig.update(data)
            Base64.encodeToString(sig.sign(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Shared Secret Derivation
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Leitet den AES-256-Shared-Secret für den Android-Schlüssel eines Kontakts ab und cacht ihn.
     * partnerPubKey64: Base64-kodierter X.509-DER ECDH P-384 Public Key (von Android-Gerät).
     * Gibt true zurück wenn die Ableitung erfolgreich war.
     */
    fun deriveSharedSecret(contactId: String, partnerPubKey64: String): Boolean {
        if (sharedSecretCache.containsKey(contactId)) return true
        if (partnerPubKey64.length <= 100) {
            Timber.tag("LETHE_E2EE").w("deriveSharedSecret[$contactId]: Key abgelehnt (zu kurz: ${partnerPubKey64.length} Zeichen)")
            return false   // SHA-256-Legacy-Keys abweisen
        }
        // Base64-Format prüfen, bevor ECDH versucht wird
        return try {
            Base64.decode(partnerPubKey64, Base64.NO_WRAP)
            deriveAndStore(contactId, partnerPubKey64, sharedSecretCache)
        } catch (e: IllegalArgumentException) {
            Timber.tag("LETHE_E2EE").w("deriveSharedSecret[$contactId]: Ungültiges Base64-Format – ${e.message}")
            false
        }
    }

    /**
     * Leitet den AES-256-Shared-Secret für das Web-Gerät eines Kontakts ab.
     * webPubKey64: Base64-kodierter SPKI/X.509-DER ECDH P-384 Public Key (vom Web-Browser via WebCrypto).
     * Cache-Key: "contactId::web"
     */
    fun deriveWebSharedSecret(contactId: String, webPubKey64: String): Boolean {
        val cacheKey = "$contactId::web"
        if (webSharedSecretCache.containsKey(cacheKey)) return true
        if (webPubKey64.length <= 80) {
            Timber.tag("LETHE_E2EE").w("deriveWebSharedSecret[$contactId]: Web-Key abgelehnt (zu kurz: ${webPubKey64.length} Zeichen)")
            return false   // SPKI P-384 ≈ 160 Zeichen; ablehnen wenn zu kurz
        }
        return try {
            Base64.decode(webPubKey64, Base64.NO_WRAP)
            deriveAndStore(cacheKey, webPubKey64, webSharedSecretCache)
        } catch (e: IllegalArgumentException) {
            Timber.tag("LETHE_E2EE").w("deriveWebSharedSecret[$contactId]: Ungültiges Base64-Format – ${e.message}")
            false
        }
    }

    private fun deriveAndStore(
        cacheKey: String,
        partnerPubKey64: String,
        cache: MutableMap<String, ByteArray>
    ): Boolean {
        return try {
            val partnerKeyBytes = Base64.decode(partnerPubKey64, Base64.NO_WRAP)
            val kf = KeyFactory.getInstance(KEY_ALGO)
            val partnerKey = kf.generatePublic(X509EncodedKeySpec(partnerKeyBytes))

            val ka: KeyAgreement
            val localPrivateKey: PrivateKey

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val softPriv = softKeyPair?.private
                if (softPriv != null) {
                    // Soft Key hat Vorrang (nach Backup-Restore oder nach backupKeys()-Aufruf)
                    localPrivateKey = softPriv
                    ka = KeyAgreement.getInstance("ECDH")
                } else {
                    val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                    if (!ks.containsAlias(KEY_ALIAS)) return false
                    localPrivateKey = ks.getKey(KEY_ALIAS, null) as PrivateKey
                    ka = KeyAgreement.getInstance("ECDH", ANDROID_KEYSTORE)
                }
            } else {
                localPrivateKey = softKeyPair?.private ?: return false
                ka = KeyAgreement.getInstance("ECDH")
            }

            ka.init(localPrivateKey)
            ka.doPhase(partnerKey, true)
            val rawSecret = ka.generateSecret()

            // HKDF-ähnliche Ableitung: HMAC-SHA256(rawSecret, "LETHE_V2_AES256") → AES-256-Schlüssel
            val aesKey = Mac.getInstance("HmacSHA256").apply {
                init(SecretKeySpec(rawSecret, "HmacSHA256"))
            }.doFinal(HKDF_INFO.toByteArray(Charsets.UTF_8))

            cache[cacheKey] = aesKey
            true
        } catch (_: Exception) {
            false
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Verschlüsselung
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Verschlüsselt für das Android-Gerät des Kontakts.
     * Gibt Klartext zurück wenn kein Shared Secret vorhanden (kein Fehler – stille Fallback-Logik).
     */
    fun encrypt(contactId: String, plaintext: String): String {
        val secret = sharedSecretCache[contactId] ?: return plaintext
        return encryptWithSecret(secret, plaintext)
    }

    /**
     * Verschlüsselt explizit für das Web-Gerät eines Kontakts.
     * Gibt null zurück wenn kein Web-Shared-Secret gecacht ist.
     */
    fun encryptForWeb(contactId: String, plaintext: String): String? {
        val secret = webSharedSecretCache["$contactId::web"] ?: return null
        return encryptWithSecret(secret, plaintext)
    }

    private fun encryptWithSecret(secret: ByteArray, plaintext: String): String {
        return try {
            val iv = ByteArray(GCM_IV_SIZE).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(AES_TRANSFORM)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(secret, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArrayOutputStream().apply { write(iv); write(ciphertext) }.toByteArray()
            "$MSG_PREFIX${Base64.encodeToString(combined, Base64.NO_WRAP)}"
        } catch (_: Exception) {
            plaintext
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Entschlüsselung
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Entschlüsselt eine Nachricht.
     * Versucht zuerst den Android-Key-Cache, dann den Web-Key-Cache als Fallback.
     * AES-GCM Authentication-Tag stellt sicher, dass ein falscher Key sofort erkannt wird.
     */
    fun decrypt(contactId: String, ciphertext: String): String {
        if (!ciphertext.startsWith(MSG_PREFIX)) return ciphertext

        // Versuch 1: Android-Shared-Secret
        sharedSecretCache[contactId]?.let { secret ->
            try { return decryptWithSecret(secret, ciphertext) } catch (_: Exception) {}
        }

        // Versuch 2: Web-Gerät-Shared-Secret (für Nachrichten die vom Web-Client stammen)
        webSharedSecretCache["$contactId::web"]?.let { secret ->
            try {
                return decryptWithSecret(secret, ciphertext)
            } catch (_: Exception) {
                return "[🔐 Entschlüsselung fehlgeschlagen]"
            }
        }

        return "[🔐 Verschlüsselte Nachricht – Schlüssel nicht verfügbar]"
    }

    /**
     * Entschlüsselt mit explizit angegebenem Secret (für interne Aufrufe).
     * Wirft Exception bei falscher Auth-Tag.
     */
    private fun decryptWithSecret(secret: ByteArray, ciphertext: String): String {
        val combined = Base64.decode(ciphertext.removePrefix(MSG_PREFIX), Base64.NO_WRAP)
        val iv   = combined.copyOf(GCM_IV_SIZE)
        val data = combined.copyOfRange(GCM_IV_SIZE, combined.size)
        val cipher = Cipher.getInstance(AES_TRANSFORM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(secret, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    /**
     * Universelle Entschlüsselung: versucht Android-Key, dann Web-Key.
     * Sicher gegen Exceptions durch AES-GCM Auth-Tag.
     */
    fun decryptBestEffort(contactId: String, ciphertext: String): String {
        if (!ciphertext.startsWith(MSG_PREFIX)) return ciphertext

        sharedSecretCache[contactId]?.let { secret ->
            try { return decryptWithSecret(secret, ciphertext) } catch (_: Exception) {}
        }
        webSharedSecretCache["$contactId::web"]?.let { secret ->
            try { return decryptWithSecret(secret, ciphertext) } catch (_: Exception) {}
        }
        return "[🔐 Verschlüsselte Nachricht – Schlüssel nicht verfügbar]"
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cache-Verwaltung
    // ──────────────────────────────────────────────────────────────────────────

    fun hasSharedSecret(contactId: String) = sharedSecretCache.containsKey(contactId)
    fun hasWebSharedSecret(contactId: String) = webSharedSecretCache.containsKey("$contactId::web")

    /**
     * Erzwingt die Neu-Ableitung des Android-Shared-Secrets für einen Kontakt.
     * Löscht den alten Cache-Eintrag und leitet mit dem neuen Public Key neu ab.
     * Wird bei Schlüssel-Rotation (Gerätewechsel) aufgerufen.
     */
    fun forceRederiveSharedSecret(contactId: String, partnerPubKey64: String): Boolean {
        sharedSecretCache.remove(contactId)
        return deriveSharedSecret(contactId, partnerPubKey64)
    }

    /**
     * Erzwingt die Neu-Ableitung des Web-Shared-Secrets für einen Kontakt.
     * Löscht den alten Cache-Eintrag und leitet mit dem neuen Web-Public-Key neu ab.
     */
    fun forceRederiveWebSharedSecret(contactId: String, webPubKey64: String): Boolean {
        webSharedSecretCache.remove("$contactId::web")
        return deriveWebSharedSecret(contactId, webPubKey64)
    }

    fun clearSecrets() {
        sharedSecretCache.clear()
        webSharedSecretCache.clear()
        conversationKeyCache.clear()
        partnerUmkCache.clear()
        umkBytes = null
    }

    /** Löscht alle Krypto-Caches für einen einzelnen Kontakt (v2 ECDH + v3 UMK). */
    fun clearSecretForContact(contactId: String) {
        sharedSecretCache.remove(contactId)
        webSharedSecretCache.remove("$contactId::web")
        conversationKeyCache.remove(contactId)
        partnerUmkCache.remove(contactId)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Gruppen-E2EE: Sender-Key-Verfahren (AES-256-GCM, symmetrisch)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Erzeugt einen neuen zufälligen AES-256 Sender-Key (32 Byte).
     * Wird einmalig pro Gruppe generiert und per ECDH-verschlüsselten Bundles
     * an alle anderen Mitglieder verteilt.
     */
    fun generateGroupSenderKey(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        return key
    }

    /**
     * Verschlüsselt eine Gruppen-Nachricht mit dem gegebenen AES-256 Sender-Key.
     * Format: "v2:<Base64(12-Byte-IV | AES-256-GCM-Ciphertext+Tag)>"
     * Identisch mit dem 1:1-Nachrichtenformat — CryptoManager.decrypt() kann es auch entschlüsseln.
     *
     * @param keyBytes  Rohe AES-256-Schlüsselbytes (32 Byte)
     * @param plaintext Klartext der Nachricht
     * @return Verschlüsselter String im v2-Format; Klartext bei Fehler (silent fallback)
     */
    fun encryptGroupMessage(keyBytes: ByteArray, plaintext: String): String {
        return try {
            encryptWithSecret(keyBytes, plaintext)
        } catch (_: Exception) {
            plaintext
        }
    }

    /**
     * Entschlüsselt eine Gruppen-Nachricht mit dem gegebenen AES-256 Sender-Key.
     * Wirft Exception wenn der GCM-Auth-Tag ungültig ist (falscher Key / Manipulation).
     *
     * @param keyBytes   Rohe AES-256-Schlüsselbytes (32 Byte)
     * @param ciphertext Verschlüsselter String im v2-Format
     * @return Entschlüsselter Klartext
     */
    fun decryptGroupMessage(keyBytes: ByteArray, ciphertext: String): String {
        if (!ciphertext.startsWith(MSG_PREFIX)) return ciphertext
        return decryptWithSecret(keyBytes, ciphertext)
    }

    /**
     * Verschlüsselt rohe Binärdaten (z.B. eine Sprachnachricht-Datei) mit dem gegebenen
     * AES-256 Sender-Key. Rohes Format ohne Base64/Präfix: 12-Byte-IV | Ciphertext+Tag.
     * Für Medien-Uploads statt der textbasierten [encryptGroupMessage].
     *
     * @param keyBytes  Rohe AES-256-Schlüsselbytes (32 Byte)
     * @param plaintext Rohe Klartext-Bytes (z.B. Inhalt einer .m4a-Datei)
     */
    fun encryptBytesWithSenderKey(keyBytes: ByteArray, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArrayOutputStream().apply { write(iv); write(ciphertext) }.toByteArray()
    }

    /**
     * Entschlüsselt rohe Binärdaten, die mit [encryptBytesWithSenderKey] verschlüsselt wurden.
     * Wirft Exception wenn der GCM-Auth-Tag ungültig ist (falscher Key / Manipulation).
     */
    fun decryptBytesWithSenderKey(keyBytes: ByteArray, ciphertext: ByteArray): ByteArray {
        val iv = ciphertext.copyOfRange(0, GCM_IV_SIZE)
        val data = ciphertext.copyOfRange(GCM_IV_SIZE, ciphertext.size)
        val cipher = Cipher.getInstance(AES_TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(data)
    }

    /**
     * Verschlüsselt den rohen Sender-Key (als Base64-String) für einen Empfänger.
     * Verwendet das bestehende 1:1-ECDH-Shared-Secret mit dem Empfänger.
     * Setzt voraus, dass deriveSharedSecret(recipientId, ...) bereits aufgerufen wurde.
     *
     * @param recipientId ID des Empfängers (Cache-Key für das Shared Secret)
     * @param keyBytes    Die 32 rohen AES-256-Sender-Key-Bytes
     * @return Verschlüsseltes Bundle im v2-Format, oder null wenn kein Shared Secret vorhanden
     */
    fun encryptSenderKeyBundle(recipientId: String, keyBytes: ByteArray): String? {
        val secret = sharedSecretCache[recipientId] ?: return null
        val keyBase64 = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
        return try {
            encryptWithSecret(secret, keyBase64)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Entschlüsselt ein empfangenes Sender-Key-Bundle mit dem ECDH-Shared-Secret des Owners.
     * Setzt voraus, dass deriveSharedSecret(ownerId, ...) bereits aufgerufen wurde.
     *
     * @param ownerId         ID des Key-Generators (wessen Shared Secret zum Entschlüsseln genutzt wird)
     * @param encryptedBundle Verschlüsseltes Bundle im v2-Format
     * @return Rohe AES-256-Schlüsselbytes (32 Byte), oder null bei Fehler / falschem Key
     */
    fun decryptSenderKeyBundle(ownerId: String, encryptedBundle: String): ByteArray? {
        return try {
            val secret = sharedSecretCache[ownerId] ?: return null
            val keyBase64 = decryptWithSecret(secret, encryptedBundle)
            val raw = Base64.decode(keyBase64, Base64.NO_WRAP)
            if (raw.size == 32) raw else null
        } catch (_: Exception) {
            null
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // KEY-SYNC: Multi-Device (Android ↔ WebChat)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Verschlüsselt den eigenen ECDH-Public-Key für den WebChat.
     * Verwendet ECDH(android_priv, web_session_pub) als temporären Kanal.
     *
     * Der WebChat sendet beim Login seinen ephemeren web_session_pub (SPKI/Base64).
     * Die Android-App leitet daraus ein temporäres Secret ab und verschlüsselt damit
     * den eigenen Android-Public-Key + das selfSecret (ECDH mit sich selbst).
     *
     * @param webSessionPub64  SPKI/Base64 des ephemeren Web-Session-Keys
     * @param ownAndroidPub64  Eigener Android ECDH Public Key (SPKI/Base64)
     * @param umkBase64        Optional: Base64 des eigenen UMK (User Master Key), damit der
     *                         WebChat v3-Nachrichten (Conversation Key) entschlüsseln kann.
     *                         Der WebChat hat sonst kein Passwort (QR-Login) um den
     *                         serverseitigen umk_v1-Backup selbst zu entschlüsseln.
     * @return Encrypted Bundle im v2-Format: enthält ownAndroidPub64 (+umk), oder null bei Fehler
     */
    fun encryptKeySyncPayload(webSessionPub64: String, ownAndroidPub64: String, umkBase64: String? = null, groupsJson: String? = null, partnerUmksJson: String? = null): String? {
        return try {
            // Temporäres ECDH-Secret mit dem ephemeren Web-Session-Key ableiten
            val partnerKeyBytes = Base64.decode(webSessionPub64, Base64.NO_WRAP)
            val kf = KeyFactory.getInstance(KEY_ALGO)
            val partnerKey = kf.generatePublic(X509EncodedKeySpec(partnerKeyBytes))

            val ka: KeyAgreement
            val localPrivateKey: PrivateKey

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val softPriv = softKeyPair?.private
                if (softPriv != null) {
                    localPrivateKey = softPriv
                    ka = KeyAgreement.getInstance("ECDH")
                } else {
                    val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                    if (!ks.containsAlias(KEY_ALIAS)) return null
                    localPrivateKey = ks.getKey(KEY_ALIAS, null) as PrivateKey
                    ka = KeyAgreement.getInstance("ECDH", ANDROID_KEYSTORE)
                }
            } else {
                localPrivateKey = softKeyPair?.private ?: return null
                ka = KeyAgreement.getInstance("ECDH")
            }

            ka.init(localPrivateKey)
            ka.doPhase(partnerKey, true)
            val rawSecret = ka.generateSecret()

            val tempSecret = Mac.getInstance("HmacSHA256").apply {
                init(SecretKeySpec(rawSecret, "HmacSHA256"))
            }.doFinal("LETHE_KEY_SYNC_V1".toByteArray(Charsets.UTF_8))

            // Payload: JSON mit android_pub (der WebChat braucht nur den Public Key),
            // optional zusätzlich der rohe UMK für v3-Conversation-Keys sowie die
            // Gruppen-Sender-Keys (der Browser kann die Android-adressierten Bundles
            // nicht selbst entschlüsseln – das Handy liefert die Klartext-Keys mit).
            val root = com.google.gson.JsonObject()
            root.addProperty("android_pub", ownAndroidPub64)
            if (!umkBase64.isNullOrBlank()) root.addProperty("umk", umkBase64)
            if (!groupsJson.isNullOrBlank()) {
                try {
                    root.add("groups", com.google.gson.JsonParser().parse(groupsJson))
                } catch (_: Exception) { /* ungültiges JSON → groups weglassen */ }
            }
            // Alle bekannten Partner-UMKs mitschicken, damit der WebChat die
            // Conversation Keys (v3) für ALLE Kontakte direkt ableiten kann – ohne
            // dass jeder Partner online sein und einen Live-UMK-Exchange beantworten
            // muss. Sonst bleibt die 1:1-Historie (eigene + empfangene v3-Nachrichten)
            // von offline-Kontakten im WebChat unentschlüsselbar. Struktur:
            // {"<partnerId>":"<umkBase64>", …}
            if (!partnerUmksJson.isNullOrBlank()) {
                try {
                    root.add("partner_umks", com.google.gson.JsonParser().parse(partnerUmksJson))
                } catch (_: Exception) { /* ungültiges JSON → partner_umks weglassen */ }
            }
            encryptWithSecret(tempSecret, root.toString())
        } catch (e: Exception) {
            android.util.Log.e("CryptoManager", "encryptKeySyncPayload failed: ${e.message}")
            null
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // KEY-BACKUP: PBKDF2 + AES-256-GCM (Server-seitiger verschlüsselter Backup)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Erstellt ein verschlüsseltes Key-Backup mit dem gegebenen Passwort.
     *
     * Für API 31+ (Hardware-Key): Da Hardware-Keys nicht exportierbar sind,
     * wird automatisch ein Software-Key generiert und als neuer ECDH-Key verwendet.
     * Dieser Wechsel ist einmalig und ermöglicht künftige Wiederherstellung.
     *
     * Format: "kbk_v1:<Base64(salt16 | iv12 | AES-256-GCM(JSON{priv,pub})+tag)>"
     *
     * @return Pair(encryptedBlob, publicKeyBase64) oder null bei Fehler.
     *         publicKeyBase64 ist der öffentliche Key (ggf. neu generiert) für Server-Upload.
     */
    fun encryptKeyForBackup(passphrase: String): Pair<String, String>? {
        return try {
            val kp = getOrCreateSoftKeyPair() ?: return null
            val privB64 = Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP)
            val pubB64  = Base64.encodeToString(kp.public.encoded,  Base64.NO_WRAP)

            val payload = """{"priv":"$privB64","pub":"$pubB64"}""".toByteArray(Charsets.UTF_8)

            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, BACKUP_PBKDF2_ITER, 256)
            val aesKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(keySpec).encoded
            keySpec.clearPassword()

            val iv = ByteArray(GCM_IV_SIZE).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(AES_TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(payload)

            val combined = ByteArrayOutputStream().apply { write(salt); write(iv); write(ciphertext) }.toByteArray()
            val blob = "$BACKUP_PREFIX${Base64.encodeToString(combined, Base64.NO_WRAP)}"
            Pair(blob, pubB64)
        } catch (e: Exception) {
            android.util.Log.e("CryptoManager", "encryptKeyForBackup failed: ${e.message}")
            null
        }
    }

    /**
     * Entschlüsselt einen Key-Backup-Blob und lädt den privaten Key als aktiven Soft-Key.
     * Wirft AEADBadTagException wenn das Passwort falsch ist.
     *
     * @return Public Key Base64 (für Upload zum Server) oder null bei Fehler.
     */
    fun decryptAndLoadKeyBackup(passphrase: String, blob: String): String? {
        return try {
            if (!blob.startsWith(BACKUP_PREFIX)) return null
            val combined = Base64.decode(blob.removePrefix(BACKUP_PREFIX), Base64.NO_WRAP)
            if (combined.size < 28) return null

            val salt       = combined.copyOf(16)
            val iv         = combined.copyOfRange(16, 28)
            val ciphertext = combined.copyOfRange(28, combined.size)

            val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, BACKUP_PBKDF2_ITER, 256)
            val aesKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(keySpec).encoded
            keySpec.clearPassword()

            val cipher = Cipher.getInstance(AES_TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val payload = String(cipher.doFinal(ciphertext), Charsets.UTF_8)

            val privB64 = payload.substringAfter("\"priv\":\"").substringBefore("\"")
            val pubB64  = payload.substringAfter("\"pub\":\"").substringBefore("\"")

            val kf   = KeyFactory.getInstance(KEY_ALGO)
            val priv = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(privB64, Base64.NO_WRAP)))
            val pub  = kf.generatePublic(X509EncodedKeySpec(Base64.decode(pubB64, Base64.NO_WRAP)))
            softKeyPair = java.security.KeyPair(pub, priv)

            pubB64
        } catch (e: Exception) {
            android.util.Log.e("CryptoManager", "decryptAndLoadKeyBackup failed: ${e.message}")
            null
        }
    }

    /**
     * Gibt das vorhandene Soft-Key-Pair zurück, oder erstellt eines (auch auf API 31+).
     * Auf API 31+ wird ein Software-Key generiert, der als softKeyPair gespeichert wird.
     * Der Hardware-Key wird dabei überschrieben (einmaliger Wechsel für Backup-Fähigkeit).
     */
    private fun getOrCreateSoftKeyPair(): java.security.KeyPair? {
        softKeyPair?.let { return it }
        return try {
            val kpg = KeyPairGenerator.getInstance(KEY_ALGO)
            kpg.initialize(ECGenParameterSpec(EC_CURVE))
            val kp = kpg.generateKeyPair()
            softKeyPair = kp
            kp
        } catch (e: Exception) {
            null
        }
    }

    // ��───────────────────────────────���─────────────────────────────────────────
    // UMK (User Master Key) – Universelle Multi-Device Verschlüsselung
    // ──────��───────────────────────────────────────────────────────────────────

    /** Prüft ob ein UMK geladen ist. */
    fun hasUmk(): Boolean = umkBytes != null

    /**
     * Generiert einen neuen zufälligen UMK (32 Byte AES-256).
     * Wird einmalig beim ersten Login nach Update aufgerufen.
     */
    fun generateUmk(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        umkBytes = key
        return key
    }

    /**
     * Setzt den UMK direkt (z.B. nach Entschlüsselung vom Server).
     */
    fun loadUmk(key: ByteArray) {
        require(key.size == 32) { "UMK muss 32 Byte sein" }
        umkBytes = key
    }

    /** Gibt die rohen UMK-Bytes zurück (für Enrollment anderer Geräte). */
    fun getUmkBytes(): ByteArray? = umkBytes?.copyOf()

    /**
     * Verschlüsselt den UMK mit dem Benutzer-Passwort via PBKDF2 + AES-256-GCM.
     * Format: "umk_v1:<Base64(salt16 | iv12 | AES-256-GCM(umk_32_bytes)+tag)>"
     */
    fun encryptUmkWithPassword(password: String): String? {
        val key = umkBytes ?: return null
        return try {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val keySpec = PBEKeySpec(password.toCharArray(), salt, UMK_PBKDF2_ITER, 256)
            val aesKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(keySpec).encoded
            keySpec.clearPassword()

            val iv = ByteArray(GCM_IV_SIZE).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(AES_TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(key)

            val combined = ByteArrayOutputStream().apply { write(salt); write(iv); write(ciphertext) }.toByteArray()
            "$UMK_PREFIX${Base64.encodeToString(combined, Base64.NO_WRAP)}"
        } catch (e: Exception) {
            Timber.tag("LETHE_UMK").e("encryptUmkWithPassword failed: ${e.message}")
            null
        }
    }

    /**
     * Entschlüsselt den UMK-Blob mit dem Benutzer-Passwort.
     * Lädt den UMK in den Speicher.
     * @return true bei Erfolg, false bei falschem Passwort oder Fehler.
     */
    fun decryptUmkWithPassword(password: String, blob: String): Boolean {
        if (!blob.startsWith(UMK_PREFIX)) return false
        return try {
            val combined = Base64.decode(blob.removePrefix(UMK_PREFIX), Base64.NO_WRAP)
            if (combined.size < 28 + 32) return false // salt(16) + iv(12) + min ciphertext

            val salt = combined.copyOf(16)
            val iv = combined.copyOfRange(16, 28)
            val ciphertext = combined.copyOfRange(28, combined.size)

            val keySpec = PBEKeySpec(password.toCharArray(), salt, UMK_PBKDF2_ITER, 256)
            val aesKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(keySpec).encoded
            keySpec.clearPassword()

            val cipher = Cipher.getInstance(AES_TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val umk = cipher.doFinal(ciphertext)

            if (umk.size != 32) return false
            umkBytes = umk
            true
        } catch (e: Exception) {
            Timber.tag("LETHE_UMK").w("decryptUmkWithPassword failed: ${e.message}")
            false
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Conversation Key – abgeleitet aus beiden UMKs
    // ───────────��──────────────────────────────────────────────────────────────

    /**
     * Registriert den UMK eines Chat-Partners (vom Server empfangen, per ECDH entschlüsselt).
     * Leitet sofort den Conversation Key ab und cached ihn.
     */
    fun registerPartnerUmk(partnerId: String, partnerUmk: ByteArray): Boolean {
        if (partnerUmk.size != 32) return false
        val myUmk = umkBytes ?: return false
        partnerUmkCache[partnerId] = partnerUmk
        return deriveConversationKey(partnerId, myUmk, partnerUmk)
    }

    /**
     * Leitet den Conversation Key ab: HMAC-SHA256(UMK_A XOR UMK_B, "LETHE_CONVERSATION_KEY_V1")
     * Der XOR-Ansatz ist kommutativ → beide Seiten berechnen denselben Key.
     */
    private fun deriveConversationKey(partnerId: String, myUmk: ByteArray, partnerUmk: ByteArray): Boolean {
        return try {
            val xorKey = ByteArray(32) { i -> (myUmk[i].toInt() xor partnerUmk[i].toInt()).toByte() }
            val ck = Mac.getInstance("HmacSHA256").apply {
                init(SecretKeySpec(xorKey, "HmacSHA256"))
            }.doFinal(HKDF_INFO_CK.toByteArray(Charsets.UTF_8))
            conversationKeyCache[partnerId] = ck
            true
        } catch (e: Exception) {
            Timber.tag("LETHE_UMK").e("deriveConversationKey failed: ${e.message}")
            false
        }
    }

    /** Prüft ob ein Conversation Key für den Partner vorhanden ist. */
    fun hasConversationKey(partnerId: String): Boolean = conversationKeyCache.containsKey(partnerId)

    /**
     * Exportiert alle abgeleiteten Conversation Keys (partnerId → Base64-AES-256).
     * Für persistente Speicherung, damit v3 nach App-Neustart ohne erneuten WS-Austausch verfügbar ist.
     */
    fun exportConversationKeys(): Map<String, String> =
        conversationKeyCache.mapValues { Base64.encodeToString(it.value, Base64.NO_WRAP) }

    /**
     * Lädt persistierte Conversation Keys (partnerId → Base64) in den Cache.
     * Vorhandene (frisch ausgetauschte) Einträge werden NICHT überschrieben.
     */
    fun importConversationKeys(keys: Map<String, String>) {
        keys.forEach { (id, b64) ->
            if (conversationKeyCache.containsKey(id)) return@forEach
            try {
                val bytes = Base64.decode(b64, Base64.NO_WRAP)
                if (bytes.size == 32) conversationKeyCache[id] = bytes
            } catch (_: Exception) {}
        }
    }

    /** Prüft ob der UMK eines Partners bekannt ist. */
    fun hasPartnerUmk(partnerId: String): Boolean = partnerUmkCache.containsKey(partnerId)

    /**
     * Exportiert alle bekannten Partner-UMKs (partnerId → Base64-32-Byte).
     * Für persistente Speicherung, damit Conversation Keys nach App-Neustart ohne
     * erneuten WS-Austausch wieder abgeleitet werden können.
     */
    fun exportPartnerUmks(): Map<String, String> =
        partnerUmkCache.mapValues { Base64.encodeToString(it.value, Base64.NO_WRAP) }

    /**
     * Lädt persistierte Partner-UMKs (partnerId → Base64) in den Cache und leitet,
     * falls der eigene UMK bereits vorhanden ist, fehlende Conversation Keys neu ab.
     * Vorhandene (frisch ausgetauschte) Einträge werden NICHT überschrieben.
     */
    fun importPartnerUmks(umks: Map<String, String>) {
        val myUmk = umkBytes
        umks.forEach { (id, b64) ->
            try {
                val bytes = partnerUmkCache[id] ?: Base64.decode(b64, Base64.NO_WRAP).also {
                    if (it.size == 32) partnerUmkCache[id] = it
                }
                if (bytes.size != 32) return@forEach
                // Conversation Key ableiten, sobald der eigene UMK vorhanden ist
                if (myUmk != null && !conversationKeyCache.containsKey(id)) {
                    deriveConversationKey(id, myUmk, bytes)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Verschlüsselt mit dem Conversation Key (UMK-basiert).
     * Format: "v3:<Base64(12-Byte-IV | AES-256-GCM(plaintext)+Tag)>"
     * Gibt null zurück wenn kein Conversation Key vorhanden.
     */
    fun encryptWithConversationKey(partnerId: String, plaintext: String): String? {
        val ck = conversationKeyCache[partnerId] ?: return null
        return try {
            val iv = ByteArray(GCM_IV_SIZE).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(AES_TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(ck, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArrayOutputStream().apply { write(iv); write(ciphertext) }.toByteArray()
            "$MSG_PREFIX_V3${Base64.encodeToString(combined, Base64.NO_WRAP)}"
        } catch (e: Exception) {
            Timber.tag("LETHE_UMK").e("encryptWithConversationKey failed: ${e.message}")
            null
        }
    }

    /**
     * Entschlüsselt eine v3-Nachricht mit dem Conversation Key.
     */
    fun decryptWithConversationKey(partnerId: String, ciphertext: String): String? {
        if (!ciphertext.startsWith(MSG_PREFIX_V3)) return null
        val ck = conversationKeyCache[partnerId] ?: return null
        return try {
            val combined = Base64.decode(ciphertext.removePrefix(MSG_PREFIX_V3), Base64.NO_WRAP)
            val iv = combined.copyOf(GCM_IV_SIZE)
            val data = combined.copyOfRange(GCM_IV_SIZE, combined.size)
            val cipher = Cipher.getInstance(AES_TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(ck, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Verschlüsselt rohe Binärdaten (z.B. eine Sprachnachricht-Datei) mit dem 1:1-Conversation-Key.
     * Rohes Format ohne Base64/Präfix: 12-Byte-IV | Ciphertext+Tag.
     * Gibt null zurück wenn kein Conversation Key für den Partner vorhanden ist.
     */
    fun encryptBytesWithConversationKey(partnerId: String, plaintext: ByteArray): ByteArray? {
        val ck = conversationKeyCache[partnerId] ?: return null
        return try {
            val iv = ByteArray(GCM_IV_SIZE).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(AES_TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(ck, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(plaintext)
            ByteArrayOutputStream().apply { write(iv); write(ciphertext) }.toByteArray()
        } catch (e: Exception) {
            Timber.tag("LETHE_UMK").e("encryptBytesWithConversationKey failed: ${e.message}")
            null
        }
    }

    /**
     * Entschlüsselt rohe Binärdaten, die mit [encryptBytesWithConversationKey] verschlüsselt wurden.
     */
    fun decryptBytesWithConversationKey(partnerId: String, ciphertext: ByteArray): ByteArray? {
        val ck = conversationKeyCache[partnerId] ?: return null
        return try {
            val iv = ciphertext.copyOfRange(0, GCM_IV_SIZE)
            val data = ciphertext.copyOfRange(GCM_IV_SIZE, ciphertext.size)
            val cipher = Cipher.getInstance(AES_TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(ck, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(data)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Universelle Entschlüsselung: v3 (UMK) → v2 (Legacy ECDH) → Klartext.
     * Versucht zuerst den neuen Conversation Key, dann Legacy-Fallback.
     */
    fun decryptUniversal(contactId: String, ciphertext: String): String {
        // v3: UMK-basierter Conversation Key
        if (ciphertext.startsWith(MSG_PREFIX_V3)) {
            decryptWithConversationKey(contactId, ciphertext)?.let { return it }
            return "[🔐 Verschlüsselte Nachricht – Conversation Key nicht verfügbar]"
        }
        // v2: Legacy ECDH (Rückwärtskompatibilität)
        if (ciphertext.startsWith(MSG_PREFIX)) {
            return decrypt(contactId, ciphertext)
        }
        // Klartext
        return ciphertext
    }

    /**
     * Verschlüsselt eine Nachricht bevorzugt mit UMK (v3), Fallback auf Legacy ECDH (v2).
     * Gibt immer einen verschlüsselten String zurück (oder Klartext wenn gar kein Key vorhanden).
     */
    fun encryptUniversal(contactId: String, plaintext: String): String {
        // Bevorzugt: UMK-basierter Conversation Key (v3)
        encryptWithConversationKey(contactId, plaintext)?.let { return it }
        // Fallback: Legacy ECDH (v2)
        return encrypt(contactId, plaintext)
    }

    /**
     * Exportiert den UMK als Base64-String (für Device-Enrollment via ECDH-Wrapping).
     */
    fun exportUmkBase64(): String? {
        val key = umkBytes ?: return null
        return Base64.encodeToString(key, Base64.NO_WRAP)
    }

    /**
     * Importiert einen UMK aus Base64 (nach Device-Enrollment).
     */
    fun importUmkFromBase64(base64: String): Boolean {
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            if (bytes.size != 32) return false
            umkBytes = bytes
            true
        } catch (e: Exception) {
            false
        }
    }
}
