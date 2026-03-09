package ru.netology.nmedia.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object RetryPolicy {
    // ========== RETRY POLICY ==========

    const val MAX_RETRIES = 3
    const val INITIAL_BACKOFF_MS = 1000L // 1 секунда

    /**
     * Возвращает задержку для повторной попытки с экспоненциальным ростом
     * retryCount: 0 -> 1000ms, 1 -> 2000ms, 2 -> 4000ms, 3 -> 8000ms
     */
    fun getBackoffDelay(retryCount: Int): Long {
        return INITIAL_BACKOFF_MS * (1 shl retryCount)
    }

    /**
     * Проверяет, можно ли повторить попытку
     */
    fun canRetry(retryCount: Int): Boolean {
        return retryCount < MAX_RETRIES
    }

    // ========== NETWORK UTILS ==========

    /**
     * Проверяет, доступна ли сеть в данный момент
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Для Android 10 и выше
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

         //Возвращает Flow<Boolean> - true когда сеть доступна, false когда недоступна

    fun observeNetwork(context: Context): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Сеть появилась
                trySend(true)
            }

            override fun onLost(network: Network) {
                // Сеть пропала
                trySend(false)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                // Изменились возможности сети (например, появился интернет)
                val hasInternet = when {
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                    else -> false
                }
                trySend(hasInternet)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Отправляем текущее состояние при подписке
        trySend(isNetworkAvailable(context))

        awaitClose {
            // Отписываемся при закрытии Flow
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()


     //Получает тип текущего подключения (для отладки)

    fun getConnectionType(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return "No network"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "Unknown"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Other"
        }
    }
}