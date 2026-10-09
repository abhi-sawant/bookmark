package com.bookmark.account.data

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val deviceName: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    val deviceName: String,
)

@Serializable
data class AuthResponse(
    val userId: Int,
    val deviceId: Int,
    val token: String,
)

@Serializable
data class ForgotPasswordRequest(
    val email: String,
)

@Serializable
data class ForgotPasswordResponse(
    val ok: Boolean,
    val message: String? = null,
)

@Serializable
data class ApiErrorBody(
    val error: ApiErrorDetail,
)

@Serializable
data class ApiErrorDetail(
    val code: String,
    val message: String? = null,
)
