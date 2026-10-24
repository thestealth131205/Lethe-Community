package com.securechat.app.push

import com.securechat.app.data.network.ApiService
import com.securechat.app.data.network.FcmTokenRequest
import com.securechat.app.data.network.IrisTokenGenerateRequest
import com.securechat.app.data.network.TokenManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Push-Registrierung über Iris – ersetzt FCM in BEIDEN Flavors (playstore + foss), damit
 * Lethe Messenger unabhängig von Google-Push-Diensten bleibt. Erzeugt (einmalig pro Account,
 * siehe TokenManager.getIrisTokenOwnerId()) über den authentifizierten /push/tokens-Endpunkt
 * einen an den eigenen Account gebundenen Iris-Token und hält darüber die persistente
 * [IrisConnection] offen – die eigentliche Verbindung wird vom NotificationHandler-
 * Foreground-Service am Leben gehalten (ensureConnected()/ping() im Ping-Tick).
 *
 * Eingehende Payloads landen transport-unabhängig wieder bei [PushPayloadHandler] – derselben
 * Stelle, die zuvor LetheFcmService (FCM) bzw. der reine WS-Pfad (FOSS) genutzt haben.
 */
class IrisPushProvider @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
    private val irisConnection: IrisConnection,
) : PushProvider {

    override fun registerToken() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val myUserId = tokenManager.getUserId()
                val existingToken = tokenManager.getIrisToken()
                val token = if (existingToken != null && tokenManager.getIrisTokenOwnerId() == myUserId) {
                    existingToken
                } else {
                    val resp = apiService.createIrisToken(
                        IrisTokenGenerateRequest(packageName = com.securechat.app.BuildConfig.APPLICATION_ID)
                    )
                    val body = resp.body()
                    if (!resp.isSuccessful || body == null) {
                        Timber.tag("LETHE_IRIS").w("Iris-Token-Erzeugung fehlgeschlagen: ${resp.code()}")
                        return@launch
                    }
                    if (myUserId != null) tokenManager.saveIrisToken(body.token, myUserId)
                    body.token
                }
                apiService.updateFcmToken(FcmTokenRequest(fcmToken = null, pushKind = "iris"))
                irisConnection.ensureConnected(token)
                Timber.tag("LETHE_IRIS").d("Iris-Push registriert und verbunden.")
            } catch (e: Exception) {
                Timber.tag("LETHE_IRIS").w(e, "Iris-Push-Registrierung fehlgeschlagen")
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PushProviderModule {
    @Binds
    @Singleton
    abstract fun bindPushProvider(impl: IrisPushProvider): PushProvider
}
