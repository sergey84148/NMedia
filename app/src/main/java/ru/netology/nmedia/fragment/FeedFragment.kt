package ru.netology.nmedia.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.netology.nmedia.R
import ru.netology.nmedia.adapter.OnInteractionListener
import ru.netology.nmedia.adapter.PostsAdapter
import ru.netology.nmedia.databinding.FragmentFeedBinding
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.viewmodel.PostViewModel

class FeedFragment : Fragment() {

    private val viewModel: PostViewModel by activityViewModels()
    private var binding: FragmentFeedBinding? = null
    private var isBannerVisible = false

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

        // Настройка плашки "Свежие записи"
        setupNewPostsBanner(binding)

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
                // Используем Bundle вместо Safe Args для избежания ошибки
                val bundle = Bundle().apply {
                    putLong("postId", post.id)
                }
                findNavController().navigate(R.id.action_feedFragment_to_postDetailFragment, bundle)
            }
        })

        binding.list.adapter = adapter
        setupRecyclerViewScrollListener(binding)

        // Обработчик свайпа вниз для синхронизации
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.syncWithServer()
            binding.swipeRefreshLayout.isRefreshing = false
        }

        // Наблюдение за состоянием данных
        viewModel.data.observe(viewLifecycleOwner) { feedModel ->
            adapter.submitList(feedModel.posts)
            binding.empty.isVisible = feedModel.posts.isEmpty()
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
            binding.noConnectionMessage.isVisible = show == true
        }

        // Наблюдение за плашкой новых постов
        viewModel.showNewPostsBanner.observe(viewLifecycleOwner) { show ->
            if (show == true && !isBannerVisible) {
                showNewPostsBanner(binding)
            } else if (show == false && isBannerVisible) {
                hideNewPostsBanner(binding)
            }
        }

        viewModel.newPostsCount.observe(viewLifecycleOwner) { count ->
            updateBannerText(binding, count ?: 0)
        }

        // Кнопка FAB для создания нового поста
        binding.fab.setOnClickListener {
            findNavController().navigate(R.id.action_feedFragment_to_newPostFragment)
        }
    }

    private fun setupNewPostsBanner(binding: FragmentFeedBinding) {
        // Устанавливаем обработчик нажатия на плашку
        binding.newPostsBanner.setOnClickListener {
            viewModel.onNewPostsBannerClicked()
            smoothScrollToTop(binding)
        }
    }

    private fun setupRecyclerViewScrollListener(binding: FragmentFeedBinding) {
        binding.list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                // Если мы наверху списка, автоматически скрываем баннер
                if (firstVisibleItemPosition == 0 && isBannerVisible) {
                    val firstItemView = layoutManager.findViewByPosition(0)
                    if (firstItemView != null && firstItemView.top >= 0) {
                        viewModel.onNewPostsBannerClicked()
                    }
                }
            }
        })
    }

    private fun showNewPostsBanner(binding: FragmentFeedBinding) {
        isBannerVisible = true
        binding.newPostsBanner.visibility = View.VISIBLE
        binding.newPostsBanner.alpha = 0f
        binding.newPostsBanner.translationY = -50f

        binding.newPostsBanner.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    private fun hideNewPostsBanner(binding: FragmentFeedBinding) {
        isBannerVisible = false
        binding.newPostsBanner.animate()
            .alpha(0f)
            .translationY(-50f)
            .setDuration(200)
            .withEndAction {
                binding.newPostsBanner.visibility = View.GONE
            }
            .start()
    }

    private fun smoothScrollToTop(binding: FragmentFeedBinding) {
        binding.list.smoothScrollToPosition(0)

        // Альтернатива с более плавной анимацией
        binding.list.post {
            binding.list.smoothScrollToPosition(0)
        }
    }

    private fun updateBannerText(binding: FragmentFeedBinding, count: Int) {
        binding.bannerText.text = when {
            count > 1 -> getString(R.string.new_posts_banner)
            count == 1 -> getString(R.string.new_post_banner)
            else -> getString(R.string.new_posts_banner)
        }
    }

    override fun onResume() {
        super.onResume()
        // При возвращении на экран проверяем новые посты
        viewModel.checkForNewPosts()
    }

    override fun onPause() {
        super.onPause()
        // Скрываем плашку при уходе с экрана
        binding?.let {
            if (isBannerVisible) {
                hideNewPostsBanner(it)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}