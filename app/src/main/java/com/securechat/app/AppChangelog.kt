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
            version = "10.4.198",
            title = "HDR-Unterstützung & Absturz-Fix",
            items = listOf(
                "Bilder und Videos werden jetzt in HDR angezeigt, sofern Gerät und Bildschirm das unterstützen – im Vollbildmodus im Chat, bei Videoanrufen, Livestreams und im Sparks-Feed",
                "Angepinnte Bilder in der Übersicht 'Angepinnte Elemente' luden das Originalfoto ungekürzt in den Speicher – behoben, das konnte bei großen Fotos zu einem Absturz durch Speichermangel führen",
            ),
            shortSummary = "HDR bei Bildern/Videos, Videoanrufen, Livestreams & Sparks; Absturz-Fix bei angepinnten Bildern"
        ),

        ChangelogEntry(
            version = "10.4.197",
            title = "Angepinnte Elemente & Reaktionen in Einzelchats",
            items = listOf(
                "Links, Bilder, Videos, Sprachnachrichten, Dateien und Beiträge können jetzt in jedem Chat angepinnt werden – im Auswahlmenü einer Nachricht erscheint 'Anpinnen'/'Lösen'. Über das 3-Punkte-Menü ('Angepinnte Elemente') gibt es eine Übersicht mit Vorschau (Bild/Video klein mit Vollbild-Tipp, Sprachnachricht direkt abspielbar), ein Tipp springt zur Stelle im Chat",
                "Reaktionen (Emojis) auf Nachrichten funktionieren in Einzelchats jetzt genauso wie in Gruppen – mit korrekten Zählern bei mehreren Reaktionen, statt nur einer einzelnen überschriebenen Reaktion",
                "Antworten direkt aus einer Benachrichtigung wurde zuverlässiger: War Lethe lange nicht geöffnet, kam die Antwort bisher erst beim nächsten App-Start an – jetzt wird sie sofort verschickt",
            ),
            shortSummary = "Angepinnte Elemente in Chats, Reaktions-Zähler auch in Einzelchats, Antwort aus Benachrichtigung zuverlässiger"
        ),

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

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
