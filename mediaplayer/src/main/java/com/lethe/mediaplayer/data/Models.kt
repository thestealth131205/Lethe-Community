package com.lethe.mediaplayer.data

import com.google.gson.annotations.SerializedName

/** Ein abspielbarer Track (vereinheitlicht aus Bibliothek/Favoriten/Playlist). */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val audioUrl: String,
    val durationSec: Int,
    val lyrics: String? = null,
    val isFavorite: Boolean = false,
    /** true wenn [id] eine music_library-ID ist (noch kein user_music-Eintrag existiert). */
    val isLibraryTrack: Boolean = false,
    /** Genre/Kategorie (nur bei Bibliothekstiteln gesetzt). */
    val genre: String? = null,
    /** Herkunft des Tracks: "user" (persönliche Musik), "lethe" (Lethe-Bibliothek) oder "audius". */
    val source: String = "user",
    /** Anzahl der Nutzer, die diesen Titel favorisiert haben (nur bei Bibliothekstiteln gesetzt). */
    val favoriteCount: Int = 0,
    /** Anzahl der Streams/Wiedergaben dieses Titels (nur bei Bibliothekstiteln gesetzt). */
    val playCount: Int = 0,
    /** FriendsMix: serverseitig optimierter Einstieg in Sekunden (NULL = Standard 30% der Dauer). */
    val mixStartSec: Float? = null,
    /** FriendsMix: serverseitig optimierter Überblend-Start in Sekunden (NULL = feste 15s vor Ende). */
    val crossfadeStartSec: Float? = null,
    /** Serverseitig ermittelte Beats-per-Minute (nur bei Bibliothekstiteln gesetzt, NULL = noch nicht berechnet). */
    val bpm: Float? = null
)

// ── Server-DTOs (Teilmenge der Lethe-API) ────────────────────────────────────

/** Antwort von GET /mymusic */
data class UserMusicDto(
    val id: String,
    @SerializedName("music_url") val musicUrl: String,
    @SerializedName("music_title") val musicTitle: String? = null,
    val artist: String? = null,
    @SerializedName("play_time") val playTime: Int? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    val favorit: Boolean = false,
    @SerializedName("playlist_id") val playlistId: String? = null,
    @SerializedName("playlist_name") val playlistName: String? = null
)

/** Antwort von GET /mymusic/playlists (+ POST-Erstellung + GET favorites-cover) */
data class PlaylistDto(
    @SerializedName("playlist_id") val playlistId: String,
    @SerializedName("playlist_name") val playlistName: String,
    @SerializedName("track_count") val trackCount: Int = 0,
    @SerializedName("total_duration_seconds") val totalDurationSeconds: Int? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    /** true = Playlist wird wie der Family/FriendsMix als DJ-Mix (15s-Crossfade, Einstieg ab 30%) abgespielt. */
    @SerializedName("play_as_mix") val playAsMix: Boolean = false
)

/** Antwort von GET /music/library */
data class LibraryTrackDto(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val genre: String? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("audio_url") val audioUrl: String = "",
    val lyrics: String? = null,
    @SerializedName("duration_seconds") val durationSeconds: Int = 0,
    @SerializedName("play_count") val playCount: Int = 0,
    @SerializedName("favorite_count") val favoriteCount: Int = 0,
    val bpm: Float? = null
)

data class FavoriteRequest(val favorit: Boolean)

/** Antwort von GET /friendsmix/contacts (Kontakt-Auswahl für die FriendsMix-Gruppe). */
data class FriendsMixContactDto(
    @SerializedName("user_id") val userId: String,
    val name: String = "",
    @SerializedName("profile_image_url") val profileImageUrl: String? = null,
    @SerializedName("in_group") val inGroup: Boolean = false
)

/** Anfrage an POST /friendsmix/group/{friendId}. */
data class FriendsMixGroupRequest(@SerializedName("in_group") val inGroup: Boolean)

/** Antwort von GET /friendsmix (ein Titel des dynamischen FriendsMix). */
data class FriendsMixTrackDto(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("audio_url") val audioUrl: String = "",
    @SerializedName("duration_seconds") val durationSeconds: Int = 0,
    @SerializedName("contributor_name") val contributorName: String? = null,
    /** Serverseitig optimierter FriendsMix-Einstieg in Sekunden (NULL = Player nutzt Standard 30%). */
    @SerializedName("mix_start_seconds") val mixStartSeconds: Float? = null,
    /** Serverseitig optimierter Überblend-Start in Sekunden (NULL = Player nutzt feste 15s vor Ende). */
    @SerializedName("crossfade_start_seconds") val crossfadeStartSeconds: Float? = null
)

/** Antwort von GET /users/me (nur die für die App-Infos-Anzeige benötigten Felder). */
data class UserMeDto(
    val name: String = "",
    @SerializedName("fake_number") val fakeNumber: String = "",
    /** true wenn der angemeldete Lethe-Nutzer ein Admin-Konto ist (schaltet den Admin-Bereich frei). */
    @SerializedName("is_admin") val isAdmin: Boolean = false
)

// ── Künstler-Accounts (Media Player) ─────────────────────────────────────────

/** Anfrage an POST /artist/register. */
data class ArtistRegisterRequest(
    val email: String,
    val password: String,
    @SerializedName("artist_name") val artistName: String
)

/** Anfrage an POST /artist/login. */
data class ArtistLoginRequest(val email: String, val password: String)

/** Voller Künstler-Datensatz (eigenes Profil + Admin-Verwaltung). */
data class ArtistDto(
    val id: String = "",
    val email: String = "",
    @SerializedName("artist_name") val artistName: String = "",
    @SerializedName("artist_picture") val artistPicture: String? = null,
    @SerializedName("artist_bio") val artistBio: String? = null,
    @SerializedName("song_ids") val songIds: List<String> = emptyList(),
    @SerializedName("is_approved") val isApproved: Boolean = false,
    @SerializedName("is_blocked") val isBlocked: Boolean = false
)

/** Antwort von POST /artist/login. */
data class ArtistLoginResponse(
    @SerializedName("access_token") val accessToken: String = "",
    val artist: ArtistDto = ArtistDto()
)

/** Anfrage an PUT /artist/me bzw. PUT /admin/artists/{id}. */
data class ArtistUpdateRequest(
    @SerializedName("artist_name") val artistName: String? = null,
    @SerializedName("artist_bio") val artistBio: String? = null,
    @SerializedName("song_ids") val songIds: List<String>? = null
)

data class ArtistApprovalRequest(@SerializedName("is_approved") val isApproved: Boolean)
data class ArtistBlockRequest(@SerializedName("is_blocked") val isBlocked: Boolean)

/** Live-Fortschritt der serverseitigen FriendsMix-BPM-Berechnung (GET /admin/bpm-status). */
data class BpmStatusDto(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("calculated") val calculated: Int = 0,
    @SerializedName("remaining") val remaining: Int = 0,
    @SerializedName("running") val running: Boolean = false,
    @SerializedName("current_title") val currentTitle: String? = null,
    @SerializedName("session_calculated") val sessionCalculated: Int = 0
)

/** Ein Song in der öffentlichen Künstler-Ansicht (GET /artists/{id}). */
data class ArtistPublicSongDto(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("audio_url") val audioUrl: String = "",
    @SerializedName("duration_seconds") val durationSeconds: Int = 0,
    val lyrics: String? = null,
    val genre: String? = null
)

/** Öffentliche Künstler-Ansicht (GET /artists, GET /artists/{id}). */
data class ArtistPublicDto(
    val id: String = "",
    @SerializedName("artist_name") val artistName: String = "",
    @SerializedName("artist_picture") val artistPicture: String? = null,
    @SerializedName("artist_bio") val artistBio: String? = null,
    val songs: List<ArtistPublicSongDto> = emptyList()
)

/** Aufbereitetes Künstler-Profil für den Künstler-Screen (Songs als abspielbare Tracks). */
data class ArtistProfile(
    val id: String,
    val name: String,
    val picture: String?,
    val bio: String?,
    val songs: List<Track>
)

/** Antwort von POST /music/upload */
data class MusicUploadResponse(
    val id: String,
    @SerializedName("media_url") val mediaUrl: String,
    @SerializedName("song_title") val songTitle: String? = null,
    val artist: String? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    val length: Int = 0
)

/** Anfrage an POST /mymusic – legt einen persönlichen Musik-Eintrag an. */
data class UserMusicSaveRequest(
    @SerializedName("music_url") val musicUrl: String,
    @SerializedName("music_title") val musicTitle: String? = null,
    val artist: String? = null,
    @SerializedName("play_time") val playTime: Int? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("library_track_id") val libraryTrackId: String? = null
)

/** Anfrage an POST /mymusic/{musicId}/playlist – hängt einen Titel an eine Playlist. */
data class UserMusicPlaylistRequest(
    @SerializedName("playlist_id") val playlistId: String? = null,
    @SerializedName("playlist_name") val playlistName: String
)

// ── Audius-DTOs (öffentliche Audius-API, kein Lethe-Server) ──────────────────

/** Antwort der Audius-Discovery-Endpoint (GET https://api.audius.co/). */
data class AudiusHostsResponse(val data: List<String> = emptyList())

/** Wrapper-Antwort für Track-Suche und Trending-Endpunkte. */
data class AudiusSearchResponse(val data: List<AudiusTrack> = emptyList())

data class AudiusArtwork(
    @SerializedName("150x150") val x150: String? = null,
    @SerializedName("480x480") val x480: String? = null,
    @SerializedName("1000x1000") val x1000: String? = null
)

data class AudiusUser(
    val id: String = "",
    val name: String = "",
    val handle: String = ""
)

data class AudiusTrack(
    val id: String = "",
    val title: String = "",
    val duration: Int = 0,
    val genre: String? = null,
    val artwork: AudiusArtwork? = null,
    val user: AudiusUser? = null
) {
    val artistName: String
        get() = user?.name?.takeIf { it.isNotBlank() }
            ?: user?.handle?.takeIf { it.isNotBlank() }
            ?: "Unknown Artist"

    val coverUrl: String?
        get() = artwork?.x480?.takeIf { it.isNotBlank() }
            ?: artwork?.x150?.takeIf { it.isNotBlank() }
            ?: artwork?.x1000?.takeIf { it.isNotBlank() }
}
