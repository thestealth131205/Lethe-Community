package com.securechat.app.cast

import android.app.Dialog
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import com.securechat.app.R

/**
 * Umgeht den Crash in MediaRouterThemeHelper.getControllerColor(), der auftritt wenn
 * android:colorBackground im Activity-Theme als transparent (0) aufgelöst wird.
 * Override von onCreateDialog statt onCreateChooserDialog, damit der gesamte
 * problematische Codepfad der Basisklasse umgangen wird.
 * Zusätzlich: Schließen-Button (✕) oben rechts im Dialog.
 */
class SafeMediaRouteChooserDialogFragment : MediaRouteChooserDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val themedContext = ContextThemeWrapper(
            requireContext(),
            R.style.Theme_SecureChat_MediaRouter
        )
        val dialog = MediaRouteChooserDialog(themedContext, R.style.Theme_SecureChat_MediaRouter)
        dialog.routeSelector = routeSelector
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        try {
            val decorView = dialog?.window?.decorView as? ViewGroup ?: return
            // Verhindert doppeltes Einfügen des Close-Buttons bei Konfigurationsänderungen
            if (decorView.findViewWithTag<ImageButton>("cast_close_btn") != null) return
            val ctx = requireContext()
            val dp = ctx.resources.displayMetrics.density
            val closeBtn = ImageButton(ctx).apply {
                tag = "cast_close_btn"
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                background = null
                val p = (10 * dp).toInt()
                setPadding(p, p, p, p)
                contentDescription = "Schließen"
                setOnClickListener { dismissAllowingStateLoss() }
            }
            val params = FrameLayout.LayoutParams(
                (36 * dp).toInt(),
                (36 * dp).toInt()
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                val margin = (8 * dp).toInt()
                setMargins(margin, margin, margin, margin)
            }
            decorView.addView(closeBtn, params)
        } catch (_: Exception) {}
    }
}
