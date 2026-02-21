package com.glassous.aime.ui.components

import android.content.Context
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.style.ReplacementSpan
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
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
    useCardStyleForHtmlCode: Boolean = false,
    isStreaming: Boolean = false,
    onCitationClick: ((String) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null
) {
    // 【修改开始】对 <think> 标签进行转义，防止被作为 HTML 标签隐藏
    val displayMarkdown = remember(markdown) {
        markdown
            .replace("<think>", "&lt;think&gt;")
            .replace("</think>", "&lt;/think&gt;")
            .replace("<search>", "&lt;search&gt;")
            .replace("</search>", "&lt;search&gt;")
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
                                    // 确保链接可点击
                                    movementMethod = android.text.method.LinkMovementMethod.getInstance()
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
                                var spanned: CharSequence = markwon.toMarkdown(textToRender)
                                
                                    // 处理引用标签 ^n
                                if (spanned is Spanned) {
                                    val builder = SpannableStringBuilder(spanned)
                                    val urlSpans = builder.getSpans(0, builder.length, URLSpan::class.java)
                                    var hasModifications = false
                                    
                                    for (span in urlSpans) {
                                        val url = span.url
                                        val start = builder.getSpanStart(span)
                                        val end = builder.getSpanEnd(span)
                                        val flags = builder.getSpanFlags(span)
                                        
                                        if (url.startsWith("citation:")) {
                                            val id = url.removePrefix("citation:")
                                            builder.removeSpan(span)
                                            
                                            // 添加点击事件
                                            builder.setSpan(object : ClickableSpan() {
                                                override fun onClick(widget: View) {
                                                    onCitationClick?.invoke(id)
                                                }
                                                override fun updateDrawState(ds: TextPaint) {
                                                    ds.isUnderlineText = false
                                                    ds.color = textColor.toArgb()
                                                }
                                            }, start, end, flags)
                                            
                                            // 添加外观样式（小卡片）
                                            builder.setSpan(CitationAppearanceSpan(id, textColor.toArgb(), textSizeSp), start, end, flags)
                                            hasModifications = true
                                        } else if (onLinkClick != null) {
                                            // 处理普通链接点击
                                            builder.removeSpan(span)
                                            
                                            builder.setSpan(object : ClickableSpan() {
                                                override fun onClick(widget: View) {
                                                    onLinkClick.invoke(url)
                                                }
                                                override fun updateDrawState(ds: TextPaint) {
                                                    ds.isUnderlineText = true
                                                    ds.color = textColor.toArgb()
                                                }
                                            }, start, end, flags)
                                            hasModifications = true
                                        }
                                    }
                                    
                                    if (hasModifications) {
                                        spanned = builder
                                    }
                                }
                                
                                // 处理流式输出的淡入动画
                                if (isStreaming) {
                                    val state = (tv.tag as? TextFadeState) ?: TextFadeState().also { tv.tag = it }
                                    val currentLength = spanned.length
                                    
                                    // 如果文本变短（可能是重新生成或编辑），重置状态
                                    if (currentLength < state.lastLength) {
                                        state.lastLength = 0
                                        state.fadingChunks.clear()
                                    }
                                    
                                    // 检测新增文本块
                                    if (currentLength > state.lastLength) {
                                        state.fadingChunks.add(state.lastLength to System.currentTimeMillis())
                                        state.lastLength = currentLength
                                    }
                                    
                                    if (state.fadingChunks.isNotEmpty()) {
                                        val builder = SpannableStringBuilder(spanned)
                                        val now = System.currentTimeMillis()
                                        val duration = 500L // 动画持续时间 500ms
                                        
                                        // 清理已完成的动画
                                        state.fadingChunks.removeAll { (_, time) -> (now - time) >= duration }
                                        
                                        // 应用淡入 Span
                                        val sortedChunks = state.fadingChunks.sortedBy { it.first }
                                        for (i in sortedChunks.indices) {
                                            val (start, time) = sortedChunks[i]
                                            val end = if (i + 1 < sortedChunks.size) sortedChunks[i+1].first else builder.length
                                            
                                            if (start < end) {
                                                builder.setSpan(
                                                    TimeBasedFadeSpan(time, duration),
                                                    start,
                                                    end,
                                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                                )
                                            }
                                        }
                                        
                                        tv.text = builder
                                        
                                        // 触发持续重绘
                                        if (state.fadingChunks.isNotEmpty()) {
                                            state.animator?.let { tv.removeCallbacks(it) }
                                            state.animator = object : Runnable {
                                                override fun run() {
                                                    val currentTime = System.currentTimeMillis()
                                                    if (state.fadingChunks.isNotEmpty()) {
                                                        tv.invalidate()
                                                        // 只有当还有未完成的动画时才继续 post
                                                        if (state.fadingChunks.any { (_, time) -> (currentTime - time) < duration }) {
                                                            tv.postOnAnimation(this)
                                                        } else {
                                                            // 所有动画已完成，清理状态
                                                            state.fadingChunks.clear()
                                                        }
                                                    }
                                                }
                                            }
                                            tv.postOnAnimation(state.animator)
                                        }
                                    } else {
                                        tv.text = spanned
                                        state.lastLength = currentLength
                                    }
                                } else {
                                    // 非流式状态，直接显示
                                    tv.text = spanned
                                    // 重置状态以防下次变为流式
                                    val state = (tv.tag as? TextFadeState) ?: TextFadeState().also { tv.tag = it }
                                    state.lastLength = spanned.length
                                    state.fadingChunks.clear()
                                }
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
            // Handle citation ^n
            if (c == '^' && prev != '\\') {
                if (i + 1 < input.length && input[i+1].isDigit()) {
                    var j = i + 1
                    while (j < input.length && input[j].isDigit()) {
                        j++
                    }
                    val num = input.substring(i + 1, j)
                    sb.append("[$num](citation:$num)")
                    i = j 
                    continue
                }
            }

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

class CitationAppearanceSpan(
    private val id: String,
    private val textColor: Int,
    private val textSizeSp: Float
) : ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val originalTextSize = paint.textSize
        val smallTextSize = originalTextSize * 0.65f
        paint.textSize = smallTextSize
        val width = paint.measureText(id)
        paint.textSize = originalTextSize
        
        val paddingX = smallTextSize * 0.6f
        val cardWidth = width + paddingX * 2
        return (cardWidth + 8).toInt() // Add margin
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val originalTextSize = paint.textSize
        val originalColor = paint.color
        val originalStyle = paint.style

        // Set smaller font for citation
        val smallTextSize = originalTextSize * 0.65f
        paint.textSize = smallTextSize
        
        val textWidth = paint.measureText(id)
        val fontMetrics = paint.fontMetrics
        val textHeight = -fontMetrics.ascent + fontMetrics.descent
        
        val paddingX = smallTextSize * 0.6f
        val paddingY = smallTextSize * 0.1f
        
        val cardWidth = textWidth + paddingX * 2
        val cardHeight = textHeight + paddingY * 2
        
        // Calculate position (Superscript style)
        // Restore original size to get main text ascent
        paint.textSize = originalTextSize
        val originalAscent = paint.ascent()
        paint.textSize = smallTextSize
        
        // Align top of card roughly with top of main text
        // y is baseline. originalAscent is negative.
        // Top of main text is y + originalAscent.
        val rectTop = y + originalAscent * 0.85f // Move slightly down from very top
        
        val rect = RectF(
            x + 4f,
            rectTop,
            x + 4f + cardWidth,
            rectTop + cardHeight
        )
        
        // Draw background
        paint.color = textColor 
        paint.alpha = 20 // Light background
        paint.style = Paint.Style.FILL
        // Corner radius relative to size
        val cornerRadius = smallTextSize * 0.3f
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        
        // Draw text
        paint.color = textColor
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        val textX = rect.centerX() - textWidth / 2
        val textBaselineY = rect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2
        
        canvas.drawText(id, textX, textBaselineY, paint)
        
        // Restore paint
        paint.textSize = originalTextSize
        paint.color = originalColor
        paint.style = originalStyle
    }
}