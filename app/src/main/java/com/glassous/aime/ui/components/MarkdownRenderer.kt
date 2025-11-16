package com.glassous.aime.ui.components

import android.content.Context
import android.text.Spanned
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.tables.TablePlugin
import java.util.regex.Pattern

data class MarkdownBlock(
    val type: BlockType,
    val content: String,
    val language: String? = null
)

enum class BlockType {
    TEXT, CODE_BLOCK
}

@Composable
fun MarkdownRenderer(
    markdown: String,
    textColor: androidx.compose.ui.graphics.Color,
    textSizeSp: Float,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    enableTables: Boolean = true
) {
    val context = LocalContext.current
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    
    // 创建不包含代码块处理的Markwon实例，用于渲染普通文本
    val markwon = remember(context, enableTables) { 
        val builder = Markwon.builder(context)
            .usePlugin(JLatexMathPlugin.create(44f))
        if (enableTables) {
            builder.usePlugin(TablePlugin.create(context))
        }
        builder.build()
    }

    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block.type) {
                BlockType.TEXT -> {
                    if (block.content.trim().isNotEmpty()) {
                        AndroidView(
                            factory = {
                                TextView(it).apply {
                                    setTextColor(textColor.toArgb())
                                    textSize = textSizeSp
                                    setLinkTextColor(textColor.toArgb())
                                    setTextIsSelectable(false)
                                    isLongClickable = true
                                    setOnLongClickListener {
                                        onLongClick()
                                        true
                                    }
                                }
                            },
                            update = { tv ->
                                tv.setTextColor(textColor.toArgb())
                                tv.textSize = textSizeSp
                                tv.setLinkTextColor(textColor.toArgb())
                                tv.setOnLongClickListener {
                                    onLongClick()
                                    true
                                }
                                markwon.setMarkdown(tv, block.content)
                            },
                            modifier = Modifier.wrapContentWidth()
                        )
                    }
                }
                BlockType.CODE_BLOCK -> {
                    CodeBlockWithCopy(
                        code = block.content,
                        language = block.language,
                        textSizeSp = textSizeSp,
                        modifier = Modifier
                            .wrapContentWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val codeBlockPattern = Pattern.compile("```(\\w+)?\\n([\\s\\S]*?)```", Pattern.MULTILINE)
    val matcher = codeBlockPattern.matcher(markdown)
    
    var lastEnd = 0
    
    while (matcher.find()) {
        // 添加代码块前的文本
        if (matcher.start() > lastEnd) {
            val textContent = markdown.substring(lastEnd, matcher.start()).trim()
            if (textContent.isNotEmpty()) {
                blocks.add(MarkdownBlock(BlockType.TEXT, textContent))
            }
        }
        
        // 添加代码块
        val language = matcher.group(1)
        val code = matcher.group(2) ?: ""
        blocks.add(MarkdownBlock(BlockType.CODE_BLOCK, code, language))
        
        lastEnd = matcher.end()
    }
    
    // 添加最后剩余的文本
    if (lastEnd < markdown.length) {
        val textContent = markdown.substring(lastEnd).trim()
        if (textContent.isNotEmpty()) {
            blocks.add(MarkdownBlock(BlockType.TEXT, textContent))
        }
    }
    
    // 如果没有找到代码块，整个内容作为文本块
    if (blocks.isEmpty()) {
        blocks.add(MarkdownBlock(BlockType.TEXT, markdown))
    }
    
    return blocks
}