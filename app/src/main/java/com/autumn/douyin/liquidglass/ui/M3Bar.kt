package com.autumn.douyin.liquidglass.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autumn.douyin.liquidglass.settings.ModuleSettings
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquid
import kotlin.math.roundToInt

/**
 * Compact Material 3 Expressive navigation bar for Douyin.
 *
 * Two render modes share the same layout and settings:
 * - solid: flat opaque M3 surface (default, zero readback)
 * - liquid glass: FolkPatch-style RuntimeShader lens on a captured backdrop
 */
object BarMetrics {
    const val HorizontalMarginDp = 10
    const val PlusGapDp = 8
    const val TabIconSizeDp = 16
}

data class BarTab(
    val label: String,
    val icon: ImageVector,
    val darkState: Boolean,
)

val DouyinTabs: List<BarTab> = listOf(
    BarTab("首页", Icons.Rounded.Cottage, darkState = true),
    BarTab("朋友", Icons.Rounded.People, darkState = true),
    BarTab("消息", Icons.Rounded.ChatBubble, darkState = false),
    BarTab("我", Icons.Rounded.Face, darkState = false),
)

const val MessageTabIndex = 2

/** Black-on-light or white-on-dark, chosen from relative luminance. */
fun contentColorOn(background: Color): Color =
    if (background.luminance() > 0.45f) Color(0xFF0B0B0D) else Color(0xFFF7F7F8)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun M3BarSurface(
    settings: ModuleSettings,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    onPlusClick: () -> Unit,
    onPlusLongClick: () -> Boolean,
    modifier: Modifier = Modifier,
    messageBadgeCount: Int = 0,
    liquidState: LiquidState? = null,
    hasBackdrop: Boolean = liquidState != null,
    backdrop: ImageBitmap? = null,
    glassTintOnly: Boolean = false,
) {
    val targetContainer = Color(settings.barColorFor(selectedIndex))
    val neutralTint = settings.liquidGlass && !settings.glassTintEnabled
    val targetTint = if (neutralTint) {
        if (targetContainer.luminance() < 0.45f) Color(0xFF3F3F46) else Color(0xFFEDEDF0)
    } else {
        targetContainer
    }
    val container by animateColorAsState(
        targetValue = targetTint,
        animationSpec = tween(durationMillis = 260),
        label = "bar-container",
    )
    val content by animateColorAsState(
        targetValue = contentColorOn(targetTint),
        animationSpec = tween(durationMillis = 260),
        label = "bar-content",
    )
    val darkSurface = container.luminance() < 0.45f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(settings.windowHeightDp.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(horizontal = BarMetrics.HorizontalMarginDp.dp)
                .padding(bottom = settings.bottomGapDp.dp)
                .height(settings.barHeightDp.dp),
            horizontalArrangement = Arrangement.spacedBy(BarMetrics.PlusGapDp.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarPill(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                selectedIndex = selectedIndex,
                container = container,
                content = content,
                darkSurface = darkSurface,
                messageBadgeCount = messageBadgeCount,
                onTabSelected = onTabSelected,
                liquidState = liquidState,
                hasBackdrop = hasBackdrop,
                backdrop = backdrop,
                glassTintOnly = glassTintOnly,
                frostDp = settings.frostDp,
                neutralTint = neutralTint,
            )
            PlusButton(
                size = settings.barHeightDp.dp,
                container = container,
                content = content,
                onClick = onPlusClick,
                onLongClick = onPlusLongClick,
                liquidState = liquidState,
                hasBackdrop = hasBackdrop,
                backdrop = backdrop,
                glassTintOnly = glassTintOnly,
                frostDp = settings.frostDp,
                neutralTint = neutralTint,
            )
        }
    }
}

@Composable
private fun BarPill(
    selectedIndex: Int,
    container: Color,
    content: Color,
    darkSurface: Boolean,
    messageBadgeCount: Int,
    onTabSelected: (Int) -> Unit,
    liquidState: LiquidState?,
    hasBackdrop: Boolean,
    backdrop: ImageBitmap?,
    glassTintOnly: Boolean,
    frostDp: Int,
    neutralTint: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    val panelModifier = when {
        backdrop != null -> modifier.frostedPanel(
            panelShape = shape,
            tint = container,
            content = content,
            darkSurface = darkSurface,
            backdrop = backdrop,
            neutralTint = neutralTint,
        )
        liquidState != null -> modifier.liquidPanel(
            state = liquidState,
            panelShape = shape,
            tint = container,
            content = content,
            darkSurface = darkSurface,
            hasBackdrop = hasBackdrop,
            backdrop = backdrop,
            frostDp = frostDp,
        )
        glassTintOnly -> modifier.systemBlurGlassPanel(
            panelShape = shape,
            tint = container,
            content = content,
            darkSurface = darkSurface,
            neutralTint = neutralTint,
        )
        else -> modifier
            .shadow(
                elevation = 4.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.22f),
                spotColor = Color.Black.copy(alpha = 0.22f),
            )
            .clip(shape)
            .background(container)
            .border(
                width = 1.dp,
                color = content.copy(alpha = if (darkSurface) 0.14f else 0.16f),
                shape = shape,
            )
    }
    BoxWithConstraints(modifier = panelModifier) {
        val tabWidth = maxWidth / DouyinTabs.size
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = 0.74f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "tab-indicator",
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = indicatorOffset + 3.dp)
                .width((tabWidth - 6.dp).coerceAtLeast(1.dp))
                .fillMaxHeight()
                .padding(vertical = 3.dp)
                .background(
                    color = content.copy(alpha = if (darkSurface) 0.16f else 0.09f),
                    shape = CircleShape,
                ),
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DouyinTabs.forEachIndexed { index, tab ->
                BarItem(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    tab = tab,
                    selected = index == selectedIndex,
                    content = content,
                    badgeCount = if (index == MessageTabIndex) messageBadgeCount else 0,
                    onClick = { onTabSelected(index) },
                )
            }
        }
    }
}

@Composable
private fun BarItem(
    tab: BarTab,
    selected: Boolean,
    content: Color,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.92f
            selected -> 1.06f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tab-scale-${tab.label}",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.56f,
        animationSpec = tween(180),
        label = "tab-alpha-${tab.label}",
    )

    Row(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = content.copy(alpha = contentAlpha),
                modifier = Modifier.size(BarMetrics.TabIconSizeDp.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = tab.label,
            color = content.copy(alpha = contentAlpha),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            style = TextStyle(
                shadow = Shadow(
                    color = if (content.luminance() > 0.5f) {
                        Color.Black.copy(alpha = 0.45f)
                    } else {
                        Color.White.copy(alpha = 0.35f)
                    },
                    offset = Offset(0f, 1f),
                    blurRadius = 2.5f,
                ),
            ),
        )
        if (badgeCount > 0) {
            Spacer(Modifier.width(2.dp))
            MessageBadge(
                count = badgeCount,
                modifier = Modifier.offset(y = (-2).dp),
            )
        }
    }
}

@Composable
fun MessageBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val label = if (count > 99) "99+" else count.toString()
    val width = when {
        count > 99 -> 21.dp
        count > 9 -> 16.dp
        else -> 12.dp
    }
    Box(
        modifier = modifier
            .size(width = width, height = 12.dp)
            .background(Color(0xFFFF453A), CircleShape)
            .border(0.8.dp, Color.White.copy(alpha = 0.9f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 7.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlusButton(
    size: androidx.compose.ui.unit.Dp,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    onLongClick: () -> Boolean,
    liquidState: LiquidState?,
    hasBackdrop: Boolean,
    backdrop: ImageBitmap?,
    glassTintOnly: Boolean,
    frostDp: Int,
    neutralTint: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "plus-scale",
    )
    val glass = liquidState != null || glassTintOnly || backdrop != null
    val backgroundModifier = when {
        backdrop != null -> Modifier.frostedPanel(
            panelShape = CircleShape,
            tint = container,
            content = content,
            darkSurface = container.luminance() < 0.45f,
            backdrop = backdrop,
            neutralTint = neutralTint,
        )
        liquidState != null -> Modifier.liquidPanel(
            state = liquidState,
            panelShape = CircleShape,
            tint = container,
            content = content,
            darkSurface = container.luminance() < 0.45f,
            hasBackdrop = hasBackdrop,
            backdrop = null,
            frostDp = frostDp,
        )
        glassTintOnly -> Modifier.systemBlurGlassPanel(
            panelShape = CircleShape,
            tint = container,
            content = content,
            darkSurface = container.luminance() < 0.45f,
            neutralTint = neutralTint,
        )
        else -> Modifier
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.22f),
                spotColor = Color.Black.copy(alpha = 0.22f),
            )
            .clip(CircleShape)
            .background(content)
    }

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(backgroundModifier)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { onLongClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "拍摄",
            tint = if (glass) content else container,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Frosted panel for devices where the system composites `FLAG_BLUR_BEHIND` at
 * display refresh rate (FolkPatch's smooth path). SurfaceFlinger does the blur,
 * so this only adds the glass tint, border and specular highlights.
 */
private fun Modifier.systemBlurGlassPanel(
    panelShape: Shape,
    tint: Color,
    content: Color,
    darkSurface: Boolean,
    neutralTint: Boolean,
): Modifier = this
    .shadow(
        elevation = 10.dp,
        shape = panelShape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.28f),
        spotColor = Color.Black.copy(alpha = 0.28f),
    )
    .clip(panelShape)
    .background(
        Brush.verticalGradient(
            listOf(
                tint.copy(alpha = if (neutralTint) 0.50f else if (darkSurface) 0.78f else 0.84f),
                tint.copy(alpha = if (neutralTint) 0.62f else if (darkSurface) 0.90f else 0.94f),
                tint.copy(alpha = if (neutralTint) 0.54f else if (darkSurface) 0.84f else 0.90f),
            ),
        ),
    )
    .border(
        width = 1.dp,
        color = content.copy(alpha = if (darkSurface) 0.22f else 0.18f),
        shape = panelShape,
    )
    .drawBehind {
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (darkSurface) 0.18f else 0.34f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.04f),
                ),
            ),
            cornerRadius = CornerRadius(size.height / 2f),
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.45f),
                    Color.Transparent,
                ),
            ),
            topLeft = Offset(size.width * 0.22f, 1.dp.toPx()),
            size = Size(size.width * 0.56f, 1.dp.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
    }

/**
 * Stable frosted panel used in the hooked app: the captured strip is already
 * blurred at 1/8 resolution, so this only draws it, tints it and adds the
 * border/specular layers. No RuntimeShader and no per-frame RenderEffect.
 */
private fun Modifier.frostedPanel(
    panelShape: Shape,
    tint: Color,
    content: Color,
    darkSurface: Boolean,
    backdrop: ImageBitmap,
    neutralTint: Boolean,
): Modifier = this
    .shadow(
        elevation = 8.dp,
        shape = panelShape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.28f),
        spotColor = Color.Black.copy(alpha = 0.28f),
    )
    .clip(panelShape)
    .drawBehind {
        val radius = size.height / 2f
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    cornerRadius = CornerRadius(radius),
                )
            )
        }
        clipPath(path) {
            drawImage(
                image = backdrop,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Low,
            )
            drawRect(
                tint.copy(
                    alpha = when {
                        neutralTint -> 0.22f
                        darkSurface -> 0.38f
                        else -> 0.46f
                    },
                )
            )
        }
    }
    .drawBehind {
        // Fixed frosted texture for pages where the video layer cannot be
        // captured: soft haze blobs plus a thin grain, not a flat black block.
        val radius = size.height / 2f
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (darkSurface) 0.10f else 0.34f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.05f),
                ),
            ),
            cornerRadius = CornerRadius(radius),
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = if (darkSurface) 0.10f else 0.26f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.22f, size.height * 0.2f),
                radius = size.width * 0.35f,
            ),
            radius = size.width * 0.35f,
            center = Offset(size.width * 0.22f, size.height * 0.2f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = if (darkSurface) 0.06f else 0.18f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.78f, size.height * 0.75f),
                radius = size.width * 0.30f,
            ),
            radius = size.width * 0.30f,
            center = Offset(size.width * 0.78f, size.height * 0.75f),
        )
    }

/**
 * FolkPatch-style glass surface: the `liquid` RuntimeShader samples the recorded
 * backdrop and applies refraction, frost, dispersion and edge lighting. Used by
 * the settings preview only; the hooked app uses [frostedPanel] for stability.
 */
private fun Modifier.liquidPanel(
    state: LiquidState?,
    panelShape: Shape,
    tint: Color,
    content: Color,
    darkSurface: Boolean,
    hasBackdrop: Boolean,
    backdrop: ImageBitmap?,
    frostDp: Int,
): Modifier {
    val baseAlpha = when {
        backdrop != null -> 0f
        !hasBackdrop -> 0.55f
        darkSurface -> 0.18f
        else -> 0.24f
    }
    val scrimAlpha = when {
        backdrop != null -> if (darkSurface) 0.10f else 0.14f
        !hasBackdrop -> 0.32f
        darkSurface -> 0.14f
        else -> 0.20f
    }
    val shadowed = shadow(
        elevation = 10.dp,
        shape = panelShape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.28f),
        spotColor = Color.Black.copy(alpha = 0.28f),
    )
    if (state == null) {
        return shadowed
            .clip(panelShape)
            .background(tint.copy(alpha = 0.72f))
            .border(
                width = 1.dp,
                color = content.copy(alpha = if (darkSurface) 0.14f else 0.16f),
                shape = panelShape,
            )
    }
    val frostedBackdrop = Modifier.drawBehind {
        val image = backdrop ?: return@drawBehind
        val radius = size.height / 2f
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    cornerRadius = CornerRadius(radius),
                )
            )
        }
        clipPath(path) {
            drawImage(
                image = image,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Low,
            )
            // Tint the frosted bitmap; the blur comes from the 1/5 capture and
            // the liquid frost, the tint keeps labels readable.
            drawRect(tint.copy(alpha = if (darkSurface) 0.36f else 0.46f))
        }
    }
    return shadowed
        .clip(panelShape)
        .then(frostedBackdrop)
        // Guaranteed frosted base. Even if a ROM rejects the AGSL lens, the bar
        // still has enough opacity for labels to stay readable over video.
        .background(tint.copy(alpha = baseAlpha))
        .liquid(state) {
            shape = panelShape
            frost = frostDp.dp
            curve = 0.40f
            refraction = 0.13f
            dispersion = 0.16f
            saturation = 0.60f
            contrast = 1.60f
            edge = 10f
            // This tint is what actually lands on the sampled backdrop: the
            // liquid layer draws over the direct frosted fallback. Keep it high
            // enough for white/black labels to stay readable on bright video.
            this.tint = tint.copy(alpha = if (backdrop != null) 0.32f else 0.16f)
        }
        .drawBehind {
            // Readability scrim: frost alone can still leave bright video behind
            // the labels. This layer sits above the lens but below the icons.
            drawRoundRect(
                color = tint.copy(alpha = scrimAlpha),
                cornerRadius = CornerRadius(size.height / 2f),
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (darkSurface) 0.16f else 0.30f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.04f),
                    ),
                ),
                cornerRadius = CornerRadius(size.height / 2f),
            )
            drawRoundRect(
                color = content.copy(alpha = if (darkSurface) 0.20f else 0.16f),
                style = Stroke(width = 1.dp.toPx()),
                cornerRadius = CornerRadius(size.height / 2f),
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.45f),
                        Color.Transparent,
                    ),
                ),
                topLeft = Offset(size.width * 0.22f, 1.dp.toPx()),
                size = Size(size.width * 0.56f, 1.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
        }
}
