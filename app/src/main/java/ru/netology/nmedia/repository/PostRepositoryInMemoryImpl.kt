package ru.netology.nmedia.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ru.netology.nmedia.Post

class PostRepositoryInMemoryImpl : PostRepository {

    // Текущий пост (можно заменить на список для поддержки нескольких постов)
    private var post = Post(
        id = 1,
        author = "Нетология. Университет интернет-профессий будущего",
        content = "Привет, это новая Нетология! Когда-то Нетология начиналась с интенсивов по онлайн-маркетингу. Затем появились курсы по дизайну, разработке, аналитике и управлению. Мы растём сами и помогаем расти студентам: от новичков до уверенных профессионалов. Но самое важное остаётся с нами: мы верим, что в каждом уже есть сила, которая заставляет хотеть больше, целиться выше, бежать быстрее. Наша миссия — помочь встать на путь роста и начать цепочку перемен → http://netolo.gy/fyb",
        published = "21 мая в 18:36",
        likes = 10,
        shares = 999,
        views = 0,
        likedByMe = false,
    )

    // MutableLiveData для трансляции изменений
    private val _data = MutableLiveData(post)
    override val data: LiveData<Post> = _data

    // Получение поста (уже реализовано)
    override fun get(): LiveData<Post> = data

    // Лайкнуть/снять лайк
    override fun like() {
        post = post.copy(
            likedByMe = !post.likedByMe,
            likes = if (post.likedByMe) post.likes - 1 else post.likes + 1
        )
        _data.value = post
    }

    // Поделиться постом (добавить +1 к shares)
    override fun share() {
        post = post.copy(shares = post.shares + 1)
        _data.value = post
    }

    // Обновить контент поста (опционально)
    override fun updateContent(newContent: String) {
        post = post.copy(content = newContent)
        _data.value = post
    }

    // Увеличить просмотры (например, при открытии поста)
    override fun increaseViews() {
        post = post.copy(views = post.views + 1)
        _data.value = post
    }
}
