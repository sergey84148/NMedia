package ru.netology.nmedia.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import ru.netology.nmedia.R
import ru.netology.nmedia.databinding.FragmentLoginBinding
import ru.netology.nmedia.viewmodel.AuthViewModel

class LoginFragment : Fragment() {

    private var binding: FragmentLoginBinding? = null
    private val authViewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = binding ?: return

        // Наблюдаем за состоянием загрузки
        authViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
            binding.buttonLogin.isEnabled = !isLoading
            binding.editLogin.isEnabled = !isLoading
            binding.editPassword.isEnabled = !isLoading
        }

        // Наблюдаем за ошибками
        authViewModel.authError.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrEmpty()) {
                binding.textInputLayoutLogin.error = null
                binding.textInputLayoutPassword.error = null

                when {
                    error.contains("логин") -> binding.textInputLayoutLogin.error = error
                    error.contains("пароль") -> binding.textInputLayoutPassword.error = error
                    else -> Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                }
            }
        }

        // Очищаем ошибки при вводе
        binding.editLogin.addTextChangedListener {
            binding.textInputLayoutLogin.error = null
            authViewModel.clearError()
        }

        binding.editPassword.addTextChangedListener {
            binding.textInputLayoutPassword.error = null
            authViewModel.clearError()
        }

        binding.buttonLogin.setOnClickListener {
            val login = binding.editLogin.text.toString().trim()
            val password = binding.editPassword.text.toString().trim()

            if (login.isEmpty()) {
                binding.textInputLayoutLogin.error = "Введите логин"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                binding.textInputLayoutPassword.error = "Введите пароль"
                return@setOnClickListener
            }

            // Пробуем войти с существующими пользователями
            // Из серверного кода доступны: netology, sber, tcs, got, student
            // Пароль для всех: secret
            authViewModel.login(login, password)
        }

        // Наблюдаем за успешным входом
        authViewModel.authenticated.observe(viewLifecycleOwner) { isAuthenticated ->
            if (isAuthenticated) {
                Toast.makeText(requireContext(), "Вход выполнен", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_loginFragment_to_feedFragment)
            }
        }

        binding.buttonRegister.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}