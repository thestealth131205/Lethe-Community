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
            version = "10.4.146",
            title = "Gruppen-Verschlüsselung & fl\u00fcssigeres Laden im Chat",
            items = listOf(
                "Behoben: In seltenen F\u00e4llen konnten Chatpartner in Gruppen \u201eSchl\u00fcssel nicht verf\u00fcgbar\u201c statt der Nachricht sehen, wenn sich der eigene Gruppen-Schl\u00fcssel zwischenzeitlich \u00e4ndern musste \u2013 die Schl\u00fcsselversionierung wurde korrigiert, sodass alle Mitglieder den neuen Schl\u00fcssel zuverl\u00e4ssig erhalten",
                "Bilder, Videos und Sprachnachrichten im Chat werden jetzt nacheinander statt gleichzeitig geladen \u2013 das sorgt f\u00fcr fl\u00fcssigeres Scrollen in Chats mit vielen Medien",
            ),
            shortSummary = "Fix f\u00fcr Gruppen-Verschl\u00fcsselung und fl\u00fcssigeres Laden von Medien im Chat"
        ),

        ChangelogEntry(
            version = "10.4.145",
            title = "Benachrichtigungen & Chat-Verlauf laden",
            items = listOf(
                "Behoben: Nachrichten, die beim erneuten Verbinden oder im Hintergrund nachgeladen wurden, l\u00f6sten manchmal versehentlich eine Benachrichtigung aus, obwohl sie bereits bekannt waren \u2013 das passiert jetzt nicht mehr",
                "Beim Hochscrollen im Chatverlauf werden \u00e4ltere Nachrichten jetzt in klaren 50er-Bl\u00f6cken nachgeladen (mit Ladekreis), statt fortlaufend im Hintergrund nachzuladen",
            ),
            shortSummary = "Weniger unn\u00f6tige Benachrichtigungen, dazu \u00fcbersichtlicheres Nachladen \u00e4lterer Chat-Nachrichten"
        ),

        ChangelogEntry(
            version = "10.4.144",
            title = "Musik im Media Player streamen & Nachrichten-Nachladen",
            items = listOf(
                "Neu: Tippt man im Chat auf das Cast-Symbol einer Musiknachricht, \u00f6ffnet sich jetzt direkt der Lethe Media Player und streamt den Titel \u2013 inklusive Titel, K\u00fcnstler und Cover. Von dort l\u00e4sst sich wie gewohnt auf Lautsprecher casten",
                "Behoben: Nach l\u00e4ngerer Zeit im Hintergrund (z.B. \u00fcber Nacht) konnte es passieren, dass einzelne per Benachrichtigung angezeigte Nachrichten nicht im Chatverlauf auftauchten \u2013 fehlende Nachrichten werden beim Wiederverbinden jetzt zuverl\u00e4ssiger nachgeladen",
            ),
            shortSummary = "Musiknachrichten \u00f6ffnen direkt den Lethe Media Player, dazu zuverl\u00e4ssigeres Nachladen fehlender Nachrichten"
        ),

        ChangelogEntry(
            version = "10.4.143",
            title = "Mehrkonten-Fix & Medien-Player-Download",
            items = listOf(
                "Behoben: Beim Wechseln zwischen mehreren Konten auf demselben Ger\u00e4t landete man nach einem Neustart manchmal im falschen Konto \u2013 Anmeldedaten werden jetzt zuverl\u00e4ssig dem richtigen Konto zugeordnet",
                "Behoben: In der Konto-Liste des Konto-Wechslers fehlten teilweise gespeicherte Konten",
                "Zwischengespeicherte Medien (Bilder/Videos) werden jetzt sauber pro Konto getrennt abgelegt, statt sich bei mehreren Konten auf demselben Ger\u00e4t zu vermischen",
                "Neu: Tippt man im Chat auf das Cast-Symbol und der Lethe Media Player ist nicht installiert, \u00f6ffnet sich jetzt eine Download-Seite daf\u00fcr",
            ),
            shortSummary = "Fix f\u00fcr Mehrkonten-Wechsel und neue Download-Seite f\u00fcr den Lethe Media Player"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
