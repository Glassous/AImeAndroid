package com.glassous.aime.data.model

import kotlinx.serialization.Serializable

/**
 * 用户资料（全部字段均为可选）
 */
@Serializable
data class UserProfile(
    val nickname: String? = null,
    val city: String? = null,
    val preferredLanguage: String? = null,
    val age: Int? = null,
    val email: String? = null,
    val phone: String? = null,
    val gender: String? = null,
    val birthday: String? = null, // YYYY-MM-DD
    val occupation: String? = null,
    val company: String? = null,
    val timezone: String? = null,
    val website: String? = null,
    val address: String? = null,
    val hobbies: String? = null, // 逗号分隔
    val bio: String? = null,
    val avatarUrl: String? = null,
    val customFields: Map<String, String> = emptyMap()
)