package com.yenaly.han1meviewer.HentaiMama

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface HentaiMamaService {

    @GET("hentai-series/")
    suspend fun getPopularVideos(
        @Query("filter") filter: String = "weekly",
    ): Response<ResponseBody>

    @GET("hentai-series/page/{page}/")
    suspend fun getPopularVideosPaged(
        @Path("page") page: Int,
        @Query("filter") filter: String = "weekly",
    ): Response<ResponseBody>

    @GET("hentai-series/")
    suspend fun getLatestVideos(
        @Query("filter") filter: String = "recent",
    ): Response<ResponseBody>

    @GET("hentai-series/page/{page}/")
    suspend fun getLatestVideosPaged(
        @Path("page") page: Int,
        @Query("filter") filter: String = "recent",
    ): Response<ResponseBody>

    @GET("page/{page}/")
    suspend fun searchVideos(
        @Path("page") page: Int,
        @Query("s") query: String,
    ): Response<ResponseBody>

    @GET("advance-search/")
    suspend fun getFilteredVideos(
        @Query("submit") submit: String = "Submit",
        @Query("filter") filter: String? = null,
        @Query("genres_filter[]") genres: List<String>? = null,
        @Query("years_filter[]") years: List<String>? = null,
        @Query("studios_filter[]") studios: List<String>? = null,
    ): Response<ResponseBody>

    @GET("advance-search/page/{page}/")
    suspend fun getFilteredVideosPaged(
        @Path("page") page: Int,
        @Query("submit") submit: String = "Submit",
        @Query("filter") filter: String? = null,
        @Query("genres_filter[]") genres: List<String>? = null,
        @Query("years_filter[]") years: List<String>? = null,
        @Query("studios_filter[]") studios: List<String>? = null,
    ): Response<ResponseBody>

    @GET
    suspend fun getVideoDetail(
        @Url url: String,
    ): Response<ResponseBody>
}
