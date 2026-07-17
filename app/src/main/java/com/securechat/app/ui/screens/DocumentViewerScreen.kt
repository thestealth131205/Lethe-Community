package com.securechat.app.ui.screens

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.securechat.app.R
import com.securechat.app.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// ── Download-State ────────────────────────────────────────────────────────────

private sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    object Done : DownloadState()
    data class Error(val message: String) : DownloadState()
}

// ── Haupt-Screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    viewModel: MainViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val documentInfo by viewModel.currentDocument.collectAsState()

    val url      = documentInfo?.url      ?: ""
    val fileName = documentInfo?.fileName ?: context.getString(R.string.doc_viewer_file_name_fallback)

    val nameLower = fileName.lowercase()
    val isPdf  = nameLower.endsWith(".pdf")  || url.lowercase().contains(".pdf")
    val isDocx = nameLower.endsWith(".docx") || url.lowercase().contains(".docx")
    val isDoc  = nameLower.endsWith(".doc")  || (url.lowercase().contains(".doc") && !isDocx)
    val textExtensions = setOf(
        "txt", "md", "kt", "kts", "java", "py", "js", "ts", "jsx", "tsx",
        "php", "c", "cpp", "cc", "h", "hpp", "cs", "go", "rs", "rb", "swift",
        "html", "htm", "css", "xml", "json", "yaml", "yml", "toml", "ini",
        "sh", "bash", "zsh", "bat", "ps1", "sql", "gradle", "properties",
        "log", "csv", "tsv", "diff", "patch"
    )
    val fileExt = nameLower.substringAfterLast('.', "")
    val isText = fileExt in textExtensions

    // States
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var cachedFile    by remember { mutableStateOf<File?>(null) }
    var pdfPages      by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var docText       by remember { mutableStateOf("") }
    var editedText    by remember { mutableStateOf("") }
    var isEditing     by remember { mutableStateOf(false) }
    var isSaving      by remember { mutableStateOf(false) }
    var saveSuccess   by remember { mutableStateOf<Boolean?>(null) }

    // Download + Parse
    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        downloadState = DownloadState.Downloading(0f)
        withContext(Dispatchers.IO) {
            try {
                val cacheFile = File(context.cacheDir, "doc_${url.hashCode()}_$fileName")
                if (!cacheFile.exists() || cacheFile.length() == 0L) {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connect()
                    val total = conn.contentLength
                    conn.inputStream.use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            val buf = ByteArray(16_384)
                            var downloaded = 0L
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                downloaded += read
                                if (total > 0) {
                                    downloadState = DownloadState.Downloading(
                                        (downloaded.toFloat() / total).coerceIn(0f, 0.99f)
                                    )
                                }
                            }
                        }
                    }
                    conn.disconnect()
                }
                cachedFile = cacheFile

                when {
                    isPdf  -> {
                        pdfPages = renderPdfPages(cacheFile)
                    }
                    isDocx -> {
                        docText    = extractDocxText(cacheFile)
                        editedText = docText
                    }
                    isDoc  -> {
                        docText    = "[Älteres .doc-Format – Darstellung eingeschränkt]\n\n" +
                                     extractDocBinaryText(cacheFile)
                        editedText = docText
                    }
                    isText -> {
                        docText    = cacheFile.readText(Charsets.UTF_8)
                        editedText = docText
                    }
                    else   -> {}
                }
                downloadState = DownloadState.Done
            } catch (e: Exception) {
                downloadState = DownloadState.Error(e.message ?: context.getString(R.string.doc_viewer_unknown_error))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 15.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.doc_viewer_back_cd))
                    }
                },
                actions = {
                    val isDone = downloadState is DownloadState.Done
                    // Bearbeiten / Speichern (nur für Text-Dokumente)
                    if ((isDocx || isDoc || isText) && isDone) {
                        if (isEditing) {
                            if (isSaving) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .padding(12.dp)
                                ) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                }
                            } else {
                                IconButton(onClick = {
                                    isSaving = true
                                    saveSuccess = null
                                    val file = cachedFile
                                    if (file != null && isDocx) {
                                        try {
                                            val outFile = File(
                                                context.cacheDir,
                                                "edited_${System.currentTimeMillis()}_$fileName"
                                            )
                                            reconstructDocx(file, editedText, outFile)
                                            saveToDownloads(context, outFile, fileName)
                                            docText    = editedText
                                            cachedFile = outFile
                                            saveSuccess = true
                                        } catch (_: Exception) {
                                            saveSuccess = false
                                        }
                                    } else {
                                        // Text-Datei (Quellcode, TXT, DOC) → mit Originalname speichern
                                        val saveFileName = if (isText) fileName
                                            else fileName.removeSuffix(".doc") + ".txt"
                                        try {
                                            val outFile = File(
                                                context.cacheDir,
                                                "edited_${System.currentTimeMillis()}_$saveFileName"
                                            )
                                            outFile.writeText(editedText, Charsets.UTF_8)
                                            saveToDownloads(context, outFile, saveFileName)
                                            docText    = editedText
                                            saveSuccess = true
                                        } catch (_: Exception) {
                                            saveSuccess = false
                                        }
                                    }
                                    isSaving  = false
                                    isEditing = false
                                }) {
                                    Icon(Icons.Default.Save, contentDescription = stringResource(R.string.doc_viewer_save_cd))
                                }
                            }
                            // Abbrechen
                            IconButton(onClick = {
                                editedText = docText
                                isEditing  = false
                            }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.doc_viewer_cancel_cd))
                            }
                        } else {
                            IconButton(onClick = { isEditing = true }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.doc_viewer_edit_cd))
                            }
                        }
                    }
                    // Download-Button für alle Dateitypen
                    if (isDone) {
                        IconButton(onClick = {
                            val file = cachedFile ?: return@IconButton
                            try { saveToDownloads(context, file, fileName) } catch (_: Exception) {}
                        }) {
                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.doc_viewer_download_cd))
                        }
                    }
                }
            )
        }
    ) { padding ->

        // Save-Feedback Snackbar
        if (saveSuccess != null) {
            LaunchedEffect(saveSuccess) {
                kotlinx.coroutines.delay(2500)
                saveSuccess = null
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.BottomCenter
            ) {
                val msg = if (saveSuccess == true) context.getString(R.string.doc_viewer_saved_success)
                          else context.getString(R.string.doc_viewer_saved_error)
                Snackbar(modifier = Modifier.padding(16.dp)) { Text(msg) }
            }
        }

        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            when (val state = downloadState) {
                is DownloadState.Idle        -> {}
                is DownloadState.Downloading -> LoadingView(state.progress)
                is DownloadState.Error       -> ErrorView(state.message)
                is DownloadState.Done        -> {
                    when {
                        isPdf  -> PdfPagesView(pages = pdfPages)
                        isDocx || isDoc || isText -> {
                            if (isEditing) {
                                DocTextEditor(
                                    text = editedText,
                                    onTextChange = { editedText = it }
                                )
                            } else {
                                DocTextViewer(text = docText)
                            }
                        }
                        else -> UnsupportedView()
                    }
                }
            }
        }
    }
}

// ── Sub-Composables ───────────────────────────────────────────────────────────

@Composable
private fun LoadingView(progress: Float) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.doc_viewer_loading), fontSize = 14.sp)
        if (progress > 0f) {
            Spacer(Modifier.height(4.dp))
            Text(
                "${(progress * 100).toInt()} %",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ErrorView(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.doc_viewer_load_failed), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun UnsupportedView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.doc_viewer_unsupported),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun PdfPagesView(pages: List<Bitmap>) {
    if (pages.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.doc_viewer_no_pages), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        return
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Pinch-to-Zoom: rememberTransformableState empfängt Zwei-Finger-Gesten.
    // scale wird auf [1f, 4f] begrenzt; offset nur bei zoom > 1 erlaubt (kein Drift bei scale=1).
    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = newScale
        if (scale > 1f) {
            offset += offsetChange
        } else {
            offset = Offset.Zero
        }
    }

    // Wenn Zoom auf 1 zurückkehrt → Offset sauber nullen (kein verrutschter Content).
    LaunchedEffect(scale) {
        if (scale <= 1.01f) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // graphicsLayer skaliert + verschiebt den gesamten Inhalt visuell.
                // transformable fängt Zwei-Finger-Events ab, LazyColumn scrollt mit einem Finger weiter.
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .transformable(state = transformableState)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp)
        ) {
            itemsIndexed(pages) { index, bitmap ->
                Column {
                    Card(
                        shape = RoundedCornerShape(4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.doc_viewer_page_cd, index + 1),
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                    Text(
                        text = stringResource(R.string.doc_viewer_page_of, index + 1, pages.size),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 2.dp)
                    )
                }
            }
        }

        // Zoom-Indikator: erscheint oben rechts sobald zoom > 1.
        if (scale > 1.05f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${(scale * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DocTextViewer(text: String) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = text.ifBlank { "(Dokument ist leer)" },
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DocTextEditor(text: String, onTextChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
        label = { Text(stringResource(R.string.doc_viewer_edit_label)) },
        shape = RoundedCornerShape(8.dp),
        maxLines = Int.MAX_VALUE
    )
}

// ── Hilfsfunktionen (IO) ─────────────────────────────────────────────────────

/** Rendert alle Seiten einer PDF-Datei als Bitmap-Liste. */
private fun renderPdfPages(file: File): List<Bitmap> {
    val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(fd)
    val pages = mutableListOf<Bitmap>()
    try {
        val targetWidthPx = 1080
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val scale = targetWidthPx.toFloat() / page.width
            val w = targetWidthPx
            val h = (page.height * scale).toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(AndroidColor.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            pages.add(bmp)
        }
    } finally {
        renderer.close()
        fd.close()
    }
    return pages
}

/** Extrahiert den Klartext aus einer DOCX-Datei (word/document.xml). */
private fun extractDocxText(file: File): String {
    val sb = StringBuilder()
    ZipInputStream(file.inputStream()).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                val xml = zis.readBytes().toString(Charsets.UTF_8)
                val factory = XmlPullParserFactory.newInstance()
                val parser  = factory.newPullParser()
                parser.setInput(StringReader(xml))
                // Aktuell aktives Start-Tag verfolgen
                var insideWt = false
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> when (parser.name) {
                            "w:t"      -> insideWt = true
                            "w:br", "w:cr" -> sb.append('\n')
                        }
                        XmlPullParser.TEXT -> {
                            if (insideWt) sb.append(parser.text)
                        }
                        XmlPullParser.END_TAG -> when (parser.name) {
                            "w:t" -> insideWt = false
                            "w:p" -> sb.append('\n')
                        }
                    }
                    event = parser.next()
                }
                break
            }
            entry = zis.nextEntry
        }
    }
    return sb.toString().trim()
}

/** Extrahiert lesbaren Text aus binärem .doc-Format (grobe Annäherung). */
private fun extractDocBinaryText(file: File): String {
    val bytes = file.readBytes()
    val sb    = StringBuilder()
    for (b in bytes) {
        val c = (b.toInt() and 0xFF)
        when {
            c in 32..126 -> sb.append(c.toChar())
            c == 10 || c == 13 -> sb.append('\n')
        }
    }
    return sb.toString()
        .replace(Regex("[ \\t]{4,}"), "  ")
        .replace(Regex("\\n{4,}"), "\n\n")
        .trim()
}

/**
 * Erstellt eine neue DOCX-Datei, die das Original-ZIP kopiert,
 * aber word/document.xml durch einfaches Paragraphen-XML mit [newText] ersetzt.
 */
private fun reconstructDocx(originalFile: File, newText: String, outputFile: File) {
    val paragraphs = newText.split("\n")

    fun String.xmlEscape() = replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    val docXml = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
        append("<w:body>")
        for (para in paragraphs) {
            append("<w:p>")
            if (para.isNotEmpty()) {
                append("<w:r><w:t xml:space=\"preserve\">${para.xmlEscape()}</w:t></w:r>")
            }
            append("</w:p>")
        }
        append("""<w:sectPr/>""")
        append("</w:body></w:document>")
    }

    ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
        // Original-Einträge kopieren (außer document.xml)
        ZipInputStream(originalFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name != "word/document.xml") {
                    zos.putNextEntry(ZipEntry(entry.name))
                    zis.copyTo(zos)
                    zos.closeEntry()
                }
                entry = zis.nextEntry
            }
        }
        // Neue word/document.xml einfügen
        zos.putNextEntry(ZipEntry("word/document.xml"))
        zos.write(docXml.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }
}

/** Speichert eine Datei in den öffentlichen Downloads-Ordner. */
private fun saveToDownloads(context: android.content.Context, file: File, fileName: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    } else {
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir.mkdirs()
        val dest = File(downloadsDir, fileName)
        file.copyTo(dest, overwrite = true)
    }
}
