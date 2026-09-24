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
            version = "10.4.183",
            title = "Geräte-Verifizierung: SMS-Alternative & P2P-Status-Fix",
            items = listOf(
                "Neu: Im Freigabe-Dialog für ein neues Gerät gibt es jetzt unten den Button \"Andere Optionen\", um statt der Freigabe über ein anderes Gerät stattdessen einen SMS-Code anzufordern",
                "Fehler behoben: Der P2P-Verbindungsstatus (Ampel neben dem Kontaktnamen) verschwand nach einer Neuanmeldung oder Geräte-Verifizierung, obwohl P2P für den Account aktiviert war – die Einstellung wird jetzt korrekt vom Server übernommen",
            ),
            shortSummary = "Neue SMS-Alternative bei Geräte-Verifizierung, P2P-Status-Anzeige repariert"
        ),

        ChangelogEntry(
            version = "10.4.182",
            title = "Videoanruf-Fixes, Zoom im Videoanruf & Geräte-Sicherheitslücke geschlossen",
            items = listOf(
                "Videoanruf: Ursache für gelegentlich schwarzes Bild ohne Ton nach Annahme behoben – die Verbindung wird jetzt laufend überwacht und bei fehlendem Bild-/Tonfluss automatisch neu aufgebaut",
                "Neu: Im Videoanruf das eigene kleine Kamerabild antippen, um es groß anzuzeigen – dabei erscheinen oben links +/- Zoom-Buttons, um dem Gesprächspartner ein Detail näher zu zeigen",
                "Sicherheitslücke geschlossen: Wurde ein verknüpftes Gerät aus der Geräteübersicht entfernt, konnte man sich auf genau diesem Gerät ohne erneute Verifizierung wieder anmelden – jetzt ist bei jedem entfernten Gerät eine erneute Verifizierung (per SMS oder Freigabe über ein anderes Gerät) nötig, damit auch die Schlüssel sauber neu ausgetauscht werden",
            ),
            shortSummary = "Videoanruf-Bildaussetzer behoben, neuer Zoom im Anruf, Geräte-Sicherheitslücke geschlossen"
        ),

        ChangelogEntry(
            version = "10.4.181",
            title = "Verbesserte Kompatibilität für die F-Droid-Verteilung",
            items = listOf(
                "Interne Anpassung der App-Versionskennung, damit Updates über F-Droid (freie App-Quelle) zuverlässig für alle Geräte-Architekturen angeboten werden",
            ),
            shortSummary = "Interne Verbesserung für zuverlässige Updates über F-Droid"
        ),

        ChangelogEntry(
            version = "10.4.180",
            title = "Anruf-Absturz behoben & Weiterleiten an Gruppen repariert",
            items = listOf(
                "Absturz beim Empfang eines Anrufs behoben, wenn die Vollbild-Berechtigung fehlte (z.B. bei bestimmten Geräteherstellern)",
                "Weiterleiten von Nachrichten/Bildern aus einem 1:1-Chat an eine Gruppe funktioniert jetzt zuverlässig – kam zuvor nie bei den Gruppenmitgliedern an",
            ),
            shortSummary = "Anruf-Absturz behoben, Weiterleiten an Gruppen funktioniert jetzt zuverlässig"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
