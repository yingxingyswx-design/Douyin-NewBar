package com.autumn.douyin.liquidglass.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.autumn.douyin.liquidglass.ModuleLog

/**
 * Experimental root capture fallback for Android 16+, where the old
 * SurfaceControl hidden APIs are gone. It shells out to `su -c screencap -p`,
 * crops the bar strip, downscales it, and delivers ~5 fps. Slow, but it can see
 * SurfaceView/DRM video layers that normal PixelCopy cannot.
 */
class ScreencapRootCapture(
    private val context: Context,
    private val onFrame: (Bitmap?) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var worker: Thread? = null
    private var running = false
    private var region: Rect = Rect()
    private var failures = 0

    fun start(captureRegion: Rect) {
        if (running) return
        region = Rect(captureRegion)
        running = true
        failures = 0
        worker = Thread(::loop, "m3-root-screencap").apply {
            isDaemon = true
            start()
        }
        ModuleLog.info("root screencap started region=$region")
    }

    fun updateRegion(captureRegion: Rect) {
        region = Rect(captureRegion)
    }

    fun stop() {
        if (!running) {
            onFrame(null)
            return
        }
        running = false
        worker?.interrupt()
        worker = null
        mainHandler.post { onFrame(null) }
        ModuleLog.info("root screencap stopped")
    }

    private fun loop() {
        while (running) {
            val startedAt = SystemClock.elapsedRealtime()
            val bitmap = runCatching { captureOnce() }.getOrElse { throwable ->
                ModuleLog.error("root screencap failed", throwable)
                null
            }
            if (bitmap != null) {
                failures = 0
                mainHandler.post { onFrame(bitmap) }
            } else {
                failures += 1
                if (failures >= 3) {
                    ModuleLog.error("root screencap unavailable, stopping")
                    stop()
                    return
                }
            }
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            val sleep = (FrameIntervalMs - elapsed).coerceAtLeast(60L)
            runCatching { Thread.sleep(sleep) }
        }
    }

    private fun captureOnce(): Bitmap? {
        val process = ProcessBuilder("/system/bin/su", "-c", "screencap -p")
            .redirectErrorStream(false)
            .start()
        val bytes = process.inputStream.readBytes()
        process.waitFor()
        if (bytes.isEmpty()) return null
        val full = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val display = context.resources.displayMetrics
        val current = Rect(region)
        if (current.isEmpty) {
            current.set(0, display.heightPixels - 160, display.widthPixels, display.heightPixels)
        }
        current.intersect(0, 0, full.width, full.height)
        if (current.isEmpty) {
            full.recycle()
            return null
        }
        val crop = Bitmap.createBitmap(full, current.left, current.top, current.width(), current.height())
        full.recycle()
        val scale = 270f / crop.width.coerceAtLeast(1)
        val targetWidth = 270
        val targetHeight = (crop.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(crop, targetWidth, targetHeight, true)
        crop.recycle()
        return scaled
    }

    private companion object {
        const val FrameIntervalMs = 160L
    }
}
