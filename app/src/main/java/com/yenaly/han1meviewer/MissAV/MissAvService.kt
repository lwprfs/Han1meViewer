package com.yenaly.han1meviewer.MissAV

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface MissAvService {

    @GET("/")
    suspend fun getHomePage(): Response<ResponseBody>

    @GET("en/today-hot")
    suspend fun getPopularVideos(
        @Query("page") page: Int = 1,
    ): Response<ResponseBody>

    @GET("en/new")
    suspend fun getLatestVideos(
        @Query("page") page: Int = 1,
    ): Response<ResponseBody>

    @GET("{genrePath}")
    suspend fun getGenreVideos(
        @Path("genrePath", encoded = true) genrePath: String,
        @Query("page") page: Int = 1,
        @Query("sort") sort: String? = null,
        @Query("filter") filter: String? = null,
    ): Response<ResponseBody>

    @GET("en/search/{query}")
    suspend fun searchVideos(
        @Path("query") query: String,
        @Query("page") page: Int = 1,
        @Query("sort") sort: String? = null,
        @Query("filter") filter: String? = null,
    ): Response<ResponseBody>

    @GET("{path}")
    suspend fun getVideoDetail(
        @Path("path", encoded = true) path: String,
    ): Response<ResponseBody>

    @POST
    suspend fun searchApi(
        @Url url: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST
    suspend fun relatedApi(
        @Url url: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST
    suspend fun recommendApi(
        @Url url: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>
}