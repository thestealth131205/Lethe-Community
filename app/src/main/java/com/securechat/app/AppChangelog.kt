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
            version = "10.4.186",
            title = "Absturz durch Speichermangel im Dauer-Hintergrunddienst behoben",
            items = listOf(
                "Fehler behoben: Der Nachrichtendienst berechnete für die Tag/Nacht-Pingfrequenz bei jedem Ping erneut die aktuelle Uhrzeit inkl. kompletter Zeitzonendaten – über viele Stunden Laufzeit konnte das zu einem Speichermangel-Absturz führen. Der Wert wird jetzt 5 Minuten zwischengespeichert",
            ),
            shortSummary = "Stabilitäts-Fix gegen seltenen Speicher-Absturz im Hintergrunddienst"
        ),

        ChangelogEntry(
            version = "10.4.185",
            title = "Klarere Fehlermeldung beim Login & Admin-Passwortänderung",
            items = listOf(
                "Fehler behoben: Beim Login erschien bei jedem Fehlschlag nur \"Login fehlgeschlagen\" ohne Grund – jetzt wird die tatsächliche Ursache vom Server angezeigt (z.B. falsches Passwort, unbekannte Nummer)",
                "Fehler behoben: Ein im Backend-Bereich neu gesetztes Nutzer-Passwort mit unsichtbaren Leerzeichen am Rand führte beim Login zu einem Fehlschlag – Passwörter werden jetzt vor dem Login-Versuch bereinigt",
            ),
            shortSummary = "Klarere Fehlermeldungen beim Login, Passwort-Bugfix"
        ),

        ChangelogEntry(
            version = "10.4.184",
            title = "Schnelleres Chat-Laden & Weiterleiten an Gruppen",
            items = listOf(
                "Lange Chats laden schneller und stabiler: Es werden zunächst nur Nachrichten der letzten 3 Tage angezeigt, ältere erscheinen beim Hochscrollen",
                "Fehler behoben: Weitergeleitete Bilder und Medien kamen in Gruppen nicht bei den Mitgliedern an",
                "Fehler behoben: Absturz bei eingehendem Anruf auf Geräten mit eingeschränkten Vollbild-Benachrichtigungen",
                "Media Player: Landscape-Ansicht mit allen Bedienelementen und Mini-Player in Detailansichten",
            ),
            shortSummary = "Chat lädt schneller, Weiterleiten an Gruppen repariert"
        ),

        ChangelogEntry(
            version = "10.4.183",
            title = "Geräte-Verifizierung: SMS-Alternative & P2P-Status-Fix",
            items = listOf(
                "Neu: Im Freigabe-Dialog für ein neues Gerät gibt es jetzt unten den Button \"Andere Optionen\", um statt der Freigabe über ein anderes Gerät stattdessen einen SMS-Code anzufordern",
                "Fehler behoben: Der P2P-Verbindungsstatus (Ampel neben dem Kontaktnamen) verschwand nach einer Neuanmeldung oder Geräte-Verifizierung, obwohl P2P für den Account aktiviert war – die Einstellung wird jetzt korrekt vom Server übernommen",
            ),
            shortSummary = "Neue SMS-Alternative bei Geräte-Verifizierung, P2P-Status-Anzeige repariert"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
