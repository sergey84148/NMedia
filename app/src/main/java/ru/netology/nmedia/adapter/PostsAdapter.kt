package ru.netology.nmedia.adapter

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.view.animation.BounceInterpolator
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ru.netology.nmedia.BuildConfig
import ru.netology.nmedia.R
import ru.netology.nmedia.databinding.CardAdBinding
import ru.netology.nmedia.databinding.CardPostBinding
import ru.netology.nmedia.databinding.ItemDateSeparatorBinding
import ru.netology.nmedia.dto.Ad
import ru.netology.nmedia.dto.DateSeparator
import ru.netology.nmedia.dto.FeedItem
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.util.ImageLoader

interface OnInteractionListener {
    fun onLike(post: Post) {}
    fun onEdit(post: Post) {}
    fun onRemove(post: Post) {}
    fun onShare(post: Post) {}
    fun onOpenPhoto(url: String) {}
}

class PostsAdapter(
    private val onInteractionListener: OnInteractionListener,
) : PagingDataAdapter<FeedItem, RecyclerView.ViewHolder>(PostDiffCallback) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is DateSeparator -> R.layout.item_date_separator
            is Ad -> R.layout.card_ad
            is Post -> R.layout.card_post
            null -> error("unknown item type")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            R.layout.item_date_separator -> {
                val binding = ItemDateSeparatorBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                DateSeparatorViewHolder(binding)
            }
            R.layout.card_post -> {
                val binding = CardPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                PostViewHolder(binding, onInteractionListener)
            }
            R.layout.card_ad -> {
                val binding = CardAdBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                AdViewHolder(binding)
            }
            else -> error("unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any?>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val postViewHolder = holder as? PostViewHolder
            payloads.forEach { payload ->
                (payload as? PayLoad)?.let { payLoad ->
                    postViewHolder?.bind(payLoad)
                }
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DateSeparator -> (holder as? DateSeparatorViewHolder)?.bind(item)
            is Ad -> (holder as? AdViewHolder)?.bind(item)
            is Post -> (holder as? PostViewHolder)?.bind(item)
            null -> error("unknown item type")
        }
    }
}

class DateSeparatorViewHolder(
    private val binding: ItemDateSeparatorBinding,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(separator: DateSeparator) {
        binding.separatorTitle.text = separator.title
    }
}

class AdViewHolder(
    private val binding: CardAdBinding,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(ad: Ad) {
        Glide.with(binding.image.context)
            .load("${BuildConfig.BASE_URL}/media/${ad.image}")
            .into(binding.image)
    }
}

class PostViewHolder(
    private val binding: CardPostBinding,
    private val onInteractionListener: OnInteractionListener,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(post: Post) {
        binding.apply {
            author.text = post.author
            content.text = post.content

            like.isChecked = post.likedByMe
            like.text = post.likes.toString()
            menu.isVisible = post.ownedByMe

            ImageLoader.loadAvatar(itemView.context, post.authorAvatar, avatar)

            if (post.attachment != null) {
                attachmentContainer.visibility = ViewGroup.VISIBLE

                ImageLoader.loadPostAttachment(
                    context = itemView.context,
                    attachment = post.attachment,
                    imageView = attachmentImage
                )

                attachmentImage.setOnClickListener {
                    val fullUrl = "http://10.0.2.2:9999/media/${post.attachment.url}"
                    onInteractionListener.onOpenPhoto(fullUrl)
                }
            } else {
                attachmentContainer.visibility = ViewGroup.GONE
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
                val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1F, 1.25F, 1F)
                val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1F, 1.25F, 1F)
                ObjectAnimator.ofPropertyValuesHolder(it, scaleX, scaleY).apply {
                    duration = 500
                    repeatCount = 100
                    interpolator = BounceInterpolator()
                }.start()
                onInteractionListener.onLike(post)
            }

            share.setOnClickListener {
                onInteractionListener.onShare(post)
            }
        }
    }

    fun bind(payload: PayLoad) {
        payload.likedByMe?.also { likedByMe ->
            binding.like.isChecked = likedByMe
            val currentLikes = binding.like.text.toString().toIntOrNull() ?: 0
            binding.like.text = if (likedByMe) {
                (currentLikes + 1).toString()
            } else {
                (currentLikes - 1).toString()
            }

            if (likedByMe) {
                ObjectAnimator.ofPropertyValuesHolder(
                    binding.like,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0F, 1.2F, 1.0F),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0F, 1.2F, 1.0F)
                ).start()
            }
        }

        payload.content?.let { content ->
            binding.content.text = content
        }
    }
}

data class PayLoad(
    val likedByMe: Boolean? = null,
    val content: String? = null,
)

object PostDiffCallback : DiffUtil.ItemCallback<FeedItem>() {
    override fun areItemsTheSame(oldItem: FeedItem, newItem: FeedItem): Boolean {
        return if (oldItem::class != newItem::class) {
            false
        } else {
            oldItem.id == newItem.id
        }
    }

    override fun areContentsTheSame(oldItem: FeedItem, newItem: FeedItem): Boolean {
        return oldItem == newItem
    }

    override fun getChangePayload(oldItem: FeedItem, newItem: FeedItem): Any? {
        if (oldItem !is Post || newItem !is Post) return null

        return PayLoad(
            likedByMe = if (oldItem.likedByMe != newItem.likedByMe) newItem.likedByMe else null,
            content = if (oldItem.content != newItem.content) newItem.content else null,
        )
    }
}