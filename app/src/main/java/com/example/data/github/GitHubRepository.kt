package com.example.data.github

import android.net.Uri
import android.util.Base64
import com.example.config.SiteConfig
import com.example.data.content.ContentJsonCodec
import com.example.data.markdown.PostFormatter
import com.example.data.model.ArtMedium
import com.example.data.model.ChapterEntry
import com.example.data.model.ConnectionTestResult
import com.example.data.model.DeskSection
import com.example.data.model.GalleryDocument
import com.example.data.model.GalleryItem
import com.example.data.model.GitHubSettings
import com.example.data.model.GratitudeBlock
import com.example.data.model.SectionGalleryState
import com.example.data.model.SeriesSummary
import com.example.data.model.WritingEntry
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Exception carrying a calm, jargon-free message for the writer plus an optional
 * technical diagnostic line for the family member checking Setup (never containing tokens).
 */
class FriendlyBlogException(
    message: String,
    val setupDiagnostic: String? = null
) : Exception(message)

/**
 * Communicates directly with the GitHub REST Contents API across all 5 DeepaRam website sections.
 *
 * - Published content lives in `content/<section>/` (and `content/gallery.json`)
 * - Drafts live in separate per-section folders under `content/drafts/<section>/` (and `content/drafts/gallery.json`)
 * - Always fetches fresh `sha` before updating or deleting so editing already-published posts works reliably.
 * - Strictly refuses writes outside `content/` (`.json`) and `public/images/` or `public/gallery/` (images).
 */
class GitHubRepository {

    private val api: GitHubApiService

    init {
        // Deliberately no HTTP logging interceptor so the GitHub token is NEVER logged.
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "MyBlogDesk-Android")
                    .build()
                chain.proceed(request)
            }
            .build()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        api = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubApiService::class.java)
    }

    // region Connection & Write-Permission Test

    suspend fun testConnection(settings: GitHubSettings): ConnectionTestResult = withContext(Dispatchers.IO) {
        if (!settings.isConfigured) {
            return@withContext ConnectionTestResult.Error(
                "Please enter your GitHub token, owner, repository name, branch, and content folder."
            )
        }

        val auth = bearerHeader(settings.token)
        val owner = settings.owner.trim().trim('/')
        val repo = settings.repo.trim().trim('/')
        val branch = settings.branch.trim()

        try {
            val repoResp = api.getRepository(auth, owner, repo)
            if (!repoResp.isSuccessful) {
                val ghMsg = extractGitHubMessage(repoResp)
                return@withContext ConnectionTestResult.Error(mapSetupHttpError(repoResp.code(), "repository", ghMsg))
            }

            val repoBody = repoResp.body()
            val fullName = repoBody?.fullName?.ifBlank { "$owner/$repo" } ?: "$owner/$repo"
            val canPush = repoBody?.permissions?.push == true

            // Crucial check: On public repos, GET succeeds even with a Read-only or wrong-repo token!
            if (!canPush) {
                return@withContext ConnectionTestResult.Error(
                    "Connected to $fullName for reading, but this token does NOT have write permission (push = false).\n\n" +
                        "Fix on GitHub:\n" +
                        "1. Open your Fine-Grained Token (or create a new one)\n" +
                        "2. Repository access → Only select repositories → select \"$fullName\"\n" +
                        "3. Repository permissions → Contents → \"Read and write\" (not Read-only)\n" +
                        "4. Paste the new token here and tap Save Setup."
                )
            }

            val branchResp = api.getBranch(auth, owner, repo, branch)
            if (!branchResp.isSuccessful) {
                val ghMsg = extractGitHubMessage(branchResp)
                return@withContext ConnectionTestResult.Error(
                    if (branchResp.code() == 404) {
                        "Branch \"$branch\" was not found in $fullName."
                    } else {
                        mapSetupHttpError(branchResp.code(), "branch", ghMsg)
                    }
                )
            }

            val rootResp = api.listDirectory(
                auth,
                owner,
                repo,
                encodeRepoPath(settings.contentRoot),
                branch
            )
            when {
                rootResp.isSuccessful -> {
                    val items = rootResp.body().orEmpty().map { it.name }.sorted()
                    val summary = if (items.isEmpty()) {
                        "Write access verified! Folder \"${settings.contentRoot}\" is ready."
                    } else {
                        "Write access verified! Found inside ${settings.contentRoot}/: ${items.joinToString(", ")}."
                    }
                    ConnectionTestResult.Success(
                        repoFullName = fullName,
                        branch = branch,
                        canWrite = true,
                        summaryLine = summary
                    )
                }
                rootResp.code() == 404 -> {
                    ConnectionTestResult.Success(
                        repoFullName = fullName,
                        branch = branch,
                        canWrite = true,
                        summaryLine = "Write access verified! Folder \"${settings.contentRoot}\" will be created automatically on your first save."
                    )
                }
                else -> {
                    val ghMsg = extractGitHubMessage(rootResp)
                    ConnectionTestResult.Error(mapSetupHttpError(rootResp.code(), "content folder", ghMsg))
                }
            }
        } catch (_: IOException) {
            ConnectionTestResult.Error("Could not reach the internet. Please check your connection and try again.")
        } catch (_: Exception) {
            ConnectionTestResult.Error("Could not connect to GitHub. Please double-check the settings and try again.")
        }
    }

    // endregion

    // region Soul Notes, Musings, and Heartlines (Published + Separate Drafts Folder)

    suspend fun fetchWritings(
        settings: GitHubSettings,
        section: DeskSection
    ): Result<List<WritingEntry>> = withContext(Dispatchers.IO) {
        if (!settings.isConfigured) return@withContext Result.success(emptyList())

        val publishedDir = publishedSectionDir(settings.contentRoot, section)
            ?: return@withContext Result.success(emptyList())
        val draftsDir = draftSectionDir(settings.contentRoot, section)
            ?: return@withContext Result.success(emptyList())

        val auth = bearerHeader(settings.token)
        try {
            val (publishedList, draftList) = coroutineScope {
                val pubJob = async { fetchWritingsFromDirectory(auth, settings, section, publishedDir, forceDraft = null) }
                val draftJob = async { fetchWritingsFromDirectory(auth, settings, section, draftsDir, forceDraft = true) }
                pubJob.await() to draftJob.await()
            }

            val combined = (publishedList.getOrThrow() + draftList.getOrThrow()).sortedWith(
                compareByDescending<WritingEntry> { it.date }
                    .thenByDescending { it.slug }
            )
            Result.success(combined)
        } catch (_: IOException) {
            Result.failure(FriendlyBlogException(OFFLINE_READ_MESSAGE))
        } catch (e: FriendlyBlogException) {
            Result.failure(e)
        } catch (_: Exception) {
            Result.failure(FriendlyBlogException("Could not open your posts right now. Please try again in a moment."))
        }
    }

    private suspend fun fetchWritingsFromDirectory(
        auth: String,
        settings: GitHubSettings,
        section: DeskSection,
        dirPath: String,
        forceDraft: Boolean?
    ): Result<List<WritingEntry>> {
        val dirResp = api.listDirectory(
            auth,
            settings.owner.trim(),
            settings.repo.trim(),
            encodeRepoPath(dirPath),
            settings.branch.trim()
        )
        if (dirResp.code() == 404) return Result.success(emptyList())
        if (!dirResp.isSuccessful) {
            return Result.failure(
                FriendlyBlogException(
                    mapReaderHttpError(dirResp.code()),
                    setupDiagnostic = "HTTP ${dirResp.code()} reading $dirPath: ${extractGitHubMessage(dirResp)}"
                )
            )
        }

        val jsonFiles = dirResp.body().orEmpty()
            .filter { it.type == "file" && it.name.lowercase().endsWith(".json") }

        val semaphore = Semaphore(4)
        val entries = coroutineScope {
            jsonFiles.map { item ->
                async {
                    semaphore.withPermit {
                        fetchSingleJsonFile(auth, settings, item.path)?.let { (file, utf8) ->
                            ContentJsonCodec.parseWriting(
                                section = section,
                                filePath = file.path.ifBlank { item.path },
                                fileName = file.name.ifBlank { item.name },
                                sha = file.sha.ifBlank { item.sha },
                                rawJson = utf8,
                                remoteImageResolver = { publicPath ->
                                    buildPublicSiteImageUrl(settings.siteUrl, publicPath)
                                }
                            )?.let { parsed ->
                                if (forceDraft != null) parsed.copy(draft = forceDraft) else parsed
                            }
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        return Result.success(entries)
    }

    /**
     * Saves a Soul Note, Musing, or Heartlines Poem.
     * - Drafts are stored in `content/drafts/<section>/<slug>.json`
     * - Published items are stored in `content/<section>/<slug>.json`
     * - Editing an already-published post fetches a fresh `sha` and updates it in place (preserving slug & URL).
     * - Publishing a draft (or moving a published post to drafts) writes to the new folder first, then deletes the old file cleanly.
     */
    suspend fun saveWriting(
        settings: GitHubSettings,
        section: DeskSection,
        existingFilePath: String?,
        existingSha: String?,
        existingSlug: String?,
        title: String,
        date: String,
        body: String,
        draft: Boolean,
        preservedCategory: String?,
        existingImagePublicPath: String?,
        localPhotoPath: String?,
        photoRemoved: Boolean,
        landscapeBg: String?,
        portraitBg: String?,
        onProgress: (String) -> Unit
    ): Result<WritingEntry> = withContext(Dispatchers.IO) {
        if (!settings.isConfigured) {
            return@withContext Result.failure(FriendlyBlogException(NOT_CONFIGURED_MESSAGE))
        }

        val cleanTitle = title.trim()
        val auth = bearerHeader(settings.token)
        val slug = existingSlug?.takeIf { it.isNotBlank() } ?: PostFormatter.generateSlug(cleanTitle)

        val targetDir = if (draft) {
            draftSectionDir(settings.contentRoot, section)
        } else {
            publishedSectionDir(settings.contentRoot, section)
        } ?: return@withContext Result.failure(FriendlyBlogException("Unsupported section."))

        val cleanExistingPath = existingFilePath?.trim('/')?.takeIf { it.isNotBlank() }
        val targetPath = "$targetDir/$slug.json"
        val isMovingFolders = cleanExistingPath != null && cleanExistingPath != targetPath

        if (!isAllowedContentJsonPath(targetPath, settings.contentRoot)) {
            return@withContext Result.failure(FriendlyBlogException(GUARDRAIL_MESSAGE))
        }

        try {
            // Step 1: Upload image FIRST if a new local photo is attached
            var finalImagePublicPath: String? = if (photoRemoved) null else existingImagePublicPath
            var uploadedDownloadUrl: String? = null

            if (!localPhotoPath.isNullOrBlank() && !photoRemoved) {
                val upload = uploadImageFile(
                    auth = auth,
                    settings = settings,
                    targetDir = settings.imagesDir,
                    fileName = "$slug.jpg",
                    localPhotoPath = localPhotoPath,
                    commitTitle = cleanTitle,
                    onProgress = onProgress
                )
                upload.fold(
                    onSuccess = { (publicPath, downloadUrl) ->
                        finalImagePublicPath = publicPath
                        uploadedDownloadUrl = downloadUrl
                    },
                    onFailure = { return@withContext Result.failure(it) }
                )
            }

            // Step 2: Fetch fresh SHA at targetPath so updates to already-published posts NEVER fail with stale SHA
            onProgress(
                when {
                    draft -> "Saving to Drafts folder…"
                    cleanExistingPath != null && !isMovingFolders -> "Updating live post…"
                    else -> "Publishing your ${section.singularName.lowercase()}…"
                }
            )
            val freshTargetSha = fetchExistingFileSha(auth, settings, targetPath)
                ?: if (!isMovingFolders) existingSha else null

            // Guard against accidentally overwriting an unrelated existing post when creating a brand-new post
            if (cleanExistingPath == null && freshTargetSha != null) {
                return@withContext Result.failure(
                    FriendlyBlogException(
                        "A ${section.singularName.lowercase()} with this same title already exists. " +
                            "Your writing is safe — please change the title slightly and try again."
                    )
                )
            }

            val jsonContent = ContentJsonCodec.serializeWriting(
                section = section,
                slug = slug,
                title = cleanTitle,
                date = date,
                body = body,
                imagePublicPath = finalImagePublicPath,
                draft = draft,
                categoryOverride = preservedCategory,
                landscapeBg = landscapeBg,
                portraitBg = portraitBg
            )
            val commitAction = when {
                draft && isMovingFolders -> "Move to drafts"
                draft && freshTargetSha != null -> "Update draft"
                draft -> "Save draft"
                freshTargetSha != null -> "Update ${section.singularName.lowercase()}"
                else -> "Publish ${section.singularName.lowercase()}"
            }

            val resp = api.putFile(
                authorization = auth,
                owner = settings.owner.trim(),
                repo = settings.repo.trim(),
                path = encodeRepoPath(targetPath),
                body = PutContentRequest(
                    message = "$commitAction: $cleanTitle",
                    content = encodeUtf8Base64(jsonContent),
                    branch = settings.branch.trim(),
                    sha = freshTargetSha
                )
            )
            if (!resp.isSuccessful) {
                return@withContext Result.failure(
                    buildWriteFailure(resp, isUpdate = freshTargetSha != null, isImage = false, path = targetPath)
                )
            }

            // Step 3: If moving between Drafts <-> Published, remove the old file cleanly
            if (isMovingFolders && cleanExistingPath != null && isAllowedContentJsonPath(cleanExistingPath, settings.contentRoot)) {
                val oldSha = fetchExistingFileSha(auth, settings, cleanExistingPath) ?: existingSha
                if (!oldSha.isNullOrBlank()) {
                    runCatching {
                        api.deleteFile(
                            authorization = auth,
                            owner = settings.owner.trim(),
                            repo = settings.repo.trim(),
                            path = encodeRepoPath(cleanExistingPath),
                            body = DeleteContentRequest(
                                message = if (draft) "Unpublish moved to drafts: $cleanTitle" else "Remove published draft: $cleanTitle",
                                sha = oldSha,
                                branch = settings.branch.trim()
                            )
                        )
                    }
                }
            }

            val savedSha = resp.body()?.content?.sha.orEmpty()
            val entry = ContentJsonCodec.parseWriting(
                section = section,
                filePath = targetPath,
                fileName = "$slug.json",
                sha = savedSha,
                rawJson = jsonContent,
                remoteImageResolver = { uploadedDownloadUrl ?: buildPublicSiteImageUrl(settings.siteUrl, it) }
            ) ?: throw FriendlyBlogException("Saved, but could not refresh the post details.")

            Result.success(entry.copy(draft = draft))
        } catch (_: IOException) {
            Result.failure(FriendlyBlogException(OFFLINE_WRITE_MESSAGE))
        } catch (e: FriendlyBlogException) {
            Result.failure(e)
        } catch (_: Exception) {
            Result.failure(FriendlyBlogException(GENERIC_WRITE_MESSAGE))
        }
    }

    // endregion

    // region Series & Chapters (Published + Separate Drafts Folders)

    suspend fun fetchSeriesList(settings: GitHubSettings): Result<List<SeriesSummary>> = withContext(Dispatchers.IO) {
        if (!settings.isConfigured) return@withContext Result.success(emptyList())

        val auth = bearerHeader(settings.token)
        val pubSeriesRoot = SiteConfig.seriesDir(settings.contentRoot)
        val draftSeriesRoot = SiteConfig.seriesDraftsDir(settings.contentRoot)

        try {
            val (pubSeries, draftSeries) = coroutineScope {
                val pubJob = async { fetchSeriesFromRoot(auth, settings, pubSeriesRoot, isDraftFolder = false) }
                val draftJob = async { fetchSeriesFromRoot(auth, settings, draftSeriesRoot, isDraftFolder = true) }
                pubJob.await() to draftJob.await()
            }

            val combined = (pubSeries.getOrThrow() + draftSeries.getOrThrow())
                .distinctBy { "${it.draft}:${it.slug}" }
                .sortedWith(compareByDescending<SeriesSummary> { it.date }.thenBy { it.title })
            Result.success(combined)
        } catch (_: IOException) {
            Result.failure(FriendlyBlogException(OFFLINE_READ_MESSAGE))
        } catch (e: FriendlyBlogException) {
            Result.failure(e)
        } catch (_: Exception) {
            Result.failure(FriendlyBlogException("Could not open your series right now. Please try again."))
        }
    }

    private suspend fun fetchSeriesFromRoot(
        auth: String,
        settings: GitHubSettings,
        seriesRoot: String,
        isDraftFolder: Boolean
    ): Result<List<SeriesSummary>> {
        val rootResp = api.listDirectory(
            auth,
            settings.owner.trim(),
            settings.repo.trim(),
            encodeRepoPath(seriesRoot),
            settings.branch.trim()
        )
        if (rootResp.code() == 404) return Result.success(emptyList())
        if (!rootResp.isSuccessful) {
            return Result.failure(
                FriendlyBlogException(
                    mapReaderHttpError(rootResp.code()),
                    setupDiagnostic = "HTTP ${rootResp.code()} reading $seriesRoot: ${extractGitHubMessage(rootResp)}"
                )
            )
        }

        val seriesDirs = rootResp.body().orEmpty().filter { it.type == "dir" }
        val semaphore = Semaphore(4)
        val summaries = coroutineScope {
            seriesDirs.map { dir ->
                async {
                    semaphore.withPermit {
                        val seriesSlug = dir.name
                        val metaPath = if (isDraftFolder) {
                            SiteConfig.seriesDraftMetaPath(settings.contentRoot, seriesSlug)
                        } else {
                            SiteConfig.seriesMetaPath(settings.contentRoot, seriesSlug)
                        }
                        val metaTuple = fetchSingleJsonFile(auth, settings, metaPath) ?: return@withPermit null
                        val pubChapters = countJsonFiles(auth, settings, SiteConfig.seriesChaptersDir(settings.contentRoot, seriesSlug))
                        val draftChapters = countJsonFiles(auth, settings, SiteConfig.seriesChaptersDraftsDir(settings.contentRoot, seriesSlug))

                        ContentJsonCodec.parseSeriesMeta(
                            seriesSlug = seriesSlug,
                            filePath = metaTuple.first.path.ifBlank { metaPath },
                            sha = metaTuple.first.sha,
                            rawJson = metaTuple.second,
                            publishedChapterCount = pubChapters,
                            draftChapterCount = draftChapters,
                            forceDraft = if (isDraftFolder) true else null
                        )
                    }
                }
            }.awaitAll().filterNotNull()
        }
        return Result.success(summaries)
    }

    suspend fun fetchSeriesChapters(
        settings: GitHubSettings,
        seriesSlug: String
    ): Result<List<ChapterEntry>> = withContext(Dispatchers.IO) {
        if (!settings.isConfigured) return@withContext Result.success(emptyList())

        val auth = bearerHeader(settings.token)
        val pubDir = SiteConfig.seriesChaptersDir(settings.contentRoot, seriesSlug)
        val draftDir = SiteConfig.seriesChaptersDraftsDir(settings.contentRoot, seriesSlug)

        try {
            val (pubChapters, draftChapters) = coroutineScope {
                val pubJob = async { fetchChaptersFromDirectory(auth, settings, seriesSlug, pubDir, forceDraft = null) }
                val draftJob = async { fetchChaptersFromDirectory(auth, settings, seriesSlug, draftDir, forceDraft = true) }
                pubJob.await() to draftJob.await()
            }

            val combined = (pubChapters.getOrThrow() + draftChapters.getOrThrow()).sortedWith(
                compareBy<ChapterEntry> { it.order }
                    .thenBy { it.date }
                    .thenBy { it.slug }
            )
            Result.success(combined)
        } catch (_: IOException) {
            Result.failure(FriendlyBlogException(OFFLINE_READ_MESSAGE))
        } catch (e: FriendlyBlogException) {
            Result.failure(e)
        } catch (_: Exception) {
            Result.failure(FriendlyBlogException("Could not open the chapters right now. Please try again."))
        }
    }

    private suspend fun fetchChaptersFromDirectory(
        auth: String,
        settings: GitHubSettings,
        seriesSlug: String,
        dirPath: String,
        forceDraft: Boolean?
    ): Result<List<ChapterEntry>> {
        val resp = api.listDirectory(
            auth,
            settings.owner.trim(),
            settings.repo.trim(),
            encodeRepoPath(dirPath),
            settings.branch.trim()
        )
        if (resp.code() == 404) return Result.success(emptyList())
        if (!resp.isSuccessful) {
            return Result.failure(
                FriendlyBlogException(
                    mapReaderHttpError(resp.code()),
                    setupDiagnostic = "HTTP ${resp.code()} reading $dirPath: ${extractGitHubMessage(resp)}"
                )
            )
        }

        val files = resp.body().orEmpty().filter { it.type == "file" && it.name.lowercase().endsWith(".json") }
        val semaphore = Semaphore(4)
        val chapters = coroutineScope {
            files.map { item ->
                async {
                    semaphore.withPermit {
                        fetchSingleJsonFile(auth, settings, item.path)?.let { (file, utf8) ->
                            ContentJsonCodec.parseChapter(
                                seriesSlug = seriesSlug,
                                filePath = file.path.ifBlank { item.path },
                                fileName = file.name.ifBlank { item.name },
                                sha = file.sha.ifBlank { item.sha },
                                rawJson = utf8,
                                remoteImageResolver = { publicPath ->
                                    buildPublicSiteImageUrl(settings.siteUrl, publicPath)
                                }
                            )?.let { parsed ->
                                if (forceDraft != null) parsed.copy(draft = forceDraft) else parsed
                            }
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        return Result.success(chapters)
    }

    suspend fun saveSeries(
        settings: GitHubSettings,
        existingFilePath: String?,
        existingSha: String?,
        existingSlug: String?,
        title: String,
        date: String,
        description: String,
        draft: Boolean,
        onProgress: (String) -> Unit
    ): Result<SeriesSummary> = withContext(Dispatchers.IO) {
        if (!settings.isConfigured) {
            return@withContext Result.failure(FriendlyBlogException(NOT_CONFIGURED_MESSAGE))
        }

        val cleanTitle = title.trim()
        val auth = bearerHeader(settings.token)
        val slug = existingSlug?.takeIf { it.isNotBlank() } ?: PostFormatter.generateSlug(cleanTitle)
        val cleanExistingPath = existingFilePath?.trim('/')?.takeIf { it.isNotBlank() }

        val targetPath = if (draft) {
            SiteConfig.seriesDraftMetaPath(settings.contentRoot, slug)
        } else {
            SiteConfig.seriesMetaPath(settings.contentRoot, slug)
        }
        val isMovingFolders = cleanExistingPath != null && cleanExistingPath != targetPath

        if (!isAllowedContentJsonPath(targetPath, settings.contentRoot)) {
            return@withContext Result.failure(FriendlyBlogException(GUARDRAIL_MESSAGE))
        }

        try {
            onProgress(if (draft) "Saving series to Drafts…" else "Saving your series…")
            val freshTargetSha = fetchExistingFileSha(auth, settings, targetPath)
                ?: if (!isMovingFolders) existingSha else null

            if (cleanExistingPath == null && freshTargetSha != null) {
                return@withContext Result.failure(
                    FriendlyBlogException("A series with this title already exists. Please use a different title.")
                )
            }

            val json = ContentJsonCodec.serializeSeriesMeta(
                slug = slug,
                title = cleanTitle,
                date = date,
                description = description,
                draft = draft
            )
            val commitPrefix = when {
                draft && isMovingFolders -> "Move series to drafts"
                draft -> "Save series draft"
                freshTargetSha != null -> "Update series"
                else -> "Publish series"
            }
            val resp = api.putFile(
                authorization = auth,
                owner = settings.owner.trim(),
                repo = settings.repo.trim(),
                path = encodeRepoPath(targetPath),
                body = PutContentRequest(
                    message = "$commitPrefix: $cleanTitle",
                    content = encodeUtf8Base64(json),
                    branch = settings.branch.trim(),
                    sha = freshTargetSha
                )
            )
            if (!resp.isSuccessful) {
                return@withContext Result.failure(
                    buildWriteFailure(resp, isUpdate = freshTargetSha != null, isImage = false, path = targetPath)
                )
            }

            if (isMovingFolders && cleanExistingPath != null && isAllowedContentJsonPath(cleanExistingPath, settings.contentRoot)) {
                val oldSha = fetchExistingFileSha(auth, settings, cleanExistingPath) ?: existingSha
                if (!oldSha.isNullOrBlank()) {
                    runCatching {
                        api.deleteFile(
                            authorization = auth,
                            owner = settings.owner.trim(),
                            repo = settings.repo.trim(),
                            path = encodeRepoPath(cleanExistingPath),
                            body = DeleteContentRequest(
                                message = if (draft) "Unpublish series moved to drafts: $cleanTitle" else "Remove published series draft: $cleanTitle",
                                sha = oldSha,
                                branch = settings.branch.trim()
                            )
                        )
                    }
                }
            }

            val savedSha = resp.body()?.content?.sha.orEmpty()
            val summary = ContentJsonCodec.parseSeriesMeta(
                seriesSlug = slug,
                filePath = targetPath,
                sha = savedSha,
                rawJson = json,
                publishedChapterCount = 0,
                draftChapterCount = 0,
                forceDraft = draft
            ) ?: throw FriendlyBlogException("Series saved, please refresh.")
            Result.success(summary)
        } catch (_: IOException) {
            Result.failure(FriendlyBlogException(OFFLINE_WRITE_MESSAGE))
        } catch (e: FriendlyBlogException) {
            Result.failure(e)
        } catch (_: Exception) {
            Result.failure(FriendlyBlogException(GENERIC_WRITE_MESSAGE))
        }
    }

    suspend fun saveChapter(
        settings: GitHubSettings,
        seriesSlug: String,
        existingFilePath: String?,
        existingSha: String?,
        existingSlug: String?,
        order: Int,
        chapterLabel: String,
        title: String,
        date: String,
        body: String,
        draft: Boolean,
        existingImagePublicPath: String?,
        localPhotoPath: String?,
        photoRemoved: Boolean,
        gratitude: GratitudeBlock?,
        onProgress: (String) -> Unit
    ): Result<ChapterEntry> = withContext(Dispatchers.IO) {
        if (!settings.isConfigured) {
            return@withContext Result.failure(FriendlyBlogException(NOT_CONFIGURED_MESSAGE))
        }

        val cleanTitle = title.trim()
        val auth = bearerHeader(settings.token)
        val cleanExistingPath = existingFilePath?.trim('/')?.takeIf { it.isNotBlank() }
        val slug = existingSlug?.takeIf { it.isNotBlank() } ?: run {
            val labelSlug = PostFormatter.generateSlug(chapterLabel.ifBlank { "chapter-$order" })
            if (labelSlug.startsWith("post-")) "chapter-$order" else labelSlug
        }

        val targetDir = if (draft) {
            SiteConfig.seriesChaptersDraftsDir(settings.contentRoot, seriesSlug)
        } else {
            SiteConfig.seriesChaptersDir(settings.contentRoot, seriesSlug)
        }
        val targetPath = "$targetDir/$slug.json"
        val isMovingFolders = cleanExistingPath != null && cleanExistingPath != targetPath

        if (!isAllowedContentJsonPath(targetPath, settings.contentRoot)) {
            return@withContext Result.failure(FriendlyBlogException(GUARDRAIL_MESSAGE))
        }

        try {
            var finalImagePublicPath: String? = if (photoRemoved) null else existingImagePublicPath
            var uploadedDownloadUrl: String? = null

            if (!localPhotoPath.isNullOrBlank() && !photoRemoved) {
                val upload = uploadImageFile(
                    auth = auth,
                    settings = settings,
                    targetDir = settings.imagesDir,
                    fileName = "$seriesSlug-$slug.jpg",
                    localPhotoPath = localPhotoPath,
                    commitTitle = "$chapterLabel - $cleanTitle",
                    onProgress = onProgress
                )
                upload.fold(
                    onSuccess = { (publicPath, downloadUrl) ->
                        finalImagePublicPath = publicPath
                        uploadedDownloadUrl = downloadUrl
                    },
                    onFailure = { return@withContext Result.failure(it) }
                )
            }

            onProgress(
                when {
                    draft -> "Saving chapter to Drafts…"
                    cleanExistingPath != null && !isMovingFolders -> "Updating live chapter…"
                    else -> "Publishing chapter…"
                }
            )
            val freshTargetSha = fetchExistingFileSha(auth, settings, targetPath)
                ?: if (!isMovingFolders) existingSha else null

            if (cleanExistingPath == null && freshTargetSha != null) {
                return@withContext Result.failure(
                    FriendlyBlogException("A chapter with label \"$chapterLabel\" already exists. Please use a different chapter label.")
                )
            }

            val json = ContentJsonCodec.serializeChapter(
                slug = slug,
                order = order,
                chapterLabel = chapterLabel,
                title = cleanTitle,
                date = date,
                body = body,
                imagePublicPath = finalImagePublicPath,
                gratitude = gratitude,
                draft = draft
            )
            val commitPrefix = when {
                draft && isMovingFolders -> "Move chapter to drafts"
                draft -> "Save chapter draft"
                freshTargetSha != null -> "Update chapter"
                else -> "Publish chapter"
            }

            val resp = api.putFile(
                authorization = auth,
                owner = settings.owner.trim(),
                repo = settings.repo.trim(),
                path = encodeRepoPath(targetPath),
                body = PutContentRequest(
                    message = "$commitPrefix: $cleanTitle",
                    content = encodeUtf8Base64(json),
                    branch = settings.branch.trim(),
                    sha = freshTargetSha
                )
            )
            if (!resp.isSuccessful) {
                return@withContext Result.failure(
                    buildWriteFailure(resp, isUpdate = freshTargetSha != null, isImage = false, path = targetPath)
                )
            }

            if (isMovingFolders && cleanExistingPath != null && isAllowedContentJsonPath(cleanExistingPath, settings.contentRoot)) {
                val oldSha = fetchExistingFileSha(auth, settings, cleanExistingPath) ?: existingSha
                if (!oldSha.isNullOrBlank()) {
                    runCatching {
                        api.deleteFile(
                            authorization = auth,
                            owner = settings.owner.trim(),
                            repo = settings.repo.trim(),
                            path = encodeRepoPath(cleanExistingPath),
                            body = DeleteContentRequest(
                                message = if (draft) "Unpublish chapter moved to drafts: $cleanTitle" else "Remove published chapter draft: $cleanTitle",
                                sha = oldSha,
                                branch = settings.branch.trim()
                            )
                        )
                    }
                }
            }

            val savedSha = resp.body()?.content?.sha.orEmpty()
            val entry = ContentJsonCodec.parseChapter(
                seriesSlug = seriesSlug,
                filePath = targetPath,
                fileName = "$slug.json",
                sha = savedSha,
                rawJson = json,
                remoteImageResolver = { uploadedDownloadUrl ?: buildPublicSiteImageUrl(settings.siteUrl, it) }
            ) ?: throw FriendlyBlogException("Chapter saved, please refresh.")

            Result.success(entry.copy(draft = draft))
        } catch (_: IOException) {
            Result.failure(FriendlyBlogException(OFFLINE_WRITE_MESSAGE))
        } catch (e: FriendlyBlogException) {
            Result.failure(e)
        } catch (_: Exception) {
            Result.failure(FriendlyBlogException(GENERIC_WRITE_MESSAGE))
        }
    }

    // endregion

    // region Art Gallery (Published `content/gallery.json` + Drafts `content/drafts/gallery.json`)

    suspend fun fetchGalleryState(settings: GitHubSettings): Result<SectionGalleryState> = withContext(Dispatchers.IO) {
        if (!settings.isConfigured) return@withContext Result.success(SectionGalleryState())

        val auth = bearerHeader(settings.token)
        val pubPath = SiteConfig.galleryFilePath(settings.contentRoot)
        val draftPath = SiteConfig.galleryDraftsFilePath(settings.contentRoot)

        try {
            val (pubDoc, draftDoc) = coroutineScope {
                val pubJob = async { fetchSingleGalleryDoc(auth, settings, pubPath, forceDraft = false) }
                val draftJob = async { fetchSingleGalleryDoc(auth, settings, draftPath, forceDraft = true) }
                pubJob.await() to draftJob.await()
            }
            Result.success(SectionGalleryState(published = pubDoc.getOrThrow(), drafts = draftDoc.getOrThrow()))
        } catch (_: IOException) {
            Result.failure(FriendlyBlogException(OFFLINE_READ_MESSAGE))
        } catch (e: FriendlyBlogException) {
            Result.failure(e)
        } catch (_: Exception) {
            Result.failure(FriendlyBlogException("Could not open your art gallery right now. Please try again."))
        }
    }

    private suspend fun fetchSingleGalleryDoc(
        auth: String,
        settings: GitHubSettings,
        path: String,
        forceDraft: Boolean
    ): Result<GalleryDocument> {
        val resp = api.getFile(
            auth,
            settings.owner.trim(),
            settings.repo.trim(),
            encodeRepoPath(path),
            settings.branch.trim()
        )
        if (resp.code() == 404) {
            return Result.success(GalleryDocument(null, emptyList(), emptyList()))
        }
        if (!resp.isSuccessful) {
            return Result.failure(
                FriendlyBlogException(
                    mapReaderHttpError(resp.code()),
                    setupDiagnostic = "HTTP ${resp.code()} reading $path: ${extractGitHubMessage(resp)}"
                )
            )
        }
        val file = resp.body() ?: return Result.success(GalleryDocument(null, emptyList(), emptyList()))
        val utf8 = decodeBase64Utf8(file.content.orEmpty())
        val parsed = ContentJsonCodec.parseGallery(
            sha = file.sha,
            rawJson = utf8,
            remoteImageResolver = { publicPath -> buildPublicSiteImageUrl(settings.siteUrl, publicPath) }
        )
        return Result.success(
            parsed.copy(
                acrylicArt = parsed.acrylicArt.map { it.copy(draft = forceDraft) },
                pencilArt = parsed.pencilArt.map { it.copy(draft = forceDraft) }
            )
        )
    }

    suspend fun saveGalleryItem(
        settings: GitHubSettings,
        existingItemId: String?,
        wasPublished: Boolean,
        medium: ArtMedium,
        caption: String,
        date: String,
        targetDraft: Boolean,
        existingSrc: List<String>,
        localPhotoPath: String?,
        onProgress: (String) -> Unit
    ): Result<SectionGalleryState> = withContext(Dispatchers.IO) {
        if (!settings.isConfigured) {
            return@withContext Result.failure(FriendlyBlogException(NOT_CONFIGURED_MESSAGE))
        }

        val cleanCaption = caption.trim()
        val auth = bearerHeader(settings.token)
        val pubPath = SiteConfig.galleryFilePath(settings.contentRoot)
        val draftPath = SiteConfig.galleryDraftsFilePath(settings.contentRoot)

        if (!isAllowedContentJsonPath(pubPath, settings.contentRoot) ||
            !isAllowedContentJsonPath(draftPath, settings.contentRoot)
        ) {
            return@withContext Result.failure(FriendlyBlogException(GUARDRAIL_MESSAGE))
        }

        try {
            // Step 1: Upload new artwork photo to `public/gallery/` FIRST
            val finalSrc: List<String> = if (!localPhotoPath.isNullOrBlank()) {
                val baseSlug = PostFormatter.generateSlug(cleanCaption.ifBlank { "artwork" })
                val fileName = "${date.ifBlank { PostFormatter.todayIsoDate() }}-$baseSlug.jpg"
                val upload = uploadImageFile(
                    auth = auth,
                    settings = settings,
                    targetDir = settings.galleryImagesDir,
                    fileName = fileName,
                    localPhotoPath = localPhotoPath,
                    commitTitle = cleanCaption.ifBlank { "Gallery artwork" },
                    onProgress = onProgress
                )
                val uploadedPublicPath = upload.getOrElse { return@withContext Result.failure(it) }.first
                listOf(uploadedPublicPath)
            } else {
                existingSrc
            }

            if (finalSrc.isEmpty()) {
                return@withContext Result.failure(FriendlyBlogException("Please add a photo of your artwork first."))
            }

            // Step 2: Fetch freshest published & draft gallery docs
            onProgress(
                when {
                    targetDraft -> "Saving artwork to Drafts…"
                    existingItemId != null && wasPublished -> "Updating live artwork…"
                    else -> "Publishing artwork to gallery…"
                }
            )
            val currentState = fetchGalleryState(settings).getOrElse { return@withContext Result.failure(it) }

            val itemId = existingItemId?.takeIf { it.isNotBlank() }
                ?: "art-${System.currentTimeMillis() / 1000L}"
            val updatedItem = GalleryItem(
                id = itemId,
                medium = medium,
                src = finalSrc,
                caption = cleanCaption,
                date = date,
                draft = targetDraft,
                remotePreviewUrl = buildPublicSiteImageUrl(settings.siteUrl, finalSrc.firstOrNull())
            )

            val isMovingBetweenDocs = existingItemId != null && (wasPublished == targetDraft)
            val targetDoc = if (targetDraft) currentState.drafts else currentState.published
            val targetPath = if (targetDraft) draftPath else pubPath

            val nextTargetDoc = upsertGalleryItemInDoc(targetDoc, updatedItem)
            val targetJson = ContentJsonCodec.serializeGallery(nextTargetDoc.acrylicArt, nextTargetDoc.pencilArt)
            val commitMsg = when {
                targetDraft && isMovingBetweenDocs -> "Move artwork to drafts: ${cleanCaption.ifBlank { "Gallery" }}"
                targetDraft -> "Save artwork draft: ${cleanCaption.ifBlank { "Gallery" }}"
                existingItemId != null && wasPublished -> "Update artwork: ${cleanCaption.ifBlank { "Gallery" }}"
                else -> "Publish artwork: ${cleanCaption.ifBlank { "Gallery" }}"
            }

            val putResp = api.putFile(
                authorization = auth,
                owner = settings.owner.trim(),
                repo = settings.repo.trim(),
                path = encodeRepoPath(targetPath),
                body = PutContentRequest(
                    message = commitMsg,
                    content = encodeUtf8Base64(targetJson),
                    branch = settings.branch.trim(),
                    sha = targetDoc.sha
                )
            )
            if (!putResp.isSuccessful) {
                return@withContext Result.failure(
                    buildWriteFailure(putResp, isUpdate = targetDoc.sha != null, isImage = false, path = targetPath)
                )
            }
            val savedTargetDoc = nextTargetDoc.copy(sha = putResp.body()?.content?.sha)

            // Step 3: If moving between Drafts <-> Published, remove from the old gallery file
            var otherDoc = if (targetDraft) currentState.published else currentState.drafts
            if (isMovingBetweenDocs && otherDoc.sha != null) {
                val otherPath = if (targetDraft) pubPath else draftPath
                val cleanedOther = removeGalleryItemFromDoc(otherDoc, itemId)
                val otherJson = ContentJsonCodec.serializeGallery(cleanedOther.acrylicArt, cleanedOther.pencilArt)
                val cleanResp = api.putFile(
                    authorization = auth,
                    owner = settings.owner.trim(),
                    repo = settings.repo.trim(),
                    path = encodeRepoPath(otherPath),
                    body = PutContentRequest(
                        message = if (targetDraft) "Unpublish artwork moved to drafts: $cleanCaption" else "Remove published artwork draft: $cleanCaption",
                        content = encodeUtf8Base64(otherJson),
                        branch = settings.branch.trim(),
                        sha = otherDoc.sha
                    )
                )
                if (cleanResp.isSuccessful) {
                    otherDoc = cleanedOther.copy(sha = cleanResp.body()?.content?.sha)
                }
            }

            val nextState = if (targetDraft) {
                SectionGalleryState(published = otherDoc, drafts = savedTargetDoc)
            } else {
                SectionGalleryState(published = savedTargetDoc, drafts = otherDoc)
            }
            Result.success(nextState)
        } catch (_: IOException) {
            Result.failure(FriendlyBlogException(OFFLINE_WRITE_MESSAGE))
        } catch (e: FriendlyBlogException) {
            Result.failure(e)
        } catch (_: Exception) {
            Result.failure(FriendlyBlogException(GENERIC_WRITE_MESSAGE))
        }
    }

    suspend fun deleteGalleryItem(
        settings: GitHubSettings,
        itemId: String,
        isDraft: Boolean,
        caption: String
    ): Result<SectionGalleryState> = withContext(Dispatchers.IO) {
        if (!settings.isConfigured) {
            return@withContext Result.failure(FriendlyBlogException(NOT_CONFIGURED_MESSAGE))
        }

        val auth = bearerHeader(settings.token)
        val targetPath = if (isDraft) {
            SiteConfig.galleryDraftsFilePath(settings.contentRoot)
        } else {
            SiteConfig.galleryFilePath(settings.contentRoot)
        }
        if (!isAllowedContentJsonPath(targetPath, settings.contentRoot)) {
            return@withContext Result.failure(FriendlyBlogException(GUARDRAIL_MESSAGE))
        }

        try {
            val currentState = fetchGalleryState(settings).getOrElse { return@withContext Result.failure(it) }
            val targetDoc = if (isDraft) currentState.drafts else currentState.published
            val cleaned = removeGalleryItemFromDoc(targetDoc, itemId)
            val json = ContentJsonCodec.serializeGallery(cleaned.acrylicArt, cleaned.pencilArt)

            val resp = api.putFile(
                authorization = auth,
                owner = settings.owner.trim(),
                repo = settings.repo.trim(),
                path = encodeRepoPath(targetPath),
                body = PutContentRequest(
                    message = "Delete artwork: ${caption.trim().ifBlank { "Gallery" }}",
                    content = encodeUtf8Base64(json),
                    branch = settings.branch.trim(),
                    sha = targetDoc.sha
                )
            )
            if (!resp.isSuccessful) {
                return@withContext Result.failure(
                    buildWriteFailure(resp, isUpdate = true, isImage = false, path = targetPath)
                )
            }

            val updatedDoc = cleaned.copy(sha = resp.body()?.content?.sha)
            Result.success(
                if (isDraft) currentState.copy(drafts = updatedDoc) else currentState.copy(published = updatedDoc)
            )
        } catch (_: IOException) {
            Result.failure(FriendlyBlogException(OFFLINE_READ_MESSAGE))
        } catch (_: Exception) {
            Result.failure(FriendlyBlogException("Could not remove this artwork right now. Please try again."))
        }
    }

    private fun upsertGalleryItemInDoc(doc: GalleryDocument, item: GalleryItem): GalleryDocument {
        val nextAcrylic = doc.acrylicArt.filterNot { it.id == item.id }.toMutableList()
        val nextPencil = doc.pencilArt.filterNot { it.id == item.id }.toMutableList()
        if (item.medium == ArtMedium.ACRYLIC) {
            val oldIdx = doc.acrylicArt.indexOfFirst { it.id == item.id }
            if (oldIdx >= 0) nextAcrylic.add(oldIdx, item) else nextAcrylic.add(0, item)
        } else {
            val oldIdx = doc.pencilArt.indexOfFirst { it.id == item.id }
            if (oldIdx >= 0) nextPencil.add(oldIdx, item) else nextPencil.add(0, item)
        }
        return doc.copy(acrylicArt = nextAcrylic, pencilArt = nextPencil)
    }

    private fun removeGalleryItemFromDoc(doc: GalleryDocument, itemId: String): GalleryDocument {
        return doc.copy(
            acrylicArt = doc.acrylicArt.filterNot { it.id == itemId },
            pencilArt = doc.pencilArt.filterNot { it.id == itemId }
        )
    }

    // endregion

    // region Delete Content File & Resolve Signed Image URL

    suspend fun deleteContentFile(
        settings: GitHubSettings,
        filePath: String,
        sha: String,
        title: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!settings.isConfigured) {
            return@withContext Result.failure(FriendlyBlogException(NOT_CONFIGURED_MESSAGE))
        }

        val cleanPath = filePath.trim('/')
        if (!isAllowedContentJsonPath(cleanPath, settings.contentRoot)) {
            return@withContext Result.failure(FriendlyBlogException(GUARDRAIL_MESSAGE))
        }

        val auth = bearerHeader(settings.token)
        try {
            // Always fetch latest sha before delete to avoid stale SHA errors
            val freshSha = fetchExistingFileSha(auth, settings, cleanPath) ?: sha
            val resp = api.deleteFile(
                authorization = auth,
                owner = settings.owner.trim(),
                repo = settings.repo.trim(),
                path = encodeRepoPath(cleanPath),
                body = DeleteContentRequest(
                    message = "Delete post: ${title.trim()}",
                    sha = freshSha,
                    branch = settings.branch.trim()
                )
            )
            if (!resp.isSuccessful) {
                val ghMsg = extractGitHubMessage(resp)
                return@withContext Result.failure(
                    FriendlyBlogException(
                        message = when (resp.code()) {
                            401, 403 -> "This phone does not have permission to remove this post. Ask your family member to check Setup."
                            404 -> "This post was already removed."
                            409, 422 -> "This post was changed somewhere else. Please refresh and try again."
                            else -> "Could not remove this post right now. Please try again."
                        },
                        setupDiagnostic = buildDiagnostic(resp.code(), cleanPath, ghMsg)
                    )
                )
            }
            Result.success(Unit)
        } catch (_: IOException) {
            Result.failure(FriendlyBlogException(OFFLINE_READ_MESSAGE))
        } catch (_: Exception) {
            Result.failure(FriendlyBlogException("Could not remove this post right now. Please try again."))
        }
    }

    suspend fun resolveRemoteImageUrl(
        settings: GitHubSettings,
        imagePublicPath: String?
    ): String? = withContext(Dispatchers.IO) {
        if (imagePublicPath.isNullOrBlank() || !settings.isConfigured) return@withContext null

        val repoPath = SiteConfig.toRepoPublicPath(imagePublicPath)
        if (repoPath != null) {
            try {
                val resp = api.getFile(
                    bearerHeader(settings.token),
                    settings.owner.trim(),
                    settings.repo.trim(),
                    encodeRepoPath(repoPath),
                    settings.branch.trim()
                )
                if (resp.isSuccessful) {
                    val downloadUrl = resp.body()?.downloadUrl
                    if (!downloadUrl.isNullOrBlank()) return@withContext downloadUrl
                }
            } catch (_: Exception) {
                // Fall back to public site URL
            }
        }
        buildPublicSiteImageUrl(settings.siteUrl, imagePublicPath)
    }

    // endregion

    // region Internal Helpers & Guardrails

    private fun publishedSectionDir(contentRoot: String, section: DeskSection): String? = when (section) {
        DeskSection.SOUL_NOTES -> SiteConfig.soulNotesDir(contentRoot)
        DeskSection.MUSINGS -> SiteConfig.musingsDir(contentRoot)
        DeskSection.HEARTLINES -> SiteConfig.heartlinesDir(contentRoot)
        else -> null
    }

    private fun draftSectionDir(contentRoot: String, section: DeskSection): String? = when (section) {
        DeskSection.SOUL_NOTES -> SiteConfig.soulNotesDraftsDir(contentRoot)
        DeskSection.MUSINGS -> SiteConfig.musingsDraftsDir(contentRoot)
        DeskSection.HEARTLINES -> SiteConfig.heartlinesDraftsDir(contentRoot)
        else -> null
    }

    private suspend fun countJsonFiles(auth: String, settings: GitHubSettings, dirPath: String): Int {
        val resp = runCatching {
            api.listDirectory(
                auth,
                settings.owner.trim(),
                settings.repo.trim(),
                encodeRepoPath(dirPath),
                settings.branch.trim()
            )
        }.getOrNull()
        return if (resp?.isSuccessful == true) {
            resp.body().orEmpty().count { it.type == "file" && it.name.lowercase().endsWith(".json") }
        } else {
            0
        }
    }

    private suspend fun uploadImageFile(
        auth: String,
        settings: GitHubSettings,
        targetDir: String,
        fileName: String,
        localPhotoPath: String,
        commitTitle: String,
        onProgress: (String) -> Unit
    ): Result<Pair<String, String?>> {
        val photoFile = File(localPhotoPath)
        if (!photoFile.exists()) {
            return Result.failure(
                FriendlyBlogException("Could not find the selected photo on your phone. Please pick the photo again.")
            )
        }

        val cleanDir = targetDir.trim().trim('/')
        val targetImageRepoPath = if (cleanDir.isEmpty()) fileName else "$cleanDir/$fileName"
        if (!isAllowedImagePath(targetImageRepoPath, cleanDir)) {
            return Result.failure(FriendlyBlogException("Cannot upload photo outside the website images folder."))
        }

        onProgress("Uploading your photo…")
        val base64Image = Base64.encodeToString(photoFile.readBytes(), Base64.NO_WRAP)
        val existingSha = fetchExistingFileSha(auth, settings, targetImageRepoPath)
        val commitMsg = if (existingSha != null) "Update image: $commitTitle" else "Add image: $commitTitle"

        val resp = api.putFile(
            authorization = auth,
            owner = settings.owner.trim(),
            repo = settings.repo.trim(),
            path = encodeRepoPath(targetImageRepoPath),
            body = PutContentRequest(
                message = commitMsg,
                content = base64Image,
                branch = settings.branch.trim(),
                sha = existingSha
            )
        )
        if (!resp.isSuccessful) {
            return Result.failure(
                buildWriteFailure(resp, isUpdate = existingSha != null, isImage = true, path = targetImageRepoPath)
            )
        }

        val publicUrlPath = SiteConfig.toPublicUrlPath(targetImageRepoPath)
        return Result.success(publicUrlPath to resp.body()?.content?.downloadUrl)
    }

    private suspend fun fetchSingleJsonFile(
        auth: String,
        settings: GitHubSettings,
        path: String
    ): Pair<GitHubFileContent, String>? {
        return try {
            val resp = api.getFile(
                auth,
                settings.owner.trim(),
                settings.repo.trim(),
                encodeRepoPath(path),
                settings.branch.trim()
            )
            if (!resp.isSuccessful) return null
            val body = resp.body() ?: return null
            body to decodeBase64Utf8(body.content.orEmpty())
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchExistingFileSha(
        auth: String,
        settings: GitHubSettings,
        repoPath: String
    ): String? {
        return try {
            val resp = api.getFile(
                auth,
                settings.owner.trim(),
                settings.repo.trim(),
                encodeRepoPath(repoPath),
                settings.branch.trim()
            )
            if (resp.isSuccessful) resp.body()?.sha else null
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeBase64Utf8(rawBase64: String): String {
        val cleaned = rawBase64.replace("\n", "").replace("\r", "")
        return String(Base64.decode(cleaned, Base64.DEFAULT), Charsets.UTF_8)
    }

    private fun encodeUtf8Base64(text: String): String {
        return Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun bearerHeader(token: String): String =
        "Bearer ${token.replace(Regex("\\s+"), "")}"

    private fun encodeRepoPath(path: String): String {
        return path
            .trim()
            .trim('/')
            .split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment -> Uri.encode(segment) }
    }

    private fun isAllowedContentJsonPath(targetPath: String, contentRoot: String): Boolean {
        if (targetPath.contains("..")) return false
        val root = contentRoot.trim().trim('/')
        val inRoot = if (root.isEmpty()) true else targetPath.startsWith("$root/")
        return inRoot && targetPath.lowercase().endsWith(".json")
    }

    private fun isAllowedImagePath(targetPath: String, imagesDir: String): Boolean {
        if (targetPath.contains("..")) return false
        val lower = targetPath.lowercase()
        val validExt = lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".webp")
        val inDir = if (imagesDir.isEmpty()) true else targetPath.startsWith("$imagesDir/")
        return inDir && validExt
    }

    private fun buildPublicSiteImageUrl(siteUrl: String, imagePublicPath: String?): String? {
        if (imagePublicPath.isNullOrBlank()) return null
        val trimmed = imagePublicPath.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        val cleanSite = siteUrl.trim().trimEnd('/')
        if (cleanSite.isBlank()) return null
        val leadingSlashPath = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return "$cleanSite$leadingSlashPath"
    }

    private fun extractGitHubMessage(response: Response<*>): String {
        return try {
            val raw = response.errorBody()?.string().orEmpty()
            if (raw.isBlank()) return ""
            JSONObject(raw).optString("message", "").trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun buildDiagnostic(code: Int, path: String, ghMessage: String): String {
        val hint = when {
            code == 403 && ghMessage.contains("Resource not accessible", ignoreCase = true) ->
                "Token needs Repository permissions → Contents: Read and write on this repo."
            code == 403 && ghMessage.contains("protected", ignoreCase = true) ->
                "Branch protection is blocking direct commits to this branch."
            ghMessage.isNotBlank() -> ghMessage
            else -> "GitHub status $code on $path"
        }
        return "HTTP $code ($path): $hint"
    }

    private fun buildWriteFailure(
        response: Response<*>,
        isUpdate: Boolean,
        isImage: Boolean,
        path: String
    ): FriendlyBlogException {
        val code = response.code()
        val ghMsg = extractGitHubMessage(response)
        val friendly = when (code) {
            401 -> "Your website key did not work. Your writing is safe on this phone — ask your family member to check Setup."
            403 -> "This phone does not have permission to save changes. Your writing is safe on this phone — ask your family member to check Setup."
            404 -> "Could not find your website folder. Your writing is safe on this phone — ask your family member to check Setup."
            409 -> "This item was changed somewhere else. Your writing is safe on this phone — please refresh and try again."
            422 -> if (!isUpdate && !isImage) {
                "A post with this same title already exists. Your writing is safe — please change the title slightly and try again."
            } else {
                "This item was changed somewhere else. Your writing is safe — please refresh and try again."
            }
            else -> if (isImage) {
                "Could not upload your photo right now. Your writing is safe on this phone — please try again."
            } else {
                GENERIC_WRITE_MESSAGE
            }
        }
        return FriendlyBlogException(
            message = friendly,
            setupDiagnostic = buildDiagnostic(code, path, ghMsg)
        )
    }

    private fun mapSetupHttpError(code: Int, target: String, ghMsg: String): String {
        val suffix = if (ghMsg.isNotBlank()) "\n\nGitHub said: \"$ghMsg\"" else ""
        return when (code) {
            401 -> "The GitHub token was rejected. Check that it was pasted completely and has not expired.$suffix"
            403 -> "Access denied. Make sure your fine-grained token selects this repository and has \"Contents: Read and write\".$suffix"
            404 -> "Could not find that $target. Check the owner, repository name, and branch.$suffix"
            else -> "GitHub could not be reached right now (status $code).$suffix"
        }
    }

    private fun mapReaderHttpError(code: Int): String = when (code) {
        401 -> "Your website key needs to be checked. Ask your family member to open Setup."
        403 -> "This phone does not have permission to read the website. Ask your family member to check Setup."
        else -> "Could not open your posts right now. Please try again in a moment."
    }

    companion object {
        private const val NOT_CONFIGURED_MESSAGE =
            "Your website is not connected yet. Your writing is safe on this phone."
        private const val GUARDRAIL_MESSAGE =
            "Only blog content files can be saved from this app. Your writing is safe on this phone."
        private const val OFFLINE_READ_MESSAGE =
            "Could not reach the internet. Please check your Wi-Fi or mobile data and try again."
        private const val OFFLINE_WRITE_MESSAGE =
            "Could not reach the internet. Your writing is safe on this phone — please try again when connected."
        private const val GENERIC_WRITE_MESSAGE =
            "Could not save right now. Your writing is still safe on this phone — please try again."
    }
}
