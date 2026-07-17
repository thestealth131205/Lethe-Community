package com.securechat.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Collections

/**
 * Singleton-Bus zwischen LetheFcmService und MainViewModel.
 *
 * Zwei Aufgaben:
 * 1. Wenn eine FCM-Nachricht eintrifft und WS die Nachricht nicht geliefert hat,
 *    emittiert der FCM-Service hier die senderId. Das ViewModel lädt die Nachricht
 *    per REST nach.
 * 2. FCM-Benachrichtigungs-Dedup: FCM registriert gezeigte messageIds damit der
 *    WS-Handler keine zweite Benachrichtigung für dieselbe Nachricht anzeigt.
 */
object FcmMessageBus {
    private val _newMessageSenderIds = MutableSharedFlow<String>(replay = 32, extraBufferCapacity = 32)
    val newMessageSenderIds: SharedFlow<String> = _newMessageSenderIds.asSharedFlow()

    /** Anrufer-IDs deren Anruf von der Gegenseite beendet/abgebrochen wurde (FCM call_cancelled). */
    private val _callCancelledCallerIds = MutableSharedFlow<String>(replay = 4, extraBufferCapacity = 8)
    val callCancelledCallerIds: SharedFlow<String> = _callCancelledCallerIds.asSharedFlow()

    /** messageIds für die FCM bereits eine Benachrichtigung gezeigt hat. */
    private val fcmNotifiedIds: MutableSet<String> =
        Collections.synchronizedSet(LinkedHashSet())
    private const val MAX_TRACKED = 200

    fun notifyNewMessage(senderId: String) {
        _newMessageSenderIds.tryEmit(senderId)
    }

    /** Wird vom FCM-Handler aufgerufen wenn die Gegenseite den Anruf beendet hat (call_cancelled). */
    fun notifyCallCancelled(callerId: String) {
        _callCancelledCallerIds.tryEmit(callerId)
    }

    /** Wird vom FCM-Handler aufgerufen nachdem eine Benachrichtigung angezeigt wurde. */
    fun markNotificationShown(messageId: String) {
        if (fcmNotifiedIds.size >= MAX_TRACKED) {
            fcmNotifiedIds.remove(fcmNotifiedIds.iterator().next())
        }
        fcmNotifiedIds.add(messageId)
    }

    /** Gibt true zurück wenn FCM für diese messageId bereits eine Benachrichtigung gezeigt hat. */
    fun wasNotificationShownByFcm(messageId: String): Boolean =
        fcmNotifiedIds.contains(messageId)
}
