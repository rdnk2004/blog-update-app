package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.markdown.PostFormatter
import com.example.data.model.DeskSection
import com.example.ui.EditorState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorState,
    onBack: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onChapterLabelChanged: (String) -> Unit,
    onBodyChanged: (String) -> Unit,
    onDateChanged: (String) -> Unit,
    createCameraUri: () -> Uri,
    onPhotoSelected: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
    onPreview: () -> Unit,
    onSaveDraft: () -> Unit,
    onPublish: () -> Unit,
    onDeletePost: () -> Unit
) {
    BackHandler(enabled = !state.isSaving) {
        onBack()
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showPublishConfirm by remember { mutableStateOf(false) }
    var showMoveToDraftsConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPhotoSelected(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCameraUri
        pendingCameraUri = null
        if (success && capturedUri != null) onPhotoSelected(capturedUri)
    }

    val controlsEnabled = !state.isSaving && !state.isCompressingPhoto

    val bodyLabel = when {
        state.isEditingSeriesMeta -> "About This Series"
        state.section == DeskSection.HEARTLINES -> "Your Poem"
        state.isChapterMode -> "Chapter Story"
        else -> stringResource(R.string.editor_body_label)
    }
    val bodyPlaceholder = when {
        state.isEditingSeriesMeta -> "Write a short introduction to this series…"
        state.section == DeskSection.HEARTLINES -> "Write your lines and stanzas here — every line break is kept…"
        else -> stringResource(R.string.editor_body_placeholder)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !state.isSaving,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back_desc)
                        )
                    }
                },
                title = {
                    Text(
                        text = state.screenHeading,
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                actions = {
                    if (!state.isEditingSeriesMeta) {
                        OutlinedButton(
                            onClick = onPreview,
                            enabled = controlsEnabled,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .height(44.dp)
                                .testTag("editor_preview_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.editor_preview),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
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
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isSaving) {
                        Row(
                            modifier = Modifier
                                .widthIn(max = 560.dp)
                                .fillMaxWidth()
                                .height(56.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = state.savingStatusMessage.ifBlank { "Saving…" },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    } else {
                        val primaryActionText = when {
                            !state.isEditingPublished -> stringResource(R.string.editor_publish)
                            state.isEditingSeriesMeta -> "Update Series"
                            state.isChapterMode -> "Update Chapter"
                            else -> "Update Live"
                        }
                        val secondaryActionText = if (state.isEditingPublished) {
                            "Move to Drafts"
                        } else {
                            stringResource(R.string.editor_save_draft)
                        }

                        Row(
                            modifier = Modifier
                                .widthIn(max = 560.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (state.isEditingPublished) {
                                        showMoveToDraftsConfirm = true
                                    } else {
                                        onSaveDraft()
                                    }
                                },
                                enabled = controlsEnabled,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .testTag("editor_save_draft_button")
                            ) {
                                Text(
                                    text = secondaryActionText,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Button(
                                onClick = { showPublishConfirm = true },
                                enabled = controlsEnabled,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .testTag("editor_publish_button")
                            ) {
                                Text(
                                    text = primaryActionText,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Context chip showing which section (and series) this piece belongs to
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val contextLabel = if (state.isChapterMode && !state.seriesTitle.isNullOrBlank()) {
                        "Series: ${state.seriesTitle}"
                    } else {
                        state.section.displayName
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = contextLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (state.isEditingPublished) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Live on Website",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    } else if (state.isEditingExisting) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Draft",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    AnimatedVisibility(visible = state.autoSavedAtMillis != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.editor_autosaved),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                // Plain-language error message + optional subtle diagnostic for Setup check
                AnimatedVisibility(visible = !state.errorMessage.isNullOrBlank()) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_error_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = state.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            if (!state.errorDiagnostic.isNullOrBlank()) {
                                Text(
                                    text = "Setup detail: ${state.errorDiagnostic}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // Chapter Label (only shown when writing a chapter inside a Series)
                if (state.isChapterMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Chapter Label",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = state.chapterLabel,
                            onValueChange = onChapterLabelChanged,
                            enabled = controlsEnabled,
                            singleLine = true,
                            placeholder = { Text("e.g. Chapter 13, Prologue, Epilogue") },
                            shape = RoundedCornerShape(14.dp),
                            colors = editorFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_chapter_label_input")
                        )
                    }
                }

                // Title (single line)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.editor_title_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = onTitleChanged,
                        enabled = controlsEnabled,
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.editor_title_placeholder),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = RoundedCornerShape(14.dp),
                        colors = editorFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_title_input")
                    )
                }

                // Date picker row
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.editor_date_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        onClick = { if (controlsEnabled) showDatePicker = true },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_date_picker_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = PostFormatter.formatDisplayDate(state.date, longMonth = true)
                                        .ifBlank { state.date },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = stringResource(R.string.editor_change_date),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Photo section (for Soul Notes, Musings, Heartlines Poems, and Chapters)
                if (!state.isEditingSeriesMeta) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.editor_photo_label),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (state.isCompressingPhoto) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Preparing your photo…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else if (state.hasVisiblePhoto) {
                            val imageModel: Any? = when {
                                !state.localPhotoPath.isNullOrBlank() -> File(state.localPhotoPath)
                                !state.existingRemoteImageUrl.isNullOrBlank() -> state.existingRemoteImageUrl
                                else -> null
                            }
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    if (imageModel != null) {
                                        AsyncImage(
                                            model = imageModel,
                                            contentDescription = "Selected post photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(220.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .testTag("editor_photo_preview")
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = onRemovePhoto,
                                        enabled = controlsEnabled,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("editor_remove_photo_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.editor_remove_photo),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val captureUri = createCameraUri()
                                        pendingCameraUri = captureUri
                                        cameraLauncher.launch(captureUri)
                                    },
                                    enabled = controlsEnabled,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .testTag("editor_take_photo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PhotoCamera,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.editor_take_photo),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        galleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    enabled = controlsEnabled,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .testTag("editor_gallery_photo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PhotoLibrary,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.editor_choose_gallery),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Body (large multiline area)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = bodyLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = state.body,
                        onValueChange = onBodyChanged,
                        enabled = controlsEnabled,
                        placeholder = {
                            Text(
                                text = bodyPlaceholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = RoundedCornerShape(14.dp),
                        colors = editorFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = if (state.isEditingSeriesMeta) 160.dp else 240.dp)
                            .testTag("editor_body_input")
                    )
                }

                // Delete option ONLY when editing an existing item (never on list)
                if (state.isEditingExisting) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = controlsEnabled,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("editor_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.isEditingPublished) {
                                "Remove From Website"
                            } else {
                                "Delete Draft"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = remember(state.date) { parseIsoDateToUtcMillis(state.date) }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { onDateChanged(formatUtcMillisToIsoDate(it)) }
                        showDatePicker = false
                    }
                ) { Text("Done", style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showPublishConfirm) {
        val displayTitle = state.title.trim().ifBlank { stringResource(R.string.preview_untitled) }
        val confirmTitle = if (state.isEditingPublished) {
            "Update your live post?"
        } else {
            stringResource(R.string.editor_confirm_publish_title)
        }
        val confirmBody = if (state.isEditingPublished) {
            "Your changes to \"$displayTitle\" will update the live post on your website in about a minute."
        } else {
            stringResource(R.string.editor_confirm_publish_body, displayTitle)
        }
        val confirmYes = if (state.isEditingPublished) {
            "Yes, Update"
        } else {
            stringResource(R.string.editor_confirm_publish_yes)
        }

        AlertDialog(
            onDismissRequest = { showPublishConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = confirmTitle,
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    text = confirmBody,
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPublishConfirm = false
                        onPublish()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("confirm_publish_yes_button")
                ) {
                    Text(
                        text = confirmYes,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPublishConfirm = false },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.editor_confirm_cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }

    if (showMoveToDraftsConfirm) {
        val displayTitle = state.title.trim().ifBlank { stringResource(R.string.preview_untitled) }
        AlertDialog(
            onDismissRequest = { showMoveToDraftsConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Move to Drafts folder?",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    text = "\"$displayTitle\" will be taken off your live website and kept safely in your Drafts folder so you can edit or publish it again anytime.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMoveToDraftsConfirm = false
                        onSaveDraft()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("confirm_move_to_drafts_yes_button")
                ) {
                    Text(
                        text = "Yes, Move to Drafts",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showMoveToDraftsConfirm = false },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = "Keep Live",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }

    if (showDeleteConfirm) {
        val displayTitle = state.title.trim().ifBlank { stringResource(R.string.preview_untitled) }
        val deleteTitle = if (state.isEditingPublished) {
            stringResource(R.string.editor_confirm_delete_title)
        } else {
            "Delete this draft?"
        }
        val deleteBody = if (state.isEditingPublished) {
            stringResource(R.string.editor_confirm_delete_body, displayTitle)
        } else {
            "\"$displayTitle\" will be removed from your Drafts folder. Are you sure?"
        }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = deleteTitle,
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    text = deleteBody,
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeletePost()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("confirm_delete_yes_button")
                ) {
                    Text(
                        text = if (state.isEditingPublished) {
                            stringResource(R.string.editor_confirm_delete_yes)
                        } else {
                            "Yes, Delete"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.editor_confirm_delete_keep),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }
}

@Composable
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline
)

private fun parseIsoDateToUtcMillis(isoDate: String): Long? {
    return try {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        formatter.parse(isoDate)?.time
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
}

private fun formatUtcMillisToIsoDate(utcMillis: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return formatter.format(Date(utcMillis))
}
