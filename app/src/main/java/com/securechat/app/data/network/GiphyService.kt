package com.securechat.app.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit-Interface für die Giphy API (api.giphy.com).
 * Wird über einen separaten Retrofit-Client ohne Auth-Interceptor aufgerufen.
 */
interface GiphyService {

    @GET("v1/gifs/trending")
    suspend fun trending(
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 24,
        @Query("rating") rating: String = "g"
    ): Response<GiphyResponse>

    @GET("v1/gifs/search")
    suspend fun search(
        @Query("api_key") apiKey: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 24,
        @Query("rating") rating: String = "g"
    ): Response<GiphyResponse>

    @GET("v1/emoji")
    suspend fun emojiTrending(
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 24
    ): Response<GiphyResponse>

    @GET("v1/emoji/search")
    suspend fun emojiSearch(
        @Query("api_key") apiKey: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 24
    ): Response<GiphyResponse>

    @GET("v1/stickers/trending")
    suspend fun stickersTrending(
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 24,
        @Query("rating") rating: String = "g"
    ): Response<GiphyResponse>

    @GET("v1/stickers/search")
    suspend fun stickersSearch(
        @Query("api_key") apiKey: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 24,
        @Query("rating") rating: String = "g"
    ): Response<GiphyResponse>
}
