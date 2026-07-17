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

        ChangelogEntry(
            version = "10.4.133",
            title = "Gruppen-Chats im Web-Chat entschlüsseln jetzt korrekt",
            items = listOf(
                "Behoben: Gruppen-Nachrichten im Web-Chat konnten nicht entschlüsselt werden (\u201eGruppen-Entschlüsselung fehlgeschlagen\u201c bzw. \u201ekein Schlüssel\u201c) \u2013 dein Handy überträgt die Gruppen-Schlüssel jetzt beim Verbinden sicher an den Web-Chat",
                "Verbessert: Der Web-Chat nutzt jetzt denselben Gruppen-Schlüssel wie dein Handy, sodass deine im Web geschriebenen Gruppen-Nachrichten von allen Mitgliedern gelesen werden und die Verlaufshistorie korrekt erscheint",
            ),
            shortSummary = "Web-Chat entschlüsselt Gruppen-Nachrichten jetzt korrekt \u2013 die Gruppen-Schlüssel werden sicher vom Handy übertragen"
        ),

        ChangelogEntry(
            version = "10.4.132",
            title = "Gruppen-Benachrichtigungen, Tipp-Anzeige & neue Playlist-Funktionen",
            items = listOf(
                "Behoben: Gruppen-Nachrichten kamen bei ausgeschaltetem Display teils nicht als Benachrichtigung an \u2013 solange du keinen Chat aktiv ge\u00f6ffnet hast, wachst du jetzt zuverl\u00e4ssig f\u00fcr neue Gruppen-Nachrichten auf",
                "Behoben: Die \u201etippt\u2026\u201c-Anzeige einer Gruppe erschien f\u00e4lschlich auch im Einzelchat der Person \u2013 Gruppen- und Einzel-Tippen werden jetzt sauber getrennt angezeigt",
                "Behoben: In seltenen F\u00e4llen wurde eine Benachrichtigung dauerhaft unterdr\u00fcckt, wenn ihre Anzeige unterbrochen wurde \u2013 die Fallback-Benachrichtigung springt jetzt zuverl\u00e4ssig ein",
                "Neu: Eigene Playlists im Lethe Medie Player lassen sich jetzt \u201eals Mix\u201c abspielen \u2013 mit nahtlosen 15-Sekunden-\u00dcberg\u00e4ngen wie beim Family/FriendsMix",
                "Neu: Kuratierte Lethe-Playlists erscheinen jetzt f\u00fcr alle Nutzer direkt auf der Startseite des Medie Players",
                "Neu: In Android Auto kannst du den laufenden Titel jetzt direkt per Herz-Symbol liken",
            ),
            shortSummary = "Zuverl\u00e4ssige Gruppen-Benachrichtigungen bei ausgeschaltetem Display, korrekte \u201etippt\u2026\u201c-Anzeige und neue Playlist-Funktionen im Medie Player"
        ),

        ChangelogEntry(
            version = "10.4.131",
            title = "FriendsMix als echter DJ-Mix & Android Auto endlich sichtbar",
            items = listOf(
                "Neu: Die FriendsMix-Playlist im Lethe Medie Player f\u00fchlt sich jetzt wie ein echter Mix an \u2013 die Lieder starten mitten im Song und blenden sanft \u00fcber 15 Sekunden ineinander \u00fcber, unabh\u00e4ngig von deinen \u00dcberblend-Einstellungen",
                "Neu: Der FriendsMix ordnet die Titel jetzt nach Geschwindigkeit (Beat/BPM) an, sodass der Rhythmus von Song zu Song nur sanft steigt oder f\u00e4llt statt abrupt zu wechseln",
                "Behoben: Der Lethe Medie Player erscheint jetzt zuverl\u00e4ssig in Android Auto \u2013 mit einem \u00fcbersichtlichen Men\u00fc aus Bibliothek, Favoriten, Playlists, FriendsMix und Lokal, und die laufende Wiedergabe wird korrekt angezeigt",
                "Neu: In Android Auto startet ein Antippen eines Titels jetzt gleich die ganze Kategorie als Warteschlange",
                "Verbessert: Gr\u00f6\u00dfere Musikdateien (bis 300\u00a0MB) lassen sich jetzt in die Lethe-Bibliothek hochladen \u2013 auch hochaufl\u00f6sende Formate wie FLAC oder WAV",
            ),
            shortSummary = "FriendsMix mixt jetzt mit sanften \u00dcberg\u00e4ngen und Beat-Sortierung, der Medie Player erscheint zuverl\u00e4ssig in Android Auto und gr\u00f6\u00dfere Musikdateien lassen sich hochladen"
        ),

    )




    /** Neuester Changelog-Eintrag (für Update-Dialog). */
    val latestEntry: ChangelogEntry? get() = entries.firstOrNull()

    /** Kurztext des neuesten Updates (für Update-Dialog-Fallback). */
    val latestShort: String get() = latestEntry?.shortSummary ?: "Neue Version verfügbar."
}
