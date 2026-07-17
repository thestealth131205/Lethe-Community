package com.securechat.app.data.network

import com.google.gson.annotations.SerializedName

// ─────────────────────────────────────────────────────────────────────────────
// Audius Music API – Datenmodelle
//
// Discovery: GET https://api.audius.co/                        → AudiusHostsResponse
// Search:    GET {host}/v1/tracks/search?query=...             → AudiusSearchResponse
// Trending:  GET {host}/v1/tracks/trending                     → AudiusSearchResponse
// Stream:    GET {host}/v1/tracks/{id}/stream?app_name=LetheApp → HTTP-Redirect auf MP3-CDN
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Antwort der Audius-Discovery-Endpoint (GET https://api.audius.co/).
 * Enthält eine Liste gültiger Host-Nodes, z.B.:
 *   ["https://audius-metadata-3.figment.io", "https://discoveryprovider.audius.co", ...]
 */
data class AudiusHostsResponse(
    val data: List<String> = emptyList()
)

/** Wrapper-Antwort für Track-Suche und Trending-Endpunkte. */
data class AudiusSearchResponse(
    val data: List<AudiusTrack> = emptyList()
)

/**
 * Cover-Art eines Tracks in verschiedenen Auflösungen.
 * Alle Felder sind nullable – nicht jeder Track hat ein Cover.
 */
data class AudiusArtwork(
    @SerializedName("150x150")   val x150: String? = null,
    @SerializedName("480x480")   val x480: String? = null,
    @SerializedName("1000x1000") val x1000: String? = null
)

/** Basis-Infos zum Creator / Künstler eines Tracks. */
data class AudiusUser(
    val id: String = "",
    val name: String = "",
    val handle: String = ""
)

/**
 * Ein einzelner Musik-Track von Audius.
 *
 * @param id              Eindeutige Track-ID (String, z.B. "D7KyD85").
 * @param title           Titel des Stücks.
 * @param duration        Länge in Sekunden.
 * @param genre           Musik-Genre (z.B. "Electronic", "Hip-Hop / Rap"). Nullable.
 * @param artwork         Cover-Art in verschiedenen Auflösungen (nullable).
 * @param user            Künstler / Creator-Profil (nullable).
 * @param playCount       Gesamte Wiedergaben auf Audius.
 * @param favoriteCount   Anzahl Favoriten auf Audius.
 */
data class AudiusTrack(
    val id: String = "",
    val title: String = "",
    val duration: Int = 0,
    val genre: String? = null,
    val artwork: AudiusArtwork? = null,
    val user: AudiusUser? = null,
    @SerializedName("play_count")     val playCount: Int = 0,
    @SerializedName("favorite_count") val favoriteCount: Int = 0
) {
    /**
     * Formatierte Dauer als "m:ss"-String (z.B. "3:07").
     * Identisches Format wie die frühere PixabayTrack.durationFormatted.
     */
    val durationFormatted: String
        get() {
            val min = duration / 60
            val sec = duration % 60
            return "$min:${sec.toString().padStart(2, '0')}"
        }

    /**
     * Künstler-Anzeigename.
     * Priorität: user.name → user.handle → "Unknown Artist".
     */
    val artistName: String
        get() = user?.name?.takeIf { it.isNotBlank() }
            ?: user?.handle?.takeIf { it.isNotBlank() }
            ?: "Unknown Artist"

    /**
     * Bestes verfügbares Cover-Art-Bild.
     * Priorität: 480 px → 150 px → 1000 px (höchste Aufl. als letzter Fallback).
     */
    val coverUrl: String?
        get() = artwork?.x480?.takeIf { it.isNotBlank() }
            ?: artwork?.x150?.takeIf { it.isNotBlank() }
            ?: artwork?.x1000?.takeIf { it.isNotBlank() }
}

/**
 * Interner UI-State für einen Track im Music-Picker BottomSheet.
 *
 * @param track      Zugehöriger [AudiusTrack].
 * @param isPlaying  True, wenn dieser Track gerade in der Vorschau abgespielt wird.
 * @param isSelected True, wenn dieser Track für den Spark-Export ausgewählt wurde.
 */
data class MusicTrackUiState(
    val track: AudiusTrack,
    val isPlaying: Boolean = false,
    val isSelected: Boolean = false
)

// ─────────────────────────────────────────────────────────────────────────────
// Lethe Bibliothek – Admin-kuratierte eigene Musikbibliothek
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Ein Track aus der Lethe-eigenen Musikbibliothek (admin-hochgeladen).
 * Entspricht der Antwort von GET /music/library.
 */
data class LetheMusicTrack(
    @SerializedName("id")                 val id: String = "",
    @SerializedName("title")              val title: String = "",
    @SerializedName("artist")             val artist: String = "",
    @SerializedName("genre")              val genre: String? = null,
    @SerializedName("cover_url")          val coverUrl: String? = null,
    @SerializedName("audio_url")          val audioUrl: String = "",
    @SerializedName("lyrics")             val lyrics: String? = null,
    @SerializedName("rights_holder")      val rightsHolder: String? = null,
    @SerializedName("license")            val license: String? = null,
    @SerializedName("duration_seconds")   val durationSeconds: Int = 0,
    @SerializedName("uploader_id")        val uploaderId: String? = null,
    @SerializedName("year")               val year: String? = null,
    @SerializedName("producer")           val producer: String? = null,
    @SerializedName("preview_offset_sec") val previewOffsetSec: Int = 0
) {
    val durationFormatted: String
        get() {
            val min = durationSeconds / 60
            val sec = durationSeconds % 60
            return "$min:${sec.toString().padStart(2, '0')}"
        }
}

/** Interner UI-State für einen Lethe-Library-Track im MusicPickerSheet. */
data class LetheMusicTrackUiState(
    val track: LetheMusicTrack,
    val isPlaying: Boolean = false,
    val isSelected: Boolean = false
)
