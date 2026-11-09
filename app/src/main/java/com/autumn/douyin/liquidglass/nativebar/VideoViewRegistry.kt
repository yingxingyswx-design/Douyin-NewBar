package com.autumn.douyin.liquidglass.nativebar

import android.view.Surface
import android.view.View
import com.autumn.douyin.liquidglass.ModuleLog
import java.lang.ref.WeakReference

/**
 * Registry filled by hooks on Douyin's `FeedVideoPlayerView`.
 *
 * The feed player exposes `getVideoView()` (the actual SurfaceView/TextureView)
 * and `getSurface()` (the producer surface). Reading these directly avoids
 * picking the wrong surface from a generic view-tree walk and lets PixelCopy
 * target the playing video layer.
 */
object VideoViewRegistry {
    @Volatile
    private var viewRef: WeakReference<View>? = null

    @Volatile
    private var surfaceRef: WeakReference<Surface>? = null

    fun updateView(view: View?) {
        if (view != null) {
            viewRef = WeakReference(view)
            ModuleLog.info("registered player view: ${view.javaClass.name} ${view.width}x${view.height}")
        }
    }

    fun updateSurface(surface: Surface?) {
        if (surface != null && surface.isValid) {
            surfaceRef = WeakReference(surface)
            ModuleLog.info("registered player surface: $surface")
        }
    }

    fun currentView(): View? = viewRef?.get()?.takeIf {
        it.isAttachedToWindow && it.isShown && it.width > 0 && it.height > 0
    }

    fun currentSurface(): Surface? = surfaceRef?.get()?.takeIf { it.isValid }
}
