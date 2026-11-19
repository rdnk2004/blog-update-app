package com.example.config

/**
 * Single site-specific configuration file for My Blog Desk (DeepaRam / blog-evd).
 *
 * Published content lives directly under `content/<section>/` (read by Vercel/Next.js).
 * Drafts live in a separate `content/drafts/<section>/` folder for each section so
 * unfinished work can never accidentally appear on the live website.
 */
object SiteConfig {
    const val DEFAULT_OWNER: String = "rdnk2004"
    const val DEFAULT_REPO: String = "blog-evd"
    const val DEFAULT_BRANCH: String = "main"
    const val DEFAULT_CONTENT_ROOT: String = "content"
    const val DEFAULT_IMAGES_DIR: String = "public/images"
    const val DEFAULT_GALLERY_IMAGES_DIR: String = "public/gallery"
    const val DEFAULT_SITE_URL: String = "https://ramdeepablog.vercel.app"

    // Published content paths
    fun soulNotesDir(contentRoot: String): String = joinPath(contentRoot, "soul-notes")
    fun musingsDir(contentRoot: String): String = joinPath(contentRoot, "musings")
    fun heartlinesDir(contentRoot: String): String = joinPath(contentRoot, "heartlines")
    fun seriesDir(contentRoot: String): String = joinPath(contentRoot, "series")
    fun seriesMetaPath(contentRoot: String, seriesSlug: String): String =
        joinPath(contentRoot, "series/$seriesSlug/series.json")
    fun seriesChaptersDir(contentRoot: String, seriesSlug: String): String =
        joinPath(contentRoot, "series/$seriesSlug/chapters")
    fun galleryFilePath(contentRoot: String): String = joinPath(contentRoot, "gallery.json")

    // Dedicated separate draft folder for EACH section
    fun draftsRoot(contentRoot: String): String = joinPath(contentRoot, "drafts")
    fun soulNotesDraftsDir(contentRoot: String): String = joinPath(draftsRoot(contentRoot), "soul-notes")
    fun musingsDraftsDir(contentRoot: String): String = joinPath(draftsRoot(contentRoot), "musings")
    fun heartlinesDraftsDir(contentRoot: String): String = joinPath(draftsRoot(contentRoot), "heartlines")
    fun seriesDraftsDir(contentRoot: String): String = joinPath(draftsRoot(contentRoot), "series")
    fun seriesDraftMetaPath(contentRoot: String, seriesSlug: String): String =
        joinPath(draftsRoot(contentRoot), "series/$seriesSlug/series.json")
    fun seriesChaptersDraftsDir(contentRoot: String, seriesSlug: String): String =
        joinPath(draftsRoot(contentRoot), "series/$seriesSlug/chapters")
    fun galleryDraftsFilePath(contentRoot: String): String = joinPath(draftsRoot(contentRoot), "gallery.json")

    /**
     * Converts a repo path under `public/` (e.g. `public/images/foo.jpg`)
     * into the public URL path expected by Next.js `<Image>` (e.g. `/images/foo.jpg`).
     */
    fun toPublicUrlPath(repoPath: String): String {
        val trimmed = repoPath.trim().trim('/')
        val withoutPublic = when {
            trimmed.startsWith("public/") -> trimmed.removePrefix("public/")
            trimmed == "public" -> ""
            else -> trimmed
        }
        return if (withoutPublic.isEmpty()) "" else "/$withoutPublic"
    }

    /**
     * Converts a public image reference (like `/gallery/swan.jpeg`, `images/foo.jpg`, or `kaartu.jpeg`)
     * back into a repository path under `public/`.
     */
    fun toRepoPublicPath(publicOrRelativePath: String): String? {
        val clean = publicOrRelativePath.trim()
        if (clean.isEmpty() || clean.startsWith("http://") || clean.startsWith("https://")) {
            return null
        }
        val stripped = clean.trimStart('/')
        return if (stripped.startsWith("public/")) stripped else "public/$stripped"
    }

    private fun joinPath(root: String, child: String): String {
        val cleanRoot = root.trim().trim('/')
        val cleanChild = child.trim().trim('/')
        return if (cleanRoot.isEmpty()) cleanChild else "$cleanRoot/$cleanChild"
    }
}
