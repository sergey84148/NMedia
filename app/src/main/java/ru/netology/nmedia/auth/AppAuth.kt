package ru.netology.nmedia.auth

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import ru.netology.nmedia.api.PostsApi
import ru.netology.nmedia.dto.PushToken
import kotlin.coroutines.EmptyCoroutineContext

class AppAuth private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val idKey = "id"
    private val tokenKey = "token"
    private val avatarKey = "avatar"
    private val pushTokenKey = "push_token"

    private val _authStateFlow = MutableStateFlow(AuthState())
    val authStateFlow: StateFlow<AuthState> = _authStateFlow

    init {
        val id = prefs.getLong(idKey, 0)
        val token = prefs.getString(tokenKey, null)
        val avatar = prefs.getString(avatarKey, null)

        if (id != 0L && !token.isNullOrEmpty()) {
            _authStateFlow.value = AuthState(id, token, avatar)
        }

        // Отправляем push токен при инициализации
        CoroutineScope(EmptyCoroutineContext).launch {
            getPushToken()?.let { sendPushToken(it) }
        }
    }

    @Synchronized
    fun setAuth(id: Long, token: String, avatar: String? = null) {
        _authStateFlow.value = AuthState(id, token, avatar)
        with(prefs.edit()) {
            putLong(idKey, id)
            putString(tokenKey, token)
            if (avatar != null) putString(avatarKey, avatar)
            apply()
        }

        sendPushToken()
    }

    @Synchronized
    fun removeAuth() {
        _authStateFlow.value = AuthState()
        with(prefs.edit()) {
            clear()
            commit()
        }

        sendPushToken()
    }

    fun sendPushToken(token: String? = null) {
        CoroutineScope(EmptyCoroutineContext).launch {
            runCatching {
                val pushToken = token ?: getPushToken() ?: FirebaseMessaging.getInstance().token.await()
                savePushToken(pushToken)
                PostsApi.service.sendPushToken(PushToken(pushToken))
            }
                .onFailure { it.printStackTrace() }
        }
    }

    // Получить ID текущего пользователя
    fun getUserId(): Long? {
        val userId = _authStateFlow.value.id
        return if (userId != 0L) userId else null
    }

    // Получить токен авторизации
    fun getToken(): String? {
        return _authStateFlow.value.token
    }

    // Получить сохраненный push токен (suspend версия)
    suspend fun getPushToken(): String? {
        return prefs.getString(pushTokenKey, null)
    }

    // Сохранить push токен
    suspend fun savePushToken(token: String) {
        prefs.edit().putString(pushTokenKey, token).apply()
    }

    // Проверить авторизован ли пользователь
    fun isAuthenticated(): Boolean {
        return _authStateFlow.value.id != 0L && !_authStateFlow.value.token.isNullOrEmpty()
    }

    companion object {
        @Volatile
        private var instance: AppAuth? = null

        fun getInstance(): AppAuth = synchronized(this) {
            instance ?: throw IllegalStateException(
                "AppAuth is not initialized, you must call AppAuth.initializeApp(Context context) first."
            )
        }

        fun initializeApp(context: Context): AppAuth = synchronized(this) {
            instance ?: buildAuth(context).also { instance = it }
        }

        fun isInitialized(): Boolean = instance != null

        private fun buildAuth(context: Context): AppAuth = AppAuth(context)
    }
}

data class AuthState(
    val id: Long = 0,
    val token: String? = null,
    val avatar: String? = null
)