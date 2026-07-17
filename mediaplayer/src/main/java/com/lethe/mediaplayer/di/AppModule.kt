package com.lethe.mediaplayer.di

import com.lethe.mediaplayer.BuildConfig
import com.lethe.mediaplayer.auth.SessionBridge
import com.lethe.mediaplayer.data.LetheMediaApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val BASE_URL = "https://letheapp.de/"

    @Provides
    @Singleton
    fun provideOkHttpClient(sessionBridge: SessionBridge): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        // Fügt das aus der Lethe-Session gelesene JWT an jede Anfrage an – aber nur, wenn die
        // Anfrage nicht bereits einen eigenen Authorization-Header trägt (z.B. Künstler-Token
        // bei den /artist/me-Endpoints). So überschreibt der Lethe-Token den Künstler-Token nicht.
        val auth = Interceptor { chain ->
            val original = chain.request()
            val token = sessionBridge.cachedToken
            val req = if (original.header("Authorization") == null && !token.isNullOrBlank()) {
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else original
            chain.proceed(req)
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(auth)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): LetheMediaApi =
        retrofit.create(LetheMediaApi::class.java)
}
