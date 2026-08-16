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
            version = "10.4.150",
            title = "Neu: Multi-Account-Einstellungen",
            items = listOf(
                "Neuer Einstellungspunkt \u201eMulti Account\u201c: Konto wechseln jetzt direkt aus den App-Einstellungen, ohne erst zum Konto-Bildschirm zu wechseln",
                "Neue Option \u201eAlle Accounts \u00fcberwachen\u201c: Bei aktivierter Einstellung wirst du bei neuen Nachrichten auf nicht eingeloggten Konten benachrichtigt (ohne Kontaktname und Text) und kannst per Tap direkt in den jeweiligen Account wechseln",
                "Neue Option \u201eGemischte Kontaktliste\u201c: Zeigt auf Wunsch Kontakte all deiner gespeicherten Accounts gemeinsam in der Kontaktliste an, mit farblich hervorgehobenem Konto-Namen hinter dem Kontaktnamen",
            ),
            shortSummary = "Neuer Multi-Account-Bereich: Konto wechseln, Account-\u00dcberwachung und gemischte Kontaktliste"
        ),

        ChangelogEntry(
            version = "10.4.149",
            title = "Lethe Assistant leichter finden & Galerie-Fix",
            items = listOf(
                "Der offizielle Lethe-Bot (Lethe Assistant) ist jetzt für alle Nutzer direkt beim Hinzufügen eines Kontakts unter \u201eBots\u201c sichtbar \u2013 ohne Suche",
                "Behoben: Nachrichten mit mehreren Bildern wurden in der Medien-Galerie eines Chats manchmal nicht korrekt angezeigt \u2013 jedes Bild erscheint dort jetzt einzeln",
                "Geteilte Song-Links aus dem Lethe Media Player nutzen jetzt eine kürzere Adresse (songs.letheapp.de) \u2013 bereits geteilte alte Links funktionieren weiterhin",
            ),
            shortSummary = "Lethe Assistant leichter zu finden, Galerie-Fix f\u00fcr Mehrfachbilder und k\u00fcrzere Song-Links"
        ),

        ChangelogEntry(
            version = "10.4.148",
            title = "Blockieren jetzt klar sichtbar",
            items = listOf(
                "Wenn du jemanden blockierst, wird das Eingabefeld im Chat jetzt deutlich gekennzeichnet, damit klar ist, dass keine Nachrichten mehr gesendet werden können",
                "Auch die blockierte Person sieht jetzt direkt im Eingabefeld, dass sie dir nicht mehr schreiben kann",
                "In der Kontaktliste zeigt das Menü bei bereits blockierten Kontakten jetzt \u201eBlockierung aufheben\u201c statt \u201eBlockieren\u201c an",
            ),
            shortSummary = "Blockieren ist jetzt für beide Seiten klar im Chat und in der Kontaktliste erkennbar"
        ),

        ChangelogEntry(
            version = "10.4.147",
            title = "Lethe Media Player synchronisiert",
            items = listOf(
                "Der Lethe Media Player wird jetzt bei jedem App-Update zuverl\u00e4ssig mit aktualisiert, damit Versionsanzeige und Update-Erkennung im Player immer auf dem neuesten Stand sind",
            ),
            shortSummary = "Lethe Media Player wird jetzt zuverl\u00e4ssig mit der App aktualisiert"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
