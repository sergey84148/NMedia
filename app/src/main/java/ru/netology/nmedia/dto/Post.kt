package ru.netology.nmedia.dto

import android.os.Parcel
import android.os.Parcelable

data class Post(
    val id: Long,
    val author: String,
    val content: String,
    val published: String,
    var likes: Int,
    var shares: Int,
    val link: String?,
    val video: String? = null,
    var likedByMe: Boolean
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readLong(),
        author = parcel.readString()!!,
        content = parcel.readString()!!,
        published = parcel.readString()!!,
        likes = parcel.readInt(),
        shares = parcel.readInt(),
        link = parcel.readString(),
        video = parcel.readString(),
        likedByMe = parcel.readByte().toInt() != 0
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(id)
        dest.writeString(author)
        dest.writeString(content)
        dest.writeString(published)
        dest.writeInt(likes)
        dest.writeInt(shares)
        dest.writeString(link)
        dest.writeString(video)
        dest.writeByte((if (likedByMe) 1 else 0).toByte())
    }

    companion object CREATOR : Parcelable.Creator<Post> {
        override fun createFromParcel(parcel: Parcel): Post = Post(parcel)
        override fun newArray(size: Int): Array<Post?> = arrayOfNulls(size)
    }
}