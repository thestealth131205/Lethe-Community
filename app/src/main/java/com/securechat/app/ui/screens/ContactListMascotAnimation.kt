package com.securechat.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// ─── Farben ────────────────────────────────────────────────────────────────

private val RobotYellow   = Color(0xFFFFC107)
private val RobotDarkGray = Color(0xFF37474F)
private val RobotLightGray = Color(0xFF78909C)
private val RobotEye      = Color(0xFF00BCD4)
private val EasterPink    = Color(0xFFF48FB1)
private val EasterPurple  = Color(0xFFCE93D8)
private val EasterGreen   = Color(0xFFA5D6A7)
private val MayOrange     = Color(0xFFFF8A65)
private val XmasRed       = Color(0xFFD32F2F)
private val XmasGreen     = Color(0xFF2E7D32)
private val XmasWhite     = Color(0xFFF5F5F5)
private val ReindeerBrown = Color(0xFF795548)
private val RudolphRed    = Color(0xFFE53935)

// ─── Öffentliches Composable ────────────────────────────────────────────────

/**
 * Zeigt eine Maskottchen-Animation in der unteren rechten Ecke.
 * Animationstyp wird über [animationType] ("none" / "easter" / "may") gesteuert.
 * Bei "none" wird nichts gerendert.
 */
@Composable
fun ContactListMascotAnimation(
    animationType: String,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 84.dp,
    enableHorizontalBounce: Boolean = true,
    mirrorDance: Boolean = false
) {
    if (animationType == "none") return

    val transition = rememberInfiniteTransition(label = "mascot")

    // Haupt-Offset: horizontales Hin- und Herhüpfen (deaktivierbar)
    val offsetX by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (!enableHorizontalBounce) 0f
                      else if (animationType == "easter") 60f else 80f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (animationType == "easter") 900 else 600,
                easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetX"
    )

    // Vertikales Hüpfen (Easter stark, Xmas leicht)
    val offsetY by transition.animateFloat(
        initialValue = 0f,
        targetValue = when (animationType) { "easter" -> -20f; "xmas" -> -4f; else -> 0f },
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450,
                easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )

    // Rotation (May tanzt, Xmas wiegt sich sanft); mirrorDance kehrt Phase um
    val rotInitial = when {
        animationType == "may" && mirrorDance -> 12f
        animationType == "may"               -> -12f
        animationType == "xmas"              -> -5f
        else                                 -> 0f
    }
    val rotTarget = when {
        animationType == "may" && mirrorDance -> -12f
        animationType == "may"               -> 12f
        animationType == "xmas"              -> 5f
        else                                 -> 0f
    }
    val rotation by transition.animateFloat(
        initialValue = rotInitial,
        targetValue = rotTarget,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )

    // Ohr-Wackeln (Easter)
    val earWiggle by transition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "earWiggle"
    )

    Canvas(modifier = modifier.size(sizeDp)) {
        translate(left = offsetX, top = offsetY) {
            rotate(degrees = rotation, pivot = Offset(size.width / 2f, size.height * 0.6f)) {
                when (animationType) {
                    "easter" -> drawEasterMascot(earWiggle)
                    "may"    -> drawMayMascot()
                    "xmas"   -> drawSantaMascot()
                    else     -> drawBaseMascot()
                }
            }
        }
    }
}

// ─── Zeichenhilfen ──────────────────────────────────────────────────────────

private fun DrawScope.drawBaseMascot() {
    val w = size.width
    val h = size.height
    val cx = w / 2f

    // Kopf (abgerundetes Rechteck, Neon-Gelb)
    drawRoundRect(
        color = RobotYellow,
        topLeft = Offset(cx - w * 0.28f, h * 0.05f),
        size = Size(w * 0.56f, h * 0.38f),
        cornerRadius = CornerRadius(w * 0.12f)
    )

    // Augen (Cyan)
    drawCircle(RobotEye, radius = w * 0.07f, center = Offset(cx - w * 0.12f, h * 0.20f))
    drawCircle(RobotEye, radius = w * 0.07f, center = Offset(cx + w * 0.12f, h * 0.20f))
    // Pupillen
    drawCircle(Color.Black, radius = w * 0.03f, center = Offset(cx - w * 0.12f, h * 0.20f))
    drawCircle(Color.Black, radius = w * 0.03f, center = Offset(cx + w * 0.12f, h * 0.20f))

    // Mund (kleines Rechteck)
    drawRoundRect(
        color = RobotDarkGray,
        topLeft = Offset(cx - w * 0.10f, h * 0.32f),
        size = Size(w * 0.20f, h * 0.06f),
        cornerRadius = CornerRadius(4f)
    )

    // Körper
    drawRoundRect(
        color = RobotDarkGray,
        topLeft = Offset(cx - w * 0.22f, h * 0.46f),
        size = Size(w * 0.44f, h * 0.34f),
        cornerRadius = CornerRadius(w * 0.08f)
    )
    // Brust-Akzent
    drawRoundRect(
        color = RobotLightGray,
        topLeft = Offset(cx - w * 0.10f, h * 0.52f),
        size = Size(w * 0.20f, h * 0.14f),
        cornerRadius = CornerRadius(4f)
    )

    // Arme
    drawRoundRect(
        color = RobotLightGray,
        topLeft = Offset(cx - w * 0.40f, h * 0.48f),
        size = Size(w * 0.16f, h * 0.22f),
        cornerRadius = CornerRadius(8f)
    )
    drawRoundRect(
        color = RobotLightGray,
        topLeft = Offset(cx + w * 0.24f, h * 0.48f),
        size = Size(w * 0.16f, h * 0.22f),
        cornerRadius = CornerRadius(8f)
    )

    // Beine
    drawRoundRect(
        color = RobotDarkGray,
        topLeft = Offset(cx - w * 0.18f, h * 0.80f),
        size = Size(w * 0.14f, h * 0.18f),
        cornerRadius = CornerRadius(6f)
    )
    drawRoundRect(
        color = RobotDarkGray,
        topLeft = Offset(cx + w * 0.04f, h * 0.80f),
        size = Size(w * 0.14f, h * 0.18f),
        cornerRadius = CornerRadius(6f)
    )
}

private fun DrawScope.drawEasterMascot(earWiggle: Float) {
    val w = size.width
    val h = size.height
    val cx = w / 2f

    // Hasenohren links (pink mit Wiggle)
    rotate(degrees = earWiggle - 6f, pivot = Offset(cx - w * 0.12f, h * 0.05f)) {
        val earPath = Path().apply {
            moveTo(cx - w * 0.18f, h * 0.05f)
            cubicTo(
                cx - w * 0.26f, -h * 0.12f,
                cx - w * 0.10f, -h * 0.12f,
                cx - w * 0.06f, h * 0.05f
            )
            close()
        }
        drawPath(earPath, EasterPink)
        val innerPath = Path().apply {
            moveTo(cx - w * 0.16f, h * 0.03f)
            cubicTo(
                cx - w * 0.21f, -h * 0.07f,
                cx - w * 0.11f, -h * 0.07f,
                cx - w * 0.08f, h * 0.03f
            )
            close()
        }
        drawPath(innerPath, EasterPurple)
    }

    // Hasenohren rechts
    rotate(degrees = -earWiggle + 6f, pivot = Offset(cx + w * 0.12f, h * 0.05f)) {
        val earPath = Path().apply {
            moveTo(cx + w * 0.06f, h * 0.05f)
            cubicTo(
                cx + w * 0.10f, -h * 0.12f,
                cx + w * 0.26f, -h * 0.12f,
                cx + w * 0.18f, h * 0.05f
            )
            close()
        }
        drawPath(earPath, EasterPink)
        val innerPath = Path().apply {
            moveTo(cx + w * 0.08f, h * 0.03f)
            cubicTo(
                cx + w * 0.11f, -h * 0.07f,
                cx + w * 0.21f, -h * 0.07f,
                cx + w * 0.16f, h * 0.03f
            )
            close()
        }
        drawPath(innerPath, EasterPurple)
    }

    // Basis-Roboter
    drawBaseMascot()

    // Oster-Eier (kleine dekorative Eier)
    drawOval(EasterGreen, topLeft = Offset(cx - w * 0.42f, h * 0.76f), size = Size(w * 0.14f, h * 0.16f))
    drawOval(EasterPink, topLeft = Offset(cx + w * 0.28f, h * 0.76f), size = Size(w * 0.14f, h * 0.16f))
}

// ─── Ostereier-Dekoration ────────────────────────────────────────────────────

/**
 * Zwei bunt bemalte weiße Ostereier, die nach links und rechts kippeln.
 * Wird links neben dem Oster-Maskottchen in der TopBar platziert.
 */
@Composable
fun EasterEggsDecoration(
    modifier: Modifier = Modifier,
    heightDp: Dp = 40.dp
) {
    val transition = rememberInfiniteTransition(label = "eggs")

    val wobble1 by transition.animateFloat(
        initialValue = -13f,
        targetValue = 13f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 680, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wobble1"
    )
    val wobble2 by transition.animateFloat(
        initialValue = 11f,
        targetValue = -11f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 540, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wobble2"
    )

    val widthDp = heightDp * 1.4f
    val canvasHeightDp = heightDp * 1.5f  // extra Platz oben für Kippelung
    Canvas(modifier = modifier.size(width = widthDp, height = canvasHeightDp)) {
        val eggW = size.width * 0.42f
        val eggH = size.height * 0.50f   // Ei-Höhe relativ zur größeren Canvas-Höhe
        val gap  = size.width * 0.16f
        val yOff = size.height - eggH    // Eier am unteren Rand, Raum oben für Rotation
        val pivotY = size.height         // Drehpunkt am untersten Canvas-Rand

        // Ei 1 links
        rotate(degrees = wobble1, pivot = Offset(eggW / 2f, pivotY)) {
            drawSingleEasterEgg(0f, yOff, eggW, eggH, EasterPink, EasterPurple, EasterGreen)
        }
        // Ei 2 rechts
        rotate(degrees = wobble2, pivot = Offset(eggW + gap + eggW / 2f, pivotY)) {
            drawSingleEasterEgg(eggW + gap, yOff, eggW, eggH, EasterGreen, EasterPink, EasterPurple)
        }
    }
}

private fun DrawScope.drawSingleEasterEgg(
    x: Float, y: Float, w: Float, h: Float,
    stripeColor: Color, dot1: Color, dot2: Color
) {
    val eggPath = Path().apply { addOval(Rect(x, y, x + w, y + h)) }

    // Weißer Hintergrund
    drawOval(Color.White, topLeft = Offset(x, y), size = Size(w, h))

    // Dekorationen geclippt auf Ei-Form
    drawContext.canvas.save()
    drawContext.canvas.clipPath(eggPath)

    // Farbiger Mittelstreifen
    drawRect(stripeColor.copy(alpha = 0.75f), topLeft = Offset(x, y + h * 0.38f), size = Size(w, h * 0.24f))

    // Zwei farbige Punkte
    drawCircle(dot1, radius = w * 0.13f, center = Offset(x + w * 0.28f, y + h * 0.22f))
    drawCircle(dot2, radius = w * 0.11f, center = Offset(x + w * 0.68f, y + h * 0.72f))

    drawContext.canvas.restore()

    // Umriss
    drawOval(Color(0xFFCCCCCC), topLeft = Offset(x, y), size = Size(w, h), style = Stroke(width = 2f))
}

private fun DrawScope.drawSantaMascot() {
    val w = size.width
    val h = size.height
    val cx = w / 2f

    // Basis-Roboter
    drawBaseMascot()

    // Weißer Bart (überdeckt Hals/Unterkiefer des Roboters)
    drawRoundRect(
        color = XmasWhite,
        topLeft = Offset(cx - w * 0.26f, h * 0.29f),
        size = Size(w * 0.52f, h * 0.15f),
        cornerRadius = CornerRadius(w * 0.12f)
    )

    // Rote Weihnachtsmütze (flache Kappe über dem Kopf)
    drawRoundRect(
        color = XmasRed,
        topLeft = Offset(cx - w * 0.27f, h * 0.01f),
        size = Size(w * 0.54f, h * 0.11f),
        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
    )

    // Weißer Hutrand
    drawRoundRect(
        color = XmasWhite,
        topLeft = Offset(cx - w * 0.29f, h * 0.08f),
        size = Size(w * 0.58f, h * 0.06f),
        cornerRadius = CornerRadius(4f)
    )

    // Goldener Gürtel am Körper
    drawRect(
        color = Color(0xFFFFD600),
        topLeft = Offset(cx - w * 0.22f, h * 0.56f),
        size = Size(w * 0.44f, h * 0.06f)
    )
    // Gürtelschnalle
    drawRoundRect(
        color = Color(0xFFFFEE00),
        topLeft = Offset(cx - w * 0.06f, h * 0.54f),
        size = Size(w * 0.12f, h * 0.10f),
        cornerRadius = CornerRadius(3f)
    )
}

private fun DrawScope.drawMayMascot() {
    val w = size.width
    val h = size.height
    val cx = w / 2f

    // Basis-Roboter
    drawBaseMascot()

    // Blumen-Hut oben
    val petalColors = listOf(MayOrange, EasterPink, RobotYellow, EasterPurple, EasterGreen)
    val hatCenterX = cx
    val hatCenterY = h * 0.02f
    val petalR = w * 0.09f
    for (i in 0 until 5) {
        val angle = i * (2 * Math.PI / 5).toFloat()
        drawCircle(
            color = petalColors[i],
            radius = petalR,
            center = Offset(
                hatCenterX + cos(angle) * w * 0.13f,
                hatCenterY + sin(angle) * w * 0.13f
            )
        )
    }
    drawCircle(RobotYellow, radius = w * 0.08f, center = Offset(hatCenterX, hatCenterY))

    // Tanznoten (kleine Achtel-Noten-Strich-Köpfe) neben dem Körper
    drawCircle(MayOrange, radius = w * 0.04f, center = Offset(cx - w * 0.46f, h * 0.40f))
    drawLine(MayOrange, Offset(cx - w * 0.42f, h * 0.40f), Offset(cx - w * 0.42f, h * 0.28f), strokeWidth = 3f)
    drawCircle(EasterPink, radius = w * 0.04f, center = Offset(cx + w * 0.46f, h * 0.44f))
    drawLine(EasterPink, Offset(cx + w * 0.42f, h * 0.44f), Offset(cx + w * 0.42f, h * 0.32f), strokeWidth = 3f)
}

// ─── Weihnachts-Rentier ──────────────────────────────────────────────────────

/**
 * Rudolph – ein Rentier mit roter Nase, das sanft wippt.
 * Wird links neben dem Weihnachtsmann in der TopBar platziert.
 */
@Composable
fun XmasReindeerDecoration(
    modifier: Modifier = Modifier,
    heightDp: Dp = 40.dp
) {
    val transition = rememberInfiniteTransition(label = "reindeer")
    val bobble by transition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bobble"
    )
    val canvasWidth = heightDp * 1.3f
    Canvas(modifier = modifier.size(width = canvasWidth, height = heightDp)) {
        rotate(degrees = bobble, pivot = Offset(size.width / 2f, size.height * 0.93f)) {
            drawReindeerFigure()
        }
    }
}

private fun DrawScope.drawReindeerFigure() {
    val w = size.width
    val h = size.height

    // Beine (4 Stück)
    val legW = w * 0.10f
    val legH = h * 0.28f
    val legTop = h * 0.64f
    for (xFrac in listOf(0.14f, 0.27f, 0.54f, 0.67f)) {
        drawRoundRect(ReindeerBrown, topLeft = Offset(w * xFrac, legTop), size = Size(legW, legH), cornerRadius = CornerRadius(4f))
    }

    // Körper
    drawOval(ReindeerBrown, topLeft = Offset(w * 0.07f, h * 0.36f), size = Size(w * 0.74f, h * 0.34f))

    // Hals
    drawRoundRect(
        color = ReindeerBrown,
        topLeft = Offset(w * 0.63f, h * 0.20f),
        size = Size(w * 0.16f, h * 0.22f),
        cornerRadius = CornerRadius(6f)
    )

    // Kopf
    drawOval(ReindeerBrown, topLeft = Offset(w * 0.63f, h * 0.08f), size = Size(w * 0.30f, h * 0.24f))

    // Schnauzenbereich (etwas heller)
    drawOval(Color(0xFFA1887F), topLeft = Offset(w * 0.79f, h * 0.17f), size = Size(w * 0.18f, h * 0.14f))

    // Geweih links
    drawLine(ReindeerBrown, Offset(w * 0.70f, h * 0.09f), Offset(w * 0.61f, h * 0.00f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
    drawLine(ReindeerBrown, Offset(w * 0.64f, h * 0.04f), Offset(w * 0.54f, h * 0.00f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
    drawLine(ReindeerBrown, Offset(w * 0.63f, h * 0.02f), Offset(w * 0.56f, -h * 0.03f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)

    // Geweih rechts
    drawLine(ReindeerBrown, Offset(w * 0.84f, h * 0.09f), Offset(w * 0.93f, h * 0.00f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
    drawLine(ReindeerBrown, Offset(w * 0.90f, h * 0.04f), Offset(w * 0.99f, h * 0.00f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
    drawLine(ReindeerBrown, Offset(w * 0.91f, h * 0.02f), Offset(w * 0.98f, -h * 0.03f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)

    // Schwanz (weißer Fleck)
    drawCircle(XmasWhite, radius = w * 0.07f, center = Offset(w * 0.11f, h * 0.47f))

    // Auge
    drawCircle(Color.Black, radius = w * 0.03f, center = Offset(w * 0.86f, h * 0.17f))

    // Rote Nase (Rudolph)
    drawCircle(RudolphRed, radius = w * 0.07f, center = Offset(w * 0.92f, h * 0.25f))
    // Glanz auf der Nase
    drawCircle(Color.White.copy(alpha = 0.45f), radius = w * 0.025f, center = Offset(w * 0.90f, h * 0.23f))
}
