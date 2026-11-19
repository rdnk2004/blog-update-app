package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.content.ContentJsonCodec
import com.example.data.markdown.PostFormatter
import com.example.data.model.ArtMedium
import com.example.data.model.DeskSection
import com.example.data.model.GalleryItem
import com.example.data.security.PinHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches My Blog Desk`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("My Blog Desk", appName)
    }

    @Test
    fun `slug falls back to timestamp for non-Latin Indian language titles`() {
        val latinSlug = PostFormatter.generateSlug("My First Post!", nowEpochSeconds = 12345L)
        assertEquals("my-first-post", latinSlug)

        val hindiSlug = PostFormatter.generateSlug("मेरी पहली कहानी", nowEpochSeconds = 1763542223L)
        assertEquals("post-1763542223", hindiSlug)
    }

    @Test
    fun `heartlines poem JSON preserves exact stanza line breaks and Unicode`() {
        val poem = "She wished to walk beside you,\nif only for a little while.\n\nஅமைதியில் மலரும் கவிதை."
        val json = ContentJsonCodec.serializeWriting(
            section = DeskSection.HEARTLINES,
            slug = "quiet-corners",
            title = "Quiet Corners",
            date = "2026-11-18",
            body = poem,
            imagePublicPath = "/images/quiet-corners.jpg",
            draft = false,
            landscapeBg = "/landscape.png",
            portraitBg = "/portrait.png"
        )

        val parsed = ContentJsonCodec.parseWriting(
            section = DeskSection.HEARTLINES,
            filePath = "content/heartlines/quiet-corners.json",
            fileName = "quiet-corners.json",
            sha = "sha1",
            rawJson = json,
            remoteImageResolver = { it }
        )

        assertNotNull(parsed)
        assertEquals(poem, parsed!!.body)
        assertEquals("/landscape.png", parsed.landscapeBg)
        assertEquals("/portrait.png", parsed.portraitBg)
        assertFalse(parsed.draft)
    }

    @Test
    fun `gallery JSON preserves multi-image acrylic and pencil entries`() {
        val acrylic = listOf(
            GalleryItem("a1", ArtMedium.ACRYLIC, listOf("/gallery/canvas-1.jpeg"), "Lakeside in Bloom", "2026-05-01", false)
        )
        val pencil = listOf(
            GalleryItem("p1", ArtMedium.PENCIL, listOf("/gallery/eye-1.jpeg", "/gallery/eye-2.jpeg"), "Eyes speak louder", "2026-05-02", true)
        )

        val json = ContentJsonCodec.serializeGallery(acrylic, pencil)
        val parsed = ContentJsonCodec.parseGallery("sha-gallery", json) { it }

        assertEquals(1, parsed.acrylicArt.size)
        assertEquals(1, parsed.pencilArt.size)
        assertEquals(2, parsed.pencilArt.first().src.size)
        assertTrue(parsed.pencilArt.first().draft)
    }

    @Test
    fun `pin hashing verifies valid and rejects invalid PINs`() {
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hashPin("2468", salt)

        assertTrue(PinHasher.verifyPin("2468", salt, hash))
        assertFalse(PinHasher.verifyPin("1234", salt, hash))
    }

    @Test
    fun `each section has a dedicated separate draft folder path`() {
        assertEquals("content/drafts/soul-notes", com.example.config.SiteConfig.soulNotesDraftsDir("content"))
        assertEquals("content/drafts/musings", com.example.config.SiteConfig.musingsDraftsDir("content"))
        assertEquals("content/drafts/heartlines", com.example.config.SiteConfig.heartlinesDraftsDir("content"))
        assertEquals(
            "content/drafts/series/my-story/series.json",
            com.example.config.SiteConfig.seriesDraftMetaPath("content", "my-story")
        )
        assertEquals(
            "content/drafts/series/my-story/chapters",
            com.example.config.SiteConfig.seriesChaptersDraftsDir("content", "my-story")
        )
        assertEquals("content/drafts/gallery.json", com.example.config.SiteConfig.galleryDraftsFilePath("content"))
    }

    @Test
    fun `editing a published post preserves its original category`() {
        val json = ContentJsonCodec.serializeWriting(
            section = DeskSection.SOUL_NOTES,
            slug = "quiet-morning",
            title = "Quiet Morning",
            date = "2026-11-19",
            body = "Updated thoughts for a quiet morning.",
            imagePublicPath = null,
            draft = false,
            categoryOverride = "Reflections"
        )
        val parsed = ContentJsonCodec.parseWriting(
            section = DeskSection.SOUL_NOTES,
            filePath = "content/soul-notes/quiet-morning.json",
            fileName = "quiet-morning.json",
            sha = "abc123",
            rawJson = json,
            remoteImageResolver = { it }
        )
        assertNotNull(parsed)
        assertEquals("Reflections", parsed!!.category)
        assertFalse(parsed.draft)
    }
}
