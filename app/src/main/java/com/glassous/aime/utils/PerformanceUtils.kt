package com.glassous.aime.utils

import androidx.compose.runtime.*
import kotlinx.coroutines.delay

/**
 * 性能优化工具类
 */
object PerformanceUtils {
    
    /**
     * 防抖函数 - 在指定时间内只执行最后一次调用
     */
    @Composable
    fun <T> rememberDebounced(
        value: T,
        delayMillis: Long = 300L
    ): T {
        var debouncedValue by remember { mutableStateOf(value) }
        
        LaunchedEffect(value) {
            delay(delayMillis)
            debouncedValue = value
        }
        
        return debouncedValue
    }
    
    /**
     * 节流函数 - 在指定时间间隔内最多执行一次
     */
    class Throttle(private val intervalMs: Long = 100L) {
        private var lastExecutionTime = 0L
        
        fun execute(action: () -> Unit) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastExecutionTime >= intervalMs) {
                action()
                lastExecutionTime = currentTime
            }
        }
    }
}