package CloudinaryImageHandler

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface retrofitCloudinary {

    @Multipart
    @POST("v1_1/dhbkbmww4/image/upload")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part,
        @Part("upload_preset") preset: RequestBody
    ): CloudinaryResponse


    @Multipart
    @POST("v1_1/dhbkbmww4/raw/upload")
    suspend fun uploadPdf(
        @Part file: MultipartBody.Part,
        @Part("upload_preset") preset: RequestBody
    ): CloudinaryResponse
}