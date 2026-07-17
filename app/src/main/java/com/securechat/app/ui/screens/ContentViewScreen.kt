package com.securechat.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.securechat.app.ui.MainViewModel

/**
 * ContentViewScreen: Rendert den HTML-Inhalt eines gekauften Creator-Beitrags via WebView.
 * Route: "content_view/{contentId}"
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentViewScreen(
    viewModel: MainViewModel,
    contentId: String,
    navController: NavController,
    fontSizeMultiplier: Float = 1.0f
) {
    val contentEditorContent by viewModel.contentEditorContent.collectAsState()
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(contentId) {
        viewModel.loadContentForEditing(contentId)
    }

    val item = contentEditorContent
    val title = item?.title ?: "Beitrag"
    // Python's json.dumps() escapt < und > als \u003c/\u003e → hier dekodieren
    val html = item?.contentHtml?.let { raw ->
        Regex("\\\\u([0-9a-fA-F]{4})").replace(raw) { m ->
            m.groupValues[1].toInt(16).toChar().toString()
        }
    }

    Scaffold(
        containerColor = Color(0xFF0F1E35),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F1E35),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (html != null) {
                AndroidView(
                    factory = { context ->
                        WebView(context).also { wv ->
                            wv.settings.javaScriptEnabled = true
                            wv.settings.domStorageEnabled = true
                            wv.settings.mediaPlaybackRequiresUserGesture = false
                            wv.webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView, request: WebResourceRequest
                                ) = false
                                override fun onPageFinished(view: WebView, url: String) {
                                    isLoading = false
                                }
                            }
                            val fullHtml = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                <meta charset="UTF-8">
                                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                <style>
                                  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                                         padding: 16px; line-height: 1.6; color: #e0e0e0; background: #0F1E35;
                                         font-size: ${(fontSizeMultiplier * 100).toInt()}%; }
                                  img { max-width: 100%; height: auto; border-radius: 8px; margin: 8px 0; }
                                  video { max-width: 100%; border-radius: 8px; margin: 8px 0; }
                                  a { color: #A8A800; }
                                  h1, h2, h3, h4, h5, h6 { color: #ffffff; }
                                  p { color: #e0e0e0; }
                                </style>
                                </head>
                                <body>$html</body>
                                </html>
                            """.trimIndent()
                            wv.loadDataWithBaseURL(null, fullHtml, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            } else {
                // Laden...
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
