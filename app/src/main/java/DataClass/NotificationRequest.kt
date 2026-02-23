package DataClass

data class NotificationRequest(
    val token: String,
    val title: String,
    val body: String,
    val type: String = "general",
    val senderUid: String = ""
)