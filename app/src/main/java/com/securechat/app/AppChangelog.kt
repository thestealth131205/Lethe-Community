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
            version = "10.4.202",
            title = "Bildqualität, Medien-Übersicht & Reaktionen im Chat verbessert",
            items = listOf(
                "Fotos unter 2 Megapixel werden beim Versenden nicht mehr verkleinert – die Originalauflösung bleibt erhalten",
                "Bilder und Videos landen nicht mehr doppelt auf dem Gerät – eine außerhalb der App nutzbare Kopie entsteht jetzt nur noch beim Tippen auf 'In Galerie speichern'",
                "Reaktionen auf Nachrichten in 1-zu-1-Chats verschwanden manchmal wieder oder kamen beim Chatpartner gar nicht erst an – behoben",
                "Die Medien-Übersicht eines Chats (Tipp auf Profil- bzw. Gruppenbild) zeigt jetzt wirklich alle gesendeten Fotos und Videos und lädt bei Bedarf weitere ältere Medien nach",
                "Im Lethe Media Player funktionierte 'Wiederholen' bei eingeschalteten Übergängen nicht richtig – behoben; außerdem unterstützt jetzt auch die Chromecast-Wiedergabe das Wiederholen",
            ),
            shortSummary = "Originalauflösung bei kleinen Fotos, kein doppeltes Speichern, Reaktionen-Fix, vollständige Medien-Übersicht, Wiederholen-Fix im Media Player"
        ),

        ChangelogEntry(
            version = "10.4.201",
            title = "Gruseligere Halloween-Reaktion bei Videoanrufen",
            items = listOf(
                "Die Halloween-Reaktion (Kürbiskopf) bei Videoanrufen wurde gruseliger: dunkle Wolken ziehen im Hintergrund vorbei, ein Totenschädel lauert in der Ecke, und zwei Blitze lassen den Bildschirm kurz aufblitzen",
            ),
            shortSummary = "Halloween-Reaktion bei Videoanrufen jetzt mit Wolken, Totenschädel und Blitzen"
        ),

        ChangelogEntry(
            version = "10.4.200",
            title = "Sound für Halloween-Reaktion bei Videoanrufen",
            items = listOf(
                "Die neue Halloween-Reaktion (Kürbiskopf) bei Videoanrufen hat jetzt einen passenden gruseligen Sound",
            ),
            shortSummary = "Halloween-Reaktion bei Videoanrufen hat jetzt einen Sound"
        ),

        ChangelogEntry(
            version = "10.4.199",
            title = "Neue Halloween-Animation & Lethe Media Player Fix",
            items = listOf(
                "Neue animierte Halloween-Reaktion bei Videoanrufen: ein leuchtender, flackernder Kürbiskopf mit Fledermäusen und Mond",
                "Die Halloween- und Weihnachts-Überraschung in der Kontaktliste sowie im Chat wurde durch eine deutlich hochwertigere, filmreife Animation ersetzt",
                "Im Lethe Media Player öffnete ein Tipp auf den Mini-Player manchmal nicht den Vollbild-Player, wenn man sich gerade auf einem Künstler-Profil befand – behoben",
            ),
            shortSummary = "Neue Halloween-Reaktion bei Videoanrufen, hochwertigere Fest-Animationen, Mini-Player-Fix im Media Player"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
