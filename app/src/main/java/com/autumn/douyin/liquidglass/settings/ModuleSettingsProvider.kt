package com.autumn.douyin.liquidglass.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.autumn.douyin.liquidglass.ModuleLog
import com.autumn.douyin.liquidglass.capture.ScreenCaptureService
import java.io.DataOutputStream
import java.io.FileNotFoundException

class ModuleSettingsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        requireAuthorizedCaller()
        return ModuleSettingsStore.cursor(
            context?.let(ModuleSettingsStore::read) ?: ModuleSettings.Default
        )
    }

    override fun getType(uri: Uri): String =
        "vnd.android.cursor.dir/vnd.com.autumn.douyin.liquidglass.settings"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        ModuleLog.info("openFile caller=$callingPackage uri=$uri")
        try {
            requireAuthorizedCaller()
            require(uri.lastPathSegment == "frames") { "unknown stream $uri" }
        } catch (throwable: Throwable) {
            ModuleLog.error("openFile rejected caller=$callingPackage uri=$uri", throwable)
            throw FileNotFoundException(throwable.message)
        }
        val pipe = ParcelFileDescriptor.createPipe()
        Thread({
            runCatching {
                val output = DataOutputStream(
                    ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
                )
                while (true) {
                    val frame = ScreenCaptureService.sharedFrame
                    if (frame != null) {
                        output.writeInt(frame.width)
                        output.writeInt(frame.height)
                        output.writeInt(frame.bytes.size)
                        output.write(frame.bytes)
                        output.flush()
                    }
                    Thread.sleep(33)
                }
            }
        }, "m3-frame-pipe").apply { isDaemon = true }.start()
        return pipe[0]
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        val pipe = openFile(uri, mode)
        return AssetFileDescriptor(pipe, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        requireAuthorizedCaller()
        return null
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        requireAuthorizedCaller()
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        requireAuthorizedCaller()
        return 0
    }

    private fun requireAuthorizedCaller() {
        val caller = callingPackage
        check(
            caller == null ||
                caller == "com.ss.android.ugc.aweme" ||
                caller == "com.autumn.douyin.liquidglass"
        ) {
            "settings are not available to $caller"
        }
    }
}
