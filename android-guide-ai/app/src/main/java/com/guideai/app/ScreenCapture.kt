private fun captureInternal(context: Context): String? {
    val activeProjection = projection ?: return null
    val metrics = DisplayMetrics()
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    @Suppress("DEPRECATION")
    windowManager.defaultDisplay.getRealMetrics(metrics)

    val fullWidth = metrics.widthPixels
    val fullHeight = metrics.heightPixels

    // Sirf upar 50% capture karo
    val captureHeight = (fullHeight * 0.50).toInt()

    // Resolution half karo — Gemini ko full HD ki zarurat nahi
    val scaledWidth = fullWidth / 2
    val scaledHeight = captureHeight / 2

    var reader: ImageReader? = null
    var virtualDisplay: android.hardware.display.VirtualDisplay? = null

    return try {
        reader = ImageReader.newInstance(
            fullWidth,
            fullHeight,
            PixelFormat.RGBA_8888,
            2
        )
        virtualDisplay = activeProjection.createVirtualDisplay(
            "GuideAI",
            fullWidth,
            fullHeight,
            metrics.densityDpi,
            0,
            reader.surface,
            null,
            null
        )

        // Retry 5 baar — 100ms interval
        var image: android.media.Image? = null
        repeat(5) {
            if (image == null) {
                Thread.sleep(100)
                image = reader?.acquireLatestImage()
            }
        }

        if (image == null) return null

        val plane = image!!.planes[0]
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val bitmapWidth = rowStride / pixelStride

        // Full bitmap banao
        val fullBitmap = Bitmap.createBitmap(
            bitmapWidth,
            fullHeight,
            Bitmap.Config.ARGB_8888
        )
        fullBitmap.copyPixelsFromBuffer(plane.buffer)
        image!!.close()

        // Upar 50% crop karo
        val croppedBitmap = Bitmap.createBitmap(
            fullBitmap,
            0,
            0,
            fullWidth.coerceAtMost(bitmapWidth),
            captureHeight
        )
        fullBitmap.recycle()

        // Scale down — half resolution
        val scaledBitmap = Bitmap.createScaledBitmap(
            croppedBitmap,
            scaledWidth,
            scaledHeight,
            true
        )
        croppedBitmap.recycle()

        // Quality 40 — file size bahut chhoti
        val output = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 40, output)
        val encoded = "data:image/jpeg;base64," + Base64.encodeToString(
            output.toByteArray(),
            Base64.NO_WRAP
        )
        scaledBitmap.recycle()
        output.reset()
        encoded

    } catch (e: Exception) {
        null
    } finally {
        try { virtualDisplay?.release() } catch (e: Exception) {}
        try { reader?.close() } catch (e: Exception) {}
    }
}
