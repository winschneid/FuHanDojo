package com.winschneid.fuhandojo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlin.math.max

/**
 * 写真を API に送れる大きさの JPEG にする。
 * 長辺を 1568px 以下に縮め（それより大きくしても画像認識の精度は上がらず、通信量と料金が増えるだけ）、
 * 撮影時の向き（EXIF）を反映する。
 */
class ImagePreparer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun prepare(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IOException("画像を開けませんでした")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("画像を読み込めませんでした")

        // まず 2 の累乗で粗く縮めてから読み込み、メモリを節約する
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("画像を読み込めませんでした")

        val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val scale = minOf(1f, MAX_EDGE.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        val prepared = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        ByteArrayOutputStream().use { out ->
            prepared.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    private companion object {
        const val MAX_EDGE = 1568
        const val JPEG_QUALITY = 85
    }
}
