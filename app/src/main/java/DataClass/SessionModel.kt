package DataClass
data class SessionModel(
    val sessionId: String = "",
    val ownerUid: String = "",
    val title: String = "",
    val description: String = "",
    val durationMinutes: Long = 0,
    val warnings: Long = 0,
    val timestamp: Long = 0,
    val status: String = "Completed",
    val coverImageUrl: String? = null
)