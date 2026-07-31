package com.securechat.app.backup

import android.accounts.Account
import android.app.Activity
import android.content.Intent

/**
 * Google-Sign-In-Anbindung für den Google-Drive-Backup-Export (F-Droid-Umbau, Phase F4).
 *
 * MainActivity registriert nur den generischen, Google-freien
 * `ActivityResultContracts.StartActivityForResult()`-Launcher und delegiert den Intent-Aufbau
 * sowie die Ergebnis-Auswertung an diese Schnittstelle, damit `src/main` frei von jeder
 * `com.google.android.gms.auth.api.signin.*`-Abhängigkeit bleibt (play-services-auth liegt
 * ausschließlich im `playstoreImplementation`-Scope).
 *
 * Der eigentliche Drive-Upload (`MainViewModel.exportToGoogleDrive`) bleibt in `src/main`,
 * da er nur `google-api-client-android`/`google-api-services-drive` nutzt (Apache-2.0-REST-
 * Clients ohne GMS-Abhängigkeit) und ein bereits erhaltenes [android.accounts.Account] entgegennimmt.
 *
 * playstore: echter Google-Sign-In-Dialog ([com.securechat.app.backup.PlaystoreGoogleAuthProvider]).
 * foss: nicht verfügbar ([com.securechat.app.backup.FossGoogleAuthProvider]) – Google-Drive-Backup
 * ist im foss-Build ausgeblendet (siehe AppSettingsScreen `BuildConfig.IS_FOSS`-Guard).
 */
interface GoogleAuthProvider {
    /** true, wenn Google-Drive-Backup in diesem Build angeboten wird. */
    val isAvailable: Boolean

    /** Baut den Sign-In-Intent für den Drive-Backup-Scope. null im foss-Build. */
    fun buildSignInIntent(activity: Activity): Intent?

    /**
     * Wertet das Ergebnis des Sign-In-Intents aus.
     * @return das angemeldete [Account] bei Erfolg, sonst null (mit Fehlermeldung via [onError]).
     */
    fun handleSignInResult(resultData: Intent?, onError: (String) -> Unit): Account?
}
