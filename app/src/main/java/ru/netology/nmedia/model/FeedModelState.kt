package ru.netology.nmedia.model

import androidx.paging.PagingData
import ru.netology.nmedia.dto.Post
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class FeedModelState(
    val loading: Boolean = false,
    val error: Boolean = false,
    val refreshing: Boolean = false,
    val syncing: Boolean = false,
    val syncError: Boolean = false,
    val pendingPostsCount: Int = 0
)

data class FeedModel(
    val posts: Flow<PagingData<Post>> = emptyFlow(),
    val empty: Boolean = false
)