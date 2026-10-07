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
            version = "10.4.196",
            title = "Anruf-Benachrichtigungen & Lautstärke-Warnung",
            items = listOf(
                "Wird die Vollbild-Anrufanzeige versehentlich mit der Zurück-Taste weggedrückt, bleibt der Anruf jetzt als kompakte Benachrichtigung mit Annehmen/Ablehnen erreichbar, statt zu verschwinden",
                "Ist die Lautstärke während eines Anrufs zu niedrig oder stumm, wird jetzt ein animierter Hinweis zum Lauterstellen eingeblendet – ebenso bei Sprachnachrichten und Status-Beiträgen mit Ton",
                "FriendsMix-Kontaktliste im Lethe Media Player lädt jetzt zuverlässiger (Fehler werden angezeigt statt stillschweigend als 'keine Kontakte')",
            ),
            shortSummary = "Anruf-Benachrichtigung bleibt erreichbar, Warnung bei zu leiser Lautstärke"
        ),

        ChangelogEntry(
            version = "10.4.195",
            title = "Medien-Kompression & automatischer Cache-Reset",
            items = listOf(
                "Im Chat gesendete Bilder werden jetzt etwas kleiner skaliert und Videos maximal in Full-HD (1920x1080) mit leicht erhöhter Kompression übertragen – spart deutlich Speicherplatz bei weiterhin guter Qualität",
                "Nach diesem Update wird der Medien-Zwischenspeicher der App einmalig automatisch geleert, damit alte, unkomprimierte Dateien nicht unnötig Platz belegen",
            ),
            shortSummary = "Kleinere Bilder/Videos beim Senden, einmaliger Cache-Reset nach Update"
        ),

        ChangelogEntry(
            version = "10.4.194",
            title = "Cache-Verwaltung optimiert",
            items = listOf(
                "Bilder wurden beim Laden im Chat doppelt zwischengespeichert – das belegte bei mehreren aktiven Chats unnötig viel Speicherplatz, jetzt nur noch einmal",
                "Der Zwischenspeicher für Medien wird jetzt automatisch alle 6 Stunden aufgeräumt statt nur beim Schließen der App, und das Limit wurde von 500 MB auf 300 MB reduziert",
            ),
            shortSummary = "Speicherplatzbedarf des Medien-Caches deutlich reduziert"
        ),

        ChangelogEntry(
            version = "10.4.193",
            title = "Chat-Eingabe & Speicherverbrauch verbessert",
            items = listOf(
                "Beim Tippen längerer Nachrichten im Chat konnte das Textfeld spürbar stocken – die Eingabe reagiert jetzt wieder sofort",
                "Speicherverbrauch im Hintergrund reduziert, der bei langer Nutzung des Messengers zu Abstürzen führen konnte",
            ),
            shortSummary = "Chat-Eingabe-Stocken behoben, Speicherverbrauch reduziert"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
