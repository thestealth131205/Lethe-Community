package com.securechat.app

/**
 * Singleton-Speicher für einen ausstehenden eingehenden Anruf.
 * Wird genutzt, um Anrufdaten zwischen LetheFcmService / MainViewModel
 * und der IncomingCallActivity zu teilen – auch wenn die Activity
 * nicht zum gleichen Stack gehört (Lock-Screen-Start via FCM).
 *
 * Lebensdauer: solange der App-Prozess läuft.
 * Wird geleert, sobald der Anruf angenommen, abgelehnt oder beendet wird.
 */
object IncomingCallStore {

    data class PendingCall(
        val callerId: String,
        val callerName: String,
        val callerImageUrl: String?,
        val sdpOffer: String,
        val callType: String,          // "VIDEO" | "VOICE"
        val isGroupCall: Boolean = false,
        val groupParticipants: List<Pair<String, String?>> = emptyList(),
        val groupId: String? = null,
        val groupName: String? = null
    )

    @Volatile
    var pendingCall: PendingCall? = null

    /** Löscht den gespeicherten Anruf (nach Annehmen / Ablehnen / Timeout). */
    fun clear() {
        pendingCall = null
    }
}
