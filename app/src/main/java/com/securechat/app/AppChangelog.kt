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

        ChangelogEntry(
            version = "10.4.185",
            title = "Klarere Fehlermeldung beim Login & Admin-Passwortänderung",
            items = listOf(
                "Fehler behoben: Beim Login erschien bei jedem Fehlschlag nur \"Login fehlgeschlagen\" ohne Grund – jetzt wird die tatsächliche Ursache vom Server angezeigt (z.B. falsches Passwort, unbekannte Nummer)",
                "Fehler behoben: Ein im Backend-Bereich neu gesetztes Nutzer-Passwort mit unsichtbaren Leerzeichen am Rand führte beim Login zu einem Fehlschlag – Passwörter werden jetzt vor dem Login-Versuch bereinigt",
            ),
            shortSummary = "Klarere Fehlermeldungen beim Login, Passwort-Bugfix"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
