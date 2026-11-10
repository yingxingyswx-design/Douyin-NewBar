package com.autumn.douyin.liquidglass.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.autumn.douyin.liquidglass.settings.DefaultDarkColor
import com.autumn.douyin.liquidglass.settings.DefaultLightColor
import com.autumn.douyin.liquidglass.settings.ModuleSettings
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState
import java.util.Locale
import kotlin.math.roundToInt

private data class ColorPalette(
    val name: String,
    val dark: Color,
    val light: Color,
)

private val Palettes = listOf(
    ColorPalette("黑白", Color(DefaultDarkColor), Color(DefaultLightColor)),
    ColorPalette("墨蓝", Color(0xFF071627), Color(0xFFEAF2FF)),
    ColorPalette("松林", Color(0xFF0B1F17), Color(0xFFEDF7F0)),
    ColorPalette("葡萄", Color(0xFF1A1026), Color(0xFFF4EEFC)),
    ColorPalette("落日", Color(0xFF2A1308), Color(0xFFFFF0E4)),
    ColorPalette("樱雾", Color(0xFF2A0E17), Color(0xFFFFF0F5)),
    ColorPalette("沙丘", Color(0xFF201A12), Color(0xFFF7F1E7)),
    ColorPalette("雾灰", Color(0xFF161618), Color(0xFFECECEE)),
)

private enum class ColorTarget { Dark, Light }

@Composable
fun BarSettingsScreen(
    versionText: String,
    settings: ModuleSettings,
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    onSettingsChange: (ModuleSettings) -> Unit,
    onOpenDouyin: () -> Unit,
    onForceStopDouyin: () -> Unit,
    onStartScreenCapture: () -> Unit,
    onStopScreenCapture: () -> Unit,
) {
    var previewIndex by remember { mutableIntStateOf(0) }
    var pickerTarget by remember { mutableStateOf<ColorTarget?>(null) }
    var showRootCaptureDialog by remember { mutableStateOf(false) }
    val previewLiquidState = rememberLiquidState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 28.dp),
        ) {
            Header(versionText)
            Spacer(Modifier.height(16.dp))
            DependencyNotice()
            Spacer(Modifier.height(16.dp))
            PreviewCard(
                settings = settings,
                previewIndex = previewIndex,
                onPreviewIndexChange = { previewIndex = it },
                liquidState = previewLiquidState,
            )

            Spacer(Modifier.height(14.dp))
            RenderModePanel(
                settings = settings,
                onSettingsChange = onSettingsChange,
                onRequestRootCapture = { showRootCaptureDialog = true },
            )

            Spacer(Modifier.height(14.dp))
            ThemeModePanel(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
            )

            Spacer(Modifier.height(26.dp))
            SectionTitle(
                title = "配色",
                subtitle = if (settings.liquidGlass) {
                    "毛玻璃模式下颜色作为玻璃染色；点色卡一键套用，或分别自定义深色 / 明亮两种状态。"
                } else {
                    "默认黑白。点按色卡一键套用，或分别自定义深色 / 明亮两种状态。"
                },
            )
            Spacer(Modifier.height(14.dp))
            PaletteRow(
                settings = settings,
                onApply = { palette ->
                    onSettingsChange(
                        settings.copy(
                            darkColor = palette.dark.toArgb(),
                            lightColor = palette.light.toArgb(),
                        )
                    )
                },
            )

            Spacer(Modifier.height(18.dp))
            ColorStateCard(
                title = "深色状态",
                tabs = "首页 · 朋友",
                color = Color(settings.darkColor),
                onClick = { pickerTarget = ColorTarget.Dark },
            )
            Spacer(Modifier.height(10.dp))
            ColorStateCard(
                title = "明亮状态",
                tabs = "消息 · 我",
                color = Color(settings.lightColor),
                onClick = { pickerTarget = ColorTarget.Light },
            )

            Spacer(Modifier.height(26.dp))
            SectionTitle(
                title = "行为",
                subtitle = "运行依赖 Root + LSPosed 环境；纯色模式零采集，毛玻璃模式仅用 PixelCopy 读取本应用窗口底部条带。",
            )
            Spacer(Modifier.height(14.dp))
            SettingsPanel {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "隐藏系统导航栏",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "关闭后恢复系统手势条 / 三键导航；开启时内容铺满底部，上滑临时呼出",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.hideSystemNav,
                        onCheckedChange = {
                            onSettingsChange(settings.copy(hideSystemNav = it))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "启用导航栏",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "切换开关后需重启抖音才会完整生效",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.enabled,
                        onCheckedChange = { onSettingsChange(settings.copy(enabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DimensionSliderRow(
                    title = "栏体高度",
                    subtitle = "26–46dp。小屏手机可以调矮，大屏或平板可以调高。",
                    value = settings.barHeightDp,
                    range = ModuleSettings.MinBarHeightDp.toFloat()..
                        ModuleSettings.MaxBarHeightDp.toFloat(),
                    onCommit = { onSettingsChange(settings.copy(barHeightDp = it)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DimensionSliderRow(
                    title = "悬浮间距",
                    subtitle = "0–16dp。栏体距离屏幕底部的空隙，默认 4dp。",
                    value = settings.bottomGapDp,
                    range = ModuleSettings.MinBottomGapDp.toFloat()..
                        ModuleSettings.MaxBottomGapDp.toFloat(),
                    onCommit = { onSettingsChange(settings.copy(bottomGapDp = it)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InfoRow("状态切换", "首页/朋友 深色 · 消息/我 明亮")
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onOpenDouyin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 20.dp),
            ) {
                Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("打开抖音", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onForceStopDouyin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                contentPadding = PaddingValues(horizontal = 20.dp),
            ) {
                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("强制停止抖音", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(22.dp))
            Text(
                text = "LSPosed 作用域：com.ss.android.ugc.aweme\n已适配抖音 39.x / 40.x · 仅修改本地界面，不收集任何数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    pickerTarget?.let { target ->
        val isDark = target == ColorTarget.Dark
        ColorPickerDialog(
            title = if (isDark) "深色状态颜色" else "明亮状态颜色",
            subtitle = if (isDark) "首页 · 朋友" else "消息 · 我",
            initialColor = Color(if (isDark) settings.darkColor else settings.lightColor),
            onDismiss = { pickerTarget = null },
            onConfirm = { color ->
                val argb = color.toArgb()
                onSettingsChange(
                    if (isDark) settings.copy(darkColor = argb)
                    else settings.copy(lightColor = argb)
                )
                pickerTarget = null
            },
        )
    }

    if (showRootCaptureDialog) {
        AlertDialog(
            onDismissRequest = { showRootCaptureDialog = false },
            title = { Text("启用 Root 抓帧（实验性）？") },
            text = {
                Text(
                    "该功能会通过 Root 启动独立进程，直接读取 SurfaceFlinger 合成帧。\n\n" +
                        "风险：可能触发厂商显示驱动/合成器崩溃，表现为花屏、黑屏、界面重启、" +
                        "手机软重启或开机循环。不会永久变砖，但出问题需要通过 Recovery " +
                        "或安全模式禁用模块才能恢复。\n\n" +
                        "需要 Root 权限；默认关闭；仅在你自己愿意承担风险时启用。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRootCaptureDialog = false
                        onSettingsChange(
                            settings.copy(rootCapture = true, realtimeGlass = false)
                        )
                    },
                ) {
                    Text("我已了解，启用")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRootCaptureDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun DependencyNotice() {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "运行依赖（必须）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.height(6.dp))
                DependencyLine(
                    "Root 权限",
                    "必须。手机需先获取 Root，并安装 LSPosed / Zygisk 框架。",
                )
                Spacer(Modifier.height(3.dp))
                DependencyLine(
                    "LSPosed 作用域",
                    "必须。启用本模块，并勾选「抖音」com.ss.android.ugc.aweme。",
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "缺少 Root 或 LSPosed，模块不会生效。修改颜色后重启抖音即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DependencyLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = "· $label：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Header(versionText: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "M3 毛玻璃",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Material 3 Expressive · 纯色 / 毛玻璃",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = versionText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun ThemeModePanel(
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
) {
    SettingsPanel {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = "App 主题",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "设置页跟随系统，或手动指定亮色 / 深色",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeOption(
                    label = "跟随系统",
                    mode = 0,
                    selected = themeMode == 0,
                    onSelect = onThemeModeChange,
                    modifier = Modifier.weight(1f),
                )
                ThemeOption(
                    label = "浅色",
                    mode = 1,
                    selected = themeMode == 1,
                    onSelect = onThemeModeChange,
                    modifier = Modifier.weight(1f),
                )
                ThemeOption(
                    label = "深色",
                    mode = 2,
                    selected = themeMode == 2,
                    onSelect = onThemeModeChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    mode: Int,
    selected: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        modifier = modifier.clickable { onSelect(mode) },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(vertical = 10.dp),
        )
    }
}

@Composable
private fun RenderModePanel(
    settings: ModuleSettings,
    onSettingsChange: (ModuleSettings) -> Unit,
    onRequestRootCapture: () -> Unit,
) {
    SettingsPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "毛玻璃",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (settings.liquidGlass) {
                        "实时模糊 + 染色 + 边缘高光，60fps 低分辨率采样"
                    } else {
                        "关闭时使用纯色 Material 3；打开后颜色会作为毛玻璃染色"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.liquidGlass,
                onCheckedChange = { onSettingsChange(settings.copy(liquidGlass = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow("渲染管线", if (settings.liquidGlass) "PixelCopy + GPU 模糊" else "纯色 Compose")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow(
            "背景采样",
            if (settings.liquidGlass) {
                if (settings.rootCapture) "Root 合成帧（实验）"
                else if (settings.realtimeGlass) "实时 30fps（实验）"
                else "静态毛玻璃（稳定）"
            } else {
                "不采样"
            },
        )
        if (settings.liquidGlass) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "实时采样（实验）",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "关闭时使用稳定静态毛玻璃；开启后读取底部画面，部分视频层可能掉帧或不可用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.realtimeGlass,
                    onCheckedChange = {
                        onSettingsChange(
                            settings.copy(realtimeGlass = it, rootCapture = false)
                        )
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Root 抓帧（实验）· Android 16 暂不可用",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Android 16 已移除旧 SurfaceControl/ScreenCapture 隐藏接口，抓帧不可用；请使用静态毛玻璃或实时采样",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = false,
                    enabled = false,
                    onCheckedChange = {},
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DimensionSliderRow(
                title = "雾面强度",
                subtitle = "0–32dp。数值越大越糊，文字越容易读；0dp 为清晰玻璃。",
                value = settings.frostDp,
                range = ModuleSettings.MinFrostDp.toFloat()..
                    ModuleSettings.MaxFrostDp.toFloat(),
                onCommit = { onSettingsChange(settings.copy(frostDp = it)) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "玻璃染色",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "关闭后改用中性黑 / 白毛玻璃，不套用状态颜色",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.glassTintEnabled,
                    onCheckedChange = {
                        onSettingsChange(settings.copy(glassTintEnabled = it))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PreviewCard(
    settings: ModuleSettings,
    previewIndex: Int,
    onPreviewIndexChange: (Int) -> Unit,
    liquidState: LiquidState,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(28.dp),
            ),
    ) {
        // Sibling liquefiable layer with a high-frequency wallpaper so the glass
        // blur/refraction is actually visible while dragging the frost slider.
        Box(
            modifier = Modifier
                .matchParentSize()
                .liquefiable(liquidState),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF2C3A4E),
                            Color(0xFF101A2B),
                            Color(0xFF3A2338),
                        ),
                    )
                )
                drawCircle(
                    color = Color(0xFFFF8A65),
                    radius = size.minDimension * 0.30f,
                    center = Offset(size.width * 0.16f, size.height * 0.18f),
                )
                drawCircle(
                    color = Color(0xFF4DD0E1),
                    radius = size.minDimension * 0.22f,
                    center = Offset(size.width * 0.82f, size.height * 0.32f),
                )
                drawCircle(
                    color = Color(0xFFF06292),
                    radius = size.minDimension * 0.18f,
                    center = Offset(size.width * 0.55f, size.height * 0.85f),
                )
                for (index in 0..10) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.18f),
                        start = Offset(size.width * (index / 10f), 0f),
                        end = Offset(size.width * (index / 10f + 0.08f), size.height),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
        }
        Column(Modifier.padding(top = 16.dp, bottom = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "实时预览",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.72f),
                )
                Text(
                    text = when {
                        settings.liquidGlass -> "毛玻璃"
                        previewIndex <= 1 -> "深色状态"
                        else -> "明亮状态"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.46f),
                )
            }
            Spacer(Modifier.height(10.dp))
            M3BarSurface(
                settings = settings,
                selectedIndex = previewIndex,
                onTabSelected = onPreviewIndexChange,
                onPlusClick = {},
                onPlusLongClick = { false },
                messageBadgeCount = 3,
                liquidState = if (settings.liquidGlass) liquidState else null,
            )
            Text(
                text = "点按标签预览两种状态",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PaletteRow(
    settings: ModuleSettings,
    onApply: (ColorPalette) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Palettes.forEach { palette ->
            val selected = palette.dark.toArgb() == settings.darkColor &&
                palette.light.toArgb() == settings.lightColor
            Column(
                modifier = Modifier
                    .width(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onApply(palette) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                            },
                            shape = CircleShape,
                        )
                        .padding(if (selected) 4.dp else 3.dp)
                        .clip(CircleShape)
                        .background(palette.dark),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.5f)
                            .align(Alignment.CenterEnd)
                            .background(palette.light),
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = contentColorOn(palette.dark),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = palette.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun ColorStateCard(
    title: String,
    tabs: String,
    color: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(color)
                    .border(
                        width = 1.dp,
                        color = contentColorOn(color).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(18.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Aa",
                    color = contentColorOn(color),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = tabs,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = color.hexString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            FilledTonalButton(
                onClick = onClick,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Colorize,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("改色", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SettingsPanel(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = { content() })
    }
}

@Composable
private fun DimensionSliderRow(
    title: String,
    subtitle: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    onCommit: (Int) -> Unit,
) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${draft.roundToInt()}dp",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = {
                onCommit(draft.roundToInt().coerceIn(range.start.toInt(), range.endInclusive.toInt()))
            },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onPrimary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ColorPickerDialog(
    title: String,
    subtitle: String,
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val hsv = remember {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColor.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.hsv(hue, saturation, value)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
                    }
                }

                Spacer(Modifier.height(16.dp))
                SaturationValuePanel(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onChanged = { s, v ->
                        saturation = s
                        value = v
                    },
                )
                Spacer(Modifier.height(14.dp))
                HueSlider(
                    hue = hue,
                    onHueChanged = { hue = it },
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(color)
                            .border(
                                1.dp,
                                contentColorOn(color).copy(alpha = 0.14f),
                                RoundedCornerShape(14.dp),
                            ),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = color.hexString(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "H ${hue.toInt()}°  S ${(saturation * 100).toInt()}%  V ${(value * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(color) },
                        shape = CircleShape,
                    ) {
                        Text("使用此颜色")
                    }
                }
            }
        }
    }
}

@Composable
private fun SaturationValuePanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChanged: (Float, Float) -> Unit,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    onChanged(
                        (position.x / size.width).coerceIn(0f, 1f),
                        1f - (position.y / size.height).coerceIn(0f, 1f),
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { position ->
                        onChanged(
                            (position.x / size.width).coerceIn(0f, 1f),
                            1f - (position.y / size.height).coerceIn(0f, 1f),
                        )
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        onChanged(
                            (change.position.x / size.width).coerceIn(0f, 1f),
                            1f - (change.position.y / size.height).coerceIn(0f, 1f),
                        )
                    },
                )
            },
    ) {
        drawRect(Color.hsv(hue, 1f, 1f))
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

        val center = Offset(
            x = saturation * size.width,
            y = (1f - value) * size.height,
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.35f),
            radius = 9.dp.toPx(),
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )
        drawCircle(
            color = Color.White,
            radius = 7.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
private fun HueSlider(
    hue: Float,
    onHueChanged: (Float) -> Unit,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    onHueChanged((position.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { position ->
                        onHueChanged((position.x / size.width).coerceIn(0f, 1f) * 360f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        onHueChanged((change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                    },
                )
            },
    ) {
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Red,
                    Color.Yellow,
                    Color.Green,
                    Color.Cyan,
                    Color.Blue,
                    Color.Magenta,
                    Color.Red,
                )
            )
        )
        val x = (hue / 360f) * size.width
        drawCircle(
            color = Color.White,
            radius = 9.dp.toPx(),
            center = Offset(x.coerceIn(9.dp.toPx(), size.width - 9.dp.toPx()), size.height / 2f),
            style = Stroke(width = 2.5.dp.toPx()),
        )
    }
}

private fun Color.hexString(): String =
    String.format(Locale.US, "#%06X", toArgb() and 0xFFFFFF)
