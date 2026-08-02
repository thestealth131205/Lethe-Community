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

/**
 * FOSS/F-Droid-Build: kein Google-Drive-Backup.
 *
 * `google-api-client-android`/`google-api-services-drive` binden transitiv `play-services-auth`
 * ein (proprietär, F-Droid-inkompatibel) und liegen daher ausschließlich im
 * `playstoreImplementation`-Scope. Der Google-Drive-Menüpunkt ist im foss-Build bereits in
 * AppSettingsScreen ausgeblendet (`BuildConfig.IS_FOSS`-Guard); diese Implementierung ist eine
 * reine Absicherung, falls sie doch aufgerufen wird.
 */
class FossDriveBackupProvider @Inject constructor() : DriveBackupProvider {

    override suspend fun uploadBackup(
        context: Context,
        account: Account,
        file: File,
        onProgress: (Float) -> Unit
    ): Result<Unit> =
        Result.failure(UnsupportedOperationException("Google-Drive-Backup ist in dieser Version nicht verfügbar."))
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DriveBackupProviderModule {
    @Binds
    @Singleton
    abstract fun bindDriveBackupProvider(impl: FossDriveBackupProvider): DriveBackupProvider
}
