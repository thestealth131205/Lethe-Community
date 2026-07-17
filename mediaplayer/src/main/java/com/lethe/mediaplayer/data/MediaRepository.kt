package com.lethe.mediaplayer.data

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** Ein lokal ausgewählter Song, bereit zum Upload. */
data class LocalAudio(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
    val title: String?,
    val artist: String?,
    val durationSec: Int
)

@Singleton
class MediaRepository @Inject constructor(
    private val api: LetheMediaApi,
    private val audiusApi: AudiusApiService
) {
    companion object {
        const val LETHE_BASE = "https://letheapp.de"
        private const val AUDIUS_FALLBACK_HOST = "https://discoveryprovider.audius.co"
    }

    @Volatile private var resolvedAudiusHost = AUDIUS_FALLBACK_HOST
    @Volatile private var audiusHostResolved = false

    private fun absUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return when {
            path.startsWith("http") -> path
            path.startsWith("/") -> "$LETHE_BASE$path"
            else -> "$LETHE_BASE/$path"
        }
    }

    /** Admin-kuratierte Lethe-Bibliothek (+ eigene Uploads). */
    suspend fun getLibrary(query: String? = null): List<Track> {
        val resp = api.getLibrary(query)
        val body = resp.body().orEmpty()
        return body.map {
            Track(
                id = it.id,
                title = it.title.ifBlank { "Unbekannt" },
                artist = it.artist,
                coverUrl = absUrl(it.coverUrl).ifBlank { null },
                audioUrl = absUrl(it.audioUrl),
                durationSec = it.durationSeconds,
                lyrics = it.lyrics,
                isLibraryTrack = true,
                genre = it.genre?.takeIf { g -> g.isNotBlank() },
                source = "lethe",
                favoriteCount = it.favoriteCount,
                playCount = it.playCount,
                bpm = it.bpm
            )
        }.filter { it.audioUrl.isNotBlank() }
    }

    // ── Audius (öffentliche API, für die Bibliothek-Ansicht "Audius") ────────

    private suspend fun ensureAudiusHost() {
        if (audiusHostResolved) return
        runCatching {
            val hosts = audiusApi.discoverHosts().body()?.data.orEmpty()
            val host = hosts.firstOrNull()?.trimEnd('/')
            if (!host.isNullOrBlank() && host.startsWith("http")) {
                resolvedAudiusHost = host
            }
        }
        audiusHostResolved = true
    }

    private fun mapAudius(tracks: List<AudiusTrack>): List<Track> = tracks.map { t ->
        Track(
            id = "audius_${t.id}",
            title = t.title.ifBlank { "Unbekannt" },
            artist = t.artistName,
            coverUrl = t.coverUrl,
            audioUrl = "$resolvedAudiusHost/v1/tracks/${t.id}/stream?app_name=LetheMediaPlayer",
            durationSec = t.duration,
            isLibraryTrack = true,
            source = "audius"
        )
    }

    /** Trending-Tracks von Audius (Startansicht des Audius-Tabs), optional nach Genre gefiltert. */
    suspend fun getAudiusTrending(genre: String? = null): List<Track> {
        ensureAudiusHost()
        val resp = audiusApi.getTrendingTracks(
            url = "$resolvedAudiusHost/v1/tracks/trending",
            genre = genre?.takeIf { it.isNotBlank() }
        )
        return mapAudius(resp.body()?.data.orEmpty())
    }

    /** Sucht Tracks auf Audius nach einem Suchbegriff. */
    suspend fun searchAudius(query: String): List<Track> {
        ensureAudiusHost()
        val resp = audiusApi.searchTracks(url = "$resolvedAudiusHost/v1/tracks/search", query = query)
        return mapAudius(resp.body()?.data.orEmpty())
    }

    /** Persönliche Musik – optional nur Favoriten oder eine Playlist. */
    suspend fun getUserMusic(favoritesOnly: Boolean = false, playlistId: String? = null): List<Track> {
        val resp = api.getUserMusic(favoritesOnly, playlistId)
        val body = resp.body().orEmpty()
        return body.map {
            Track(
                id = it.id,
                title = (it.musicTitle ?: "Unbekannt").ifBlank { "Unbekannt" },
                artist = it.artist.orEmpty(),
                coverUrl = absUrl(it.coverUrl).ifBlank { null },
                audioUrl = absUrl(it.musicUrl),
                durationSec = it.playTime ?: 0,
                isFavorite = it.favorit
            )
        }.filter { it.audioUrl.isNotBlank() }
    }

    suspend fun getPlaylists(): List<PlaylistDto> =
        api.getPlaylists().body().orEmpty().map { it.copy(coverUrl = absUrl(it.coverUrl).ifBlank { null }) }

    // ── Lethe-Playlists (global vom Admin kuratiert) ─────────────────────────

    /** Alle vom Admin angelegten Lethe-Playlists (bei jedem Nutzer sichtbar). */
    suspend fun getLethePlaylists(): List<PlaylistDto> =
        api.getLethePlaylists().body().orEmpty().map { it.copy(coverUrl = absUrl(it.coverUrl).ifBlank { null }) }

    /** Die Titel einer Lethe-Playlist als abspielbare Library-Tracks. */
    suspend fun getLethePlaylistTracks(playlistId: String): List<Track> =
        api.getLethePlaylistTracks(playlistId).body().orEmpty().map {
            Track(
                id = it.id,
                title = it.title.ifBlank { "Unbekannt" },
                artist = it.artist,
                coverUrl = absUrl(it.coverUrl).ifBlank { null },
                audioUrl = absUrl(it.audioUrl),
                durationSec = it.durationSeconds,
                lyrics = it.lyrics,
                isLibraryTrack = true,
                genre = it.genre?.takeIf { g -> g.isNotBlank() },
                source = "lethe",
                playCount = it.playCount,
                bpm = it.bpm
            )
        }.filter { it.audioUrl.isNotBlank() }

    /** Legt eine globale Lethe-Playlist an (nur Admins). */
    suspend fun createLethePlaylist(name: String, trackIds: List<String>): PlaylistDto {
        val namePart = name.toRequestBody("text/plain".toMediaTypeOrNull())
        val idsJson = com.google.gson.Gson().toJson(trackIds)
        val idsPart = idsJson.toRequestBody("text/plain".toMediaTypeOrNull())
        val created = api.adminCreateLethePlaylist(namePart, idsPart).body()
            ?: throw IllegalStateException("Lethe-Playlist konnte nicht angelegt werden")
        return created.copy(coverUrl = absUrl(created.coverUrl).ifBlank { null })
    }

    /** Löscht eine globale Lethe-Playlist (nur Admins). */
    suspend fun deleteLethePlaylist(playlistId: String): Boolean = runCatching {
        api.adminDeleteLethePlaylist(playlistId).isSuccessful
    }.getOrDefault(false)

    /** Auto-generiertes Collage-Cover der Lieblingssongs-Kachel (Homescreen). */
    suspend fun getFavoritesCover(): String? = runCatching {
        absUrl(api.getFavoritesCover().body()?.coverUrl).ifBlank { null }
    }.getOrNull()

    /** Legt eine neue (ggf. leere) Playlist an, optional mit manuell gewähltem Cover-Bild. */
    suspend fun createPlaylist(
        name: String,
        coverBytes: ByteArray?,
        coverMimeType: String?,
        playAsMix: Boolean = false
    ): PlaylistDto {
        val namePart = name.toRequestBody("text/plain".toMediaTypeOrNull())
        val coverPart = coverBytes?.let {
            MultipartBody.Part.createFormData(
                "cover", "cover.jpg", it.toRequestBody((coverMimeType ?: "image/jpeg").toMediaTypeOrNull())
            )
        }
        val mixPart = playAsMix.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val created = api.createPlaylist(namePart, coverPart, mixPart).body()
            ?: throw IllegalStateException("Playlist konnte nicht angelegt werden")
        return created.copy(coverUrl = absUrl(created.coverUrl).ifBlank { null })
    }

    /**
     * Aktualisiert Name und/oder Cover einer Playlist. [name] null lässt den Namen unverändert,
     * [coverBytes] null lässt das Cover unverändert, [playAsMix] null lässt den Mix-Modus unverändert.
     */
    suspend fun updatePlaylist(
        playlistId: String,
        name: String?,
        coverBytes: ByteArray?,
        coverMimeType: String?,
        playAsMix: Boolean? = null
    ): PlaylistDto {
        val namePart = name?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())
        val coverPart = coverBytes?.let {
            MultipartBody.Part.createFormData(
                "cover", "cover.jpg", it.toRequestBody((coverMimeType ?: "image/jpeg").toMediaTypeOrNull())
            )
        }
        val mixPart = playAsMix?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val updated = api.updatePlaylist(playlistId, namePart, coverPart, mixPart).body()
            ?: throw IllegalStateException("Playlist konnte nicht aktualisiert werden")
        return updated.copy(coverUrl = absUrl(updated.coverUrl).ifBlank { null })
    }

    /** Löscht eine Playlist des Nutzers samt ihrer zugeordneten Titel. */
    suspend fun deletePlaylist(playlistId: String) {
        val resp = api.deletePlaylist(playlistId)
        if (!resp.isSuccessful) throw IllegalStateException("Playlist konnte nicht gelöscht werden")
    }

    /** Liefert Name + fake_number des angemeldeten Lethe-Kontos (für die App-Infos-Ansicht). */
    suspend fun getMe(): UserMeDto? = runCatching { api.getMe().body() }.getOrNull()

    /**
     * Markiert [track] als Favorit (oder entfernt die Markierung). POST /mymusic/{id}/favorite
     * erwartet eine user_music-ID; Bibliothekstitel (music_library) haben noch keinen
     * persönlichen Eintrag, daher wird für sie zuerst per POST /mymusic einer angelegt
     * (bzw. der bestehende anhand der music_url wiederverwendet).
     */
    suspend fun setFavorite(track: Track, favorite: Boolean): Boolean = runCatching {
        val musicId = if (track.isLibraryTrack) {
            api.saveUserMusic(
                UserMusicSaveRequest(
                    musicUrl = track.audioUrl,
                    musicTitle = track.title,
                    artist = track.artist,
                    playTime = track.durationSec.takeIf { it > 0 },
                    coverUrl = track.coverUrl,
                    libraryTrackId = track.id
                )
            ).body()?.id ?: track.id
        } else {
            track.id
        }
        api.setFavorite(musicId, FavoriteRequest(favorite)).isSuccessful
    }.getOrDefault(false)

    // ── FriendsMix ───────────────────────────────────────────────────────────

    /** Akzeptierte Kontakte für die FriendsMix-Gruppenauswahl (Profilbild absolut aufgelöst). */
    suspend fun getFriendsMixContacts(): List<FriendsMixContactDto> =
        api.getFriendsMixContacts().body().orEmpty().map {
            it.copy(profileImageUrl = absUrl(it.profileImageUrl).ifBlank { null })
        }

    /** Fügt einen Kontakt zur FriendsMix-Gruppe hinzu bzw. entfernt ihn. */
    suspend fun setFriendInGroup(friendId: String, inGroup: Boolean): Boolean = runCatching {
        api.updateFriendsMixGroup(friendId, FriendsMixGroupRequest(inGroup)).isSuccessful
    }.getOrDefault(false)

    /** Der dynamisch vom Server zusammengestellte FriendsMix (als abspielbare Tracks). */
    suspend fun getFriendsMix(): List<Track> =
        api.getFriendsMix().body().orEmpty().map {
            Track(
                id = it.id,
                title = it.title.ifBlank { "Unbekannt" },
                artist = it.artist,
                coverUrl = absUrl(it.coverUrl).ifBlank { null },
                audioUrl = absUrl(it.audioUrl),
                durationSec = it.durationSeconds,
                mixStartSec = it.mixStartSeconds,
                crossfadeStartSec = it.crossfadeStartSeconds
            )
        }.filter { it.audioUrl.isNotBlank() }

    // ── Künstler-Accounts (Media Player) ──────────────────────────────────────

    /** Registriert einen neuen Künstler-Account. Gibt null bei Erfolg, sonst eine Fehlermeldung. */
    suspend fun artistRegister(email: String, password: String, artistName: String): String? = runCatching {
        val resp = api.artistRegister(ArtistRegisterRequest(email.trim(), password, artistName.trim()))
        if (resp.isSuccessful) null else (resp.errorBody()?.string()?.let { extractDetail(it) } ?: "Registrierung fehlgeschlagen.")
    }.getOrDefault("Server nicht erreichbar.")

    /** Meldet einen Künstler an. Gibt bei Erfolg (Token, Profil) zurück, sonst wirft Exception mit Meldung. */
    suspend fun artistLogin(email: String, password: String): ArtistLoginResponse {
        val resp = api.artistLogin(ArtistLoginRequest(email.trim(), password))
        if (resp.isSuccessful) {
            return resp.body() ?: throw IllegalStateException("Leere Antwort vom Server.")
        }
        throw IllegalStateException(resp.errorBody()?.string()?.let { extractDetail(it) } ?: "Anmeldung fehlgeschlagen.")
    }

    /** Lädt das eigene Künstler-Profil (mit Künstler-Token). */
    suspend fun artistMe(token: String): ArtistDto? = runCatching {
        api.artistMe("Bearer $token").body()?.let { it.copy(artistPicture = absUrl(it.artistPicture).ifBlank { null }) }
    }.getOrNull()

    /** Aktualisiert Name/Bio/zugeordnete Songs des eigenen Künstler-Profils. */
    suspend fun artistUpdateMe(
        token: String,
        artistName: String?,
        artistBio: String?,
        songIds: List<String>?
    ): ArtistDto? = runCatching {
        api.artistUpdateMe("Bearer $token", ArtistUpdateRequest(artistName, artistBio, songIds))
            .body()?.let { it.copy(artistPicture = absUrl(it.artistPicture).ifBlank { null }) }
    }.getOrNull()

    /** Lädt ein Künstlerbild hoch. */
    suspend fun artistUploadPicture(token: String, bytes: ByteArray, mimeType: String, fileName: String): ArtistDto? = runCatching {
        val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", fileName, body)
        api.artistUploadPicture("Bearer $token", part)
            .body()?.let { it.copy(artistPicture = absUrl(it.artistPicture).ifBlank { null }) }
    }.getOrNull()

    /** Öffentliche Liste aller freigegebenen Künstler (für den Künstler-Screen). */
    suspend fun getArtists(): List<ArtistProfile> =
        api.getArtists().body().orEmpty().map { mapArtistPublic(it) }

    /** Öffentliches Künstler-Profil inkl. abspielbarer Songs. */
    suspend fun getArtist(artistId: String): ArtistProfile? = runCatching {
        api.getArtist(artistId).body()?.let { mapArtistPublic(it) }
    }.getOrNull()

    private fun mapArtistPublic(dto: ArtistPublicDto): ArtistProfile = ArtistProfile(
        id = dto.id,
        name = dto.artistName,
        picture = absUrl(dto.artistPicture).ifBlank { null },
        bio = dto.artistBio,
        songs = dto.songs.map {
            Track(
                id = it.id,
                title = it.title.ifBlank { "Unbekannt" },
                artist = it.artist.ifBlank { dto.artistName },
                coverUrl = absUrl(it.coverUrl).ifBlank { null },
                audioUrl = absUrl(it.audioUrl),
                durationSec = it.durationSeconds,
                lyrics = it.lyrics,
                isLibraryTrack = true,
                genre = it.genre?.takeIf { g -> g.isNotBlank() },
                source = "lethe"
            )
        }.filter { it.audioUrl.isNotBlank() }
    )

    // ── Admin-Verwaltung der Künstler (nutzt den Lethe-User-Token) ──

    suspend fun adminGetArtists(): List<ArtistDto> =
        api.adminGetArtists().body().orEmpty().map { it.copy(artistPicture = absUrl(it.artistPicture).ifBlank { null }) }

    suspend fun adminApproveArtist(artistId: String, approved: Boolean): ArtistDto? = runCatching {
        api.adminApproveArtist(artistId, ArtistApprovalRequest(approved)).body()
    }.getOrNull()

    suspend fun adminBlockArtist(artistId: String, blocked: Boolean): ArtistDto? = runCatching {
        api.adminBlockArtist(artistId, ArtistBlockRequest(blocked)).body()
    }.getOrNull()

    suspend fun adminUpdateArtist(artistId: String, artistName: String?, artistBio: String?, songIds: List<String>?): ArtistDto? = runCatching {
        api.adminUpdateArtist(artistId, ArtistUpdateRequest(artistName, artistBio, songIds)).body()
    }.getOrNull()

    suspend fun adminDeleteArtist(artistId: String): Boolean = runCatching {
        api.adminDeleteArtist(artistId).isSuccessful
    }.getOrDefault(false)

    /** Live-Fortschritt der serverseitigen FriendsMix-BPM-Berechnung. */
    suspend fun adminBpmStatus(): BpmStatusDto? = runCatching {
        api.adminBpmStatus().body()
    }.getOrNull()

    /** Extrahiert das "detail"-Feld einer FastAPI-Fehlerantwort (JSON), sonst den Rohtext. */
    private fun extractDetail(errorBody: String): String = runCatching {
        val obj = com.google.gson.JsonParser().parse(errorBody).asJsonObject
        obj.get("detail")?.asString ?: errorBody
    }.getOrDefault(errorBody)

    /** Meldet eine Wiedergabe eines Lethe-Library-Titels für den Stream-Zähler (fire-and-forget). */
    suspend fun registerPlay(track: Track) {
        if (track.source != "lethe") return
        runCatching { api.registerPlay(track.id) }
    }

    /**
     * Lädt einen lokalen Song hoch, legt ihn in der persönlichen Musik an und
     * hängt ihn an die Ziel-Playlist (bestehend über [playlistId] oder neu über [playlistName]).
     */
    suspend fun addLocalTrackToPlaylist(
        local: LocalAudio,
        playlistId: String?,
        playlistName: String
    ) {
        val body = local.bytes.toRequestBody(local.mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", local.fileName, body)
        val artistPart = local.artist?.takeIf { it.isNotBlank() }
            ?.toRequestBody("text/plain".toMediaTypeOrNull())
        val titlePart = local.title?.takeIf { it.isNotBlank() }
            ?.toRequestBody("text/plain".toMediaTypeOrNull())

        val uploaded = api.uploadMusic(part, artistPart, titlePart).body()
            ?: throw IllegalStateException("Upload fehlgeschlagen")

        val saved = api.saveUserMusic(
            UserMusicSaveRequest(
                musicUrl = uploaded.mediaUrl,
                musicTitle = local.title ?: uploaded.songTitle,
                artist = local.artist ?: uploaded.artist,
                playTime = local.durationSec.takeIf { it > 0 } ?: uploaded.length,
                coverUrl = uploaded.coverUrl
            )
        ).body() ?: throw IllegalStateException("Speichern fehlgeschlagen")

        val ok = api.addToPlaylist(
            saved.id,
            UserMusicPlaylistRequest(playlistId = playlistId, playlistName = playlistName)
        ).isSuccessful
        if (!ok) throw IllegalStateException("Zur Playlist hinzufügen fehlgeschlagen")
    }

    /**
     * Hängt einen bereits abspielbaren Titel (aktuelle Wiedergabe) an die Ziel-Playlist.
     * Bibliothekstitel werden dafür zuerst als persönlicher Musik-Eintrag gespeichert.
     */
    suspend fun addTrackToPlaylist(track: Track, playlistId: String?, playlistName: String): Boolean = runCatching {
        val musicId = if (track.isLibraryTrack) {
            api.saveUserMusic(
                UserMusicSaveRequest(
                    musicUrl = track.audioUrl,
                    musicTitle = track.title,
                    artist = track.artist,
                    playTime = track.durationSec.takeIf { it > 0 },
                    coverUrl = track.coverUrl,
                    libraryTrackId = track.id
                )
            ).body()?.id ?: track.id
        } else {
            track.id
        }
        api.addToPlaylist(
            musicId,
            UserMusicPlaylistRequest(playlistId = playlistId, playlistName = playlistName)
        ).isSuccessful
    }.getOrDefault(false)
}
