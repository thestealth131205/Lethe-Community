package com.securechat.app.media.gif

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Median-Cut-Farbquantisierung: reduziert ein gewichtetes Farb-Histogramm auf max.
 * [buildPalette]-`maxColors` Paletteneinträge (Standardalgorithmus, keine Fremdbibliothek nötig).
 * [nearestIndex] mapped eine RGB-Farbe per euklidischem Abstand auf den nächsten Paletten-Index.
 */
object MedianCutQuantizer {

    private class ColorBucket(val colors: MutableList<IntArray>) // je Eintrag: [r, g, b, count]

    fun buildPalette(histogram: Map<Int, Int>, maxColors: Int): IntArray {
        if (histogram.isEmpty()) return intArrayOf(0x000000)
        val entries = histogram.entries.map { (rgb, count) ->
            intArrayOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, count)
        }.toMutableList()
        if (entries.size <= maxColors) {
            return entries.map { (it[0] shl 16) or (it[1] shl 8) or it[2] }.toIntArray()
        }

        val buckets = mutableListOf(ColorBucket(entries))
        while (buckets.size < maxColors) {
            var splitIdx = -1
            var bestRange = -1
            var bestAxis = 0
            for ((idx, bucket) in buckets.withIndex()) {
                if (bucket.colors.size <= 1) continue
                for (axis in 0..2) {
                    val min = bucket.colors.minOf { it[axis] }
                    val max = bucket.colors.maxOf { it[axis] }
                    val range = max - min
                    if (range > bestRange) {
                        bestRange = range; splitIdx = idx; bestAxis = axis
                    }
                }
            }
            if (splitIdx == -1) break

            val bucket = buckets[splitIdx]
            bucket.colors.sortBy { it[bestAxis] }
            val totalWeight = bucket.colors.sumOf { it[3].toLong() }
            var cumWeight = 0L
            var splitPos = bucket.colors.size / 2
            for ((i, c) in bucket.colors.withIndex()) {
                cumWeight += c[3]
                if (cumWeight >= totalWeight / 2) {
                    splitPos = (i + 1).coerceIn(1, bucket.colors.size - 1)
                    break
                }
            }
            val left = bucket.colors.subList(0, splitPos).toMutableList()
            val right = bucket.colors.subList(splitPos, bucket.colors.size).toMutableList()
            buckets[splitIdx] = ColorBucket(left)
            buckets.add(ColorBucket(right))
        }

        return buckets.map { bucket ->
            var r = 0L; var g = 0L; var b = 0L; var w = 0L
            for (c in bucket.colors) {
                r += c[0].toLong() * c[3]; g += c[1].toLong() * c[3]; b += c[2].toLong() * c[3]; w += c[3]
            }
            if (w == 0L) w = 1
            val rr = (r / w).toInt().coerceIn(0, 255)
            val gg = (g / w).toInt().coerceIn(0, 255)
            val bb = (b / w).toInt().coerceIn(0, 255)
            (rr shl 16) or (gg shl 8) or bb
        }.toIntArray()
    }

    fun nearestIndex(rgb: Int, palette: IntArray): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        var best = 0
        var bestDist = Int.MAX_VALUE
        for (i in palette.indices) {
            val pr = (palette[i] shr 16) and 0xFF
            val pg = (palette[i] shr 8) and 0xFF
            val pb = palette[i] and 0xFF
            val dr = r - pr; val dg = g - pg; val db = b - pb
            val dist = dr * dr + dg * dg + db * db
            if (dist < bestDist) {
                bestDist = dist; best = i
                if (dist == 0) break
            }
        }
        return best
    }
}

/**
 * Minimaler, reiner Kotlin-GIF89a-Encoder (kein natives Binär/Fremd-Lib, F-Droid-tauglich).
 * Ersatz für FFmpegs GIF-Export im `foss`-Flavor (siehe [com.securechat.app.media.Media3FfmpegProvider]).
 *
 * Nutzt pro Frame eine lokale Farbtabelle (max. 256 Farben, via [MedianCutQuantizer]) und einen
 * unkomprimierten LZW-Bildstrom (spezifikationskonform, aber ohne Wörterbuch-Kompression – bewusste
 * Vereinfachung zugunsten von Robustheit/Korrektheit gegenüber Dateigröße; für Sticker-/Chat-GIFs
 * unkritisch).
 */
class GifEncoder(private val out: OutputStream) {

    private var headerWritten = false

    fun writeHeader(width: Int, height: Int) {
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(width); writeShort(height)
        out.write(0x00) // kein globales Farbtabelle, Farbauflösung/Sortierung irrelevant
        out.write(0x00) // Hintergrundfarb-Index
        out.write(0x00) // Pixel-Seitenverhältnis
        // NETSCAPE2.0 Application Extension → Endlosschleife (vor dem ersten Frame)
        out.write(0x21); out.write(0xFF); out.write(0x0B)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(0x03); out.write(0x01)
        writeShort(0) // Loop-Zähler 0 = unendlich
        out.write(0x00)
        headerWritten = true
    }

    fun writeFrame(indices: ByteArray, palette: IntArray, width: Int, height: Int, delayCs: Int) {
        check(headerWritten) { "writeHeader() muss zuerst aufgerufen werden" }
        val paletteSizePow2 = paletteSizePow2(palette.size)
        val bitsPerPixel = colorTableBits(paletteSizePow2)

        // Graphic Control Extension
        out.write(0x21); out.write(0xF9); out.write(0x04)
        out.write(0x04) // Disposal-Methode=1 (nicht entsorgen), keine Transparenz
        writeShort(delayCs.coerceIn(1, 65535))
        out.write(0x00) // kein Transparenz-Index
        out.write(0x00)

        // Image Descriptor
        out.write(0x2C)
        writeShort(0); writeShort(0)
        writeShort(width); writeShort(height)
        out.write(0x80 or (bitsPerPixel - 1)) // lokale Farbtabelle, Größe = bitsPerPixel-1

        // Lokale Farbtabelle (mit Schwarz aufgefüllt bis paletteSizePow2)
        for (i in 0 until paletteSizePow2) {
            val rgb = if (i < palette.size) palette[i] else 0
            out.write((rgb shr 16) and 0xFF)
            out.write((rgb shr 8) and 0xFF)
            out.write(rgb and 0xFF)
        }

        val minCodeSize = bitsPerPixel.coerceAtLeast(2)
        out.write(minCodeSize)
        val compressed = LzwEncoder.encode(indices, minCodeSize)
        writeSubBlocks(compressed)
        out.write(0x00) // Block-Terminator
    }

    fun writeTrailer() {
        out.write(0x3B)
        out.flush()
    }

    private fun writeSubBlocks(data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val chunk = minOf(255, data.size - offset)
            out.write(chunk)
            out.write(data, offset, chunk)
            offset += chunk
        }
    }

    private fun writeShort(value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    private fun paletteSizePow2(size: Int): Int {
        var p = 2
        while (p < size) p = p shl 1
        return p.coerceAtLeast(2)
    }

    private fun colorTableBits(paletteSizePow2: Int): Int {
        var bits = 1
        while ((1 shl bits) < paletteSizePow2) bits++
        return bits
    }
}

/**
 * Spezifikationskonformer GIF-LZW-Bildstrom OHNE Wörterbuch-Kompression: Clear-Code, dann jeder
 * Pixel-Index 1:1 als eigener Code fester Breite ([minCodeSize] + 1 Bit), End-Code. Da alle
 * Rohindizes < Clear-Code sind, ist nie eine Coderweiterung nötig – Trade-off größere Dateien
 * gegen maximale Korrektheit ohne CI-Testlauf.
 */
private object LzwEncoder {
    fun encode(indices: ByteArray, minCodeSize: Int): ByteArray {
        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1
        val codeSize = minCodeSize + 1

        val output = ByteArrayOutputStream()
        var bitBuffer = 0L
        var bitCount = 0

        fun emit(code: Int) {
            bitBuffer = bitBuffer or (code.toLong() shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                output.write((bitBuffer and 0xFF).toInt())
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
            }
        }

        emit(clearCode)
        for (b in indices) emit(b.toInt() and 0xFF)
        emit(endCode)
        if (bitCount > 0) output.write((bitBuffer and 0xFF).toInt())
        return output.toByteArray()
    }
}
