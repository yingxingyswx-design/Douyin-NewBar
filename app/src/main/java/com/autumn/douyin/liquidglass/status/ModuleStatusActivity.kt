package com.autumn.douyin.liquidglass.status

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.autumn.douyin.liquidglass.root.CompositeFrameDaemonLauncher
import com.autumn.douyin.liquidglass.capture.ScreenCaptureService
import com.autumn.douyin.liquidglass.settings.AppThemePreference
import com.autumn.douyin.liquidglass.settings.ModuleSettings
import com.autumn.douyin.liquidglass.settings.ModuleSettingsMirror
import com.autumn.douyin.liquidglass.settings.ModuleSettingsStore
import com.autumn.douyin.liquidglass.theme.M3BarSettingsTheme
import com.autumn.douyin.liquidglass.ui.BarSettingsScreen
import java.util.concurrent.Executors

class ModuleStatusActivity : ComponentActivity() {
    private var settings by mutableStateOf(ModuleSettings.Default)
    private var themeMode by mutableIntStateOf(AppThemePreference.System)
    private val mirrorExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "m3-bar-settings-mirror").apply { isDaemon = true }
    }
    private val rootExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "m3-bar-root-actions").apply { isDaemon = true }
    }
    private val capturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            ScreenCaptureService.start(this, result.resultCode, data)
            Toast.makeText(this, "录屏抓帧已启动", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "未授权录屏", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        settings = ModuleSettingsStore.read(this)
        themeMode = AppThemePreference.read(this)
        syncMirror(settings)
        val versionText = runCatching {
            "v${packageManager.getPackageInfo(packageName, 0).versionName}"
        }.getOrDefault("v2.2.0")

        setContent {
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                AppThemePreference.Light -> false
                AppThemePreference.Dark -> true
                else -> systemDark
            }
            LaunchedEffect(darkTheme) {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
                window.setBackgroundDrawable(
                    ColorDrawable(
                        if (darkTheme) 0xFF0A0A0C.toInt() else 0xFFF6F6F8.toInt(),
                    )
                )
            }
            M3BarSettingsTheme(darkTheme = darkTheme) {
                BarSettingsScreen(
                    versionText = versionText,
                    settings = settings,
                    themeMode = themeMode,
                    onThemeModeChange = ::updateThemeMode,
                    onSettingsChange = ::updateSettings,
                    onOpenDouyin = ::openDouyin,
                    onForceStopDouyin = ::forceStopDouyin,
                    onStartScreenCapture = ::requestScreenCapture,
                    onStopScreenCapture = ::stopScreenCapture,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        settings = ModuleSettingsStore.read(this)
    }

    private fun updateThemeMode(mode: Int) {
        themeMode = mode.coerceIn(
            AppThemePreference.System,
            AppThemePreference.Dark,
        )
        AppThemePreference.write(this, themeMode)
    }

    private fun updateSettings(next: ModuleSettings) {
        val previous = settings
        settings = ModuleSettingsStore.write(this, next)
        com.autumn.douyin.liquidglass.ModuleLog.info(
            "settings saved: enabled=${settings.enabled} height=${settings.barHeightDp} " +
                "gap=${settings.bottomGapDp} revision=${settings.revision}",
        )
        syncMirror(settings)
    }

    private fun startRootDaemon() {
        val appContext = applicationContext ?: return
        rootExecutor.execute {
            val result = CompositeFrameDaemonLauncher.start(
                context = appContext,
                loggingEnabled = false,
                framePeriodMillis = 33,
                captureWidth = 480,
            )
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (result.first) "Root 抓帧已启动" else "Root 抓帧启动失败：${result.second}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun syncMirror(snapshot: ModuleSettings) {
        val appContext = applicationContext ?: return
        // Root-free delivery: Douyin's own process writes the settings file.
        runCatching {
            sendBroadcast(
                Intent("com.autumn.douyin.liquidglass.SETTINGS_CHANGED")
                    .setPackage(DOUYIN_PACKAGE)
                    .putExtra("settings", ModuleSettingsStore.serialize(snapshot)),
            )
        }
        mirrorExecutor.execute {
            val ok = ModuleSettingsMirror.write(appContext, snapshot)
            com.autumn.douyin.liquidglass.ModuleLog.info("settings mirror written=$ok")
        }
    }

    private fun openDouyin() {
        val intent = packageManager.getLaunchIntentForPackage(DOUYIN_PACKAGE)
        if (intent == null) {
            Toast.makeText(this, "未安装抖音", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            startActivity(
                Intent(intent).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                )
            )
        }.onFailure {
            Toast.makeText(this, "无法打开抖音", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        runCatching {
            capturePermissionLauncher.launch(manager.createScreenCaptureIntent())
        }.onFailure {
            Toast.makeText(this, "无法请求录屏权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopScreenCapture() {
        ScreenCaptureService.stop(this)
        Toast.makeText(this, "录屏抓帧已停止", Toast.LENGTH_SHORT).show()
    }

    private fun forceStopDouyin() {        Toast.makeText(this, "正在强制停止抖音…", Toast.LENGTH_SHORT).show()
        rootExecutor.execute {
            val ok = runCatching {
                val process = ProcessBuilder(
                    "/system/bin/su",
                    "-c",
                    "am force-stop $DOUYIN_PACKAGE",
                ).redirectErrorStream(true).start()
                process.inputStream.readBytes()
                process.waitFor() == 0
            }.getOrDefault(false)
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (ok) "抖音已强制停止" else "强停失败，请检查 Root 授权",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private companion object {
        const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"
    }
}
