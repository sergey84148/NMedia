package ru.netology.nmedia.model

import ru.netology.nmedia.dto.Post

data class FeedModelState(
    val loading: Boolean = false,
    val error: Boolean = false,
    val refreshing: Boolean = false,
    val syncing: Boolean = false,        // НОВОЕ: идет синхронизация с сервером
    val syncError: Boolean = false,      // НОВОЕ: ошибка синхронизации
    val pendingPostsCount: Int = 0       // НОВОЕ: количество ожидающих синхронизации постов
)

data class FeedModel(
    val posts: List<Post> = emptyList()
) {
    // Вычисляемое поле - true если список постов пуст
    val empty: Boolean
        get() = posts.isEmpty()
}