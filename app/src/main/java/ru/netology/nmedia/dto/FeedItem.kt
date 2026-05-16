package ru.netology.nmedia.dto

import ru.netology.nmedia.enumeration.AttachmentType
import java.util.Date

sealed class FeedItem {
    abstract val id: Long
}

data class DateSeparator(
    override val id: Long,
    val title: String,
    val date: Date
) : FeedItem()

data class Ad(
    override val id: Long,
    val url: String,
    val image: String,
) : FeedItem()

data class Post(
    override val id: Long,
    val authorId: Long,
    val author: String,
    val authorAvatar: String,
    val content: String,
    val published: Long,
    val likedByMe: Boolean,
    val likes: Int = 0,
    val shares: Int = 0,
    val video: String? = null,
    val attachment: Attachment? = null,
    val ownedByMe: Boolean = false,
) : FeedItem()

data class Attachment(
    val url: String,
    val type: AttachmentType,
)