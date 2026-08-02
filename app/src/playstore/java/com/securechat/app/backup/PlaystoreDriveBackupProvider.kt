package com.securechat.app.backup

import android.accounts.Account
import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google-Drive-REST-Upload für den Backup-Export – nur im `playstore`-Flavor
 * (google-api-client-android/google-api-services-drive binden transitiv `play-services-auth`
 * ein, siehe [FossDriveBackupProvider]).
 */
class PlaystoreDriveBackupProvider @Inject constructor() : DriveBackupProvider {

    override suspend fun uploadBackup(
        context: Context,
        account: Account,
        file: File,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val credential = com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
                .usingOAuth2(context, listOf("https://www.googleapis.com/auth/drive.file"))
            credential.selectedAccount = account

            val transport = com.google.api.client.http.javanet.NetHttpTransport()
            val jsonFactory = com.google.api.client.json.gson.GsonFactory.getDefaultInstance()
            val driveService = com.google.api.services.drive.Drive.Builder(transport, jsonFactory, credential)
                .setApplicationName("Lethe")
                .build()

            // Ordner "Lethe-Backups" suchen oder erstellen
            val folderQuery = driveService.files().list()
                .setQ("name='Lethe-Backups' and mimeType='application/vnd.google-apps.folder' and trashed=false")
                .setSpaces("drive")
                .execute()
            val folderId = if (folderQuery.files.isNotEmpty()) {
                folderQuery.files[0].id
            } else {
                val folderMeta = com.google.api.services.drive.model.File()
                    .setName("Lethe-Backups")
                    .setMimeType("application/vnd.google-apps.folder")
                driveService.files().create(folderMeta).setFields("id").execute().id
            }

            onProgress(0.5f)

            // Datei hochladen
            val fileMeta = com.google.api.services.drive.model.File()
                .setName(file.name)
                .setParents(listOf(folderId))
            val mediaContent = com.google.api.client.http.FileContent("application/octet-stream", file)
            driveService.files().create(fileMeta, mediaContent)
                .setFields("id, name")
                .execute()

            onProgress(1f)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DriveBackupProviderModule {
    @Binds
    @Singleton
    abstract fun bindDriveBackupProvider(impl: PlaystoreDriveBackupProvider): DriveBackupProvider
}
