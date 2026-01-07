package ru.netology.nmedia.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.netology.nmedia.dto.Post

@Entity
data class PostEntity constructor(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val author: String = "",
    val content: String = "",
    val published: String = "",
    var likes: Int = 0,
    var shares: Int = 0,
    val video: String? = null,
    val link: String = "",
    var likedByMe: Boolean = false
) {
    fun toDto() = Post(id, author, content, published, likes, shares, video, link, likedByMe)

    companion object {
        fun fromDto(dto: Post) =
            PostEntity(dto.id, dto.author, dto.content, dto.published, dto.likes, dto.shares, dto.video, dto.link, dto.likedByMe)
    }
}



