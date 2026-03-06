package com.glassous.aime.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.glassous.aime.data.preferences.S3Preferences
import kotlinx.coroutines.launch

@Composable
fun S3ConfigDialog(
    s3Preferences: S3Preferences,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val endpoint by s3Preferences.s3Endpoint.collectAsState(initial = "")
    val region by s3Preferences.s3Region.collectAsState(initial = "")
    val accessKey by s3Preferences.s3AccessKey.collectAsState(initial = "")
    val secretKey by s3Preferences.s3SecretKey.collectAsState(initial = "")
    val bucketName by s3Preferences.s3BucketName.collectAsState(initial = "")
    val forcePathStyle by s3Preferences.s3ForcePathStyle.collectAsState(initial = false)

    var currentEndpoint by remember { mutableStateOf("") }
    var currentRegion by remember { mutableStateOf("") }
    var currentAccessKey by remember { mutableStateOf("") }
    var currentSecretKey by remember { mutableStateOf("") }
    var currentBucketName by remember { mutableStateOf("") }
    var currentForcePathStyle by remember { mutableStateOf(false) }

    // Initialize state when data is loaded
    LaunchedEffect(endpoint, region, accessKey, secretKey, bucketName, forcePathStyle) {
        if (currentEndpoint.isEmpty()) currentEndpoint = endpoint
        if (currentRegion.isEmpty()) currentRegion = region
        if (currentAccessKey.isEmpty()) currentAccessKey = accessKey
        if (currentSecretKey.isEmpty()) currentSecretKey = secretKey
        if (currentBucketName.isEmpty()) currentBucketName = bucketName
        currentForcePathStyle = forcePathStyle
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("S3 配置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = currentEndpoint,
                    onValueChange = { currentEndpoint = it },
                    label = { Text("Endpoint (必填)") },
                    placeholder = { Text("例如: https://s3.amazonaws.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = currentRegion,
                    onValueChange = { currentRegion = it },
                    label = { Text("Region (选填)") },
                    placeholder = { Text("例如: us-east-1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = currentAccessKey,
                    onValueChange = { currentAccessKey = it },
                    label = { Text("Access Key (必填)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = currentSecretKey,
                    onValueChange = { currentSecretKey = it },
                    label = { Text("Secret Key (必填)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = currentBucketName,
                    onValueChange = { currentBucketName = it },
                    label = { Text("Bucket Name (必填)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("强制路径样式 (Path Style)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "AWS S3 建议关闭，私有部署(如MinIO)建议开启",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = currentForcePathStyle,
                        onCheckedChange = { currentForcePathStyle = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        s3Preferences.setS3Endpoint(currentEndpoint.trim())
                        s3Preferences.setS3Region(currentRegion.trim())
                        s3Preferences.setS3AccessKey(currentAccessKey.trim())
                        s3Preferences.setS3SecretKey(currentSecretKey.trim())
                        s3Preferences.setS3BucketName(currentBucketName.trim())
                        s3Preferences.setS3ForcePathStyle(currentForcePathStyle)
                        onDismiss()
                    }
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
