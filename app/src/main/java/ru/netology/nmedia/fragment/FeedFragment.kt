package ru.netology.nmedia.fragment

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.netology.nmedia.R
import ru.netology.nmedia.adapter.OnInteractionListener
import ru.netology.nmedia.adapter.PostsAdapter
import ru.netology.nmedia.databinding.FragmentFeedBinding
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.viewmodel.AuthViewModel
import ru.netology.nmedia.viewmodel.PostViewModel

class FeedFragment : Fragment() {

    private val viewModel: PostViewModel by activityViewModels()
    private val authViewModel: AuthViewModel by activityViewModels()
    private var binding: FragmentFeedBinding? = null
    private var isBannerVisible = false
    private lateinit var adapter: PostsAdapter

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

        setupNewPostsBanner(binding)

        adapter = PostsAdapter(object : OnInteractionListener {
            override fun onEdit(post: Post) {
                if (!isAuthenticated()) {
                    showAuthDialog()
                    return
                }
                viewModel.edit(post)
                findNavController().navigate(R.id.action_feedFragment_to_newPostFragment)
            }

            override fun onLike(post: Post) {
                if (!isAuthenticated()) {
                    showAuthDialog()
                    return
                }
                viewModel.likeById(post.id)
            }

            override fun onRemove(post: Post) {
                if (!isAuthenticated()) {
                    showAuthDialog()
                    return
                }
                viewModel.removeById(post.id)
            }

            override fun onShare(post: Post) {
                if (!isAuthenticated()) {
                    showAuthDialog()
                    return
                }
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, post.content)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(intent, getString(R.string.chooser_share_post))
                startActivity(shareIntent)
            }

            override fun onOpenPhoto(url: String) {
                val bundle = Bundle().apply {
                    putString("url", url)
                }
                findNavController().navigate(R.id.action_feedFragment_to_photoFragment, bundle)
            }
        })

        binding.list.adapter = adapter
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        setupRecyclerViewScrollListener(binding)

        // 👇 НАБЛЮДАЕМ ЗА ИЗМЕНЕНИЕМ СОСТОЯНИЯ АВТОРИЗАЦИИ
        lifecycleScope.launch {
            authViewModel.authStateChanged.observe(viewLifecycleOwner) { changed ->
                if (changed) {
                    // При логине или логауте обновляем список постов
                    adapter.refresh()
                }
            }
        }

        // Обработка PagingData
        lifecycleScope.launch {
            viewModel.pagingDataFlow.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        // Отслеживаем состояния загрузки
        lifecycleScope.launch {
            adapter.loadStateFlow.collect { loadState ->
                val isLoading = loadState.refresh is LoadState.Loading
                binding.progress.isVisible = isLoading
                binding.swipeRefreshLayout.isRefreshing = loadState.refresh is LoadState.Loading

                val isError = loadState.refresh is LoadState.Error
                if (isError) {
                    binding.errorGroup.isVisible = true
                    binding.retryButton.setOnClickListener {
                        adapter.retry()
                        binding.errorGroup.isVisible = false
                    }
                } else {
                    binding.errorGroup.isVisible = false
                }

                val isEmpty = loadState.refresh is LoadState.NotLoading && adapter.itemCount == 0
                binding.empty.isVisible = isEmpty
            }
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            if (!isAuthenticated()) {
                showAuthDialog()
                binding.swipeRefreshLayout.isRefreshing = false
                return@setOnRefreshListener
            }
            adapter.refresh()
        }

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

        binding.fab.setOnClickListener {
            if (!isAuthenticated()) {
                showAuthDialog()
                return@setOnClickListener
            }
            findNavController().navigate(R.id.action_feedFragment_to_newPostFragment)
        }

        viewModel.postCreated.observe(viewLifecycleOwner) {
            adapter.refresh()
        }
    }

    private fun isAuthenticated(): Boolean {
        return authViewModel.authenticated.value == true
    }

    private fun showAuthDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Требуется авторизация")
            .setMessage("Для выполнения этого действия необходимо войти в аккаунт")
            .setPositiveButton("Войти") { _, _ ->
                findNavController().navigate(R.id.action_feedFragment_to_loginFragment)
            }
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Зарегистрироваться") { _, _ ->
                findNavController().navigate(R.id.action_feedFragment_to_registerFragment)
            }
            .show()
    }

    private fun setupNewPostsBanner(binding: FragmentFeedBinding) {
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
    }

    private fun updateBannerText(binding: FragmentFeedBinding, count: Int) {
        binding.bannerText.text = when {
            count > 1 -> {
                val text = when (Resources.getSystem().configuration.locales[0].language) {
                    "ru" -> "Новых постов: %d"
                    else -> "New posts: %d"
                }
                String.format(text, count)
            }
            count == 1 -> {
                when (Resources.getSystem().configuration.locales[0].language) {
                    "ru" -> "Новый пост"
                    else -> "New post"
                }
            }
            else -> {
                val text = when (Resources.getSystem().configuration.locales[0].language) {
                    "ru" -> "Новых постов: %d"
                    else -> "New posts: %d"
                }
                String.format(text, 0)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.refresh()
    }

    override fun onPause() {
        super.onPause()
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