package CloudinaryImageHandler

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object CloudinaryClient {
    private const val Base_Url ="https://api.cloudinary.com/"
    val api: retrofitCloudinary by lazy {
        Retrofit.Builder()
            .baseUrl(Base_Url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(retrofitCloudinary::class.java)
    }
}