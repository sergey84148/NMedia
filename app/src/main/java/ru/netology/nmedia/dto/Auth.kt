package ru.netology.nmedia.dto

import okhttp3.MultipartBody

data class LoginRequest(
    val login: String,
    val pass: String
)

data class RegisterRequest(
    val login: String,
    val pass: String,
    val name: String,
    val file: MultipartBody.Part? = null
)

data class AuthResponse(
    val id: Long,
    val token: String,
    val avatar: String? = null
)