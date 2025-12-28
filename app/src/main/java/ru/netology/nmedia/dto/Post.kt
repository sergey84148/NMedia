package ru.netology.nmedia.dto

import android.os.Parcel
import android.os.Parcelable

/**
 * Представляет собой сущность поста.
 *
 * @param id уникальный идентификатор поста
 * @param author автор поста
 * @param content основной текст поста
 * @param published дата публикации
 * @param likes количество лайков
 * @param shares количество шарингов
 * @param video ссылка на видео (может быть пустой)
 * @param likedByMe признак, что пост понравился пользователю
 */
data class Post(
    val id: Long,
    val author: String,
    val content: String,
    val published: String,
    var likes: Int,
    var shares: Int,
    val video: String? = null,
    var likedByMe: Boolean
) : Parcelable {

    /**
     * Конструктор для десериализации из Parcel.
     */
    constructor(parcel: Parcel) : this(
        id = parcel.readLong(),
        author = parcel.readString()!!,
        content = parcel.readString()!!,
        published = parcel.readString()!!,
        likes = parcel.readInt(),
        shares = parcel.readInt(),
        video = parcel.readString(), // nullable field
        likedByMe = parcel.readByte().toInt() != 0
    )

    /**
     * Обязательное описание содержания объекта.
     */
    override fun describeContents(): Int = 0

    /**
     * Метод записи объекта в Parcel.
     */
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(id)
        dest.writeString(author)
        dest.writeString(content)
        dest.writeString(published)
        dest.writeInt(likes)
        dest.writeInt(shares)
        dest.writeString(video)
        dest.writeByte((if (likedByMe) 1 else 0).toByte())
    }

    /**
     * Креатор для воссоздания объекта из Parcel.
     */
    companion object CREATOR : Parcelable.Creator<Post> {
        override fun createFromParcel(parcel: Parcel): Post = Post(parcel)
        override fun newArray(size: Int): Array<Post?> = arrayOfNulls(size)
    }
}