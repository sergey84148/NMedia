package ru.netology.nmedia.utils

import ru.netology.nmedia.dto.DateSeparator
import ru.netology.nmedia.dto.FeedItem
import ru.netology.nmedia.dto.Post
import java.util.Calendar
import java.util.Date

object DateSeparatorHelper {

    private const val MILLIS_IN_DAY = 24 * 60 * 60 * 1000L

    enum class DateGroup(val title: String) {
        TODAY("Сегодня"),
        YESTERDAY("Вчера"),
        LAST_WEEK("На прошлой неделе")
    }

    fun getDateGroup(published: Long): DateGroup {
        val now = System.currentTimeMillis()
        val todayStart = getStartOfDay(now)
        val yesterdayStart = todayStart - MILLIS_IN_DAY
        val twoDaysAgoStart = yesterdayStart - MILLIS_IN_DAY

        return when {
            published >= todayStart -> DateGroup.TODAY
            published >= yesterdayStart -> DateGroup.YESTERDAY
            else -> DateGroup.LAST_WEEK
        }
    }

    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun addDateSeparators(posts: List<Post>): List<FeedItem> {
        if (posts.isEmpty()) return emptyList()

        val result = mutableListOf<FeedItem>()
        var lastGroup: DateGroup? = null
        var separatorId = 0L

        // Сортируем посты по дате (новые сверху)
        val sortedPosts = posts.sortedByDescending { it.published }

        sortedPosts.forEach { post ->
            val currentGroup = getDateGroup(post.published)

            if (lastGroup != currentGroup) {
                val separator = DateSeparator(
                    id = separatorId--,
                    title = currentGroup.title,
                    date = Date(post.published)
                )
                result.add(separator)
                lastGroup = currentGroup
            }
            result.add(post)
        }

        return result
    }
}