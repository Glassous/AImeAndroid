package com.glassous.aime.data.model

import com.google.gson.annotations.SerializedName

/**
 * GitHub Release API响应数据模型
 */
data class GitHubRelease(
    @SerializedName("tag_name")
    val tagName: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("body")
    val body: String?,
    
    @SerializedName("html_url")
    val htmlUrl: String,
    
    @SerializedName("published_at")
    val publishedAt: String,
    
    @SerializedName("assets")
    val assets: List<ReleaseAsset>
)

/**
 * Release资产（APK文件等）
 */
data class ReleaseAsset(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("browser_download_url")
    val downloadUrl: String,
    
    @SerializedName("size")
    val size: Long,
    
    @SerializedName("content_type")
    val contentType: String
)

/**
 * 版本更新信息
 */
data class VersionUpdateInfo(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val downloadUrl: String?,
    val releaseNotes: String?,
    val releaseUrl: String?
)