package com.glassous.aime.ui.components

import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance

// 用于存储 TextView 的淡入动画状态
class TextFadeState {
    var lastLength: Int = 0
    val fadingChunks = mutableListOf<Pair<Int, Long>>() // (startIndex, startTime)
    var animator: Runnable? = null
}

// 基于时间的淡入 Span
class TimeBasedFadeSpan(
    private val startTime: Long,
    private val duration: Long = 500L
) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(tp: TextPaint?) {
        if (tp == null) return
        val now = System.currentTimeMillis()
        val elapsed = now - startTime
        
        if (elapsed < 0) {
            tp.alpha = 0
        } else if (elapsed < duration) {
            val progress = elapsed.toFloat() / duration
            // 使用 ease-out 插值让动画更自然
            val easedProgress = 1f - (1f - progress) * (1f - progress)
            val alphaScale = easedProgress.coerceIn(0f, 1f)
            
            // 保持原有 alpha 值，应用淡入效果
            tp.alpha = (tp.alpha * alphaScale).toInt()
        } else {
            // 动画结束，保持原有 alpha
        }
    }
}
