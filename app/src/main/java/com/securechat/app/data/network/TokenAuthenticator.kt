package com.securechat.app.data.network

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Authenticator: wird automatisch aufgerufen wenn der Server 401 antwortet.
 * Versucht einmalig das Token zu erneuern (POST /refresh) und wiederholt dann den Request.
 * Schlägt der Refresh fehl, wird null zurückgegeben → User muss sich neu einloggen.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager
) : Authenticator {

    // Header-Marker, um Endlosschleifen zu verhindern (Refresh darf nicht nochmal refreshen)
    private val REFRESH_HEADER = "X-Token-Refreshed"

    override fun authenticate(route: Route?, response: Response): Request? {
        // Bereits versucht zu refreshen → aufgeben
        if (response.request.header(REFRESH_HEADER) != null) return null

        val currentToken = tokenManager.getToken() ?: return null

        synchronized(this) {
            // Prüfen ob ein anderer Thread das Token bereits erneuert hat
            val latestToken = tokenManager.getToken()
            if (latestToken != null && latestToken != currentToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $latestToken")
                    .header(REFRESH_HEADER, "true")
                    .build()
            }

            // Synchroner Refresh-Call (ohne Retrofit, um Circular-Dependency zu vermeiden)
            val refreshRequest = okhttp3.Request.Builder()
                .url("https://letheapp.de/refresh")
                .post(ByteArray(0).toRequestBody(null))
                .header("Authorization", "Bearer $currentToken")
                .build()

            return try {
                val refreshResponse = response.request.url.let {
                    okhttp3.OkHttpClient().newCall(refreshRequest).execute()
                }

                if (!refreshResponse.isSuccessful) {
                    val code = refreshResponse.code
                    refreshResponse.close()
                    // NUR bei echter Auth-Ablehnung (401/403) die Sitzung beenden. Transiente
                    // Serverfehler (5xx, z.B. während eines Rolling-Restarts der Backend-Instanzen)
                    // oder Rate-Limits dürfen NICHT zum Logout führen – sonst sperrt ein kurzer
                    // Server-Hänger den User dauerhaft aus, bis er die App neu startet bzw. sich
                    // neu anmeldet. Bei transienten Fehlern bleibt das Token erhalten und ein
                    // späterer Request / WS-Reconnect kann sich erholen.
                    if (code == 401 || code == 403) {
                        tokenManager.clearToken()
                        tokenManager.signalSessionExpired()
                    }
                    return null
                }

                val body = refreshResponse.body?.string() ?: return null
                val json = JSONObject(body)
                val newToken = json.getString("access_token")
                val userId = json.getString("user_id")

                tokenManager.saveToken(newToken, userId)

                response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .header(REFRESH_HEADER, "true")
                    .build()
            } catch (e: Exception) {
                null
            }
        }
    }
}
