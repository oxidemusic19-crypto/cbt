package com.cbt.musicplayer.studio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Uploads audio to a separately hosted HTTPS source-separation API. */
object StemApi {
    fun upload(context: Context, uri: Uri, endpoint: String, apiKey: String): String {
        require(endpoint.startsWith("https://")) { "Use a secure HTTPS server URL." }
        val boundary = "CBT-${UUID.randomUUID()}"
        val connection = (URL(endpoint.trimEnd('/') + "/separate").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            } ?: "audio-upload"
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open selected audio file.")
            BufferedOutputStream(connection.outputStream).use { output ->
                output.write("--$boundary\r\n".toByteArray())
                output.write("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n".toByteArray())
                output.write("Content-Type: $mime\r\n\r\n".toByteArray())
                BufferedInputStream(input).use { stream ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
                output.write("\r\n--$boundary--\r\n".toByteArray())
                output.flush()
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Server returned HTTP $code: $body")
            return body.ifBlank { "Server accepted request (HTTP $code), but returned an empty response." }
        } finally {
            connection.disconnect()
        }
    }
}
