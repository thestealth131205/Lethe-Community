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
            version = "10.4.190",
            title = "Benachrichtigungen, Reaktionen & Zitate korrigiert",
            items = listOf(
                "Fehler behoben: Nach Akku-leer/Neustart wurden bereits zugestellte Benachrichtigungen teils erneut mit Ton/Vibration angezeigt",
                "Reaktionen (z.B. ❤️) auf Bilder/Videos in Gruppenchats werden jetzt zuverlässig bei allen angezeigt und bei mehreren gleichen Reaktionen hochgezählt",
                "Zitierte Nachricht blieb beim Senden einer Sprachnachricht oder eines Bildes/Videos am Textfeld hängen, statt an die gesendete Nachricht angehängt zu werden – jetzt korrekt verknüpft (auch in Gruppenchats)",
                "Samsung-Geräte: Bilder/Videos beim Betreten eines Gruppenchats laden jetzt deutlich flüssiger statt einzeln und langsam nacheinander",
            ),
            shortSummary = "Benachrichtigungs-Duplikate, Gruppen-Reaktionen & Zitate behoben"
        ),

        ChangelogEntry(
            version = "10.4.189",
            title = "Halloween-Lumi & Grußkarte zum Teilen",
            items = listOf(
                "Neues Halloween-Lumi im Chat (Smiley-Button): Kürbis mit rot flackernden Augen, Totenschädel und klapperndes Skelett während des gruseligen Abdunkelns",
                "Die gruselige Abdunkel-/Aufhell-Animation beim Öffnen der Kontaktliste läuft jetzt langsamer über 6 Sekunden mit kurzem weißen Aufblitzen in der Mitte",
                "Mehrfaches Tippen auf die Halloween-Deko in der Kontaktliste öffnet jetzt eine teilbare Happy-Halloween-Grußkarte mit dem eigenen Benutzernamen",
            ),
            shortSummary = "Halloween-Lumi zum Versenden & teilbare Halloween-Grußkarte"
        ),

        ChangelogEntry(
            version = "10.4.188",
            title = "Halloween-Event & Weihnachts-Vorbereitung",
            items = listOf(
                "Neues Halloween-Event: wackelnde Kürbisköpfe und ein klapperndes Skelett in der Kontaktliste und auf dem Login-Bildschirm, dazu ein kurzes gruseliges Abdunkeln/Aufhellen beim Öffnen",
                "Weihnachts-Event erweitert: zusätzlich sanft fallende Schneeflocken neben Weihnachtsmann und Rentier",
                "Admin-Bereich: Halloween und Weihnachten können jetzt direkt über die Event-Auswahl aktiviert werden",
            ),
            shortSummary = "Halloween-Event mit Kürbissen & Skelett, Weihnachten erweitert"
        ),

        ChangelogEntry(
            version = "10.4.187",
            title = "Chat-Speicherverbrauch reduziert & Media-Player-Verbesserungen",
            items = listOf(
                "Fehler behoben: Sehr aktive Gruppenchats mit vielen Bildern/Videos konnten die App durch Speichermangel zum Absturz bringen – das Zeitfenster für geladene Nachrichten wird jetzt nicht mehr versehentlich komplett deaktiviert und medienreiche Chats starten mit einer kleineren Anzeigemenge",
                "Verwaiste, inaktive Gruppen ohne Mitglieder werden bereinigt",
                "Media Player: Songtexte-Button erscheint jetzt auch nach längerer Nichtnutzung zuverlässig beim letzten Titel",
                "Media Player (Windows): Lethe-Playlisten, Titel-Detailansicht und Playlist-Hinzufügen-Button ergänzt",
            ),
            shortSummary = "Weniger Abstürze in aktiven Gruppenchats, Media-Player-Fixes"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
