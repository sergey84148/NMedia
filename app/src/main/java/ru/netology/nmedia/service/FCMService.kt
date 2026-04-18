package ru.netology.nmedia.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.netology.nmedia.R
import ru.netology.nmedia.auth.AppAuth
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class FCMService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "remote"
        private const val TAG = "FCMService"
    }

    @Inject
    lateinit var appAuth: AppAuth

    private val gson = Gson()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_remote_name)
            val descriptionText = getString(R.string.channel_remote_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "========== MESSAGE RECEIVED ==========")
        Log.d(TAG, "Data: ${message.data}")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val contentJson = message.data["content"]
                if (contentJson.isNullOrBlank()) {
                    Log.d(TAG, "No content in message")
                    return@launch
                }

                Log.d(TAG, "Content JSON: $contentJson")

                val pushMessage = gson.fromJson(contentJson, PushMessage::class.java)
                val recipientId = pushMessage.recipientId
                val content = pushMessage.content

                val currentUserId = appAuth.getUserId()

                Log.d(TAG, "recipientId: $recipientId, currentUserId: $currentUserId")
                Log.d(TAG, "content: $content")

                when {
                    recipientId == null -> {
                        Log.d(TAG, "Mass notification - showing")
                        showNotification(content)
                    }
                    recipientId == 0L && currentUserId == null -> {
                        Log.d(TAG, "Anonymous notification - showing")
                        showNotification(content)
                    }
                    recipientId == 0L && currentUserId != null -> {
                        Log.d(TAG, "Server thinks we're anonymous but we're authenticated - resending token")
                        resendPushToken()
                    }
                    recipientId != null && currentUserId != null && recipientId == currentUserId -> {
                        Log.d(TAG, "Personal notification for current user - showing")
                        showNotification(content)
                    }
                    recipientId != null && currentUserId != null && recipientId != currentUserId -> {
                        Log.d(TAG, "Recipient mismatch - resending token")
                        resendPushToken()
                    }
                    recipientId != null && currentUserId == null -> {
                        Log.d(TAG, "Recipient exists but user not authenticated - resending token")
                        resendPushToken()
                    }
                    else -> {
                        Log.d(TAG, "Unknown case - showing notification as fallback")
                        showNotification(content)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message", e)
            }
        }
    }

    private suspend fun resendPushToken() {
        try {
            val token = appAuth.getPushToken()
            if (token != null) {
                appAuth.sendPushToken(token)
                Log.d(TAG, "Push token resent successfully")
            } else {
                Log.d(TAG, "No push token to resend")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resend push token", e)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New token generated: $token")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appAuth.savePushToken(token)
                appAuth.sendPushToken(token)
                Log.d(TAG, "New token saved and sent to server")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save/send new token", e)
            }
        }
    }

    private fun showNotification(content: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val notificationBuilder = NotificationCompat.Builder(this@FCMService, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setAutoCancel(true)
                .setShowWhen(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)

            sendNotification(notificationBuilder.build())
        }
    }

    private fun sendNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(Random.nextInt(100_000), notification)
            Log.d(TAG, "Notification sent")
        } else {
            Log.d(TAG, "No permission to send notification")
        }
    }
}

data class PushMessage(
    val recipientId: Long? = null,
    val content: String = ""
)