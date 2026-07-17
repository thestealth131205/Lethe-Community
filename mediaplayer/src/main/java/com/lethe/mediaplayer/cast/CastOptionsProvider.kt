package com.lethe.mediaplayer.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/** Nutzt dieselbe Lethe-Cast-Receiver-App wie die Haupt-App. */
class CastOptionsProvider : OptionsProvider {

    companion object {
        const val RECEIVER_APP_ID = "8622B21C"
    }

    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(RECEIVER_APP_ID)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
