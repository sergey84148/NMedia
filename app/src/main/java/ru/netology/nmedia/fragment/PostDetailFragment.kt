package ru.netology.nmedia.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ru.netology.nmedia.databinding.FragmentPostDetailBinding
import ru.netology.nmedia.dto.Post

class PostDetailFragment : Fragment() {

    // Binding-переменная
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

        // Получаем переданный объект Post из аргументов
        val post = arguments?.getParcelable<Post>(ARG_POST_KEY)
        post?.let {
            // Привязываем данные к соответствующим View
            binding.postTitle.text = it.author
            //binding.postContent.text = it.content
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Освобождаем память после уничтожения фрагмента
    }
}