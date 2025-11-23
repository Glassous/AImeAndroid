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
}
