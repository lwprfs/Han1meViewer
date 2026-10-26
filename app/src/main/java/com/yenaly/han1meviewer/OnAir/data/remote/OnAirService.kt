package com.yenaly.han1meviewer.OnAir.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface OnAirService {

    @GET("./")
    suspend fun getHomePage(
        @Query("page") page: Int? = null,
        @Query("q") query: String? = null,
        @Query("field") field: String? = null,
        @Query("length") length: String? = null,
        @Query("date") date: String? = null,
        @Query("sort") sort: String? = null,
    ): Response<ResponseBody>

    @GET("watch/{videoId}")
    suspend fun getWatchPage(
        @Path("videoId") videoId: String,
        @Query("quality") quality: String? = null,
    ): Response<ResponseBody>

    @GET("pictures")
    suspend fun getPictures(
        @Query("page") page: Int? = null,
        @Query("q") query: String? = null,
    ): Response<ResponseBody>

    @GET("tags")
    suspend fun getTags(
        @Query("page") page: Int? = null,
        @Query("q") query: String? = null,
        @Query("sort") sort: String? = null,
    ): Response<ResponseBody>

    @GET("tag/{tagName}")
    suspend fun getTagPage(
        @Path("tagName") tagName: String,
        @Query("page") page: Int? = null,
        @Query("q") query: String? = null,
        @Query("field") field: String? = null,
        @Query("length") length: String? = null,
        @Query("date") date: String? = null,
        @Query("sort") sort: String? = null,
    ): Response<ResponseBody>

    @GET("user/{username}")
    suspend fun getUserProfile(
        @Path("username") username: String,
        @Query("page") page: Int? = null,
    ): Response<ResponseBody>

    @GET("announcements")
    suspend fun getAnnouncements(): Response<ResponseBody>

    @GET
    suspend fun getRaw(
        @Url url: String,
    ): Response<ResponseBody>
}
