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

        ChangelogEntry(
            version = "10.4.171",
            title = "Mitglieder entfernen & Gruppen-Schlüssel-Fixes",
            items = listOf(
                "Neu: In der Mitgliederverwaltung einer Gruppe kann man sich jetzt selbst entfernen (Gruppe verlassen) – der Ersteller und Admins können auch andere Mitglieder entfernen",
                "Entfernte Mitglieder sehen die Gruppe danach sofort nicht mehr in ihrer Chat-Liste",
                "Fehler behoben, durch den sich nach dem Löschen einer angepinnten Gruppe keine neue Gruppe mehr anpinnen ließ",
                "Fehler behoben, durch den ein Kontakt nach einer Neuanmeldung in manchen Gruppen keine Nachrichten mehr entschlüsseln konnte (\"Schlüssel nicht verfügbar\")",
            ),
            shortSummary = "Mitglieder aus Gruppen entfernen, Pin-Fix und Verschlüsselungs-Fix nach Neuanmeldung"
        ),

        ChangelogEntry(
            version = "10.4.170",
            title = "Speicher-Absturz behoben & Videoanruf-Weichzeichner verbessert",
            items = listOf(
                "Absturz beim Öffnen eines Gruppenchats mit neuem Bild durch Speicherüberlauf behoben – Bild-Zwischenspeicher wird jetzt effizienter genutzt",
                "Der Bildeditor benötigt jetzt deutlich weniger Arbeitsspeicher, ohne Qualitätsverlust",
                "Der Hintergrund-Weichzeichner bei Videoanrufen wirkt jetzt feiner und stärker statt grob verpixelt",
            ),
            shortSummary = "Absturz-Fix bei Gruppenbildern, sparsamerer Bildeditor, feinerer Videoanruf-Weichzeichner"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
