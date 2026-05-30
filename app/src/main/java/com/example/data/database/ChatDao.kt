package com.example.data.database

import androidx.room.*
import com.example.data.model.UserEntity
import com.example.data.model.MessageEntity
import com.example.data.model.FriendRequestEntity
import com.example.data.model.FriendshipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // Users
    @Query("SELECT * FROM users")
    fun getAllUsersFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE isMe = 1")
    fun getLocalProfilesFlow(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    // Messages
    @Query("""
        SELECT * FROM messages 
        WHERE (senderUsername = :user1 AND receiverUsername = :user2) 
           OR (senderUsername = :user2 AND receiverUsername = :user1)
        ORDER BY timestamp ASC
    """)
    fun getChatMessagesFlow(user1: String, user2: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MessageEntity): Long

    // Friend Requests
    @Query("SELECT * FROM friend_requests WHERE receiverUsername = :receiver AND status = 'PENDING' ORDER BY timestamp DESC")
    fun getPendingRequestsFlow(receiver: String): Flow<List<FriendRequestEntity>>

    @Query("SELECT * FROM friend_requests WHERE senderUsername = :sender OR receiverUsername = :sender")
    fun getAllRequestsFlow(sender: String): Flow<List<FriendRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriendRequest(request: FriendRequestEntity): Long

    @Query("SELECT * FROM friend_requests WHERE id = :id LIMIT 1")
    suspend fun getRequestById(id: Long): FriendRequestEntity?

    @Query("UPDATE friend_requests SET status = :status WHERE id = :requestId")
    suspend fun updateRequestStatus(requestId: Long, status: String)

    // Friendships
    @Query("""
        SELECT * FROM friendships 
        WHERE (username1 = :username OR username2 = :username)
    """)
    fun getFriendshipsFlow(username: String): Flow<List<FriendshipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriendship(friendship: FriendshipEntity)

    @Query("DELETE FROM friendships WHERE (username1 = :user1 AND username2 = :user2) OR (username1 = :user2 AND username2 = :user1)")
    suspend fun deleteFriendship(user1: String, user2: String)
}
