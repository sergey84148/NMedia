package ru.netology.nmedia.activity

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ru.netology.nmedia.R
import ru.netology.nmedia.adapter.OnInteractionListener
import ru.netology.nmedia.adapter.PostsAdapter
import ru.netology.nmedia.databinding.ActivityMainBinding
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.util.AndroidUtils
import ru.netology.nmedia.viewmodel.PostViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: PostViewModel by viewModels()
    private lateinit var adapter: PostsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Инициализация View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Настройка отступов с учётом системных панелей
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Установка адаптер с обработчиками взаимодействий
        adapter = PostsAdapter(object : OnInteractionListener {
            override fun onEdit(post: Post) {
                viewModel.edit(post)
            }

            override fun onLike(post: Post) {
                viewModel.likeById(post.id)
            }

            override fun onRemove(post: Post) {
                viewModel.removeById(post.id)
            }

            override fun onShare(post: Post) {
                viewModel.shareById(post.id)
            }

            override fun onCancelEdit(post: Post) {
                viewModel.onCancelEdit()
            }
        })

        // Привязка адаптера к RecyclerView
        binding.list.adapter = adapter

        // Получаем ссылку на кнопку отмены
        val cancelEditBtn = findViewById<ImageButton>(R.id.cancel_edit)

        // Добавляем обработчик изменений текста для управления видимостью кнопки
        binding.content.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Показываем кнопку, если текст есть, иначе скрываем
                val hasText = !binding.content.text.isNullOrBlank()
                cancelEditBtn.visibility = if (hasText) View.VISIBLE else View.GONE
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Назначаем обработчик кликов на кнопку отмены
        cancelEditBtn.setOnClickListener {
            // Очищаем текст
            binding.content.setText("")
            // Потеря фокуса
            binding.content.clearFocus()
            // Скрываем клавиатуру
            AndroidUtils.hideKeyboard(binding.content)
        }

        // Наблюдаем за списком постов
        viewModel.data.observe(this) { posts ->
            adapter.submitList(posts)
        }

        // Наблюдаем за редактируемым постом
        viewModel.editedPost.observe(this) { post ->
            if (post == null) {
                // Сбрасываем состояние редактирования
                binding.content.setText("")
                AndroidUtils.hideKeyboard(binding.content)
            } else {
                // Активируем режим редактирования
                with(binding.content) {
                    setText(post.content)
                    requestFocus()
                    AndroidUtils.showKeyboard(this)
                }
            }
        }

        // Обрабатываем нажатие на кнопку сохранения
        binding.save.setOnClickListener {
            with(binding.content) {
                if (text.isNullOrBlank()) {
                    Toast.makeText(
                        this@MainActivity,
                        context.getString(R.string.error_empty_content),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                viewModel.save(text.toString())
                setText("")
                clearFocus()
                AndroidUtils.hideKeyboard(this)
            }
        }
    }
}