package ru.netology.nmedia.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.github.dhaval2404.imagepicker.ImagePicker
import ru.netology.nmedia.R
import ru.netology.nmedia.databinding.FragmentRegisterBinding
import ru.netology.nmedia.viewmodel.AuthViewModel
import java.io.File

class RegisterFragment : Fragment() {

    private var binding: FragmentRegisterBinding? = null
    private val authViewModel: AuthViewModel by activityViewModels()
    private var selectedImageFile: File? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                binding?.avatarImage?.setImageURI(uri)
                selectedImageFile = File(uri.path ?: "")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = binding ?: return

        // Выбор аватара
        binding.avatarImage.setOnClickListener {
            ImagePicker.with(this)
                .cropSquare()
                .compress(512)
                .maxResultSize(512, 512)
                .createIntent { intent ->
                    pickImageLauncher.launch(intent)
                }
        }

        // Наблюдаем за состоянием загрузки
        authViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
            binding.buttonRegister.isEnabled = !isLoading
            binding.editLogin.isEnabled = !isLoading
            binding.editName.isEnabled = !isLoading
            binding.editPassword.isEnabled = !isLoading
            binding.editConfirmPassword.isEnabled = !isLoading
        }

        // Наблюдаем за ошибками
        authViewModel.authError.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrEmpty()) {
                binding.textInputLayoutLogin.error = null
                binding.textInputLayoutName.error = null
                binding.textInputLayoutPassword.error = null
                binding.textInputLayoutConfirmPassword.error = null

                when {
                    error.contains("логин") -> binding.textInputLayoutLogin.error = error
                    error.contains("имя") -> binding.textInputLayoutName.error = error
                    error.contains("пароль") -> binding.textInputLayoutPassword.error = error
                    error.contains("существует") -> binding.textInputLayoutLogin.error = error
                    else -> Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                }
            }
        }

        // Очищаем ошибки при вводе
        binding.editLogin.addTextChangedListener {
            binding.textInputLayoutLogin.error = null
            authViewModel.clearError()
        }

        binding.editName.addTextChangedListener {
            binding.textInputLayoutName.error = null
            authViewModel.clearError()
        }

        binding.editPassword.addTextChangedListener {
            binding.textInputLayoutPassword.error = null
            binding.textInputLayoutConfirmPassword.error = null
            authViewModel.clearError()
        }

        binding.editConfirmPassword.addTextChangedListener {
            binding.textInputLayoutPassword.error = null
            binding.textInputLayoutConfirmPassword.error = null
            authViewModel.clearError()
        }

        binding.buttonRegister.setOnClickListener {
            val login = binding.editLogin.text.toString().trim()
            val name = binding.editName.text.toString().trim()
            val password = binding.editPassword.text.toString().trim()
            val confirmPassword = binding.editConfirmPassword.text.toString().trim()

            if (login.isEmpty()) {
                binding.textInputLayoutLogin.error = "Введите логин"
                return@setOnClickListener
            }
            if (name.isEmpty()) {
                binding.textInputLayoutName.error = "Введите имя"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                binding.textInputLayoutPassword.error = "Введите пароль"
                return@setOnClickListener
            }
            if (confirmPassword.isEmpty()) {
                binding.textInputLayoutConfirmPassword.error = "Подтвердите пароль"
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                binding.textInputLayoutConfirmPassword.error = "Пароли не совпадают"
                return@setOnClickListener
            }

            authViewModel.register(login, password, name, selectedImageFile)
        }

        // Наблюдаем за успешной регистрацией
        authViewModel.authenticated.observe(viewLifecycleOwner) { isAuthenticated ->
            if (isAuthenticated) {
                Toast.makeText(requireContext(), "Регистрация выполнена", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_registerFragment_to_feedFragment)
            }
        }

        binding.buttonBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}