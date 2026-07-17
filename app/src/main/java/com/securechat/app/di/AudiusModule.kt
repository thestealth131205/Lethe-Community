package com.securechat.app.di

import com.securechat.app.data.network.AudiusApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────────────────
// Hilt-Modul für den Audius-API-Client.
//
// Separater OkHttpClient ohne Lethe-Server-Auth-Interceptors, damit Audius-
// Anfragen keine JWT-Token enthalten.
//
// Der Retrofit-Client nutzt https://api.audius.co/ als Base-URL, aber alle
// Suchanfragen überschreiben diese via @Url mit dem dynamisch ermittelten
// Host-Node. followRedirects(true) ist notwendig, da Audius-Stream-Endpunkte
// auf CDN-URLs weiterleiten.
// ─────────────────────────────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
object AudiusModule {

    // Retrofit Base-URL: nur für Discovery und @Url-Endpunkte als Fallback
    private const val AUDIUS_BASE_URL = "https://api.audius.co/"

    /**
     * Separater OkHttpClient für Audius (kein Auth-Interceptor, kein Token).
     * followRedirects und followSslRedirects müssen true sein, da die
     * Stream-Endpunkte HTTP-303-Redirects auf CDN-MP3-URLs senden.
     */
    @Provides
    @Singleton
    @Named("audius")
    fun provideAudiusOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Retrofit-Service für die Audius Track-Suche und Trending-Endpunkte.
     * Base-URL = https://api.audius.co/ (Fallback; wird bei @Url-Calls überschrieben).
     */
    @Provides
    @Singleton
    fun provideAudiusApiService(@Named("audius") client: OkHttpClient): AudiusApiService {
        return Retrofit.Builder()
            .baseUrl(AUDIUS_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AudiusApiService::class.java)
    }
}
