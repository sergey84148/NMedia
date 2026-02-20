package ru.netology.nmedia.dto

data class Post(
    val id: Long,
    val author: String,
    val authorAvatar: String?,
    val content: String,
    val published: String,
    var likes: Int,
    var shares: Int,
    val video: String? = null,
    val link: String,
    var likedByMe: Boolean
)