package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.data.model.ArtMedium
import com.example.ui.GalleryEditorState
import java.io.File

/**
 * Photo-first editor for adding or editing paintings and sketches in DeepaRam's Art Gallery
 * (`content/gallery.json` + `public/gallery/`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryEditorScreen(
    state: GalleryEditorState,
    onBack: () -> Unit,
    onMediumChanged: (ArtMedium) -> Unit,
    onCaptionChanged: (String) -> Unit,
    createCameraUri: () -> Uri,
    onPhotoSelected: (Uri) -> Unit,
    onSaveDraft: () -> Unit,
    onPublish: () -> Unit,
    onDelete: () -> Unit
) {
    BackHandler(enabled = !state.isSaving) {
        onBack()
    }

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
                            .testTag("gallery_editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back_desc)
                        )
                    }
                },
                title = {
                    Text(
                        text = when {
                            state.isEditingPublished -> "Edit Live Artwork"
                            state.isEditingExisting -> "Edit Artwork Draft"
                            else -> "Add Artwork"
                        },
                        style = MaterialTheme.typography.headlineSmall
                    )
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
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    } else {
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
                                    .testTag("gallery_save_draft_button")
                            ) {
                                Text(
                                    text = if (state.isEditingPublished) {
                                        "Move to Drafts"
                                    } else {
                                        stringResource(R.string.editor_save_draft)
                                    },
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
                                    .testTag("gallery_publish_button")
                            ) {
                                Text(
                                    text = if (state.isEditingPublished) {
                                        "Update Live"
                                    } else {
                                        stringResource(R.string.editor_publish)
                                    },
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
                if (state.isEditingExisting) {
                    StatusBadge(draft = !state.isEditingPublished)
                }

                AnimatedVisibility(visible = !state.errorMessage.isNullOrBlank()) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
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

                // Choose Gallery Category (Acrylic vs Pencil)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Gallery Collection",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ArtMedium.entries.forEach { medium ->
                            val selected = state.medium == medium
                            Surface(
                                onClick = { if (controlsEnabled) onMediumChanged(medium) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.5.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .testTag("gallery_medium_${medium.name.lowercase()}")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                    Text(
                                        text = medium.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Artwork Photo Preview & Picker
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Artwork Photo",
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
                                modifier = Modifier.padding(24.dp),
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
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    } else if (state.hasPhoto) {
                        val imageModel: Any? = when {
                            !state.localPhotoPath.isNullOrBlank() -> File(state.localPhotoPath)
                            !state.existingPreviewUrl.isNullOrBlank() -> state.existingPreviewUrl
                            else -> null
                        }
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AsyncImage(
                                model = imageModel,
                                contentDescription = "Artwork preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val uri = createCameraUri()
                                pendingCameraUri = uri
                                cameraLauncher.launch(uri)
                            },
                            enabled = controlsEnabled,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("gallery_take_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.hasPhoto) "Retake" else stringResource(R.string.editor_take_photo),
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
                                .testTag("gallery_choose_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.hasPhoto) "Change Photo" else "Gallery",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Caption field
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Caption",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = state.caption,
                        onValueChange = onCaptionChanged,
                        enabled = controlsEnabled,
                        placeholder = {
                            Text(
                                text = "Write a caption for your artwork…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gallery_caption_input")
                    )
                }

                if (state.isEditingExisting) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = controlsEnabled,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("gallery_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.isEditingPublished) "Remove From Gallery" else "Delete Draft Artwork",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showPublishConfirm) {
        AlertDialog(
            onDismissRequest = { showPublishConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = if (state.isEditingPublished) "Update live artwork?" else "Add to your Art Gallery?",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    text = if (state.isEditingPublished) {
                        "Your updated artwork details will appear in your ${state.medium.displayName} gallery in about a minute."
                    } else {
                        "This artwork will appear in your ${state.medium.displayName} gallery so everyone can see it."
                    },
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
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = if (state.isEditingPublished) "Yes, Update" else "Yes, Publish",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPublishConfirm = false },
                    modifier = Modifier.height(48.dp)
                ) { Text("Not yet", style = MaterialTheme.typography.labelLarge) }
            }
        )
    }

    if (showMoveToDraftsConfirm) {
        AlertDialog(
            onDismissRequest = { showMoveToDraftsConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Move artwork to Drafts?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    text = "This artwork will be hidden from your live gallery and kept safely in your Art Gallery Drafts folder.",
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
                    modifier = Modifier.height(48.dp)
                ) { Text("Yes, Move to Drafts", style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showMoveToDraftsConfirm = false },
                    modifier = Modifier.height(48.dp)
                ) { Text("Keep Live", style = MaterialTheme.typography.labelLarge) }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = if (state.isEditingPublished) "Remove this artwork?" else "Delete draft artwork?",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    text = if (state.isEditingPublished) {
                        "This artwork will be removed from your gallery page. Are you sure?"
                    } else {
                        "This draft artwork will be deleted from your Drafts folder. Are you sure?"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) { Text(if (state.isEditingPublished) "Yes, Remove" else "Yes, Delete", style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    modifier = Modifier.height(48.dp)
                ) { Text("Keep Artwork", style = MaterialTheme.typography.labelLarge) }
            }
        )
    }
}
