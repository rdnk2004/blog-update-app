package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Resizes and compresses selected or captured photos locally before upload:
 * max 1600 px wide, JPEG quality 80 (0.8), preserving upright EXIF orientation.
 */
class ImageCompressor(private val context: Context) {

    companion object {
        private const val MAX_WIDTH_PX = 1600
        private const val JPEG_QUALITY = 80
    }

    /**
     * Creates a temporary content URI via FileProvider for zero-permission system camera capture.
     */
    fun createCameraCaptureUri(): Uri {
        val captureDir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val photoFile = File(captureDir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }

    /**
     * Compresses the photo at [sourceUri] to a JPEG file in app-private storage (max 1600px wide, quality 80).
     * Returns the absolute file path of the compressed JPEG.
     */
    suspend fun compressToDraftFile(sourceUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val orientation = readExifOrientation(sourceUri)

            // Step 1: Read bounds without loading full bitmap into memory
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: return@withContext Result.failure(IllegalStateException("Could not open photo."))

            val rawWidth = boundsOptions.outWidth
            val rawHeight = boundsOptions.outHeight
            if (rawWidth <= 0 || rawHeight <= 0) {
                return@withContext Result.failure(IllegalStateException("Photo could not be read."))
            }

            // Step 2: Calculate safe subsampling factor
            val maxDim = max(rawWidth, rawHeight)
            var sampleSize = 1
            while (maxDim / (sampleSize * 2) >= MAX_WIDTH_PX) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decoded = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext Result.failure(IllegalStateException("Could not decode photo."))

            // Step 3: Apply EXIF rotation so portrait photos stay upright
            val oriented = applyExifOrientation(decoded, orientation)
            if (oriented !== decoded) {
                decoded.recycle()
            }

            // Step 4: Scale down proportionally if wider than 1600px
            val finalBitmap = if (oriented.width > MAX_WIDTH_PX) {
                val scale = MAX_WIDTH_PX.toFloat() / oriented.width.toFloat()
                val targetHeight = (oriented.height * scale).roundToInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(oriented, MAX_WIDTH_PX, targetHeight, true)
                if (scaled !== oriented) {
                    oriented.recycle()
                }
                scaled
            } else {
                oriented
            }

            // Step 5: Save JPEG quality 80 to app-private draft_photos folder
            val outDir = File(context.filesDir, "draft_photos").apply { mkdirs() }
            // Clean older draft photos to keep storage tidy
            outDir.listFiles()?.forEach { oldFile ->
                if (System.currentTimeMillis() - oldFile.lastModified() > 24 * 60 * 60 * 1000L) {
                    oldFile.delete()
                }
            }
            val outFile = File(outDir, "photo_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { fos ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
            }
            finalBitmap.recycle()

            Result.success(outFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readExifOrientation(uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
