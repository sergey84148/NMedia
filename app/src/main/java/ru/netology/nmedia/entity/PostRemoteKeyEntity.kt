package ru.netology.nmedia.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "post_remote_keys")
data class PostRemoteKeyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: KeyType,
    val key: Long = 0
) {
    enum class KeyType {
        TOP,
        BOTTOM
    }
}