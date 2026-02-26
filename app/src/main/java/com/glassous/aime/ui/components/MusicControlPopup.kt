package com.glassous.aime.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * 艺术化的L型音乐控制弹窗
 */
@Composable
fun MusicControlPopup(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPauseToggle: () -> Unit,
    isPlaying: Boolean
) {
    // 使用 MutableTransitionState 来控制动画
    val expandedState = remember { MutableTransitionState(false) }
    
    // 当 visible 变为 true 时，开始进场动画
    LaunchedEffect(visible) {
        expandedState.targetState = visible
    }

    // 当动画完成且目标状态为 false 时，触发 onDismiss (虽然 Popup 会自己处理消失，但我们需要同步状态)
    if (!visible && !expandedState.currentState && !expandedState.targetState) {
        // 动画已结束
    }

    if (visible || expandedState.currentState || expandedState.targetState) {
        val blurState = LocalDialogBlurState.current
        
        DisposableEffect(Unit) {
            blurState.value = true
            onDispose { 
                // 只有当完全关闭时才取消模糊，这里简化处理，由调用方控制 visible
                // 实际上如果 onDismiss 被调用，visible 变 false，这里会被销毁
                 blurState.value = false
            }
        }

        val buttonSize = 52.dp //稍微加大按钮尺寸
        val holeSize = 48.dp // 留空尺寸，对应 MiniPlayer
        
        Popup(
            alignment = Alignment.TopEnd,
            onDismissRequest = { 
                expandedState.targetState = false
                onDismiss() 
            },
            offset = IntOffset(0, 0),
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            AnimatedVisibility(
                visibleState = expandedState,
                enter = scaleIn(
                    animationSpec = tween(300),
                    transformOrigin = TransformOrigin(1f, 0f) // 从右上角展开
                ) + fadeIn(tween(300)),
                exit = scaleOut(
                    animationSpec = tween(200),
                    transformOrigin = TransformOrigin(1f, 0f)
                ) + fadeOut(tween(200))
            ) {
                Box(
                    modifier = Modifier
                        .width(buttonSize + holeSize)
                        .height(buttonSize * 2)
                ) {
                    // 背景形状
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = SmoothLShape(
                            buttonSizePx = with(LocalDensity.current) { buttonSize.toPx() },
                            holeSizePx = with(LocalDensity.current) { holeSize.toPx() },
                            cornerRadiusPx = with(LocalDensity.current) { 20.dp.toPx() }
                        ),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp
                    ) {
                        // Empty content, just background
                    }
                    
                    // 按钮布局
                    Column {
                        Row(
                            modifier = Modifier.height(buttonSize),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Prev Button (Left Top)
                            Box(
                                modifier = Modifier
                                    .size(buttonSize)
                                    .clickable(onClick = onPrev),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.SkipPrevious, 
                                    "Previous",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            // Hole (Right Top) - Empty space for MiniPlayer
                            Spacer(modifier = Modifier.size(holeSize))
                        }
                        
                        Row(
                            modifier = Modifier.height(buttonSize),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Pause/Play Button (Left Bottom - Corner)
                            Box(
                                modifier = Modifier
                                    .size(buttonSize)
                                    .clickable(onClick = onPauseToggle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    "Play/Pause",
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            // Next Button (Right Bottom)
                            Box(
                                modifier = Modifier
                                    .size(buttonSize)
                                    .clickable(onClick = onNext),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.SkipNext, 
                                    "Next",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 平滑的L型形状
 */
private class SmoothLShape(
    private val buttonSizePx: Float,
    private val holeSizePx: Float,
    private val cornerRadiusPx: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            val w = size.width
            val h = size.height
            
            // 使用两个圆角矩形合并，或者直接绘制路径
            // 这里我们使用 PathOperation.Union 来合并两个圆角矩形，这样处理圆角连接处更自然
            
            // 竖向矩形 (左侧)
            val verticalRect = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(0f, 0f, buttonSizePx, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx)
                    )
                )
            }
            
            // 横向矩形 (底部)
            val horizontalRect = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(0f, buttonSizePx, w, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx)
                    )
                )
            }
            
            op(verticalRect, horizontalRect, PathOperation.Union)
        }
        return Outline.Generic(path)
    }
}
