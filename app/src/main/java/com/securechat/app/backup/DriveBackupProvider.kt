package com.securechat.app.backup

import android.accounts.Account
import android.content.Context
import java.io.File

/**
 * Lädt eine bereits lokal erstellte Backup-Datei zu Google Drive hoch (F-Droid-Umbau, Phase F4).
 *
 * `MainViewModel.exportToGoogleDrive` erstellt das verschlüsselte Backup lokal (kein Google-
 * Bezug) und delegiert nur den eigentlichen Upload an diese Schnittstelle, damit `src/main`
 * frei von jeder `com.google.api.client.*`/`com.google.api.services.drive.*`-Abhängigkeit
 * bleibt (der google-api-client-Android-Adapter bindet transitiv `play-services-auth` ein
 * und liegt ausschließlich im `playstoreImplementation`-Scope).
 *
 * playstore: echter Google-Drive-REST-Upload ([com.securechat.app.backup.PlaystoreDriveBackupProvider]).
 * foss: nicht verfügbar ([com.securechat.app.backup.FossDriveBackupProvider]) – Google-Drive-
 * Backup ist im foss-Build ausgeblendet (siehe AppSettingsScreen `BuildConfig.IS_FOSS`-Guard).
 */
interface DriveBackupProvider {
    /**
     * Lädt [file] in den Ordner "Lethe-Backups" des per [account] angemeldeten Google-Drive-
     * Kontos hoch (Ordner wird bei Bedarf erstellt). [onProgress] meldet Fortschritt 0f..1f.
     */
    suspend fun uploadBackup(
        context: Context,
        account: Account,
        file: File,
        onProgress: (Float) -> Unit
    ): Result<Unit>
}
