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
        val serviceIntent = Intent(context, CaptureService::class.java)
        context.startForegroundService(serviceIntent)
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(resultCode, data)
    }

    fun capture(context: Context): String? {
        val activeProjection = projection ?: return null
        val metrics = DisplayMetrics()
        (context.getSystemService(WindowManager::class.java)).defaultDisplay.getRealMetrics(metrics)
        val reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        val virtualDisplay = activeProjection.createVirtualDisplay("Guide AI", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, 0, reader.surface, null, null)
        return try {
            Thread.sleep(150)
            val image = reader.acquireLatestImage() ?: return null
            val plane = image.planes[0]
            val bitmap = Bitmap.createBitmap(plane.rowStride / plane.pixelStride, metrics.heightPixels, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(plane.buffer)
            image.close()
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, output)
            "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } finally {
            virtualDisplay.release()
            reader.close()
        }
    }

    fun stop(context: Context) {
        projection?.stop()
        projection = null
        context.stopService(Intent(context, CaptureService::class.java))
    }
}
