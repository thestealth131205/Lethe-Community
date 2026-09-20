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
            version = "10.4.177",
            title = "Pre-Release-Musik, lokale Kopierfunktion & Desktop-Jam",
            items = listOf(
                "Neu: Admins können Kontakten per Langdruck in der Kontaktliste Zugriff auf unveröffentlichte (Pre-Release) Musik freischalten",
                "Musik-Upload/-Bearbeitung hat jetzt eine \"Unveröffentlicht\"-Option – solche Titel sind nur für freigeschaltete Nutzer sichtbar (mit PRE-Kennzeichen)",
                "Lokale Musik lässt sich jetzt direkt zwischen Smartphone und Windows Media Player kopieren",
                "Lethe Media Player für Windows: Gemeinsam hören (Jam) und Chromecast werden jetzt unterstützt",
                "Künstler-Herz (Fan werden) funktioniert jetzt zuverlässig, auch für Audius-Künstler ohne bisherige Tracks in der Bibliothek",
            ),
            shortSummary = "Pre-Release-Musik für ausgewählte Kontakte, lokale Musik-Kopierfunktion, Jam & Cast im Windows-Player"
        ),

        ChangelogEntry(
            version = "10.4.176",
            title = "Windows Media Player & Künstler-Verbesserungen",
            items = listOf(
                "Windows Media Player: MSI-Installer erkennt jetzt Updates zuverlässig statt eine Zweitinstallation anzulegen",
                "Webseite bietet den Windows Media Player jetzt als Installer (MSI) und portable Version (EXE) zum Download an",
                "Push-Benachrichtigung auf dem Smartphone, wenn eine Geräte-Authentifizierung (z.B. Windows/Web/neues Handy) im Hintergrund angefordert wird",
                "Künstler-Fan-Button (Herz für Neuerscheinungs-Benachrichtigungen) funktioniert jetzt auch bei Audius-Künstlern",
                "Künstler-Scanner findet jetzt auch Tracks auf Audius und verknüpft sie automatisch mit dem Künstler",
                "Zurück-Button der Künstler-Ansicht wird nicht mehr von der Statusleiste verdeckt",
            ),
            shortSummary = "Windows Media Player Update-Fix, Downloads auf der Webseite, Künstler-Fan-Button für Audius"
        ),

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

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
