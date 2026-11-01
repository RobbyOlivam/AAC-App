package com.robbyolivam.aac.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.util.UUID

/** Button pictures: stored small (≤512px JPEG) so boards stay fast on cheap tablets. */
class MediaFiles(private val context: Context, private val store: VocabularyStore) {

    private val cache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun load(relativePath: String): ImageBitmap? {
        cache.get(relativePath)?.let { return it }
        val file = store.resolve(relativePath)
        if (!file.exists()) return null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() ?: return null
        cache.put(relativePath, bitmap)
        return bitmap
    }

    /** Saves a picked image; returns its relative path. */
    fun importImage(uri: Uri, maxSize: Int = MAX_SIZE): String? =
        decode(uri, maxSize)?.let { saveBitmap(it, maxSize) }

    /** Loads a picked image (scaled down to [maxSize]) without saving it, e.g. for cropping first. */
    fun decode(uri: Uri, maxSize: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val size = info.size
                val scale = maxSize.toFloat() / maxOf(size.width, size.height)
                if (scale < 1f) decoder.setTargetSize((size.width * scale).toInt(), (size.height * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()

    fun saveBitmap(source: Bitmap, maxSize: Int = MAX_SIZE): String {
        val scale = maxSize.toFloat() / maxOf(source.width, source.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
        } else {
            source
        }
        val name = "media/img-${UUID.randomUUID()}.jpg"
        store.resolve(name).also { it.parentFile?.mkdirs() }.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)
        }
        return name
    }

    fun newAudioFile(): Pair<String, File> {
        val name = "media/audio-${UUID.randomUUID()}.m4a"
        return name to store.resolve(name).also { it.parentFile?.mkdirs() }
    }

    fun clearCache() = cache.evictAll()

    private companion object {
        const val MAX_SIZE = 512
    }
}
