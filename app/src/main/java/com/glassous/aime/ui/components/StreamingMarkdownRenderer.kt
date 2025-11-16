package com.glassous.aime.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun StreamingMarkdownRenderer(
    markdown: String,
    textColor: Color,
    textSizeSp: Float,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    Column(modifier = modifier) {
        MarkdownRenderer(
            markdown = markdown,
            textColor = textColor,
            textSizeSp = textSizeSp,
            onLongClick = onLongClick,
            enableTables = !isStreaming
        )
        if (isStreaming) {
            val transition = rememberInfiniteTransition(label = "cursor")
            val alpha by transition.animateFloat(
                initialValue = 1f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "cursorAlpha"
            )
            Row {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "|",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}