package com.securechat.app.push

/**
 * Transport-unabhängige Push-Registrierung (F-Droid-Umbau, Phase F2).
 *
 * Der `playstore`-Flavor implementiert das über Firebase Cloud Messaging
 * ([com.securechat.app.push.FcmPushProvider]) und registriert den FCM-Token am Server.
 * Der `foss`/F-Droid-Flavor hält stattdessen den WebSocket über den persistenten
 * Foreground-Service offen ([com.securechat.app.push.FossPushProvider]) und braucht
 * keinen Push-Token → no-op.
 *
 * MainViewModel ruft nur diese Schnittstelle auf, damit `src/main` frei von jeder
 * Firebase-Abhängigkeit bleibt (Firebase liegt ausschließlich im
 * `playstoreImplementation`-Scope).
 */
interface PushProvider {
    /** Registriert – falls für den Transport nötig – den Push-Token am Server. */
    fun registerToken()
}
