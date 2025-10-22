package com.glassous.aime.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glassous.aime.data.ChatMessage

@RunWith(AndroidJUnit4::class)
class MessageBubbleTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun setBubbleContent(md: String) {
        composeTestRule.setContent {
            val blurState = remember { mutableStateOf(false) }
            CompositionLocalProvider(LocalDialogBlurState provides blurState) {
                MessageBubble(
                    message = ChatMessage(
                        id = 1L,
                        conversationId = 1L,
                        isFromUser = false,
                        content = md,
                        timestamp = System.currentTimeMillis()
                    ),
                    onShowDetails = {}
                )
            }
        }
    }

    @Test
    fun longPress_showsDialog_onListMarkdown() {
        val md = """
            * item 1\n
            * item 2
        """.trimIndent()
        setBubbleContent(md)
        composeTestRule.onNodeWithTag("bubble-1").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("复制全文").assertExists()
    }

    @Test
    fun longPress_showsDialog_onCodeBlockMarkdown() {
        val md = """
            ```kotlin\n
            val x = 1\n
            println(x)
            ```
        """.trimIndent()
        setBubbleContent(md)
        composeTestRule.onNodeWithTag("bubble-1").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("复制全文").assertExists()
    }

    @Test
    fun longPress_showsDialog_onQuoteMarkdown() {
        val md = "> 引用示例\n\n一些内容"
        setBubbleContent(md)
        composeTestRule.onNodeWithTag("bubble-1").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("复制全文").assertExists()
    }
}