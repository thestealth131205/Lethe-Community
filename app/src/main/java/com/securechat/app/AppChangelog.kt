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
            version = "10.4.179",
            title = "Multi-Device: automatische Nachentschlüsselung",
            items = listOf(
                "Beim Verbindungsaufbau wird jetzt proaktiv geprüft, ob Schlüssel von einem anderen angemeldeten Gerät nachgeladen werden müssen – nicht erst wenn eine einzelne Nachricht fehlschlägt",
                "Nachrichten, die zuvor als \"Schlüssel nicht verfügbar\" gespeichert wurden, werden beim erneuten Öffnen des Chats automatisch nachentschlüsselt, sobald das Gerät den passenden Schlüssel erhalten hat",
            ),
            shortSummary = "Multi-Device: verpasste Nachrichten werden automatisch nachentschlüsselt"
        ),

        ChangelogEntry(
            version = "10.4.178",
            title = "Echtes Multi-Device-E2EE & Geräteverwaltung",
            items = listOf(
                "Neu: Ein zweites Messenger-Handy kann jetzt live über das erste verknüpfte Gerät die Verschlüsselungs-Schlüssel übernehmen – beide Geräte können danach alle Chats lesen, ohne dass sich der Schlüssel des Erstgeräts ändert",
                "\"Neue Handynummer angeben\" ist jetzt im 3-Punkte-Menü unter \"Account\" zu finden (statt direkt im Hauptmenü)",
                "Neu: Übersicht \"Verbundene Geräte\" unter Geräte – zeigt alle mit dem Account verknüpften Geräte inkl. Kennzeichnung des ursprünglichen Host-Geräts, mit Löschen-Button pro Gerät",
            ),
            shortSummary = "Echte Multi-Device-Ende-zu-Ende-Verschlüsselung, Geräteübersicht mit Host-Kennzeichnung"
        ),

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

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
