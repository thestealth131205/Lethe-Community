package com.lethe.mediaplayer.data

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Audius Music API – öffentliche API, kein Lethe-Server-Auth-Token.
 * Such-/Trending-Endpunkte nutzen @Url, da der tatsächliche Host-Node zur Laufzeit
 * via Discovery (GET https://api.audius.co/) ermittelt wird.
 */
interface AudiusApiService {

    @GET
    suspend fun discoverHosts(
        @Url url: String = "https://api.audius.co/"
    ): Response<AudiusHostsResponse>

    @GET
    suspend fun searchTracks(
        @Url url: String,
        @Query("query") query: String,
        @Query("app_name") appName: String = "LetheMediaPlayer"
    ): Response<AudiusSearchResponse>

    @GET
    suspend fun getTrendingTracks(
        @Url url: String,
        @Query("genre") genre: String? = null,
        @Query("app_name") appName: String = "LetheMediaPlayer"
    ): Response<AudiusSearchResponse>
}
