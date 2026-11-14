package com.cbt.musicplayer.data

import android.content.Context
import android.provider.MediaStore

object MusicRepository {
    fun scan(context: Context): List<Song> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val result = mutableListOf<Song>()
        context.contentResolver.query(
            collection, projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val songId = cursor.getLong(id)
                result += Song(
                    songId,
                    cursor.getString(title) ?: "Unknown title",
                    cursor.getString(artist) ?: "Unknown artist",
                    cursor.getString(album) ?: "Unknown album",
                    cursor.getLong(duration),
                    android.content.ContentUris.withAppendedId(collection, songId)
                )
            }
        }
        return result
    }
}
