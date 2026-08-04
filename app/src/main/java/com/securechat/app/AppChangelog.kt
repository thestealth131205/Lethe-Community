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
            version = "10.4.141",
            title = "Chat-Backup & weiterer Cast-Fix",
            items = listOf(
                "Neu: Optionales Chat-Backup in den App-Einstellungen \u2013 speichert Nachrichten zus\u00e4tzlich lesbar auf dem Server, damit sie bei Ger\u00e4teverlust wiederherstellbar sind (bewusst opt-in, da dies das Ende-zu-Ende-Prinzip f\u00fcr diese Nachrichten aufhebt; l\u00e4sst sich jederzeit wieder deaktivieren, wobei bereits gesicherte Nachrichten dann vom Server entfernt werden)",
                "Behoben: Musik-Cast auf Lautsprecher blieb in manchen F\u00e4llen weiterhin auf \u201epausiert\u201c stehen, wenn zuvor ein Spark-Cast abgebrochen oder mehrere Ger\u00e4te-Apps gleichzeitig aktiv waren \u2013 Ger\u00e4te-Auswahl und Wiedergabe-Ziel werden jetzt zuverl\u00e4ssig zur\u00fcckgesetzt",
            ),
            shortSummary = "Neues optionales Chat-Backup, dazu ein weiterer Fix f\u00fcr zuverl\u00e4ssigeres Musik-Casting"
        ),

        ChangelogEntry(
            version = "10.4.140",
            title = "Musik-Cast-Fix f\u00fcr Lautsprecher",
            items = listOf(
                "Behoben: Musik-Streaming per Cast auf reine Audio-Lautsprecher (z.B. Google/Nest Mini) blieb auf \u201epausiert\u201c stehen, obwohl die Verbindung erfolgreich war \u2013 Musik wird jetzt \u00fcber Googles Standard-Empf\u00e4nger gestreamt und startet zuverl\u00e4ssig",
            ),
            shortSummary = "Musik-Streaming per Cast auf Lautsprecher funktioniert jetzt zuverl\u00e4ssig"
        ),

        ChangelogEntry(
            version = "10.4.139",
            title = "Absturz-Fix beim App-Start",
            items = listOf(
                "Behoben: Die App st\u00fcrzte bei manchen Nutzern direkt beim Start ab, nachdem der neue Konto-Switcher aus 10.4.138 hinzugekommen war",
            ),
            shortSummary = "Wichtiger Fix f\u00fcr einen Absturz beim App-Start"
        ),

        ChangelogEntry(
            version = "10.4.138",
            title = "Account-Switcher & Mehr-Konten-Fix",
            items = listOf(
                "Neu: Konto-Switcher \u2013 wechsle im Login-Bildschirm oder unter Konto \u2192 Konto wechseln zwischen mehreren Lethe-Accounts auf diesem Ger\u00e4t, ohne das Passwort erneut einzugeben",
                "Behoben: Beim Anlegen eines zweiten Accounts auf demselben Ger\u00e4t wurden manchmal noch Kontakte und Daten des vorherigen Accounts angezeigt \u2013 Konten werden jetzt zuverl\u00e4ssig sauber getrennt",
                "Behoben: Videocalls in Gruppen konnten \u201enicht erreichbar\u201c melden und abbrechen, wenn Teilnehmer im Energiesparmodus waren \u2013 die Anruf-Benachrichtigung wird jetzt zuverl\u00e4ssig zugestellt",
                "Behoben: Ein seltener Absturz beim Empfang von Nachrichten im Hintergrund wurde behoben",
            ),
            shortSummary = "Neuer Konto-Switcher zum Wechseln zwischen mehreren Accounts ohne Passwort, dazu ein Fix f\u00fcr sauber getrennte Konten auf einem Ger\u00e4t"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
