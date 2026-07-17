package com.securechat.app.data.local

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// SparkFilterEngine
//
// Definiert alle verfügbaren Video-Filter für den Spark-Editor.
//
// Jeder Filter besitzt:
//   - [ffmpegVf]        → Der `-vf`-Filterstring für FFmpeg beim endgültigen Export.
//   - [overlayColor]    → Eine Compose-Farbe, die als halbtransparentes Overlay für
//                         die Live-Vorschau im Editor über den PlayerView gelegt wird.
//   - [overlayAlpha]    → Deckkraft des Overlays (0f = unsichtbar, 1f = vollständig).
//   - [saturation]      → Sättigungsmultiplikator für den Vorschau-Tint (1f = normal).
//   - [displayName]     → Anzeigename für die Filter-Leiste.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Enum aller unterstützten Spark-Filter.
 * Die Reihenfolge bestimmt die Reihenfolge in der horizontalen Filter-Leiste.
 */
enum class SparkFilterId {
    NONE,
    VIVID,
    NOIR,
    CINEMATIC,
    LOFI,
    RETRO,
    WARM,
    COLD
}

/**
 * Vollständige Filterkonfiguration für einen [SparkFilterId]-Wert.
 *
 * @param id           Referenz zum [SparkFilterId]-Enum.
 * @param displayName  Anzeigename in der UI.
 * @param ffmpegVf     FFmpeg `-vf`-Filterstring für den Export (leerer String = kein Filter).
 * @param overlayColor Farbe des Live-Vorschau-Overlays in Compose.
 * @param overlayAlpha Deckkraft des Overlays (0f – 0.5f).
 * @param labelColor   Farbe des Filter-Labels in der Leiste (Kontrast auf overlayColor).
 */
data class SparkFilterConfig(
    val id: SparkFilterId,
    val displayName: String,
    val ffmpegVf: String,
    val overlayColor: Color,
    val overlayAlpha: Float,
    val labelColor: Color = Color.White
)

/**
 * Zentrales Register aller Filter-Konfigurationen.
 * Zugriff via [SparkFilterEngine.forId] oder [SparkFilterEngine.allFilters].
 */
object SparkFilterEngine {

    // ─── Filter-Definitionen ──────────────────────────────────────────────────

    /**
     * NONE – Kein Filter. Originalvideo ohne Bearbeitung.
     */
    private val NONE = SparkFilterConfig(
        id = SparkFilterId.NONE,
        displayName = "Original",
        ffmpegVf = "",
        overlayColor = Color.Transparent,
        overlayAlpha = 0f,
        labelColor = Color.White
    )

    /**
     * VIVID – Erhöhte Sättigung und Kontrast. Knallige, lebendige Farben.
     *
     * FFmpeg:
     *   eq=saturation=1.6:contrast=1.15:brightness=0.04
     *   → Erhöht Farbsättigung, Kontrast und minimale Helligkeit.
     */
    private val VIVID = SparkFilterConfig(
        id = SparkFilterId.VIVID,
        displayName = "Vivid",
        ffmpegVf = "eq=saturation=1.6:contrast=1.15:brightness=0.04",
        overlayColor = Color(0xFFFFD54F),   // warmes Gelb
        overlayAlpha = 0.08f,
        labelColor = Color.White
    )

    /**
     * NOIR – Schwarzweiß-Film-Ästhetik mit erhöhtem Kontrast.
     *
     * FFmpeg:
     *   hue=s=0,eq=contrast=1.3:brightness=-0.05
     *   → Entfernt alle Farbe (s=0), erhöht Kontrast für Noir-Dramatik.
     */
    private val NOIR = SparkFilterConfig(
        id = SparkFilterId.NOIR,
        displayName = "Noir",
        ffmpegVf = "hue=s=0,eq=contrast=1.35:brightness=-0.05",
        overlayColor = Color(0xFF212121),   // fast schwarz
        overlayAlpha = 0.20f,
        labelColor = Color.White
    )

    /**
     * CINEMATIC – Kinofilme-Look: gequetschte Farben, kühle Schatten, warme Mitten.
     *
     * FFmpeg:
     *   curves=r='0/0 0.2/0.1 0.8/0.9 1/1':g='0/0 0.2/0.15 0.8/0.85 1/1':b='0/0.05 0.5/0.5 1/0.95',
     *   eq=contrast=1.1:saturation=0.85,vignette=angle=PI/4:mode=backward
     *   → Tonkurven für Filmlook, Vignette für Kinorahmen.
     */
    private val CINEMATIC = SparkFilterConfig(
        id = SparkFilterId.CINEMATIC,
        displayName = "Cinematic",
        ffmpegVf = "curves=r='0/0 0.2/0.1 0.8/0.9 1/1':g='0/0 0.2/0.15 0.8/0.85 1/1':b='0/0.05 0.5/0.5 1/0.95',eq=contrast=1.1:saturation=0.85,vignette=angle=PI/4:mode=backward",
        overlayColor = Color(0xFF1A237E),   // dunkles Indigo
        overlayAlpha = 0.12f,
        labelColor = Color.White
    )

    /**
     * LOFI – Verwaschener, leicht körniger Vintage-Look.
     *
     * FFmpeg:
     *   eq=contrast=0.9:saturation=0.7:brightness=0.05,
     *   noise=alls=8:allf=t+u,
     *   curves=r='0/0.05 1/0.95':b='0/0.05 1/0.9'
     *   → Reduzierter Kontrast/Sättigung, Film-Korn, angehobene Schwarzwerte.
     */
    private val LOFI = SparkFilterConfig(
        id = SparkFilterId.LOFI,
        displayName = "Lo-Fi",
        ffmpegVf = "eq=contrast=0.9:saturation=0.7:brightness=0.05,noise=alls=8:allf=t+u,curves=r='0/0.05 1/0.95':b='0/0.05 1/0.9'",
        overlayColor = Color(0xFF795548),   // warmes Braun
        overlayAlpha = 0.10f,
        labelColor = Color.White
    )

    /**
     * RETRO – Sepia-ähnlicher 70er-Jahre-Look mit Farbverschiebung.
     *
     * FFmpeg:
     *   colorbalance=rs=0.2:gs=-0.05:bs=-0.25:rm=0.1:gm=0.0:bm=-0.1,
     *   eq=saturation=0.8:contrast=1.05,
     *   curves=all='0/0 0.5/0.55 1/0.9'
     *   → Rote/Orange Tints, reduzierte Sättigung, angehobene Lichter.
     */
    private val RETRO = SparkFilterConfig(
        id = SparkFilterId.RETRO,
        displayName = "Retro",
        ffmpegVf = "colorbalance=rs=0.2:gs=-0.05:bs=-0.25:rm=0.1:gm=0.0:bm=-0.1,eq=saturation=0.8:contrast=1.05,curves=all='0/0 0.5/0.55 1/0.9'",
        overlayColor = Color(0xFFFF8F00),   // Orange-Bernstein
        overlayAlpha = 0.10f,
        labelColor = Color.White
    )

    /**
     * WARM – Wärme-Filter: goldene Farben, erhöhte Hauttöne.
     *
     * FFmpeg:
     *   colortemperature=temperature=4500,
     *   eq=saturation=1.1:brightness=0.03
     *   → Senkt Farbtemperatur Richtung Warmweiß, erhöht leicht Sättigung.
     */
    private val WARM = SparkFilterConfig(
        id = SparkFilterId.WARM,
        displayName = "Warm",
        ffmpegVf = "eq=saturation=1.1:brightness=0.03,curves=r='0/0 0.5/0.6 1/1':g='0/0 0.5/0.5 1/0.9':b='0/0 0.5/0.4 1/0.8'",
        overlayColor = Color(0xFFFF6F00),   // tiefes Orange
        overlayAlpha = 0.09f,
        labelColor = Color.White
    )

    /**
     * COLD – Kälte-Filter: bläuliches, klares Licht.
     *
     * FFmpeg:
     *   curves=r='0/0 0.5/0.45 1/0.9':g='0/0 0.5/0.5 1/0.95':b='0/0.05 0.5/0.55 1/1',
     *   eq=saturation=0.9:contrast=1.05
     *   → Schiebt Farbbalance Richtung Blau/Cyan, kühler Look.
     */
    private val COLD = SparkFilterConfig(
        id = SparkFilterId.COLD,
        displayName = "Cold",
        ffmpegVf = "curves=r='0/0 0.5/0.45 1/0.9':g='0/0 0.5/0.5 1/0.95':b='0/0.05 0.5/0.55 1/1',eq=saturation=0.9:contrast=1.05",
        overlayColor = Color(0xFF1565C0),   // Königsblau
        overlayAlpha = 0.10f,
        labelColor = Color.White
    )

    // ─── Öffentliche API ──────────────────────────────────────────────────────

    /**
     * Sortierte Liste aller Filter für die UI-Leiste.
     * NONE steht immer an erster Stelle.
     */
    val allFilters: List<SparkFilterConfig> = listOf(
        NONE, VIVID, NOIR, CINEMATIC, LOFI, RETRO, WARM, COLD
    )

    /**
     * Gibt die [SparkFilterConfig] für eine bestimmte [SparkFilterId] zurück.
     * Fällt auf [NONE] zurück, wenn die ID nicht gefunden wird.
     */
    fun forId(id: SparkFilterId): SparkFilterConfig {
        return allFilters.firstOrNull { it.id == id } ?: NONE
    }

    /**
     * Gibt den FFmpeg `-vf`-Filterstring für eine [SparkFilterId] zurück.
     * Gibt einen leeren String zurück, wenn kein Filter aktiv ist.
     */
    fun ffmpegFilterFor(id: SparkFilterId): String {
        return forId(id).ffmpegVf
    }
}
