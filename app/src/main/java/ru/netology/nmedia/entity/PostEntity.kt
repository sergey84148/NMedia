package ru.netology.nmedia.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.netology.nmedia.dto.Attachment
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.enumeration.AttachmentType
import ru.netology.nmedia.enumeration.SyncState

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val author: String = "",
    val authorId: Long = 0,
    val authorAvatar: String? = null,
    val content: String = "",
    val published: Long = 0,
    var likes: Int = 0,
    var shares: Int = 0,
    val video: String? = null,
    var likedByMe: Boolean = false,
    val syncState: SyncState = SyncState.SYNCED,
    val lastModified: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val isNew: Boolean = false,
    val attachmentUrl: String? = null,
    val attachmentDescription: String? = null,
    val attachmentType: AttachmentType? = null
) {
    fun toDto(): Post = Post(
        id = id,
        author = author,
        authorId = authorId,
        authorAvatar = authorAvatar ?: "",
        content = content,
        published = published,
        likedByMe = likedByMe,
        likes = likes,
        shares = shares,
        video = video,
        attachment = if (attachmentUrl != null && attachmentType != null) {
            Attachment(
                url = attachmentUrl,
                description = attachmentDescription,
                type = attachmentType
            )
        } else null
    )

    companion object {
        fun fromDto(
            dto: Post,
            syncState: SyncState = SyncState.SYNCED,
            isNew: Boolean = false
        ): PostEntity = PostEntity(
            id = dto.id,
            author = dto.author,
            authorId = dto.authorId,
            authorAvatar = dto.authorAvatar,
            content = dto.content,
            published = dto.published,
            likes = dto.likes,
            shares = dto.shares,
            video = dto.video,
            likedByMe = dto.likedByMe,
            syncState = syncState,
            lastModified = System.currentTimeMillis(),
            isNew = isNew,
            attachmentUrl = dto.attachment?.url,
            attachmentDescription = dto.attachment?.description,
            attachmentType = dto.attachment?.type
        )

        fun updateFromDto(
            existingEntity: PostEntity,
            dto: Post,
            syncState: SyncState = SyncState.SYNCED,
            isNew: Boolean = false
        ): PostEntity = existingEntity.copy(
            id = dto.id,
            author = dto.author,
            authorId = dto.authorId,
            authorAvatar = dto.authorAvatar,
            content = dto.content,
            published = dto.published,
            likes = dto.likes,
            shares = dto.shares,
            video = dto.video,
            likedByMe = dto.likedByMe,
            syncState = syncState,
            lastModified = System.currentTimeMillis(),
            isNew = isNew,
            attachmentUrl = dto.attachment?.url,
            attachmentDescription = dto.attachment?.description,
            attachmentType = dto.attachment?.type
        )
    }
}

// Функции расширения для преобразования списков
fun List<PostEntity>.toDto(): List<Post> = map(PostEntity::toDto)

fun List<Post>.toEntity(): List<PostEntity> = map { PostEntity.fromDto(it) }