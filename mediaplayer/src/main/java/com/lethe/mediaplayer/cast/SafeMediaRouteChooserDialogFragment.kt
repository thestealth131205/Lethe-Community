package com.lethe.mediaplayer.cast

import android.app.Dialog
import android.os.Bundle
import android.view.ContextThemeWrapper
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import com.lethe.mediaplayer.R

/**
 * Umgeht den Crash in MediaRouterThemeHelper.getControllerColor(), der auftritt wenn
 * android:colorBackground im Activity-Theme als transparent (0) aufgelöst wird.
 * Override von onCreateDialog statt onCreateChooserDialog, damit der gesamte
 * problematische Codepfad der Basisklasse umgangen wird. Analog zu
 * com.securechat.app.cast.SafeMediaRouteChooserDialogFragment der Haupt-App
 * (derselbe Fix, da CastButtonFactory.setUpMediaRouteButton() den Klick sonst über
 * MediaRouteButton.showDialog() an die vanilla MediaRouteChooserDialogFragment weiterreicht,
 * die das Activity-Theme unabhängig vom ContextThemeWrapper des Buttons auswertet).
 */
class SafeMediaRouteChooserDialogFragment : MediaRouteChooserDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val themedContext = ContextThemeWrapper(
            requireContext(),
            R.style.Theme_LetheMediaPlayer_MediaRouter
        )
        val dialog = MediaRouteChooserDialog(themedContext, R.style.Theme_LetheMediaPlayer_MediaRouter)
        dialog.routeSelector = routeSelector
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        return dialog
    }
}
