package com.glassous.aime.data.repository

import android.content.Context
import android.webkit.MimeTypeMap
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteStream
import aws.smithy.kotlin.runtime.net.url.Url
import com.glassous.aime.data.preferences.S3Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class S3UploadRepository(
    private val context: Context,
    private val s3Preferences: S3Preferences
) {
    private fun normalizeEndpoint(rawEndpoint: String): String {
        val trimmed = rawEndpoint.trim().trimEnd('/')
        return if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    suspend fun uploadFile(
        file: File,
        onProgress: (Float) -> Unit
    ): Result<String> = coroutineScope {
        try {
            val endpoint = s3Preferences.s3Endpoint.first()
            val region = s3Preferences.s3Region.first().ifBlank { "us-east-1" }
            val accessKey = s3Preferences.s3AccessKey.first()
            val secretKey = s3Preferences.s3SecretKey.first()
            val bucketName = s3Preferences.s3BucketName.first()
            val forcePathStyle = s3Preferences.s3ForcePathStyle.first()
            val normalizedEndpoint = normalizeEndpoint(endpoint)

            if (endpoint.isBlank() || accessKey.isBlank() || secretKey.isBlank() || bucketName.isBlank()) {
                return@coroutineScope Result.failure(IllegalStateException("S3 configuration incomplete"))
            }

            val client = S3Client {
                this.region = region
                this.endpointUrl = Url.parse(normalizedEndpoint)
                this.credentialsProvider = StaticCredentialsProvider {
                    this.accessKeyId = accessKey
                    this.secretAccessKey = secretKey
                }
                this.forcePathStyle = forcePathStyle
            }

            val extension = file.extension
            val nameWithoutExt = file.nameWithoutExtension
            val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            // Replace : with _ if needed? No, user asked for HH:MM:SS. 
            // But : is invalid in Windows filenames. S3 keys allow it.
            // We are generating S3 key, not local file.
            val objectKey = "${nameWithoutExt}${timestamp}.${extension}"

            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"

            onProgress(0f) // Initial state

            val fileFlow = flow {
                file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var totalRead = 0L
                    val totalSize = file.length()
                    while (input.read(buffer).also { read = it } != -1) {
                        emit(buffer.copyOf(read))
                        totalRead += read
                        if (totalSize > 0) {
                            onProgress(totalRead.toFloat() / totalSize)
                        }
                    }
                }
            }

            val request = PutObjectRequest {
                bucket = bucketName
                key = objectKey
                body = fileFlow.toByteStream(this@coroutineScope, file.length())
                contentType = mimeType
            }

            client.putObject(request)
            onProgress(1.0f)

            // Construct URL
            val cleanEndpoint = normalizedEndpoint.trimEnd('/')
            val url = if (forcePathStyle) {
                // Path style: endpoint/bucket/key
                "$cleanEndpoint/$bucketName/$objectKey"
            } else {
                // Virtual-hosted style: bucket.endpoint/key
                // Need to extract protocol and host
                val s3Url = Url.parse(cleanEndpoint)
                val protocol = s3Url.scheme.protocolName
                val host = s3Url.host.toString()
                "$protocol://$bucketName.$host/$objectKey"
            }
            
            Result.success(url)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
