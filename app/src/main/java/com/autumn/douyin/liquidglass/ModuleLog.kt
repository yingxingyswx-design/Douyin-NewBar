package com.autumn.douyin.liquidglass

import android.util.Log

/** Lightweight logcat wrapper; the bar no longer writes diagnostic files. */
object ModuleLog {
    private const val Tag = "DouyinM3Bar"

    val isEnabled: Boolean
        get() = true

    fun info(message: String) {
        runCatching { Log.i(Tag, message) }
    }

    fun error(message: String, throwable: Throwable? = null) {
        runCatching { Log.e(Tag, message, throwable) }
    }

    inline fun info(message: () -> String) = info(message())

    inline fun error(throwable: Throwable? = null, message: () -> String) =
        error(message(), throwable)
}
