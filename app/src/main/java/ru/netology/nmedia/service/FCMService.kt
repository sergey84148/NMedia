package ru.netology.nmedia.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import ru.netology.nmedia.R
import ru.netology.nmedia.auth.AppAuth
import kotlin.random.Random

class FCMService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "remote"
    }

    private val actionKey = "action"
    private val contentKey = "content"
    private val recipientIdKey = "recipientId"
    private val gson = Gson()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_remote_name)
            val descriptionText = getString(R.string.channel_remote_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Получаем recipientId из данных сообщения
                val recipientIdStr = message.data[recipientIdKey]
                val recipientId = recipientIdStr?.toLongOrNull()
                val currentUserId = AppAuth.getInstance().getUserId()

                // Логика проверки recipientId
                when {
                    // Массовая рассылка (recipientId = null или отсутствует)
                    recipientIdStr == null -> {
                        handleAction(message)
                    }
                    // Анонимная аутентификация (recipientId = 0)
                    recipientId == 0L && currentUserId == null -> {
                        handleAction(message)
                    }
                    recipientId == 0L && currentUserId != null -> {
                        // Сервер считает нас анонимом, но у нас есть авторизация - отправляем токен заново
                        resendPushToken()
                    }
                    // recipientId совпадает с текущим пользователем
                    recipientId != null && currentUserId != null && recipientId == currentUserId -> {
                        handleAction(message)
                    }
                    // recipientId не совпадает с текущим пользователем
                    recipientId != null && currentUserId != null && recipientId != currentUserId -> {
                        // На устройстве другая аутентификация - отправляем токен заново
                        resendPushToken()
                    }
                    // recipientId есть, но пользователь не авторизован
                    recipientId != null && currentUserId == null -> {
                        resendPushToken()
                    }
                    else -> {
                        // По умолчанию пытаемся обработать действие
                        handleAction(message)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun handleAction(message: RemoteMessage) {
        message.data[actionKey]?.let { action ->
            when (Action.valueOf(action)) {
                Action.NEW_POST -> {
                    val newPost = gson.fromJson(message.data[contentKey], NewPost::class.java)
                    handleNewPost(newPost)
                }
            }
        }
    }

    private suspend fun resendPushToken() {
        try {
            // Получаем текущий push token
            val token = AppAuth.getInstance().getPushToken()
            if (token != null) {
                AppAuth.getInstance().sendPushToken(token)
            } else {
                // Если сохраненного токена нет, получаем новый
                val fcmToken = FirebaseMessaging.getInstance().token.await()
                AppAuth.getInstance().savePushToken(fcmToken)
                AppAuth.getInstance().sendPushToken(fcmToken)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Сохраняем и отправляем новый токен на сервер
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppAuth.getInstance().savePushToken(token)
                AppAuth.getInstance().sendPushToken(token)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleNewPost(newPost: NewPost) {
        // Создаем уведомление в главном потоке
        CoroutineScope(Dispatchers.Main).launch {
            val notificationBuilder = NotificationCompat.Builder(this@FCMService, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("${newPost.userName} опубликовал новый пост:")
                .setStyle(NotificationCompat.BigTextStyle().bigText(newPost.postText))
                .setAutoCancel(true)
                .setShowWhen(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            sendNotification(notificationBuilder.build())
        }
    }

    private fun sendNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(Random.nextInt(100_000), notification)
        }
    }
}

// Перечисляем возможные действия
enum class Action {
    NEW_POST
}

// Класс модели данных о новом посте
data class NewPost(
    val userName: String,
    val postAuthor: String,
    val postText: String,
    val timestamp: Long
)