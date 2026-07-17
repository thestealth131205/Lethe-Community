package com.lethe.mediaplayer.data

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brücke zwischen [com.lethe.mediaplayer.MainActivity] (empfängt ACTION_VIEW-Intents, z.B.
 * "Öffnen mit" aus einem Dateimanager oder Musik-Auswahldialog von Android) und dem
 * [com.lethe.mediaplayer.ui.PlayerViewModel] (kopiert die Datei in den SmartCache-Ordner
 * und startet die Wiedergabe, sobald die Lethe-Session bereit ist).
 */
@Singleton
class ExternalAudioImportBridge @Inject constructor() {
    private val _pendingUri = MutableStateFlow<Uri?>(null)
    val pendingUri: StateFlow<Uri?> = _pendingUri

    fun submit(uri: Uri) {
        _pendingUri.value = uri
    }

    fun consume() {
        _pendingUri.value = null
    }
}
