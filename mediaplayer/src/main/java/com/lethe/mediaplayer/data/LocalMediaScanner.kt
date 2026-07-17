package com.lethe.mediaplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Durchsucht den geräteweiten Medienspeicher (MediaStore) nach lokal vorhandenen Musikdateien.
 *
 * MediaStore indexiert automatisch die "üblich verdächtigen" Ordner (Music, Download, Podcasts,
 * Ringtones …) und liest dabei bereits die ID3-Tags aus. Es werden nur Titel zurückgegeben, die
 * - mindestens eine Minute lang sind und
 * - sowohl einen Interpreten als auch einen Titel im Tag hinterlegt haben.
 */
@Singleton
class LocalMediaScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        /** Mindestlänge eines Titels, damit er als "Lied" gilt (1 Minute). */
        private const val MIN_DURATION_MS = 60_000L
    }

    private val albumArtBase: Uri = Uri.parse("content://media/external/audio/albumart")

    /** Liest alle passenden lokalen Musiktitel aus dem MediaStore (Blocking-IO). */
    fun scan(): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )
        // Nur echte Musik, min. 1 Minute, Titel + Interpret vorhanden (kein MediaStore-Platzhalter).
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            append(" AND ${MediaStore.Audio.Media.DURATION} >= ?")
            append(" AND ${MediaStore.Audio.Media.TITLE} IS NOT NULL AND TRIM(${MediaStore.Audio.Media.TITLE}) != ''")
            append(" AND ${MediaStore.Audio.Media.ARTIST} IS NOT NULL AND TRIM(${MediaStore.Audio.Media.ARTIST}) != ''")
            append(" AND ${MediaStore.Audio.Media.ARTIST} != ?")
        }
        val selectionArgs = arrayOf(MIN_DURATION_MS.toString(), MediaStore.UNKNOWN_STRING)
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val tracks = mutableListOf<Track>()
        context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol)?.trim().orEmpty()
                val artist = cursor.getString(artistCol)?.trim().orEmpty()
                if (title.isBlank() || artist.isBlank()) continue
                val durationMs = cursor.getLong(durationCol)
                val albumId = cursor.getLong(albumIdCol)
                val audioUri = ContentUris.withAppendedId(collection, id)
                val coverUri = ContentUris.withAppendedId(albumArtBase, albumId)
                tracks += Track(
                    id = "local_$id",
                    title = title,
                    artist = artist,
                    coverUrl = coverUri.toString(),
                    audioUrl = audioUri.toString(),
                    durationSec = (durationMs / 1000L).toInt(),
                    source = "local"
                )
            }
        }
        return tracks
    }
}
