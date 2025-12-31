package ru.netology.nmedia.utils

object Utils {

    @JvmStatic
    fun formatNumber(value: Int): String {
        return when {
            value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
            value >= 1000 -> "%.1fK".format(value / 1000.0)
            else -> "$value"
        }
    }
}