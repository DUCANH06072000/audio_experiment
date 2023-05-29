package com.example.record_audio;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.Multipart;
import retrofit2.http.PUT;
import retrofit2.http.Part;

public interface ApiService {
    String domain = "https://e6ec-1-53-222-145.ngrok-free.app/";
    ApiService apiService = new Retrofit.Builder().baseUrl(domain).
            addConverterFactory(GsonConverterFactory.create()).build().
            create(ApiService.class);

    @Multipart
    @PUT("process")
    Call<ResponseBody> updateLoadFile (@Part MultipartBody.Part filePart);
}
