package com.securechat.app.ui.screens

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.securechat.app.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

// ── Farbschema (VS-Code-Dark angelehnt) ─────────────────────────────────────
private val CodeBg          = Color(0xFF1E1E1E)
private val CodeDefault     = Color(0xFFD4D4D4)
private val ColKeyword      = Color(0xFF569CD6)
private val ColType         = Color(0xFF4EC9B0)
private val ColString       = Color(0xFFCE9178)
private val ColComment      = Color(0xFF6A9955)
private val ColNumber       = Color(0xFFB5CEA8)
private val ColAnnotation   = Color(0xFFC586C0)
private val ColPreprocessor = Color(0xFFC586C0)
private val ColAttribute    = Color(0xFF9CDCFE)
private val ColTagPunct     = Color(0xFF808080)
private val ColTagName      = Color(0xFF569CD6)

private sealed class CodeLoadState {
    object Loading : CodeLoadState()
    object Done : CodeLoadState()
    data class Error(val message: String) : CodeLoadState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeViewerScreen(
    viewModel: MainViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val info by viewModel.currentCode.collectAsState()

    val url = info?.url ?: ""
    val fileName = info?.fileName ?: "Code"
    val ext = fileName.substringAfterLast('.', "").lowercase()

    var loadState by remember { mutableStateOf<CodeLoadState>(CodeLoadState.Loading) }
    var codeText  by remember { mutableStateOf("") }
    var cachedFile by remember { mutableStateOf<File?>(null) }
    var savedResult by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(url) {
        if (url.isBlank()) {
            loadState = CodeLoadState.Error("Keine Datei")
            return@LaunchedEffect
        }
        loadState = CodeLoadState.Loading
        withContext(Dispatchers.IO) {
            try {
                val safeName = fileName.takeLast(64).replace(Regex("[^A-Za-z0-9._-]"), "_")
                val cacheFile = File(context.cacheDir, "code_${url.hashCode()}_$safeName")
                if (!cacheFile.exists() || cacheFile.length() == 0L) {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connect()
                    conn.inputStream.use { input ->
                        FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
                    }
                    conn.disconnect()
                }
                cachedFile = cacheFile
                codeText = cacheFile.readText(Charsets.UTF_8)
                loadState = CodeLoadState.Done
            } catch (e: Exception) {
                loadState = CodeLoadState.Error(e.message ?: "Fehler beim Laden")
            }
        }
    }

    val highlighted = remember(codeText, ext) {
        if (codeText.length <= 400_000) highlightCode(codeText, ext)
        else AnnotatedString(codeText)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (loadState is CodeLoadState.Done) {
                        IconButton(onClick = {
                            val file = cachedFile ?: return@IconButton
                            savedResult = try {
                                saveCodeToDownloads(context, file, fileName); true
                            } catch (_: Exception) { false }
                        }) {
                            Icon(Icons.Default.Download, contentDescription = "Herunterladen")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(CodeBg)
        ) {
            when (val s = loadState) {
                is CodeLoadState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is CodeLoadState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(s.message, color = CodeDefault, fontSize = 13.sp)
                    }
                }
                is CodeLoadState.Done -> {
                    Text(
                        text = highlighted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = CodeDefault,
                        softWrap = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }
            }

            if (savedResult != null) {
                LaunchedEffect(savedResult) {
                    kotlinx.coroutines.delay(2500)
                    savedResult = null
                }
                Snackbar(modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                ) {
                    Text(if (savedResult == true) "In Downloads gespeichert" else "Speichern fehlgeschlagen")
                }
            }
        }
    }
}

/** Speichert die Code-Datei mit Originalnamen in den öffentlichen Downloads-Ordner. */
private fun saveCodeToDownloads(context: Context, file: File, fileName: String) {
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
        file.copyTo(File(downloadsDir, fileName), overwrite = true)
    }
}

// ── Syntax-Highlighting ─────────────────────────────────────────────────────

/**
 * Erzeugt eine farblich hervorgehobene AnnotatedString-Darstellung des Codes.
 * Unterstützt C#, C/C++, HTML, PHP, Java, XML, JSON, YAML und Python
 * (weitere gängige Endungen fallen auf den generischen Lexer zurück).
 */
fun highlightCode(code: String, ext: String): AnnotatedString = when (ext) {
    "html", "htm", "xml" -> highlightMarkup(code)
    "json" -> highlightJson(code)
    "yml", "yaml" -> highlightYaml(code)
    else -> highlightGeneric(code, langConfigFor(ext))
}

private data class LangConfig(
    val keywords: Set<String>,
    val lineComment: String?,
    val blockStart: String?,
    val blockEnd: String?,
    val annotations: Boolean = false,
    val preprocessor: Boolean = false,
    val dollarVars: Boolean = false,
    val tripleStrings: Boolean = false
)

private fun langConfigFor(ext: String): LangConfig = when (ext) {
    "cs" -> LangConfig(CS_KEYWORDS, "//", "/*", "*/", annotations = true, preprocessor = true)
    "cpp", "cc", "cxx", "c", "h", "hpp", "hh" ->
        LangConfig(CPP_KEYWORDS, "//", "/*", "*/", preprocessor = true)
    "php" -> LangConfig(PHP_KEYWORDS, "//", "/*", "*/", dollarVars = true)
    "java" -> LangConfig(JAVA_KEYWORDS, "//", "/*", "*/", annotations = true)
    "py" -> LangConfig(PY_KEYWORDS, "#", null, null, annotations = true, tripleStrings = true)
    "kt", "kts" -> LangConfig(KOTLIN_KEYWORDS, "//", "/*", "*/", annotations = true)
    "js", "ts", "jsx", "tsx" -> LangConfig(JS_KEYWORDS, "//", "/*", "*/")
    else -> LangConfig(emptySet(), "//", "/*", "*/")
}

private fun highlightGeneric(code: String, cfg: LangConfig): AnnotatedString = buildAnnotatedString {
    val n = code.length
    var i = 0
    var lineStartPending = true // nur Whitespace seit letztem Zeilenumbruch

    fun isIdentStart(c: Char) = c.isLetter() || c == '_' || (cfg.dollarVars && c == '$')

    while (i < n) {
        val c = code[i]

        if (c == '\n') { append('\n'); i++; lineStartPending = true; continue }
        if (c == ' ' || c == '\t' || c == '\r') { append(c); i++; continue }

        // Blockkommentar
        if (cfg.blockStart != null && cfg.blockEnd != null && code.startsWith(cfg.blockStart, i)) {
            val end = code.indexOf(cfg.blockEnd, i + cfg.blockStart.length)
            val stop = if (end == -1) n else end + cfg.blockEnd.length
            withStyle(SpanStyle(color = ColComment)) { append(code.substring(i, stop)) }
            i = stop; lineStartPending = false; continue
        }

        // Triple-Strings (Python)
        if (cfg.tripleStrings && (code.startsWith("\"\"\"", i) || code.startsWith("'''", i))) {
            val t = code.substring(i, i + 3)
            val end = code.indexOf(t, i + 3)
            val stop = if (end == -1) n else end + 3
            withStyle(SpanStyle(color = ColString)) { append(code.substring(i, stop)) }
            i = stop; lineStartPending = false; continue
        }

        // Zeilenkommentar
        if (cfg.lineComment != null && code.startsWith(cfg.lineComment, i)) {
            val end = code.indexOf('\n', i)
            val stop = if (end == -1) n else end
            withStyle(SpanStyle(color = ColComment)) { append(code.substring(i, stop)) }
            i = stop; continue
        }

        // Präprozessor (#... am Zeilenanfang, C/C++/C#)
        if (cfg.preprocessor && c == '#' && lineStartPending) {
            val end = code.indexOf('\n', i)
            val stop = if (end == -1) n else end
            withStyle(SpanStyle(color = ColPreprocessor)) { append(code.substring(i, stop)) }
            i = stop; continue
        }

        // Strings
        if (c == '"' || c == '\'' || c == '`') {
            var j = i + 1
            while (j < n) {
                val d = code[j]
                if (d == '\\') { j += 2; continue }
                if (d == c) { j++; break }
                if (d == '\n' && c != '`') break
                j++
            }
            val stop = j.coerceAtMost(n)
            withStyle(SpanStyle(color = ColString)) { append(code.substring(i, stop)) }
            i = stop; lineStartPending = false; continue
        }

        // Annotationen / Dekoratoren
        if (cfg.annotations && c == '@' && i + 1 < n && (code[i + 1].isLetter() || code[i + 1] == '_')) {
            var j = i + 1
            while (j < n && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '.')) j++
            withStyle(SpanStyle(color = ColAnnotation)) { append(code.substring(i, j)) }
            i = j; lineStartPending = false; continue
        }

        // PHP-Variablen ($name)
        if (cfg.dollarVars && c == '$' && i + 1 < n && (code[i + 1].isLetter() || code[i + 1] == '_')) {
            var j = i + 1
            while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
            withStyle(SpanStyle(color = ColAttribute)) { append(code.substring(i, j)) }
            i = j; lineStartPending = false; continue
        }

        // Bezeichner / Keywords / Typen
        if (isIdentStart(c)) {
            var j = i + 1
            while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
            val word = code.substring(i, j)
            when {
                word in cfg.keywords -> withStyle(SpanStyle(color = ColKeyword)) { append(word) }
                word.first().isUpperCase() -> withStyle(SpanStyle(color = ColType)) { append(word) }
                else -> append(word)
            }
            i = j; lineStartPending = false; continue
        }

        // Zahlen
        if (c.isDigit()) {
            var j = i + 1
            while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
            withStyle(SpanStyle(color = ColNumber)) { append(code.substring(i, j)) }
            i = j; lineStartPending = false; continue
        }

        append(c); i++; lineStartPending = false
    }
}

private fun highlightMarkup(code: String): AnnotatedString = buildAnnotatedString {
    val n = code.length
    var i = 0
    while (i < n) {
        if (code.startsWith("<!--", i)) {
            val end = code.indexOf("-->", i)
            val stop = if (end == -1) n else end + 3
            withStyle(SpanStyle(color = ColComment)) { append(code.substring(i, stop)) }
            i = stop; continue
        }
        if (code[i] == '<') {
            // Tag-Ende suchen (Anführungszeichen berücksichtigen)
            var j = i + 1
            var q: Char? = null
            while (j < n) {
                val d = code[j]
                if (q != null) { if (d == q) q = null }
                else {
                    if (d == '"' || d == '\'') q = d
                    else if (d == '>') { j++; break }
                }
                j++
            }
            val stop = j.coerceAtMost(n)
            appendTag(code.substring(i, stop))
            i = stop; continue
        }
        val next = code.indexOf('<', i)
        val stop = if (next == -1) n else next
        append(code.substring(i, stop))
        i = stop
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendTag(tag: String) {
    val m = tag.length
    var k = 0
    withStyle(SpanStyle(color = ColTagPunct)) { append("<") }
    k = 1
    if (k < m && (tag[k] == '/' || tag[k] == '!' || tag[k] == '?')) {
        withStyle(SpanStyle(color = ColTagPunct)) { append(tag[k].toString()) }; k++
    }
    val nameStart = k
    while (k < m && (tag[k].isLetterOrDigit() || tag[k] == ':' || tag[k] == '-' || tag[k] == '_')) k++
    if (k > nameStart) withStyle(SpanStyle(color = ColTagName)) { append(tag.substring(nameStart, k)) }
    while (k < m) {
        val d = tag[k]
        when {
            d == '"' || d == '\'' -> {
                var e = k + 1
                while (e < m && tag[e] != d) e++
                e = (e + 1).coerceAtMost(m)
                withStyle(SpanStyle(color = ColString)) { append(tag.substring(k, e)) }
                k = e
            }
            d.isLetter() || d == '_' || d == ':' -> {
                val s = k
                while (k < m && (tag[k].isLetterOrDigit() || tag[k] == '-' || tag[k] == ':' || tag[k] == '_')) k++
                withStyle(SpanStyle(color = ColAttribute)) { append(tag.substring(s, k)) }
            }
            d == '>' || d == '/' -> { withStyle(SpanStyle(color = ColTagPunct)) { append(d.toString()) }; k++ }
            else -> { append(d.toString()); k++ }
        }
    }
}

private fun highlightJson(code: String): AnnotatedString = buildAnnotatedString {
    val n = code.length
    var i = 0
    while (i < n) {
        val c = code[i]
        if (c == '"') {
            var j = i + 1
            while (j < n) {
                val d = code[j]
                if (d == '\\') { j += 2; continue }
                if (d == '"') { j++; break }
                j++
            }
            val stop = j.coerceAtMost(n)
            var k = stop
            while (k < n && (code[k] == ' ' || code[k] == '\t' || code[k] == '\r' || code[k] == '\n')) k++
            val isKey = k < n && code[k] == ':'
            withStyle(SpanStyle(color = if (isKey) ColAttribute else ColString)) { append(code.substring(i, stop)) }
            i = stop; continue
        }
        if (c.isDigit() || (c == '-' && i + 1 < n && code[i + 1].isDigit())) {
            var j = i + 1
            while (j < n && (code[j].isDigit() || code[j] == '.' || code[j] == 'e' || code[j] == 'E' || code[j] == '+' || code[j] == '-')) j++
            withStyle(SpanStyle(color = ColNumber)) { append(code.substring(i, j)) }
            i = j; continue
        }
        if (c.isLetter()) {
            var j = i + 1
            while (j < n && code[j].isLetter()) j++
            val w = code.substring(i, j)
            if (w == "true" || w == "false" || w == "null") withStyle(SpanStyle(color = ColKeyword)) { append(w) }
            else append(w)
            i = j; continue
        }
        append(c); i++
    }
}

private fun highlightYaml(code: String): AnnotatedString = buildAnnotatedString {
    val lines = code.split("\n")
    for ((idx, line) in lines.withIndex()) {
        val trimmed = line.trimStart()
        val indentLen = line.length - trimmed.length
        if (indentLen > 0) append(line.substring(0, indentLen))

        if (trimmed.startsWith("#")) {
            withStyle(SpanStyle(color = ColComment)) { append(trimmed) }
        } else {
            var rest = trimmed
            if (rest.startsWith("- ")) { append("- "); rest = rest.substring(2) }
            else if (rest == "-") { append("-"); rest = "" }

            val colon = yamlColonIndex(rest)
            if (colon >= 0) {
                withStyle(SpanStyle(color = ColAttribute)) { append(rest.substring(0, colon)) }
                append(":")
                appendYamlValue(rest.substring(colon + 1))
            } else {
                appendYamlValue(rest)
            }
        }
        if (idx < lines.size - 1) append("\n")
    }
}

private fun yamlColonIndex(s: String): Int {
    for (p in s.indices) {
        if (s[p] == ':' && (p == s.length - 1 || s[p + 1] == ' ')) return p
    }
    return -1
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendYamlValue(v: String) {
    val ci = v.indexOf(" #")
    val valuePart = if (ci >= 0) v.substring(0, ci) else v
    val commentPart = if (ci >= 0) v.substring(ci) else ""

    val leadWs = valuePart.takeWhile { it == ' ' || it == '\t' }
    val core = valuePart.substring(leadWs.length)
    append(leadWs)
    when {
        core.isEmpty() -> {}
        core.startsWith("\"") || core.startsWith("'") -> withStyle(SpanStyle(color = ColString)) { append(core) }
        core == "true" || core == "false" || core == "null" || core == "yes" || core == "no" || core == "~" ->
            withStyle(SpanStyle(color = ColKeyword)) { append(core) }
        core.toDoubleOrNull() != null -> withStyle(SpanStyle(color = ColNumber)) { append(core) }
        else -> append(core)
    }
    if (commentPart.isNotEmpty()) withStyle(SpanStyle(color = ColComment)) { append(commentPart) }
}

// ── Keyword-Sätze ───────────────────────────────────────────────────────────

private val CS_KEYWORDS = setOf(
    "abstract", "as", "async", "await", "base", "bool", "break", "byte", "case", "catch",
    "char", "checked", "class", "const", "continue", "decimal", "default", "delegate", "do",
    "double", "else", "enum", "event", "explicit", "extern", "false", "finally", "fixed",
    "float", "for", "foreach", "get", "goto", "if", "implicit", "in", "int", "interface",
    "internal", "is", "lock", "long", "namespace", "new", "null", "object", "operator", "out",
    "override", "params", "partial", "private", "protected", "public", "readonly", "record",
    "ref", "return", "sbyte", "sealed", "set", "short", "sizeof", "stackalloc", "static",
    "string", "struct", "switch", "this", "throw", "true", "try", "typeof", "uint", "ulong",
    "unchecked", "unsafe", "ushort", "using", "value", "var", "virtual", "void", "volatile",
    "when", "where", "while", "yield"
)

private val CPP_KEYWORDS = setOf(
    "alignas", "alignof", "and", "asm", "auto", "bool", "break", "case", "catch", "char",
    "class", "const", "constexpr", "continue", "decltype", "default", "delete", "do", "double",
    "dynamic_cast", "else", "enum", "explicit", "export", "extern", "false", "float", "for",
    "friend", "goto", "if", "inline", "int", "long", "mutable", "namespace", "new", "noexcept",
    "nullptr", "operator", "private", "protected", "public", "register", "return", "short",
    "signed", "sizeof", "static", "static_cast", "struct", "switch", "template", "this", "throw",
    "true", "try", "typedef", "typeid", "typename", "union", "unsigned", "using", "virtual",
    "void", "volatile", "wchar_t", "while"
)

private val PHP_KEYWORDS = setOf(
    "abstract", "and", "array", "as", "break", "callable", "case", "catch", "class", "clone",
    "const", "continue", "declare", "default", "do", "echo", "else", "elseif", "empty",
    "enddeclare", "endfor", "endforeach", "endif", "endswitch", "endwhile", "enum", "extends",
    "final", "finally", "fn", "for", "foreach", "function", "global", "goto", "if", "implements",
    "include", "include_once", "instanceof", "insteadof", "interface", "isset", "list", "match",
    "namespace", "new", "or", "parent", "print", "private", "protected", "public", "readonly",
    "require", "require_once", "return", "self", "static", "switch", "throw", "trait", "try",
    "unset", "use", "var", "while", "xor", "yield", "true", "false", "null"
)

private val JAVA_KEYWORDS = setOf(
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
    "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
    "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
    "new", "package", "permits", "private", "protected", "public", "record", "return", "sealed",
    "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
    "transient", "try", "var", "void", "volatile", "while", "yield", "true", "false", "null"
)

private val PY_KEYWORDS = setOf(
    "and", "as", "assert", "async", "await", "break", "case", "class", "continue", "def", "del",
    "elif", "else", "except", "False", "finally", "for", "from", "global", "if", "import", "in",
    "is", "lambda", "match", "None", "nonlocal", "not", "or", "pass", "raise", "return", "self",
    "True", "try", "while", "with", "yield"
)

private val KOTLIN_KEYWORDS = setOf(
    "abstract", "as", "break", "by", "catch", "class", "companion", "const", "continue", "data",
    "do", "else", "enum", "false", "final", "finally", "for", "fun", "if", "import", "in", "init",
    "interface", "internal", "is", "lateinit", "null", "object", "open", "operator", "override",
    "package", "private", "protected", "public", "return", "sealed", "super", "suspend", "this",
    "throw", "true", "try", "typealias", "val", "var", "vararg", "when", "while"
)

private val JS_KEYWORDS = setOf(
    "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default",
    "delete", "do", "else", "export", "extends", "false", "finally", "for", "function", "if",
    "import", "in", "instanceof", "let", "new", "null", "of", "return", "super", "switch", "this",
    "throw", "true", "try", "typeof", "var", "void", "while", "with", "yield", "static"
)
