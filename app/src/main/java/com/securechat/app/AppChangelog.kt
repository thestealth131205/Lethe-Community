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

        ChangelogEntry(
            version = "10.4.156",
            title = "Circle-Videos & Lethe Media Player verbessert",
            items = listOf(
                "Circle-Videos: Ein antippbares Play-Symbol zeigt jetzt immer an, ob ein Video gerade läuft oder wartet \u2013 kein scheinbar toter schwarzer Kreis mehr; nach dem Aufklappen mit Ton erscheint ein Wiederholen-Button",
                "Circle-Videos stürzen nicht mehr ab, wenn mehrere gleichzeitig sichtbar sind \u2013 die begrenzten Video-Decoder des Geräts werden jetzt sauber verwaltet",
                "Lethe Media Player: Shuffle und Wiederholung lassen sich jetzt auch in Android Auto direkt über eigene Buttons umschalten",
            ),
            shortSummary = "Stabilere Circle-Videos und Shuffle/Wiederholung in Android Auto"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
