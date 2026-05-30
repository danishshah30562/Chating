package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserEntity
import com.example.data.model.MessageEntity
import com.example.data.model.FriendRequestEntity
import com.example.data.repository.ChatRepository
import com.example.network.GeminiClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.util.Log

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    private val _currentMeUsername = MutableStateFlow("alice")
    val currentMeUsername: StateFlow<String> = _currentMeUsername.asStateFlow()

    private val _activeChatUsername = MutableStateFlow<String?>(null)
    val activeChatUsername: StateFlow<String?> = _activeChatUsername.asStateFlow()

    // Error and loading states
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val allUsers: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val localProfiles: StateFlow<List<UserEntity>> = repository.localProfiles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Active User Object
    val activeMeUser: StateFlow<UserEntity?> = _currentMeUsername
        .flatMapLatest { username ->
            allUsers.map { list -> list.find { it.username == username } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Current Chats
    val currentChatMessages: StateFlow<List<MessageEntity>> = combine(
        _currentMeUsername,
        _activeChatUsername
    ) { me, partner ->
        if (partner == null) emptyFlow()
        else repository.getChatMessages(me, partner)
    }.flatMapLatest { it }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Friendship status: retrieve all friends of the current user
    val friends: StateFlow<List<UserEntity>> = _currentMeUsername
        .flatMapLatest { me ->
            repository.getFriendships(me).combine(allUsers) { friendships, users ->
                friendships.mapNotNull { f ->
                    val partnerUsername = if (f.username1 == me) f.username2 else f.username1
                    users.find { it.username == partnerUsername }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently incoming pending requests
    val pendingRequests: StateFlow<List<FriendRequestEntity>> = _currentMeUsername
        .flatMapLatest { me -> repository.getPendingRequests(me) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Sent/Received friend requests (for showing pending states in browse search)
    val allRequestsOfActiveMe: StateFlow<List<FriendRequestEntity>> = _currentMeUsername
        .flatMapLatest { me -> repository.getAllRequestsForUser(me) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.seedDatabase()
            // Default select first chat partner
            delay(500)
            if (_activeChatUsername.value == null) {
                _activeChatUsername.value = "gemini"
            }
        }
    }

    fun switchActiveProfile(username: String) {
        viewModelScope.launch {
            _currentMeUsername.value = username
            // Attempt to keep existing partner, otherwise fallback to standard companion
            if (username == _activeChatUsername.value) {
                val alternate = if (username == "alice") "bob" else "alice"
                _activeChatUsername.value = alternate
            }
        }
    }

    fun selectChatPartner(username: String) {
        _activeChatUsername.value = username
    }

    fun updateMyStatus(newStatus: String, newMessage: String) {
        viewModelScope.launch {
            val me = activeMeUser.value ?: return@launch
            val updated = me.copy(status = newStatus, statusMessage = newMessage)
            repository.updateUser(updated)
        }
    }

    fun sendMessage(contentText: String) {
        if (contentText.isBlank()) return
        val sender = _currentMeUsername.value
        val receiver = _activeChatUsername.value ?: return

        viewModelScope.launch {
            val messageObj = MessageEntity(
                senderUsername = sender,
                receiverUsername = receiver,
                content = contentText
            )
            repository.insertMessage(messageObj)

            // If messaging Gemini, trigger real-time AI reply
            if (receiver == "gemini") {
                triggerGeminiReply(contentText)
            } else if (receiver == "dave") {
                triggerDaveReply(contentText)
            } else if (receiver == "tina") {
                triggerTinaReply(contentText)
            }
        }
    }

    private suspend fun triggerGeminiReply(prompt: String) {
        // Set Gemini status typing
        val geminiUser = repository.getUserByUsername("gemini")
        if (geminiUser != null) {
            repository.updateUser(geminiUser.copy(status = "Typing...", statusMessage = "Computing replies..."))
        }

        // Simulate typing delay
        delay(1500)

        // Get context history from Room
        val history = repository.getChatMessages(_currentMeUsername.value, "gemini").first()

        val reply = GeminiClient.getReply(prompt, history)

        // Save reply & reset typing status
        val latestGemini = repository.getUserByUsername("gemini")
        if (latestGemini != null) {
            repository.updateUser(latestGemini.copy(status = "Online", statusMessage = "Always ready with queries! 🤖"))
        }

        repository.insertMessage(
            MessageEntity(
                senderUsername = "gemini",
                receiverUsername = _currentMeUsername.value,
                content = reply
            )
        )
    }

    private fun triggerDaveReply(prompt: String) {
        viewModelScope.launch {
            val daveUser = repository.getUserByUsername("dave")
            if (daveUser != null) {
                repository.updateUser(daveUser.copy(status = "Typing...", statusMessage = "Dave is resolving bugs..."))
            }
            delay(1200)

            val coderJokes = listOf(
                "Why do programmers wear glasses? Because they can't C#! 😂",
                "There are 10 sorts of people in the world... those who understand binary, and those who don't.",
                "My code isn't working, but I have no idea why... Oh never mind, I missed a semicolon!",
                "Have you tried clearing Gradle caches? That solves everything."
            )
            val randomReply = coderJokes.random()

            val latestDave = repository.getUserByUsername("dave")
            if (latestDave != null) {
                repository.updateUser(latestDave.copy(status = "Busy", statusMessage = "Refactoring... Do not disturb"))
            }

            repository.insertMessage(
                MessageEntity(
                    senderUsername = "dave",
                    receiverUsername = _currentMeUsername.value,
                    content = randomReply
                )
            )
        }
    }

    private fun triggerTinaReply(prompt: String) {
        viewModelScope.launch {
            val tinaUser = repository.getUserByUsername("tina")
            if (tinaUser != null) {
                repository.updateUser(tinaUser.copy(status = "Typing...", statusMessage = "Tina is choosing design specs..."))
            }
            delay(1000)

            val creativeReplies = listOf(
                "Ooh! That typography choice is absolutely gorgeous! Pairing it with modern line spacing.",
                "Let's add more whitespace! Negative space is the soul of interface design 🎨",
                "I am testing a lightweight dark theme. Looks sleek!",
                "Did you try compiling the screen? The animations flow beautifully!"
            )
            val randomReply = creativeReplies.random()

            val latestTina = repository.getUserByUsername("tina")
            if (latestTina != null) {
                repository.updateUser(latestTina.copy(status = "Online", statusMessage = "Polishing Material 3 designs 🎨"))
            }

            repository.insertMessage(
                MessageEntity(
                    senderUsername = "tina",
                    receiverUsername = _currentMeUsername.value,
                    content = randomReply
                )
            )
        }
    }

    fun sendFriendRequestTo(targetUsername: String) {
        viewModelScope.launch {
            val success = repository.sendFriendRequest(_currentMeUsername.value, targetUsername)
            if (!success) {
                _errorMessage.value = "Cannot send request. User does not exist or already connected."
                delay(3000)
                _errorMessage.value = null
            } else {
                // If sent to dave or tina or mystery, auto-accept or handle it interactively!
                if (targetUsername == "dave") {
                    delay(2000)
                    autoAcceptSentRequest(targetUsername)
                } else if (targetUsername == "tina") {
                    delay(4000)
                    autoAcceptSentRequest(targetUsername)
                }
            }
        }
    }

    private suspend fun autoAcceptSentRequest(simUser: String) {
        // Find the pending requests
        val requests = repository.getPendingRequests(simUser).first()
        val match = requests.find { it.senderUsername == _currentMeUsername.value }
        if (match != null) {
            repository.acceptFriendRequest(match.id)
            Log.d("ChatViewModel", "Simulated user '$simUser' accepted friend request of ${_currentMeUsername.value}")
        }
    }

    fun acceptFriendRequest(requestId: Long) {
        viewModelScope.launch {
            repository.acceptFriendRequest(requestId)
        }
    }

    fun declineFriendRequest(requestId: Long) {
        viewModelScope.launch {
            repository.declineFriendRequest(requestId)
        }
    }

    fun unfriend(targetUsername: String) {
        viewModelScope.launch {
            repository.removeFriendship(_currentMeUsername.value, targetUsername)
            // If active chat partner was this user, reset it
            if (_activeChatUsername.value == targetUsername) {
                _activeChatUsername.value = "gemini"
            }
        }
    }
}
