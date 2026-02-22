package ru.netology.nmedia.dto

import kotlinx.serialization.Serializable

@Serializable
data class Attachment(
    val url: String,
    val description: String? = null,
    val type: String // например, "IMAGE"
)
