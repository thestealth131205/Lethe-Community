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
            version = "10.4.157",
            title = "Stabilitätsverbesserung bei Circle-Videos",
            items = listOf(
                "Interner Umbau: Der Anzeige-Code für Circle-Videos in Chats wurde in eine eigene Komponente ausgelagert, um seltene Abstürze beim Kompilieren auf manchen Geräten zu vermeiden",
            ),
            shortSummary = "Interne Stabilitätsverbesserung bei Circle-Videos"
        ),

        ChangelogEntry(
            version = "10.4.156",
            title = "Circle-Videos & Lethe Media Player verbessert",
            items = listOf(
                "Circle-Videos: Ein antippbares Play-Symbol zeigt jetzt immer an, ob ein Video gerade läuft oder wartet \u2013 kein scheinbar toter schwarzer Kreis mehr; nach dem Aufklappen mit Ton erscheint ein Wiederholen-Button",
                "Circle-Videos stürzen nicht mehr ab, wenn mehrere gleichzeitig sichtbar sind \u2013 die begrenzten Video-Decoder des Geräts werden jetzt sauber verwaltet",
                "Lethe Media Player: Shuffle und Wiederholung lassen sich jetzt auch in Android Auto direkt über eigene Buttons umschalten",
            ),
            shortSummary = "Stabilere Circle-Videos und Shuffle/Wiederholung in Android Auto"
        ),

        ChangelogEntry(
            version = "10.4.155",
            title = "Benachrichtigungen & Videocalls verbessert",
            items = listOf(
                "Status-Benachrichtigungen und Herz-Reaktionen auf deinen Status kommen jetzt auch ohne Google-Dienste zuverl\u00e4ssig an",
                "Wenn jemand auf deine Nachricht reagiert, wirst du jetzt benachrichtigt (z.\u202fB. \u201eAlex hat auf deine Nachricht mit \u2764\ufe0f reagiert\u201c)",
                "Behoben: Videocall st\u00fcrzte beim Aktivieren von Hintergrund-Unsch\u00e4rfe oder Farbverlauf ab",
                "Behoben: Nach einem abgest\u00fcrzten Videocall wurde der Ton dauerhaft f\u00e4lschlich \u00fcber die H\u00f6rmuschel ausgegeben",
                "Admins werden jetzt auch ohne Google-Dienste \u00fcber neue Support-Tickets benachrichtigt",
            ),
            shortSummary = "Zuverl\u00e4ssigere Benachrichtigungen und stabilere Videocalls"
        ),

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

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
