package ru.netology.nmedia.dto

import ru.netology.nmedia.enumeration.AttachmentType

data class Post(
    val id: Long,
    val author: String,
    val authorId: Long,
    val authorAvatar: String = "",
    val content: String,
    val published: Long,
    val likedByMe: Boolean,
    val likes: Int = 0,
    val shares: Int = 0,
    val video: String? = null,
    val attachment: Attachment? = null,
    val ownedByMe: Boolean = false
)

data class Attachment(
    val url: String,
    val description: String? = null,
    val type: AttachmentType,
)