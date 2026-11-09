package com.autumn.douyin.liquidglass.hook

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.autumn.douyin.liquidglass.ModuleLog
import com.autumn.douyin.liquidglass.nativebar.NativeBottomBar
import com.autumn.douyin.liquidglass.nativebar.NativeBottomBarLocator
import com.autumn.douyin.liquidglass.nativebar.NativeBottomBarStateMonitor
import com.autumn.douyin.liquidglass.nativebar.VideoViewRegistry
import com.autumn.douyin.liquidglass.settings.ModuleSettings
import com.autumn.douyin.liquidglass.settings.ModuleSettingsBridge
import com.autumn.douyin.liquidglass.ui.OverlayController
import com.autumn.douyin.liquidglass.ui.SolidBarOverlayView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

class BarHook : IXposedHookLoadPackage {
    private val installedActivities =
        Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())
    private val installedOverlays =
        Collections.synchronizedMap(WeakHashMap<Activity, SolidBarOverlayView>())
    private val installedStateMonitors =
        Collections.synchronizedMap(WeakHashMap<Activity, NativeBottomBarStateMonitor>())
    private val installedControllers =
        Collections.synchronizedMap(WeakHashMap<Activity, OverlayController>())
    private val installedBars =
        Collections.synchronizedMap(WeakHashMap<Activity, NativeBottomBar>())

    @Volatile
    private var applicationContext: Context? = null
    private var settingsReceiverRegistered = false

    @Volatile
    private var featureHooksInstalled = false

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != DOUYIN_PACKAGE || lpparam.processName != DOUYIN_PACKAGE) return

        installHook("Application.attach") {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "attach",
                Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.args[0] as? Context ?: return
                        applicationContext = context.applicationContext ?: context
                        ModuleSettingsBridge.start(context, ::onSettingsChanged)
                        onSettingsChanged(ModuleSettingsBridge.current)
                    }
                },
            )
        }

        installHook("Instrumentation.callActivityOnCreate") {
            XposedHelpers.findAndHookMethod(
                Instrumentation::class.java,
                "callActivityOnCreate",
                Activity::class.java,
                Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.args[0] as? Activity ?: return
                        if (!isMainDouyinActivity(activity)) return
                        installOverlay(activity)
                    }
                },
            )
        }

        installHook("Activity.onResume") {
            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as? Activity ?: return
                        if (!isMainDouyinActivity(activity)) return
                        ModuleSettingsBridge.refreshAsync()
                        installOverlay(activity)
                    }
                },
            )
        }

        installHook("Activity.onDestroy") {
            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "onDestroy",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as? Activity ?: return
                        if (!isMainDouyinActivity(activity)) return
                        removeOverlay(activity)
                    }
                },
            )
        }

        installHook("SplashActivity.onResume") {
            runCatching {
                val splashClass = XposedHelpers.findClass(
                    "com.ss.android.ugc.aweme.splash.SplashActivity",
                    lpparam.classLoader,
                )
                XposedBridge.hookAllMethods(
                    splashClass,
                    "onResume",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val activity = param.thisObject as? Activity ?: return
                            installOverlay(activity)
                        }
                    },
                )
            }
        }
    }

    private inline fun installHook(
        name: String,
        block: () -> Unit,
    ) {
        runCatching(block)
            .onSuccess { ModuleLog.info("hook installed: $name") }
            .onFailure { ModuleLog.error("hook failed: $name", it) }
    }

    private fun onSettingsChanged(settings: ModuleSettings) {
        if (settings.enabled) installFeatureHooksOnce()
        synchronized(installedOverlays) {
            installedOverlays.values.forEach { overlay -> overlay.updateSettings(settings) }
        }
    }

    private fun installFeatureHooksOnce() {
        if (featureHooksInstalled) return
        val classLoader = applicationContext?.classLoader ?: return
        featureHooksInstalled = true
        TransparentBarIntegration.install(classLoader)
    }

    private fun installVideoViewHooks(classLoader: ClassLoader) {
        runCatching {
            val playerView = XposedHelpers.findClass(
                "com.ss.android.ugc.aweme.feed.ui.FeedVideoPlayerView",
                classLoader,
            )
            XposedBridge.hookAllMethods(
                playerView,
                "getVideoView",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        VideoViewRegistry.updateView(param.result as? View)
                    }
                },
            )
            XposedBridge.hookAllMethods(
                playerView,
                "getSurface",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        VideoViewRegistry.updateSurface(param.result as? android.view.Surface)
                    }
                },
            )
            ModuleLog.info("feed video view hooks installed")
        }.onFailure {
            ModuleLog.error("failed to install feed video view hooks", it)
        }
    }

    private fun isMainDouyinActivity(activity: Activity): Boolean {
        val name = activity.javaClass.name
        return name == MAIN_ACTIVITY ||
            name == "com.ss.android.ugc.aweme.main.MainActivity" ||
            name.endsWith(".splash.SplashActivity")
    }

    private fun makeSystemNavigationBarTransparent(activity: Activity) {
        runCatching {
            val window = activity.window
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            window.setNavigationBarDividerColor(android.graphics.Color.TRANSPARENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            val controller = androidx.core.view.WindowCompat.getInsetsController(
                window,
                window.decorView,
            )
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }.onFailure {
            ModuleLog.error("transparent system navigation bar failed", it)
        }
    }

    private fun registerSettingsReceiver(context: Context) {
        if (settingsReceiverRegistered) return
        settingsReceiverRegistered = true
        runCatching {
            context.registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context?, intent: Intent?) {
                        val raw = intent?.getStringExtra("settings") ?: return
                        runCatching {
                            val directory = java.io.File(
                                context.getExternalFilesDir(null),
                                "m3-bar",
                            )
                            directory.mkdirs()
                            java.io.File(directory, "settings.properties")
                                .writeText(raw, Charsets.UTF_8)
                            ModuleLog.info("settings applied via broadcast")
                        }.onFailure {
                            ModuleLog.error("broadcast settings write failed", it)
                        }
                    }
                },
                IntentFilter(SETTINGS_ACTION),
                Context.RECEIVER_EXPORTED,
            )
        }.onFailure {
            ModuleLog.error("settings receiver register failed", it)
        }
    }

    private fun installOverlay(activity: Activity) {
        val application = activity.applicationContext ?: return
        applicationContext = application
        registerSettingsReceiver(application)
        makeSystemNavigationBarTransparent(activity)
        ModuleSettingsBridge.start(application, ::onSettingsChanged)
        if (!ModuleSettingsBridge.current.enabled) return

        // Only one bar window per process. Keeping both the splash and main
        // activity overlays alive doubled the 60fps capture workload.
        val staleOverlays = synchronized(installedOverlays) {
            installedOverlays.keys.filter { it !== activity }
        }
        staleOverlays.forEach(::removeOverlay)

        synchronized(installedActivities) {
            if (!installedActivities.add(activity)) return
        }

        val content = activity.findViewById<ViewGroup>(android.R.id.content)
            ?: activity.window.decorView as? ViewGroup
            ?: return

        content.post {
            if (tryInstallOverlay(activity, content)) return@post
            content.postDelayed({
                if (!tryInstallOverlay(activity, content)) {
                    synchronized(installedActivities) { installedActivities.remove(activity) }
                    ModuleLog.error("bottom bar not found after retry")
                }
            }, 700)
        }
    }

    private fun tryInstallOverlay(activity: Activity, content: ViewGroup): Boolean {
        return runCatching {
            val nativeBar = NativeBottomBarLocator.find(content) ?: return false
            ModuleLog.info("native bar found: ${nativeBar.describe()}")

            val controller = OverlayController(nativeBar)
            val initialSettings = ModuleSettingsBridge.current
            val overlay = SolidBarOverlayView(
                context = activity,
                controller = controller,
                initialSettings = initialSettings,
            )
            val stateMonitor = NativeBottomBarStateMonitor(nativeBar) { present, _ ->
                overlay.setNativeBarPresent(present)
            }

            installedOverlays.putIfAbsent(activity, overlay)
            installedStateMonitors.putIfAbsent(activity, stateMonitor)
            installedControllers.putIfAbsent(activity, controller)
            installedBars.putIfAbsent(activity, nativeBar)

            nativeBar.suppressOriginalUi()
            controller.start()
            overlay.startLifecycle()
            activity.windowManager.addView(
                overlay,
                createOverlayLayoutParams(activity, initialSettings),
            )
            stateMonitor.start()
            ModuleLog.info("solid M3 bar overlay installed")
            true
        }.getOrElse { throwable ->
            ModuleLog.error("failed to install solid bar", throwable)
            false
        }
    }

    private fun removeOverlay(activity: Activity) {
        installedStateMonitors.remove(activity)?.stop()
        installedControllers.remove(activity)?.stop()
        installedBars.remove(activity)?.restoreOriginalUi()
        val overlay = installedOverlays.remove(activity) ?: return
        runCatching { activity.windowManager.removeView(overlay) }
            .onFailure { ModuleLog.error("failed to remove overlay", it) }
        synchronized(installedActivities) { installedActivities.remove(activity) }
    }

    private fun createOverlayLayoutParams(
        activity: Activity,
        settings: ModuleSettings,
    ): WindowManager.LayoutParams {
        val heightPx = (settings.windowHeightDp * activity.resources.displayMetrics.density)
            .roundToInt()
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0
            setTitle("DouyinM3Bar")
            token = activity.window.decorView.windowToken
            windowAnimations = 0
        }
    }

    private companion object {
        const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"
        const val MAIN_ACTIVITY = "com.ss.android.ugc.aweme.splash.SplashActivity"
        const val SETTINGS_ACTION = "com.autumn.douyin.liquidglass.SETTINGS_CHANGED"
    }
}
