package ru.netology.nmedia.api

import retrofit2.Retrofit
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import ru.netology.nmedia.auth.AppAuth

// Этот делегат создает сервис (например, PostsApiService), а не просто Retrofit
class RetrofitServiceDelegate<T>(
    private val baseUrl: String,
    private val serviceClass: Class<T>,
    private val builder: () -> Retrofit.Builder
) : ReadOnlyProperty<Any?, T> {

    private var service: T? = null

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (service == null || !AppAuth.isInitialized()) {
            if (!AppAuth.isInitialized()) {
                // Если Auth не готов, выбрасываем исключение, чтобы повторить попытку позже.
                throw IllegalStateException("AppAuth not ready")
            }
            val retrofit = builder()
                .baseUrl(baseUrl)
                .build()

            service = retrofit.create(serviceClass)
        }
        return service!!
    }
}