package com.securechat.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil.ImageLoader
import coil.request.ImageRequest
import com.securechat.app.data.local.ContactEntity
import com.securechat.app.data.local.GroupEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verwaltet Dynamic Shortcuts für Direct Share (Android 10+) und Google Assistant.
 *
 * Die Top-5-Kontakte (sortiert nach jüngstem Nachrichtenaustausch) erscheinen direkt
 * im System-Share-Sheet. Jeder Shortcut hat die Kategorie MESSAGING_CONTACT, die
 * in shortcuts.xml als <share-target> registriert ist.
 *
 * setShortcutId() in NotificationHelper verknüpft Benachrichtigungen mit diesen
 * Shortcuts, damit Android das Ranking automatisch anpasst.
 */
@Singleton
class ShortcutHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CATEGORY_SHARE = "com.securechat.app.share.MESSAGING_CONTACT"
        private const val MAX_SHORTCUTS = 5
    }

    /**
     * Aktualisiert die Dynamic Shortcuts basierend auf [topContacts] und [topGroups].
     * [recentChatIds] bestimmt die gemeinsame Reihenfolge (aktuellster zuerst).
     * Beide Listen werden anhand ihrer chatId in [recentChatIds] einsortiert und
     * die Top-[MAX_SHORTCUTS] Einträge werden als Shortcuts registriert.
     */
    suspend fun refreshTopContactShortcuts(
        topContacts: List<ContactEntity>,
        topGroups: List<GroupEntity> = emptyList(),
        recentChatIds: List<String> = emptyList()
    ) {
        // Gemeinsame Rangliste: Wenn recentChatIds bekannt, danach sortieren, sonst Contacts first.
        val contactMap = topContacts.associateBy { it.userId }
        val groupMap = topGroups.associateBy { it.groupId }

        data class Entry(val id: String, val rank: Int)

        val combined: List<Entry> = if (recentChatIds.isNotEmpty()) {
            recentChatIds.mapIndexed { i, id -> Entry(id, i) }
                .filter { it.id in contactMap || it.id in groupMap }
        } else {
            topContacts.mapIndexed { i, c -> Entry(c.userId, i) } +
                topGroups.mapIndexed { i, g -> Entry(g.groupId, topContacts.size + i) }
        }

        val top = combined.sortedBy { it.rank }.take(MAX_SHORTCUTS)
        if (top.isEmpty()) return

        val shortcuts = top.mapIndexedNotNull { index, entry ->
            val contact = contactMap[entry.id]
            val group = groupMap[entry.id]
            when {
                contact != null -> buildContactShortcut(contact, index)
                group != null -> buildGroupShortcut(group, index)
                else -> null
            }
        }

        withContext(Dispatchers.IO) {
            try {
                val newIds = shortcuts.map { it.id }.toSet()
                // Shortcuts die nicht mehr gültig sind (gelöschte Kontakte/Gruppen) deaktivieren,
                // damit sie sofort aus dem Share Sheet verschwinden (long-lived Shortcuts bleiben
                // sonst trotz removeAll im System-Share-Ranking sichtbar).
                val existing = ShortcutManagerCompat.getShortcuts(
                    context, ShortcutManagerCompat.FLAG_MATCH_DYNAMIC
                ).map { it.id }.toSet()
                val toDisable = (existing - newIds).toList()
                if (toDisable.isNotEmpty()) {
                    try { ShortcutManagerCompat.disableShortcuts(context, toDisable, null) } catch (_: Exception) {}
                }
                // Shortcuts die evtl. vorher deaktiviert wurden (z.B. bei Löschung+Wiederherstellung) re-enablen
                if (shortcuts.isNotEmpty()) {
                    try { ShortcutManagerCompat.enableShortcuts(context, shortcuts) } catch (_: Exception) {}
                }
                ShortcutManagerCompat.removeAllDynamicShortcuts(context)
                ShortcutManagerCompat.addDynamicShortcuts(context, shortcuts)
                Timber.tag("LETHE_SHORTCUTS").d("${shortcuts.size} Shortcuts aktualisiert, ${toDisable.size} deaktiviert")
            } catch (e: Exception) {
                Timber.tag("LETHE_SHORTCUTS").e(e, "Fehler beim Aktualisieren der Shortcuts")
            }
        }
    }

    /** Deaktiviert einen einzelnen Shortcut (gelöschter Kontakt/Gruppe) sofort im Share Sheet. */
    suspend fun disableShortcut(id: String) {
        withContext(Dispatchers.IO) {
            try {
                ShortcutManagerCompat.disableShortcuts(context, listOf(id), null)
                Timber.tag("LETHE_SHORTCUTS").d("Shortcut $id deaktiviert")
            } catch (_: Exception) {}
        }
    }

    private suspend fun buildContactShortcut(contact: ContactEntity, rank: Int): ShortcutInfoCompat {
        val name = contact.customAlias?.takeIf { it.isNotBlank() }
            ?: contact.username?.takeIf { it.isNotBlank() }
            ?: contact.fakeNumber
        val icon = loadContactIcon(contact.profileImageUrl)
        val iconCompat = if (icon != null) IconCompat.createWithAdaptiveBitmap(icon)
                         else IconCompat.createWithResource(context, R.mipmap.ic_launcher)

        val person = Person.Builder()
            .setKey(contact.userId)
            .setName(name)
            .setIcon(iconCompat)
            .build()

        val openChatIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("chat_id", contact.userId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return ShortcutInfoCompat.Builder(context, contact.userId)
            .setShortLabel(name)
            .setLongLabel(name)
            .setIcon(iconCompat)
            .setIntent(openChatIntent)
            .setCategories(setOf(CATEGORY_SHARE))
            .setLongLived(true)
            .setRank(rank)
            .setPersons(arrayOf(person))
            .build()
    }

    private suspend fun buildGroupShortcut(group: GroupEntity, rank: Int): ShortcutInfoCompat {
        val name = group.name
        val icon = loadContactIcon(group.groupImageUrl)
        val iconCompat = if (icon != null) IconCompat.createWithAdaptiveBitmap(icon)
                         else IconCompat.createWithResource(context, R.mipmap.ic_launcher)

        val person = Person.Builder()
            .setKey(group.groupId)
            .setName(name)
            .setIcon(iconCompat)
            .build()

        val openChatIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("chat_id", group.groupId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return ShortcutInfoCompat.Builder(context, group.groupId)
            .setShortLabel(name)
            .setLongLabel(name)
            .setIcon(iconCompat)
            .setIntent(openChatIntent)
            .setCategories(setOf(CATEGORY_SHARE))
            .setLongLived(true)
            .setRank(rank)
            .setPersons(arrayOf(person))
            .build()
    }

    private suspend fun loadContactIcon(url: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (url == null) return@withContext null
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(128, 128)
                .build()
            (loader.execute(request).drawable as? BitmapDrawable)?.bitmap
        } catch (_: Exception) { null }
    }
}
