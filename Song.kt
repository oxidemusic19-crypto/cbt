package com.cbt.musicplayer.data

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri
)

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(total / 60, total % 60)
}
