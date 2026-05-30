package com.example.data.repository

import com.example.data.database.ChatDao
import com.example.data.model.UserEntity
import com.example.data.model.MessageEntity
import com.example.data.model.FriendRequestEntity
import com.example.data.model.FriendshipEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import android.util.Log

class ChatRepository(private val chatDao: ChatDao) {

    val allUsers: Flow<List<UserEntity>> = chatDao.getAllUsersFlow()
    val localProfiles: Flow<List<UserEntity>> = chatDao.getLocalProfilesFlow()

    fun getChatMessages(user1: String, user2: String): Flow<List<MessageEntity>> =
        chatDao.getChatMessagesFlow(user1, user2)

    fun getPendingRequests(receiver: String): Flow<List<FriendRequestEntity>> =
        chatDao.getPendingRequestsFlow(receiver)

    fun getAllRequestsForUser(username: String): Flow<List<FriendRequestEntity>> =
        chatDao.getAllRequestsFlow(username)

    fun getFriendships(username: String): Flow<List<FriendshipEntity>> =
        chatDao.getFriendshipsFlow(username)

    suspend fun insertUser(user: UserEntity) = chatDao.insertUser(user)

    suspend fun updateUser(user: UserEntity) = chatDao.updateUser(user)

    suspend fun getUserByUsername(username: String): UserEntity? = chatDao.getUserByUsername(username)

    suspend fun insertMessage(message: MessageEntity): Long = chatDao.insertMessage(message)

    suspend fun sendFriendRequest(sender: String, receiver: String): Boolean {
        // Verify receiver exists
        val receiverUser = chatDao.getUserByUsername(receiver) ?: return false
        if (sender == receiver) return false

        // Check if already friends
        val friendships = chatDao.getFriendshipsFlow(sender).firstOrNull() ?: emptyList()
        val isAlreadyFriend = friendships.any { 
            (it.username1 == sender && it.username2 == receiver) || 
            (it.username1 == receiver && it.username2 == sender) 
        }
        if (isAlreadyFriend) return false

        // Check if request already exists
        val requests = chatDao.getAllRequestsFlow(sender).firstOrNull() ?: emptyList()
        val requestExists = requests.any {
            (it.senderUsername == sender && it.receiverUsername == receiver) ||
            (it.senderUsername == receiver && it.receiverUsername == sender)
        }
        if (requestExists) return false

        val request = FriendRequestEntity(
            senderUsername = sender,
            receiverUsername = receiver,
            status = "PENDING"
        )
        chatDao.insertFriendRequest(request)
        return true
    }

    suspend fun acceptFriendRequest(requestId: Long): Boolean {
        val request = chatDao.getRequestById(requestId) ?: return false
        chatDao.updateRequestStatus(requestId, "ACCEPTED")
        
        // Insert into friendship table
        val friendship = FriendshipEntity(
            username1 = request.senderUsername,
            username2 = request.receiverUsername
        )
        chatDao.insertFriendship(friendship)
        return true
    }

    suspend fun declineFriendRequest(requestId: Long): Boolean {
        val request = chatDao.getRequestById(requestId) ?: return false
        chatDao.updateRequestStatus(requestId, "DECLINED")
        return true
    }

    suspend fun removeFriendship(user1: String, user2: String) {
        chatDao.deleteFriendship(user1, user2)
    }

    suspend fun seedDatabase() {
        // Only seed if empty
        val existingUsers = chatDao.getAllUsersFlow().firstOrNull() ?: emptyList()
        if (existingUsers.isNotEmpty()) {
            Log.d("ChatRepository", "Database already seeded. Skipping...")
            return
        }

        Log.d("ChatRepository", "Seeding database with default users and chats...")

        // Local profiles of who "you" can be (Allows switching active accounts)
        val alice = UserEntity("alice", "Alice Cooper", "#AB47BC", "Online", "Building Jetpack Compose layouts! ✨", isMe = true)
        val bob = UserEntity("bob", "Bob Vance", "#26A69A", "Away", "Vance Refrigeration ❄️", isMe = true)
        val charlie = UserEntity("charlie", "Charlie Brown", "#FF7043", "Busy", "Sigh... Let's debug this bug 💤", isMe = true)

        // Simulated other participants
        val geminiObj = UserEntity("gemini", "Gemini AI", "#1E88E5", "Online", "Always ready with smart replies! 🤖", isMe = false)
        val devDave = UserEntity("dave", "Dev Dave", "#78909C", "Busy", "Refactoring... Do not disturb", isMe = false)
        val designTina = UserEntity("tina", "Designer Tina", "#EC407A", "Online", "Polishing Material 3 designs 🎨", isMe = false)
        val mysteryUser = UserEntity("mystery", "John Doe", "#9E9E9E", "Offline", "Shy offline member", isMe = false)

        listOf(alice, bob, charlie, geminiObj, devDave, designTina, mysteryUser).forEach {
            chatDao.insertUser(it)
        }

        // Alice's friendships
        chatDao.insertFriendship(FriendshipEntity(username1 = "alice", username2 = "gemini"))
        // Bob's friendships
        chatDao.insertFriendship(FriendshipEntity(username1 = "bob", username2 = "gemini"))
        // Charlie's friendships
        chatDao.insertFriendship(FriendshipEntity(username1 = "charlie", username2 = "dave"))

        // Pending Friend Requests:
        // Dave sent a friend request to Alice
        chatDao.insertFriendRequest(FriendRequestEntity(senderUsername = "dave", receiverUsername = "alice", status = "PENDING"))
        // Tina sent a friend request to Bob
        chatDao.insertFriendRequest(FriendRequestEntity(senderUsername = "tina", receiverUsername = "bob", status = "PENDING"))
        // Charlie sent a request to Tina
        chatDao.insertFriendRequest(FriendRequestEntity(senderUsername = "charlie", receiverUsername = "tina", status = "PENDING"))

        // Default Chat history
        val initialMessages = listOf(
            MessageEntity(senderUsername = "gemini", receiverUsername = "alice", content = "Hey Alice! Super excited to be in ChatPulse! Ask me anything! 🚀"),
            MessageEntity(senderUsername = "alice", receiverUsername = "gemini", content = "Hey Gemini! Just testing out this real-time app layout, looks amazing!"),
            MessageEntity(senderUsername = "gemini", receiverUsername = "alice", content = "It looks absolutely fantastic. Let's make sure our users can chat seamlessly!"),
            
            MessageEntity(senderUsername = "gemini", receiverUsername = "bob", content = "Hey Bob! Want to talk about cooling systems? 🥶"),
            MessageEntity(senderUsername = "dave", receiverUsername = "charlie", content = "Charlie, my code is compiling since 2 hours! Any suggestions?"),
            MessageEntity(senderUsername = "charlie", receiverUsername = "dave", content = "Grab a coffee, Dave. Coffee solves compile delays!")
        )
        
        initialMessages.forEach {
            chatDao.insertMessage(it)
        }
    }
}
