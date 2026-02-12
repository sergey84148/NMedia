package ru.netology.nmedia.repository

import ru.netology.nmedia.dto.Post

interface PostRepository {
    fun getAll(): List<Post>
    suspend fun likeById(id: Long): Post
    fun save(post: Post): Post
    fun removeById(id: Long)
    fun shareById(id: Long)
}