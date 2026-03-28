package ru.netology.nmedia.auth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppAuth private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val idKey = "id"
    private val tokenKey = "token"
    private val avatarKey = "avatar"

    private val _authStateFlow = MutableStateFlow(AuthState())
    val authStateFlow: StateFlow<AuthState> = _authStateFlow

    init {
        val id = prefs.getLong(idKey, 0)
        val token = prefs.getString(tokenKey, null)
        val avatar = prefs.getString(avatarKey, null)

        if (id != 0L && !token.isNullOrEmpty()) {
            _authStateFlow.value = AuthState(id, token, avatar)
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
    }

    @Synchronized
    fun removeAuth() {
        _authStateFlow.value = AuthState()
        with(prefs.edit()) {
            clear()
            commit()
        }
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