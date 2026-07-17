package com.lethe.mediaplayer.cast

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Winziger lokaler HTTP-Server, der lokale Medien (`content://`/`file://`) für ein Cast-Gerät
 * erreichbar macht. Chromecast-Empfänger können nur HTTP(S)-URLs abspielen – Geräte-lokale
 * Titel (MediaStore-Scan, importierte oder auto-heruntergeladene Dateien) müssen deshalb
 * über die LAN-IP des Telefons ausgeliefert werden.
 *
 * Unterstützt HTTP-Range-Anfragen (für Spulen auf dem Cast-Gerät). Bindet nur, solange
 * gecastet wird ([serve]/[stop]).
 */
@Singleton
class LocalCastServer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private data class Entry(val uri: Uri, val contentType: String)

    private val entries = HashMap<String, Entry>()
    private var serverSocket: ServerSocket? = null
    private var port: Int = 0

    /** Startet den Server (idempotent) und liefert die LAN-Basis-URL oder null, wenn keine IP verfügbar. */
    private fun ensureStarted(): String? {
        val ip = localIpAddress() ?: return null
        if (serverSocket == null) {
            val socket = ServerSocket(0)
            serverSocket = socket
            port = socket.localPort
            thread(isDaemon = true, name = "LocalCastServer") { acceptLoop(socket) }
        }
        return "http://$ip:$port"
    }

    /**
     * Registriert eine lokale Medien-URI und liefert eine über das LAN erreichbare HTTP-URL,
     * die auf das Cast-Gerät geladen werden kann. Gibt null zurück, wenn keine LAN-IP existiert.
     */
    @Synchronized
    fun serve(id: String, uri: Uri, contentType: String): String? {
        val base = ensureStarted() ?: return null
        val token = sanitize(id)
        entries[token] = Entry(uri, contentType)
        return "$base/$token"
    }

    @Synchronized
    fun stop() {
        entries.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun sanitize(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            thread(isDaemon = true) { runCatching { handle(client) } }
        }
    }

    private fun handle(client: Socket) {
        client.use { sock ->
            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val rawPath = parts[1].trimStart('/')
            val token = URLDecoder.decode(rawPath.substringBefore('?'), "UTF-8")

            var rangeHeader: String? = null
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = line.substringAfter(':').trim()
                }
            }

            val entry = synchronized(this) { entries[token] }
            val out = sock.getOutputStream()
            if (entry == null) {
                writeStatus(out, "404 Not Found")
                return
            }
            serveEntry(out, entry, rangeHeader)
        }
    }

    private fun serveEntry(out: OutputStream, entry: Entry, rangeHeader: String?) {
        val totalLength = contentLength(entry.uri)
        val input = runCatching { context.contentResolver.openInputStream(entry.uri) }.getOrNull()
        if (input == null) {
            writeStatus(out, "404 Not Found")
            return
        }
        input.use { stream ->
            var start = 0L
            var end = if (totalLength > 0) totalLength - 1 else Long.MAX_VALUE
            val isPartial = rangeHeader != null && totalLength > 0
            if (isPartial) {
                val spec = rangeHeader!!.removePrefix("bytes=").trim()
                val dash = spec.indexOf('-')
                if (dash >= 0) {
                    start = spec.substring(0, dash).toLongOrNull() ?: 0L
                    end = spec.substring(dash + 1).toLongOrNull()?.coerceAtMost(totalLength - 1) ?: (totalLength - 1)
                }
            }
            val header = StringBuilder()
            if (isPartial) {
                header.append("HTTP/1.1 206 Partial Content\r\n")
                header.append("Content-Range: bytes $start-$end/$totalLength\r\n")
            } else {
                header.append("HTTP/1.1 200 OK\r\n")
            }
            header.append("Content-Type: ${entry.contentType}\r\n")
            header.append("Accept-Ranges: bytes\r\n")
            if (totalLength > 0) {
                header.append("Content-Length: ${end - start + 1}\r\n")
            }
            header.append("Connection: close\r\n\r\n")
            out.write(header.toString().toByteArray())

            if (start > 0) skipFully(stream, start)
            copyRange(stream, out, if (totalLength > 0) end - start + 1 else Long.MAX_VALUE)
            out.flush()
        }
    }

    private fun copyRange(input: InputStream, out: OutputStream, maxBytes: Long) {
        val buffer = ByteArray(64 * 1024)
        var remaining = maxBytes
        while (remaining > 0) {
            val toRead = if (remaining < buffer.size) remaining.toInt() else buffer.size
            val read = input.read(buffer, 0, toRead)
            if (read < 0) break
            runCatching { out.write(buffer, 0, read) }.onFailure { return }
            remaining -= read
        }
    }

    private fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        val skipBuffer = ByteArray(64 * 1024)
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val read = input.read(skipBuffer, 0, minOf(skipBuffer.size.toLong(), remaining).toInt())
                if (read < 0) break
                remaining -= read
            }
        }
    }

    private fun contentLength(uri: Uri): Long = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
    }.getOrNull()?.takeIf { it >= 0 } ?: -1L

    private fun writeStatus(out: OutputStream, status: String) {
        runCatching {
            out.write("HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()
        }
    }

    /** Erste nicht-lokale IPv4-Adresse (z.B. WLAN 192.168.x.x) für die Cast-Erreichbarkeit im LAN. */
    private fun localIpAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()
}
