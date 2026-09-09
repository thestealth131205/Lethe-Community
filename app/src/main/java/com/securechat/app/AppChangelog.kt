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
            version = "10.4.171",
            title = "Mitglieder entfernen & Gruppen-Schlüssel-Fixes",
            items = listOf(
                "Neu: In der Mitgliederverwaltung einer Gruppe kann man sich jetzt selbst entfernen (Gruppe verlassen) – der Ersteller und Admins können auch andere Mitglieder entfernen",
                "Entfernte Mitglieder sehen die Gruppe danach sofort nicht mehr in ihrer Chat-Liste",
                "Fehler behoben, durch den sich nach dem Löschen einer angepinnten Gruppe keine neue Gruppe mehr anpinnen ließ",
                "Fehler behoben, durch den ein Kontakt nach einer Neuanmeldung in manchen Gruppen keine Nachrichten mehr entschlüsseln konnte (\"Schlüssel nicht verfügbar\")",
            ),
            shortSummary = "Mitglieder aus Gruppen entfernen, Pin-Fix und Verschlüsselungs-Fix nach Neuanmeldung"
        ),

        ChangelogEntry(
            version = "10.4.170",
            title = "Speicher-Absturz behoben & Videoanruf-Weichzeichner verbessert",
            items = listOf(
                "Absturz beim Öffnen eines Gruppenchats mit neuem Bild durch Speicherüberlauf behoben – Bild-Zwischenspeicher wird jetzt effizienter genutzt",
                "Der Bildeditor benötigt jetzt deutlich weniger Arbeitsspeicher, ohne Qualitätsverlust",
                "Der Hintergrund-Weichzeichner bei Videoanrufen wirkt jetzt feiner und stärker statt grob verpixelt",
            ),
            shortSummary = "Absturz-Fix bei Gruppenbildern, sparsamerer Bildeditor, feinerer Videoanruf-Weichzeichner"
        ),

        ChangelogEntry(
            version = "10.4.169",
            title = "Akkuverbrauch im Hintergrund reduziert",
            items = listOf(
                "Der regelmäßige Geschwindigkeitstest im Hintergrund läuft jetzt nur noch halb so oft",
                "Die Verbindungsprüfung läuft tagsüber etwas seltener und nachts (22-6 Uhr) deutlich seltener",
                "Die Verbindungsprüfung setzt einen Durchlauf aus, wenn die letzten Prüfungen bereits erfolgreich waren",
            ),
            shortSummary = "Weniger Akkuverbrauch im Hintergrund durch optimierte Verbindungsprüfungen"
        ),

        ChangelogEntry(
            version = "10.4.168",
            title = "Code-Anhänge & Chat-Verbesserungen",
            items = listOf(
                "Neu: Dateien lassen sich jetzt als Code-Anhang senden (eigenes Symbol im Anhang-Menü) – werden im Chat als Codeblock mit Syntax-Hervorhebung angezeigt und mit Originalnamen gespeichert",
                "Neu: Ein Codeblock lässt sich auch direkt im Textfeld eingeben (/* dateiname.endung ... */) – wird automatisch als Code-Datei gesendet",
                "Der Code-Betrachter hat jetzt einen Download-Button und erkennt zusätzlich C#, C++, HTML, PHP, Java, XML, JSON, YAML und Python",
                "Tippt man auf eine zitierte Nachricht, wird die Originalnachricht beim Hinspringen kurz hervorgehoben",
                "Circle- und normale Videos im Chat werden nach dem ersten Abspielen nicht mehr schwarz und lassen sich weiterhin abspielen",
            ),
            shortSummary = "Code-Anhänge im Chat, verbesserter Code-Betrachter, Zitat-Hervorhebung und Video-Wiedergabe-Fix"
        ),

        ChangelogEntry(
            version = "10.4.167",
            title = "Anruf-Benachrichtigungen & Jump or Die verbessert",
            items = listOf(
                "Eingehende Anrufe werden auf der SmartWatch (WearOS) jetzt korrekt als Anruf mit Annehmen/Ablehnen angezeigt statt nur als normale Nachricht",
                "Beim Videoanruf erscheint beim Angerufenen nicht mehr kurz nacheinander erst ein Banner und dann die Vollbildansicht – je nach Bildschirmzustand nur noch eine der beiden",
                "Bricht der Anrufer ab, weil der Angerufene nicht erreichbar ist, hört es bei diesem jetzt sofort auf zu klingeln, statt bis zum eigenen Timeout weiterzulaufen",
                "Jump or Die: Schwarzes Loch, Schutzschild und Blase sind jetzt 50% größer und leichter zu treffen",
            ),
            shortSummary = "WearOS-Anrufanzeige, Anruf-Benachrichtigung entdoppelt, zuverlässiger Anrufabbruch, größere Items in Jump or Die"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
