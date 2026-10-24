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
            version = "10.4.207",
            title = "Iris ersetzt FCM als Push-Weg",
            items = listOf(
                "Lethe Messenger nutzt jetzt in beiden Versionen (Play Store und F-Droid/FOSS) den eigenen, offenen Iris-Push-Dienst statt Google FCM für Benachrichtigungen und Anrufe – Google Play Services werden dafür nicht mehr benötigt",
                "Neuer Schalter 'Nur Iris-Push' im Backend-Bereich für Admins, um FCM als Fallback komplett zu deaktivieren",
                "Fehler behoben: Die Iris-Push-Endpunkte waren über die öffentliche Adresse nicht erreichbar (fehlender Eintrag in der Server-Weiterleitung), wodurch sich kein Token erzeugen ließ",
            ),
            shortSummary = "Push-Zustellung läuft jetzt über den eigenen Iris-Dienst statt Google FCM (beide Versionen)"
        ),

        ChangelogEntry(
            version = "10.4.206",
            title = "Iris Push & zuverlässigere Reaktions-Benachrichtigungen",
            items = listOf(
                "Neu: Iris Push im Backend-Bereich – ein offener, FCM-unabhängiger Push-Weg für eigene Geräte/Apps, inklusive Token-Verwaltung direkt in der App",
                "Fehler behoben: Reagierten mehrere Personen kurz hintereinander auf eigene Nachrichten, überschrieb eine neue Benachrichtigung die vorherige ungesehen, statt sich daneben anzuzeigen",
                "Zustellung von Gruppen-Reaktions-Benachrichtigungen bei geschlossener App verbessert",
            ),
            shortSummary = "Neuer Iris-Push-Dienst im Backend-Bereich, Reaktions-Benachrichtigungen verschwinden nicht mehr ungesehen"
        ),

        ChangelogEntry(
            version = "10.4.205",
            title = "Neues Feuerwerk-Lumi & Weihnachts-Reaktion bei Videoanrufen",
            items = listOf(
                "Neues Feuerwerk-Lumi zum Versenden in 1-zu-1- und Gruppenchats: mehrere bunte Raketen (Rot, Grün, Pink, Blau) steigen auf und zerplatzen kugelförmig",
                "Neue Weihnachts-Reaktion bei Videoanrufen: ein Tannenbaum mit Lichterkette und ein vorbeifliegender Schlitten mit Rentier im Schneefall",
                "Die Weihnachts-Glückwunschkarte in der Kontaktliste lässt sich jetzt wie die anderen Saisonkarten direkt teilen",
                "Live-Emoji-Animationen bei Video- und Sprachanrufen wurden im Querformat bzw. auf Tablets teilweise über den oberen Bildschirmrand hinaus unsichtbar – jetzt passen sie sich der verfügbaren Höhe an",
            ),
            shortSummary = "Feuerwerk-Lumi, Weihnachts-Reaktion bei Anrufen inkl. teilbarer Grußkarte, Emoji-Animationen im Querformat/Tablet behoben"
        ),

        ChangelogEntry(
            version = "10.4.204",
            title = "Benachrichtigung bei Reaktionen & Media-Player-Neuerungen",
            items = listOf(
                "Reagiert jemand auf eine eigene Nachricht (auch in Gruppen), gibt es jetzt eine Benachrichtigung mit Name und Emoji – ein Tipp darauf führt direkt zum passenden Chat",
                "Dabei einen seit einigen Tagen bestehenden Fehler behoben, durch den solche Benachrichtigungen bei geschlossener App manchmal gar nicht ankamen",
                "Im Lethe Media Player für Windows/Desktop gibt es jetzt Zufallswiedergabe und Wiederholen (auch beim Casten über Chromecast)",
                "In der Lethe-Musikbibliothek waren über 'Künstler scannen' verknüpfte Titel für andere Nutzer teils unsichtbar – behoben",
            ),
            shortSummary = "Benachrichtigung bei Reaktionen auf eigene Nachrichten, Zufallswiedergabe/Wiederholen im Desktop-Player, Bibliotheks-Fix"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
