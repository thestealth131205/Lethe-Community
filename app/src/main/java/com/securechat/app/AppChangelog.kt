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
            version = "10.4.165",
            title = "Startbildschirm-Widgets & Gruppen-Verbesserungen",
            items = listOf(
                "Neu: Kontakte und Gruppen lassen sich als Widget auf den Startbildschirm legen – immer mit dem aktuellen Profil- bzw. Gruppenbild, ein Tipp öffnet direkt den Chat",
                "Gruppennachrichten erscheinen jetzt sofort, auch wenn sie per Benachrichtigung eintreffen – kein Warten mehr und kein erneutes Öffnen des Chats nötig",
                "Ein geändertes Gruppenbild wird jetzt auch dann geladen, wenn man während der Änderung offline war oder erst später zur Gruppe hinzugefügt wurde",
                "Teilen an eine Gruppe öffnet den Gruppenchat jetzt mit vorbefülltem Text/Bild, statt sofort und ungefragt zu senden",
                "Profil- und Gruppenbilder werden regelmäßig auf Aktualität geprüft und nur bei echter Änderung neu geladen",
            ),
            shortSummary = "Startbildschirm-Widgets für Kontakte/Gruppen, schnellere Gruppennachrichten und zuverlässigere Gruppenbilder"
        ),

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

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
