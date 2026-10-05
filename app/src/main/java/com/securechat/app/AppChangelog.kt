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
            version = "10.4.192",
            title = "Backend-Passwort-Bestätigung verbessert",
            items = listOf(
                "Beim Bestätigen des Backend-Passworts im Drei-Punkte-Menü wird jetzt ein Lade-Symbol im Button angezeigt, solange die Prüfung läuft",
                "Bei schlechter Verbindung erscheint nach 15 Sekunden eine klare Fehlermeldung, statt dass der Button dauerhaft hängen bleibt",
            ),
            shortSummary = "Backend-Passwort-Bestätigung: Lade-Anzeige & Zeitüberschreitung bei schlechter Verbindung"
        ),

        ChangelogEntry(
            version = "10.4.191",
            title = "Anruf-Benachrichtigungen & Reaktionen korrigiert",
            items = listOf(
                "Bei eingehenden Anrufen erscheint jetzt nur noch eine Benachrichtigung statt beider gleichzeitig: Ist das Display aus oder der Sperrbildschirm aktiv, nur die kleine Benachrichtigung; wird das Gerät gerade aktiv genutzt, direkt die Vollbild-Anrufansicht",
                "Fehler behoben: Eine gesetzte Reaktion (z.B. ❤️) konnte kurz aufblitzen und sofort wieder verschwinden, wenn gleichzeitig eine Benachrichtigung oder ein Chat-Abgleich lief",
            ),
            shortSummary = "Anruf-Benachrichtigungen vereinheitlicht, Reaktions-Flackern behoben"
        ),

        ChangelogEntry(
            version = "10.4.190",
            title = "Benachrichtigungen, Reaktionen & Zitate korrigiert",
            items = listOf(
                "Fehler behoben: Nach Akku-leer/Neustart wurden bereits zugestellte Benachrichtigungen teils erneut mit Ton/Vibration angezeigt",
                "Reaktionen (z.B. ❤️) auf Bilder/Videos in Gruppenchats werden jetzt zuverlässig bei allen angezeigt und bei mehreren gleichen Reaktionen hochgezählt",
                "Zitierte Nachricht blieb beim Senden einer Sprachnachricht oder eines Bildes/Videos am Textfeld hängen, statt an die gesendete Nachricht angehängt zu werden – jetzt korrekt verknüpft (auch in Gruppenchats)",
                "Samsung-Geräte: Bilder/Videos beim Betreten eines Gruppenchats laden jetzt deutlich flüssiger statt einzeln und langsam nacheinander",
            ),
            shortSummary = "Benachrichtigungs-Duplikate, Gruppen-Reaktionen & Zitate behoben"
        ),

        ChangelogEntry(
            version = "10.4.189",
            title = "Halloween-Lumi & Grußkarte zum Teilen",
            items = listOf(
                "Neues Halloween-Lumi im Chat (Smiley-Button): Kürbis mit rot flackernden Augen, Totenschädel und klapperndes Skelett während des gruseligen Abdunkelns",
                "Die gruselige Abdunkel-/Aufhell-Animation beim Öffnen der Kontaktliste läuft jetzt langsamer über 6 Sekunden mit kurzem weißen Aufblitzen in der Mitte",
                "Mehrfaches Tippen auf die Halloween-Deko in der Kontaktliste öffnet jetzt eine teilbare Happy-Halloween-Grußkarte mit dem eigenen Benutzernamen",
            ),
            shortSummary = "Halloween-Lumi zum Versenden & teilbare Halloween-Grußkarte"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
