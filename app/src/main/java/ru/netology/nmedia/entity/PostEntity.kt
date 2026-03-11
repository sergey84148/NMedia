package ru.netology.nmedia.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.enumeration.SyncState

@Entity(tableName = "PostEntity")
data class PostEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serverId: Long? = null,  // ID на сервере (null для новых постов)
    val author: String = "",
    val authorAvatar: String? = null,
    val content: String = "",
    val published: String = "",
    var likes: Int = 0,
    var shares: Int = 0,
    val video: String? = null,
    var likedByMe: Boolean = false,
    val syncState: SyncState = SyncState.SYNCED,  // состояние синхронизации
    val lastModified: Long = System.currentTimeMillis(),  // время последнего изменения
    val retryCount: Int = 0  // счетчик попыток
) {
    fun toDto(): Post = Post(
        id = serverId ?: 0L,  // для UI всегда используем 0 для новых постов
        author = author,
        authorAvatar = authorAvatar,
        content = content,
        published = published,
        likedByMe = likedByMe,
        likes = likes,
        shares = shares,
        video = video
    )

    companion object {
        fun fromDto(dto: Post, syncState: SyncState = SyncState.SYNCED): PostEntity = PostEntity(
            id = 0,  // всегда 0, auto-generate сгенерирует правильный ID
            serverId = dto.id.takeIf { it != 0L },  // сохраняем serverId только если не 0
            author = dto.author,
            authorAvatar = dto.authorAvatar,
            content = dto.content,
            published = dto.published,
            likes = dto.likes,
            shares = dto.shares,
            video = dto.video,
            likedByMe = dto.likedByMe,
            syncState = syncState,
            lastModified = System.currentTimeMillis()
        )
    }
}