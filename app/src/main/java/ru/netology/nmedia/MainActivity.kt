package ru.netology.nmedia

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ru.netology.nmedia.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Создаем экземпляр модели Post
        val post = Post(
            id = 1,
            author = "Нетология. Университет интернет-профессий будущего",
            content = "Привет, это новая Нетология! Когда-то Нетология начиналась с интенсивов по онлайн-маркетингу. Затем появились курсы по дизайну, разработке, аналитике и управлению. Мы растём сами и помогаем расти студентам: от новичков до уверенных профессионалов. Но самое важное остаётся с нами: мы верим, что в каждом уже есть сила, которая заставляет хотеть больше, целиться выше, бежать быстрее. Наша миссия — помочь встать на путь роста и начать цепочку перемен → http://netolo.gy/fyb",
            published = "21 мая в 18:36",
            likes = 10,
            shares = 999,
            views = 1,
            likedByMe = false
        )

        // Присваиваем данные модели к UI-элементам
        with(binding) {
            author.text = post.author
            published.text = post.published
            content.text = post.content

            // Формируем текст лейблов с числом
            likeCount.text = formatNumber(post.likes)
            shareCount.text = formatNumber(post.shares)
            viewsCount.text = formatNumber(post.views)

            // Назначаем обработчики кликов
            like.setOnClickListener {
                post.likedByMe = !post.likedByMe
                if (post.likedByMe) {
                    post.likes++
                } else {
                    post.likes--
                }
                like.setImageResource(if (post.likedByMe) R.drawable.ic_liked_24 else R.drawable.ic_like_24)
                likeCount.text = formatNumber(post.likes)
            }

            share.setOnClickListener {
                post.shares++
                shareCount.text = formatNumber(post.shares)
            }
            /**
            // Обработчик для корневого элемента (общая область экрана)
            binding.root.setOnClickListener {
                println("Корневая область была нажата.")
            }

            // Обработчик для кнопки Like
            binding.like.setOnClickListener {
                println("Кнопка Like была нажата.")
            }

            // Обработчик для аватара
            binding.avatar.setOnClickListener {
                println("Аватар был нажат.")
            }
            **/
        }
    }

    // Функция форматирования чисел
    private fun formatNumber(value: Int): String {
        return when {
            value >= 1_000_000 -> "${value / 1_000_000}.${(value % 1_000_000) / 100_000}M"
            value >= 10_000 -> "${value / 1000}K"
            value > 999 -> "${value / 1000}.${(value % 1000) / 100}K"
            else -> "$value"
        }
    }
}

