package ru.netology.nmedia.fragment

import android.content.Intent
import android.os.Bundle
import android.telephony.PhoneNumberUtils.formatNumber
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
import ru.netology.nmedia.viewmodel.PostViewModel

class PostDetailFragment : Fragment() {

    private var _binding: FragmentPostDetailBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val ARG_POST_KEY = "post_key"

        fun newInstance(post: Post): PostDetailFragment {
            val fragment = PostDetailFragment()
            val args = Bundle().apply { putParcelable(ARG_POST_KEY, post) }
            fragment.arguments = args
            return fragment
        }
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

        val postId = arguments?.getParcelable<Post>(ARG_POST_KEY)?.id

        viewModel.data.observe(viewLifecycleOwner) { posts ->
            val post = posts.find { it.id == postId }
            if (post != null) {
                binding.author.text = post.author
                binding.content.text = post.content
                binding.published.text = post.published
                binding.like.isChecked = post.likedByMe
                binding.like.text = post.likes.toString()
                binding.menu.setOnClickListener { showPopup(it, post) }
                binding.share.setOnClickListener { share(post) }
                binding.share.text = formatNumber(post.shares)
            } else {
                findNavController().navigateUp()
            }
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