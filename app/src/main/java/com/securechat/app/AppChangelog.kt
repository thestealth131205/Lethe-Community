package com.securechat.app

/**
 * Eintrag im App-Changelog.
 * [version]      – Versionsnummer (z.B. "5.23")
 * [title]        – Kurzer Titel des Updates
 * [items]        – Nutzerfreundliche Beschreibung der Änderungen (nicht technisch)
 * [shortSummary] – Einzeiliger Kurztext für den Update-Dialog
 */
data class ChangelogEntry(
    val version: String,
    val title: String,
    val items: List<String>,
    val shortSummary: String
)

/**
 * Lokaler Changelog der App – wird in InfoScreen ("Was ist neu") und im Update-Dialog angezeigt.
 * WICHTIG: Bei jeder Versionserhöhung neuen Eintrag OBEN einfügen und ältesten entfernen
 * (maximal 4 Einträge behalten).
 */
object AppChangelog {

    val entries: List<ChangelogEntry> = listOf(

        ChangelogEntry(
            version = "10.4.204",
            title = "Benachrichtigung bei Reaktionen & Media-Player-Neuerungen",
            items = listOf(
                "Reagiert jemand auf eine eigene Nachricht (auch in Gruppen), gibt es jetzt eine Benachrichtigung mit Name und Emoji – ein Tipp darauf führt direkt zum passenden Chat",
                "Dabei einen seit einigen Tagen bestehenden Fehler behoben, durch den solche Benachrichtigungen bei geschlossener App manchmal gar nicht ankamen",
                "Im Lethe Media Player für Windows/Desktop gibt es jetzt Zufallswiedergabe und Wiederholen (auch beim Casten über Chromecast)",
                "In der Lethe-Musikbibliothek waren über 'Künstler scannen' verknüpfte Titel für andere Nutzer teils unsichtbar – behoben",
            ),
            shortSummary = "Benachrichtigung bei Reaktionen auf eigene Nachrichten, Zufallswiedergabe/Wiederholen im Desktop-Player, Bibliotheks-Fix"
        ),

        ChangelogEntry(
            version = "10.4.203",
            title = "Akkuverbrauch im Hintergrund deutlich reduziert",
            items = listOf(
                "Bei Play-Store-Installationen mit funktionierendem Push (FCM) läuft der Dauer-Verbindungsdienst jetzt nicht mehr ständig im Hintergrund – Nachrichten und Anrufe kommen stattdessen über Push, was den Akkuverbrauch bei wenig Nutzung deutlich senkt",
                "Der Wach-Zustand des Geräts wird nicht mehr dauerhaft gehalten, sondern nur noch für den kurzen Moment der Verbindungsprüfung – dazwischen kann das Gerät wieder in den Energiesparschlaf wechseln",
                "Betrifft sowohl die Play-Store- als auch die F-Droid/FOSS-Version; bei F-Droid bleibt der Dauerdienst weiterhin aktiv, da es dort keine alternative Push-Zustellung gibt",
            ),
            shortSummary = "Deutlich weniger Akkuverbrauch im Hintergrund durch reduzierte Wach-Zustände und Push statt Dauerverbindung (Play Store)"
        ),

        ChangelogEntry(
            version = "10.4.202",
            title = "Bildqualität, Medien-Übersicht & Reaktionen im Chat verbessert",
            items = listOf(
                "Fotos unter 2 Megapixel werden beim Versenden nicht mehr verkleinert – die Originalauflösung bleibt erhalten",
                "Bilder und Videos landen nicht mehr doppelt auf dem Gerät – eine außerhalb der App nutzbare Kopie entsteht jetzt nur noch beim Tippen auf 'In Galerie speichern'",
                "Reaktionen auf Nachrichten in 1-zu-1-Chats verschwanden manchmal wieder oder kamen beim Chatpartner gar nicht erst an – behoben",
                "Die Medien-Übersicht eines Chats (Tipp auf Profil- bzw. Gruppenbild) zeigt jetzt wirklich alle gesendeten Fotos und Videos und lädt bei Bedarf weitere ältere Medien nach",
                "Im Lethe Media Player funktionierte 'Wiederholen' bei eingeschalteten Übergängen nicht richtig – behoben; außerdem unterstützt jetzt auch die Chromecast-Wiedergabe das Wiederholen",
            ),
            shortSummary = "Originalauflösung bei kleinen Fotos, kein doppeltes Speichern, Reaktionen-Fix, vollständige Medien-Übersicht, Wiederholen-Fix im Media Player"
        ),

        ChangelogEntry(
            version = "10.4.201",
            title = "Gruseligere Halloween-Reaktion bei Videoanrufen",
            items = listOf(
                "Die Halloween-Reaktion (Kürbiskopf) bei Videoanrufen wurde gruseliger: dunkle Wolken ziehen im Hintergrund vorbei, ein Totenschädel lauert in der Ecke, und zwei Blitze lassen den Bildschirm kurz aufblitzen",
            ),
            shortSummary = "Halloween-Reaktion bei Videoanrufen jetzt mit Wolken, Totenschädel und Blitzen"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
