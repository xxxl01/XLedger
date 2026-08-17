package com.xdl.xledger.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** 压缩后的导入图片（WebP base64，对齐旧版 expo-image-manipulator 行为）。 */
data class CompressedImportImage(
    val sourceUri: String,
    val uri: String,
    val width: Int,
    val height: Int,
    val mimeType: String = "image/webp",
    val dataUrl: String,
)

/**
 * 图片压缩：读取相册 uri → 限制尺寸 → 压缩为 WebP base64 data URL。
 * 对应旧版 service/images.ts 的 compressImageForLlm。
 */
object ImageCompressor {

    private const val MAX_DIMENSION = 2048
    private const val WEBP_QUALITY = 80

    suspend fun compress(context: Context, sourceUri: Uri): CompressedImportImage =
        withContext(Dispatchers.IO) {
            val bitmap = decodeSampled(context, sourceUri, MAX_DIMENSION)
                ?: throw IllegalStateException("无法读取图片")

            val output = ByteArrayOutputStream()
            val compressed = bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, output)
            if (!compressed || output.size() == 0) {
                throw IllegalStateException("图片压缩失败")
            }

            val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            CompressedImportImage(
                sourceUri = sourceUri.toString(),
                uri = sourceUri.toString(),
                width = bitmap.width,
                height = bitmap.height,
                dataUrl = "data:image/webp;base64,$base64",
            )
        }

    /** 按目标最大边采样解码，控制内存占用。 */
    private fun decodeSampled(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension ||
            bounds.outHeight / sampleSize > maxDimension
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }
}
