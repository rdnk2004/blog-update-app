package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.config.SiteConfig
import com.example.data.model.DeskSection
import com.example.data.model.GitHubSettings
import com.example.data.model.LocalDraft
import com.example.data.security.PinHasher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.deskDataStore by preferencesDataStore(name = "my_blog_desk_prefs")

/**
 * Device-local storage for hashed PIN, GitHub repository settings (token stays on device only),
 * and automatic draft saving so writing is never lost.
 */
class DeskPreferences(private val context: Context) {

    private object Keys {
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_HASH = stringPreferencesKey("pin_hash")

        val GITHUB_TOKEN = stringPreferencesKey("github_token")
        val GITHUB_OWNER = stringPreferencesKey("github_owner")
        val GITHUB_REPO = stringPreferencesKey("github_repo")
        val GITHUB_BRANCH = stringPreferencesKey("github_branch")
        val CONTENT_ROOT = stringPreferencesKey("content_root")
        val IMAGES_DIR = stringPreferencesKey("images_dir")
        val GALLERY_IMAGES_DIR = stringPreferencesKey("gallery_images_dir")
        val SITE_URL = stringPreferencesKey("site_url")

        val DRAFT_SECTION = stringPreferencesKey("draft_section")
        val DRAFT_SERIES_SLUG = stringPreferencesKey("draft_series_slug")
        val DRAFT_SERIES_TITLE = stringPreferencesKey("draft_series_title")
        val DRAFT_CHAPTER_ORDER = intPreferencesKey("draft_chapter_order")
        val DRAFT_CHAPTER_LABEL = stringPreferencesKey("draft_chapter_label")
        val DRAFT_IS_SERIES_META = booleanPreferencesKey("draft_is_series_meta")
        val DRAFT_EDITING_PATH = stringPreferencesKey("draft_editing_path")
        val DRAFT_EDITING_SHA = stringPreferencesKey("draft_editing_sha")
        val DRAFT_EDITING_SLUG = stringPreferencesKey("draft_editing_slug")
        val DRAFT_EDITING_WAS_PUBLISHED = booleanPreferencesKey("draft_editing_was_published")
        val DRAFT_PRESERVED_CATEGORY = stringPreferencesKey("draft_preserved_category")
        val DRAFT_TITLE = stringPreferencesKey("draft_title")
        val DRAFT_DATE = stringPreferencesKey("draft_date")
        val DRAFT_BODY = stringPreferencesKey("draft_body")
        val DRAFT_EXISTING_IMAGE = stringPreferencesKey("draft_existing_image")
        val DRAFT_EXISTING_REMOTE_IMAGE = stringPreferencesKey("draft_existing_remote_image")
        val DRAFT_LOCAL_PHOTO = stringPreferencesKey("draft_local_photo")
        val DRAFT_PHOTO_REMOVED = booleanPreferencesKey("draft_photo_removed")
        val DRAFT_UPDATED_AT = longPreferencesKey("draft_updated_at")
    }

    val hasPinFlow: Flow<Boolean> = context.deskDataStore.data.map { prefs ->
        !prefs[Keys.PIN_HASH].isNullOrBlank() && !prefs[Keys.PIN_SALT].isNullOrBlank()
    }

    val settingsFlow: Flow<GitHubSettings> = context.deskDataStore.data.map { prefs ->
        GitHubSettings(
            token = prefs[Keys.GITHUB_TOKEN].orEmpty(),
            owner = prefs[Keys.GITHUB_OWNER]?.takeIf { it.isNotBlank() } ?: SiteConfig.DEFAULT_OWNER,
            repo = prefs[Keys.GITHUB_REPO]?.takeIf { it.isNotBlank() } ?: SiteConfig.DEFAULT_REPO,
            branch = prefs[Keys.GITHUB_BRANCH]?.takeIf { it.isNotBlank() } ?: SiteConfig.DEFAULT_BRANCH,
            contentRoot = prefs[Keys.CONTENT_ROOT]?.takeIf { it.isNotBlank() } ?: SiteConfig.DEFAULT_CONTENT_ROOT,
            imagesDir = prefs[Keys.IMAGES_DIR]?.takeIf { it.isNotBlank() } ?: SiteConfig.DEFAULT_IMAGES_DIR,
            galleryImagesDir = prefs[Keys.GALLERY_IMAGES_DIR]?.takeIf { it.isNotBlank() } ?: SiteConfig.DEFAULT_GALLERY_IMAGES_DIR,
            siteUrl = prefs[Keys.SITE_URL]?.takeIf { it.isNotBlank() } ?: SiteConfig.DEFAULT_SITE_URL
        )
    }

    val localDraftFlow: Flow<LocalDraft?> = context.deskDataStore.data.map { prefs ->
        val updatedAt = prefs[Keys.DRAFT_UPDATED_AT] ?: 0L
        if (updatedAt == 0L) {
            null
        } else {
            val section = runCatching {
                DeskSection.valueOf(prefs[Keys.DRAFT_SECTION] ?: DeskSection.SOUL_NOTES.name)
            }.getOrDefault(DeskSection.SOUL_NOTES)

            val draft = LocalDraft(
                section = section,
                seriesSlug = prefs[Keys.DRAFT_SERIES_SLUG]?.takeIf { it.isNotBlank() },
                seriesTitle = prefs[Keys.DRAFT_SERIES_TITLE]?.takeIf { it.isNotBlank() },
                chapterOrder = prefs[Keys.DRAFT_CHAPTER_ORDER] ?: 1,
                chapterLabel = prefs[Keys.DRAFT_CHAPTER_LABEL].orEmpty(),
                isEditingSeriesMeta = prefs[Keys.DRAFT_IS_SERIES_META] ?: false,
                editingFilePath = prefs[Keys.DRAFT_EDITING_PATH]?.takeIf { it.isNotBlank() },
                editingSha = prefs[Keys.DRAFT_EDITING_SHA]?.takeIf { it.isNotBlank() },
                editingSlug = prefs[Keys.DRAFT_EDITING_SLUG]?.takeIf { it.isNotBlank() },
                editingWasPublished = prefs[Keys.DRAFT_EDITING_WAS_PUBLISHED] ?: false,
                preservedCategory = prefs[Keys.DRAFT_PRESERVED_CATEGORY].orEmpty(),
                title = prefs[Keys.DRAFT_TITLE].orEmpty(),
                date = prefs[Keys.DRAFT_DATE].orEmpty(),
                body = prefs[Keys.DRAFT_BODY].orEmpty(),
                existingImagePublicPath = prefs[Keys.DRAFT_EXISTING_IMAGE]?.takeIf { it.isNotBlank() },
                existingRemoteImageUrl = prefs[Keys.DRAFT_EXISTING_REMOTE_IMAGE]?.takeIf { it.isNotBlank() },
                localPhotoPath = prefs[Keys.DRAFT_LOCAL_PHOTO]?.takeIf { it.isNotBlank() },
                photoRemoved = prefs[Keys.DRAFT_PHOTO_REMOVED] ?: false,
                updatedAtMillis = updatedAt
            )
            if (draft.hasContent) draft else null
        }
    }

    suspend fun savePin(pin: String) {
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hashPin(pin, salt)
        context.deskDataStore.edit { prefs ->
            prefs[Keys.PIN_SALT] = salt
            prefs[Keys.PIN_HASH] = hash
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val prefs = context.deskDataStore.data.first()
        val salt = prefs[Keys.PIN_SALT].orEmpty()
        val hash = prefs[Keys.PIN_HASH].orEmpty()
        return PinHasher.verifyPin(pin, salt, hash)
    }

    suspend fun clearPin() {
        context.deskDataStore.edit { prefs ->
            prefs.remove(Keys.PIN_SALT)
            prefs.remove(Keys.PIN_HASH)
        }
    }

    suspend fun saveSettings(settings: GitHubSettings) {
        // Strip accidental whitespace/newlines from pasted tokens or repo fields
        val cleanToken = settings.token.replace(Regex("\\s+"), "")
        context.deskDataStore.edit { prefs ->
            prefs[Keys.GITHUB_TOKEN] = cleanToken
            prefs[Keys.GITHUB_OWNER] = settings.owner.trim().trim('/')
            prefs[Keys.GITHUB_REPO] = settings.repo.trim().trim('/')
            prefs[Keys.GITHUB_BRANCH] = settings.branch.trim()
            prefs[Keys.CONTENT_ROOT] = settings.contentRoot.trim().trim('/')
            prefs[Keys.IMAGES_DIR] = settings.imagesDir.trim().trim('/')
            prefs[Keys.GALLERY_IMAGES_DIR] = settings.galleryImagesDir.trim().trim('/')
            prefs[Keys.SITE_URL] = settings.siteUrl.trim()
        }
    }

    suspend fun saveLocalDraft(draft: LocalDraft) {
        context.deskDataStore.edit { prefs ->
            prefs[Keys.DRAFT_SECTION] = draft.section.name
            prefs[Keys.DRAFT_SERIES_SLUG] = draft.seriesSlug.orEmpty()
            prefs[Keys.DRAFT_SERIES_TITLE] = draft.seriesTitle.orEmpty()
            prefs[Keys.DRAFT_CHAPTER_ORDER] = draft.chapterOrder
            prefs[Keys.DRAFT_CHAPTER_LABEL] = draft.chapterLabel
            prefs[Keys.DRAFT_IS_SERIES_META] = draft.isEditingSeriesMeta
            prefs[Keys.DRAFT_EDITING_PATH] = draft.editingFilePath.orEmpty()
            prefs[Keys.DRAFT_EDITING_SHA] = draft.editingSha.orEmpty()
            prefs[Keys.DRAFT_EDITING_SLUG] = draft.editingSlug.orEmpty()
            prefs[Keys.DRAFT_EDITING_WAS_PUBLISHED] = draft.editingWasPublished
            prefs[Keys.DRAFT_PRESERVED_CATEGORY] = draft.preservedCategory
            prefs[Keys.DRAFT_TITLE] = draft.title
            prefs[Keys.DRAFT_DATE] = draft.date
            prefs[Keys.DRAFT_BODY] = draft.body
            prefs[Keys.DRAFT_EXISTING_IMAGE] = draft.existingImagePublicPath.orEmpty()
            prefs[Keys.DRAFT_EXISTING_REMOTE_IMAGE] = draft.existingRemoteImageUrl.orEmpty()
            prefs[Keys.DRAFT_LOCAL_PHOTO] = draft.localPhotoPath.orEmpty()
            prefs[Keys.DRAFT_PHOTO_REMOVED] = draft.photoRemoved
            prefs[Keys.DRAFT_UPDATED_AT] = draft.updatedAtMillis
        }
    }

    suspend fun clearLocalDraft() {
        context.deskDataStore.edit { prefs ->
            prefs.remove(Keys.DRAFT_SECTION)
            prefs.remove(Keys.DRAFT_SERIES_SLUG)
            prefs.remove(Keys.DRAFT_SERIES_TITLE)
            prefs.remove(Keys.DRAFT_CHAPTER_ORDER)
            prefs.remove(Keys.DRAFT_CHAPTER_LABEL)
            prefs.remove(Keys.DRAFT_IS_SERIES_META)
            prefs.remove(Keys.DRAFT_EDITING_PATH)
            prefs.remove(Keys.DRAFT_EDITING_SHA)
            prefs.remove(Keys.DRAFT_EDITING_SLUG)
            prefs.remove(Keys.DRAFT_EDITING_WAS_PUBLISHED)
            prefs.remove(Keys.DRAFT_PRESERVED_CATEGORY)
            prefs.remove(Keys.DRAFT_TITLE)
            prefs.remove(Keys.DRAFT_DATE)
            prefs.remove(Keys.DRAFT_BODY)
            prefs.remove(Keys.DRAFT_EXISTING_IMAGE)
            prefs.remove(Keys.DRAFT_EXISTING_REMOTE_IMAGE)
            prefs.remove(Keys.DRAFT_LOCAL_PHOTO)
            prefs.remove(Keys.DRAFT_PHOTO_REMOVED)
            prefs.remove(Keys.DRAFT_UPDATED_AT)
        }
    }
}
