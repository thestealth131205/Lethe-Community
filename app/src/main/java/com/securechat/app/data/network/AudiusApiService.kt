package com.securechat.app.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

// ─────────────────────────────────────────────────────────────────────────────
// Audius Music API – Retrofit Service Interface
//
// Alle Such- und Trending-Endpunkte nutzen @Url, da der tatsächliche Host-Node
// zur Laufzeit via Discovery (GET https://api.audius.co/) ermittelt wird.
// Der Retrofit-Client hat https://api.audius.co/ als Base-URL (nur als Fallback).
//
// Verwendung im ViewModel:
//   val searchUrl = "$resolvedHost/v1/tracks/search"
//   audiusApiService.searchTracks(url = searchUrl, query = "chill")
//
// Stream-URLs werden NICHT über Retrofit abgerufen – der Pfad wird direkt
// als String gebaut und an ExoPlayer (Vorschau) bzw. FFmpeg (Export) übergeben:
//   "$resolvedHost/v1/tracks/$trackId/stream?app_name=LetheApp"
// ─────────────────────────────────────────────────────────────────────────────

interface AudiusApiService {

    /**
     * Sucht Tracks auf Audius nach einem Suchbegriff.
     *
     * @param url      Vollständige URL des gewählten Host-Nodes, z.B.:
     *                 "https://discoveryprovider.audius.co/v1/tracks/search"
     * @param query    Suchbegriff (z.B. "chill", "epic", "electronic").
     * @param appName  App-Identifikator für Audius-Analytics-Compliance.
     */
    @GET
    suspend fun searchTracks(
        @Url    url: String,
        @Query("query")    query: String,
        @Query("app_name") appName: String = "LetheApp"
    ): Response<AudiusSearchResponse>

    /**
     * Lädt Trending-Tracks ohne Suchbegriff (für die Startansicht des Pickers).
     *
     * @param url     Vollständige URL des gewählten Host-Nodes, z.B.:
     *                "https://discoveryprovider.audius.co/v1/tracks/trending"
     * @param appName App-Identifikator für Audius-Analytics-Compliance.
     */
    @GET
    suspend fun getTrendingTracks(
        @Url    url: String,
        @Query("app_name") appName: String = "LetheApp"
    ): Response<AudiusSearchResponse>
}
