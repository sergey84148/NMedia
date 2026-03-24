package ru.netology.nmedia.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.enumeration.SyncState

@Entity(tableName = "PostEntity")
data class PostEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serverId: Long? = null,
    val author: String = "",
    val authorAvatar: String = "",  // 👈 ИЗМЕНЕНО: теперь String, не nullable
    val content: String = "",
    val published: Long = 0,
    var likes: Int = 0,
    var shares: Int = 0,
    val video: String? = null,
    var likedByMe: Boolean = false,
    val syncState: SyncState = SyncState.SYNCED,
    val lastModified: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val isNew: Boolean = false
) {
    fun toDto(): Post = Post(
        id = serverId ?: 0L,
        author = author,
        authorAvatar = authorAvatar,  // Теперь String
        content = content,
        published = published,
        likedByMe = likedByMe,
        likes = likes,
        shares = shares,
        video = video,
        attachment = null
    )

    companion object {
        fun fromDto(dto: Post, syncState: SyncState = SyncState.SYNCED, isNew: Boolean = false): PostEntity = PostEntity(
            id = dto.id, // <----
            serverId = dto.id.takeIf { it != 0L },
            author = dto.author,
            authorAvatar = dto.authorAvatar ?: "",
            content = dto.content,
            published = dto.published,
            likes = dto.likes,
            shares = dto.shares,
            video = dto.video,
            likedByMe = dto.likedByMe,
            syncState = syncState,
            lastModified = System.currentTimeMillis(),
            isNew = isNew
        )
    }
}