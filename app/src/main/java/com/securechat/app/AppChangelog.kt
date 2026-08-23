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
            version = "10.4.154",
            title = "Media Player, Videocalls & Sparks verbessert",
            items = listOf(
                "Lethe Media Player: Wiedergabe bricht beim Casten nach einem Lied nicht mehr ab; Shuffle/Wiederholung sind jetzt auch beim Casten nutzbar, Lautstärketasten regeln direkt das Cast-Gerät",
                "Geteilte Musik-Links sind jetzt kurz und funktionieren beim Favorisieren sofort, ohne den Song erneut zu öffnen",
                "Videocalls: Statt nur \u201eHintergrund unscharf\u201c gibt es jetzt eine echte Hintergrundauswahl (unscharf, Farbverläufe oder eigenes Galeriebild) mit weichen Kanten",
                "Sparks: Beim Erstellen füllt ein vergrößerter, unscharfer Hintergrund den freien Bildschirmbereich aus; Tonaussetzer bei kurzer Musik und zu langer Bilddauer behoben",
            ),
            shortSummary = "Zuverlässigeres Casten, echte Hintergrundauswahl bei Videocalls und verbesserte Sparks"
        ),

        ChangelogEntry(
            version = "10.4.153",
            title = "Benachrichtigungen, Support & Sparks verbessert",
            items = listOf(
                "Chat-Benachrichtigungen werden jetzt auch beim Wegwischen sofort als zugestellt/gelesen markiert",
                "Deutlich mehr Benachrichtigungen (Anrufe, Nearby, Spark- & Diskussions-Kommentare, Likes, Support-Antworten, Spiele-Rangliste, Kreator-Bewerbungen) kommen jetzt zuverlässig an \u2013 auch ohne Google-Dienste",
                "Neu: Antwortfunktion für Support-Tickets und Kreator-Bewerbungen mit eigener Status-Übersicht \u201eMeine Bewerbung\u201c",
                "Sparks: Bilder/Videos werden beim Erstellen nicht mehr verzerrt \u2013 neue Formatauswahl mit Zoom & Verschieben; im Feed werden nicht-vertikale Formate proportional korrekt dargestellt",
            ),
            shortSummary = "Zuverlässigere Benachrichtigungen, Support-Antworten und unverzerrte Sparks"
        ),

        ChangelogEntry(
            version = "10.4.152",
            title = "Weiterleiten, Reaktionen & Zuverl\u00e4ssigkeit",
            items = listOf(
                "Beim Weiterleiten werden Kontakte und Gruppen jetzt nach H\u00e4ufigkeit sortiert, Gruppenbilder angezeigt und angepinnte Chats erscheinen ganz oben",
                "Die Emoji-Reaktionsleiste wird jetzt unter der Nachricht angezeigt, wenn oben kein Platz ist \u2013 so ist sie immer sofort sichtbar",
                "Die App bleibt zuverl\u00e4ssiger im Hintergrund verbunden und startet nach einem Ger\u00e4te-Neustart automatisch wieder \u2013 die Meldung \u201eDu k\u00f6nntest neue Nachrichten haben\u201c erscheint nicht mehr unn\u00f6tig",
            ),
            shortSummary = "Besseres Weiterleiten, gut sichtbare Reaktionsleiste und zuverl\u00e4ssigere Hintergrundverbindung"
        ),

        ChangelogEntry(
            version = "10.4.151",
            title = "Benachrichtigungen & Live-Standort verbessert",
            items = listOf(
                "Behoben: Bereits gelesene Nachrichten wurden nach einem Neuverbinden manchmal erneut als neue Nachricht angezeigt",
                "Behoben: Benachrichtigungen zeigten in seltenen Fällen unverständlichen, noch verschlüsselten Text an \u2013 jetzt erscheint stattdessen \u201eNeue Nachricht\u201c, bis der Inhalt entschlüsselt werden kann",
                "Behoben: Der geteilte Live-Standort aktualisiert sich jetzt zuverlässig fortlaufend statt bei einem alten Stand stehen zu bleiben",
            ),
            shortSummary = "Zuverlässigere Benachrichtigungen und fortlaufend aktualisierter Live-Standort"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
