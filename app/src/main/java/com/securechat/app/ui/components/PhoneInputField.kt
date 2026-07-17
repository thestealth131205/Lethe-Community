package com.securechat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * EPIC 2 – Telefonnummer-Eingabefeld mit Ländervorwahl-Auswahl.
 *
 * Layout: [ +49 ▼ ] [ Nummer eingeben           ]
 *
 * @param countryCode    Ausgewählte Ländervorwahl (z.B. "+49").
 * @param localNumber    Nummer ohne Vorwahl (führende Null wird automatisch entfernt).
 * @param onCountryCodeChange Callback wenn Vorwahl geändert wird.
 * @param onLocalNumberChange Callback wenn Nummer geändert wird.
 * @param enabled        Ob das Feld editierbar ist.
 */
@Composable
fun PhoneInputField(
    countryCode: String,
    localNumber: String,
    onCountryCodeChange: (String) -> Unit,
    onLocalNumberChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "Telefonnummer"
) {
    val countryCodes = listOf(
        "+49" to "🇩🇪 Deutschland",
        "+43" to "🇦🇹 Österreich",
        "+41" to "🇨🇭 Schweiz",
        "+1"  to "🇺🇸 USA / Kanada",
        "+44" to "🇬🇧 Großbritannien",
        "+33" to "🇫🇷 Frankreich",
        "+39" to "🇮🇹 Italien",
        "+34" to "🇪🇸 Spanien",
        "+31" to "🇳🇱 Niederlande",
        "+32" to "🇧🇪 Belgien",
        "+48" to "🇵🇱 Polen",
        "+90" to "🇹🇷 Türkei",
        "+7"  to "🇷🇺 Russland",
        "+86" to "🇨🇳 China",
        "+81" to "🇯🇵 Japan",
        "+82" to "🇰🇷 Südkorea",
        "+91" to "🇮🇳 Indien",
        "+55" to "🇧🇷 Brasilien",
        "+52" to "🇲🇽 Mexiko",
        "+27" to "🇿🇦 Südafrika"
    )

    var showMenu by remember { mutableStateOf(false) }
    val currentLabel = countryCodes.firstOrNull { it.first == countryCode }?.second ?: countryCode

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Ländervorwahl-Button
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                        )
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = enabled) { showMenu = true }
                        .padding(horizontal = 12.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = countryCode,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Vorwahl wählen",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    countryCodes.forEach { (code, name) ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, fontSize = 14.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        code,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            onClick = {
                                onCountryCodeChange(code)
                                showMenu = false
                            }
                        )
                    }
                }
            }

            // Nummer-Eingabefeld
            OutlinedTextField(
                value = localNumber,
                onValueChange = { input ->
                    // Führende Null entfernen (0176... → 176...)
                    val trimmed = input.trimStart('0')
                    onLocalNumberChange(trimmed)
                },
                placeholder = { Text("Nummer eingeben") },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier
                    .weight(1f)
                    .offset(x = (-1).dp)  // Überlappung der Border mit dem Vorwahl-Button
            )
        }
        Text(
            text = "Vollständige Nummer: ${countryCode}${localNumber}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
    }
}

/**
 * Normalisiert Ländervorwahl + lokale Nummer zu E.164.
 * "+49" + "1761234567" → "+491761234567"
 */
fun buildE164(countryCode: String, localNumber: String): String {
    val digits = localNumber.filter { it.isDigit() }
    return "$countryCode$digits"
}
