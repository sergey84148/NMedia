package ru.netology.nmedia.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import ru.netology.nmedia.api.ApiService
import ru.netology.nmedia.auth.AppAuth
import ru.netology.nmedia.error.ApiError
import java.io.File
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val apiService: ApiService,
    private val appAuth: AppAuth
) : ViewModel() {

    private val _authenticated = MutableLiveData(false)
    val authenticated: LiveData<Boolean> = _authenticated

    private val _data = MutableLiveData<AuthData>()
    val data: LiveData<AuthData> = _data

    private val _authError = MutableLiveData<String?>()
    val authError: LiveData<String?> = _authError

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // LiveData для уведомления о смене авторизации
    private val _authStateChanged = MutableLiveData(false)
    val authStateChanged: LiveData<Boolean> = _authStateChanged

    init {
        viewModelScope.launch {
            appAuth.authStateFlow.collect { authState ->
                val isAuth = authState.token != null && authState.id != 0L
                _authenticated.postValue(isAuth)
                _data.postValue(AuthData(authState.id, authState.token))
                // Уведомляем об изменении состояния авторизации
                _authStateChanged.postValue(true)
                Log.d("AuthViewModel", "Auth state changed: isAuth=$isAuth, userId=${authState.id}")
            }
        }
    }

    fun login(login: String, password: String) {
        _isLoading.value = true
        _authError.value = null

        viewModelScope.launch {
            try {
                Log.d("AuthViewModel", "Login attempt with: $login")
                val response = apiService.login(login, password)

                Log.d("AuthViewModel", "Login response code: ${response.code()}")

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        appAuth.setAuth(body.id, body.token, body.avatar)
                        Log.d("AuthViewModel", "Login successful: userId=${body.id}")
                        _authError.value = null
                    } else {
                        _authError.value = "Ошибка: пустой ответ от сервера"
                        Log.e("AuthViewModel", "Empty response body")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("AuthViewModel", "Login failed: ${response.code()} - $errorBody")
                    _authError.value = when (response.code()) {
                        401, 403 -> "Неверный логин или пароль"
                        400 -> "Неверный формат запроса"
                        404 -> "Пользователь не найден"
                        else -> "Ошибка сервера: ${response.code()}"
                    }
                }
            } catch (e: IOException) {
                Log.e("AuthViewModel", "Network error during login", e)
                _authError.value = "Ошибка сети. Проверьте подключение к интернету"
            } catch (e: ApiError) {
                Log.e("AuthViewModel", "API error during login", e)
                _authError.value = "Ошибка сервера: ${e.message}"
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Unknown error during login", e)
                _authError.value = "Неизвестная ошибка. Попробуйте позже"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(login: String, password: String, name: String, avatarFile: File? = null) {
        _isLoading.value = true
        _authError.value = null

        viewModelScope.launch {
            try {
                Log.d("AuthViewModel", "Register attempt with: $login, $name")

                val loginPart = login.toRequestBody("text/plain".toMediaType())
                val passPart = password.toRequestBody("text/plain".toMediaType())
                val namePart = name.toRequestBody("text/plain".toMediaType())

                var avatarPart: MultipartBody.Part? = null
                if (avatarFile != null && avatarFile.exists()) {
                    val requestFile = avatarFile.asRequestBody("image/jpeg".toMediaType())
                    avatarPart = MultipartBody.Part.createFormData("file", avatarFile.name, requestFile)
                }

                val response = apiService.register(loginPart, passPart, namePart, avatarPart)

                Log.d("AuthViewModel", "Register response code: ${response.code()}")

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        appAuth.setAuth(body.id, body.token, body.avatar)
                        Log.d("AuthViewModel", "Register successful: userId=${body.id}")
                        _authError.value = null
                    } else {
                        _authError.value = "Ошибка: пустой ответ от сервера"
                        Log.e("AuthViewModel", "Empty response body")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("AuthViewModel", "Register failed: ${response.code()} - $errorBody")
                    _authError.value = when (response.code()) {
                        409 -> "Пользователь с таким логином уже существует"
                        400 -> "Неверный формат запроса"
                        else -> "Ошибка сервера: ${response.code()}"
                    }
                }
            } catch (e: IOException) {
                Log.e("AuthViewModel", "Network error during register", e)
                _authError.value = "Ошибка сети. Проверьте подключение к интернету"
            } catch (e: ApiError) {
                Log.e("AuthViewModel", "API error during register", e)
                _authError.value = "Ошибка сервера: ${e.message}"
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Unknown error during register", e)
                _authError.value = "Неизвестная ошибка. Попробуйте позже"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        appAuth.removeAuth()
    }

    fun clearError() {
        _authError.value = null
    }
}

data class AuthData(
    val userId: Long = 0,
    val token: String? = null
)