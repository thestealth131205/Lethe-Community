package com.securechat.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.securechat.app.data.local.AppTheme

// ======================================================================
// Chat-spezifische Farben (fest, nicht vom User änderbar)
// ======================================================================

/** Chat-Hintergrund Hell: warmes Beige */
val ChatBgLight = Color(0xFFFAF5E8.toInt())

/** Chat-Hintergrund Dunkel: sehr dunkles Blau */
val ChatBgDark = Color(0xFF07131F.toInt())

/** Gesprächspartner-Blase Hell: reines Weiß */
val BubblePartnerLight = Color.White

/** Gesprächspartner-Blase Dunkel: Dunkelblau, etwas heller als Hintergrund */
val BubblePartnerDark = Color(0xFF1A3050.toInt())

/** Eigene Blase Dunkel: abgedunkeltes Pastelblau */
val BubbleOwnDark = Color(0xFF2E5878.toInt())

// ======================================================================
// App Surface-Farben (TopBar, NavBar, BottomBar, Kontaktliste)
// ======================================================================

private val LightSurface = Color(0xFFEFEFEF.toInt())  // Leicht abgedunkeltes Weiß
private val DarkSurface  = Color(0xFF0F1E35.toInt())  // Dunkelblau

// Standard-Akzentfarbe (Buttons, FAB, aktive NavBar-Elemente, TopBar-Titel im Hell-Theme)
private val DefaultPrimary = Color(0xFFA8A800.toInt())  // Neon-Gelb, 66% Helligkeit
private val DefaultAccent  = Color(0xFF1A1A1A.toInt())  // Fast-Schwarz

// ======================================================================
// Hilfs-Composables für konsistente TopAppBar-Farben
// ======================================================================

/**
 * Gibt zurück, ob das aktuelle Farbschema hell ist.
 * Basiert auf der Surface-Luminanz – zuverlässiger Indikator für Light/Dark.
 */
@Composable
fun isLightSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() > 0.5f

/**
 * Titelfarbe der TopAppBar:
 * - Hell-Theme: Neon-Gelb (primary) – auffälliger Akzent auf weißer Leiste
 * - Dunkel-Theme: Weiß (onSurface) – maximaler Kontrast auf dunkler Leiste
 */
@Composable
fun topBarTitleColor(): Color =
    if (isLightSurface()) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface

/** CompositionLocal für das aktive App-Thema (Material oder Glossy Morph). */
val LocalAppTheme = staticCompositionLocalOf { AppTheme.MATERIAL }

/** Berechnet ob auf dieser Hintergrundfarbe schwarzer oder weißer Text besser lesbar ist. */
fun contrastColor(background: Color): Color {
    val luminance = 0.2126f * background.red + 0.7152f * background.green + 0.0722f * background.blue
    return if (luminance > 0.35f) Color.Black else Color.White
}

@Composable
fun SecureChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    primaryColor: Color = DefaultPrimary,
    accentColor: Color = DefaultAccent,
    barColor: Color? = null,
    backgroundColor: Color? = null,
    appTheme: AppTheme = AppTheme.MATERIAL,
    content: @Composable () -> Unit
) {
    val onPrimaryColor = contrastColor(primaryColor)
    val isGlossy = appTheme == AppTheme.GLOSSY_MORPH
    // Glossy Morph: Oberflächen leicht aufgehellt/aufgehellt für Glassmorphismus-Look
    val baseSurface = barColor ?: if (darkTheme) DarkSurface else LightSurface
    val surfaceColor = if (isGlossy && darkTheme)
        Color(baseSurface.red * 1.15f, baseSurface.green * 1.15f, baseSurface.blue * 1.25f).let { c ->
            Color(c.red.coerceAtMost(1f), c.green.coerceAtMost(1f), c.blue.coerceAtMost(1f))
        }
    else baseSurface
    val bgColor = backgroundColor ?: if (darkTheme) Color(0xFF07131F.toInt()) else LightSurface

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary          = primaryColor,
            onPrimary        = onPrimaryColor,
            secondary        = primaryColor,
            tertiary         = primaryColor,
            background       = bgColor,                    // Hintergrundfarbe (Screens)
            surface          = surfaceColor,               // Dunkelblau (TopBar/NavBar/BottomBar)
            surfaceVariant   = Color(0xFF1A2F4A.toInt()),  // Etwas helleres Dunkelblau (Eingabefeld)
            onBackground     = contrastColor(bgColor),
            onSurface        = contrastColor(surfaceColor),
            onSurfaceVariant = Color(0xFFCCCCCC.toInt()),
            surfaceTint      = Color.Transparent           // Kein heller Tonal-Overlay auf erhöhten Flächen
        )
    } else {
        lightColorScheme(
            primary          = primaryColor,
            onPrimary        = onPrimaryColor,
            secondary        = primaryColor,
            tertiary         = primaryColor,
            background       = bgColor,                    // Hintergrundfarbe (Screens)
            surface          = surfaceColor,               // Off-White (TopBar/NavBar/BottomBar)
            surfaceVariant   = Color(0xFFE0E0E0.toInt()),  // Etwas dunkler (Eingabefeld)
            onBackground     = contrastColor(bgColor),
            onSurface        = contrastColor(surfaceColor),
            onSurfaceVariant = Color(0xFF333333.toInt()),
            surfaceTint      = Color.Transparent           // Kein heller Tonal-Overlay auf erhöhten Flächen
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            // Status-Bar bekommt dieselbe Farbe wie TopBar (surface), nicht die Akzentfarbe
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = colorScheme.surface.luminance() > 0.5f
        }
    }

    CompositionLocalProvider(LocalAppTheme provides appTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
