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

        ChangelogEntry(
            version = "10.4.137",
            title = "Sicherheits-Updates & Nearby-Blockierung",
            items = listOf(
                "Behoben: Blockierte Nutzer konnten in Lethe Nearby trotzdem im Umkreis-Feed erscheinen, Profile ansehen oder dich liken \u2013 Blockierungen werden jetzt in Nearby vollst\u00e4ndig durchgesetzt",
                "Neu: Bei der Eingabe des SMS-Codes zeigt die App jetzt einen Countdown \u2013 danach kannst du den Code erneut anfordern oder ein Problem melden",
                "Verbessert: SMS-Codes sind jetzt zus\u00e4tzlich pro Telefonnummer gegen Missbrauch abgesichert",
                "Behoben: Der Preis f\u00fcr 3D-Datei-K\u00e4ufe im Chat wird jetzt ausschlie\u00dflich vom Server festgelegt statt vom Ger\u00e4t des K\u00e4ufers",
            ),
            shortSummary = "Nearby-Blockierungen werden jetzt vollst\u00e4ndig durchgesetzt, dazu ein neuer SMS-Code-Timer und mehr Schutz gegen Missbrauch"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
