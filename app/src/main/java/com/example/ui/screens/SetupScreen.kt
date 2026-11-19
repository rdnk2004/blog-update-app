package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WifiFind
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ConnectionTestResult
import com.example.data.model.GitHubSettings

/**
 * Hidden Setup screen (reached via long-press on "My Blog Desk" title).
 * For the site maintainer only: configures GitHub fine-grained token, repo, branch, and content folders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    currentSettings: GitHubSettings,
    isTestingConnection: Boolean,
    connectionTestResult: ConnectionTestResult?,
    onBack: () -> Unit,
    onTestConnection: (GitHubSettings) -> Unit,
    onSave: (GitHubSettings, clearExistingToken: Boolean) -> Unit,
    onResetPin: () -> Unit
) {
    BackHandler(enabled = !isTestingConnection) {
        onBack()
    }

    var isReplacingToken by remember(currentSettings.hasToken) { mutableStateOf(!currentSettings.hasToken) }
    var clearTokenRequested by remember { mutableStateOf(false) }
    var newTokenInput by remember { mutableStateOf("") }
    var owner by remember(currentSettings.owner) { mutableStateOf(currentSettings.owner) }
    var repo by remember(currentSettings.repo) { mutableStateOf(currentSettings.repo) }
    var branch by remember(currentSettings.branch) { mutableStateOf(currentSettings.branch) }
    var contentRoot by remember(currentSettings.contentRoot) { mutableStateOf(currentSettings.contentRoot) }
    var imagesDir by remember(currentSettings.imagesDir) { mutableStateOf(currentSettings.imagesDir) }
    var galleryImagesDir by remember(currentSettings.galleryImagesDir) { mutableStateOf(currentSettings.galleryImagesDir) }
    var siteUrl by remember(currentSettings.siteUrl) { mutableStateOf(currentSettings.siteUrl) }
    var showResetPinDialog by remember { mutableStateOf(false) }

    fun buildCandidateSettings(): GitHubSettings = GitHubSettings(
        token = if (clearTokenRequested) "" else newTokenInput.trim(),
        owner = owner.trim(),
        repo = repo.trim(),
        branch = branch.trim(),
        contentRoot = contentRoot.trim().trim('/'),
        imagesDir = imagesDir.trim().trim('/'),
        galleryImagesDir = galleryImagesDir.trim().trim('/'),
        siteUrl = siteUrl.trim()
    )

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
                        enabled = !isTestingConnection,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("setup_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back_desc)
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.setup_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = stringResource(R.string.setup_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = { onSave(buildCandidateSettings(), clearTokenRequested) },
                        enabled = !isTestingConnection,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("setup_save_button")
                    ) {
                        Text(
                            text = stringResource(R.string.setup_save),
                            style = MaterialTheme.typography.labelLarge
                        )
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Fine-grained token security notice
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.setup_security_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                // Masked GitHub Token field
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.setup_token_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (currentSettings.hasToken && !isReplacingToken && !clearTokenRequested) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${currentSettings.maskedTokenHint}  (${stringResource(R.string.setup_token_saved_hint)})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = { isReplacingToken = true },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("setup_replace_token_button")
                                    ) {
                                        Text(
                                            text = stringResource(R.string.setup_token_replace),
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            clearTokenRequested = true
                                            isReplacingToken = true
                                            newTokenInput = ""
                                        },
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text(
                                            text = "Clear Token",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = newTokenInput,
                            onValueChange = {
                                newTokenInput = it
                                clearTokenRequested = false
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            placeholder = {
                                Text("github_pat_…", style = MaterialTheme.typography.bodyMedium)
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = setupTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_token_input")
                        )
                    }
                }

                SetupTextField(
                    label = stringResource(R.string.setup_owner_label),
                    value = owner,
                    onValueChange = { owner = it },
                    testTag = "setup_owner_input"
                )

                SetupTextField(
                    label = stringResource(R.string.setup_repo_label),
                    value = repo,
                    onValueChange = { repo = it },
                    testTag = "setup_repo_input"
                )

                SetupTextField(
                    label = stringResource(R.string.setup_branch_label),
                    value = branch,
                    onValueChange = { branch = it },
                    testTag = "setup_branch_input"
                )

                SetupTextField(
                    label = "Content Root Folder",
                    value = contentRoot,
                    onValueChange = { contentRoot = it },
                    testTag = "setup_content_root_input"
                )

                SetupTextField(
                    label = "Story & Note Images Folder",
                    value = imagesDir,
                    onValueChange = { imagesDir = it },
                    testTag = "setup_images_path_input"
                )

                SetupTextField(
                    label = "Art Gallery Images Folder",
                    value = galleryImagesDir,
                    onValueChange = { galleryImagesDir = it },
                    testTag = "setup_gallery_images_path_input"
                )

                SetupTextField(
                    label = stringResource(R.string.setup_site_url_label),
                    value = siteUrl,
                    onValueChange = { siteUrl = it },
                    testTag = "setup_site_url_input"
                )

                OutlinedButton(
                    onClick = { onTestConnection(buildCandidateSettings()) },
                    enabled = !isTestingConnection,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("setup_test_connection_button")
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Testing connection…", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.WifiFind,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.setup_test_connection),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                AnimatedVisibility(visible = connectionTestResult != null) {
                    when (val result = connectionTestResult) {
                        is ConnectionTestResult.Success -> {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setup_test_success")
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Connected to ${result.repoFullName} (${result.branch})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = result.summaryLine,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                        is ConnectionTestResult.Error -> {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setup_test_error")
                            ) {
                                Text(
                                    text = result.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        null -> Unit
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                OutlinedButton(
                    onClick = { showResetPinDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("setup_reset_pin_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LockReset,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.setup_reset_pin),
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showResetPinDialog) {
        AlertDialog(
            onDismissRequest = { showResetPinDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Change 4-Digit PIN?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    text = "This will clear the current PIN and ask for a new 4-digit PIN right away.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetPinDialog = false
                        onResetPin()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) { Text("Change PIN", style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetPinDialog = false },
                    modifier = Modifier.height(48.dp)
                ) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
            }
        )
    }
}

@Composable
private fun SetupTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = setupTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}

@Composable
private fun setupTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline
)
