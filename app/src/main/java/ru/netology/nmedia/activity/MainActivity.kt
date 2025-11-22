package ru.netology.nmedia.activity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ru.netology.nmedia.R
import ru.netology.nmedia.databinding.ActivityMainBinding
import ru.netology.nmedia.viewmodel.PostViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: PostViewModel by viewModels()

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

        // Наблюдение за данными
        viewModel.data.observe(this) { post ->
            with(binding) {
                author.text = post.author
                published.text = post.published
                content.text = post.content
                likeCount.text = formatNumber(post.likes)
                shareCount.text = formatNumber(post.shares)
                like.setImageResource(
                    if (post.likedByMe) R.drawable.ic_liked_24 else R.drawable.ic_like_24
                )
            }
        }

        // Обработчик клика на кнопку Like
        binding.like.setOnClickListener {
            viewModel.like()
        }

        binding.share.setOnClickListener {
            viewModel.share()
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

