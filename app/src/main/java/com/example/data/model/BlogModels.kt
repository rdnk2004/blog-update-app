package com.example.data.model

import com.example.config.SiteConfig

/**
 * The 5 content sections of ramdeepablog.vercel.app (DeepaRam).
 */
enum class DeskSection(
    val displayName: String,
    val singularName: String,
    val subtitle: String,
    val defaultCategory: String
) {
    SOUL_NOTES(
        displayName = "Soul Notes",
        singularName = "Soul Note",
        subtitle = "Whispers of the heart & reflections",
        defaultCategory = "Soul"
    ),
    MUSINGS(
        displayName = "Musings",
        singularName = "Musing",
        subtitle = "Wandering thoughts & life stories",
        defaultCategory = "Life"
    ),
    HEARTLINES(
        displayName = "Heartlines",
        singularName = "Poem",
        subtitle = "Moments in verse & poetry",
        defaultCategory = "Poetry"
    ),
    SERIES(
        displayName = "Series",
        singularName = "Series",
        subtitle = "Stories told across chapters",
        defaultCategory = "Story"
    ),
    GALLERY(
        displayName = "Art Gallery",
        singularName = "Artwork",
        subtitle = "Acrylic on canvas & pencil art",
        defaultCategory = "Art"
    )
}

/**
 * Folder view filter within every section: Published (live site) vs Drafts (separate draft folder).
 */
enum class ContentFolderTab(val label: String) {
    PUBLISHED("Published"),
    DRAFTS("Drafts")
}

enum class ArtMedium(val displayName: String) {
    ACRYLIC("Acrylic on Canvas"),
    PENCIL("Pencil Art")
}

/**
 * GitHub repository and site settings saved locally on the device.
 * Overrides [toString] so the access token is never accidentally written to logs.
 */
data class GitHubSettings(
    val token: String = "",
    val owner: String = SiteConfig.DEFAULT_OWNER,
    val repo: String = SiteConfig.DEFAULT_REPO,
    val branch: String = SiteConfig.DEFAULT_BRANCH,
    val contentRoot: String = SiteConfig.DEFAULT_CONTENT_ROOT,
    val imagesDir: String = SiteConfig.DEFAULT_IMAGES_DIR,
    val galleryImagesDir: String = SiteConfig.DEFAULT_GALLERY_IMAGES_DIR,
    val siteUrl: String = SiteConfig.DEFAULT_SITE_URL
) {
    val hasToken: Boolean
        get() = token.isNotBlank()

    val maskedTokenHint: String
        get() = if (token.length >= 4) "••••••••${token.takeLast(4)}" else "••••••••"

    val isConfigured: Boolean
        get() = token.isNotBlank() &&
            owner.isNotBlank() &&
            repo.isNotBlank() &&
            branch.isNotBlank() &&
            contentRoot.isNotBlank()

    override fun toString(): String {
        return "GitHubSettings(token=***, owner='$owner', repo='$repo', branch='$branch', " +
            "contentRoot='$contentRoot', imagesDir='$imagesDir', galleryImagesDir='$galleryImagesDir', " +
            "siteUrl='$siteUrl')"
    }
}

/**
 * A written entry in Soul Notes, Musings, or Heartlines (Poems).
 */
data class WritingEntry(
    val section: DeskSection,
    val slug: String,
    val filePath: String,
    val sha: String,
    val title: String,
    val date: String,
    val displayDate: String,
    val category: String,
    val readTime: String,
    val snippet: String,
    val imagePublicPath: String?,
    val remoteImageUrl: String?,
    val body: String,
    val draft: Boolean,
    val landscapeBg: String? = null,
    val portraitBg: String? = null
)

/**
 * A Series root definition (`content/series/<slug>/series.json` or `content/drafts/series/<slug>/series.json`).
 */
data class SeriesSummary(
    val slug: String,
    val filePath: String,
    val sha: String,
    val title: String,
    val date: String,
    val displayDate: String,
    val category: String,
    val description: String,
    val draft: Boolean,
    val publishedChapterCount: Int = 0,
    val draftChapterCount: Int = 0
)

data class GratitudePerson(
    val name: String,
    val role: String
)

data class GratitudeBlock(
    val title: String,
    val subtitle: String,
    val items: List<GratitudePerson>
)

/**
 * A Chapter inside a Series (`content/series/.../chapters/` or `content/drafts/series/.../chapters/`).
 */
data class ChapterEntry(
    val seriesSlug: String,
    val slug: String,
    val filePath: String,
    val sha: String,
    val order: Int,
    val chapterLabel: String,
    val title: String,
    val date: String,
    val displayDate: String,
    val readTime: String,
    val snippet: String,
    val imagePublicPath: String?,
    val remoteImageUrl: String?,
    val body: String,
    val gratitude: GratitudeBlock? = null,
    val draft: Boolean
)

/**
 * An artwork item inside `content/gallery.json` (published) or `content/drafts/gallery.json` (drafts).
 */
data class GalleryItem(
    val id: String,
    val medium: ArtMedium,
    val src: List<String>,
    val caption: String,
    val date: String,
    val draft: Boolean,
    val remotePreviewUrl: String? = null
)

data class GalleryDocument(
    val sha: String?,
    val acrylicArt: List<GalleryItem>,
    val pencilArt: List<GalleryItem>
) {
    val totalCount: Int
        get() = acrylicArt.size + pencilArt.size
}

data class SectionGalleryState(
    val published: GalleryDocument = GalleryDocument(null, emptyList(), emptyList()),
    val drafts: GalleryDocument = GalleryDocument(null, emptyList(), emptyList())
)

/**
 * In-progress writing auto-saved on the device so nothing is ever lost.
 */
data class LocalDraft(
    val section: DeskSection = DeskSection.SOUL_NOTES,
    val seriesSlug: String? = null,
    val seriesTitle: String? = null,
    val chapterOrder: Int = 1,
    val chapterLabel: String = "",
    val isEditingSeriesMeta: Boolean = false,
    val editingFilePath: String? = null,
    val editingSha: String? = null,
    val editingSlug: String? = null,
    val editingWasPublished: Boolean = false,
    val preservedCategory: String = "",
    val title: String = "",
    val date: String = "",
    val body: String = "",
    val existingImagePublicPath: String? = null,
    val existingRemoteImageUrl: String? = null,
    val localPhotoPath: String? = null,
    val photoRemoved: Boolean = false,
    val updatedAtMillis: Long = 0L
) {
    val hasContent: Boolean
        get() = title.isNotBlank() || body.isNotBlank() || !localPhotoPath.isNullOrBlank()
}

/**
 * Result of testing the GitHub repository connection from the hidden Setup screen.
 */
sealed class ConnectionTestResult {
    data class Success(
        val repoFullName: String,
        val branch: String,
        val canWrite: Boolean,
        val summaryLine: String
    ) : ConnectionTestResult()

    data class Error(val message: String) : ConnectionTestResult()
}
