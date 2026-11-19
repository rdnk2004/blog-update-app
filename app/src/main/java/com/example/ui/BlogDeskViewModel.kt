package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.github.FriendlyBlogException
import com.example.data.github.GitHubRepository
import com.example.data.local.DeskPreferences
import com.example.data.markdown.PostFormatter
import com.example.data.media.ImageCompressor
import com.example.data.model.ArtMedium
import com.example.data.model.ChapterEntry
import com.example.data.model.ConnectionTestResult
import com.example.data.model.ContentFolderTab
import com.example.data.model.DeskSection
import com.example.data.model.GalleryItem
import com.example.data.model.GitHubSettings
import com.example.data.model.GratitudeBlock
import com.example.data.model.LocalDraft
import com.example.data.model.SectionGalleryState
import com.example.data.model.SeriesSummary
import com.example.data.model.WritingEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Distinct screens for My Blog Desk.
 */
sealed class DeskScreen {
    data object Lock : DeskScreen()
    data object Home : DeskScreen()
    data object SeriesDetail : DeskScreen()
    data object Editor : DeskScreen()
    data object GalleryEditor : DeskScreen()
    data object Preview : DeskScreen()
    data class Success(
        val itemTitle: String,
        val sectionLabel: String,
        val siteUrl: String,
        val isUpdate: Boolean = false
    ) : DeskScreen()
    data class Setup(val returnToLock: Boolean = false) : DeskScreen()
}

enum class PinMode {
    LOADING,
    CREATE,
    CONFIRM,
    UNLOCK
}

/**
 * Editor state covering Soul Notes, Musings, Heartlines (Poems), Series Metadata, and Series Chapters.
 */
data class EditorState(
    val section: DeskSection = DeskSection.SOUL_NOTES,
    val isEditingSeriesMeta: Boolean = false,
    val seriesSlug: String? = null,
    val seriesTitle: String? = null,
    val chapterOrder: Int = 1,
    val chapterLabel: String = "",
    val editingFilePath: String? = null,
    val editingSha: String? = null,
    val editingSlug: String? = null,
    val isEditingPublished: Boolean = false,
    val preservedCategory: String = "",
    val title: String = "",
    val date: String = PostFormatter.todayIsoDate(),
    val body: String = "",
    val existingImagePublicPath: String? = null,
    val existingRemoteImageUrl: String? = null,
    val localPhotoPath: String? = null,
    val photoRemoved: Boolean = false,
    val preservedLandscapeBg: String? = null,
    val preservedPortraitBg: String? = null,
    val preservedGratitude: GratitudeBlock? = null,
    val isCompressingPhoto: Boolean = false,
    val isSaving: Boolean = false,
    val savingStatusMessage: String = "",
    val errorMessage: String? = null,
    val errorDiagnostic: String? = null,
    val autoSavedAtMillis: Long? = null
) {
    val isEditingExisting: Boolean
        get() = !editingFilePath.isNullOrBlank() && !editingSha.isNullOrBlank()

    val isChapterMode: Boolean
        get() = section == DeskSection.SERIES && !isEditingSeriesMeta && !seriesSlug.isNullOrBlank()

    val hasVisiblePhoto: Boolean
        get() = !photoRemoved && (!localPhotoPath.isNullOrBlank() || !existingRemoteImageUrl.isNullOrBlank() || !existingImagePublicPath.isNullOrBlank())

    val screenHeading: String
        get() = when {
            isEditingSeriesMeta -> when {
                isEditingPublished -> "Edit Live Series"
                isEditingExisting -> "Edit Series Draft"
                else -> "New Series"
            }
            isChapterMode -> when {
                isEditingPublished -> "Edit Live Chapter"
                isEditingExisting -> "Edit Chapter Draft"
                else -> "New Chapter"
            }
            else -> when {
                isEditingPublished -> "Edit Live ${section.singularName}"
                isEditingExisting -> "Edit ${section.singularName} Draft"
                else -> "New ${section.singularName}"
            }
        }
}

/**
 * Photo-first editor state for the Art Gallery section (`content/gallery.json` & `content/drafts/gallery.json`).
 */
data class GalleryEditorState(
    val editingItemId: String? = null,
    val isEditingPublished: Boolean = false,
    val medium: ArtMedium = ArtMedium.ACRYLIC,
    val caption: String = "",
    val date: String = PostFormatter.todayIsoDate(),
    val existingSrc: List<String> = emptyList(),
    val existingPreviewUrl: String? = null,
    val localPhotoPath: String? = null,
    val isCompressingPhoto: Boolean = false,
    val isSaving: Boolean = false,
    val savingStatusMessage: String = "",
    val errorMessage: String? = null,
    val errorDiagnostic: String? = null
) {
    val isEditingExisting: Boolean
        get() = !editingItemId.isNullOrBlank()

    val hasPhoto: Boolean
        get() = !localPhotoPath.isNullOrBlank() || existingSrc.isNotEmpty()
}

data class BlogDeskUiState(
    val currentScreen: DeskScreen = DeskScreen.Lock,
    val pinMode: PinMode = PinMode.LOADING,
    val pinInput: String = "",
    val pendingNewPin: String = "",
    val pinError: String? = null,
    val settings: GitHubSettings = GitHubSettings(),
    val selectedSection: DeskSection = DeskSection.SOUL_NOTES,
    val selectedFolderTab: ContentFolderTab = ContentFolderTab.PUBLISHED,
    val writingsBySection: Map<DeskSection, List<WritingEntry>> = emptyMap(),
    val seriesList: List<SeriesSummary> = emptyList(),
    val selectedSeries: SeriesSummary? = null,
    val selectedSeriesFolderTab: ContentFolderTab = ContentFolderTab.PUBLISHED,
    val selectedSeriesChapters: List<ChapterEntry> = emptyList(),
    val isLoadingChapters: Boolean = false,
    val chaptersError: String? = null,
    val galleryState: SectionGalleryState = SectionGalleryState(),
    val isLoadingSection: Boolean = false,
    val homeError: String? = null,
    val homeNotice: String? = null,
    val savedLocalDraft: LocalDraft? = null,
    val editor: EditorState = EditorState(),
    val galleryEditor: GalleryEditorState = GalleryEditorState(),
    val isTestingConnection: Boolean = false,
    val connectionTestResult: ConnectionTestResult? = null
)

class BlogDeskViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = DeskPreferences(application.applicationContext)
    private val repository = GitHubRepository()
    private val imageCompressor = ImageCompressor(application.applicationContext)

    private val _uiState = MutableStateFlow(BlogDeskUiState())
    val uiState: StateFlow<BlogDeskUiState> = _uiState.asStateFlow()

    private var autoSaveJob: Job? = null

    init {
        viewModelScope.launch {
            val hasPin = preferences.hasPinFlow.first()
            _uiState.update {
                it.copy(pinMode = if (hasPin) PinMode.UNLOCK else PinMode.CREATE)
            }
        }

        viewModelScope.launch {
            preferences.settingsFlow.collectLatest { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }

        viewModelScope.launch {
            preferences.localDraftFlow.collectLatest { draft ->
                _uiState.update { it.copy(savedLocalDraft = draft) }
            }
        }
    }

    // region PIN Lock

    fun onPinDigitPressed(digit: Char) {
        val state = _uiState.value
        if (state.pinMode == PinMode.LOADING || state.pinInput.length >= 4) return

        val nextInput = state.pinInput + digit
        _uiState.update { it.copy(pinInput = nextInput, pinError = null) }

        if (nextInput.length == 4) {
            handleCompletedPin(nextInput)
        }
    }

    fun onPinBackspace() {
        _uiState.update { state ->
            if (state.pinInput.isEmpty()) state
            else state.copy(pinInput = state.pinInput.dropLast(1), pinError = null)
        }
    }

    fun onPinClear() {
        _uiState.update { it.copy(pinInput = "", pinError = null) }
    }

    private fun handleCompletedPin(pin: String) {
        viewModelScope.launch {
            delay(120)
            when (_uiState.value.pinMode) {
                PinMode.CREATE -> {
                    _uiState.update {
                        it.copy(
                            pinMode = PinMode.CONFIRM,
                            pendingNewPin = pin,
                            pinInput = "",
                            pinError = null
                        )
                    }
                }
                PinMode.CONFIRM -> {
                    if (pin == _uiState.value.pendingNewPin) {
                        preferences.savePin(pin)
                        _uiState.update {
                            it.copy(
                                pinMode = PinMode.UNLOCK,
                                pendingNewPin = "",
                                pinInput = "",
                                pinError = null,
                                currentScreen = DeskScreen.Home
                            )
                        }
                        refreshCurrentSection()
                    } else {
                        _uiState.update {
                            it.copy(
                                pinMode = PinMode.CREATE,
                                pendingNewPin = "",
                                pinInput = "",
                                pinError = "Those PINs did not match. Please choose your 4-digit PIN again."
                            )
                        }
                    }
                }
                PinMode.UNLOCK -> {
                    if (preferences.verifyPin(pin)) {
                        _uiState.update {
                            it.copy(
                                pinInput = "",
                                pinError = null,
                                currentScreen = DeskScreen.Home
                            )
                        }
                        refreshCurrentSection()
                    } else {
                        _uiState.update {
                            it.copy(
                                pinInput = "",
                                pinError = "That PIN is not right. Please try again."
                            )
                        }
                    }
                }
                PinMode.LOADING -> Unit
            }
        }
    }

    fun lockApp() {
        _uiState.update {
            it.copy(
                currentScreen = DeskScreen.Lock,
                pinMode = PinMode.UNLOCK,
                pinInput = "",
                pinError = null
            )
        }
    }

    // endregion

    // region Home Sections, Folder Tabs & Loading

    fun selectSection(section: DeskSection) {
        if (_uiState.value.selectedSection == section && _uiState.value.homeError == null) return
        _uiState.update { it.copy(selectedSection = section, homeError = null) }
        refreshSection(section)
    }

    fun selectFolderTab(tab: ContentFolderTab) {
        _uiState.update { it.copy(selectedFolderTab = tab) }
    }

    fun selectSeriesFolderTab(tab: ContentFolderTab) {
        _uiState.update { it.copy(selectedSeriesFolderTab = tab) }
    }

    fun refreshCurrentSection() {
        refreshSection(_uiState.value.selectedSection)
    }

    private fun refreshSection(section: DeskSection) {
        val settings = _uiState.value.settings
        if (!settings.isConfigured) {
            _uiState.update { it.copy(isLoadingSection = false, homeError = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSection = true, homeError = null) }
            when (section) {
                DeskSection.SOUL_NOTES, DeskSection.MUSINGS, DeskSection.HEARTLINES -> {
                    val result = repository.fetchWritings(settings, section)
                    result.fold(
                        onSuccess = { list ->
                            _uiState.update { state ->
                                state.copy(
                                    isLoadingSection = false,
                                    writingsBySection = state.writingsBySection + (section to list),
                                    homeError = null
                                )
                            }
                        },
                        onFailure = { err ->
                            _uiState.update {
                                it.copy(
                                    isLoadingSection = false,
                                    homeError = err.message ?: "Could not open your posts right now."
                                )
                            }
                        }
                    )
                }

                DeskSection.SERIES -> {
                    val result = repository.fetchSeriesList(settings)
                    result.fold(
                        onSuccess = { series ->
                            _uiState.update {
                                it.copy(
                                    isLoadingSection = false,
                                    seriesList = series,
                                    homeError = null
                                )
                            }
                        },
                        onFailure = { err ->
                            _uiState.update {
                                it.copy(
                                    isLoadingSection = false,
                                    homeError = err.message ?: "Could not open your series right now."
                                )
                            }
                        }
                    )
                }

                DeskSection.GALLERY -> {
                    val result = repository.fetchGalleryState(settings)
                    result.fold(
                        onSuccess = { galleryState ->
                            _uiState.update {
                                it.copy(
                                    isLoadingSection = false,
                                    galleryState = galleryState,
                                    homeError = null
                                )
                            }
                        },
                        onFailure = { err ->
                            _uiState.update {
                                it.copy(
                                    isLoadingSection = false,
                                    homeError = err.message ?: "Could not open your art gallery right now."
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    fun dismissHomeNotice() {
        _uiState.update { it.copy(homeNotice = null) }
    }

    // endregion

    // region Series -> Chapters Navigation

    fun openSeriesDetail(series: SeriesSummary) {
        _uiState.update {
            it.copy(
                currentScreen = DeskScreen.SeriesDetail,
                selectedSeries = series,
                selectedSeriesFolderTab = ContentFolderTab.PUBLISHED,
                selectedSeriesChapters = emptyList(),
                chaptersError = null
            )
        }
        refreshSelectedSeriesChapters()
    }

    fun refreshSelectedSeriesChapters() {
        val series = _uiState.value.selectedSeries ?: return
        val settings = _uiState.value.settings
        if (!settings.isConfigured) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChapters = true, chaptersError = null) }
            val result = repository.fetchSeriesChapters(settings, series.slug)
            result.fold(
                onSuccess = { chapters ->
                    val pubCount = chapters.count { !it.draft }
                    val draftCount = chapters.count { it.draft }
                    _uiState.update { state ->
                        val updatedSeries = state.selectedSeries?.copy(
                            publishedChapterCount = pubCount,
                            draftChapterCount = draftCount
                        )
                        state.copy(
                            isLoadingChapters = false,
                            selectedSeries = updatedSeries,
                            selectedSeriesChapters = chapters,
                            chaptersError = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingChapters = false,
                            chaptersError = err.message ?: "Could not open the chapters right now."
                        )
                    }
                }
            )
        }
    }

    fun backFromSeriesDetailToHome() {
        _uiState.update { it.copy(currentScreen = DeskScreen.Home) }
        refreshSection(DeskSection.SERIES)
    }

    // endregion

    // region Launching Editors (All 5 Sections)

    fun startPrimaryActionForCurrentSection() {
        when (val section = _uiState.value.selectedSection) {
            DeskSection.SOUL_NOTES, DeskSection.MUSINGS, DeskSection.HEARTLINES -> {
                _uiState.update {
                    it.copy(
                        currentScreen = DeskScreen.Editor,
                        editor = EditorState(
                            section = section,
                            date = PostFormatter.todayIsoDate()
                        )
                    )
                }
            }

            DeskSection.SERIES -> {
                _uiState.update {
                    it.copy(
                        currentScreen = DeskScreen.Editor,
                        editor = EditorState(
                            section = DeskSection.SERIES,
                            isEditingSeriesMeta = true,
                            date = PostFormatter.todayIsoDate()
                        )
                    )
                }
            }

            DeskSection.GALLERY -> {
                _uiState.update {
                    it.copy(
                        currentScreen = DeskScreen.GalleryEditor,
                        galleryEditor = GalleryEditorState(
                            medium = ArtMedium.ACRYLIC,
                            date = PostFormatter.todayIsoDate()
                        )
                    )
                }
            }
        }
    }

    fun startEditSeriesMeta() {
        val series = _uiState.value.selectedSeries ?: return
        _uiState.update {
            it.copy(
                currentScreen = DeskScreen.Editor,
                editor = EditorState(
                    section = DeskSection.SERIES,
                    isEditingSeriesMeta = true,
                    seriesSlug = series.slug,
                    seriesTitle = series.title,
                    editingFilePath = series.filePath,
                    editingSha = series.sha,
                    editingSlug = series.slug,
                    isEditingPublished = !series.draft,
                    preservedCategory = series.category,
                    title = series.title,
                    date = series.date.ifBlank { PostFormatter.todayIsoDate() },
                    body = series.description
                )
            )
        }
    }

    fun startNewChapterForSelectedSeries() {
        val series = _uiState.value.selectedSeries ?: return
        val existingChapters = _uiState.value.selectedSeriesChapters
        val highestRegularOrder = existingChapters
            .map { it.order }
            .filter { it in 1..900 }
            .maxOrNull() ?: 0
        val nextOrder = highestRegularOrder + 1

        _uiState.update {
            it.copy(
                currentScreen = DeskScreen.Editor,
                editor = EditorState(
                    section = DeskSection.SERIES,
                    isEditingSeriesMeta = false,
                    seriesSlug = series.slug,
                    seriesTitle = series.title,
                    chapterOrder = nextOrder,
                    chapterLabel = "Chapter $nextOrder",
                    date = PostFormatter.todayIsoDate()
                )
            )
        }
    }

    fun openChapterForEditing(chapter: ChapterEntry) {
        val seriesTitle = _uiState.value.selectedSeries?.title
        _uiState.update {
            it.copy(
                currentScreen = DeskScreen.Editor,
                editor = EditorState(
                    section = DeskSection.SERIES,
                    isEditingSeriesMeta = false,
                    seriesSlug = chapter.seriesSlug,
                    seriesTitle = seriesTitle,
                    chapterOrder = chapter.order,
                    chapterLabel = chapter.chapterLabel,
                    editingFilePath = chapter.filePath,
                    editingSha = chapter.sha,
                    editingSlug = chapter.slug,
                    isEditingPublished = !chapter.draft,
                    title = chapter.title,
                    date = chapter.date.ifBlank { PostFormatter.todayIsoDate() },
                    body = chapter.body,
                    existingImagePublicPath = chapter.imagePublicPath,
                    existingRemoteImageUrl = chapter.remoteImageUrl,
                    preservedGratitude = chapter.gratitude
                )
            )
        }
        resolveEditorImageIfNeeded(chapter.filePath, chapter.imagePublicPath)
    }

    fun openWritingForEditing(entry: WritingEntry) {
        _uiState.update {
            it.copy(
                currentScreen = DeskScreen.Editor,
                editor = EditorState(
                    section = entry.section,
                    editingFilePath = entry.filePath,
                    editingSha = entry.sha,
                    editingSlug = entry.slug,
                    isEditingPublished = !entry.draft,
                    preservedCategory = entry.category,
                    title = entry.title,
                    date = entry.date.ifBlank { PostFormatter.todayIsoDate() },
                    body = entry.body,
                    existingImagePublicPath = entry.imagePublicPath,
                    existingRemoteImageUrl = entry.remoteImageUrl,
                    preservedLandscapeBg = entry.landscapeBg,
                    preservedPortraitBg = entry.portraitBg
                )
            )
        }
        resolveEditorImageIfNeeded(entry.filePath, entry.imagePublicPath)
    }

    fun openGalleryItemForEditing(item: GalleryItem) {
        _uiState.update {
            it.copy(
                currentScreen = DeskScreen.GalleryEditor,
                galleryEditor = GalleryEditorState(
                    editingItemId = item.id,
                    isEditingPublished = !item.draft,
                    medium = item.medium,
                    caption = item.caption,
                    date = item.date.ifBlank { PostFormatter.todayIsoDate() },
                    existingSrc = item.src,
                    existingPreviewUrl = item.remotePreviewUrl
                )
            )
        }
        val firstSrc = item.src.firstOrNull()
        if (!firstSrc.isNullOrBlank()) {
            viewModelScope.launch {
                val resolved = repository.resolveRemoteImageUrl(_uiState.value.settings, firstSrc)
                if (!resolved.isNullOrBlank()) {
                    _uiState.update { state ->
                        if (state.galleryEditor.editingItemId == item.id) {
                            state.copy(galleryEditor = state.galleryEditor.copy(existingPreviewUrl = resolved))
                        } else {
                            state
                        }
                    }
                }
            }
        }
    }

    fun resumeLocalDraft() {
        val draft = _uiState.value.savedLocalDraft ?: return
        _uiState.update {
            it.copy(
                selectedSection = draft.section,
                currentScreen = DeskScreen.Editor,
                editor = EditorState(
                    section = draft.section,
                    isEditingSeriesMeta = draft.isEditingSeriesMeta,
                    seriesSlug = draft.seriesSlug,
                    seriesTitle = draft.seriesTitle,
                    chapterOrder = draft.chapterOrder,
                    chapterLabel = draft.chapterLabel,
                    editingFilePath = draft.editingFilePath,
                    editingSha = draft.editingSha,
                    editingSlug = draft.editingSlug,
                    isEditingPublished = draft.editingWasPublished,
                    preservedCategory = draft.preservedCategory,
                    title = draft.title,
                    date = draft.date.ifBlank { PostFormatter.todayIsoDate() },
                    body = draft.body,
                    existingImagePublicPath = draft.existingImagePublicPath,
                    existingRemoteImageUrl = draft.existingRemoteImageUrl,
                    localPhotoPath = draft.localPhotoPath,
                    photoRemoved = draft.photoRemoved,
                    autoSavedAtMillis = draft.updatedAtMillis
                )
            )
        }
    }

    private fun resolveEditorImageIfNeeded(filePath: String, imagePublicPath: String?) {
        if (imagePublicPath.isNullOrBlank()) return
        viewModelScope.launch {
            val resolved = repository.resolveRemoteImageUrl(_uiState.value.settings, imagePublicPath)
            if (!resolved.isNullOrBlank()) {
                _uiState.update { state ->
                    if (state.editor.editingFilePath == filePath && !state.editor.photoRemoved) {
                        state.copy(editor = state.editor.copy(existingRemoteImageUrl = resolved))
                    } else {
                        state
                    }
                }
            }
        }
    }

    // endregion

    // region Writing / Chapter / Series Editor Actions

    fun onEditorTitleChanged(title: String) {
        _uiState.update {
            it.copy(editor = it.editor.copy(title = title, errorMessage = null, errorDiagnostic = null))
        }
        scheduleAutoSave()
    }

    fun onEditorChapterLabelChanged(label: String) {
        _uiState.update {
            it.copy(editor = it.editor.copy(chapterLabel = label, errorMessage = null, errorDiagnostic = null))
        }
        scheduleAutoSave()
    }

    fun onEditorBodyChanged(body: String) {
        _uiState.update {
            it.copy(editor = it.editor.copy(body = body, errorMessage = null, errorDiagnostic = null))
        }
        scheduleAutoSave()
    }

    fun onEditorDateChanged(isoDate: String) {
        _uiState.update {
            it.copy(editor = it.editor.copy(date = isoDate, errorMessage = null, errorDiagnostic = null))
        }
        scheduleAutoSave()
    }

    fun createCameraCaptureUri(): Uri = imageCompressor.createCameraCaptureUri()

    fun onEditorPhotoSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(editor = it.editor.copy(isCompressingPhoto = true, errorMessage = null, errorDiagnostic = null))
            }
            imageCompressor.compressToDraftFile(uri).fold(
                onSuccess = { compressedPath ->
                    _uiState.update {
                        it.copy(
                            editor = it.editor.copy(
                                localPhotoPath = compressedPath,
                                photoRemoved = false,
                                isCompressingPhoto = false,
                                errorMessage = null,
                                errorDiagnostic = null
                            )
                        )
                    }
                    scheduleAutoSave(immediate = true)
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            editor = it.editor.copy(
                                isCompressingPhoto = false,
                                errorMessage = "Could not use that photo. Please try choosing another one."
                            )
                        )
                    }
                }
            )
        }
    }

    fun onEditorRemovePhoto() {
        _uiState.update {
            it.copy(
                editor = it.editor.copy(
                    localPhotoPath = null,
                    photoRemoved = true,
                    errorMessage = null,
                    errorDiagnostic = null
                )
            )
        }
        scheduleAutoSave(immediate = true)
    }

    private fun scheduleAutoSave(immediate: Boolean = false) {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            if (!immediate) delay(450)
            val ed = _uiState.value.editor
            val now = System.currentTimeMillis()
            val draft = LocalDraft(
                section = ed.section,
                seriesSlug = ed.seriesSlug,
                seriesTitle = ed.seriesTitle,
                chapterOrder = ed.chapterOrder,
                chapterLabel = ed.chapterLabel,
                isEditingSeriesMeta = ed.isEditingSeriesMeta,
                editingFilePath = ed.editingFilePath,
                editingSha = ed.editingSha,
                editingSlug = ed.editingSlug,
                editingWasPublished = ed.isEditingPublished,
                preservedCategory = ed.preservedCategory,
                title = ed.title,
                date = ed.date,
                body = ed.body,
                existingImagePublicPath = ed.existingImagePublicPath,
                existingRemoteImageUrl = ed.existingRemoteImageUrl,
                localPhotoPath = ed.localPhotoPath,
                photoRemoved = ed.photoRemoved,
                updatedAtMillis = now
            )
            if (draft.hasContent) {
                preferences.saveLocalDraft(draft)
                _uiState.update { it.copy(editor = it.editor.copy(autoSavedAtMillis = now)) }
            }
        }
    }

    fun navigateBackFromEditor() {
        val ed = _uiState.value.editor
        if (ed.isSaving) return
        if (ed.title.isNotBlank() || ed.body.isNotBlank() || !ed.localPhotoPath.isNullOrBlank()) {
            scheduleAutoSave(immediate = true)
        }
        val returnToSeries = ed.section == DeskSection.SERIES && _uiState.value.selectedSeries != null &&
            (ed.isChapterMode || ed.isEditingExisting)
        _uiState.update {
            it.copy(currentScreen = if (returnToSeries) DeskScreen.SeriesDetail else DeskScreen.Home)
        }
    }

    fun openPreview() {
        val ed = _uiState.value.editor
        if (ed.isSaving || ed.isCompressingPhoto) return
        _uiState.update { it.copy(currentScreen = DeskScreen.Preview) }
    }

    fun backFromPreviewToEditor() {
        if (_uiState.value.editor.isSaving) return
        _uiState.update { it.copy(currentScreen = DeskScreen.Editor) }
    }

    fun saveDraftToGitHub() = submitEditorToGitHub(draft = true)
    fun publishToGitHub() = submitEditorToGitHub(draft = false)

    private fun submitEditorToGitHub(draft: Boolean) {
        val state = _uiState.value
        val ed = state.editor
        if (ed.isSaving || ed.isCompressingPhoto) return

        if (ed.title.isBlank()) {
            _uiState.update {
                it.copy(
                    currentScreen = DeskScreen.Editor,
                    editor = it.editor.copy(errorMessage = "Please write a title first.", errorDiagnostic = null)
                )
            }
            return
        }
        if (!draft && ed.body.isBlank()) {
            val prompt = if (ed.isEditingSeriesMeta) {
                "Please write a short description for your series before publishing."
            } else {
                "Please write something in your post before publishing."
            }
            _uiState.update {
                it.copy(
                    currentScreen = DeskScreen.Editor,
                    editor = it.editor.copy(errorMessage = prompt, errorDiagnostic = null)
                )
            }
            return
        }
        if (!state.settings.isConfigured) {
            _uiState.update {
                it.copy(
                    editor = it.editor.copy(
                        errorMessage = "Your website is not connected yet. Your writing is safe on this phone.",
                        errorDiagnostic = null
                    )
                )
            }
            return
        }

        scheduleAutoSave(immediate = true)
        val isUpdatingLive = !draft && ed.isEditingPublished

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    editor = it.editor.copy(
                        isSaving = true,
                        savingStatusMessage = when {
                            draft && ed.isEditingPublished -> "Moving to Drafts folder…"
                            draft -> "Saving to Drafts folder…"
                            isUpdatingLive -> "Updating live post…"
                            else -> "Publishing…"
                        },
                        errorMessage = null,
                        errorDiagnostic = null
                    )
                )
            }

            val onProgress: (String) -> Unit = { status ->
                _uiState.update { it.copy(editor = it.editor.copy(savingStatusMessage = status)) }
            }

            when {
                ed.isEditingSeriesMeta -> {
                    repository.saveSeries(
                        settings = state.settings,
                        existingFilePath = ed.editingFilePath,
                        existingSha = ed.editingSha,
                        existingSlug = ed.editingSlug,
                        title = ed.title,
                        date = ed.date,
                        description = ed.body,
                        draft = draft,
                        onProgress = onProgress
                    ).fold(
                        onSuccess = { savedSeries ->
                            preferences.clearLocalDraft()
                            if (draft) {
                                _uiState.update {
                                    it.copy(
                                        currentScreen = DeskScreen.SeriesDetail,
                                        selectedFolderTab = ContentFolderTab.DRAFTS,
                                        selectedSeries = savedSeries,
                                        editor = EditorState(),
                                        homeNotice = if (ed.isEditingPublished) {
                                            "Moved \"${savedSeries.title}\" to Series Drafts."
                                        } else {
                                            "Saved \"${savedSeries.title}\" in Series Drafts."
                                        }
                                    )
                                }
                                refreshSelectedSeriesChapters()
                            } else {
                                _uiState.update {
                                    it.copy(
                                        selectedFolderTab = ContentFolderTab.PUBLISHED,
                                        selectedSeries = savedSeries,
                                        currentScreen = DeskScreen.Success(
                                            itemTitle = savedSeries.title,
                                            sectionLabel = "Series",
                                            siteUrl = state.settings.siteUrl,
                                            isUpdate = isUpdatingLive
                                        ),
                                        editor = EditorState()
                                    )
                                }
                            }
                        },
                        onFailure = ::handleEditorSaveFailure
                    )
                }

                ed.isChapterMode -> {
                    val seriesSlug = ed.seriesSlug ?: return@launch
                    repository.saveChapter(
                        settings = state.settings,
                        seriesSlug = seriesSlug,
                        existingFilePath = ed.editingFilePath,
                        existingSha = ed.editingSha,
                        existingSlug = ed.editingSlug,
                        order = ed.chapterOrder,
                        chapterLabel = ed.chapterLabel.ifBlank { "Chapter ${ed.chapterOrder}" },
                        title = ed.title,
                        date = ed.date,
                        body = ed.body,
                        draft = draft,
                        existingImagePublicPath = ed.existingImagePublicPath,
                        localPhotoPath = ed.localPhotoPath,
                        photoRemoved = ed.photoRemoved,
                        gratitude = ed.preservedGratitude,
                        onProgress = onProgress
                    ).fold(
                        onSuccess = { savedChapter ->
                            preferences.clearLocalDraft()
                            if (draft) {
                                _uiState.update {
                                    it.copy(
                                        currentScreen = DeskScreen.SeriesDetail,
                                        selectedSeriesFolderTab = ContentFolderTab.DRAFTS,
                                        editor = EditorState(),
                                        homeNotice = if (ed.isEditingPublished) {
                                            "Moved \"${savedChapter.title}\" to Chapter Drafts."
                                        } else {
                                            "Saved \"${savedChapter.title}\" in Chapter Drafts."
                                        }
                                    )
                                }
                                refreshSelectedSeriesChapters()
                            } else {
                                _uiState.update {
                                    it.copy(
                                        selectedSeriesFolderTab = ContentFolderTab.PUBLISHED,
                                        currentScreen = DeskScreen.Success(
                                            itemTitle = savedChapter.title,
                                            sectionLabel = "Series Chapter",
                                            siteUrl = state.settings.siteUrl,
                                            isUpdate = isUpdatingLive
                                        ),
                                        editor = EditorState()
                                    )
                                }
                            }
                        },
                        onFailure = ::handleEditorSaveFailure
                    )
                }

                else -> {
                    repository.saveWriting(
                        settings = state.settings,
                        section = ed.section,
                        existingFilePath = ed.editingFilePath,
                        existingSha = ed.editingSha,
                        existingSlug = ed.editingSlug,
                        title = ed.title,
                        date = ed.date,
                        body = ed.body,
                        draft = draft,
                        preservedCategory = ed.preservedCategory.takeIf { it.isNotBlank() },
                        existingImagePublicPath = ed.existingImagePublicPath,
                        localPhotoPath = ed.localPhotoPath,
                        photoRemoved = ed.photoRemoved,
                        landscapeBg = ed.preservedLandscapeBg,
                        portraitBg = ed.preservedPortraitBg,
                        onProgress = onProgress
                    ).fold(
                        onSuccess = { saved ->
                            preferences.clearLocalDraft()
                            if (draft) {
                                _uiState.update {
                                    it.copy(
                                        currentScreen = DeskScreen.Home,
                                        selectedFolderTab = ContentFolderTab.DRAFTS,
                                        editor = EditorState(),
                                        homeNotice = if (ed.isEditingPublished) {
                                            "Moved \"${saved.title}\" to ${saved.section.displayName} Drafts."
                                        } else {
                                            "Saved \"${saved.title}\" in ${saved.section.displayName} Drafts."
                                        }
                                    )
                                }
                                refreshSection(saved.section)
                            } else {
                                _uiState.update {
                                    it.copy(
                                        selectedFolderTab = ContentFolderTab.PUBLISHED,
                                        currentScreen = DeskScreen.Success(
                                            itemTitle = saved.title,
                                            sectionLabel = saved.section.displayName,
                                            siteUrl = state.settings.siteUrl,
                                            isUpdate = isUpdatingLive
                                        ),
                                        editor = EditorState()
                                    )
                                }
                            }
                        },
                        onFailure = ::handleEditorSaveFailure
                    )
                }
            }
        }
    }

    private fun handleEditorSaveFailure(error: Throwable) {
        val friendly = error as? FriendlyBlogException
        val message = friendly?.message
            ?: error.message
            ?: "Could not save right now. Your writing is safe on this phone."
        _uiState.update {
            it.copy(
                editor = it.editor.copy(
                    isSaving = false,
                    savingStatusMessage = "",
                    errorMessage = message,
                    errorDiagnostic = friendly?.setupDiagnostic
                )
            )
        }
    }

    fun deleteCurrentEditorItem() {
        val state = _uiState.value
        val ed = state.editor
        val path = ed.editingFilePath ?: return
        val sha = ed.editingSha ?: return
        if (ed.isSaving) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    editor = it.editor.copy(
                        isSaving = true,
                        savingStatusMessage = "Removing…",
                        errorMessage = null,
                        errorDiagnostic = null
                    )
                )
            }
            repository.deleteContentFile(
                settings = state.settings,
                filePath = path,
                sha = sha,
                title = ed.title.ifBlank { path.substringAfterLast('/') }
            ).fold(
                onSuccess = {
                    if (state.savedLocalDraft?.editingFilePath == path) {
                        preferences.clearLocalDraft()
                    }
                    val backToSeries = ed.isChapterMode && state.selectedSeries != null
                    _uiState.update {
                        it.copy(
                            currentScreen = if (backToSeries) DeskScreen.SeriesDetail else DeskScreen.Home,
                            editor = EditorState(),
                            homeNotice = if (ed.isEditingPublished) {
                                "Removed from your website."
                            } else {
                                "Draft deleted."
                            }
                        )
                    }
                    if (backToSeries) {
                        refreshSelectedSeriesChapters()
                    } else {
                        refreshSection(ed.section)
                    }
                },
                onFailure = { err ->
                    val friendly = err as? FriendlyBlogException
                    _uiState.update {
                        it.copy(
                            editor = it.editor.copy(
                                isSaving = false,
                                savingStatusMessage = "",
                                errorMessage = friendly?.message ?: err.message ?: "Could not remove this right now.",
                                errorDiagnostic = friendly?.setupDiagnostic
                            )
                        )
                    }
                }
            )
        }
    }

    // endregion

    // region Art Gallery Editor Actions

    fun onGalleryMediumChanged(medium: ArtMedium) {
        _uiState.update {
            it.copy(
                galleryEditor = it.galleryEditor.copy(
                    medium = medium,
                    errorMessage = null,
                    errorDiagnostic = null
                )
            )
        }
    }

    fun onGalleryCaptionChanged(caption: String) {
        _uiState.update {
            it.copy(
                galleryEditor = it.galleryEditor.copy(
                    caption = caption,
                    errorMessage = null,
                    errorDiagnostic = null
                )
            )
        }
    }

    fun onGalleryDateChanged(isoDate: String) {
        _uiState.update {
            it.copy(
                galleryEditor = it.galleryEditor.copy(
                    date = isoDate,
                    errorMessage = null,
                    errorDiagnostic = null
                )
            )
        }
    }

    fun onGalleryPhotoSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    galleryEditor = it.galleryEditor.copy(
                        isCompressingPhoto = true,
                        errorMessage = null,
                        errorDiagnostic = null
                    )
                )
            }
            imageCompressor.compressToDraftFile(uri).fold(
                onSuccess = { compressedPath ->
                    _uiState.update {
                        it.copy(
                            galleryEditor = it.galleryEditor.copy(
                                localPhotoPath = compressedPath,
                                isCompressingPhoto = false,
                                errorMessage = null,
                                errorDiagnostic = null
                            )
                        )
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            galleryEditor = it.galleryEditor.copy(
                                isCompressingPhoto = false,
                                errorMessage = "Could not use that photo. Please try choosing another one."
                            )
                        )
                    }
                }
            )
        }
    }

    fun navigateBackFromGalleryEditor() {
        if (_uiState.value.galleryEditor.isSaving) return
        _uiState.update { it.copy(currentScreen = DeskScreen.Home) }
    }

    fun saveGalleryArtwork(draft: Boolean) {
        val state = _uiState.value
        val ge = state.galleryEditor
        if (ge.isSaving || ge.isCompressingPhoto) return

        if (!ge.hasPhoto) {
            _uiState.update {
                it.copy(
                    galleryEditor = it.galleryEditor.copy(
                        errorMessage = "Please add a photo of your artwork first.",
                        errorDiagnostic = null
                    )
                )
            }
            return
        }
        if (ge.caption.isBlank()) {
            _uiState.update {
                it.copy(
                    galleryEditor = it.galleryEditor.copy(
                        errorMessage = "Please write a short caption for your artwork.",
                        errorDiagnostic = null
                    )
                )
            }
            return
        }
        if (!state.settings.isConfigured) {
            _uiState.update {
                it.copy(
                    galleryEditor = it.galleryEditor.copy(
                        errorMessage = "Your website is not connected yet.",
                        errorDiagnostic = null
                    )
                )
            }
            return
        }

        val isUpdatingLive = !draft && ge.isEditingPublished

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    galleryEditor = it.galleryEditor.copy(
                        isSaving = true,
                        savingStatusMessage = when {
                            draft && ge.isEditingPublished -> "Moving artwork to Drafts…"
                            draft -> "Saving artwork to Drafts…"
                            isUpdatingLive -> "Updating live artwork…"
                            else -> "Publishing artwork…"
                        },
                        errorMessage = null,
                        errorDiagnostic = null
                    )
                )
            }

            repository.saveGalleryItem(
                settings = state.settings,
                existingItemId = ge.editingItemId,
                wasPublished = ge.isEditingPublished,
                medium = ge.medium,
                caption = ge.caption,
                date = ge.date,
                targetDraft = draft,
                existingSrc = ge.existingSrc,
                localPhotoPath = ge.localPhotoPath,
                onProgress = { status ->
                    _uiState.update {
                        it.copy(galleryEditor = it.galleryEditor.copy(savingStatusMessage = status))
                    }
                }
            ).fold(
                onSuccess = { updatedGalleryState ->
                    if (draft) {
                        _uiState.update {
                            it.copy(
                                currentScreen = DeskScreen.Home,
                                selectedFolderTab = ContentFolderTab.DRAFTS,
                                galleryState = updatedGalleryState,
                                galleryEditor = GalleryEditorState(),
                                homeNotice = if (ge.isEditingPublished) {
                                    "Moved artwork to Art Gallery Drafts."
                                } else {
                                    "Artwork saved in Art Gallery Drafts."
                                }
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                selectedFolderTab = ContentFolderTab.PUBLISHED,
                                galleryState = updatedGalleryState,
                                galleryEditor = GalleryEditorState(),
                                currentScreen = DeskScreen.Success(
                                    itemTitle = ge.caption.trim(),
                                    sectionLabel = ge.medium.displayName,
                                    siteUrl = state.settings.siteUrl,
                                    isUpdate = isUpdatingLive
                                )
                            )
                        }
                    }
                },
                onFailure = { err ->
                    val friendly = err as? FriendlyBlogException
                    _uiState.update {
                        it.copy(
                            galleryEditor = it.galleryEditor.copy(
                                isSaving = false,
                                savingStatusMessage = "",
                                errorMessage = friendly?.message ?: err.message ?: "Could not save this artwork right now.",
                                errorDiagnostic = friendly?.setupDiagnostic
                            )
                        )
                    }
                }
            )
        }
    }

    fun deleteCurrentGalleryItem() {
        val state = _uiState.value
        val ge = state.galleryEditor
        val id = ge.editingItemId ?: return
        if (ge.isSaving) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    galleryEditor = it.galleryEditor.copy(
                        isSaving = true,
                        savingStatusMessage = "Removing artwork…",
                        errorMessage = null,
                        errorDiagnostic = null
                    )
                )
            }
            repository.deleteGalleryItem(
                settings = state.settings,
                itemId = id,
                isDraft = !ge.isEditingPublished,
                caption = ge.caption
            ).fold(
                onSuccess = { updatedGalleryState ->
                    _uiState.update {
                        it.copy(
                            currentScreen = DeskScreen.Home,
                            galleryState = updatedGalleryState,
                            galleryEditor = GalleryEditorState(),
                            homeNotice = if (ge.isEditingPublished) {
                                "Artwork removed from your website."
                            } else {
                                "Draft artwork deleted."
                            }
                        )
                    }
                },
                onFailure = { err ->
                    val friendly = err as? FriendlyBlogException
                    _uiState.update {
                        it.copy(
                            galleryEditor = it.galleryEditor.copy(
                                isSaving = false,
                                savingStatusMessage = "",
                                errorMessage = friendly?.message ?: err.message ?: "Could not remove this artwork right now.",
                                errorDiagnostic = friendly?.setupDiagnostic
                            )
                        )
                    }
                }
            )
        }
    }

    fun finishSuccessToHome() {
        val state = _uiState.value
        val backToSeries = state.selectedSection == DeskSection.SERIES && state.selectedSeries != null
        _uiState.update {
            it.copy(currentScreen = if (backToSeries) DeskScreen.SeriesDetail else DeskScreen.Home)
        }
        if (backToSeries) {
            refreshSelectedSeriesChapters()
        } else {
            refreshCurrentSection()
        }
    }

    // endregion

    // region Hidden Setup Screen (for site maintainer)

    fun openHiddenSetup(fromLock: Boolean = false) {
        _uiState.update {
            it.copy(
                currentScreen = DeskScreen.Setup(returnToLock = fromLock),
                connectionTestResult = null
            )
        }
    }

    fun closeSetup() {
        val returnToLock = (_uiState.value.currentScreen as? DeskScreen.Setup)?.returnToLock == true
        _uiState.update {
            it.copy(
                currentScreen = if (returnToLock) DeskScreen.Lock else DeskScreen.Home,
                connectionTestResult = null
            )
        }
        if (!returnToLock) refreshCurrentSection()
    }

    fun testGitHubConnection(candidateSettings: GitHubSettings) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true, connectionTestResult = null) }
            val cleanedToken = candidateSettings.token.replace(Regex("\\s+"), "")
            val effectiveToken = cleanedToken.ifBlank { _uiState.value.settings.token }
            val result = repository.testConnection(candidateSettings.copy(token = effectiveToken))
            _uiState.update { it.copy(isTestingConnection = false, connectionTestResult = result) }
        }
    }

    fun saveGitHubSettings(candidateSettings: GitHubSettings, clearExistingToken: Boolean = false) {
        viewModelScope.launch {
            val cleanedInput = candidateSettings.token.replace(Regex("\\s+"), "")
            val effectiveToken = when {
                clearExistingToken -> ""
                cleanedInput.isNotBlank() -> cleanedInput
                else -> _uiState.value.settings.token
            }
            val finalSettings = candidateSettings.copy(token = effectiveToken)
            preferences.saveSettings(finalSettings)
            val returnToLock = (_uiState.value.currentScreen as? DeskScreen.Setup)?.returnToLock == true
            _uiState.update {
                it.copy(
                    settings = finalSettings,
                    currentScreen = if (returnToLock) DeskScreen.Lock else DeskScreen.Home,
                    homeNotice = if (returnToLock) null else "Setup saved on this phone.",
                    connectionTestResult = null
                )
            }
            if (!returnToLock && finalSettings.isConfigured) {
                refreshCurrentSection()
            }
        }
    }

    fun resetPinFromSetup() {
        viewModelScope.launch {
            preferences.clearPin()
            _uiState.update {
                it.copy(
                    currentScreen = DeskScreen.Lock,
                    pinMode = PinMode.CREATE,
                    pinInput = "",
                    pendingNewPin = "",
                    pinError = null
                )
            }
        }
    }

    // endregion
}
