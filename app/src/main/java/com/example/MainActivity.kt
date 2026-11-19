package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.BlogDeskViewModel
import com.example.ui.DeskScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.GalleryEditorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LockScreen
import com.example.ui.screens.PreviewScreen
import com.example.ui.screens.SeriesDetailScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.screens.SuccessScreen
import com.example.ui.theme.MyBlogDeskTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )
        setContent {
            MyBlogDeskTheme {
                MyBlogDeskApp()
            }
        }
    }
}

@Composable
fun MyBlogDeskApp(
    viewModel: BlogDeskViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Crossfade(
        targetState = state.currentScreen,
        animationSpec = tween(durationMillis = 220),
        label = "screenTransition"
    ) { screen ->
        when (screen) {
            is DeskScreen.Lock -> {
                LockScreen(
                    pinMode = state.pinMode,
                    pinInput = state.pinInput,
                    pinError = state.pinError,
                    onDigitPressed = viewModel::onPinDigitPressed,
                    onBackspace = viewModel::onPinBackspace,
                    onClear = viewModel::onPinClear,
                    onLongPressTitle = { viewModel.openHiddenSetup(fromLock = true) }
                )
            }

            is DeskScreen.Home -> {
                HomeScreen(
                    isConfigured = state.settings.isConfigured,
                    selectedSection = state.selectedSection,
                    selectedFolderTab = state.selectedFolderTab,
                    writings = state.writingsBySection[state.selectedSection].orEmpty(),
                    seriesList = state.seriesList,
                    galleryState = state.galleryState,
                    isLoading = state.isLoadingSection,
                    errorMessage = state.homeError,
                    noticeMessage = state.homeNotice,
                    savedLocalDraft = state.savedLocalDraft,
                    onSelectSection = viewModel::selectSection,
                    onSelectFolderTab = viewModel::selectFolderTab,
                    onDismissNotice = viewModel::dismissHomeNotice,
                    onRefresh = viewModel::refreshCurrentSection,
                    onLockApp = viewModel::lockApp,
                    onLongPressTitle = { viewModel.openHiddenSetup(fromLock = false) },
                    onPrimaryCreateClick = viewModel::startPrimaryActionForCurrentSection,
                    onResumeDraft = viewModel::resumeLocalDraft,
                    onWritingClick = viewModel::openWritingForEditing,
                    onSeriesClick = viewModel::openSeriesDetail,
                    onGalleryItemClick = viewModel::openGalleryItemForEditing
                )
            }

            is DeskScreen.SeriesDetail -> {
                val selectedSeries = state.selectedSeries
                if (selectedSeries != null) {
                    SeriesDetailScreen(
                        series = selectedSeries,
                        selectedFolderTab = state.selectedSeriesFolderTab,
                        chapters = state.selectedSeriesChapters,
                        isLoading = state.isLoadingChapters,
                        errorMessage = state.chaptersError,
                        onSelectFolderTab = viewModel::selectSeriesFolderTab,
                        onBack = viewModel::backFromSeriesDetailToHome,
                        onRefresh = viewModel::refreshSelectedSeriesChapters,
                        onEditSeriesMeta = viewModel::startEditSeriesMeta,
                        onAddNextChapter = viewModel::startNewChapterForSelectedSeries,
                        onChapterClick = viewModel::openChapterForEditing
                    )
                }
            }

            is DeskScreen.Editor -> {
                EditorScreen(
                    state = state.editor,
                    onBack = viewModel::navigateBackFromEditor,
                    onTitleChanged = viewModel::onEditorTitleChanged,
                    onChapterLabelChanged = viewModel::onEditorChapterLabelChanged,
                    onBodyChanged = viewModel::onEditorBodyChanged,
                    onDateChanged = viewModel::onEditorDateChanged,
                    createCameraUri = viewModel::createCameraCaptureUri,
                    onPhotoSelected = viewModel::onEditorPhotoSelected,
                    onRemovePhoto = viewModel::onEditorRemovePhoto,
                    onPreview = viewModel::openPreview,
                    onSaveDraft = viewModel::saveDraftToGitHub,
                    onPublish = viewModel::publishToGitHub,
                    onDeletePost = viewModel::deleteCurrentEditorItem
                )
            }

            is DeskScreen.GalleryEditor -> {
                GalleryEditorScreen(
                    state = state.galleryEditor,
                    onBack = viewModel::navigateBackFromGalleryEditor,
                    onMediumChanged = viewModel::onGalleryMediumChanged,
                    onCaptionChanged = viewModel::onGalleryCaptionChanged,
                    createCameraUri = viewModel::createCameraCaptureUri,
                    onPhotoSelected = viewModel::onGalleryPhotoSelected,
                    onSaveDraft = { viewModel.saveGalleryArtwork(draft = true) },
                    onPublish = { viewModel.saveGalleryArtwork(draft = false) },
                    onDelete = viewModel::deleteCurrentGalleryItem
                )
            }

            is DeskScreen.Preview -> {
                PreviewScreen(
                    state = state.editor,
                    onBack = viewModel::backFromPreviewToEditor,
                    onPublish = viewModel::publishToGitHub
                )
            }

            is DeskScreen.Success -> {
                SuccessScreen(
                    itemTitle = screen.itemTitle,
                    sectionLabel = screen.sectionLabel,
                    siteUrl = screen.siteUrl,
                    isUpdate = screen.isUpdate,
                    onBackHome = viewModel::finishSuccessToHome
                )
            }

            is DeskScreen.Setup -> {
                SetupScreen(
                    currentSettings = state.settings,
                    isTestingConnection = state.isTestingConnection,
                    connectionTestResult = state.connectionTestResult,
                    onBack = viewModel::closeSetup,
                    onTestConnection = viewModel::testGitHubConnection,
                    onSave = viewModel::saveGitHubSettings,
                    onResetPin = viewModel::resetPinFromSetup
                )
            }
        }
    }
}
