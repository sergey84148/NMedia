package ru.netology.nmedia.util

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import android.util.Log
import ru.netology.nmedia.R

object ImageLoader {
    private const val BASE_URL = "http://10.0.2.2:9999"

    fun loadAvatar(context: Context, avatarName: String?, imageView: ImageView) {
        if (avatarName.isNullOrEmpty()) {
            imageView.setImageResource(R.drawable.ic_launcher_background)
            return
        }

        val avatarUrl = "$BASE_URL/avatars/$avatarName"
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
}
