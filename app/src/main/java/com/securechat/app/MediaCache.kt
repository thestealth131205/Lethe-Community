package com.securechat.app

import android.content.Context
import com.securechat.app.data.local.ProfileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lokaler Medien-Cache für Lethe.
 *
 * Lädt Dateien von einer URL herunter und speichert sie dauerhaft im internen
 * App-Speicher (filesDir/media_cache/<profil>/<subDir>/) – pro aktivem Profil
 * (Account) isoliert, damit Medien verschiedener Accounts auf demselben Gerät
 * nicht vermischt werden. Nachfolgende Aufrufe mit derselben URL liefern sofort
 * die gecachte Datei zurück, ohne Netzwerk-Anfrage.
 *
 * Empfohlene subDir-Werte:
 *   "images"   – Profilbilder, Chat-Bilder
 *   "audio"    – Sprachnachrichten, Musik-Anhänge
 *   "video"    – Video-Nachrichten
 *   "3d"       – 3D-Dateien (.stl, .obj, .3mf)
 *   "status"   – Status-Fotos und -Videos (kurze TTL empfohlen)
 *   "docs"     – PDF, ZIP, sonstige Dokumente
 */
@Singleton
class MediaCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    /** Sanitizter Profil-Ordnername des aktuell aktiven Accounts. */
    private fun profileFolder(): String =
        ProfileManager.getActiveProfile(context).ifBlank { "default" }

    private val cacheRoot: File
        get() = File(context.filesDir, "media_cache/${profileFolder()}").also { it.mkdirs() }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Gibt die gecachte [File] für [url] zurück.
     * Falls nicht vorhanden, wird die Datei heruntergeladen und gecacht.
     *
     * @param url        Vollständige Download-URL
     * @param subDir     Unterordner (z. B. "images", "audio", "3d", "status")
     * @param extension  Dateiendung – wird aus [url] abgeleitet wenn null
     * @return           Lokale Datei oder null bei Fehler
     */
    suspend fun get(
        url: String,
        subDir: String = "misc",
        extension: String? = null
    ): File? = withContext(Dispatchers.IO) {
        val file = cacheFileFor(url, subDir, extension)
        if (file.exists() && file.length() > 0) return@withContext file
        download(url, file)
    }

    /**
     * Gibt den lokalen Dateipfad zurück, falls [url] bereits gecacht ist.
     * Kein Netzwerk-Zugriff.
     */
    fun getCachedPath(
        url: String,
        subDir: String = "misc",
        extension: String? = null
    ): String? {
        val file = cacheFileFor(url, subDir, extension)
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    /** Gibt zurück ob [url] bereits gecacht ist, ohne etwas herunterzuladen. */
    fun isCached(url: String, subDir: String = "misc", extension: String? = null): Boolean =
        getCachedPath(url, subDir, extension) != null

    /**
     * Löscht gecachte Dateien.
     * @param subDir  Nur diesen Unterordner löschen; null = gesamten Cache löschen
     */
    fun clearCache(subDir: String? = null) {
        val target = if (subDir != null) File(cacheRoot, subDir) else cacheRoot
        target.deleteRecursively()
        Timber.tag("LETHE_CACHE").d("Cache gelöscht: ${target.path}")
    }

    /**
     * Lädt [url] in ein chat-spezifisches Unterverzeichnis herunter.
     * Beim Verlassen des Chats kann [clearChatCache] aufgerufen werden,
     * um genau diese Dateien wieder zu löschen.
     *
     * @param url     Vollständige Download-URL
     * @param chatId  Chat-Kennung (wird als Ordnername verwendet)
     * @param subDir  Medientyp-Unterordner, z. B. "images", "audio", "video", "3d"
     */
    suspend fun getForChat(
        url: String,
        chatId: String,
        subDir: String,
        extension: String? = null
    ): File? = withContext(Dispatchers.IO) {
        val dir = chatSubDir(chatId, subDir)
        val ext = extension
            ?: url.substringAfterLast('.', "").take(5).lowercase().ifBlank { "bin" }
        val file = File(dir, "${url.hashCode().toLong() and 0xFFFFFFFFL}.$ext")
        if (file.exists() && file.length() > 0) return@withContext file
        download(url, file)
    }

    /** Prüft ob [url] bereits im chat-spezifischen Cache vorliegt (kein Netzwerk-Zugriff). */
    fun isCachedForChat(
        url: String,
        chatId: String,
        subDir: String,
        extension: String? = null
    ): Boolean {
        val dir = chatSubDir(chatId, subDir)
        val ext = extension
            ?: url.substringAfterLast('.', "").take(5).lowercase().ifBlank { "bin" }
        val file = File(dir, "${url.hashCode().toLong() and 0xFFFFFFFFL}.$ext")
        return file.exists() && file.length() > 0
    }

    /** Gibt den lokalen Dateipfad zurück, falls [url] im chat-spezifischen Cache vorliegt (kein Netzwerk-Zugriff). */
    fun getCachedPathForChat(
        url: String,
        chatId: String,
        subDir: String,
        extension: String? = null
    ): String? {
        val dir = chatSubDir(chatId, subDir)
        val ext = extension
            ?: url.substringAfterLast('.', "").take(5).lowercase().ifBlank { "bin" }
        val file = File(dir, "${url.hashCode().toLong() and 0xFFFFFFFFL}.$ext")
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    /**
     * Lädt eine Ende-zu-Ende-verschlüsselte Datei herunter, entschlüsselt sie via [decrypt]
     * und cached ausschließlich das Klartext-Ergebnis chat-spezifisch (in "<subDir>_dec").
     * Der heruntergeladene Ciphertext-Zwischenstand ("<subDir>_enc") wird danach gelöscht.
     * Bei bereits vorhandenem Klartext-Cache-Eintrag erfolgt kein erneuter Download/keine
     * erneute Entschlüsselung.
     *
     * @param decrypt  liefert die Klartext-Bytes oder null bei Entschlüsselungsfehler
     *                 (z. B. fehlender Schlüssel) – dann wird nichts gecacht.
     */
    suspend fun getDecryptedForChat(
        url: String,
        chatId: String,
        subDir: String,
        extension: String,
        decrypt: suspend (ByteArray) -> ByteArray?
    ): File? = withContext(Dispatchers.IO) {
        val outDir = chatSubDir(chatId, "${subDir}_dec")
        val outFile = File(outDir, "${url.hashCode().toLong() and 0xFFFFFFFFL}.$extension")
        if (outFile.exists() && outFile.length() > 0) return@withContext outFile
        val encFile = getForChat(url, chatId, "${subDir}_enc", "bin") ?: return@withContext null
        val plain = try {
            decrypt(encFile.readBytes())
        } catch (e: Exception) {
            Timber.tag("LETHE_CACHE").e(e, "Entschlüsselung fehlgeschlagen: $url")
            null
        }
        encFile.delete()
        if (plain == null) return@withContext null
        outFile.writeBytes(plain)
        outFile
    }

    /**
     * Löscht alle gecachten Medien-Dateien für [chatId].
     * Sollte aufgerufen werden, wenn der Nutzer den Chat verlässt.
     */
    fun clearChatCache(chatId: String) {
        val target = File(cacheRoot, "chat_${chatId.replace(Regex("[^a-zA-Z0-9_-]"), "_")}")
        if (target.exists()) {
            target.deleteRecursively()
            Timber.tag("LETHE_CACHE").d("Chat-Cache gelöscht: ${target.path}")
        }
    }

    private fun chatSubDir(chatId: String, subDir: String): File {
        val safeChatId = chatId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(cacheRoot, "chat_$safeChatId/$subDir").also { it.mkdirs() }
    }

    // -------------------------------------------------------------------------
    // Öffentlicher Galerie-Speicher (Movies/Pictures)
    //
    // Videos und Bilder werden dauerhaft in die öffentlichen Ordner
    // Movies/Lethe bzw. Pictures/Lethe verschoben, sodass sie in der Galerie
    // sichtbar sind und beim erneuten Abspielen/Betrachten direkt von dort
    // (lokal, ohne Datenvolumen) geladen werden.
    // -------------------------------------------------------------------------

    private fun publicRelativePath(isVideo: Boolean): String =
        if (isVideo) "${android.os.Environment.DIRECTORY_MOVIES}/Lethe/${profileFolder()}"
        else "${android.os.Environment.DIRECTORY_PICTURES}/Lethe/${profileFolder()}"

    /** Stabiler Anzeigename auf Basis des URL-Hashes (analog zum internen Cache). */
    private fun publicDisplayName(url: String, isVideo: Boolean): String {
        val id = url.hashCode().toLong() and 0xFFFFFFFFL
        return "lethe_$id." + if (isVideo) "mp4" else "jpg"
    }

    /**
     * Gibt die öffentliche URI ([content]:// ab API 29, sonst [file]://) zurück,
     * falls das Medium bereits im Galerie-Speicher liegt – sonst null.
     * Kein Netzwerk-Zugriff.
     */
    fun findPublicUri(url: String, isVideo: Boolean): android.net.Uri? {
        if (url.isBlank()) return null
        val displayName = publicDisplayName(url, isVideo)
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val collection = if (isVideo)
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(android.provider.MediaStore.MediaColumns._ID)
                val selection =
                    "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME}=? AND " +
                    "${android.provider.MediaStore.MediaColumns.RELATIVE_PATH}=?"
                val args = arrayOf(displayName, publicRelativePath(isVideo) + "/")
                context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(0)
                        return android.content.ContentUris.withAppendedId(collection, id)
                    }
                }
                null
            } else {
                val type = if (isVideo) android.os.Environment.DIRECTORY_MOVIES
                else android.os.Environment.DIRECTORY_PICTURES
                @Suppress("DEPRECATION")
                val dir = File(android.os.Environment.getExternalStoragePublicDirectory(type), "Lethe/${profileFolder()}")
                val f = File(dir, displayName)
                if (f.exists() && f.length() > 0) android.net.Uri.fromFile(f) else null
            }
        } catch (e: Exception) {
            Timber.tag("LETHE_CACHE").w(e, "findPublicUri fehlgeschlagen: $url")
            null
        }
    }

    /** true, wenn das Medium bereits im öffentlichen Galerie-Speicher liegt. */
    fun isInPublicStore(url: String, isVideo: Boolean): Boolean = findPublicUri(url, isVideo) != null

    /**
     * Verschiebt [source] in den öffentlichen Ordner Movies/Lethe (Video) bzw.
     * Pictures/Lethe (Bild). Die Quelldatei wird nach erfolgreichem Kopieren gelöscht.
     *
     * @return Öffentliche URI oder null bei Fehler (z. B. fehlende Schreibrechte < API 29).
     */
    suspend fun moveToPublic(source: File, url: String, isVideo: Boolean): android.net.Uri? =
        withContext(Dispatchers.IO) {
            if (!source.exists() || source.length() == 0L) return@withContext null
            // Bereits vorhanden → Quelle entfernen und vorhandene URI liefern.
            findPublicUri(url, isVideo)?.let { existing ->
                source.delete()
                return@withContext existing
            }
            val displayName = publicDisplayName(url, isVideo)
            val mime = if (isVideo) "video/mp4" else "image/jpeg"
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val collection = if (isVideo)
                        android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, publicRelativePath(isVideo))
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val uri = context.contentResolver.insert(collection, values)
                        ?: return@withContext null
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        source.inputStream().use { it.copyTo(out) }
                    } ?: run {
                        context.contentResolver.delete(uri, null, null)
                        return@withContext null
                    }
                    values.clear()
                    values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    source.delete()
                    Timber.tag("LETHE_CACHE").d("In Galerie verschoben: $displayName")
                    uri
                } else {
                    // API < 29: WRITE_EXTERNAL_STORAGE nötig. Ohne Recht: Cache behalten.
                    val granted = context.checkSelfPermission(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!granted) return@withContext null
                    val type = if (isVideo) android.os.Environment.DIRECTORY_MOVIES
                    else android.os.Environment.DIRECTORY_PICTURES
                    @Suppress("DEPRECATION")
                    val dir = File(android.os.Environment.getExternalStoragePublicDirectory(type), "Lethe/${profileFolder()}")
                        .also { it.mkdirs() }
                    val dest = File(dir, displayName)
                    source.inputStream().use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    android.media.MediaScannerConnection.scanFile(
                        context, arrayOf(dest.absolutePath), arrayOf(mime), null
                    )
                    source.delete()
                    Timber.tag("LETHE_CACHE").d("In Galerie verschoben: ${dest.path}")
                    android.net.Uri.fromFile(dest)
                }
            } catch (e: Exception) {
                Timber.tag("LETHE_CACHE").e(e, "moveToPublic fehlgeschlagen: $url")
                null
            }
        }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun cacheFileFor(url: String, subDir: String, extension: String?): File {
        val ext = extension
            ?: url.substringAfterLast('.', "").take(5).lowercase().ifBlank { "bin" }
        // Stabiler Dateiname auf Basis des URL-Hashes (unsigned long)
        val name = "${url.hashCode().toLong() and 0xFFFFFFFFL}.$ext"
        return File(File(cacheRoot, subDir).also { it.mkdirs() }, name)
    }

    private fun download(url: String, target: File): File? {
        return try {
            val response = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) {
                Timber.tag("LETHE_CACHE").w("Download fehlgeschlagen (${response.code}): $url")
                return null
            }
            response.body?.byteStream()?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            Timber.tag("LETHE_CACHE").d("Gecacht: ${target.name} (${target.length()} Bytes)")
            target
        } catch (e: Exception) {
            Timber.tag("LETHE_CACHE").e(e, "Download-Fehler: $url")
            target.takeIf { it.exists() }?.delete()
            null
        }
    }
}
