package ru.netology.nmedia.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import ru.netology.nmedia.R
import ru.netology.nmedia.databinding.FragmentNewPostBinding
import ru.netology.nmedia.util.AndroidUtils
import ru.netology.nmedia.util.StringArg
import ru.netology.nmedia.viewmodel.PostViewModel

class NewPostFragment : Fragment() {

    companion object {
        var Bundle.textArg: String? by StringArg
    }

    private val viewModel: PostViewModel by activityViewModels()
    private var binding: FragmentNewPostBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNewPostBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = binding ?: return

        // Заполняем поле ввода при редактировании
        arguments?.textArg?.let { text ->
            binding.edit.setText(text)
        }

        // Кнопка сохранения
        binding.ok.setOnClickListener {
            val content = binding.edit.text.toString().trim()
            if (content.isBlank()) {
                Snackbar.make(binding.root, R.string.error_empty_content, Snackbar.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            // Сохраняем пост
            viewModel.save(content)
            AndroidUtils.hideKeyboard(requireView())

            // Сразу возвращаемся на FeedFragment
            // Данные уже сохранены локально и отобразятся в списке
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}