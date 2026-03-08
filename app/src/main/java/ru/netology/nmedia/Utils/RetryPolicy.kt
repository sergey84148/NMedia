package ru.netology.nmedia.utils

object RetryPolicy {
    const val MAX_RETRIES = 3
    const val INITIAL_BACKOFF_MS = 1000L // 1 секунда

    /**
     * Возвращает задержку для повторной попытки с экспоненциальным ростом
     * retryCount: 0 -> 1000ms, 1 -> 2000ms, 2 -> 4000ms, 3 -> 8000ms
     */
    fun getBackoffDelay(retryCount: Int): Long {
        return INITIAL_BACKOFF_MS * (1 shl retryCount)
    }


     //Проверяет, можно ли повторить попытку

    fun canRetry(retryCount: Int): Boolean {
        return retryCount < MAX_RETRIES
    }
}