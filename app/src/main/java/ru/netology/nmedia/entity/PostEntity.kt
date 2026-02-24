package ru.netology.nmedia.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.netology.nmedia.dto.Attachment
import ru.netology.nmedia.dto.Post

@Entity(tableName = "PostEntity")  // Явно указываем имя таблицы
data class PostEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val author: String = "",
    val authorAvatar: String? = null,
    val content: String = "",
    val published: String = "",
    var likes: Int = 0,
    var shares: Int = 0,
    val video: String? = null,
    val link: String = "",
    var likedByMe: Boolean = false
    // attachment исключен из БД
) {
    fun toDto() = Post(
        id = id,
        author = author,
        authorAvatar = authorAvatar,
        content = content,
        published = published,
        likes = likes,
        shares = shares,
        video = video,
        link = link,
        attachment = null,  // attachment не сохраняется
        likedByMe = likedByMe
    )

    companion object {
        fun fromDto(dto: Post) = PostEntity(
            id = dto.id,
            author = dto.author,
            authorAvatar = dto.authorAvatar,
            content = dto.content,
            published = dto.published,
            likes = dto.likes,
            shares = dto.shares,
            video = dto.video,
            link = dto.link,
            likedByMe = dto.likedByMe
        )
    }
}