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
            version = "10.4.142",
            title = "Cast-Fix, Gruppenbilder & Chat-Backup",
            items = listOf(
                "Behoben: Musik-/Video-Streaming per Cast blieb weiterhin manchmal auf \u201epausiert\u201c stehen \u2013 dem Wiedergabe-Befehl fehlte eine notwendige Sitzungskennung, die jetzt korrekt mitgeschickt wird",
                "Behoben: Gruppenbilder in der \u201eKontakt teilen\u201c-Liste wurden bei jedem \u00d6ffnen neu geladen statt aus dem Zwischenspeicher \u2013 Bilder werden jetzt korrekt zwischengespeichert",
                "Behoben: Gel\u00f6schte Chat-Nachrichten wurden beim n\u00e4chsten App-Start wieder nachgeladen \u2013 das L\u00f6schen wird jetzt auch mit dem Server synchronisiert",
                "Chat-Backup erweitert: automatischer Hintergrund-Abgleich bestehender Nachrichten sowie vollst\u00e4ndiges L\u00f6schen der Server-Kopie beim Deaktivieren",
                "Diverse Stabilit\u00e4tsverbesserungen bei Gruppen-Videoanrufen und der Konto-Anmeldung",
            ),
            shortSummary = "Fix f\u00fcr Cast-Streaming, Gruppenbilder beim Teilen und Chat-L\u00f6schen-Synchronisation"
        ),

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

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
