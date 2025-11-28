package ru.netology.nmedia.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.netology.nmedia.R
import ru.netology.nmedia.databinding.CardPostBinding
import ru.netology.nmedia.dto.Post

typealias OnLikeListener = (post: Post) -> Unit
typealias OnShareListener = (post: Post) -> Unit

class PostsAdapter(
    private val onLikeListener: OnLikeListener,
    private val onShareListener: OnShareListener
) : RecyclerView.Adapter<PostViewHolder>() {

    var list = emptyList<Post>()
        set(value) {
            field = value
            notifyDataSetChanged()  // Важно: перерисовка при обновлении списка
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = CardPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding, onLikeListener, onShareListener)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount(): Int = list.size
}

class PostViewHolder(
    private val binding: CardPostBinding,
    private val onLikeListener: OnLikeListener,
    private val onShareListener: OnShareListener
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(post: Post) {
        binding.apply {
            author.text = post.author
            published.text = post.published
            content.text = post.content
            likeCount.text = formatNumber(post.likes)
            shareCount.text = formatNumber(post.shares)

            // Иконка лайка
            like.setImageResource(
                if (post.likedByMe) R.drawable.ic_liked_24 else R.drawable.ic_like_24
            )
            like.setOnClickListener { onLikeListener(post) }

            // Иконка шера
            share.setOnClickListener { onShareListener(post) }
        }
    }

    // Исправленная функция форматирования
    private fun formatNumber(value: Int): String {
        return when {
            value >= 1_000_000 -> {
                val millions = value / 1_000_000
                val remainder = (value % 1_000_000) / 100_000  // 1 цифра после точки
                "${millions}.${remainder}M"
            }
            value >= 10_000 -> "${value / 1000}K"  // Например, 15000 → 15K
            value > 999 -> {
                val thousands = value / 1000
                val remainder = (value % 1000) / 100  // 1 цифра после точки
                "${thousands}.${remainder}K"
            }
            else -> value.toString()
        }
    }
}
