package com.yenaly.han1meviewer.FapFolder.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface FapFolderService {

    @GET("./")
    suspend fun getHomePage(): Response<ResponseBody>

    @GET(".")
    suspend fun getHomePageAlt(): Response<ResponseBody>

    @GET("search/")
    suspend fun getSearchLanding(): Response<ResponseBody>

    @GET
    suspend fun getByUrl(@Url url: String): Response<ResponseBody>

    @GET
    suspend fun getSearchUrl(@Url url: String): Response<ResponseBody>

    @GET
    suspend fun getHashtagUrl(@Url url: String): Response<ResponseBody>

    @GET
    suspend fun getPostUrl(@Url url: String): Response<ResponseBody>

    @GET
    suspend fun getGroupUrl(@Url url: String): Response<ResponseBody>

    @GET
    suspend fun getProfileUrl(@Url url: String): Response<ResponseBody>

    @GET
    suspend fun getAlbumUrl(@Url url: String): Response<ResponseBody>

    @GET
    suspend fun getSettingsPage(): Response<ResponseBody>

    @FormUrlEncoded
    @POST("includes/ajax/core/signin.php")
    suspend fun signIn(
        @Field("username_email") usernameOrEmail: String,
        @Field("password") password: String,
        @Field("remember") remember: String = "on",
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("includes/ajax/data/load.php")
    suspend fun loadMore(
        @Field("get") get: String,
        @Field("offset") offset: Int,
        @Field("filter") filter: String? = null,
        @Field("id") id: String? = null,
        @Field("type") type: String? = null,
        @Field("uid") uid: String? = null,
        @Field("premfeed") premfeed: Int = 0,
        @Field("time") time: Int = -1,
        @Field("search") search: String? = null,
        @Field("hashtag") hashtag: String? = null,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("includes/ajax/posts/lightbox.php")
    suspend fun lightbox(
        @Field("id") photoId: String,
        @Field("context") context: String,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("includes/ajax/data/search.php")
    suspend fun autocompleteSearch(
        @Field("query") query: String,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("includes/ajax/posts/reaction.php")
    suspend fun react(
        @Field("do") action: String,
        @Field("id") id: String,
        @Field("reaction") reaction: String? = null,
        @Field("checked_id") checkedId: String? = null,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("includes/ajax/posts/filter.php")
    suspend fun filterPosts(
        @Query("handle") handle: String = "posts",
        @Field("get") get: String,
        @Field("filter") filter: String? = null,
        @Field("id") id: String? = null,
        @Field("offset") offset: Int? = null,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("includes/ajax/posts/filter.php")
    suspend fun filterComments(
        @Query("handle") handle: String = "comments",
        @Field("get") get: String,
        @Field("id") id: String,
        @Field("offset") offset: Int? = null,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("includes/ajax/posts/comment.php")
    suspend fun postComment(
        @Field("handle") handle: String,
        @Field("id") id: String,
        @Field("message") message: String,
        @Field("photo") photo: String? = null,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("includes/ajax/users/connect.php")
    suspend fun connectAction(
        @Field("do") action: String,
        @Field("id") id: String,
        @Field("uid") uid: String? = null,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("includes/ajax/data/live.php")
    suspend fun heartbeat(
        @Field("last_request") lastRequest: String = "0",
        @Field("last_message") lastMessage: String = "0",
        @Field("last_notification") lastNotification: String = "0",
        @Field("last_post") lastPost: String = "0",
    ): Response<ResponseBody>
}
