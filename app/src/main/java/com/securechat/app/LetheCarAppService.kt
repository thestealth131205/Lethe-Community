package com.securechat.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import coil.ImageLoader
import coil.request.ImageRequest
import com.securechat.app.data.local.ContactDao
import com.securechat.app.data.local.ContactEntity
import com.securechat.app.data.local.MessageDao
import com.securechat.app.data.local.MessageEntity
import com.securechat.app.data.network.WebSocketManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID

/**
 * Hilt EntryPoint für den Datenbankzugriff aus dem CarAppService heraus.
 * Da CarAppService kein @AndroidEntryPoint unterstützt, wird EntryPointAccessors genutzt.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CarAppEntryPoint {
    fun contactDao(): ContactDao
    fun messageDao(): MessageDao
    fun webSocketManager(): WebSocketManager
}

/**
 * Android Auto CarAppService für Lethe Messaging.
 *
 * Registriert im Manifest mit der Kategorie [androidx.car.app.category.MESSAGING].
 * Stellt eine Konversationsliste bereit, die:
 *  - Kontakt-Avatare als CarIcon (Row.IMAGE_TYPE_ICON) anzeigt
 *  - Bei Bild-Nachrichten ein Vorschau-Thumbnail (Row.IMAGE_TYPE_LARGE) lädt
 *  - Sprachnachrichten mit AA-freundlichem Hinweistext ("🎤 Sprachnachricht") darstellt
 *  - Tippen auf eine Konversation öffnet den Chat direkt in der Lethe-MainActivity
 */
class LetheCarAppService : CarAppService() {

    /**
     * Erlaubt alle Android-Auto-Hosts (entwicklungsfreundlich).
     * Für Produktion: HostValidator.Builder(applicationContext).addAllowedHosts(...).build()
     */
    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            CarAppEntryPoint::class.java
        )
        return LetheCarSession(
            contactDao = entryPoint.contactDao(),
            messageDao = entryPoint.messageDao(),
            webSocketManager = entryPoint.webSocketManager()
        )
    }
}

/**
 * Car App Session – Einstiegspunkt für die Android-Auto-UI.
 * Delegiert die Screen-Erstellung an [ConversationListScreen].
 */
class LetheCarSession(
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
    private val webSocketManager: WebSocketManager
) : Session() {
    override fun onCreateScreen(intent: Intent): Screen =
        ConversationListScreen(carContext, contactDao, messageDao, webSocketManager)
}

/**
 * Android-Auto-Screen: zeigt die letzten Lethe-Konversationen als [ListTemplate].
 *
 * Jede Zeile enthält:
 *  - Titel: Kontaktname (customAlias → username → fakeNumber)
 *  - Untertitel: Vorschautext der letzten Nachricht (AA-optimiert für Audio/Bild/Video)
 *  - Icon:  Kontakt-Avatar (IMAGE_TYPE_ICON) oder Bild-Thumbnail (IMAGE_TYPE_LARGE)
 *
 * Avatare und Thumbnails werden asynchron via Coil geladen; nach dem Laden
 * wird [invalidate] aufgerufen um die Anzeige zu aktualisieren.
 */
class ConversationListScreen(
    carContext: CarContext,
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
    private val webSocketManager: WebSocketManager
) : Screen(carContext) {

    /** Avatar-Bitmaps gecacht nach chatId */
    private val avatarCache = mutableMapOf<String, Bitmap>()

    /** Thumbnail-Bitmaps für Bild-Nachrichten gecacht nach chatId */
    private val thumbnailCache = mutableMapOf<String, Bitmap>()

    /** Sortierte Liste der Chat-IDs (neueste Konversation zuerst) */
    private var chatIds: List<String> = emptyList()

    /** Kontaktdaten gecacht nach userId */
    private val contactsMap = mutableMapOf<String, ContactEntity>()

    /** Letzte Nachricht gecacht nach chatId */
    private val lastMsgMap = mutableMapOf<String, MessageEntity>()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        // Scope beim Zerstören des Screens abbrechen um Ressourcen-Leaks zu vermeiden
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
        loadConversations()
    }

    /**
     * Lädt die Konversationsliste aus Room (neueste zuerst), dann Kontaktdaten und letzte
     * Nachrichten. Startet anschließend das asynchrone Laden von Avataren und Thumbnails.
     */
    private fun loadConversations() {
        scope.launch {
            try {
                val ids = withContext(Dispatchers.IO) {
                    messageDao.getChatIdsSortedByRecent().first().take(MAX_CONVERSATIONS)
                }
                chatIds = ids

                withContext(Dispatchers.IO) {
                    ids.forEach { chatId ->
                        contactDao.getContactById(chatId)?.let { contactsMap[chatId] = it }
                        messageDao.getLastMessageForChat(chatId).first()?.let { lastMsgMap[chatId] = it }
                    }
                }

                // Erstes Render ohne Bilder, dann Bilder nachladen
                invalidate()
                loadImagesAsync(ids)

            } catch (e: Exception) {
                Timber.tag("LETHE_CAR").e(e, "Konversationsliste laden fehlgeschlagen")
                invalidate()
            }
        }
    }

    /**
     * Lädt Kontakt-Avatare (128 px) und Nachrichten-Thumbnails (256 px) im Hintergrund.
     * Ruft [invalidate] auf sobald mindestens ein Bild neu geladen wurde.
     */
    private fun loadImagesAsync(ids: List<String>) {
        scope.launch(Dispatchers.IO) {
            var anyNewImage = false
            ids.forEach { chatId ->
                val contact = contactsMap[chatId]
                val lastMsg = lastMsgMap[chatId]

                // Avatar laden (128×128 px reicht für Icon-Darstellung)
                if (!avatarCache.containsKey(chatId) && !contact?.profileImageUrl.isNullOrBlank()) {
                    loadBitmapFromUrl(contact!!.profileImageUrl!!, 128)?.let {
                        avatarCache[chatId] = it
                        anyNewImage = true
                    }
                }

                // Bild-Thumbnail laden (256×256 px für IMAGE_TYPE_LARGE)
                if (!thumbnailCache.containsKey(chatId)
                    && lastMsg?.mediaType == "image"
                    && !lastMsg.mediaUrl.isNullOrBlank()
                ) {
                    loadBitmapFromUrl(lastMsg.mediaUrl!!, 256)?.let {
                        thumbnailCache[chatId] = it
                        anyNewImage = true
                    }
                }
            }
            if (anyNewImage) {
                withContext(Dispatchers.Main) { invalidate() }
            }
        }
    }

    /**
     * Lädt eine Bitmap aus einer HTTP/HTTPS-URL via Coil (IO-Dispatcher).
     * Gibt null zurück wenn das Laden fehlschlägt (Netzwerkfehler, ungültige URL, etc.).
     */
    private suspend fun loadBitmapFromUrl(url: String, size: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(carContext)
                val request = ImageRequest.Builder(carContext)
                    .data(url)
                    .allowHardware(false)
                    .size(size, size)
                    .build()
                (loader.execute(request).drawable as? BitmapDrawable)?.bitmap
            } catch (e: Exception) {
                Timber.tag("LETHE_CAR").w(e, "Bitmap konnte nicht geladen werden: $url")
                null
            }
        }

    /**
     * Konvertiert ein Android [Bitmap] in ein für Android Auto verwendbares [CarIcon].
     * Intern wird [IconCompat.createWithBitmap] genutzt.
     */
    private fun bitmapToCarIcon(bitmap: Bitmap): CarIcon =
        CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()

    /**
     * Erstellt den AA-lesbaren Vorschautext für die letzte Nachricht einer Konversation.
     *
     * Sprachnachrichten erhalten einen expliziten Hinweis statt rohem Dateinamen oder
     * Chiffretext. Android Auto liest diesen Text per TTS vor.
     */
    private fun formatPreview(msg: MessageEntity): String = when (msg.mediaType) {
        "audio"    -> "\uD83C\uDFA4 Sprachnachricht – Tippe zum Abspielen in Lethe"
        "image"    -> "\uD83D\uDCF7 Bild"
        "video"    -> "\uD83C\uDFA5 Video"
        "document" -> "\uD83D\uDCCE Dokument"
        "poll"     -> "\uD83D\uDCCA Umfrage"
        else       -> msg.content?.take(80) ?: "Neue Nachricht"
    }

    /**
     * Erzeugt das [ListTemplate] für Android Auto.
     *
     * Für jede Konversation wird eine [Row] erstellt:
     *  - Titel: Kontaktname
     *  - Untertitel: Vorschautext der letzten Nachricht (Audio/Bild/Video AA-optimiert)
     *  - Icon: Bei Bild-Nachrichten IMAGE_TYPE_LARGE mit Thumbnail; sonst IMAGE_TYPE_ICON
     *    mit Kontakt-Avatar (Fallback: App-Icon ic_notification)
     *  - OnClickListener: Öffnet die MainActivity mit chat_id-Extra
     */
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        if (chatIds.isEmpty()) {
            listBuilder.setNoItemsMessage("Keine Konversationen vorhanden")
        } else {
            chatIds.forEach { chatId ->
                val contact = contactsMap[chatId]
                val contactName = contact?.customAlias
                    ?: contact?.username
                    ?: contact?.fakeNumber
                    ?: "Unbekannt"
                val lastMsg = lastMsgMap[chatId]
                val preview = lastMsg?.let { formatPreview(it) } ?: "Lethe"

                val rowBuilder = Row.Builder()
                    .setTitle(contactName)
                    .addText(preview)
                    .setOnClickListener {
                        // Zum ComposeScreen navigieren für Spracheingabe / Diktat
                        screenManager.push(
                            ComposeScreen(carContext, chatId, contactName, webSocketManager)
                        )
                    }

                // Bild-Thumbnail hat Vorrang (IMAGE_TYPE_LARGE zeigt größeres Vorschaubild)
                val thumbBitmap = thumbnailCache[chatId]
                if (thumbBitmap != null && lastMsg?.mediaType == "image") {
                    rowBuilder.setImage(bitmapToCarIcon(thumbBitmap), Row.IMAGE_TYPE_LARGE)
                } else {
                    // Kontakt-Avatar oder App-Icon als Fallback
                    val avatarBitmap = avatarCache[chatId]
                    if (avatarBitmap != null) {
                        rowBuilder.setImage(bitmapToCarIcon(avatarBitmap), Row.IMAGE_TYPE_ICON)
                    } else {
                        rowBuilder.setImage(
                            CarIcon.Builder(
                                IconCompat.createWithResource(carContext, R.drawable.ic_notification)
                            ).build(),
                            Row.IMAGE_TYPE_ICON
                        )
                    }
                }

                listBuilder.addItem(rowBuilder.build())
            }
        }

        return ListTemplate.Builder()
            .setTitle("Lethe – Chats")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .build()
    }

    companion object {
        /** Maximale Anzahl Konversationen in der Android-Auto-Ansicht */
        private const val MAX_CONVERSATIONS = 10
    }
}

/**
 * Android-Auto-Screen: Spracheingabe / Diktat für eine Konversation.
 *
 * Öffnet ein [InputTemplate] mit Mikrofon-Prompt. Gibt der Nutzer einen Text ein
 * (via Sprache oder Tastatur), wird er direkt als Klartextnachricht über den
 * WebSocket geschickt (unverschlüsselt – Auto-Kontext, kein E2EE).
 *
 * Nach dem Absenden kehrt die App automatisch zur Konversationsliste zurück.
 */
class ComposeScreen(
    carContext: CarContext,
    private val contactId: String,
    private val contactName: String,
    private val webSocketManager: WebSocketManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val callback = object : SearchTemplate.SearchCallback {
            override fun onSearchSubmitted(searchText: String) {
                val trimmed = searchText.trim()
                if (trimmed.isNotEmpty()) {
                    webSocketManager.sendMessage(
                        type = "message",
                        receiverId = contactId,
                        payload = mapOf(
                            "content_blob" to trimmed,
                            "media_type" to "text",
                            "is_encrypted" to false,
                            "client_message_id" to UUID.randomUUID().toString(),
                            "sender_timestamp" to System.currentTimeMillis()
                        )
                    )
                }
                screenManager.pop()
            }

            override fun onSearchTextChanged(searchText: String) {
                // Kein Live-Feedback nötig
            }
        }

        return SearchTemplate.Builder(callback)
            .setHeaderAction(Action.BACK)
            .setSearchHint("An $contactName: Nachricht sprechen…")
            .build()
    }
}
