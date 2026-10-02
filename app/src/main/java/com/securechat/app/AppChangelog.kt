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

        ChangelogEntry(
            version = "10.4.186",
            title = "Absturz durch Speichermangel im Dauer-Hintergrunddienst behoben",
            items = listOf(
                "Fehler behoben: Der Nachrichtendienst berechnete für die Tag/Nacht-Pingfrequenz bei jedem Ping erneut die aktuelle Uhrzeit inkl. kompletter Zeitzonendaten – über viele Stunden Laufzeit konnte das zu einem Speichermangel-Absturz führen. Der Wert wird jetzt 5 Minuten zwischengespeichert",
            ),
            shortSummary = "Stabilitäts-Fix gegen seltenen Speicher-Absturz im Hintergrunddienst"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
