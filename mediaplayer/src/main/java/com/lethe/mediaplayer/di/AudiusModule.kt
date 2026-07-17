package com.lethe.mediaplayer.di

import com.lethe.mediaplayer.data.AudiusApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Separater Client für die Audius-API (kein Lethe-Auth-Interceptor, kein JWT).
 * followRedirects/followSslRedirects müssen true sein, da der Stream-Endpunkt
 * per HTTP-303-Redirect auf eine CDN-MP3-URL verweist.
 */
@Module
@InstallIn(SingletonComponent::class)
object AudiusModule {

    @Provides
    @Singleton
    @Named("audius")
    fun provideAudiusOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    @Provides
    @Singleton
    fun provideAudiusApiService(@Named("audius") client: OkHttpClient): AudiusApiService =
        Retrofit.Builder()
            .baseUrl("https://api.audius.co/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AudiusApiService::class.java)
}
