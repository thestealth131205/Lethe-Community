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

        ChangelogEntry(
            version = "10.4.136",
            title = "OpenStreetMap-Karten & Video-Editor-Verbesserungen",
            items = listOf(
                "Neu: Die Karten f\u00fcr Live-Standort, Standort-Teilen und Profil-Umkreis nutzen jetzt OpenStreetMap",
                "Behoben: Beim Anpassen der Helligkeit im Video-Editor blieb die Vorschau stehen und lie\u00df sich nicht mehr fortsetzen \u2013 die Vorschau l\u00e4uft jetzt w\u00e4hrend der Anpassung weiter",
                "Behoben: Beim Senden eines Videos aus dem Video-Editor verschwand die Video-Nachricht in manchen F\u00e4llen spurlos \u2013 gro\u00dfe Videos werden jetzt zuverl\u00e4ssig verarbeitet und gesendet",
                "Behoben: Gruppen zeigten in der App-internen Teilen-Liste kein Gruppenbild, sondern nur einen Platzhalter \u2013 jetzt wird das echte Gruppenbild angezeigt",
                "Behoben: Der QR-Code bzw. Einladungslink im Konto lud in manchen F\u00e4llen endlos \u2013 bei einem Fehler erscheint jetzt ein Hinweis mit Wiederholen-Option",
            ),
            shortSummary = "Karten nutzen jetzt OpenStreetMap, der Video-Editor wurde stabiler und Gruppenbilder erscheinen in der Teilen-Liste"
        ),

        ChangelogEntry(
            version = "10.4.135",
            title = "Video-Editor mit Live-Vorschau & stabilere Videocalls",
            items = listOf(
                "Neu: Die Vorschau im Video-Editor zeigt Anpassungen wie Helligkeit, Kontrast und S\u00e4ttigung jetzt in Echtzeit an",
                "Neu: Tippe im Video-Editor auf die laufende Vorschau, um das Video anzuhalten \u2013 erneutes Antippen setzt die Wiedergabe fort",
                "Verbessert: Mit dem Video-Editor erstellte Videos werden beim Senden nicht mehr ein zweites Mal komprimiert \u2013 das Senden geht schneller und die Qualit\u00e4t bleibt erhalten",
                "Behoben: Ein Absturz im Videocall, der beim Beenden des Anrufs auftreten konnte, wurde behoben",
            ),
            shortSummary = "Der Video-Editor zeigt Anpassungen live an und erstellte Videos werden nicht mehr doppelt komprimiert \u2013 dazu ein Absturz-Fix im Videocall"
        ),

        ChangelogEntry(
            version = "10.4.134",
            title = "Amazon-Vorschau & Videocall-Verbesserungen",
            items = listOf(
                "Behoben: Amazon-Kurzlinks (amzn.eu) zeigten im Chat keine Produktvorschau mit Bild \u2013 stattdessen erschien nur ein Platzhalter. Die Produktvorschau inkl. Bild wird jetzt korrekt angezeigt",
                "Behoben: Beim Antippen der Steuerungsleiste im Videocall wurde versehentlich das Haupt- und Kleinbild umgeschaltet \u2013 Taps auf die Leiste bleiben jetzt wirkungslos",
                "Verbessert: Der Auflegen-Button im Videocall hat einen kleineren Ber\u00fchrbereich, damit du nicht mehr versehentlich auflegst",
                "Verbessert: Die Bedienelemente im Videocall wurden neu angeordnet \u2013 mit direktem Zugriff auf Emoji-Reaktionen",
            ),
            shortSummary = "Amazon-Kurzlinks zeigen wieder die Produktvorschau, und der Videocall wurde gegen versehentliches Umschalten und Auflegen abgesichert"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
