package com.yenaly.han1meviewer.logic.network.service

import com.yenaly.han1meviewer.logic.model.github.Artifacts
import com.yenaly.han1meviewer.logic.model.github.CommitComparison
import com.yenaly.han1meviewer.logic.model.github.Release
import com.yenaly.han1meviewer.logic.model.github.WorkflowRuns
import com.yenaly.han1meviewer.logic.network.HUpdater
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface HGitHubService {

    @GET("releases/latest")
    suspend fun getLatestVersion(): Response<Release>

    @GET("actions/workflows/ci.yml/runs?event=push&status=success&per_page=1")
    suspend fun getWorkflowRuns(
        @Query("branch") branch: String = HUpdater.DEFAULT_BRANCH,
    ): Response<WorkflowRuns>

    @GET("compare/{curSha}...{latestSha}")
    suspend fun getCommitComparison(
        @Path("curSha") curSha: String,
        @Path("latestSha") latestSha: String,
    ): Response<CommitComparison>

    @GET
    suspend fun getArtifacts(
        @Url url: String,
    ): Response<Artifacts>

    @GET
    @Streaming
    suspend fun request(
        @Url url: String,
    ): Response<ResponseBody>
}
