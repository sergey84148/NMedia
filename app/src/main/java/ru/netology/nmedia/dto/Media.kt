package ru.netology.nmedia.dto

import java.io.File

data class Media(val id: String) {
    val url: String = ""

}

data class MediaUpload(val file: File)