package ru.netology.nmedia.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ru.netology.nmedia.R
import ru.netology.nmedia.adapter.OnInteractionListener
import ru.netology.nmedia.adapter.PostsAdapter
import ru.netology.nmedia.databinding.ActivityMainBinding
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.viewmodel.PostViewModel

class MainActivity : AppCompatActivity() {

    private val viewModel: PostViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Регистрация ожидаемого результата от EditPostActivity
        val editPostLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val editedContent = result.data!!.getStringExtra(NewPostActivity.RESULT_EDITED_POST)
                if (editedContent != null) {
                    viewModel.updateEditedPost(editedContent)
                }
            }
        }

        // Регистрация ожидаемого результата от NewPostActivity
        val newPostLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val newContent = result.data!!.getStringExtra(NewPostActivity.EXTRA_NEW_POST_CONTENT)
                if (newContent != null) {
                    // Формируем новый пост и передаем его в ViewModel
                    val newPost = Post(
                        id = System.currentTimeMillis(),
                        author = "Автор",
                        content = newContent,
                        published = "Сегодня",
                        likes = 0,
                        shares = 0,
                        likedByMe = false
                    )
                    viewModel.save(newPost)
                }
            }
        }

        // Настроим адаптер и обработчики
        val adapter = PostsAdapter(object : OnInteractionListener {
            override fun onEdit(post: Post) {
                // Переход на экран редактирования
                val intent = Intent(this@MainActivity, NewPostActivity::class.java)
                intent.putExtra(NewPostActivity.EXTRA_POST_CONTENT, post.content)
                editPostLauncher.launch(intent)
            }

            override fun onLike(post: Post) {
                viewModel.likeById(post.id)
            }

            override fun onRemove(post: Post) {
                viewModel.removeById(post.id)
            }

            override fun onShare(post: Post) {
                viewModel.shareById(post.id)
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, post.content)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(intent, getString(R.string.chooser_share_post))
                startActivity(shareIntent)
            }
        })

        binding.list.adapter = adapter
        viewModel.data.observe(this) { posts ->
            adapter.submitList(posts)
        }

        // Установка обработчика FAB для создания нового поста
        binding.fab.setOnClickListener {
            newPostLauncher.launch(Intent(this, NewPostActivity::class.java))
        }
    }
}