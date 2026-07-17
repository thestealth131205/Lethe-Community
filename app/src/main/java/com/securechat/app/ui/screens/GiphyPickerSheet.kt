package com.securechat.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.securechat.app.data.network.GiphyGif
import com.securechat.app.ui.MainViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Wiederverwendbares GIF-Picker-Sheet (Giphy) mit drei Tabs: Trends, Emojis, Text.
 * Wird in ChatScreen (Emoji-Panel Tab), NearbyMatchChatScreen und SparksFeedScreen verwendet.
 */
@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
fun GiphyPickerSheet(
    viewModel: MainViewModel,
    onGifSelected: (gifUrl: String) -> Unit,
    modifier: Modifier = Modifier,
    onSearchFocusChanged: ((Boolean) -> Unit)? = null
) {
    val trendingGifs by viewModel.trendingGifs.collectAsState()
    val searchedGifs by viewModel.searchedGifs.collectAsState()
    val emojiGifs by viewModel.emojiGifs.collectAsState()
    val searchedEmojis by viewModel.searchedEmojis.collectAsState()
    val stickerGifs by viewModel.stickerGifs.collectAsState()
    val searchedStickers by viewModel.searchedStickers.collectAsState()
    val isLoading by viewModel.giphyLoading.collectAsState()
    val apiKey by viewModel.giphyApiKey.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val queryFlow = remember { MutableStateFlow("") }

    // Debounced search: 400 ms, Tab-abhängig
    LaunchedEffect(Unit) {
        queryFlow
            .debounce(400L)
            .distinctUntilChanged()
            .collect { query ->
                when (selectedTab) {
                    0 -> {
                        if (query.isBlank()) {
                            viewModel.searchGiphy("")
                            if (trendingGifs.isEmpty()) viewModel.loadTrendingGifs()
                        } else {
                            viewModel.searchGiphy(query)
                        }
                    }
                    1 -> {
                        if (query.isBlank()) {
                            viewModel.searchEmojis("")
                            if (emojiGifs.isEmpty()) viewModel.loadEmojiGifs()
                        } else {
                            viewModel.searchEmojis(query)
                        }
                    }
                    2 -> {
                        if (query.isBlank()) {
                            viewModel.searchStickers("")
                            if (stickerGifs.isEmpty()) viewModel.loadTrendingStickers()
                        } else {
                            viewModel.searchStickers(query)
                        }
                    }
                }
            }
    }

    LaunchedEffect(Unit) {
        viewModel.fetchGiphyApiKey()
    }

    LaunchedEffect(apiKey) {
        if (!apiKey.isNullOrBlank()) {
            if (trendingGifs.isEmpty()) viewModel.loadTrendingGifs()
        }
    }

    // Tab-Wechsel: entsprechende Inhalte nachladen
    LaunchedEffect(selectedTab, apiKey) {
        if (apiKey.isNullOrBlank()) return@LaunchedEffect
        when (selectedTab) {
            1 -> if (emojiGifs.isEmpty()) viewModel.loadEmojiGifs()
            2 -> if (stickerGifs.isEmpty()) viewModel.loadTrendingStickers()
        }
    }

    val displayGifs = when (selectedTab) {
        0 -> if (searchQuery.isBlank()) trendingGifs else searchedGifs
        1 -> if (searchQuery.isBlank()) emojiGifs else searchedEmojis
        2 -> if (searchQuery.isBlank()) stickerGifs else searchedStickers
        else -> emptyList()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Suchfeld (20dp flacher als Standard)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { q ->
                searchQuery = q
                queryFlow.value = q
            },
            placeholder = { Text("GIFs suchen…", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        queryFlow.value = ""
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "Löschen", modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 12.dp)
                .onFocusChanged { state -> onSearchFocusChanged?.invoke(state.isFocused) }
        )

        // Tabs: Trends | Emojis | Text
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = {
                    selectedTab = 0
                    queryFlow.value = searchQuery
                },
                text = { Text("Trends", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    queryFlow.value = searchQuery
                },
                text = { Text("Emojis", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = {
                    selectedTab = 2
                    queryFlow.value = searchQuery
                },
                text = { Text("Text", fontSize = 12.sp) }
            )
        }

        val currentApiKey = apiKey
        if (currentApiKey == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (currentApiKey.isBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("GIFs momentan nicht verfügbar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { viewModel.fetchGiphyApiKey() }) {
                    Text("Erneut versuchen")
                }
            }
        } else if (isLoading && displayGifs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (displayGifs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Keine Ergebnisse gefunden", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(displayGifs, key = { it.id }) { gif ->
                    GiphyGifItem(gif = gif, onClick = { onGifSelected(gif.displayUrl) })
                }
            }
        }

        // Giphy-Attribution (Pflicht laut Giphy Terms of Service)
        Text(
            text = "Powered by GIPHY",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun GiphyGifItem(gif: GiphyGif, onClick: () -> Unit) {
    AsyncImage(
        model = gif.displayUrl,
        contentDescription = gif.title.takeIf { it.isNotBlank() },
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentScale = ContentScale.Crop
    )
}
