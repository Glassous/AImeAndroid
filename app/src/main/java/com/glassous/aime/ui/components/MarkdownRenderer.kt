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
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import java.util.regex.Pattern

data class MarkdownBlock(
    val type: BlockType,
    val content: String,
    val language: String? = null
)

enum class BlockType {
    TEXT, CODE_BLOCK, MERMAID
}

@Composable
fun MarkdownRenderer(
    markdown: String,
    textColor: androidx.compose.ui.graphics.Color,
    textSizeSp: Float,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    enableTables: Boolean = true,
    enableCodeBlocks: Boolean = true,
    enableLatex: Boolean = true,
    onHtmlPreview: ((String) -> Unit)? = null,
    onHtmlPreviewSource: ((String) -> Unit)? = null,
    useCardStyleForHtmlCode: Boolean = false
) {
    // 【修改开始】对 <think> 标签进行转义，防止被作为 HTML 标签隐藏
    val displayMarkdown = remember(markdown) {
        markdown
            .replace("<think>", "&lt;think&gt;")
            .replace("</think>", "&lt;/think&gt;")
    }
    // 【修改结束】

    val context = LocalContext.current
    // 使用处理过的 displayMarkdown 进行解析
    val blocks = remember(displayMarkdown, enableCodeBlocks) { 
        if (enableCodeBlocks) {
            parseMarkdownBlocks(displayMarkdown) 
        } else {
            listOf(MarkdownBlock(BlockType.TEXT, displayMarkdown))
        }
    }

    // 创建不包含代码块处理的Markwon实例，用于渲染普通文本
    val markwon = remember(context, enableTables, enableLatex, textSizeSp) {
        val scaledDensity = context.resources.displayMetrics.scaledDensity
        val textPx = textSizeSp * scaledDensity

        val builder = Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(HtmlPlugin.create())
        
        if (enableLatex) {
            builder.usePlugin(
                JLatexMathPlugin.create(
                    textPx,
                    textPx,
                    object : JLatexMathPlugin.BuilderConfigure {
                        override fun configureBuilder(builder: JLatexMathPlugin.Builder) {
                            builder.inlinesEnabled(true)
                        }
                    }
                )
            )
        }

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
                                val textToRender = preprocessText(block.content, enableLatex)
                                markwon.setMarkdown(tv, textToRender)
                            },
                            modifier = Modifier.wrapContentWidth()
                        )
                    }
                }
                BlockType.MERMAID -> {
                    MermaidWebView(
                        mermaidCode = block.content,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                BlockType.CODE_BLOCK -> {
                    CodeBlockWithCopy(
                        code = block.content,
                        language = block.language,
                        textSizeSp = textSizeSp,
                        modifier = Modifier
                            .wrapContentWidth()
                            .padding(vertical = 4.dp),
                        onPreview = if (onHtmlPreview != null) {
                            { onHtmlPreview(block.content) }
                        } else {
                            null
                        },
                        onPreviewSource = if (onHtmlPreviewSource != null) {
                            { onHtmlPreviewSource(block.content) }
                        } else {
                            null
                        },
                        useCardStyle = useCardStyleForHtmlCode
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
        
        if (language.equals("mermaid", ignoreCase = true)) {
            blocks.add(MarkdownBlock(BlockType.MERMAID, code, language))
        } else {
            blocks.add(MarkdownBlock(BlockType.CODE_BLOCK, code, language))
        }

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

private fun preprocessText(input: String, enableLatex: Boolean): String {
    val sb = StringBuilder()
    var i = 0
    var inInlineCode = false
    var inMath = false
    var inSup = false
    var inSub = false
    
    while (i < input.length) {
        val c = input[i]
        val prev = if (i > 0) input[i - 1] else '\u0000'
        
        // Handle inline code backticks
        if (c == '`' && prev != '\\') {
            inInlineCode = !inInlineCode
            sb.append(c)
            i++
            continue
        }

        if (!inInlineCode) {
            if (enableLatex) {
                // Handle LaTeX delimiters \[ \] \( \)
                if (c == '\\' && i + 1 < input.length) {
                    val next = input[i + 1]
                    if (next == '[' || next == ']' || next == '(' || next == ')') {
                        if (!inMath) {
                            sb.append("$$")
                            inMath = true
                        } else {
                            sb.append("$$")
                            inMath = false
                        }
                        i += 2
                        continue
                    }
                }

                // Handle single dollar $
                if (c == '$' && prev != '\\') {
                    val next = if (i + 1 < input.length) input[i + 1] else '\u0000'
                    val prevChar = prev
                    // Avoid matching $$ (already handled by Markwon or previous iteration)
                    if (next == '$' || prevChar == '$') {
                        // Let Markwon handle $$ (or it's the second $ of $$ we just processed)
                        // If we see $$ here, we just append it and update state if needed?
                        // Actually, Markwon expects $$ for display math.
                        // We should probably track $$ toggle too.
                        // The original code was a bit loose.
                        // Let's assume $$ toggles math.
                        if (next == '$') {
                            // Found $$, skip next
                            sb.append("$$")
                            inMath = !inMath
                            i += 2
                            continue
                        } else if (prevChar == '$') {
                            // Should have been handled by previous iteration
                            sb.append(c)
                            i++
                            continue
                        }
                    }
                    
                    // Convert single $ to $$
                    if (!inMath) {
                        sb.append("$$")
                        inMath = true
                    } else {
                        sb.append("$$")
                        inMath = false
                    }
                    i++
                    continue
                }
            }

            // Sup/Sub handling (only if not in math)
            if (!inMath) {
                 if (c == '^' && prev != '\\') {
                     if (inSup) {
                         sb.append("</sup>")
                         inSup = false
                     } else {
                         sb.append("<sup>")
                         inSup = true
                     }
                     i++
                     continue
                 }
                 if (c == '~' && prev != '\\') {
                     if (inSub) {
                         sb.append("</sub>")
                         inSub = false
                     } else {
                         sb.append("<sub>")
                         inSub = true
                     }
                     i++
                     continue
                 }
            }
        }
        
        sb.append(c)
        i++
    }
    return sb.toString()
}
