package com.glassous.aime.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.glassous.aime.BuildConfig

data class AccountSession(
    @SerializedName("user_id") val userId: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("session_token") val sessionToken: String?
)

data class RpcAccountResponse(
    @SerializedName("ok") val ok: Boolean?,
    @SerializedName("message") val message: String?,
    @SerializedName("user_id") val userId: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("session_token") val sessionToken: String?
)

class SupabaseAuthService(
    private val client: OkHttpClient = OkHttpClient()
) {
    private val gson = Gson()

    data class RpcSyncResponse(
        @SerializedName("ok") val ok: Boolean?,
        @SerializedName("message") val message: String?
    )

    suspend fun signUp(email: String, password: String): Triple<Boolean, AccountSession?, String> {
        val url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/accounts_register"
        val json = gson.toJson(mapOf("p_email" to email, "p_password" to password))
        val body = json.toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(url)
            .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .addHeader("Accept", "application/json")
            .addHeader("Prefer", "return=representation")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        val resp = client.newCall(req).execute()
        val raw = resp.body?.string() ?: ""
        resp.close()
        val parsed = try { gson.fromJson(raw, RpcAccountResponse::class.java) } catch (_: Exception) { null }
        val ok = resp.isSuccessful && (parsed?.ok == true) && !parsed.sessionToken.isNullOrBlank() && !parsed.userId.isNullOrBlank()
        val session = if (ok) AccountSession(parsed?.userId, parsed?.email, parsed?.sessionToken) else null
        val msg = parsed?.message ?: if (ok) "注册并登录成功" else if (raw.isNotBlank()) raw else "注册失败"
        return Triple(ok, session, msg)
    }

    suspend fun login(email: String, password: String): Triple<Boolean, AccountSession?, String> {
        val url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/accounts_login"
        val json = gson.toJson(mapOf("p_email" to email, "p_password" to password))
        val body = json.toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(url)
            .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .addHeader("Accept", "application/json")
            .addHeader("Prefer", "return=representation")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        val resp = client.newCall(req).execute()
        val raw = resp.body?.string() ?: ""
        resp.close()
        val parsed = try { gson.fromJson(raw, RpcAccountResponse::class.java) } catch (_: Exception) { null }
        val ok = resp.isSuccessful && (parsed?.ok == true) && !parsed.sessionToken.isNullOrBlank() && !parsed.userId.isNullOrBlank()
        val session = if (ok) AccountSession(parsed?.userId, parsed?.email, parsed?.sessionToken) else null
        val msg = parsed?.message ?: if (ok) "登录成功" else if (raw.isNotBlank()) raw else "登录失败"
        return Triple(ok, session, msg)
    }

    suspend fun recover(email: String): Pair<Boolean, String> {
        val url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/accounts_recover"
        val json = gson.toJson(mapOf("p_email" to email))
        val body = json.toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(url)
            .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .addHeader("Accept", "application/json")
            .addHeader("Prefer", "return=representation")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        val resp = client.newCall(req).execute()
        val raw = resp.body?.string() ?: ""
        resp.close()
        val parsed = try { gson.fromJson(raw, RpcAccountResponse::class.java) } catch (_: Exception) { null }
        val ok = resp.isSuccessful && (parsed?.ok == true)
        val msg = parsed?.message ?: if (ok) "已发送重置邮件，请查收" else if (raw.isNotBlank()) raw else "发送失败"
        return ok to msg
    }

    suspend fun resetPassword(email: String, newPassword: String): Pair<Boolean, String> {
        val url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/accounts_reset_password"
        val json = gson.toJson(mapOf("p_email" to email, "p_new_password" to newPassword))
        val body = json.toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(url)
            .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .addHeader("Accept", "application/json")
            .addHeader("Prefer", "return=representation")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        val resp = client.newCall(req).execute()
        val raw = resp.body?.string() ?: ""
        resp.close()
        val parsed = try { gson.fromJson(raw, RpcAccountResponse::class.java) } catch (_: Exception) { null }
        val ok = resp.isSuccessful && (parsed?.ok == true)
        val msg = parsed?.message ?: if (ok) "密码已重置" else if (raw.isNotBlank()) raw else "重置失败"
        return ok to msg
    }

    // 云端同步：上传备份（增量由服务端基于唯一键处理）
    suspend fun uploadBackup(
        token: String,
        backupDataJson: String,
        syncHistory: Boolean = true,
        syncModelConfig: Boolean = true,
        syncSelectedModel: Boolean = true,
        syncApiKey: Boolean = false
    ): Pair<Boolean, String> {
        val url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/sync_upload_backup"
        val payload = buildString {
            append('{')
            append("\"p_token\":")
            append(gson.toJson(token))
            append(',')
            append("\"p_data\":")
            append(backupDataJson)
            append(',')
            append("\"p_sync_history\":")
            append(if (syncHistory) "true" else "false")
            append(',')
            append("\"p_sync_model_config\":")
            append(if (syncModelConfig) "true" else "false")
            append(',')
            append("\"p_sync_selected_model\":")
            append(if (syncSelectedModel) "true" else "false")
            append(',')
            append("\"p_sync_api_key\":")
            append(if (syncApiKey) "true" else "false")
            append('}')
        }
        val body = payload.toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(url)
            .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .addHeader("Accept", "application/json")
            .addHeader("Prefer", "return=representation")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        val resp = client.newCall(req).execute()
        val raw = resp.body?.string() ?: ""
        resp.close()
        val parsed = try { gson.fromJson(raw, RpcSyncResponse::class.java) } catch (_: Exception) { null }
        val ok = resp.isSuccessful && (parsed?.ok != false)
        val msg = when {
            ok -> "上传成功"
            parsed?.message != null -> "上传失败：" + parsed.message
            raw.isNotBlank() -> "上传失败：" + raw.take(500)
            else -> "上传失败：未知错误"
        }
        return ok to msg
    }

    // 云端同步：下载备份（完整数据，用于本地增量合并）
    suspend fun downloadBackup(token: String): Triple<Boolean, String, String?> {
        val url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/sync_download_backup"
        val payload = gson.toJson(mapOf("p_token" to token))
        val body = payload.toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(url)
            .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .addHeader("Accept", "application/json")
            .addHeader("Prefer", "return=representation")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        val resp = client.newCall(req).execute()
        val raw = resp.body?.string() ?: ""
        val trimmed = raw.trim()
        var normalizedStr = trimmed
        var ok = resp.isSuccessful
        var msg = "下载成功"
        try {
            if (normalizedStr.startsWith("[")) {
                val arr = com.google.gson.JsonParser.parseString(normalizedStr).asJsonArray
                normalizedStr = if (arr.size() > 0) arr[0].toString() else ""
            }
            val element = com.google.gson.JsonParser.parseString(normalizedStr)
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                if (obj.has("ok")) {
                    val okFlag = obj.get("ok").asBoolean
                    val message = if (obj.has("message")) obj.get("message").asString else null
                    ok = ok && okFlag
                    msg = message ?: if (ok) "下载成功" else "下载失败"
                    if (!ok) {
                        resp.close()
                        return Triple(false, msg, null)
                    }
                }
                fun toLongField(o: com.google.gson.JsonObject, field: String) {
                    if (o.has(field) && !o.get(field).isJsonNull) {
                        val v = o.get(field)
                        try {
                            val num = if (v.isJsonPrimitive && v.asJsonPrimitive.isNumber) v.asJsonPrimitive.asDouble else v.asString.toDouble()
                            o.addProperty(field, num.toLong())
                        } catch (_: Exception) {}
                    }
                }
                toLongField(obj, "exportedAt")
                if (obj.has("conversations") && obj.get("conversations").isJsonArray) {
                    val convs = obj.getAsJsonArray("conversations")
                    for (convElem in convs) {
                        if (convElem.isJsonObject) {
                            val convObj = convElem.asJsonObject
                            toLongField(convObj, "lastMessageTime")
                            if (convObj.has("messages") && convObj.get("messages").isJsonArray) {
                                val msgsArr = convObj.getAsJsonArray("messages")
                                for (mElem in msgsArr) {
                                    if (mElem.isJsonObject) {
                                        val mObj = mElem.asJsonObject
                                        toLongField(mObj, "timestamp")
                                    }
                                }
                            }
                        }
                    }
                }
                normalizedStr = obj.toString()
            }
        } catch (_: Exception) {
            ok = ok && normalizedStr.isNotBlank()
            msg = if (ok) "下载成功" else "下载失败"
        }
        resp.close()
        val backupJson: String? = if (ok && normalizedStr.isNotBlank()) normalizedStr else null
        return Triple(ok, msg, backupJson)
    }
}
