package com.glassous.aime.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun StreamingMarkdownRenderer(
    markdown: String,
    textColor: Color,
    textSizeSp: Float,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    onHtmlPreview: ((String) -> Unit)? = null,
    onHtmlPreviewSource: ((String) -> Unit)? = null,
    useCardStyleForHtmlCode: Boolean = false,
    enableTypewriterEffect: Boolean = true,
    onCitationClick: ((String) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null,
    isShareMode: Boolean = false // Added new parameter
) {
    Column(modifier = modifier) {
        MarkdownRenderer(
            markdown = markdown,
            textColor = textColor,
            textSizeSp = textSizeSp,
            onLongClick = onLongClick,
            enableTables = true, // Always enable tables to prevent layout shift
            enableCodeBlocks = true, // Always enable code blocks
            enableLatex = true, // Always enable latex
            onHtmlPreview = onHtmlPreview,
            onHtmlPreviewSource = onHtmlPreviewSource,
            useCardStyleForHtmlCode = useCardStyleForHtmlCode,
            isStreaming = isStreaming && enableTypewriterEffect,
            onCitationClick = onCitationClick,
            onLinkClick = onLinkClick,
            isShareMode = isShareMode
        )
    }
}