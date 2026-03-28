package ru.netology.nmedia.model

import ru.netology.nmedia.dto.Post

data class FeedModelState(
    val loading: Boolean = false,
    val error: Boolean = false,
    val refreshing: Boolean = false,
    val syncing: Boolean = false,
    val syncError: Boolean = false,
    val pendingPostsCount: Int = 0
)

data class FeedModel(
    val posts: List<Post> = emptyList()
) {
    val empty: Boolean
        get() = posts.isEmpty()
}