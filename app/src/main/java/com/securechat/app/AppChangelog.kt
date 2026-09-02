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
            version = "10.4.163",
            title = "Video-Wiedergabe & Benachrichtigungen verbessert",
            items = listOf(
                "Ältere, hochgescrollte Videos im Chat spielen jetzt zuverlässig ab, statt beim Antippen schwarz mit 00:00 zu bleiben – bis dahin wird ein Ladekreis angezeigt",
                "Beim direkten Antworten aus einer Benachrichtigung wird jetzt wieder das eigene Profilbild neben \"Du\" angezeigt",
                "Bei der Registrierung wird eine bereits eingegebene Landesvorwahl nicht mehr fälschlich verdoppelt, wodurch die SMS mit dem Code zuverlässig ankommt",
                "Ein fehlgeschlagener SMS-Versand beim Registrieren wird jetzt sofort als Fehlermeldung angezeigt statt stillschweigend zu warten",
            ),
            shortSummary = "Videowiedergabe, Antwort-Benachrichtigung mit Profilbild und SMS-Versand bei der Registrierung verbessert"
        ),

        ChangelogEntry(
            version = "10.4.162",
            title = "Kopfhörer-Ausgabe & Jump-or-Die verbessert",
            items = listOf(
                "Lethe Media Player: Der Ton kommt jetzt zuverlässig über eingesteckte Klinken-Kopfhörer, USB- und Bluetooth-Geräte (auch neuere LE-Audio-Geräte) statt fälschlich über den Lautsprecher",
                "Jump or Die: Wer einmal über 1000 Punkte hatte, bekommt bis zum Verlassen des Spiels bei jedem Neustart zwei schwarze Löcher – das zweite erscheint 10 Stufen vor dem ersten",
            ),
            shortSummary = "Kopfhörer-Ausgabe im Media Player korrigiert, zweites schwarzes Loch in Jump or Die"
        ),

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

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
