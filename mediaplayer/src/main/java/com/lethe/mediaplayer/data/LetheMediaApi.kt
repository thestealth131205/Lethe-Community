package com.lethe.mediaplayer.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface LetheMediaApi {

    @GET("users/me")
    suspend fun getMe(): Response<UserMeDto>

    @GET("mymusic")
    suspend fun getUserMusic(
        @Query("favorites_only") favoritesOnly: Boolean = false,
        @Query("playlist_id") playlistId: String? = null
    ): Response<List<UserMusicDto>>

    @GET("mymusic/playlists")
    suspend fun getPlaylists(): Response<List<PlaylistDto>>

    @GET("mymusic/favorites-cover")
    suspend fun getFavoritesCover(): Response<PlaylistDto>

    @DELETE("mymusic/playlists/{playlistId}")
    suspend fun deletePlaylist(@Path("playlistId") playlistId: String): Response<Unit>

    @Multipart
    @POST("mymusic/playlists")
    suspend fun createPlaylist(
        @Part("name") name: RequestBody,
        @Part cover: MultipartBody.Part? = null,
        @Part("play_as_mix") playAsMix: RequestBody? = null
    ): Response<PlaylistDto>

    @Multipart
    @PUT("mymusic/playlists/{playlistId}")
    suspend fun updatePlaylist(
        @Path("playlistId") playlistId: String,
        @Part("name") name: RequestBody? = null,
        @Part cover: MultipartBody.Part? = null,
        @Part("play_as_mix") playAsMix: RequestBody? = null
    ): Response<PlaylistDto>

    @GET("music/library")
    suspend fun getLibrary(
        @Query("q") query: String? = null
    ): Response<List<LibraryTrackDto>>

    // ── Lethe-Playlists (global vom Admin kuratiert, bei allen Nutzern sichtbar) ──

    @GET("lethe-playlists")
    suspend fun getLethePlaylists(): Response<List<PlaylistDto>>

    @GET("lethe-playlists/{playlistId}")
    suspend fun getLethePlaylistTracks(
        @Path("playlistId") playlistId: String
    ): Response<List<LibraryTrackDto>>

    @Multipart
    @POST("admin/lethe-playlists")
    suspend fun adminCreateLethePlaylist(
        @Part("name") name: RequestBody,
        @Part("track_ids") trackIds: RequestBody? = null
    ): Response<PlaylistDto>

    @DELETE("admin/lethe-playlists/{playlistId}")
    suspend fun adminDeleteLethePlaylist(
        @Path("playlistId") playlistId: String
    ): Response<Unit>

    @POST("mymusic/{musicId}/favorite")
    suspend fun setFavorite(
        @Path("musicId") musicId: String,
        @Body request: FavoriteRequest
    ): Response<UserMusicDto>

    @Multipart
    @POST("music/upload")
    suspend fun uploadMusic(
        @Part file: MultipartBody.Part,
        @Part("artist") artist: RequestBody? = null,
        @Part("song_title") songTitle: RequestBody? = null
    ): Response<MusicUploadResponse>

    @POST("mymusic")
    suspend fun saveUserMusic(@Body request: UserMusicSaveRequest): Response<UserMusicDto>

    @POST("mymusic/{musicId}/playlist")
    suspend fun addToPlaylist(
        @Path("musicId") musicId: String,
        @Body request: UserMusicPlaylistRequest
    ): Response<UserMusicDto>

    @POST("music/{musicId}/play")
    suspend fun registerPlay(@Path("musicId") musicId: String): Response<Unit>

    @GET("friendsmix/contacts")
    suspend fun getFriendsMixContacts(): Response<List<FriendsMixContactDto>>

    @POST("friendsmix/group/{friendId}")
    suspend fun updateFriendsMixGroup(
        @Path("friendId") friendId: String,
        @Body request: FriendsMixGroupRequest
    ): Response<FriendsMixContactDto>

    @GET("friendsmix")
    suspend fun getFriendsMix(): Response<List<FriendsMixTrackDto>>

    // ── Jam (geteilte Live-Playlist, per QR-Code beitretbar) ──

    @POST("jam/create")
    suspend fun createJam(@Body request: JamCreateRequest): Response<JamStateDto>

    @GET("jam/{jamId}")
    suspend fun getJam(@Path("jamId") jamId: String): Response<JamStateDto>

    @POST("jam/{jamId}/join")
    suspend fun joinJam(@Path("jamId") jamId: String): Response<JamStateDto>

    @POST("jam/{jamId}/tracks")
    suspend fun addJamTracks(
        @Path("jamId") jamId: String,
        @Body request: JamAddTracksRequest
    ): Response<JamStateDto>

    @POST("jam/{jamId}/leave")
    suspend fun leaveJam(@Path("jamId") jamId: String): Response<Unit>

    @DELETE("jam/{jamId}")
    suspend fun endJam(@Path("jamId") jamId: String): Response<Unit>

    // ── Künstler-Accounts (Media Player) ──
    // register/login benötigen keine Auth; die /artist/me-Endpoints tragen den Künstler-Token
    // explizit als @Header, damit der Interceptor ihn nicht durch den Lethe-User-Token ersetzt.

    @POST("artist/register")
    suspend fun artistRegister(@Body request: ArtistRegisterRequest): Response<Unit>

    @POST("artist/login")
    suspend fun artistLogin(@Body request: ArtistLoginRequest): Response<ArtistLoginResponse>

    @GET("artist/me")
    suspend fun artistMe(@Header("Authorization") auth: String): Response<ArtistDto>

    @PUT("artist/me")
    suspend fun artistUpdateMe(
        @Header("Authorization") auth: String,
        @Body request: ArtistUpdateRequest
    ): Response<ArtistDto>

    @Multipart
    @POST("artist/me/picture")
    suspend fun artistUploadPicture(
        @Header("Authorization") auth: String,
        @Part file: MultipartBody.Part
    ): Response<ArtistDto>

    @GET("artists")
    suspend fun getArtists(): Response<List<ArtistPublicDto>>

    @GET("artists/{artistId}")
    suspend fun getArtist(@Path("artistId") artistId: String): Response<ArtistPublicDto>

    // Admin-Verwaltung (nutzt den normalen Lethe-User-Token via Interceptor; Backend prüft is_admin).
    @GET("admin/artists")
    suspend fun adminGetArtists(): Response<List<ArtistDto>>

    @PATCH("admin/artists/{artistId}/approve")
    suspend fun adminApproveArtist(
        @Path("artistId") artistId: String,
        @Body request: ArtistApprovalRequest
    ): Response<ArtistDto>

    @PATCH("admin/artists/{artistId}/block")
    suspend fun adminBlockArtist(
        @Path("artistId") artistId: String,
        @Body request: ArtistBlockRequest
    ): Response<ArtistDto>

    @PUT("admin/artists/{artistId}")
    suspend fun adminUpdateArtist(
        @Path("artistId") artistId: String,
        @Body request: ArtistUpdateRequest
    ): Response<ArtistDto>

    @DELETE("admin/artists/{artistId}")
    suspend fun adminDeleteArtist(@Path("artistId") artistId: String): Response<Unit>

    @GET("admin/bpm-status")
    suspend fun adminBpmStatus(): Response<BpmStatusDto>
}
