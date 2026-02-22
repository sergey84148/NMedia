package ru.netology.nmedia.dto

import kotlinx.serialization.Serializable


@Serializable
data class Post(
    val id: Long,
    val author: String,
    val authorAvatar: String?,
    val content: String,
    val published: String,
    var likes: Int = 0,
    var shares: Int = 0,
    val video: String? = null,
    val link: String = "",
    var likedByMe: Boolean = false,
    val attachment: Attachment? = null
)
