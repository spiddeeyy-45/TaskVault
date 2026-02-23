package DataClass

data class MessageModel(
    val senderId: String = "",
    val message: String = "",
    val timestamp: Long = 0L,
    val type: String = "text",
    val imageUrl: String? = null
)