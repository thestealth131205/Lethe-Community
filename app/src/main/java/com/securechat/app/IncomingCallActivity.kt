package com.securechat.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.securechat.app.ui.theme.SecureChatTheme

/**
 * Vollbild-Aktivität für eingehende Anrufe auf dem Sperrbildschirm.
 *
 * Wird gestartet von:
 * - LetheFcmService (FCM-Push wenn Gerät im Doze/Standby)
 * - MainViewModel (WebSocket-Anruf wenn Bildschirm gesperrt)
 *
 * Zeigt Anrufername, Typ und Annehmen/Ablehnen-Buttons.
 * Bei Annehmen → startet MainActivity mit navigate_to=accept_call.
 * Bei Ablehnen → startet MainActivity mit navigate_to=decline_call.
 */
class IncomingCallActivity : ComponentActivity() {

    companion object {
        /** Broadcast-Action: Anruf wurde von außen beendet (Timeout / call_end via WS). */
        const val ACTION_CALL_CANCELLED = "com.securechat.app.CALL_CANCELLED"

        /** Startet die Activity vom Hintergrund (FCM/Service). */
        fun startFromBackground(context: Context) {
            val intent = Intent(context, IncomingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
            }
            context.startActivity(intent)
        }
    }

    private val callCancelledReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CALL_CANCELLED) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sperrbildschirm-Flags: Bildschirm einschalten + über Lock-Screen zeigen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Kein ausstehender Anruf → sofort schließen
        val call = IncomingCallStore.pendingCall
        if (call == null) {
            finish()
            return
        }

        // Broadcast-Receiver für Remote-Abbruch (Timeout, call_end via WebSocket)
        ContextCompat.registerReceiver(
            this,
            callCancelledReceiver,
            IntentFilter(ACTION_CALL_CANCELLED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            SecureChatTheme {
                IncomingCallScreen(
                    callerName        = call.callerName,
                    callType          = call.callType,
                    isGroupCall       = call.isGroupCall,
                    groupParticipants = call.groupParticipants,
                    groupName         = call.groupName,
                    onAccept          = { acceptCall() },
                    onDecline         = { declineCall() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(callCancelledReceiver) } catch (_: Exception) {}
    }

    private fun acceptCall() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "accept_call")
        }
        startActivity(intent)
        finish()
    }

    private fun declineCall() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "decline_call")
        }
        startActivity(intent)
        finish()
    }
}

@Composable
private fun IncomingCallScreen(
    callerName: String,
    callType: String,
    isGroupCall: Boolean = false,
    groupParticipants: List<Pair<String, String?>> = emptyList(),
    groupName: String? = null,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val isVideo = callType != "VOICE"

    // Pulsierender Ring um den "Annehmen"-Button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D1A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Anruf-Typ-Icon
            Icon(
                imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = null,
                tint = Color(0xFFCCCC00),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            val callLabel = when {
                isGroupCall && isVideo -> "Eingehender Gruppenvideoanruf"
                isGroupCall            -> "Eingehender Gruppensprachanruf"
                isVideo                -> "Eingehender Videoanruf"
                else                   -> "Eingehender Sprachanruf"
            }
            Text(
                text = callLabel,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = callerName,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            if (isGroupCall && !groupName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = groupName,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (isGroupCall && groupParticipants.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "mit " + groupParticipants.take(2).joinToString(", ") { it.first } +
                        if (groupParticipants.size > 2) " +${groupParticipants.size - 2}" else "",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Buttons: Ablehnen (links) — Annehmen (rechts)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 56.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ablehnen
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = onDecline,
                        containerColor = Color(0xFFD32F2F),
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            Icons.Default.CallEnd,
                            contentDescription = "Ablehnen",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Ablehnen", color = Color.White, fontSize = 13.sp)
                }

                // Annehmen (pulsierend)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        // Puls-Hintergrund
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .scale(pulseScale)
                                .background(Color(0x3343A047), CircleShape)
                        )
                        FloatingActionButton(
                            onClick = onAccept,
                            containerColor = Color(0xFF388E3C),
                            shape = CircleShape,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                                contentDescription = "Annehmen",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Annehmen", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}
