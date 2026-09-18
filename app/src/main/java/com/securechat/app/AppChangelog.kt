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
            version = "10.4.175",
            title = "Geräte-Authentifizierung per Lethe Messenger",
            items = listOf(
                "Neu: Ein neues Gerät (Media Player, Web Chat, weiteres Handy) kann sich jetzt per Freigabe auf einem bereits verknüpften Lethe-Messenger-Smartphone anmelden – Bestätigung per Passwort oder Fingerabdruck statt SMS-Code",
                "Die Freigabe-Anfrage zeigt App, Gerät und ungefähren Standort (per IP) des anfragenden Geräts an, inkl. \"Nein, das bin ich nicht\"-Option",
                "SMS-Verifizierung bleibt weiterhin als Fallback, falls kein Messenger-Gerät verknüpft ist",
                "Lethe Media Player für Windows (Desktop) neu hinzugefügt",
            ),
            shortSummary = "Neue Geräte per Lethe Messenger freischalten statt per SMS, Windows Media Player"
        ),

        ChangelogEntry(
            version = "10.4.174",
            title = "Neue Handynummer & Playlist-Verschieben",
            items = listOf(
                "Neu: Im Kontaktlisten-Menü unter \"Account\" kann jetzt eine neue Handynummer per SMS-Code bestätigt werden – alle Kontakte erhalten automatisch eine Schlüssel-Erneuerung und im Chat erscheint ein Hinweis auf die neue Nummer",
                "Songtexte-Viewer: Zeilen sind jetzt durch Leerzeilen besser lesbar, der komplette Text lässt sich beim Teilen bis zum Ende durchscrollen, und in der Teilen-Ansicht kann per Zwei-Finger-Geste gezoomt werden",
                "Fehler behoben, durch den der Jam-Ansicht beim gleichzeitigen Anzeigen von Teilnehmern und Kontakten abstürzen konnte",
            ),
            shortSummary = "Neue Handynummer mit automatischer Schlüssel-Erneuerung, verbesserter Songtexte-Viewer"
        ),

        ChangelogEntry(
            version = "10.4.173",
            title = "Gemeinsam hören überarbeitet",
            items = listOf(
                "\"Gemeinsam hören\" im Chat öffnet jetzt die eigene Lethe-Bibliothek (Playlisten/Lieblingssongs) statt einer Datei-Auswahl – ein Antippen startet die Wiedergabe sofort bei allen Teilnehmern",
            ),
            shortSummary = "\"Gemeinsam hören\" nutzt jetzt die eigene Musikbibliothek statt Dateiauswahl"
        ),

        ChangelogEntry(
            version = "10.4.172",
            title = "Gruppen-Pin-Fix",
            items = listOf(
                "Bis zu 3 Gruppen können jetzt gleichzeitig angepinnt werden (statt bisher 2)",
                "Fehler behoben, durch den nach dem Löschen einer angepinnten Gruppe weiterhin keine neue Gruppe angepinnt werden konnte",
            ),
            shortSummary = "Bis zu 3 angepinnte Gruppen möglich, verwaiste Pins werden jetzt zuverlässig entfernt"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
