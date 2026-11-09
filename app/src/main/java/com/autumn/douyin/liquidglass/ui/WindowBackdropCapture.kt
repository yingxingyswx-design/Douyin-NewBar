package com.autumn.douyin.liquidglass.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.autumn.douyin.liquidglass.ModuleLog
import com.autumn.douyin.liquidglass.nativebar.VideoViewRegistry

/**
 * Captures the activity window strip behind the floating bar with PixelCopy.
 *
 * A 60 Hz Choreographer loop fills a 1/4-resolution destination bitmap (about
 * 270x30 px) and applies a cheap separable blur. The low resolution is what makes
 * the live lens affordable: full-screen readback at 60 fps would be far too slow.
 */
class WindowBackdropCapture(
    private val activity: Activity,
    private val overlayView: View,
    private val frostLevelProvider: () -> Int,
    private val onFrame: (ImageBitmap?) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var inFlight = false
    private var failures = 0
    private var lastCaptureNanos = 0L
    private var smallCaptureSupported = true
    private var cachedSurface: SurfaceView? = null
    private var cachedTexture: TextureView? = null
    private var lastSurfaceScanAt = 0L
    private var lastValidFrameAt = 0L
    private var inFlightSince = 0L
    private var capturedFrames = 0
    private var fpsWindowStartedAt = 0L

    fun start() {
        if (running) return
        running = true
        failures = 0
        lastCaptureNanos = 0L
        handler.post(captureRunnable)
    }

    fun stop() {
        if (!running) {
            onFrame(null)
            return
        }
        running = false
        handler.removeCallbacks(captureRunnable)
        inFlight = false
        onFrame(null)
    }

    private val captureRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.elapsedRealtime()
            if (inFlight && now - inFlightSince > InFlightTimeoutMs) {
                inFlight = false
            }
            try {
                if (!inFlight) captureOnce()
            } catch (throwable: Throwable) {
                inFlight = false
                ModuleLog.error("captureOnce failed", throwable)
            }
            if (running) handler.postDelayed(this, FrameIntervalMs)
        }
    }

    private fun captureOnce() {
        val window = activity.window ?: return
        val decor = window.decorView
        if (!decor.isAttachedToWindow || decor.width <= 0 || decor.height <= 0) return
        if (!overlayView.isAttachedToWindow || overlayView.visibility != View.VISIBLE) return
        if (overlayView.width <= 0 || overlayView.height <= 0) return

        val overlayLocation = IntArray(2)
        overlayView.getLocationOnScreen(overlayLocation)
        val decorLocation = IntArray(2)
        decor.getLocationOnScreen(decorLocation)

        val left = (overlayLocation[0] - decorLocation[0]).coerceIn(0, decor.width - 1)
        val top = (overlayLocation[1] - decorLocation[1]).coerceIn(0, decor.height - 1)
        val right = (left + overlayView.width).coerceIn(left + 1, decor.width)
        val bottom = (top + overlayView.height).coerceIn(top + 1, decor.height)
        val source = Rect(left, top, right, bottom)

        // Fallback: visible TextureView / SurfaceView, then the window strip.
        val texture = findVideoTexture(decor)
        if (texture != null && captureTexture(texture, source, decor)) return
        val surface = findVideoSurface(decor)
        if (surface != null) {
            if (captureSurface(surface, source, decor)) return
        }
        captureWindow(window, source)
    }

    private fun capturePlayerSurface(
        surface: android.view.Surface,
        playerView: View,
        source: Rect,
        decor: View,
    ): Boolean {
        val viewLocation = IntArray(2)
        playerView.getLocationOnScreen(viewLocation)
        val decorLocation = IntArray(2)
        decor.getLocationOnScreen(decorLocation)
        val viewRect = Rect(
            viewLocation[0] - decorLocation[0],
            viewLocation[1] - decorLocation[1],
            viewLocation[0] - decorLocation[0] + playerView.width,
            viewLocation[1] - decorLocation[1] + playerView.height,
        )
        val intersection = Rect(source)
        if (!intersection.intersect(viewRect)) return false
        if (intersection.width() < source.width() / 4) return false

        val scale = CaptureScale
        val destWidth = (intersection.width() / scale).coerceAtLeast(1)
        val destHeight = (intersection.height() / scale).coerceAtLeast(1)
        val video = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
        val localSource = Rect(
            intersection.left - viewRect.left,
            intersection.top - viewRect.top,
            intersection.right - viewRect.left,
            intersection.bottom - viewRect.top,
        )
        inFlight = true
        inFlightSince = SystemClock.elapsedRealtime()
        try {
            PixelCopy.request(
                surface,
                localSource,
                video,
                { result ->
                    inFlight = false
                    if (!running) {
                        video.recycle()
                        return@request
                    }
                    if (result != PixelCopy.SUCCESS || !isOpaqueEnough(video)) {
                        video.recycle()
                        dropIfStale()
                        captureWindow(activity.window, source)
                        return@request
                    }
                    val strip = Bitmap.createBitmap(
                        (source.width() / scale).coerceAtLeast(1),
                        (source.height() / scale).coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    val canvas = Canvas(strip)
                    canvas.drawColor(android.graphics.Color.BLACK)
                    canvas.drawBitmap(
                        video,
                        ((intersection.left - source.left) / scale.toFloat()),
                        ((intersection.top - source.top) / scale.toFloat()),
                        Paint(Paint.FILTER_BITMAP_FLAG),
                    )
                    video.recycle()
                    failures = 0
                    lastValidFrameAt = SystemClock.elapsedRealtime()
                    noteFrameCaptured()
                    onFrame(frost(strip).asImageBitmap())
                },
                handler,
            )
        } catch (throwable: Throwable) {
            inFlight = false
            video.recycle()
            ModuleLog.error("PixelCopy player surface threw", throwable)
            return false
        }
        return true
    }

    private fun captureTexture(
        texture: TextureView,
        source: Rect,
        decor: View,
    ): Boolean {
        if (!texture.isAvailable || texture.width <= 0 || texture.height <= 0) return false
        val textureLocation = IntArray(2)
        texture.getLocationOnScreen(textureLocation)
        val decorLocation = IntArray(2)
        decor.getLocationOnScreen(decorLocation)
        val textureRect = Rect(
            textureLocation[0] - decorLocation[0],
            textureLocation[1] - decorLocation[1],
            textureLocation[0] - decorLocation[0] + texture.width,
            textureLocation[1] - decorLocation[1] + texture.height,
        )
        val intersection = Rect(source)
        if (!intersection.intersect(textureRect)) return false
        if (intersection.width() < source.width() / 4) return false

        return runCatching {
            val scale = CaptureScale * 2
            val fullWidth = (texture.width / scale).coerceAtLeast(1)
            val fullHeight = (texture.height / scale).coerceAtLeast(1)
            val full = texture.getBitmap(fullWidth, fullHeight) ?: return false
            val strip = Bitmap.createBitmap(
                (source.width() / CaptureScale).coerceAtLeast(1),
                (source.height() / CaptureScale).coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(strip)
            canvas.drawColor(android.graphics.Color.BLACK)
            val sourceRect = Rect(
                (intersection.left - textureRect.left) / scale,
                (intersection.top - textureRect.top) / scale,
                (intersection.right - textureRect.left) / scale,
                (intersection.bottom - textureRect.top) / scale,
            )
            val destinationRect = Rect(
                (intersection.left - source.left) / CaptureScale,
                (intersection.top - source.top) / CaptureScale,
                (intersection.right - source.left) / CaptureScale,
                (intersection.bottom - source.top) / CaptureScale,
            )
            canvas.drawBitmap(
                full,
                sourceRect,
                destinationRect,
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            full.recycle()
            if (!isOpaqueEnough(strip)) {
                strip.recycle()
                return false
            }
            lastValidFrameAt = SystemClock.elapsedRealtime()
            noteFrameCaptured()
            onFrame(frost(strip).asImageBitmap())
            true
        }.getOrDefault(false)
    }

    private fun captureWindow(window: android.view.Window, source: Rect) {
        val scale = if (smallCaptureSupported) CaptureScale else 1
        val destinationWidth = (source.width() / scale).coerceAtLeast(1)
        val destinationHeight = (source.height() / scale).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(
            destinationWidth,
            destinationHeight,
            Bitmap.Config.ARGB_8888,
        )
        inFlight = true
        inFlightSince = SystemClock.elapsedRealtime()
        try {
            PixelCopy.request(
                window,
                source,
                bitmap,
                { result ->
                    inFlight = false
                    if (!running) return@request
                    if (result == PixelCopy.SUCCESS) {
                        failures = 0
                        if (isOpaqueEnough(bitmap)) {
                            lastValidFrameAt = SystemClock.elapsedRealtime()
                            noteFrameCaptured()
                            onFrame(frost(bitmap).asImageBitmap())
                        } else {
                            bitmap.recycle()
                            dropIfStale()
                        }
                    } else {
                        handleFailure(result, bitmap, scale)
                    }
                },
                handler,
            )
        } catch (throwable: Throwable) {
            inFlight = false
            ModuleLog.error("PixelCopy window request threw", throwable)
            stop()
        }
    }

    private fun captureSurface(
        surface: SurfaceView,
        source: Rect,
        decor: View,
    ): Boolean {
        val window = activity.window ?: return false
        val surfaceLocation = IntArray(2)
        surface.getLocationOnScreen(surfaceLocation)
        val decorLocation = IntArray(2)
        decor.getLocationOnScreen(decorLocation)
        val surfaceRect = Rect(
            surfaceLocation[0] - decorLocation[0],
            surfaceLocation[1] - decorLocation[1],
            surfaceLocation[0] - decorLocation[0] + surface.width,
            surfaceLocation[1] - decorLocation[1] + surface.height,
        )
        val intersection = Rect(source)
        if (!intersection.intersect(surfaceRect)) return false
        if (intersection.width() < source.width() / 4) return false

        val scale = CaptureScale
        val destWidth = (intersection.width() / scale).coerceAtLeast(1)
        val destHeight = (intersection.height() / scale).coerceAtLeast(1)
        val localSource = Rect(
            intersection.left - surfaceRect.left,
            intersection.top - surfaceRect.top,
            intersection.right - surfaceRect.left,
            intersection.bottom - surfaceRect.top,
        )
        inFlight = true
        inFlightSince = SystemClock.elapsedRealtime()

        fun finish(video: Bitmap) {
            if (!running) {
                video.recycle()
                inFlight = false
                return
            }
            val strip = Bitmap.createBitmap(
                (source.width() / scale).coerceAtLeast(1),
                (source.height() / scale).coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(strip)
            canvas.drawColor(android.graphics.Color.BLACK)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(
                video,
                ((intersection.left - source.left) / scale.toFloat()),
                ((intersection.top - source.top) / scale.toFloat()),
                paint,
            )
            video.recycle()
            failures = 0
            inFlight = false
            lastValidFrameAt = SystemClock.elapsedRealtime()
            noteFrameCaptured()
            onFrame(frost(strip).asImageBitmap())
        }

        fun attempt(index: Int) {
            if (!running) {
                inFlight = false
                return
            }
            if (index > 1) {
                inFlight = false
                dropIfStale()
                captureWindow(window, source)
                return
            }
            val video = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
            val listener = PixelCopy.OnPixelCopyFinishedListener { result ->
                if (result == PixelCopy.SUCCESS && isOpaqueEnough(video)) {
                    finish(video)
                } else {
                    video.recycle()
                    attempt(index + 1)
                }
            }
            try {
                when (index) {
                    0 -> PixelCopy.request(surface, localSource, video, listener, handler)
                    else -> PixelCopy.request(
                        surface.holder.surface,
                        localSource,
                        video,
                        listener,
                        handler,
                    )
                }
            } catch (throwable: Throwable) {
                ModuleLog.error("PixelCopy surface attempt $index threw", throwable)
                video.recycle()
                attempt(index + 1)
            }
        }
        attempt(0)
        return true
    }

    private fun noteFrameCaptured() {
        capturedFrames += 1
        val now = SystemClock.elapsedRealtime()
        if (fpsWindowStartedAt == 0L) fpsWindowStartedAt = now
        if (now - fpsWindowStartedAt >= 2_000L) {
            ModuleLog.info("glass capture fps=${capturedFrames * 1000L / (now - fpsWindowStartedAt)}")
            capturedFrames = 0
            fpsWindowStartedAt = now
        }
    }

    private fun dropIfStale() {
        if (SystemClock.elapsedRealtime() - lastValidFrameAt > StaleTimeoutMs) {
            onFrame(null)
        }
    }

    private fun handleFailure(result: Int, bitmap: Bitmap, scale: Int) {
        if (smallCaptureSupported && scale > 1) {
            // Some ROMs only support 1:1 readback. Retry full-size and
            // downscale on our side.
            smallCaptureSupported = false
        }
        bitmap.recycle()
        failures += 1
        if (failures == 1) {
            ModuleLog.error("PixelCopy failed code=$result")
        }
        if (failures >= 3) stop()
    }

    private fun findVideoSurface(decor: View): SurfaceView? {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSurfaceScanAt > SurfaceScanIntervalMs) {
            lastSurfaceScanAt = now
            cachedSurface = collectLargestSurface(decor)
            cachedTexture = collectLargestTexture(decor)
        }
        return cachedSurface?.takeIf {
            it.isAttachedToWindow && it.isShown && it.width > 0 && it.height > 0
        }
    }

    private fun findVideoTexture(decor: View): TextureView? {
        findVideoSurface(decor)
        return cachedTexture?.takeIf {
            it.isAttachedToWindow && it.isShown && it.isAvailable &&
                it.width > 0 && it.height > 0
        }
    }

    private fun collectLargestTexture(root: View): TextureView? {
        var best: TextureView? = null
        var bestArea = 0
        fun visit(view: View) {
            if (view is TextureView && view.isShown && view.width > 0 && view.height > 0) {
                val area = view.width * view.height
                if (area > bestArea) {
                    best = view
                    bestArea = area
                }
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    visit(view.getChildAt(index))
                }
            }
        }
        visit(root)
        return best
    }

    private fun collectLargestSurface(root: View): SurfaceView? {
        var best: SurfaceView? = null
        var bestArea = 0
        fun visit(view: View) {
            if (view is SurfaceView && view.isShown && view.width > 0 && view.height > 0) {
                val area = view.width * view.height
                if (area > bestArea) {
                    best = view
                    bestArea = area
                }
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    visit(view.getChildAt(index))
                }
            }
        }
        visit(root)
        return best
    }

    private fun isOpaqueEnough(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return false
        val stepX = (width / 16).coerceAtLeast(1)
        val stepY = (height / 8).coerceAtLeast(1)
        var samples = 0
        var transparent = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val alpha = (bitmap.getPixel(x, y) ushr 24) and 0xFF
                if (alpha < 250) transparent++
                samples++
                x += stepX
            }
            y += stepY
        }
        return samples > 0 && transparent * 4 < samples
    }

    private fun frost(source: Bitmap): Bitmap {
        val level = frostLevelProvider().coerceIn(0, 32)
        if (level == 0) return source
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        // 1/8 capture is only ~135x16 px, so even two passes are negligible.
        val radius = (level / 8).coerceIn(1, 4)
        val passes = if (level <= 10) 1 else 2
        repeat(passes) {
            boxBlur(pixels, width, height, radius)
        }
        source.setPixels(pixels, 0, width, 0, 0, width, height)
        return source
    }

    private fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int) {
        val horizontal = IntArray(pixels.size)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                val from = (x - radius).coerceAtLeast(0)
                val to = (x + radius).coerceAtMost(width - 1)
                for (sampleX in from..to) {
                    val color = pixels[row + sampleX]
                    a += color ushr 24 and 0xFF
                    r += color ushr 16 and 0xFF
                    g += color ushr 8 and 0xFF
                    b += color and 0xFF
                    count++
                }
                horizontal[row + x] =
                    (a / count shl 24) or (r / count shl 16) or (g / count shl 8) or (b / count)
            }
        }
        for (x in 0 until width) {
            for (y in 0 until height) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                val from = (y - radius).coerceAtLeast(0)
                val to = (y + radius).coerceAtMost(height - 1)
                for (sampleY in from..to) {
                    val color = horizontal[sampleY * width + x]
                    a += color ushr 24 and 0xFF
                    r += color ushr 16 and 0xFF
                    g += color ushr 8 and 0xFF
                    b += color and 0xFF
                    count++
                }
                pixels[y * width + x] =
                    (a / count shl 24) or (r / count shl 16) or (g / count shl 8) or (b / count)
            }
        }
    }

    private companion object {
        const val FrameIntervalMs = 33L // 30 fps
        const val CaptureScale = 4
        const val SurfaceScanIntervalMs = 1_000L
        const val StaleTimeoutMs = 200L
        const val InFlightTimeoutMs = 200L
    }
}
