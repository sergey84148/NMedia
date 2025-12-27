import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ru.netology.nmedia.databinding.FragmentPostDetailBinding
import ru.netology.nmedia.dto.Post

class PostDetailFragment : Fragment() {

    // Объявляем переменную для binding
    private var _binding: FragmentPostDetailBinding? = null
    private val binding get() = _binding!!

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
    ): View {
        _binding = FragmentPostDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Получаем переданный пост из аргументов
        val post = arguments?.getParcelable<Post>(ARG_POST_KEY)
        post?.let {
            // Отображаем детали поста
            binding.postTitle.text = it.title // Предполагается, что у вас есть эти View в вашем FragmentPostDetailBinding
            binding.postContent.text = it.content // Предполагается, что у вас есть эти View в вашем FragmentPostDetailBinding
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Очищаем binding при уничтожении представления фрагмента, чтобы избежать утечек памяти
    }
}