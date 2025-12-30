package ru.netology.nmedia.dto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Post(
    val id: Long,
    val author: String,
    val content: String,
    val published: String,
    var likes: Int,
    var shares: Int,
    val video: String? = null,
    val link: String,
    var likedByMe: Boolean
) : Parcelable