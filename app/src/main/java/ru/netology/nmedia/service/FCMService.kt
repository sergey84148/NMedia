package ru.netology.nmedia.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import ru.netology.nmedia.R
import kotlin.random.Random

class FCMService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "remote"
    }

    private val actionKey = "action"
    private val contentKey = "content"
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
        try {
            message.data[actionKey]?.let { action ->
                when (Action.valueOf(action)) {
                    Action.NEW_POST -> handleNewPost(gson.fromJson(message.data[contentKey], NewPost::class.java))
                }
            }
        } catch (_: IllegalArgumentException) {
            // Обработка ситуации, когда передано неверное значение действия
        }
    }

    override fun onNewToken(token: String) {
        println(token)
    }

    private fun handleNewPost(newPost: NewPost) {
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${newPost.userName} опубликовал новый пост:")
            .setStyle(NotificationCompat.BigTextStyle().bigText(newPost.postText))
            .setAutoCancel(true)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        sendNotification(notificationBuilder.build())
    }

    private fun sendNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
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