package com.airgradient.android.ui.locationdetail.Views

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.airgradient.android.R
import com.airgradient.android.data.models.LocationDetailUiState
import com.airgradient.android.data.models.ShareUiState
import com.airgradient.android.ui.location.Views.NotificationsBottomSheet
import com.airgradient.android.ui.locationdetail.ViewModels.LocationDetailViewModel
import com.airgradient.android.ui.shared.ShareEvent
import com.airgradient.android.ui.shared.Views.AgTopBar
import com.airgradient.android.ui.community.Views.ProjectDetailSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailScreen(
    locationId: Int,
    onNavigateBack: () -> Unit,
    viewModel: LocationDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showNotifications by remember { mutableStateOf(false) }
    var showRemoveBookmarkDialog by remember { mutableStateOf(false) }

    LaunchedEffect(locationId) {
        viewModel.showLocationDetail(locationId)
    }

    LaunchedEffect(viewModel) {
        viewModel.shareEvents.collect { event ->
            val intent = when (event) {
                is ShareEvent.Text -> Intent(Intent.ACTION_SEND).apply {
                    type = event.mimeType
                    putExtra(Intent.EXTRA_SUBJECT, event.subject)
                    putExtra(Intent.EXTRA_TEXT, event.message)
                }
                is ShareEvent.Image -> Intent(Intent.ACTION_SEND).apply {
                    type = event.mimeType
                    putExtra(Intent.EXTRA_SUBJECT, event.subject)
                    putExtra(Intent.EXTRA_TEXT, event.message)
                    putExtra(Intent.EXTRA_STREAM, event.uri)
                    clipData = android.content.ClipData.newUri(
                        context.contentResolver,
                        event.subject,
                        event.uri
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            runCatching {
                if (context !is Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)))
            }.onFailure {
                Toast.makeText(context, R.string.error_share_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(uiState.shareState.errorMessage) {
        uiState.shareState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.dismissDialog() }
    }

    val title = uiState.location?.name ?: stringResource(R.string.unknown_location)
    val handleBookmarkToggle = remember(uiState.isBookmarked) {
        {
            if (uiState.isBookmarked) {
                showRemoveBookmarkDialog = true
            } else {
                viewModel.toggleBookmark()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LocationDetailTopBar(
                title = title,
                shareState = uiState.shareState,
                isBookmarked = uiState.isBookmarked,
                hasNotifications = uiState.hasActiveNotifications,
                onBack = onNavigateBack,
                onShare = viewModel::shareLocation,
                onToggleBookmark = handleBookmarkToggle,
                onNotifications = {
                    if (uiState.location?.id != null) {
                        showNotifications = true
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error ?: "",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                LocationDetailScreenContent(
                    modifier = Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding),
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }
    }

    if (showNotifications) {
        val location = uiState.location
        if (location != null) {
            NotificationsBottomSheet(
                onDismiss = { shouldRefresh ->
                    showNotifications = false
                    if (shouldRefresh) {
                        viewModel.refreshNotificationStatus()
                    }
                },
                locationId = location.id,
                locationName = location.name
            )
        } else {
            showNotifications = false
        }
    }

    if (showRemoveBookmarkDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveBookmarkDialog = false },
            title = { Text(stringResource(R.string.button_remove_bookmark)) },
            text = { Text(stringResource(R.string.confirm_remove_bookmark_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveBookmarkDialog = false
                        viewModel.toggleBookmark()
                    }
                ) {
                    Text(stringResource(R.string.button_remove_bookmark))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveBookmarkDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    ProjectDetailSheet(
        state = uiState.communityProjectDetail,
        onDismiss = viewModel::dismissCommunityProjectDetail,
        onRetry = viewModel::retryCommunityProjectDetail
    )
}

@Composable
private fun LocationDetailScreenContent(
    modifier: Modifier,
    uiState: LocationDetailUiState,
    viewModel: LocationDetailViewModel
) {
    Box(modifier = modifier.fillMaxSize()) {
        LocationDetailContent(
            uiState = uiState,
            onTimeframeChange = viewModel::updateChartTimeframe,
            onWHOGuidelinesClick = { /* TODO: implement navigation to WHO details */ },
            onCommunityProjectsRetry = viewModel::retryCommunityProjects,
            onCommunityProjectSelected = viewModel::onCommunityProjectSelected
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationDetailTopBar(
    title: String,
    shareState: ShareUiState,
    isBookmarked: Boolean,
    hasNotifications: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onToggleBookmark: () -> Unit,
    onNotifications: () -> Unit
) {
    val iconTint = MaterialTheme.colorScheme.primary
    val shareEnabled = shareState.isReady
    val shareIconColor = if (shareEnabled) {
        iconTint
    } else {
        iconTint.copy(alpha = 0.5f)
    }

    AgTopBar(
        title = title,
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back_button),
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        actions = {
            IconButton(
                onClick = onShare,
                enabled = shareEnabled,
                modifier = Modifier.size(48.dp)
            ) {
                if (shareState.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = iconTint
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = stringResource(R.string.button_share_location),
                        tint = shareIconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            IconButton(
                onClick = onNotifications,
                modifier = Modifier.size(48.dp)
            ) {
                val notificationsIcon = if (hasNotifications) {
                    Icons.Filled.Notifications
                } else {
                    Icons.Outlined.Notifications
                }
                Icon(
                    imageVector = notificationsIcon,
                    contentDescription = stringResource(R.string.settings_section_notifications),
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = onToggleBookmark,
                modifier = Modifier.size(48.dp)
            ) {
                val icon = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder
                val description = if (isBookmarked) {
                    stringResource(R.string.button_remove_bookmark)
                } else {
                    stringResource(R.string.button_bookmark_location)
                }
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            navigationIconContentColor = iconTint,
            actionIconContentColor = iconTint,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
