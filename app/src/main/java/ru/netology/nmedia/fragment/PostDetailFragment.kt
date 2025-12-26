package ru.netology.nmedia.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ru.netology.nmedia.databinding.FragmentPostDetailBinding
import ru.netology.nmedia.dto.Post

class PostDetailFragment : Fragment() {

    companion object {
        const val ARG_POST_KEY = "post_key"

        fun newInstance(post: Post): PostDetailFragment {
            val fragment = PostDetailFragment()
            val args = Bundle()
            args.putParcelable(ARG_POST_KEY, post)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentPostDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedinstanceState)

        // Получаем переданный пост из аргументов
        val post = arguments?.getParcelable<Post>(ARG_POST_KEY)
        if (post != null) {
            // Отображаем детали поста
            binding.postTitle.text = post.title
            binding.postContent.text = post.content
        }
    }
}