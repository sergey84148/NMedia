package ru.netology.nmedia.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import ru.netology.nmedia.R
import ru.netology.nmedia.adapter.OnInteractionListener
import ru.netology.nmedia.adapter.PostsAdapter
import ru.netology.nmedia.databinding.FragmentFeedBinding
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.viewmodel.PostViewModel

class FeedFragment : Fragment() {

    private val viewModel: PostViewModel by activityViewModels()
    private var binding: FragmentFeedBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = this.binding!!

        // Адаптер для RecyclerView
        val adapter = PostsAdapter(object : OnInteractionListener {
            override fun onEdit(post: Post) {
                viewModel.edit(post)
                findNavController().navigate(R.id.action_feedFragment_to_newPostFragment)
            }

            override fun onLike(post: Post) {
                viewModel.likeById(post.id)
            }

            override fun onRemove(post: Post) {
                viewModel.removeById(post.id)
            }

            override fun onShare(post: Post) {
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, post.content)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(intent, getString(R.string.chooser_share_post))
                startActivity(shareIntent)
            }

            override fun onOpenPost(post: Post) {
                // Логика открытия поста (если нужна)
            }
        })

        binding.list.adapter = adapter

        // Обработчик свайпа вниз для синхронизации
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.syncWithServer()
            binding.swipeRefreshLayout.isRefreshing = false
        }

        // Наблюдение за состоянием данных
        viewModel.data.observe(viewLifecycleOwner) { feedModel ->
            adapter.submitList(feedModel.posts)
            binding.empty.isVisible = feedModel.empty
        }

        // Наблюдение за состоянием загрузки и ошибок
        viewModel.state.observe(viewLifecycleOwner) { state ->
            binding.progress.isVisible = state.loading
            binding.syncProgress.isVisible = state.syncing

            // Обработка ошибок загрузки
            if (state.error) {
                binding.errorGroup.isVisible = true
                binding.retryButton.setOnClickListener {
                    viewModel.loadPosts()
                    binding.errorGroup.isVisible = false
                }
            } else {
                binding.errorGroup.isVisible = false
            }

            // Обновление текста сообщения об отсутствии сети с количеством ожидающих постов
            if (state.pendingPostsCount > 0 && viewModel.isNetworkAvailable.value == false) {
                binding.noConnectionMessage.text = getString(
                    R.string.sync_error_with_count,
                    state.pendingPostsCount
                )
            }
        }

        // Наблюдение за состоянием сети
        viewModel.showNoConnectionMessage.observe(viewLifecycleOwner) { show ->
            binding.noConnectionMessage.isVisible = show
        }

        // Кнопка FAB для создания нового поста
        binding.fab.setOnClickListener {
            findNavController().navigate(R.id.action_feedFragment_to_newPostFragment)
        }
    }

    override fun onResume() {
        super.onResume()
        // При возвращении на экран тоже синхронизируемся
        viewModel.syncWithServer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}