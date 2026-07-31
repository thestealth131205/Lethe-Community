package com.securechat.app.backup

import android.accounts.Account
import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google-Sign-In für den Google-Drive-Backup-Export – nur im `playstore`-Flavor
 * (play-services-auth ist proprietär und für F-Droid nicht zulässig; siehe [FossGoogleAuthProvider]).
 */
class PlaystoreGoogleAuthProvider @Inject constructor() : GoogleAuthProvider {

    override val isAvailable: Boolean = true

    override fun buildSignInIntent(activity: Activity): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
        val client = GoogleSignIn.getClient(activity, gso)
        return client.signInIntent
    }

    override fun handleSignInResult(resultData: Intent?, onError: (String) -> Unit): Account? {
        val task = GoogleSignIn.getSignedInAccountFromIntent(resultData)
        return try {
            val account = task.getResult(ApiException::class.java)
            account?.account
        } catch (e: Exception) {
            onError("Google-Anmeldung fehlgeschlagen: ${e.message}")
            null
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GoogleAuthProviderModule {
    @Binds
    @Singleton
    abstract fun bindGoogleAuthProvider(impl: PlaystoreGoogleAuthProvider): GoogleAuthProvider
}
