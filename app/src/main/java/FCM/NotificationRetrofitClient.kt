package FCM

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NotificationRetrofitClient {
    private const val BASE_URL = "https://taskvaultfcmbackend.onrender.com"

    val api: NotificationApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NotificationApi::class.java)
    }
}