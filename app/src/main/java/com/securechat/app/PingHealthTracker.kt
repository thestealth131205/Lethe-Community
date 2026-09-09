package com.securechat.app

/**
 * Trackt die letzten zwei Ping-Ergebnisse des primären NotificationHandler-Ping-Loops.
 * Der (aktuell nicht gestartete) WebSocketKeepaliveService nutzt dies, um einen redundanten
 * eigenen Ping auszusetzen, solange NotificationHandler bereits eine gesunde Verbindung bestätigt.
 */
object PingHealthTracker {
    @Volatile private var lastSuccess: Boolean = false
    @Volatile private var secondLastSuccess: Boolean = false

    @Synchronized
    fun record(success: Boolean) {
        secondLastSuccess = lastSuccess
        lastSuccess = success
    }

    /** true, wenn die letzten beiden erfassten Pings beide erfolgreich (WS verbunden) waren. */
    @Synchronized
    fun lastTwoSuccessful(): Boolean = lastSuccess && secondLastSuccess
}
