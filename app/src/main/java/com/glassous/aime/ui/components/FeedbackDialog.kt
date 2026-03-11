package com.glassous.aime.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.glassous.aime.BuildConfig
import com.glassous.aime.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Composable
fun FeedbackDialog(
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val client = remember { OkHttpClient() }
    
    val errorMessage = stringResource(R.string.feedback_error)
    val emptyMessage = stringResource(R.string.feedback_empty_message)

    AlertDialog(
        onDismissRequest = { if (!isSubmitting && !isSuccess) onDismiss() },
        title = { Text(stringResource(R.string.feedback)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.feedback_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.feedback_name)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    enabled = !isSubmitting && !isSuccess,
                    singleLine = true
                )

                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    label = { Text(stringResource(R.string.feedback_contact)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    enabled = !isSubmitting && !isSuccess,
                    singleLine = true
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(stringResource(R.string.feedback_message)) },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    enabled = !isSubmitting && !isSuccess,
                    maxLines = 10
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (message.isBlank()) {
                        scope.launch {
                            snackbarHostState.showSnackbar(emptyMessage)
                        }
                        return@Button
                    }
                    
                    isSubmitting = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            try {
                                val json = JSONObject().apply {
                                    put("access_key", BuildConfig.WEB3FORMS_ACCESS_KEY)
                                    put("name", name.ifBlank { "Anonymous AIme User" })
                                    put("contact_info", contact.ifBlank { "not provided" })
                                    put("message", message)
                                    put("subject", "AIme Feedback - ${BuildConfig.VERSION_NAME}")
                                    put("from_name", "AIme App")
                                }
                                
                                val requestBody = json.toString().toRequestBody("application/json".toMediaType())
                                val request = Request.Builder()
                                    .url("https://api.web3forms.com/submit")
                                    .addHeader("Accept", "application/json")
                                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
                                    .addHeader("Origin", "https://aime.app")
                                    .addHeader("Referer", "https://aime.app/")
                                    .post(requestBody)
                                    .build()
                                
                                client.newCall(request).execute().use { response ->
                                    response.isSuccessful
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                false
                            }
                        }
                        
                        isSubmitting = false
                        if (result) {
                            isSuccess = true
                            delay(2000)
                            onDismiss()
                        } else {
                            snackbarHostState.showSnackbar(errorMessage)
                        }
                    }
                },
                enabled = !isSubmitting && !isSuccess,
                colors = if (isSuccess) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) else ButtonDefaults.buttonColors()
            ) {
                AnimatedContent(
                    targetState = if (isSubmitting) "loading" else if (isSuccess) "success" else "idle",
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "ButtonContent"
                ) { state ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (state) {
                            "loading" -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.feedback_submitting))
                            }
                            "success" -> {
                                Text("感谢反馈 🎉")
                            }
                            else -> {
                                Text(stringResource(R.string.feedback_submit))
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting && !isSuccess
            ) {
                Text("取消")
            }
        }
    )
}


