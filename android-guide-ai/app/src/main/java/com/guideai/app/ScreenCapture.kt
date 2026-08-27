package com.guideai.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.ByteArrayOutputStream

object ScreenCapture {
    private var projection: MediaProjection? = null

    fun start(context: Context, resultCode: Int, data: Intent) {
        try {
            val serviceIntent = Intent(context, CaptureService::class.java)
            context.startForegroundService(serviceIntent)
            val manager = context.getSystemService(MediaProjectionManager::class.java)
            projection = manager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            projection = null
        }
    }

    fun capture(context: Context): String? {
        return try {
            captureInternal(context)
        } catch (e: Throwable) {
            null
        }
    }

    private fun captureInternal(context: Context): String? {
        val activeProjection = projection ?: return null
        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        var reader: ImageReader? = null
        var virtualDisplay: android.hardware.display.VirtualDisplay? = null

        return try {
            reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
            virtualDisplay = activeProjection.createVirtualDisplay(
                "GuideAI",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                0,
                reader.surface,
                null,
                null
            )
            Thread.sleep(300)
            val image = reader.acquireLatestImage() ?: return null
            val plane = image.planes[0]
            val bitmap = Bitmap.createBitmap(plane.rowStride / plane.pixelStride, metrics.heightPixels, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(plane.buffer)
            image.close()
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, output)
            val encoded = "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            bitmap.recycle()
            output.reset()
            encoded
        } catch (e: Exception) {
            null
        } finally {
            try { virtualDisplay?.release() } catch (e: Exception) {}
            try { reader?.close() } catch (e: Exception) {}
        }
    }

    fun stop(context: Context) {
        try { projection?.stop() } catch (e: Exception) {}
        projection = null
        try { context.stopService(Intent(context, CaptureService::class.java)) } catch (e: Exception) {}
    }
}
