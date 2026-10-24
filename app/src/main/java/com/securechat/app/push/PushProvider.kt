package com.securechat.app.push

/**
 * Transport-unabhängige Push-Registrierung.
 *
 * Einzige Implementierung ist [com.securechat.app.push.IrisPushProvider] – identisch für
 * `playstore` und `foss`, registriert einen an den Account gebundenen Iris-Token
 * (/push/tokens) und hält darüber die FCM-unabhängige Push-Zustellung offen.
 */
interface PushProvider {
    /** Registriert – falls für den Transport nötig – den Push-Token am Server. */
    fun registerToken()
}
