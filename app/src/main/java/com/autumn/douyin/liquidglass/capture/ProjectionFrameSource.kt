package com.autumn.douyin.liquidglass.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.autumn.douyin.liquidglass.ModuleLog
import java.io.DataInputStream

/**
 * Reads the low-resolution screen mirror from the module's ContentProvider pipe
 * (ParcelFileDescriptor), avoiding HyperOS localhost socket isolation. No frame
 * is ever persisted.
 */
class ProjectionFrameSource(
    private val context: Context,
    private val barRegion: () -> Rect,
    private val screenSize: () -> Rect,
    private val onFrame: (Bitmap?) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var worker: Thread? = null
    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        worker = Thread(::loop, "m3-projection-client").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        worker?.interrupt()
        worker = null
        mainHandler.post { onFrame(null) }
    }

    private fun loop() {
        while (running) {
            val input = runCatching {
                context.contentResolver.openInputStream(FRAME_URI)
            }.onFailure {
                ModuleLog.info("projection pipe open failed: ${it.javaClass.simpleName}")
            }.getOrNull()
            if (input == null) {
                sleep(800)
                continue
            }
            ModuleLog.info("projection pipe connected")
            runCatching {
                DataInputStream(input).use { stream ->
                    while (running) {
                        val width = stream.readInt()
                        val height = stream.readInt()
                        val count = stream.readInt()
                        if (width <= 0 || height <= 0 || count != width * height * 4) break
                        val bytes = ByteArray(count)
                        stream.readFully(bytes)
                        val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        frame.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes))
                        val crop = cropBar(frame)
                        frame.recycle()
                        if (crop != null) mainHandler.post { onFrame(crop) }
                    }
                }
            }.onFailure {
                ModuleLog.info("projection pipe disconnected: ${it.message}")
            }
            sleep(500)
        }
    }

    private fun cropBar(frame: Bitmap): Bitmap? {
        val screen = screenSize()
        val bar = barRegion()
        if (screen.height() <= 0 || bar.height() <= 0) return null
        val scale = frame.height.toFloat() / screen.height().toFloat()
        val top = (bar.top * scale).toInt().coerceIn(0, frame.height - 1)
        val bottom = (bar.bottom * scale).toInt().coerceIn(top + 1, frame.height)
        val source = Bitmap.createBitmap(frame, 0, top, frame.width, bottom - top)
        val targetWidth = 270
        val targetHeight = (source.height * (targetWidth.toFloat() / source.width))
            .toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        source.recycle()
        return scaled
    }

    private fun sleep(millis: Long) {
        runCatching { Thread.sleep(millis) }
    }

    private companion object {
        val FRAME_URI: Uri = Uri.parse(
            "content://com.autumn.douyin.liquidglass.settings/frames"
        )
    }
}
