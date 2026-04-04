package ru.netology.nmedia.util

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import android.util.Log
import android.view.View
import ru.netology.nmedia.R
import ru.netology.nmedia.api.BASE_URL
import ru.netology.nmedia.dto.Attachment


object ImageLoader {

    fun loadAvatar(context: Context, avatarName: String?, imageView: ImageView) {
        if (avatarName.isNullOrEmpty()) {
            imageView.setImageResource(R.drawable.ic_launcher_background)
            return
        }

        val avatarUrl = "http://10.0.2.2:9999/avatars/$avatarName"
        Log.d("ImageLoader", "Loading avatar from: $avatarUrl")

        Glide.with(context)
            .load(avatarUrl)
            .placeholder(R.drawable.ic_loading_100dp)
            .error(R.drawable.ic_error_100dp)
            .timeout(10000)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .circleCrop()
            .into(imageView)
    }

    fun loadPostAttachment(context: Context, attachment: Attachment?, imageView: ImageView) {
        if (attachment == null || attachment.url.isEmpty()) {
            imageView.visibility = View.GONE
            return
        }

        // Если attachment.url содержит только имя файла, добавляем путь /images/
        val fullUrl = if (attachment.url.startsWith("http")) {
            attachment.url // если уже полный URL, используем как есть
        } else {
            "http://10.0.2.2:9999/media/${attachment.url}"
        }

        Log.d("ImageLoader", "Loading post attachment from: $fullUrl")

        Glide.with(context)
            .load(fullUrl)
            .placeholder(R.drawable.ic_loading_100dp)
            .error(R.drawable.ic_error_100dp)
            .timeout(10000)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(imageView)
    }
}
