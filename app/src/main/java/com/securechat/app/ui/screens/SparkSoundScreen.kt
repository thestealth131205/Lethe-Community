package com.securechat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.securechat.app.data.network.CreatorContentResponse
import com.securechat.app.ui.MainViewModel

/**
 * Zeigt alle Sparks die denselben Sound verwenden.
 * Der originale Spark (von dem der Sound stammt) wird immer als erster Eintrag
 * mit einem "Original"-Banner angezeigt.
 * Layout: 3 Kacheln pro Zeile (wie SparksProfileScreen).
 */
@Composable
fun SparkSoundScreen(
    sparkId: String,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSpark: (sparkId: String) -> Unit = {},
    onUseSound: (soundOriginSparkId: String, musicTitle: String?, musicArtist: String?, musicCoverUrl: String?) -> Unit = { _, _, _, _ -> }
) {
    var sparks by remember { mutableStateOf<List<CreatorContentResponse>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(sparkId) {
        viewModel.loadSparksBySound(sparkId) { list ->
            sparks = list
            isLoading = false
        }
    }

    // Song-Titel aus dem ersten Spark (Original)
    val songTitle = sparks?.firstOrNull()?.musicTitle
        ?: sparks?.firstOrNull()?.title
        ?: "Sound"
    val songArtist = sparks?.firstOrNull()?.musicArtist

    val originalSpark = sparks?.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07131F))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Zurück",
                    tint = Color.White
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = songTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!songArtist.isNullOrBlank()) {
                    Text(
                        text = songArtist,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // Song-Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1A1A))
                    .border(1.dp, Color(0xFFA8A800), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val coverUrl = sparks?.firstOrNull()?.musicCoverUrl
                if (!coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFFA8A800),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
        }

        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFA8A800))
            }
        } else {
            val list = sparks ?: emptyList()
            if (list.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Keine Sparks mit diesem Sound gefunden", color = Color.White.copy(alpha = 0.6f))
                }
            } else {
                // Anzahl-Info
                Text(
                    text = "${list.size} Spark${if (list.size != 1) "s" else ""} mit diesem Sound",
                    color = Color(0xFFA8A800),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(list) { idx, spark ->
                        SparkSoundTile(
                            spark = spark,
                            isOriginal = idx == 0,
                            onClick = { onNavigateToSpark(spark.id) }
                        )
                    }
                }
            }
        }

        // "Sound benutzen"-Button
        Button(
            onClick = {
                val origin = originalSpark ?: return@Button
                onUseSound(
                    sparkId,
                    origin.musicTitle ?: origin.title,
                    origin.musicArtist,
                    origin.musicCoverUrl,
                )
            },
            enabled = originalSpark != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA8A800)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White
            )
            Spacer(Modifier.width(8.dp))
            Text("Sound benutzen", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SparkSoundTile(
    spark: CreatorContentResponse,
    isOriginal: Boolean,
    onClick: () -> Unit
) {
    val isImageSpark = spark.mediaType == "image_spark"

    Box(
        modifier = Modifier
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF111111))
            .clickable(onClick = onClick)
    ) {
        // Vorschaubild
        val previewUrl = spark.previewImageUrl
            ?: spark.sparkImageUrls?.firstOrNull()
        if (!previewUrl.isNullOrBlank()) {
            AsyncImage(
                model = previewUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Play-Icon für Video-Sparks (kein Play für Bild-Sparks)
        if (!isImageSpark) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Mehrere-Bilder-Indikator für image_spark
        if (isImageSpark && (spark.sparkImageUrls?.size ?: 0) > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "1/${spark.sparkImageUrls?.size ?: 0}",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // "Original"-Banner für den ersten Spark
        if (isOriginal) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color(0xFFA8A800).copy(alpha = 0.9f))
                    .padding(vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Original",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Gradient unten + View-Count
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (spark.viewCount > 0) "${formatViewCount(spark.viewCount)}" else "",
                color = Color.White,
                fontSize = 10.sp
            )
        }
    }
}

private fun formatViewCount(count: Int): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}M"
    count >= 1_000 -> "${count / 1_000}K"
    else -> "$count"
}
