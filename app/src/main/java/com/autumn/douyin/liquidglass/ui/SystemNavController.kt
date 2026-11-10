package com.autumn.douyin.liquidglass.ui

import android.app.Activity
import android.graphics.Color
import android.os.Build
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Applies the optional immersive (hidden) system navigation bar. */
object SystemNavController {
    fun apply(activity: Activity, hide: Boolean) {
        runCatching {
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (hide) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                )
                window.navigationBarColor = Color.TRANSPARENT
                window.setNavigationBarDividerColor(Color.TRANSPARENT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                controller.hide(WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.navigationBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }
}
