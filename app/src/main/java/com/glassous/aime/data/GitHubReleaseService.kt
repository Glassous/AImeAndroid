package com.glassous.aime.data

import com.glassous.aime.data.model.GitHubRelease
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * GitHub Release API接口
 */
interface GitHubReleaseApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease
}

/**
 * GitHub Release服务类
 */
class GitHubReleaseService {
    private val api: GitHubReleaseApi
    
    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        api = retrofit.create(GitHubReleaseApi::class.java)
    }
    
    /**
     * 获取最新Release信息
     */
    suspend fun getLatestRelease(owner: String, repo: String): Result<GitHubRelease> {
        return try {
            val release = api.getLatestRelease(owner, repo)
            Result.success(release)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 比较版本号
     * @return 如果version1 > version2返回正数，相等返回0，小于返回负数
     */
    fun compareVersions(version1: String, version2: String): Int {
        // 移除v前缀
        val v1 = version1.removePrefix("v")
        val v2 = version2.removePrefix("v")
        
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        
        val maxLength = maxOf(parts1.size, parts2.size)
        
        for (i in 0 until maxLength) {
            val p1 = parts1.getOrNull(i) ?: 0
            val p2 = parts2.getOrNull(i) ?: 0
            
            if (p1 != p2) {
                return p1 - p2
            }
        }
        
        return 0
    }
    
    /**
     * 从Release资产中查找APK下载链接
     */
    fun findApkDownloadUrl(release: GitHubRelease, namePattern: String = "AIme-v"): String? {
        return release.assets
            .firstOrNull { asset ->
                asset.name.startsWith(namePattern) && asset.name.endsWith(".apk")
            }?.downloadUrl
    }
}