package com.bookmark.metadata.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.palette.graphics.Palette
import com.bookmark.core.data.IoDispatcher
import com.bookmark.metadata.http.UserAgent
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** A thumbnail that made it to disk. */
data class StoredThumbnail(
    /** Bare filename inside `filesDir/thumbnails/`. */
    val relativePath: String,
    val width: Int,
    val height: Int,
    val accentColor: Int?,
)

/**
 * Download, vet, downscale, encode, store (spec 7.4).
 *
 * Candidates are tried in order and a rejection falls through to the next, so a
 * page whose first `og:image` is a 1x1 tracking pixel still gets a real preview
 * from its second. When every candidate is rejected the caller falls back to the
 * generated monogram tile, which is never an error state (spec 8.3).
 *
 * The decisions live in [ThumbnailPolicy]; this class is only the platform calls
 * around them.
 */
@Singleton
class ThumbnailPipeline @Inject constructor(
    private val client: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun store(
        candidates: List<String>,
        bookmarkId: String,
        directory: File,
    ): StoredThumbnail? = withContext(io) {
        candidates.firstNotNullOfOrNull { candidate ->
            runCatching { attempt(candidate, bookmarkId, directory) }.getOrNull()
        }
    }

    /**
     * "Pick from device" (spec 5.2 item 5): the same vet/downscale/encode
     * policy as a fetched candidate, just sourced from a local `Uri` (the
     * Android Photo Picker's result) instead of a network download.
     */
    suspend fun storeFromUri(
        context: Context,
        uri: Uri,
        bookmarkId: String,
        directory: File,
    ): StoredThumbnail? = withContext(io) {
        // MAX_DOWNLOAD_BYTES guards an *untrusted remote* candidate (spec 7.4
        // step 1); it does not apply here -- a modern phone photo routinely
        // exceeds 10MB, and capping this read would silently downgrade a
        // deliberate "Pick from device" choice to the monogram tile.
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext null
        encode(bytes, bookmarkId, directory)
    }

    private fun attempt(url: String, bookmarkId: String, directory: File): StoredThumbnail? {
        val bytes = download(url) ?: return null
        return encode(bytes, bookmarkId, directory)
    }

    private fun encode(bytes: ByteArray, bookmarkId: String, directory: File): StoredThumbnail? {
        // Bounds-only decode first: rejecting a tracking pixel must not cost the
        // memory of materialising it.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        if (!ThumbnailPolicy.isAcceptable(bounds.outWidth, bounds.outHeight)) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = ThumbnailPolicy.sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

        val scaled = try {
            val (width, height) = ThumbnailPolicy.targetSize(decoded.width, decoded.height)
            if (width == decoded.width && height == decoded.height) {
                decoded
            } else {
                Bitmap.createScaledBitmap(decoded, width, height, true)
            }
        } catch (e: IllegalArgumentException) {
            decoded.recycle()
            return null
        }

        return try {
            // Written aside and renamed so a crash mid-encode cannot leave a
            // truncated file under the name the database already points at. A
            // leftover .tmp is not in allThumbnailPaths(), so the orphan sweep
            // reclaims it.
            if (!directory.exists() && !directory.mkdirs()) return null
            val target = File(directory, "$bookmarkId$EXTENSION")
            val temporary = File(directory, "$bookmarkId$EXTENSION$TEMP_SUFFIX")

            val encoded = temporary.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, ThumbnailPolicy.WEBP_QUALITY, out)
            }
            if (!encoded || !temporary.renameTo(target)) {
                temporary.delete()
                return null
            }

            StoredThumbnail(
                relativePath = target.name,
                width = scaled.width,
                height = scaled.height,
                // Tints the placeholder behind the image while it loads (spec 7.4).
                accentColor = Palette.from(scaled).generate().dominantSwatch?.rgb,
            )
        } finally {
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }

    /** Same timeout policy as the page fetch, capped at 10MB (spec 7.4). */
    private fun download(url: String): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UserAgent.CHROME)
            .header("Accept", "image/*")
            .header("Range", "bytes=0-${ThumbnailPolicy.MAX_DOWNLOAD_BYTES - 1}")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val mimeType = response.header("Content-Type").orEmpty().substringBefore(';').trim()
                if (!mimeType.startsWith("image/", ignoreCase = true)) return null

                val body = response.body
                // A declared length over the cap is rejected before reading.
                if (body.contentLength() > ThumbnailPolicy.MAX_DOWNLOAD_BYTES) return null
                readCapped(body.byteStream())
            }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            // OkHttp rejects a malformed candidate URL outright.
            null
        }
    }

    /** Reads up to the cap; a body that exceeds it is discarded, not truncated. */
    private fun readCapped(stream: java.io.InputStream): ByteArray? {
        val limit = ThumbnailPolicy.MAX_DOWNLOAD_BYTES.toInt()
        val out = java.io.ByteArrayOutputStream(INITIAL_BUFFER_BYTES)
        val chunk = ByteArray(READ_CHUNK_BYTES)
        while (true) {
            val read = stream.read(chunk)
            if (read == -1) break
            if (out.size() + read > limit) return null
            out.write(chunk, 0, read)
        }
        return out.toByteArray().takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val EXTENSION = ".webp"
        const val TEMP_SUFFIX = ".tmp"
        const val READ_CHUNK_BYTES = 32 * 1024
        const val INITIAL_BUFFER_BYTES = 64 * 1024
    }
}
