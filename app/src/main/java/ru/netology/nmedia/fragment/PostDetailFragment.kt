package ru.netology.nmedia.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import ru.netology.nmedia.R
import ru.netology.nmedia.databinding.FragmentPostDetailBinding
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.utils.Utils
import ru.netology.nmedia.viewmodel.PostViewModel

class PostDetailFragment : Fragment() {

    private var _binding: FragmentPostDetailBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val ARG_POST_ID = "postId"
    }

    private val viewModel: PostViewModel by viewModels(ownerProducer = ::requireParentFragment)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Получаем идентификатор поста из аргументов
        val postId = arguments?.getLong(ARG_POST_ID)!!

        // Наблюдаем за списком постов и выбираем нужный по идентификатору
        viewModel.data.observe(viewLifecycleOwner) { posts ->
            val post = posts.find { it.id == postId }
            if (post != null) {
                bindPost(post)
            } else {
                findNavController().navigateUp()
            }
        }
    }

    private fun bindPost(post: Post) {
        binding.apply {
            author.text = post.author
            content.text = post.content
            published.text = post.published
            like.isChecked = post.likedByMe
            like.text = post.likes.toString()
            share.text = Utils.formatNumber(post.shares)

            // Показываем видео-контент только если оно доступно
            videoContainer.visibility =
                if (post.video != null && post.video.isNotEmpty())
                    View.VISIBLE
                else
                    View.GONE

            share.setOnClickListener { share(post) }
            menu.setOnClickListener { showPopup(it, post) }
            like.setOnClickListener { viewModel.likeById(post.id) }
        }
    }

    private fun showPopup(menuButton: View, post: Post) {
        val popupMenu = PopupMenu(requireContext(), menuButton)
        popupMenu.inflate(R.menu.options_post)

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.remove -> {
                    viewModel.removeById(post.id)
                    true
                }
                R.id.edit -> {
                    viewModel.edit(post)
                    findNavController().navigate(
                        R.id.action_postDetailFragment_to_newPostFragment,
                        Bundle().apply { putString("textArg", post.content) }
                    )
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun share(post: Post) {
        viewModel.shareById(post.id)
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, post.content)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(intent, getString(R.string.chooser_share_post))
        startActivity(shareIntent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}