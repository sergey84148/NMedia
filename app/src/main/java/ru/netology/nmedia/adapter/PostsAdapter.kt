package ru.netology.nmedia.adapter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.netology.nmedia.R
import ru.netology.nmedia.databinding.CardPostBinding
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.utils.Utils.formatNumber
import ru.netology.nmedia.util.ImageLoader





// Интерфейс для обработки взаимодействий с элементами списка
interface OnInteractionListener {
    fun onLike(post: Post) {}

    fun onEdit(post: Post) {}
    fun onRemove(post: Post) {}
    fun onShare(post: Post) {}
    fun onOpenPost(post: Post) {}
}

// Адаптер для отображения постов
class PostsAdapter(
    private val onInteractionListener: OnInteractionListener,
) : ListAdapter<Post, PostViewHolder>(PostDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = CardPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding, onInteractionListener)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = getItem(position)
        holder.bind(post)

        // Добавляем обработчик клика на весь элемент
        val detailsListener = View.OnClickListener {
            onInteractionListener.onOpenPost(post)
        }

        holder.itemView.setOnClickListener(detailsListener)
        holder.binding.content.setOnClickListener(detailsListener)

        // Обработка видео
        if (post.video != null) {
            holder.binding.videoContainer.visibility = View.VISIBLE
            holder.binding.videoThumbnail.setOnClickListener { openVideo(holder.binding.root.context, post.video) }
            holder.binding.playButton.setOnClickListener { openVideo(holder.binding.root.context, post.video) }
        } else {
            holder.binding.videoContainer.visibility = View.GONE
        }
    }

    // Вспомогательная функция для открытия видео
    private fun openVideo(context: Context, videoUrl: String?) {
        if (videoUrl != null) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Нет приложения для просмотра видео", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class PostViewHolder(
    internal val binding: CardPostBinding,
    private val onInteractionListener: OnInteractionListener,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(post: Post) {
        binding.apply {
            author.text = post.author
            published.text = post.published
            content.text = post.content

            like.isChecked = post.likedByMe
            like.text = post.likes.toString()
            share.text = formatNumber(post.shares)

            ImageLoader.loadAvatar(itemView.context, post.authorAvatar, avatar)

            menu.setOnClickListener {
                PopupMenu(it.context, it).apply {
                    inflate(R.menu.options_post)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.remove -> {
                                onInteractionListener.onRemove(post)
                                true
                            }
                            R.id.edit -> {
                                onInteractionListener.onEdit(post)
                                true
                            }
                            else -> false
                        }
                    }
                }.show()
            }

            like.setOnClickListener {
                onInteractionListener.onLike(post)
            }

            share.setOnClickListener {
                onInteractionListener.onShare(post)
                share.text = formatNumber(post.shares)
            }
        }
    }

}

object PostDiffCallback : DiffUtil.ItemCallback<Post>() {
    override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean =
        oldItem == newItem
}