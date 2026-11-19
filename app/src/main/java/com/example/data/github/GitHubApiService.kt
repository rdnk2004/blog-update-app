package com.example.data.github

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class GitHubRepoResponse(
    @Json(name = "full_name") val fullName: String = "",
    @Json(name = "default_branch") val defaultBranch: String = "main",
    @Json(name = "permissions") val permissions: GitHubRepoPermissions? = null
)

@JsonClass(generateAdapter = true)
data class GitHubRepoPermissions(
    @Json(name = "push") val push: Boolean = false,
    @Json(name = "pull") val pull: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GitHubBranchResponse(
    @Json(name = "name") val name: String = ""
)

@JsonClass(generateAdapter = true)
data class GitHubContentItem(
    @Json(name = "name") val name: String = "",
    @Json(name = "path") val path: String = "",
    @Json(name = "sha") val sha: String = "",
    @Json(name = "type") val type: String = "",
    @Json(name = "download_url") val downloadUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubFileContent(
    @Json(name = "name") val name: String = "",
    @Json(name = "path") val path: String = "",
    @Json(name = "sha") val sha: String = "",
    @Json(name = "content") val content: String? = null,
    @Json(name = "encoding") val encoding: String? = null,
    @Json(name = "download_url") val downloadUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class PutContentRequest(
    @Json(name = "message") val message: String,
    @Json(name = "content") val content: String,
    @Json(name = "branch") val branch: String,
    @Json(name = "sha") val sha: String? = null
)

@JsonClass(generateAdapter = true)
data class PutContentResponse(
    @Json(name = "content") val content: GitHubContentItem? = null
)

@JsonClass(generateAdapter = true)
data class DeleteContentRequest(
    @Json(name = "message") val message: String,
    @Json(name = "sha") val sha: String,
    @Json(name = "branch") val branch: String
)

/**
 * GitHub REST Contents API interface.
 * Never logs Authorization headers or tokens.
 */
interface GitHubApiService {

    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Header("Authorization") authorization: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GitHubRepoResponse>

    @GET("repos/{owner}/{repo}/branches/{branch}")
    suspend fun getBranch(
        @Header("Authorization") authorization: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String
    ): Response<GitHubBranchResponse>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun listDirectory(
        @Header("Authorization") authorization: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Query("ref") branch: String
    ): Response<List<GitHubContentItem>>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFile(
        @Header("Authorization") authorization: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Query("ref") branch: String
    ): Response<GitHubFileContent>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun putFile(
        @Header("Authorization") authorization: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body body: PutContentRequest
    ): Response<PutContentResponse>

    @HTTP(method = "DELETE", path = "repos/{owner}/{repo}/contents/{path}", hasBody = true)
    suspend fun deleteFile(
        @Header("Authorization") authorization: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body body: DeleteContentRequest
    ): Response<Unit>
}
