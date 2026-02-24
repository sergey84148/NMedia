package ru.netology.nmedia.dto

import kotlinx.serialization.Serializable
import ru.netology.nmedia.enumeration.AttachmentType

@Serializable
data class Post(
    val id: Long,
    val author: String,
    val content: String,
    val publishedAt: Long,
    val likesCount: Int = 0,
    val sharesCount: Int = 0,
    val isLikedByCurrentUser: Boolean = false,
    val attachment: Attachment? = null
)

@Serializable
data class Attachment(
    val url: String,
    val description: String?,
    val type: AttachmentType,
)