package com.yenaly.han1meviewer.RouVideo.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface RouVideoService {
    @GET
    suspend fun get(@Url url: String): Response<ResponseBody>
}
