package ru.netology.nmedia.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import ru.netology.nmedia.databinding.FragmentNewPostBinding
import ru.netology.nmedia.util.StringArg
import ru.netology.nmedia.viewmodel.PostViewModel

class NewPostFragment : Fragment() {

    companion object {
        var Bundle.textArg: String? by StringArg
    }



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentNewPostBinding.inflate(inflater, container, false)
        val viewModel: PostViewModel by viewModels (ownerProducer = ::requireParentFragment)
        arguments?.textArg.let { text ->
            binding.edit.setText(text)
        }

        binding.ok.setOnClickListener {
            if (!binding.edit.text.isNullOrBlank()) {
                viewModel.save(binding.edit.text.toString())
                findNavController().navigateUp()
            }
        }
        return binding.root
    }
}