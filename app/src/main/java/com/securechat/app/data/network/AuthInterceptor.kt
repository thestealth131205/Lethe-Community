package com.securechat.app.data.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Interceptor, der automatisch das Authorization-Token zu jedem API-Request hinzufügt.
 * Wird vom OkHttpClient verwendet, bevor Requests an den Server gesendet werden.
 */
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Hole das gespeicherte Token
        val token = tokenManager.getToken()

        // Wenn kein Token vorhanden ist, sende Request ohne Authorization Header
        // (z.B. bei Login/Register)
        if (token.isNullOrEmpty()) {
            return chain.proceed(originalRequest)
        }

        // Füge das Token als Bearer Token zum Authorization Header hinzu
        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(authenticatedRequest)
    }
}