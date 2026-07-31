package com.securechat.app.backup

import android.accounts.Account
import android.app.Activity
import android.content.Intent
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FOSS/F-Droid-Build: kein Google-Sign-In, kein Google-Drive-Backup.
 *
 * `play-services-auth` ist proprietär und für F-Droid nicht zulässig. Der Google-Drive-
 * Menüpunkt ist im foss-Build bereits in AppSettingsScreen ausgeblendet
 * (`BuildConfig.IS_FOSS`-Guard); diese Implementierung ist eine reine Absicherung,
 * falls sie doch aufgerufen wird.
 */
class FossGoogleAuthProvider @Inject constructor() : GoogleAuthProvider {

    override val isAvailable: Boolean = false

    override fun buildSignInIntent(activity: Activity): Intent? = null

    override fun handleSignInResult(resultData: Intent?, onError: (String) -> Unit): Account? {
        onError("Google-Drive-Backup ist in dieser Version nicht verfügbar.")
        return null
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GoogleAuthProviderModule {
    @Binds
    @Singleton
    abstract fun bindGoogleAuthProvider(impl: FossGoogleAuthProvider): GoogleAuthProvider
}
