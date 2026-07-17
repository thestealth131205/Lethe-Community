package com.securechat.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.securechat.app.data.network.UserStickerResponse
import com.securechat.app.ui.MainViewModel

/**
 * Sticker-Tab im Emoji-Panel.
 * Zeigt die eigenen Sticker des Nutzers in einem Raster an und bietet einen "Erstellen"-Button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StickerPickerSheet(
    viewModel: MainViewModel,
    onStickerSelected: (stickerUrl: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val stickers by viewModel.myStickers.collectAsState()
    var showCreator by remember { mutableStateOf(false) }
    var stickerToDelete by remember { mutableStateOf<UserStickerResponse?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadMyStickers()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // "Erstellen"-Button oben
        Button(
            onClick = { showCreator = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Erstellen", fontSize = 14.sp)
        }

        if (stickers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Noch keine Sticker – erstelle deinen ersten!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(stickers, key = { it.id }) { sticker ->
                    StickerGridItem(
                        sticker = sticker,
                        onClick = {
                            val absoluteUrl = if (sticker.url.startsWith("http")) sticker.url else "https://letheapp.de${sticker.url}"
                            onStickerSelected(absoluteUrl)
                        },
                        onLongClick = { stickerToDelete = sticker }
                    )
                }
            }
        }
    }

    // Sticker-Ersteller Dialog
    if (showCreator) {
        StickerCreatorSheet(
            viewModel = viewModel,
            onDismiss = { showCreator = false }
        )
    }

    // Löschen-Bestätigungsdialog
    if (stickerToDelete != null) {
        AlertDialog(
            onDismissRequest = { stickerToDelete = null },
            title = { Text("Sticker löschen?") },
            text = { Text("Dieser Sticker wird dauerhaft gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSticker(stickerToDelete!!.id)
                    stickerToDelete = null
                }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { stickerToDelete = null }) { Text("Abbrechen") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerGridItem(
    sticker: UserStickerResponse,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    AsyncImage(
        model = if (sticker.url.startsWith("http")) sticker.url else "https://letheapp.de${sticker.url}",
        contentDescription = null,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentScale = ContentScale.Crop
    )
}
