package com.xdl.xledger.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 列表缩略图加载（content:// uri → Compose ImageBitmap）。 */
object BitmapLoader {

    /** 解码为缩略图；目标边 512px 以内，控制列表内存。 */
    suspend fun loadThumbnail(context: Context, uri: Uri, targetSize: Int = 512): ImageBitmap? =
        withContext(Dispatchers.IO) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                val bitmap: Bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val scale = maxOf(
                        info.size.width / targetSize.toFloat(),
                        info.size.height / targetSize.toFloat(),
                        1f,
                    )
                    decoder.setTargetSampleSize(scale.toInt())
                }
                bitmap.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
}
