package com.securechat.app.contacts

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder

/**
 * Account-Typ für die Lethe-Kontakte-Integration.
 * Muss exakt mit res/xml/authenticator.xml + sync_contacts.xml übereinstimmen.
 */
const val LETHE_ACCOUNT_TYPE = "com.Lethe.app"

/** Anzeigename des einzigen Lethe-Accounts, an den alle RawContacts gehängt werden. */
const val LETHE_ACCOUNT_NAME = "Lethe"

/**
 * Minimaler AbstractAccountAuthenticator – der Lethe-Account dient nur als Container
 * für die RawContacts (damit Lethe unter "Verbundene Apps" erscheint). Es gibt keine
 * echte Anmeldung über AccountManager, daher liefern fast alle Methoden leere/null-Antworten.
 */
class LetheAccountAuthenticator(context: Context) : AbstractAccountAuthenticator(context) {

    override fun addAccount(
        response: AccountAuthenticatorResponse?,
        accountType: String?,
        authTokenType: String?,
        requiredFeatures: Array<out String>?,
        options: Bundle?
    ): Bundle = Bundle()

    override fun editProperties(
        response: AccountAuthenticatorResponse?,
        accountType: String?
    ): Bundle = Bundle()

    override fun confirmCredentials(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        options: Bundle?
    ): Bundle? = null

    override fun getAuthToken(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        authTokenType: String?,
        options: Bundle?
    ): Bundle = Bundle().apply {
        putParcelable(AccountManager.KEY_INTENT, Intent())
    }

    override fun getAuthTokenLabel(authTokenType: String?): String? = null

    override fun updateCredentials(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        authTokenType: String?,
        options: Bundle?
    ): Bundle? = null

    override fun hasFeatures(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        features: Array<out String>?
    ): Bundle = Bundle().apply {
        putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false)
    }
}

/**
 * Bind-Service, der den Authenticator dem System bereitstellt (AccountManager-Framework).
 */
class LetheAuthenticatorService : Service() {
    private lateinit var authenticator: LetheAccountAuthenticator

    override fun onCreate() {
        super.onCreate()
        authenticator = LetheAccountAuthenticator(this)
    }

    override fun onBind(intent: Intent?): IBinder = authenticator.iBinder
}
