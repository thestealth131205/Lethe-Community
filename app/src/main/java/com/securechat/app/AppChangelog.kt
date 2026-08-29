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
            version = "10.4.161",
            title = "Flipper-Automat: Respawn-Position korrigiert",
            items = listOf(
                "Im Pinball-Spiel spawnt eine festhängende Kugel jetzt leicht links der Mitte statt exakt mittig, damit sie nicht mehr zwischen oberem Bumper und Decke gefangen werden und so Punkte gefarmt werden können",
            ),
            shortSummary = "Flipper-Respawn-Position angepasst, um Punkte-Farming zu verhindern"
        ),

        ChangelogEntry(
            version = "10.4.160",
            title = "Flipper-Automat: Festhänger-Erkennung verbessert",
            items = listOf(
                "Im Pinball-Spiel greift die Erkennung festhängender Kugeln jetzt auf dem gesamten Spielfeld, auch wenn eine Kugel ruhig auf dem Flipper liegen bleibt – vorher konnte das Spiel dadurch nicht beendet werden",
            ),
            shortSummary = "Festhängende Kugeln werden im Flipper-Spiel jetzt überall zuverlässig erkannt"
        ),

        ChangelogEntry(
            version = "10.4.159",
            title = "Flipper-Automat verbessert",
            items = listOf(
                "Im Pinball-Spiel verschwindet eine Kugel jetzt automatisch und fällt von oben mittig neu herunter, wenn sie länger als 3 Sekunden praktisch an derselben Stelle festhängt",
            ),
            shortSummary = "Festhängende Kugeln im Flipper-Spiel lösen sich jetzt automatisch"
        ),

        ChangelogEntry(
            version = "10.4.158",
            title = "Anrufliste & Code-Blöcke verbessert",
            items = listOf(
                "Verpasste Videocall-Benachrichtigungen werden jetzt beim Öffnen der Anrufliste automatisch als gelesen markiert, inkl. Anzeige der Anzahl ungelesener verpasster Anrufe",
                "Code-Blöcke im Chat lassen sich jetzt über ein neues Symbol im Vollbild anzeigen und bearbeiten",
            ),
            shortSummary = "Anrufliste markiert verpasste Anrufe automatisch als gelesen, Code-Blöcke im Vollbild bearbeitbar"
        ),

        ChangelogEntry(
            version = "10.4.157",
            title = "Stabilitätsverbesserung bei Circle-Videos",
            items = listOf(
                "Interner Umbau: Der Anzeige-Code für Circle-Videos in Chats wurde in eine eigene Komponente ausgelagert, um seltene Abstürze beim Kompilieren auf manchen Geräten zu vermeiden",
            ),
            shortSummary = "Interne Stabilitätsverbesserung bei Circle-Videos"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
