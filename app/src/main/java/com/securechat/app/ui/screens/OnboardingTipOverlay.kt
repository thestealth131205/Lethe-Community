package com.securechat.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.securechat.app.R
import kotlin.math.roundToInt

/**
 * Zeigt ein Onboarding-Pfeil-Overlay mit Sprechblase.
 *
 * Das Overlay liegt in einem Popup über dem gesamten Screen-Inhalt.
 * Der Pfeil (bouncend) zeigt auf [targetRect].
 *
 * @param targetRect    Bildschirm-Koordinaten (boundsInWindow) des Ziel-Elements
 * @param title         Kurzer Fett-Text in der Blase
 * @param body          Optionaler Fließtext unter dem Titel
 * @param onDismiss     Wenn nicht null: Schließen-Button wird gezeigt, Callback beim Tippen
 * @param focusable     True = scrim, taps werden vom Overlay abgefangen (für Info-Blasen)
 */
@Composable
fun OnboardingTipOverlay(
    targetRect: Rect,
    title: String,
    body: String? = null,
    onDismiss: (() -> Unit)? = null,
    focusable: Boolean = false
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }

    val isTargetInBottomHalf = targetRect.center.y > screenHeightPx / 2
    val arrowIcon: ImageVector = if (isTargetInBottomHalf) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward

    // Bounce-Animation für den Pfeil
    val infiniteTransition = rememberInfiniteTransition(label = "onboardingArrow")
    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isTargetInBottomHalf) 10f else -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    // --- Berechnungen (gemeinsam für beide Rendering-Pfade) ---
    val arrowSizePx = with(density) { 36.dp.toPx() }
    val arrowPaddingTopPx    = with(density) { 4.dp.toPx() }
    val arrowPaddingBottomPx = with(density) { 24.dp.toPx() }
    val arrowX = (targetRect.center.x - arrowSizePx / 2)
        .coerceIn(0f, screenWidthPx - arrowSizePx)
    val arrowY = if (isTargetInBottomHalf) {
        targetRect.top - arrowSizePx - arrowPaddingBottomPx
    } else {
        targetRect.bottom + arrowPaddingTopPx
    }
    val bubbleOffsetY = if (isTargetInBottomHalf) {
        (screenHeightPx * 0.10f).roundToInt()
    } else {
        (screenHeightPx * 0.65f).roundToInt()
    }

    // --- Inhalt als Lambda, wird direkt oder in Popup gerendert ---
    val overlayContent: @Composable BoxScope.() -> Unit = {
        if (focusable) {
            // Halbtransparenter Scrim der Touches abfängt
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onDismiss?.invoke() }
                    )
            )
        }

        // --- Pfeil direkt am Ziel-Element ---
        Icon(
            imageVector = arrowIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(36.dp)
                .offset {
                    IntOffset(
                        arrowX.roundToInt(),
                        (arrowY + bounce).roundToInt()
                    )
                }
        )

        // --- Sprechblasen-Karte ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .offset { IntOffset(0, bubbleOffsetY) }
                .fillMaxWidth()
                .let { m ->
                    if (onDismiss != null && focusable) m.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {}
                    ) else m
                }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (body != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = body,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
                if (onDismiss != null) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.onboarding_tip_close),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (focusable) {
        // Modales Overlay: in eigenem Popup-Fenster, blockiert Touches bewusst
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset.Zero,
            properties = PopupProperties(focusable = true)
        ) {
            Box(modifier = Modifier.fillMaxSize(), content = overlayContent)
        }
    } else {
        // Nicht-modales Overlay: direkt im Composition-Tree rendern.
        // Kein eigenes Popup-Fenster → keine Touch-Blockierung.
        Box(modifier = Modifier.fillMaxSize(), content = overlayContent)
    }
}

/**
 * Feier-Overlay für "Erste Nachricht verschickt" – modales Fullscreen-Popup.
 *
 * @param onDismiss Callback wenn der Nutzer bestätigt
 */
@Composable
fun FirstMessageCelebrationOverlay(
    onDismiss: () -> Unit,
    ageVerified: Boolean
) {
    Popup(
        alignment = Alignment.Center,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.60f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {}
                ),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(8.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_tip_first_msg_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.onboarding_tip_first_msg_body),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (!ageVerified) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.onboarding_tip_first_msg_age),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDismiss) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }
}
