package ru.netology.nmedia.error

import android.database.SQLException
import java.io.IOException

sealed class AppError() : RuntimeException() {
    companion object {
        fun from(e: Throwable): AppError = when (e) {
            is AppError -> e
            is SQLException -> DbError()
            is IOException -> NetworkError()
            else -> UnknownError()
        }
    }
}

class ApiError(code1: Int, code: String) : AppError()

class NetworkError : AppError()
class DbError : AppError()
class UnknownError : AppError()