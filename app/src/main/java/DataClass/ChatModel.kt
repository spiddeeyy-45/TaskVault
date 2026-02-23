package DataClass

data class ChatModel(
    val chatId: String = "",
    val friendUid: String = "",
    val lastMessage: String? = null,
    val lastMessageTime: Long? = null,
    val lastTimestamp: Long = 0L,
    val unreadCount: Int = 0
)


