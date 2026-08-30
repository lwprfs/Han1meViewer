package com.yenaly.han1meviewer.HentaiMama

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface HentaiMamaService {

    @GET("/")
    suspend fun getHomePage(): Response<ResponseBody>

    @GET("tvshows/page/{page}/")
    suspend fun getLatestVideos(
        @Path("page") page: Int,
    ): Response<ResponseBody>

    // New: Popular videos endpoint (same as Tachiyomi extension)
    @GET("advance-search/page/{page}/")
    suspend fun getPopularVideos(
        @Path("page") page: Int,
        @Query("submit") submit: String = "Submit",
        @Query("filter") filter: String = "weekly",
    ): Response<ResponseBody>

    @GET("advance-search/page/{page}/")
    suspend fun getFilteredVideos(
        @Path("page") page: Int,
        @Query("submit") submit: String = "Submit",
        @Query("filter") filter: String? = null,
        @Query("genres_filter[]") genres: Set<String>? = null,
        @Query("years_filter[]") years: Set<String>? = null,
        @Query("studios_filter[]") studios: Set<String>? = null,
    ): Response<ResponseBody>

    @GET("page/{page}/")
    suspend fun searchVideos(
        @Path("page") page: Int,
        @Query("s") query: String,
    ): Response<ResponseBody>

    @GET("{path}")
    suspend fun getVideoDetail(
        @Path("path", encoded = true) path: String,
    ): Response<ResponseBody>

    @POST
    @FormUrlEncoded
    suspend fun getPlayerContents(
        @Url url: String = HentaiMamaConstants.API_URL,
        @Field("action") action: String = "get_player_contents",
        @Field("a") a: String,
    ): Response<ResponseBody>
}