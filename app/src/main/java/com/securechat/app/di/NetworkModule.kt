package com.securechat.app.di

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.securechat.app.BuildConfig
import com.securechat.app.data.network.ApiService
import com.securechat.app.data.network.AuthInterceptor
import com.securechat.app.data.network.FallbackInterceptor
import com.securechat.app.data.network.GiphyService
import com.securechat.app.data.network.TokenAuthenticator
import com.securechat.app.data.network.TokenManager
import com.securechat.app.data.network.TorProxyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val SERVER_DOMAIN = "letheapp.de"
    private const val PROTOCOL = "https"

    // URL: https://letheapp.de/
    private const val HTTP_URL = "$PROTOCOL://$SERVER_DOMAIN/"

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            // Nur im Debug-Build aktiv – im Release-Build keine Token oder Inhalte loggen
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenManager: TokenManager): AuthInterceptor {
        return AuthInterceptor(tokenManager)
    }

    @Provides
    @Singleton
    fun provideTokenAuthenticator(tokenManager: TokenManager): TokenAuthenticator {
        return TokenAuthenticator(tokenManager)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
        fallbackInterceptor: FallbackInterceptor,
        torProxyManager: TorProxyManager
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .authenticator(tokenAuthenticator)     // Automatischer Refresh bei 401
            .addInterceptor(loggingInterceptor)    // Zuerst loggen (vor Token-Injektion)
            .addInterceptor(authInterceptor)       // Dann Token hinzufügen
            .addInterceptor(fallbackInterceptor)   // Bei Verbindungsausfall → Backup/Onion
            // Dynamischer ProxySelector: im TOR_ONLY-Modus SOCKS5 via Orbot
            .proxySelector(torProxyManager.proxySelector)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            // Tor-Verbindungen können länger dauern (Onion-Routing)
            .connectTimeout(90, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(HTTP_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    /** Einfacher OkHttpClient ohne Auth-Interceptors – für externe APIs wie Giphy. */
    @Provides
    @Singleton
    @Named("plain")
    fun providePlainOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** Retrofit-Instanz für die Giphy API (api.giphy.com). */
    @Provides
    @Singleton
    fun provideGiphyService(@Named("plain") plainClient: OkHttpClient): GiphyService {
        return Retrofit.Builder()
            .baseUrl("https://api.giphy.com/")
            .client(plainClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GiphyService::class.java)
    }

    /**
     * Coil ImageLoader mit hartem Memory- und Disk-Cache.
     * - MemoryCache: 25 % des verfügbaren RAM (typ. 50–100 MB)
     * - DiskCache: 150 MB auf dem internen Speicher
     * - respectCacheHeaders = false → ignoriert No-Cache-Header des Servers
     * - crossfade = true → weicher Ladeübergang
     */
    @Provides
    @Singleton
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            // Decode-/Fetch-Parallelität hart begrenzen. Coil nutzt sonst Dispatchers.IO
            // (bis zu 64 parallel). Beim schnellen Scrollen durch einen langen, medienreichen
            // Chat werden viele bereits freigegebene Bild-Bubbles in rascher Folge komponiert →
            // jede stößt sofort eine 640×640-Dekodierung an → dutzende volle Bitmaps gleichzeitig
            // im Speicher → Allokations-Lawine → OutOfMemoryError. Mit max. 2 Decodes /4 Fetches
            // gleichzeitig kann der transiente Spitzenverbrauch nicht mehr den Heap sprengen;
            // nicht mehr sichtbare Bubbles werden vor ihrem Decode wieder verworfen (Coil canceln).
            .decoderDispatcher(Dispatchers.IO.limitedParallelism(2))
            .fetcherDispatcher(Dispatchers.IO.limitedParallelism(4))
            .components {
                // GIF-Unterstützung: API 28+ nutzt den schnellen ImageDecoderDecoder,
                // ältere Geräte (API 26–27) nutzen den Software-GifDecoder
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.15)  // Reduziert von 25% auf 15% um OOM-Risiko zu minimieren
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("lethe_image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150 MB
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
}