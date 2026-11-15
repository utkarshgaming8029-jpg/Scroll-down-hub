package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.graphics.scale
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MediaStorageHelper(private val context: Context) {

    private val mediaDir: File by lazy {
        File(context.filesDir, "scrolldown_media").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Compresses picked image to <= ~160KB JPEG Data URI so it syncs cleanly
     * inside Cloud Firestore documents across devices within the 1MB doc limit.
     */
    suspend fun persistCompressedImage(uri: Uri, maxDim: Int = 720, quality: Int = 72): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                }
                var sampleSize = 1
                while (boundsOpts.outWidth / sampleSize > maxDim || boundsOpts.outHeight / sampleSize > maxDim) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: error("Unable to decode selected image")

                val scale = minOf(
                    maxDim.toFloat() / decoded.width.coerceAtLeast(1),
                    maxDim.toFloat() / decoded.height.coerceAtLeast(1),
                    1f
                )
                val targetBitmap = if (scale < 1f) {
                    decoded.scale(
                        (decoded.width * scale).toInt().coerceAtLeast(1),
                        (decoded.height * scale).toInt().coerceAtLeast(1)
                    ).also { decoded.recycle() }
                } else {
                    decoded
                }

                val baos = ByteArrayOutputStream()
                var currentQuality = quality
                targetBitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, baos)
                while (baos.size() > 160_000 && currentQuality > 30) {
                    baos.reset()
                    currentQuality -= 12
                    targetBitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, baos)
                }
                targetBitmap.recycle()

                val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                "data:image/jpeg;base64,$b64"
            }
        }

    /**
     * Copies picked video into internal app storage, and if <= 240KB embeds as a Base64 Data URI
     * so short clips play seamlessly across devices inside Firestore docs.
     */
    suspend fun persistVideo(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val outFile = File(mediaDir, "vid_${UUID.randomUUID()}.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            } ?: error("Unable to open selected video")

            if (outFile.length() in 1..240_000L) {
                val bytes = outFile.readBytes()
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                "data:video/mp4;base64,$b64"
            } else {
                Uri.fromFile(outFile).toString()
            }
        }
    }

    /**
     * Safely removes locally persisted internal app media files when a Story or Post is deleted.
     * Inline Base64 Data URIs live inside the Firestore document itself and are removed on doc delete.
     * Missing files or external URIs never fail deletion.
     */
    suspend fun deleteStoredMediaIfLocal(uriOrDataUri: String?) = withContext(Dispatchers.IO) {
        if (uriOrDataUri.isNullOrBlank() || uriOrDataUri.startsWith("data:")) return@withContext
        runCatching {
            val parsed = uriOrDataUri.toUri()
            if (parsed.scheme == "file") {
                val path = parsed.path ?: return@runCatching
                val target = File(path).canonicalFile
                val root = mediaDir.canonicalFile
                if (target.path.startsWith(root.path) && target.exists()) {
                    target.delete()
                }
            }
        }
    }

    /**
     * Encodes recorded voice message (.m4a) as Data URI when <= 240KB for cross-device Firestore sync.
     */
    suspend fun persistVoiceNote(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = audioFile.readBytes()
            require(bytes.isNotEmpty()) { "Recorded audio clip is empty." }
            if (bytes.size <= 240_000) {
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                "data:audio/mp4;base64,$b64"
            } else {
                Uri.fromFile(audioFile).toString()
            }
        }
    }

    companion object {
        fun resolveCoilModel(uriOrDataUri: String?): Any? {
            if (uriOrDataUri.isNullOrBlank()) return null
            return if (uriOrDataUri.startsWith("data:image/")) {
                val commaIdx = uriOrDataUri.indexOf(',')
                if (commaIdx != -1) {
                    runCatching {
                        Base64.decode(uriOrDataUri.substring(commaIdx + 1), Base64.DEFAULT)
                    }.getOrNull()
                } else null
            } else {
                uriOrDataUri.toUri()
            }
        }

        fun resolvePlayableUri(context: Context, uriOrDataUri: String): Uri {
            if (uriOrDataUri.startsWith("data:video/") || uriOrDataUri.startsWith("data:audio/")) {
                val commaIdx = uriOrDataUri.indexOf(',')
                if (commaIdx != -1) {
                    val isAudio = uriOrDataUri.startsWith("data:audio/")
                    val ext = if (isAudio) ".m4a" else ".mp4"
                    val cacheFile = File(context.cacheDir, "sd_media_${uriOrDataUri.hashCode()}$ext")
                    if (!cacheFile.exists() || cacheFile.length() == 0L) {
                        runCatching {
                            val decoded = Base64.decode(uriOrDataUri.substring(commaIdx + 1), Base64.DEFAULT)
                            FileOutputStream(cacheFile).use { it.write(decoded) }
                        }
                    }
                    if (cacheFile.exists()) return Uri.fromFile(cacheFile)
                }
            }
            return uriOrDataUri.toUri()
        }
    }
}
