package ru.netology.nmedia.adapter


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.netology.nmedia.R
import ru.netology.nmedia.databinding.CardPostBinding
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.util.ImageLoader

// Интерфейс для обработки взаимодействий с элементами списка
interface OnInteractionListener {
    fun onLike(post: Post) {}
    fun onEdit(post: Post) {}
    fun onRemove(post: Post) {}
    fun onShare(post: Post) {}
    fun onOpenPhoto(url: String) {}
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
    }
}

class PostViewHolder(
    internal val binding: CardPostBinding,
    private val onInteractionListener: OnInteractionListener,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(post: Post) {
        binding.apply {
            author.text = post.author
            content.text = post.content

            like.isChecked = post.likedByMe
            like.text = post.likes.toString()
            //menu.isVisible = post.ownedByMe

            ImageLoader.loadAvatar(itemView.context, post.authorAvatar, avatar)

            // 👇 ОБРАБОТКА ВЛОЖЕНИЯ (ИЗОБРАЖЕНИЯ)
            if (post.attachment != null) {
                attachmentContainer.visibility = View.VISIBLE

                // Загружаем изображение
                ImageLoader.loadPostAttachment(
                    context = itemView.context,
                    attachment = post.attachment,
                    imageView = attachmentImage
                )

                // Отображаем описание, если есть
                if (!post.attachment.description.isNullOrEmpty()) {
                    attachmentDescription.text = post.attachment.description
                    attachmentDescription.visibility = View.VISIBLE
                } else {
                    attachmentDescription.visibility = View.GONE
                }

                // 👇 ОБРАБОТЧИК КЛИКА НА ИЗОБРАЖЕНИЕ
                attachmentImage.setOnClickListener {
                    val fullUrl = "http://10.0.2.2:9999/media/${post.attachment.url}"
                    onInteractionListener.onOpenPhoto(fullUrl)
                }
            } else {
                attachmentContainer.visibility = View.GONE
            }

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