package com.lethe.mediaplayer.cast

import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import androidx.mediarouter.app.MediaRouteControllerDialogFragment
import androidx.mediarouter.app.MediaRouteDialogFactory

/**
 * Dialog-Factory für MediaRouteButton. WICHTIG: MediaRouteButton.performClick() ruft intern
 * IMMER showDialogInternal() auf (unabhängig von einem gesetzten OnClickListener) und erzeugt
 * dabei über die Dialog-Factory das Chooser- bzw. Controller-Fragment. Ohne diese Factory nutzt
 * der Button die vanilla-Fragmente, die das Activity-Theme statt den ContextThemeWrapper des
 * Buttons auswerten und mit "background can not be translucent: #0" crashen. Über
 * setDialogFactory(...) verwendet der eingebaute Pfad stattdessen die Safe-Fragmente.
 */
class SafeMediaRouteDialogFactory : MediaRouteDialogFactory() {
    override fun onCreateChooserDialogFragment(): MediaRouteChooserDialogFragment =
        SafeMediaRouteChooserDialogFragment()

    override fun onCreateControllerDialogFragment(): MediaRouteControllerDialogFragment =
        SafeMediaRouteControllerDialogFragment()
}
