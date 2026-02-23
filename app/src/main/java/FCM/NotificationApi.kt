package FCM

import DataClass.NotificationRequest
import DataClass.NotificationResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface NotificationApi {

    @POST("send-notification")
    suspend fun sendNotification(
        @Body request: NotificationRequest
    ): Response<NotificationResponse>
}
