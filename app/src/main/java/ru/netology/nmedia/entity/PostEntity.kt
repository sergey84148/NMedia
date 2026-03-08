package ru.netology.nmedia.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.enumeration.SyncState

@Entity(tableName = "PostEntity")
data class PostEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serverId: Long? = null,  // НОВОЕ: ID на сервере
    val author: String = "",
    val authorAvatar: String? = null,
    val content: String = "",
    val published: String = "",
    var likes: Int = 0,
    var shares: Int = 0,
    val video: String? = null,
    val link: String = "",
    var likedByMe: Boolean = false,
    val syncState: SyncState = SyncState.SYNCED,  // НОВОЕ: состояние синхронизации
    val lastModified: Long = System.currentTimeMillis(),  // НОВОЕ: время последнего изменения
    val retryCount: Int = 0  // НОВОЕ: счетчик попыток
) {
    fun toDto() = Post(
        id = serverId ?: id,  // ИЗМЕНЕНО: используем serverId если есть
        author = author,
        authorAvatar = authorAvatar,
        content = content,
        published = published,
        likedByMe = likedByMe,
        shares = shares,
        video = video,
        likes = likes,
    )

    companion object {
        fun fromDto(dto: Post, syncState: SyncState = SyncState.SYNCED): PostEntity = PostEntity(
            id = if (dto.id == 0L) 0 else dto.id,
            serverId = if (dto.id != 0L) dto.id else null,  // НОВОЕ: сохраняем serverId
            author = dto.author,
            authorAvatar = dto.authorAvatar,
            content = dto.content,
            published = dto.published,
            likes = dto.likes,
            shares = dto.shares,
            video = dto.video,
            likedByMe = dto.likedByMe,
            syncState = syncState,  // НОВОЕ
            lastModified = System.currentTimeMillis()  // НОВОЕ
        )
    }
}