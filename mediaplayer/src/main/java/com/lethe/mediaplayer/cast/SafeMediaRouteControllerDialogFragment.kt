package com.lethe.mediaplayer.cast

import android.content.Context
import android.os.Bundle
import android.view.ContextThemeWrapper
import androidx.mediarouter.app.MediaRouteControllerDialog
import androidx.mediarouter.app.MediaRouteControllerDialogFragment
import com.lethe.mediaplayer.R

/**
 * Analog zu SafeMediaRouteChooserDialogFragment, aber für den Controller-Dialog, der
 * bei aktiver Cast-Session statt des Chooser-Dialogs geöffnet wird. Erzeugt den
 * MediaRouteControllerDialog mit einem AppCompat-Theme mit opakem colorBackground,
 * damit MediaRouterThemeHelper.getControllerColor() nicht mit
 * "background can not be translucent: #0" crasht.
 */
class SafeMediaRouteControllerDialogFragment : MediaRouteControllerDialogFragment() {
    override fun onCreateControllerDialog(
        context: Context,
        savedInstanceState: Bundle?
    ): MediaRouteControllerDialog {
        val themedContext = ContextThemeWrapper(context, R.style.Theme_LetheMediaPlayer_MediaRouter)
        return MediaRouteControllerDialog(themedContext, R.style.Theme_LetheMediaPlayer_MediaRouter)
    }
}
