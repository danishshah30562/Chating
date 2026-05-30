package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val username: String,
    val displayName: String,
    val avatarColorHex: String, // e.g. "#FF9800"
    val status: String, // Online, Away, Busy, Offline
    val statusMessage: String,
    val isMe: Boolean = false,
    val lastActiveTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderUsername: String,
    val receiverUsername: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Entity(tableName = "friend_requests")
data class FriendRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderUsername: String,
    val receiverUsername: String,
    val status: String, // PENDING, ACCEPTED, DECLINED
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "friendships")
data class FriendshipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username1: String,
    val username2: String,
    val timestamp: Long = System.currentTimeMillis()
)
