package com.example.data.content

import com.example.data.markdown.PostFormatter
import com.example.data.model.ArtMedium
import com.example.data.model.ChapterEntry
import com.example.data.model.DeskSection
import com.example.data.model.GalleryDocument
import com.example.data.model.GalleryItem
import com.example.data.model.GratitudeBlock
import com.example.data.model.GratitudePerson
import com.example.data.model.SeriesSummary
import com.example.data.model.WritingEntry
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max

@JsonClass(generateAdapter = true)
data class ProseNoteJson(
    @Json(name = "slug") val slug: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "date") val date: String = "",
    @Json(name = "displayDate") val displayDate: String = "",
    @Json(name = "category") val category: String = "",
    @Json(name = "readTime") val readTime: String = "",
    @Json(name = "snippet") val snippet: String = "",
    @Json(name = "image") val image: String? = null,
    @Json(name = "draft") val draft: Boolean = false,
    @Json(name = "content") val content: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class HeartlinesPoemJson(
    @Json(name = "slug") val slug: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "date") val date: String = "",
    @Json(name = "displayDate") val displayDate: String = "",
    @Json(name = "snippet") val snippet: String = "",
    @Json(name = "content") val content: String = "",
    @Json(name = "image") val image: String? = null,
    @Json(name = "landscapeBg") val landscapeBg: String? = null,
    @Json(name = "portraitBg") val portraitBg: String? = null,
    @Json(name = "draft") val draft: Boolean = false
)

@JsonClass(generateAdapter = true)
data class SeriesMetaJson(
    @Json(name = "slug") val slug: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "date") val date: String = "",
    @Json(name = "displayDate") val displayDate: String = "",
    @Json(name = "category") val category: String = "Story",
    @Json(name = "description") val description: String = "",
    @Json(name = "draft") val draft: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GratitudePersonJson(
    @Json(name = "name") val name: String = "",
    @Json(name = "role") val role: String = ""
)

@JsonClass(generateAdapter = true)
data class GratitudeBlockJson(
    @Json(name = "title") val title: String = "",
    @Json(name = "subtitle") val subtitle: String = "",
    @Json(name = "items") val items: List<GratitudePersonJson> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ChapterJson(
    @Json(name = "slug") val slug: String = "",
    @Json(name = "order") val order: Int = 1,
    @Json(name = "chapterLabel") val chapterLabel: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "date") val date: String = "",
    @Json(name = "displayDate") val displayDate: String = "",
    @Json(name = "readTime") val readTime: String = "",
    @Json(name = "snippet") val snippet: String = "",
    @Json(name = "image") val image: String? = null,
    @Json(name = "content") val content: String = "",
    @Json(name = "gratitude") val gratitude: GratitudeBlockJson? = null,
    @Json(name = "draft") val draft: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GalleryEntryJson(
    @Json(name = "id") val id: String = "",
    @Json(name = "src") val src: List<String> = emptyList(),
    @Json(name = "caption") val caption: String = "",
    @Json(name = "date") val date: String = "",
    @Json(name = "draft") val draft: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GalleryFileJson(
    @Json(name = "acrylicArt") val acrylicArt: List<GalleryEntryJson> = emptyList(),
    @Json(name = "pencilArt") val pencilArt: List<GalleryEntryJson> = emptyList()
)

/**
 * Serializes and parses the canonical JSON content files for DeepaRam (`blog-evd`).
 * Preserves full UTF-8 Unicode (including Indian languages), exact paragraph/stanza breaks,
 * and special website fields (gratitude blocks, wallpaper backgrounds, multi-image gallery items).
 */
object ContentJsonCodec {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val proseAdapter = moshi.adapter(ProseNoteJson::class.java).indent("  ")
    private val poemAdapter = moshi.adapter(HeartlinesPoemJson::class.java).indent("  ")
    private val seriesAdapter = moshi.adapter(SeriesMetaJson::class.java).indent("  ")
    private val chapterAdapter = moshi.adapter(ChapterJson::class.java).indent("  ")
    private val galleryAdapter = moshi.adapter(GalleryFileJson::class.java).indent("  ")

    fun formatWebsiteDisplayDate(isoDate: String): String {
        if (isoDate.isBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            val parsed = parser.parse(isoDate.trim()) ?: return isoDate
            SimpleDateFormat("MMMM d, yyyy", Locale.US).format(parsed)
        } catch (_: Exception) {
            isoDate
        }
    }

    fun estimateReadTime(text: String): String {
        val wordCount = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val minutes = max(1, (wordCount + 179) / 180)
        return "$minutes min read"
    }

    fun buildProseSnippet(body: String): String {
        val firstLine = body
            .replace("\r\n", "\n")
            .lines()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("[IMAGE:") && !it.startsWith("[CENTER]") }
            .orEmpty()
        return if (firstLine.length > 140) firstLine.take(137).trimEnd() + "…" else firstLine
    }

    fun buildPoemSnippet(body: String): String {
        val lines = body
            .replace("\r\n", "\n")
            .lines()
            .filter { it.isNotBlank() }
            .take(3)
        return lines.joinToString("\n")
    }

    fun serializeWriting(
        section: DeskSection,
        slug: String,
        title: String,
        date: String,
        body: String,
        imagePublicPath: String?,
        draft: Boolean,
        categoryOverride: String? = null,
        landscapeBg: String? = null,
        portraitBg: String? = null
    ): String {
        val cleanTitle = title.trim()
        val displayDate = formatWebsiteDisplayDate(date)
        val normalizedBody = body.replace("\r\n", "\n")

        return when (section) {
            DeskSection.HEARTLINES -> {
                val payload = HeartlinesPoemJson(
                    slug = slug,
                    title = cleanTitle,
                    date = date,
                    displayDate = displayDate,
                    snippet = buildPoemSnippet(normalizedBody),
                    content = normalizedBody,
                    image = imagePublicPath?.takeIf { it.isNotBlank() },
                    landscapeBg = landscapeBg,
                    portraitBg = portraitBg,
                    draft = draft
                )
                poemAdapter.toJson(payload) + "\n"
            }

            else -> {
                val paragraphs = normalizedBody
                    .split(Regex("\\n\\s*\\n"))
                    .flatMap { block ->
                        block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
                    }
                val payload = ProseNoteJson(
                    slug = slug,
                    title = cleanTitle,
                    date = date,
                    displayDate = displayDate,
                    category = categoryOverride?.takeIf { it.isNotBlank() } ?: section.defaultCategory,
                    readTime = estimateReadTime(normalizedBody),
                    snippet = buildProseSnippet(normalizedBody),
                    image = imagePublicPath?.takeIf { it.isNotBlank() },
                    draft = draft,
                    content = paragraphs
                )
                proseAdapter.toJson(payload) + "\n"
            }
        }
    }

    fun parseWriting(
        section: DeskSection,
        filePath: String,
        fileName: String,
        sha: String,
        rawJson: String,
        remoteImageResolver: (String?) -> String?
    ): WritingEntry? {
        return try {
            val fallbackSlug = fileName.removeSuffix(".json")
            if (section == DeskSection.HEARTLINES) {
                val parsed = poemAdapter.fromJson(rawJson) ?: return null
                val slug = parsed.slug.ifBlank { fallbackSlug }
                val isoDate = normalizeDateOrEmpty(parsed.date, parsed.displayDate)
                WritingEntry(
                    section = section,
                    slug = slug,
                    filePath = filePath,
                    sha = sha,
                    title = parsed.title.ifBlank { slug },
                    date = isoDate,
                    displayDate = parsed.displayDate.ifBlank { PostFormatter.formatDisplayDate(isoDate, true) },
                    category = section.defaultCategory,
                    readTime = estimateReadTime(parsed.content),
                    snippet = parsed.snippet.ifBlank { buildPoemSnippet(parsed.content) },
                    imagePublicPath = parsed.image,
                    remoteImageUrl = remoteImageResolver(parsed.image),
                    body = parsed.content,
                    draft = parsed.draft,
                    landscapeBg = parsed.landscapeBg,
                    portraitBg = parsed.portraitBg
                )
            } else {
                val parsed = proseAdapter.fromJson(rawJson) ?: return null
                val slug = parsed.slug.ifBlank { fallbackSlug }
                val isoDate = normalizeDateOrEmpty(parsed.date, parsed.displayDate)
                val joinedBody = parsed.content.joinToString("\n\n")
                WritingEntry(
                    section = section,
                    slug = slug,
                    filePath = filePath,
                    sha = sha,
                    title = parsed.title.ifBlank { slug },
                    date = isoDate,
                    displayDate = parsed.displayDate.ifBlank { PostFormatter.formatDisplayDate(isoDate, true) },
                    category = parsed.category.ifBlank { section.defaultCategory },
                    readTime = parsed.readTime.ifBlank { estimateReadTime(joinedBody) },
                    snippet = parsed.snippet.ifBlank { buildProseSnippet(joinedBody) },
                    imagePublicPath = parsed.image,
                    remoteImageUrl = remoteImageResolver(parsed.image),
                    body = joinedBody,
                    draft = parsed.draft
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun serializeSeriesMeta(
        slug: String,
        title: String,
        date: String,
        description: String,
        draft: Boolean
    ): String {
        val payload = SeriesMetaJson(
            slug = slug,
            title = title.trim(),
            date = date,
            displayDate = formatWebsiteDisplayDate(date),
            category = DeskSection.SERIES.defaultCategory,
            description = description.trim(),
            draft = draft
        )
        return seriesAdapter.toJson(payload) + "\n"
    }

    fun parseSeriesMeta(
        seriesSlug: String,
        filePath: String,
        sha: String,
        rawJson: String,
        publishedChapterCount: Int,
        draftChapterCount: Int,
        forceDraft: Boolean? = null
    ): SeriesSummary? {
        return try {
            val parsed = seriesAdapter.fromJson(rawJson) ?: return null
            val isoDate = normalizeDateOrEmpty(parsed.date, parsed.displayDate)
            SeriesSummary(
                slug = parsed.slug.ifBlank { seriesSlug },
                filePath = filePath,
                sha = sha,
                title = parsed.title.ifBlank { seriesSlug },
                date = isoDate,
                displayDate = parsed.displayDate.ifBlank { PostFormatter.formatDisplayDate(isoDate, true) },
                category = parsed.category.ifBlank { DeskSection.SERIES.defaultCategory },
                description = parsed.description,
                draft = forceDraft ?: parsed.draft,
                publishedChapterCount = publishedChapterCount,
                draftChapterCount = draftChapterCount
            )
        } catch (_: Exception) {
            null
        }
    }

    fun serializeChapter(
        slug: String,
        order: Int,
        chapterLabel: String,
        title: String,
        date: String,
        body: String,
        imagePublicPath: String?,
        gratitude: GratitudeBlock?,
        draft: Boolean
    ): String {
        val normalizedBody = body.replace("\r\n", "\n")
        val payload = ChapterJson(
            slug = slug,
            order = order,
            chapterLabel = chapterLabel.trim(),
            title = title.trim(),
            date = date,
            displayDate = formatWebsiteDisplayDate(date),
            readTime = estimateReadTime(normalizedBody),
            snippet = buildProseSnippet(normalizedBody),
            image = imagePublicPath?.takeIf { it.isNotBlank() },
            content = normalizedBody,
            gratitude = gratitude?.let { block ->
                GratitudeBlockJson(
                    title = block.title,
                    subtitle = block.subtitle,
                    items = block.items.map { GratitudePersonJson(it.name, it.role) }
                )
            },
            draft = draft
        )
        return chapterAdapter.toJson(payload) + "\n"
    }

    fun parseChapter(
        seriesSlug: String,
        filePath: String,
        fileName: String,
        sha: String,
        rawJson: String,
        remoteImageResolver: (String?) -> String?
    ): ChapterEntry? {
        return try {
            val parsed = chapterAdapter.fromJson(rawJson) ?: return null
            val slug = parsed.slug.ifBlank { fileName.removeSuffix(".json") }
            val isoDate = normalizeDateOrEmpty(parsed.date, parsed.displayDate)
            ChapterEntry(
                seriesSlug = seriesSlug,
                slug = slug,
                filePath = filePath,
                sha = sha,
                order = parsed.order,
                chapterLabel = parsed.chapterLabel,
                title = parsed.title.ifBlank { slug },
                date = isoDate,
                displayDate = parsed.displayDate.ifBlank { PostFormatter.formatDisplayDate(isoDate, true) },
                readTime = parsed.readTime.ifBlank { estimateReadTime(parsed.content) },
                snippet = parsed.snippet.ifBlank { buildProseSnippet(parsed.content) },
                imagePublicPath = parsed.image,
                remoteImageUrl = remoteImageResolver(parsed.image),
                body = parsed.content,
                gratitude = parsed.gratitude?.let { g ->
                    GratitudeBlock(
                        title = g.title,
                        subtitle = g.subtitle,
                        items = g.items.map { GratitudePerson(it.name, it.role) }
                    )
                },
                draft = parsed.draft
            )
        } catch (_: Exception) {
            null
        }
    }

    fun parseGallery(
        sha: String?,
        rawJson: String,
        remoteImageResolver: (String?) -> String?
    ): GalleryDocument {
        val parsed = try {
            galleryAdapter.fromJson(rawJson)
        } catch (_: Exception) {
            null
        } ?: GalleryFileJson()

        val acrylic = parsed.acrylicArt.mapIndexed { idx, entry ->
            GalleryItem(
                id = entry.id.ifBlank { "acrylic-$idx-${entry.src.firstOrNull().orEmpty().hashCode()}" },
                medium = ArtMedium.ACRYLIC,
                src = entry.src,
                caption = entry.caption,
                date = entry.date,
                draft = entry.draft,
                remotePreviewUrl = remoteImageResolver(entry.src.firstOrNull())
            )
        }
        val pencil = parsed.pencilArt.mapIndexed { idx, entry ->
            GalleryItem(
                id = entry.id.ifBlank { "pencil-$idx-${entry.src.firstOrNull().orEmpty().hashCode()}" },
                medium = ArtMedium.PENCIL,
                src = entry.src,
                caption = entry.caption,
                date = entry.date,
                draft = entry.draft,
                remotePreviewUrl = remoteImageResolver(entry.src.firstOrNull())
            )
        }
        return GalleryDocument(sha = sha, acrylicArt = acrylic, pencilArt = pencil)
    }

    fun serializeGallery(acrylicArt: List<GalleryItem>, pencilArt: List<GalleryItem>): String {
        val payload = GalleryFileJson(
            acrylicArt = acrylicArt.map {
                GalleryEntryJson(
                    id = it.id,
                    src = it.src,
                    caption = it.caption.trim(),
                    date = it.date,
                    draft = it.draft
                )
            },
            pencilArt = pencilArt.map {
                GalleryEntryJson(
                    id = it.id,
                    src = it.src,
                    caption = it.caption.trim(),
                    date = it.date,
                    draft = it.draft
                )
            }
        )
        return galleryAdapter.toJson(payload) + "\n"
    }

    private fun normalizeDateOrEmpty(isoCandidate: String, displayCandidate: String): String {
        if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(isoCandidate.trim())) {
            return isoCandidate.trim()
        }
        val candidate = isoCandidate.ifBlank { displayCandidate }.trim()
        if (candidate.isBlank()) return ""
        val patterns = listOf("MMMM d, yyyy", "d MMM, yyyy", "MMM d, yyyy", "yyyy-MM-dd")
        for (pattern in patterns) {
            try {
                val parsed = SimpleDateFormat(pattern, Locale.US).parse(candidate)
                if (parsed != null) {
                    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed)
                }
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return isoCandidate
    }
}
