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

data class RpcQuestionResponse(
    @SerializedName("ok") val ok: Boolean?,
    @SerializedName("message") val message: String?,
    @SerializedName("question") val question: String?
)

class SupabaseAuthService(
    private val client: OkHttpClient = OkHttpClient()
) {
    private val gson = Gson()

    data class RpcSyncResponse(
        @SerializedName("ok") val ok: Boolean?,
        @SerializedName("message") val message: String?
    )

    // 更新：注册时传入安全问题和答案
    suspend fun signUp(email: String, password: String, question: String = "", answer: String = ""): Triple<Boolean, AccountSession?, String> {
        val url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/accounts_register"
        val map = mutableMapOf("p_email" to email, "p_password" to password)
        if (question.isNotEmpty()) map["p_question"] = question
        if (answer.isNotEmpty()) map["p_answer"] = answer

        val json = gson.toJson(map)
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

    // 新增：获取安全问题
    suspend fun getSecurityQuestion(email: String): Pair<Boolean, String> {
        val url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/accounts_get_security_question"
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

        val parsed = try { gson.fromJson(raw, RpcQuestionResponse::class.java) } catch (_: Exception) { null }
        val ok = resp.isSuccessful && (parsed?.ok == true)
        val result = if (ok) (parsed?.question ?: "") else (parsed?.message ?: "获取问题失败")
        return ok to result
    }

    // 新增：通过回答安全问题重置密码
    suspend fun resetPasswordWithAnswer(email: String, answer: String, newPassword: String): Pair<Boolean, String> {
        val url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/accounts_reset_password_with_answer"
        val json = gson.toJson(mapOf(
            "p_email" to email,
            "p_answer" to answer,
            "p_new_password" to newPassword
        ))
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

    // 新增：验证密码并更新安全问题（用于老用户设置或修改）
    suspend fun updateSecurityQuestion(email: String, password: String, question: String, answer: String): Pair<Boolean, String> {
        val url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/accounts_update_security_question"
        val json = gson.toJson(mapOf(
            "p_email" to email,
            "p_password" to password,
            "p_new_question" to question,
            "p_new_answer" to answer
        ))
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
        val msg = parsed?.message ?: if (ok) "安全问题已设置" else if (raw.isNotBlank()) raw else "设置失败"
        return ok to msg
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

    // 云端同步相关方法保持不变
    suspend fun uploadBackup(
        token: String,
        backupDataJson: String,
        syncHistory: Boolean = true,
        syncModelConfig: Boolean = true,
        syncSelectedModel: Boolean = true,
        syncApiKey: Boolean = false
    ): Pair<Boolean, String> {
        android.util.Log.d("SupabaseAuthService", "Starting uploadBackup with token: ${token.take(10)}...")
        val url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/sync_upload_backup"
        android.util.Log.d("SupabaseAuthService", "Upload URL: $url")
        
        // 解析 backupDataJson 为 JsonElement 对象
        val backupDataJsonElement = gson.fromJson(backupDataJson, com.google.gson.JsonElement::class.java)
        
        // 使用 Map 构建 JSON payload，确保所有值都被正确序列化
        val payloadMap = mapOf(
            "p_token" to token,
            "p_data" to backupDataJsonElement,
            "p_sync_history" to syncHistory,
            "p_sync_model_config" to syncModelConfig,
            "p_sync_selected_model" to syncSelectedModel,
            "p_sync_api_key" to syncApiKey
        )
        
        // 使用 Gson 将整个 Map 转换为 JSON 字符串
        val payload = gson.toJson(payloadMap)
        
        android.util.Log.d("SupabaseAuthService", "Upload payload length: ${payload.length}, syncHistory=$syncHistory, syncModelConfig=$syncModelConfig")
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
        android.util.Log.d("SupabaseAuthService", "Upload response code: ${resp.code}, raw response: $raw")
        resp.close()
        val parsed = try { gson.fromJson(raw, RpcSyncResponse::class.java) } catch (_: Exception) { null }
        val ok = resp.isSuccessful && (parsed?.ok != false)
        val msg = when {
            ok -> "上传成功"
            parsed?.message != null -> "上传失败：" + parsed.message
            raw.isNotBlank() -> "上传失败：" + raw.take(500)
            else -> "上传失败：未知错误"
        }
        android.util.Log.d("SupabaseAuthService", "Upload result: ok=$ok, msg=$msg")
        return ok to msg
    }

    suspend fun downloadBackup(token: String): Triple<Boolean, String, String?> {
        android.util.Log.d("SupabaseAuthService", "Starting downloadBackup with token: ${token.take(10)}...")
        val url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/sync_download_backup"
        android.util.Log.d("SupabaseAuthService", "Download URL: $url")
        val payload = gson.toJson(mapOf("p_token" to token))
        android.util.Log.d("SupabaseAuthService", "Download payload: $payload")
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
        android.util.Log.d("SupabaseAuthService", "Download response code: ${resp.code}, raw response: $raw")
        val trimmed = raw.trim()
        var normalizedStr = trimmed
        var ok = resp.isSuccessful
        var msg = "下载成功"
        try {
            if (normalizedStr.startsWith("[")) {
                val arr = com.google.gson.JsonParser.parseString(normalizedStr).asJsonArray
                normalizedStr = if (arr.size() > 0) arr[0].toString() else ""
                android.util.Log.d("SupabaseAuthService", "Parsed array response, normalized: $normalizedStr")
            }
            val element = com.google.gson.JsonParser.parseString(normalizedStr)
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                if (obj.has("ok")) {
                    val okFlag = obj.get("ok").asBoolean
                    val message = if (obj.has("message")) obj.get("message").asString else null
                    ok = ok && okFlag
                    msg = message ?: if (ok) "下载成功" else "下载失败"
                    android.util.Log.d("SupabaseAuthService", "Parsed response object: ok=$okFlag, message=$message")
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
                    android.util.Log.d("SupabaseAuthService", "Processing ${convs.size()} conversations")
                    for (convElem in convs) {
                        if (convElem.isJsonObject) {
                            val convObj = convElem.asJsonObject
                            toLongField(convObj, "lastMessageTime")
                            if (convObj.has("messages") && convObj.get("messages").isJsonArray) {
                                val msgsArr = convObj.getAsJsonArray("messages")
                                android.util.Log.d("SupabaseAuthService", "Processing ${msgsArr.size()} messages in conversation")
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
        } catch (e: Exception) {
            android.util.Log.e("SupabaseAuthService", "Error parsing download response", e)
            ok = ok && normalizedStr.isNotBlank()
            msg = if (ok) "下载成功" else "下载失败"
        }
        resp.close()
        val backupJson: String? = if (ok && normalizedStr.isNotBlank()) normalizedStr else null
        android.util.Log.d("SupabaseAuthService", "Download result: ok=$ok, msg=$msg, hasData=${backupJson != null}")
        return Triple(ok, msg, backupJson)
    }
}