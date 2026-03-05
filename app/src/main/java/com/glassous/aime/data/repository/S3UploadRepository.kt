package com.glassous.aime.data.repository

import android.content.Context
import android.webkit.MimeTypeMap
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.fromFile
import aws.smithy.kotlin.runtime.net.url.Url
import com.glassous.aime.data.preferences.S3Preferences
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class S3UploadRepository(
    private val context: Context,
    private val s3Preferences: S3Preferences
) {
    suspend fun uploadFile(
        file: File,
        onProgress: (Float) -> Unit
    ): Result<String> {
        return try {
            val endpoint = s3Preferences.s3Endpoint.first()
            val region = s3Preferences.s3Region.first().ifBlank { "us-east-1" }
            val accessKey = s3Preferences.s3AccessKey.first()
            val secretKey = s3Preferences.s3SecretKey.first()
            val bucketName = s3Preferences.s3BucketName.first()

            if (endpoint.isBlank() || accessKey.isBlank() || secretKey.isBlank() || bucketName.isBlank()) {
                return Result.failure(IllegalStateException("S3 configuration incomplete"))
            }

            val client = S3Client {
                this.region = region
                this.endpointUrl = Url.parse(endpoint)
                this.credentialsProvider = StaticCredentialsProvider {
                    this.accessKeyId = accessKey
                    this.secretAccessKey = secretKey
                }
                this.forcePathStyle = true
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

            val request = PutObjectRequest {
                bucket = bucketName
                key = objectKey
                body = ByteStream.fromFile(file)
                contentType = mimeType
            }

            client.putObject(request)
            onProgress(1.0f)

            // Construct URL
            val cleanEndpoint = endpoint.trimEnd('/')
            // Assuming path style access as we set forcePathStyle = true
            val url = "$cleanEndpoint/$bucketName/$objectKey"
            
            Result.success(url)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
