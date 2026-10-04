package com.yenaly.han1meviewer.HentaiMama.data.remote

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

    @GET("advance-search/")
    suspend fun getFilteredVideosPaged(
        @Query("page") page: Int,
        @Query("submit") submit: String = "Submit",
        @Query("filter") filter: String? = null,
        @Query("genres_filter[]") genres: List<String>? = null,
        @Query("years_filter[]") years: List<String>? = null,
        @Query("studios_filter[]") studios: List<String>? = null,
    ): Response<ResponseBody>

    @GET("genre/{slug}/")
    suspend fun getGenrePage(
        @Path("slug") slug: String,
        @Query("filter") filter: String? = null,
    ): Response<ResponseBody>

    @GET("genre/{slug}/page/{page}/")
    suspend fun getGenrePagePaged(
        @Path("slug") slug: String,
        @Path("page") page: Int,
        @Query("filter") filter: String? = null,
    ): Response<ResponseBody>

    @GET("genre/{slug}/")
    suspend fun getGenrePageSearch(
        @Path("slug") slug: String,
        @Query("s") query: String,
    ): Response<ResponseBody>

    @GET("genre/{slug}/page/{page}/")
    suspend fun getGenrePageSearchPaged(
        @Path("slug") slug: String,
        @Path("page") page: Int,
        @Query("s") query: String,
    ): Response<ResponseBody>

    @GET("studio/{slug}/")
    suspend fun getStudioPage(
        @Path("slug") slug: String,
        @Query("filter") filter: String? = null,
    ): Response<ResponseBody>

    @GET
    suspend fun getVideoDetail(
        @Url url: String,
    ): Response<ResponseBody>
}
