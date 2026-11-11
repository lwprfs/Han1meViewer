package com.yenaly.han1meviewer.XHamster.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Url

interface XHamsterService {
    @GET
    suspend fun get(@Url url: String): Response<ResponseBody>

    @GET
    suspend fun getJson(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
    ): Response<ResponseBody>

    @POST
    suspend fun post(
        @Url url: String,
        @Body body: RequestBody,
        @HeaderMap headers: Map<String, String>,
    ): Response<ResponseBody>
}
