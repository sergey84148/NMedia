package ru.netology.nmedia.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.netology.nmedia.R
import ru.netology.nmedia.databinding.CardPostBinding
import ru.netology.nmedia.dto.Post

typealias OnLikeListener = (post: Post) -> Unit
typealias OnShareListener = (post: Post) -> Unit

class PostsAdapter(
    private val onLikeListener: OnLikeListener,
    private val onShareListener: OnShareListener
) : ListAdapter<Post, PostsAdapter.PostViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Post>() {
            override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = CardPostBinding.inflate(inflater, parent, false)
        return PostViewHolder(binding, onLikeListener, onShareListener)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))  // Используем метод getItem() адаптера
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

                updateLikeIcon(post)

                like.setOnClickListener { onLikeListener(post) }
                share.setOnClickListener { onShareListener(post) }
            }
        }

        private fun updateLikeIcon(post: Post) {
            binding.like.setImageResource(if (post.likedByMe) R.drawable.ic_liked_24 else R.drawable.ic_like_24)
        }

        private fun formatNumber(value: Int): String {
            return when {
                value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
                value >= 1000 -> "%.1fK".format(value / 1000.0)
                else -> "$value"
            }
        }
    }
}