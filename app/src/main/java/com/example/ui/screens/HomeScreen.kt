package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.markdown.PostFormatter
import com.example.data.model.ContentFolderTab
import com.example.data.model.DeskSection
import com.example.data.model.GalleryItem
import com.example.data.model.LocalDraft
import com.example.data.model.SectionGalleryState
import com.example.data.model.SeriesSummary
import com.example.data.model.WritingEntry

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    isConfigured: Boolean,
    selectedSection: DeskSection,
    selectedFolderTab: ContentFolderTab,
    writings: List<WritingEntry>,
    seriesList: List<SeriesSummary>,
    galleryState: SectionGalleryState,
    isLoading: Boolean,
    errorMessage: String?,
    noticeMessage: String?,
    savedLocalDraft: LocalDraft?,
    onSelectSection: (DeskSection) -> Unit,
    onSelectFolderTab: (ContentFolderTab) -> Unit,
    onDismissNotice: () -> Unit,
    onRefresh: () -> Unit,
    onLockApp: () -> Unit,
    onLongPressTitle: () -> Unit,
    onPrimaryCreateClick: () -> Unit,
    onResumeDraft: () -> Unit,
    onWritingClick: (WritingEntry) -> Unit,
    onSeriesClick: (SeriesSummary) -> Unit,
    onGalleryItemClick: (GalleryItem) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(noticeMessage) {
        if (!noticeMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(noticeMessage)
            onDismissNotice()
        }
    }

    val primaryButtonText = when (selectedSection) {
        DeskSection.SOUL_NOTES -> "New Soul Note"
        DeskSection.MUSINGS -> "New Musing"
        DeskSection.HEARTLINES -> "New Poem"
        DeskSection.SERIES -> "New Series"
        DeskSection.GALLERY -> "Add Artwork"
    }
    val primaryButtonIcon = if (selectedSection == DeskSection.GALLERY) {
        Icons.Rounded.AddPhotoAlternate
    } else {
        Icons.Rounded.Edit
    }

    val publishedCount = when (selectedSection) {
        DeskSection.SOUL_NOTES, DeskSection.MUSINGS, DeskSection.HEARTLINES -> writings.count { !it.draft }
        DeskSection.SERIES -> seriesList.count { !it.draft }
        DeskSection.GALLERY -> galleryState.published.totalCount
    }
    val draftsCount = when (selectedSection) {
        DeskSection.SOUL_NOTES, DeskSection.MUSINGS, DeskSection.HEARTLINES -> writings.count { it.draft }
        DeskSection.SERIES -> seriesList.count { it.draft }
        DeskSection.GALLERY -> galleryState.drafts.totalCount
    }

    val visibleWritings = remember(writings, selectedFolderTab) {
        writings.filter { if (selectedFolderTab == ContentFolderTab.DRAFTS) it.draft else !it.draft }
    }
    val visibleSeries = remember(seriesList, selectedFolderTab) {
        seriesList.filter { if (selectedFolderTab == ContentFolderTab.DRAFTS) it.draft else !it.draft }
    }
    val visibleGalleryDoc = if (selectedFolderTab == ContentFolderTab.DRAFTS) {
        galleryState.drafts
    } else {
        galleryState.published
    }

    val isCurrentFolderEmpty = when (selectedSection) {
        DeskSection.SOUL_NOTES, DeskSection.MUSINGS, DeskSection.HEARTLINES -> visibleWritings.isEmpty()
        DeskSection.SERIES -> visibleSeries.isEmpty()
        DeskSection.GALLERY -> visibleGalleryDoc.totalCount == 0
    }
    val hasAnyLoadedData = (publishedCount + draftsCount) > 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLongPressTitle()
                                }
                            )
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .testTag("home_app_title")
                    )
                },
                actions = {
                    if (isConfigured) {
                        IconButton(
                            onClick = onRefresh,
                            enabled = !isLoading,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("home_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.home_refresh_desc),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onLockApp,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = stringResource(R.string.home_lock_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 6.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = onPrimaryCreateClick,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .height(58.dp)
                            .testTag("new_post_button")
                    ) {
                        Icon(
                            imageVector = primaryButtonIcon,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = primaryButtonText,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Warm horizontal section selector matching DeepaRam's 5 website sections
            LazyRow(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(DeskSection.entries) { section ->
                    SectionPill(
                        section = section,
                        selected = section == selectedSection,
                        onClick = { onSelectSection(section) }
                    )
                }
            }

            // Dedicated Published | Drafts folder tabs for this section
            FolderTabBar(
                selectedTab = selectedFolderTab,
                publishedCount = publishedCount,
                draftsCount = draftsCount,
                onSelectTab = onSelectFolderTab,
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (savedLocalDraft != null) {
                    item(key = "resume_draft") {
                        ResumeDraftBanner(
                            draft = savedLocalDraft,
                            onResume = onResumeDraft
                        )
                    }
                }

                if (!isConfigured) {
                    item(key = "setup_needed") {
                        SetupNeededCard()
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    item(key = "error_card") {
                        HomeErrorCard(message = errorMessage, onRetry = onRefresh)
                    }
                }

                if (isLoading && !hasAnyLoadedData) {
                    item(key = "loading_state") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 56.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Opening ${selectedSection.displayName}…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!isLoading && isCurrentFolderEmpty && isConfigured && errorMessage.isNullOrBlank()) {
                    item(key = "empty_state") {
                        EmptySectionState(
                            section = selectedSection,
                            folderTab = selectedFolderTab,
                            primaryButtonText = primaryButtonText
                        )
                    }
                }

                if (isLoading && hasAnyLoadedData) {
                    item(key = "refreshing_banner") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Refreshing ${selectedSection.displayName}…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                when (selectedSection) {
                    DeskSection.SOUL_NOTES, DeskSection.MUSINGS, DeskSection.HEARTLINES -> {
                        items(items = visibleWritings, key = { it.filePath }) { entry ->
                            WritingCard(entry = entry, onClick = { onWritingClick(entry) })
                        }
                    }

                    DeskSection.SERIES -> {
                        items(items = visibleSeries, key = { "${it.draft}_${it.slug}" }) { series ->
                            SeriesCard(series = series, onClick = { onSeriesClick(series) })
                        }
                    }

                    DeskSection.GALLERY -> {
                        if (visibleGalleryDoc.acrylicArt.isNotEmpty()) {
                            item(key = "header_acrylic_${selectedFolderTab.name}") {
                                GallerySectionHeader(
                                    title = "Acrylic on Canvas",
                                    count = visibleGalleryDoc.acrylicArt.size
                                )
                            }
                            items(
                                items = visibleGalleryDoc.acrylicArt,
                                key = { "a_${selectedFolderTab.name}_${it.id}" }
                            ) { item ->
                                GalleryItemCard(item = item, onClick = { onGalleryItemClick(item) })
                            }
                        }
                        if (visibleGalleryDoc.pencilArt.isNotEmpty()) {
                            item(key = "header_pencil_${selectedFolderTab.name}") {
                                Spacer(modifier = Modifier.height(6.dp))
                                GallerySectionHeader(
                                    title = "Pencil Art",
                                    count = visibleGalleryDoc.pencilArt.size
                                )
                            }
                            items(
                                items = visibleGalleryDoc.pencilArt,
                                key = { "p_${selectedFolderTab.name}_${it.id}" }
                            ) { item ->
                                GalleryItemCard(item = item, onClick = { onGalleryItemClick(item) })
                            }
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

/**
 * Clean 2-folder switcher separating Published items (live website) from Drafts (dedicated draft folder).
 */
@Composable
fun FolderTabBar(
    selectedTab: ContentFolderTab,
    publishedCount: Int,
    draftsCount: Int,
    onSelectTab: (ContentFolderTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FolderTabButton(
                label = "Published ($publishedCount)",
                icon = Icons.Rounded.Public,
                selected = selectedTab == ContentFolderTab.PUBLISHED,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = { onSelectTab(ContentFolderTab.PUBLISHED) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("folder_tab_published")
            )
            FolderTabButton(
                label = "Drafts ($draftsCount)",
                icon = Icons.Rounded.FolderOpen,
                selected = selectedTab == ContentFolderTab.DRAFTS,
                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                selectedContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = { onSelectTab(ContentFolderTab.DRAFTS) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("folder_tab_drafts")
            )
        }
    }
}

@Composable
private fun FolderTabButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    selectedContainerColor: androidx.compose.ui.graphics.Color,
    selectedContentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) selectedContainerColor else androidx.compose.ui.graphics.Color.Transparent,
        modifier = modifier.height(48.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) selectedContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) selectedContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionPill(
    section: DeskSection,
    selected: Boolean,
    onClick: () -> Unit
) {
    val icon: ImageVector = when (section) {
        DeskSection.SOUL_NOTES -> Icons.Rounded.Spa
        DeskSection.MUSINGS -> Icons.Rounded.AutoStories
        DeskSection.HEARTLINES -> Icons.Rounded.FavoriteBorder
        DeskSection.SERIES -> Icons.Rounded.MenuBook
        DeskSection.GALLERY -> Icons.Rounded.Palette
    }
    val bgColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .height(48.dp)
            .testTag("section_tab_${section.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = section.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor
            )
        }
    }
}

@Composable
private fun ResumeDraftBanner(
    draft: LocalDraft,
    onResume: () -> Unit
) {
    Card(
        onClick = onResume,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("resume_draft_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Unfinished ${draft.section.singularName} saved on phone",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                val previewTitle = draft.title.ifBlank { draft.body.take(48) }
                if (previewTitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = previewTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.home_resume_draft_action),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SetupNeededCard() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup_needed_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_writing_desk_empty_1795078328251),
                contentDescription = stringResource(R.string.home_setup_needed_title),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.home_setup_needed_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_setup_needed_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HomeErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_error_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("home_retry_button")
            ) {
                Text(
                    text = "Try Again",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun EmptySectionState(
    section: DeskSection,
    folderTab: ContentFolderTab,
    primaryButtonText: String
) {
    val isDrafts = folderTab == ContentFolderTab.DRAFTS
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isDrafts) {
            Image(
                painter = painterResource(id = R.drawable.img_writing_desk_empty_1795078328251),
                contentDescription = section.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
            Spacer(modifier = Modifier.height(18.dp))
        } else {
            Spacer(modifier = Modifier.height(24.dp))
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(54.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
        Text(
            text = if (isDrafts) {
                "No drafts in ${section.displayName}"
            } else {
                "No published ${section.displayName.lowercase()} yet"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isDrafts) {
                "When you tap \"Save Draft\", it stays safely in this folder — hidden from your website until you are ready to publish."
            } else {
                "Tap \"$primaryButtonText\" below to add your first one."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WritingCard(
    entry: WritingEntry,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("post_item_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.displayDate.ifBlank { PostFormatter.formatDisplayDate(entry.date) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AnimatedVisibility(visible = !entry.imagePublicPath.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Rounded.Image,
                                contentDescription = stringResource(R.string.home_photo_attached_desc),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                StatusBadge(draft = entry.draft)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (entry.snippet.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = entry.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (entry.section == DeskSection.HEARTLINES) 3 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SeriesCard(
    series: SeriesSummary,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("series_item_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    val pubLabel = if (series.publishedChapterCount == 1) {
                        "1 Chapter"
                    } else {
                        "${series.publishedChapterCount} Chapters"
                    }
                    val fullCountLabel = if (series.draftChapterCount > 0) {
                        val dLabel = if (series.draftChapterCount == 1) "1 Draft" else "${series.draftChapterCount} Drafts"
                        "$pubLabel · $dLabel"
                    } else {
                        pubLabel
                    }
                    Text(
                        text = fullCountLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                StatusBadge(draft = series.draft)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = series.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (series.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = series.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Open series chapters",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun GallerySectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Brush,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun GalleryItemCard(
    item: GalleryItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("gallery_item_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.remotePreviewUrl,
                contentDescription = item.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                StatusBadge(draft = item.draft)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.caption.ifBlank { "Untitled Artwork" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.src.size > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${item.src.size} paired images",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(draft: Boolean) {
    val badgeBg = if (draft) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val badgeText = if (draft) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val badgeLabel = if (draft) {
        stringResource(R.string.home_badge_draft)
    } else {
        stringResource(R.string.home_badge_published)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(badgeBg)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = badgeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = badgeText
        )
    }
}
