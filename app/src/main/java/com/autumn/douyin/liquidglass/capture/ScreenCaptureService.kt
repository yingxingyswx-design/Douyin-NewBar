package com.autumn.douyin.liquidglass.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.autumn.douyin.liquidglass.ModuleLog
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference

/**
 * Foreground MediaProjection service. Captures a low-resolution full-screen
 * mirror ~30fps and serves the newest RGBA frame on 127.0.0.1:47620. The hooked
 * Douyin process connects from ProjectionFrameSource and crops the bar strip.
 *
 * All processing is local; frames are never written to disk or uploaded.
 */
class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var server: ServerSocket? = null
    private var serverThread: Thread? = null

    private val latestFrame = AtomicReference<Frame?>(null)
    @Volatile
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                0
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) startProjection(resultCode, data)
        return START_STICKY
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        runCatching {
            val manager = getSystemService(MediaProjectionManager::class.java)
            projection = manager.getMediaProjection(resultCode, data)
            projection?.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopSelf()
                    }
                },
                Handler(Looper.getMainLooper()),
            )
            val metrics = resources.displayMetrics
            val width = CAPTURE_WIDTH
            val height = (width * metrics.heightPixels / metrics.widthPixels).coerceAtLeast(1)
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader
            reader.setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                runCatching {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val rowStride = plane.rowStride
                    val pixelStride = plane.pixelStride
                    val bytes = ByteArray(width * height * 4)
                    if (rowStride == width * pixelStride) {
                        buffer.get(bytes)
                    } else {
                        val row = ByteArray(width * pixelStride)
                        for (y in 0 until height) {
                            buffer.position(y * rowStride)
                            buffer.get(row, 0, row.size)
                            System.arraycopy(row, 0, bytes, y * width * pixelStride, row.size)
                        }
                    }
                    latestFrame.set(Frame(width, height, bytes))
                    sharedFrame = Frame(width, height, bytes)
                }
                image.close()
            }, null)
            virtualDisplay = projection?.createVirtualDisplay(
                "m3-bar-capture",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                null,
            )
            running = true
            startServer()
            ModuleLog.info("screen capture projection started ${width}x$height")
        }.onFailure {
            ModuleLog.error("screen capture projection failed", it)
            stopSelf()
        }
    }

    private fun startServer() {
        serverThread = Thread({
            runCatching {
                val socket = ServerSocket(PORT, 1, InetAddress.getByName("127.0.0.1"))
                server = socket
                while (running) {
                    val client = socket.accept()
                    Thread({
                        runCatching {
                            val output = DataOutputStream(client.getOutputStream())
                            while (running) {
                                val frame = latestFrame.get()
                                if (frame != null) {
                                    output.writeInt(frame.width)
                                    output.writeInt(frame.height)
                                    output.writeInt(frame.bytes.size)
                                    output.write(frame.bytes)
                                    output.flush()
                                }
                                Thread.sleep(33)
                            }
                        }.onFailure { ModuleLog.error("capture client ended", it) }
                        runCatching { client.close() }
                    }, "m3-capture-client").apply { isDaemon = true }.start()
                }
            }.onFailure { ModuleLog.error("capture server failed", it) }
        }, "m3-capture-server").apply { isDaemon = true }
        serverThread?.start()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "毛玻璃录屏抓帧",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("毛玻璃录屏抓帧")
            .setContentText("正在本机采集底栏区域，用于视频页实时毛玻璃")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        running = false
        serverThread?.interrupt()
        runCatching { server?.close() }
        runCatching { virtualDisplay?.release() }
        runCatching { imageReader?.close() }
        runCatching { projection?.stop() }
        super.onDestroy()
    }

    data class Frame(val width: Int, val height: Int, val bytes: ByteArray)

    companion object {
        const val PORT = 47620
        @Volatile
        var sharedFrame: Frame? = null
            private set
        private const val CAPTURE_WIDTH = 180
        private const val CHANNEL_ID = "m3-bar-capture"
        private const val NOTIFICATION_ID = 47620
        private const val ACTION_STOP = "com.autumn.douyin.liquidglass.STOP_CAPTURE"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ScreenCaptureService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
