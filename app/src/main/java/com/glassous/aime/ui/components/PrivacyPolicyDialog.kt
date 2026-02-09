package com.glassous.aime.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.glassous.aime.data.PrivacyPolicyData

@Composable
fun PrivacyPolicyDialog(
    onDismissRequest: () -> Unit,
    isFirstRun: Boolean = false,
    onAgree: () -> Unit = {},
    onDisagree: () -> Unit = {}
) {
    val properties = if (isFirstRun) {
        DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    } else {
        DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    }

    Dialog(
        onDismissRequest = {
            if (!isFirstRun) {
                onDismissRequest()
            }
        },
        properties = properties
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f) // Occupy most of the screen height
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxSize()
            ) {
                Text(
                    text = if (isFirstRun) "欢迎使用 AIme" else "隐私政策",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Content Scrollable Area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    MarkdownRenderer(
                        markdown = PrivacyPolicyData.MARKDOWN_CONTENT,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        textSizeSp = 14f,
                        onLongClick = {},
                        enableTables = true
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                if (isFirstRun) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDisagree) {
                            Text("不同意并退出")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = onAgree) {
                            Text("同意并继续")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismissRequest) {
                            Text("关闭")
                        }
                    }
                }
            }
        }
    }
}
