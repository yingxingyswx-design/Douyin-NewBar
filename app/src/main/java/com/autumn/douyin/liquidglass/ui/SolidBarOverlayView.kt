package com.autumn.douyin.liquidglass.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.autumn.douyin.liquidglass.ModuleLog
import com.autumn.douyin.liquidglass.capture.ProjectionFrameSource
import com.autumn.douyin.liquidglass.root.RootFrameProvider
import com.autumn.douyin.liquidglass.settings.ModuleSettings
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState
import kotlin.math.roundToInt

/**
 * Thin window that hosts only the navigation bar.
 *
 * Solid mode draws a pure Compose color bar. Liquid-glass mode additionally runs a
 * PixelCopy capture of the activity strip behind the window, feeds it into a
 * `liquefiable` layer, and lets the `liquid` RuntimeShader refract it. No root
 * capture daemon and no SurfaceControl pipeline are involved.
 */
class SolidBarOverlayView(
    context: Context,
    private val controller: OverlayController,
    initialSettings: ModuleSettings,
) : FrameLayout(context), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleOwner = OverlayLifecycleOwner()
    private val savedStateController = SavedStateRegistryController.create(this)
    private val settingsState = mutableStateOf(initialSettings)
    private val backdropFrame = mutableStateOf<ImageBitmap?>(null)
    private val projectionFrame = mutableStateOf<ImageBitmap?>(null)
    private var backdropCapture: WindowBackdropCapture? = null
    private var rootFrameProvider: RootFrameProvider? = null
    private var projectionSource: ProjectionFrameSource? = null
    private var lifecycleStarted = false
    private var desiredPresent = true
    private var presenceAnimator: ValueAnimator? = null

    private val showInterpolator = AccelerateDecelerateInterpolator()
    private val hideInterpolator = AccelerateDecelerateInterpolator()

    override val viewModelStore: ViewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleOwner.lifecycle

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    init {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, windowHeightPx(initialSettings))
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

        setViewTreeLifecycleOwner(lifecycleOwner)
        setViewTreeViewModelStoreOwner(this)
        setViewTreeSavedStateRegistryOwner(this)
        savedStateController.performAttach()

        val composeView = ComposeView(context)
        composeView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        composeView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeViewModelStoreOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)
        composeView.setContent {
            val settings = settingsState.value
            val frame = projectionFrame.value ?: backdropFrame.value
            M3BarSurface(
                settings = settings,
                selectedIndex = controller.selectedTab,
                onTabSelected = controller::clickTab,
                onPlusClick = controller::clickPlus,
                onPlusLongClick = controller::longClickPlus,
                messageBadgeCount = controller.messageBadgeCount,
                liquidState = null,
                hasBackdrop = frame != null,
                backdrop = frame,
                glassTintOnly = settings.liquidGlass && frame == null,
            )
        }
        addView(composeView)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncBackdropCapture()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        syncBackdropCapture()
    }

    fun updateSettings(settings: ModuleSettings) {
        val previous = settingsState.value
        if (settings == previous) return
        settingsState.value = settings
        ModuleLog.info(
            "overlay settings revision=${settings.revision} height=${settings.barHeightDp} " +
                "gap=${settings.bottomGapDp} liquid=${settings.liquidGlass} " +
                "enabled=${settings.enabled} previousHeight=${previous.barHeightDp}",
        )
        if (previous.windowHeightDp != settings.windowHeightDp) {
            resizeWindow(settings)
        }
        if (previous.liquidGlass != settings.liquidGlass ||
            previous.realtimeGlass != settings.realtimeGlass ||
            previous.rootCapture != settings.rootCapture
        ) {
            syncBackdropCapture()
        }
        if (previous.windowHeightDp != settings.windowHeightDp) {
            rootFrameProvider?.let(::updateRootRegion)
        }
    }

    private fun syncBackdropCapture() {
        val activityFinishing = (context as? Activity)?.isFinishing == true
        val captureAllowed = settingsState.value.liquidGlass &&
            isAttachedToWindow &&
            visibility == View.VISIBLE &&
            windowVisibility == View.VISIBLE &&
            !activityFinishing
        if (!captureAllowed) {
            stopBackdropCapture()
            stopProjectionSource()
            return
        }
        if (settingsState.value.rootCapture) {
            backdropCapture?.stop()
            backdropCapture = null
            startRootCapture()
            return
        }
        stopRootCapture()
        if (!settingsState.value.realtimeGlass) {
            backdropCapture?.stop()
            backdropCapture = null
            return
        }
        if (backdropCapture != null) return
        val activity = context as? Activity ?: return
        ModuleLog.info(
            "starting glass backdrop capture attached=$isAttachedToWindow " +
                "visible=$visibility windowVisible=$windowVisibility",
        )
        backdropCapture = WindowBackdropCapture(
            activity = activity,
            overlayView = this,
            frostLevelProvider = { settingsState.value.frostDp },
        ) { frame ->
            backdropFrame.value = frame
        }.also { it.start() }
    }

    private fun startRootCapture() {
        if (rootFrameProvider != null) return
        ModuleLog.info("starting root composite capture")
        val provider = RootFrameProvider(context) { bitmap ->
            backdropFrame.value = bitmap?.asImageBitmap()
        }
        updateRootRegion(provider)
        provider.start()
        rootFrameProvider = provider
    }

    private fun updateRootRegion(provider: RootFrameProvider) {
        val display = resources.displayMetrics
        val height = windowHeightPx(settingsState.value)
        provider.updateCaptureRegion(
            Rect(
                0,
                (display.heightPixels - height).coerceAtLeast(0),
                display.widthPixels,
                display.heightPixels,
            ),
            IntOffset.Zero,
        )
    }

    private fun stopRootCapture() {
        rootFrameProvider?.stop()
        rootFrameProvider = null
    }

    private fun stopBackdropCapture() {
        backdropCapture?.stop()
        backdropCapture = null
        stopRootCapture()
    }

    private fun startProjectionSource() {
        if (projectionSource != null) return
        ModuleLog.info("starting projection frame source")
        projectionSource = ProjectionFrameSource(
            context = context,
            barRegion = {
                val display = resources.displayMetrics
                Rect(
                    0,
                    display.heightPixels - windowHeightPx(settingsState.value),
                    display.widthPixels,
                    display.heightPixels,
                )
            },
            screenSize = {
                val display = resources.displayMetrics
                Rect(0, 0, display.widthPixels, display.heightPixels)
            },
        ) { bitmap ->
            projectionFrame.value = bitmap?.asImageBitmap()
        }.also { it.start() }
    }

    private fun stopProjectionSource() {
        projectionSource?.stop()
        projectionSource = null
        projectionFrame.value = null
    }

    private fun windowHeightPx(settings: ModuleSettings): Int =
        (settings.windowHeightDp * resources.displayMetrics.density).roundToInt()

    private fun resizeWindow(settings: ModuleSettings) {
        val height = windowHeightPx(settings)
        val params = layoutParams ?: return
        if (params.height == height) return
        ModuleLog.info("resize overlay window ${params.height} -> $height")
        params.height = height
        val windowManager = context.getSystemService(WindowManager::class.java)
        runCatching {
            windowManager?.updateViewLayout(this, params)
        }.onFailure {
            ModuleLog.error("resize overlay window failed", it)
            layoutParams = params
            requestLayout()
        }
        if (visibility == View.GONE) translationY = height.toFloat()
    }

    fun setNativeBarPresent(present: Boolean) {
        if (present == desiredPresent) return
        desiredPresent = present
        if (present) {
            visibility = View.VISIBLE
            syncBackdropCapture()
            animatePresence(
                targetTranslationY = 0f,
                durationMs = ShowDurationMs,
                interpolator = showInterpolator,
            )
        } else {
            stopBackdropCapture()
            animatePresence(
                targetTranslationY = height.toFloat().takeIf { it > 0f } ?: windowHeightPx(
                    settingsState.value,
                ).toFloat(),
                durationMs = HideDurationMs,
                interpolator = hideInterpolator,
            ) {
                visibility = View.GONE
                translationY = height.toFloat()
            }
        }
    }

    private fun animatePresence(
        targetTranslationY: Float,
        durationMs: Long,
        interpolator: TimeInterpolator,
        onEnd: () -> Unit = {},
    ) {
        val previous = presenceAnimator
        presenceAnimator = null
        previous?.cancel()

        val start = translationY
        if (start == targetTranslationY) {
            onEnd()
            return
        }
        val animator = ValueAnimator.ofFloat(start, targetTranslationY)
        animator.duration = durationMs
        animator.interpolator = interpolator
        animator.addUpdateListener { animation ->
            translationY = animation.animatedValue as Float
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (presenceAnimator !== animation) return
                presenceAnimator = null
                onEnd()
            }
        })
        presenceAnimator = animator
        animator.start()
    }

    fun startLifecycle() {
        if (lifecycleStarted) return
        lifecycleStarted = true
        savedStateController.performRestore(null)
        lifecycleOwner.dispatchEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.dispatchEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.dispatchEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun destroyLifecycle() {
        if (!lifecycleStarted) return
        lifecycleStarted = false
        lifecycleOwner.dispatchEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleOwner.dispatchEvent(Lifecycle.Event.ON_STOP)
        lifecycleOwner.dispatchEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }

    override fun onDetachedFromWindow() {
        stopBackdropCapture()
        stopProjectionSource()
        presenceAnimator?.cancel()
        presenceAnimator = null
        destroyLifecycle()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val ShowDurationMs = 220L
        const val HideDurationMs = 160L
    }

    private class OverlayLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry

        fun dispatchEvent(event: Lifecycle.Event) {
            registry.handleLifecycleEvent(event)
        }
    }
}
