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
            version = "10.4.152",
            title = "Weiterleiten, Reaktionen & Zuverl\u00e4ssigkeit",
            items = listOf(
                "Beim Weiterleiten werden Kontakte und Gruppen jetzt nach H\u00e4ufigkeit sortiert, Gruppenbilder angezeigt und angepinnte Chats erscheinen ganz oben",
                "Die Emoji-Reaktionsleiste wird jetzt unter der Nachricht angezeigt, wenn oben kein Platz ist \u2013 so ist sie immer sofort sichtbar",
                "Die App bleibt zuverl\u00e4ssiger im Hintergrund verbunden und startet nach einem Ger\u00e4te-Neustart automatisch wieder \u2013 die Meldung \u201eDu k\u00f6nntest neue Nachrichten haben\u201c erscheint nicht mehr unn\u00f6tig",
            ),
            shortSummary = "Besseres Weiterleiten, gut sichtbare Reaktionsleiste und zuverl\u00e4ssigere Hintergrundverbindung"
        ),

        ChangelogEntry(
            version = "10.4.151",
            title = "Benachrichtigungen & Live-Standort verbessert",
            items = listOf(
                "Behoben: Bereits gelesene Nachrichten wurden nach einem Neuverbinden manchmal erneut als neue Nachricht angezeigt",
                "Behoben: Benachrichtigungen zeigten in seltenen Fällen unverständlichen, noch verschlüsselten Text an \u2013 jetzt erscheint stattdessen \u201eNeue Nachricht\u201c, bis der Inhalt entschlüsselt werden kann",
                "Behoben: Der geteilte Live-Standort aktualisiert sich jetzt zuverlässig fortlaufend statt bei einem alten Stand stehen zu bleiben",
            ),
            shortSummary = "Zuverlässigere Benachrichtigungen und fortlaufend aktualisierter Live-Standort"
        ),

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

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
