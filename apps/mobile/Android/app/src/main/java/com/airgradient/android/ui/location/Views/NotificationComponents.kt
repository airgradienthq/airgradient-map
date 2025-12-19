package com.airgradient.android.ui.location.Views

import android.Manifest
import android.os.Build
import android.util.Log
import android.widget.NumberPicker
import android.widget.TextView
import androidx.compose.animation.animateColorAsState
import android.graphics.Color as AndroidColor
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.airgradient.android.R
import com.airgradient.android.data.models.AQIColorPalette
import com.airgradient.android.domain.models.NotificationWeekday
import com.airgradient.android.domain.models.ThresholdAlertFrequency
import com.airgradient.android.ui.location.ViewModels.NotificationSettingsViewModel
import com.airgradient.android.ui.location.ViewModels.NotificationSettingsUiState
import com.airgradient.android.ui.location.ViewModels.ScheduledNotificationUiModel
import com.airgradient.android.ui.shared.Views.AgBottomSheetDefaults
import com.airgradient.android.ui.shared.Views.AgBottomSheetTopBar
import com.airgradient.android.ui.shared.Utils.aqiCategoryLabelRes
import com.airgradient.android.ui.shared.Views.LabeledSwitchRow
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.onesignal.OneSignal
import java.util.Locale
import kotlinx.coroutines.launch

private const val NOTIFICATIONS_TAG = "NotificationsBottomSheet"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun NotificationsBottomSheet(
    onDismiss: (Boolean) -> Unit,
    locationId: Int,
    locationName: String,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }

    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var hasPendingChanges by remember { mutableStateOf(false) }
    var initialSnapshot by remember(locationId) { mutableStateOf<NotificationSettingsSnapshot?>(null) }

    val currentSnapshot = NotificationSettingsSnapshot.fromUiState(uiState)
    val baselineReady = uiState.playerIdMissing ||
        uiState.locationName.isNotBlank() ||
        uiState.scheduledNotifications.isNotEmpty() ||
        uiState.thresholdRegistrationId != null

    LaunchedEffect(currentSnapshot, baselineReady, uiState.isLoading) {
        if (initialSnapshot == null && baselineReady && !uiState.isLoading) {
            initialSnapshot = currentSnapshot
        }
    }

    val dataChanged = initialSnapshot != null && initialSnapshot != currentSnapshot
    val shouldRefresh = hasPendingChanges || dataChanged
    val refreshOnDismiss by rememberUpdatedState(newValue = shouldRefresh)

    LaunchedEffect(notificationPermissionState?.status) {
        val status = notificationPermissionState?.status
        when (status) {
            is PermissionStatus.Granted -> {
                Log.d(NOTIFICATIONS_TAG, "POST_NOTIFICATIONS granted; executing pending action")
                pendingPermissionAction?.invoke()
                pendingPermissionAction = null
                if (uiState.playerIdMissing) {
                    Log.d(NOTIFICATIONS_TAG, "Refreshing settings after permission grant")
                    viewModel.refresh()
                }
            }
            is PermissionStatus.Denied -> {
                Log.d(
                    NOTIFICATIONS_TAG,
                    "POST_NOTIFICATIONS denied (shouldShowRationale=${status.shouldShowRationale})"
                )
                pendingPermissionAction = null
            }
            null -> Unit
        }
    }

    fun requestNotificationPermissionIfNeeded(action: () -> Unit) {
        val state = notificationPermissionState
        if (state == null || state.status.isGranted) {
            Log.d(NOTIFICATIONS_TAG, "Permission already granted or not required; running action immediately")
            action()
        } else {
            pendingPermissionAction = action
            coroutineScope.launch {
                Log.d(
                    NOTIFICATIONS_TAG,
                    "Invoking OneSignal.Notifications.requestPermission(fallbackToSettings=true)"
                )
                val result = runCatching {
                    OneSignal.Notifications.requestPermission(fallbackToSettings = true)
                }

                val granted = result.getOrNull()
                if (granted == true) {
                    Log.d(NOTIFICATIONS_TAG, "OneSignal returned granted=true; consuming pending action")
                    pendingPermissionAction?.invoke()
                    pendingPermissionAction = null
                } else {
                    if (result.isFailure) {
                        Log.e(NOTIFICATIONS_TAG, "OneSignal request failed", result.exceptionOrNull())
                    } else {
                        Log.d(
                            NOTIFICATIONS_TAG,
                            "OneSignal returned $granted; falling back to system permission dialog"
                        )
                    }
                    // Fall back to the platform permission request if OneSignal is not ready or declined.
                    Log.d(NOTIFICATIONS_TAG, "Launching platform POST_NOTIFICATIONS prompt")
                    state.launchPermissionRequest()
                }
            }
        }
    }

    LaunchedEffect(locationId) {
        viewModel.initialize(locationId, locationName)
    }

    var showAddTimeSheet by remember { mutableStateOf(false) }
    var editingSchedule by remember(locationId) { mutableStateOf<ScheduledNotificationUiModel?>(null) }
    var pendingDeletion by remember { mutableStateOf<ScheduledNotificationUiModel?>(null) }

    val categories = remember { buildThresholdCategories() }
    val fallbackThreshold = categories.firstOrNull()?.alertThreshold ?: 0.0
    val selectedThresholdValue = uiState.thresholdValueUg ?: fallbackThreshold
    val currentCategoryKey = categories.firstOrNull { category ->
        isApproximately(category.alertThreshold, selectedThresholdValue)
    }?.key ?: categories.firstOrNull()?.key.orEmpty()
    
    ModalBottomSheet(
        onDismissRequest = { onDismiss(refreshOnDismiss) },
        sheetState = modalBottomSheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(AgBottomSheetDefaults.MAX_EXPANDED_HEIGHT_FRACTION)
                .verticalScroll(rememberScrollState())
        ) {
            AgBottomSheetTopBar(
                title = uiState.locationName.ifEmpty { locationName },
                actions = {
                    TextButton(
                        onClick = { onDismiss(refreshOnDismiss) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.action_done),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                if (uiState.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }

                uiState.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                if (uiState.playerIdMissing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permissionStatus = notificationPermissionState?.status
                    ElevatedButton(
                        onClick = {
                            requestNotificationPermissionIfNeeded {
                                viewModel.refresh()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Text(stringResource(R.string.notifications_enable_push_button))
                    }

                    if (permissionStatus is PermissionStatus.Denied) {
                        val instructionalText = if (permissionStatus.shouldShowRationale) {
                            stringResource(R.string.notifications_permission_rationale)
                        } else {
                            stringResource(R.string.notifications_permission_settings_message)
                        }
                        Text(
                            text = instructionalText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }

                // Scheduled Notifications Section
                Text(
                    text = stringResource(R.string.notifications_section_scheduled_header),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                uiState.scheduledNotifications.forEach { item ->
                    key("scheduled-${item.registrationId}-${item.time}") {
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    if (item.registrationId != null) {
                                        pendingDeletion = item
                                        false
                                    } else {
                                        false
                                    }
                                } else {
                                    false
                                }
                            }
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromEndToStart = true,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = { NotificationDismissBackground(dismissState) }
                            ) {
                                NotificationTimeItem(
                                    model = item,
                                    onToggle = { enabled ->
                                        requestNotificationPermissionIfNeeded {
                                            hasPendingChanges = true
                                            viewModel.toggleSchedule(item, enabled)
                                        }
                                    },
                                    onClick = {
                                        editingSchedule = item
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Add Time Button
                TextButton(
                    onClick = { showAddTimeSheet = true },
                    modifier = Modifier.padding(vertical = 8.dp),
                    enabled = !uiState.playerIdMissing
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF007AFF)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.notifications_add_time), color = Color(0xFF007AFF))
                }

                Text(
                text = stringResource(R.string.settings_scheduled_notifications_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                HorizontalDivider(
                    Modifier,
                    DividerDefaults.Thickness,
                    color = Color.Gray.copy(alpha = 0.2f)
                )

                Spacer(modifier = Modifier.height(24.dp))
                
                // PM2.5 Threshold Alerts Section
                Text(
                    text = stringResource(R.string.notifications_section_threshold_header),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Enable threshold alerts toggle
                LabeledSwitchRow(
                    checked = uiState.thresholdEnabled,
                    onCheckedChange = { enabled ->
                        requestNotificationPermissionIfNeeded {
                            hasPendingChanges = true
                            viewModel.updateThreshold(
                                thresholdValueUg = selectedThresholdValue,
                                frequency = uiState.thresholdFrequency,
                                enabled = enabled
                            )
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF007AFF)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.notifications_enable_threshold),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Start,
                        maxLines = 2
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (uiState.thresholdEnabled) {
                    Text(
                        text = stringResource(R.string.notifications_high_alert_threshold),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // AQI Categories
                    ThresholdCategoryList(
                        categories = categories,
                        currentCategoryKey = currentCategoryKey,
                        onCategorySelect = { category ->
                            requestNotificationPermissionIfNeeded {
                                hasPendingChanges = true
                                viewModel.updateThreshold(
                                    thresholdValueUg = category.alertThreshold,
                                    frequency = uiState.thresholdFrequency,
                                    enabled = uiState.thresholdEnabled
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    AlertFrequencySection(
                        selectedFrequency = uiState.thresholdFrequency,
                        onFrequencySelected = { frequency ->
                            requestNotificationPermissionIfNeeded {
                                hasPendingChanges = true
                                viewModel.updateThreshold(
                                    thresholdValueUg = selectedThresholdValue,
                                    frequency = frequency,
                                    enabled = uiState.thresholdEnabled
                                )
                            }
                        },
                        triggerValue = selectedThresholdValue,
                        triggerUnit = stringResource(R.string.unit_ugm3)
                    )

                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    // Add Time Bottom Sheet
    if (showAddTimeSheet) {
        AddTimeBottomSheet(
            onDismiss = { showAddTimeSheet = false },
            onSave = { time, days ->
                requestNotificationPermissionIfNeeded {
                    hasPendingChanges = true
                    viewModel.addSchedule(time, days)
                    showAddTimeSheet = false
                }
            }
        )
    }

    editingSchedule?.let { schedule ->
        AddTimeBottomSheet(
            onDismiss = { editingSchedule = null },
            onSave = { time, days ->
                requestNotificationPermissionIfNeeded {
                    hasPendingChanges = true
                    viewModel.updateSchedule(schedule, time, days)
                    editingSchedule = null
                }
            },
            initialTime = schedule.time,
            initialDays = schedule.days,
            titleRes = R.string.notifications_edit_time
        )
    }

    pendingDeletion?.let { model ->
        DeleteScheduledTimeDialog(
            schedule = model,
            onConfirm = {
                requestNotificationPermissionIfNeeded {
                    hasPendingChanges = true
                    model.registrationId?.let { viewModel.deleteSchedule(it) }
                    pendingDeletion = null
                }
            },
            onDismiss = { pendingDeletion = null }
        )
    }
}

private data class NotificationSettingsSnapshot(
    val scheduledNotifications: List<ScheduledNotificationUiModel>,
    val thresholdValueUg: Double?,
    val thresholdFrequency: ThresholdAlertFrequency,
    val thresholdEnabled: Boolean
) {
    companion object {
        fun fromUiState(uiState: NotificationSettingsUiState): NotificationSettingsSnapshot {
            return NotificationSettingsSnapshot(
                scheduledNotifications = uiState.scheduledNotifications.map { it.copy() },
                thresholdValueUg = uiState.thresholdValueUg,
                thresholdFrequency = uiState.thresholdFrequency,
                thresholdEnabled = uiState.thresholdEnabled
            )
        }
    }
}

@Composable
private fun NotificationTimeItem(
    model: ScheduledNotificationUiModel,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = model.time,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Normal
            )
            val sortedDays = remember(model.days) { model.days.sortedBy { it.sortOrder } }
            val daySet = sortedDays.toSet()
            val context = LocalContext.current
            val dayLabel = when {
                daySet.isEmpty() -> stringResource(R.string.notifications_select_days_label)
                daySet.size == WEEKDAY_ALL_SET.size -> stringResource(R.string.notifications_everyday)
                daySet == WEEKDAY_WEEKDAYS_SET -> stringResource(R.string.notifications_weekdays)
                daySet == WEEKDAY_WEEKENDS_SET -> stringResource(R.string.notifications_weekends)
                else -> sortedDays.joinToString(separator = ", ") { day ->
                    context.getString(dayAbbreviationRes(day))
                }
            }
            Text(
                text = dayLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }

        Switch(
            checked = model.isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF007AFF)
            )
        )
    }
}

@Composable
private fun AlertFrequencySection(
    selectedFrequency: ThresholdAlertFrequency,
    onFrequencySelected: (ThresholdAlertFrequency) -> Unit,
    triggerValue: Double,
    triggerUnit: String
) {
    Text(
        text = stringResource(R.string.settings_threshold_frequency_label),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = Color.Black
    )

    Spacer(modifier = Modifier.height(16.dp))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ThresholdAlertFrequency.values().forEach { frequency ->
            AlertFrequencyOption(
                frequency = frequency,
                isSelected = frequency == selectedFrequency,
                onSelect = { onFrequencySelected(frequency) }
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text(
        text = stringResource(
            R.string.alert_triggers_when_format,
            formatThresholdValue(triggerValue),
            triggerUnit
        ),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color.Black
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = stringResource(alertFrequencyDescriptionRes(selectedFrequency)),
        style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic,
        color = Color.Gray
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertFrequencyOption(
    frequency: ThresholdAlertFrequency,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val containerColor = if (isSelected) Color(0xFF007AFF) else Color(0xFFF5F5F5)
    val contentColor = if (isSelected) Color.White else Color(0xFF1C1C1E)

    Card(
        onClick = onSelect,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(alertFrequencyLabelRes(frequency)),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(28.dp))
            }
        }
    }
}

@Composable
private fun DeleteScheduledTimeDialog(
    schedule: ScheduledNotificationUiModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.button_delete_time),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.notifications_delete_dialog_message, schedule.time),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = Color(0xFFFF3B30),
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationDismissBackground(
    dismissState: SwipeToDismissBoxState
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
            Color(0xFFFF3B30)
        } else {
            Color(0xFFE0E0E0)
        },
        label = "dismissBackgroundColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = stringResource(R.string.button_delete_time),
            tint = Color.White
        )
    }
}

private fun formatThresholdValue(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}

private fun buildThresholdCategories(): List<ThresholdCategory> {
    return AQIColorPalette.US_EPA_RANGES
        .filter { it.category != "Good" }
        .map { range ->
            ThresholdCategory(
                key = range.category,
                labelRes = aqiCategoryLabelRes(range.category),
                alertThreshold = range.min,
                color = Color(range.color)
            )
        }
}

private fun isApproximately(a: Double, b: Double, epsilon: Double = 0.05): Boolean {
    return kotlin.math.abs(a - b) < epsilon
}

private val WEEKDAY_WEEKDAYS_SET = setOf(
    NotificationWeekday.MONDAY,
    NotificationWeekday.TUESDAY,
    NotificationWeekday.WEDNESDAY,
    NotificationWeekday.THURSDAY,
    NotificationWeekday.FRIDAY
)

private val WEEKDAY_WEEKENDS_SET = setOf(
    NotificationWeekday.SATURDAY,
    NotificationWeekday.SUNDAY
)

private val WEEKDAY_ALL_SET = NotificationWeekday.values().toSet()

@StringRes
private fun dayAbbreviationRes(day: NotificationWeekday): Int = when (day) {
    NotificationWeekday.MONDAY -> R.string.notifications_weekday_mon
    NotificationWeekday.TUESDAY -> R.string.notifications_weekday_tue
    NotificationWeekday.WEDNESDAY -> R.string.notifications_weekday_wed
    NotificationWeekday.THURSDAY -> R.string.notifications_weekday_thu
    NotificationWeekday.FRIDAY -> R.string.notifications_weekday_fri
    NotificationWeekday.SATURDAY -> R.string.notifications_weekday_sat
    NotificationWeekday.SUNDAY -> R.string.notifications_weekday_sun
}

@StringRes
private fun alertFrequencyLabelRes(frequency: ThresholdAlertFrequency): Int = when (frequency) {
    ThresholdAlertFrequency.ONLY_ONCE -> R.string.threshold_frequency_once
    ThresholdAlertFrequency.HOURLY -> R.string.threshold_frequency_hourly
    ThresholdAlertFrequency.SIX_HOURLY -> R.string.threshold_frequency_six_hourly
    ThresholdAlertFrequency.DAILY -> R.string.threshold_frequency_daily
}

@StringRes
private fun alertFrequencyDescriptionRes(frequency: ThresholdAlertFrequency): Int = when (frequency) {
    ThresholdAlertFrequency.ONLY_ONCE -> R.string.alert_frequency_only_once_desc
    ThresholdAlertFrequency.HOURLY -> R.string.alert_frequency_hourly_desc
    ThresholdAlertFrequency.SIX_HOURLY -> R.string.alert_frequency_six_hourly_desc
    ThresholdAlertFrequency.DAILY -> R.string.alert_frequency_daily_desc
}

@Composable
private fun ThresholdCategoryList(
    categories: List<ThresholdCategory>,
    currentCategoryKey: String,
    onCategorySelect: (ThresholdCategory) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { category ->
            ThresholdCategoryItem(
                category = category,
                isSelected = category.key == currentCategoryKey,
                onClick = { onCategorySelect(category) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThresholdCategoryItem(
    category: ThresholdCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) category.color.copy(alpha = 0.9f) else Color(0xFFF5F5F5)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(category.labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color.White else Color.Black
                )
                Text(
                    text = stringResource(
                        R.string.alert_triggered_at_format,
                        formatThresholdValue(category.alertThreshold),
                        stringResource(R.string.unit_ugm3)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color.Gray
                )
            }
            
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.notifications_cd_selected),
                        tint = category.color,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTimeBottomSheet(
    onDismiss: () -> Unit,
    onSave: (String, Set<NotificationWeekday>) -> Unit,
    initialTime: String? = null,
    initialDays: Set<NotificationWeekday>? = null,
    @StringRes titleRes: Int = R.string.notifications_add_time
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val defaultTime = remember(initialTime) { parseHourMinute(initialTime) ?: (9 to 0) }
    var selectedHour by remember(initialTime) { mutableStateOf(defaultTime.first) }
    var selectedMinute by remember(initialTime) { mutableStateOf(sanitizeMinute(defaultTime.second)) }
    var selectedDays by remember(initialDays) {
        mutableStateOf(initialDays?.takeIf { it.isNotEmpty() } ?: WEEKDAY_WEEKDAYS_SET)
    }
    LaunchedEffect(Unit) {
        modalBottomSheetState.expand()
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        containerColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color.Gray.copy(alpha = 0.3f))
            )
        }
    ) {
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val effectiveBottomPadding = if (bottomPadding < 16.dp) 16.dp else bottomPadding
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(AgBottomSheetDefaults.MAX_EXPANDED_HEIGHT_FRACTION)
                .padding(horizontal = 16.dp)
                .padding(bottom = effectiveBottomPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel), color = Color(0xFF007AFF))
                }

                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
                
                Spacer(modifier = Modifier.width(60.dp)) // Balance the cancel button
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Time Picker Section
            Text(
                text = stringResource(R.string.notifications_time_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Time Picker Display
            TimePickerDisplay(
                hour = selectedHour,
                minute = selectedMinute,
                onHourChange = { selectedHour = it },
                onMinuteChange = { selectedMinute = it }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Select Days Section
            Text(
                text = stringResource(R.string.notifications_select_days_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Days of week selection
            DaySelector(
                selectedDays = selectedDays,
                onDaysChange = { selectedDays = it }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Save Button
            Button(
                onClick = {
                    val timeString = String.format("%02d:%02d", selectedHour, selectedMinute)
                    onSave(timeString, selectedDays)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                enabled = selectedDays.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF007AFF)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    stringResource(R.string.action_save),
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun parseHourMinute(time: String?): Pair<Int, Int>? {
    if (time.isNullOrBlank()) return null
    val parts = time.split(":")
    if (parts.size < 2) return null
    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: return null
    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: return null
    return hour to minute
}

private fun sanitizeMinute(minute: Int): Int {
    val bounded = minute.coerceIn(0, 59)
    val rounded = ((bounded + 2) / 5) * 5
    return if (rounded >= 60) 55 else rounded
}

@Composable
private fun TimePickerDisplay(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    val minuteValues = remember { Array(12) { index -> String.format("%02d", index * 5) } }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = 0
                    maxValue = 23
                    wrapSelectorWheel = true
                    descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                    setTextColorCompat(AndroidColor.BLACK)
                    setFormatter { value -> String.format("%02d", value) }
                    setOnValueChangedListener { _, _, newVal -> onHourChange(newVal) }
                }
            },
            update = { picker ->
                if (picker.value != hour) {
                    picker.value = hour
                }
            }
        )

        Text(
            text = ":",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
            color = Color.Black
        )

        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = 0
                    maxValue = minuteValues.lastIndex
                    displayedValues = minuteValues
                    wrapSelectorWheel = true
                    descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                    setTextColorCompat(AndroidColor.BLACK)
                    setOnValueChangedListener { _, _, newVal -> onMinuteChange(newVal * 5) }
                }
            },
            update = { picker ->
                val target = minute.coerceIn(0, 55) / 5
                if (picker.value != target) {
                    picker.value = target
                }
            }
        )
    }
}

private fun NumberPicker.setTextColorCompat(color: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        setTextColor(color)
    } else {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child is TextView) {
                child.setTextColor(color)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaySelector(
    selectedDays: Set<NotificationWeekday>,
    onDaysChange: (Set<NotificationWeekday>) -> Unit
) {
    val orderedDays = NotificationWeekday.values()

    Column {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            orderedDays.forEach { day ->
                DayButton(
                    day = day,
                    isSelected = selectedDays.contains(day),
                    onClick = {
                        if (selectedDays.contains(day)) {
                            onDaysChange(selectedDays - day)
                        } else {
                            onDaysChange(selectedDays + day)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickSelectButton(
                labelRes = R.string.notifications_weekdays,
                modifier = Modifier.weight(1f),
                onClick = { onDaysChange(WEEKDAY_WEEKDAYS_SET) }
            )
            QuickSelectButton(
                labelRes = R.string.notifications_weekends,
                modifier = Modifier.weight(1f),
                onClick = { onDaysChange(WEEKDAY_WEEKENDS_SET) }
            )
            QuickSelectButton(
                labelRes = R.string.notifications_everyday,
                modifier = Modifier.weight(1f),
                onClick = { onDaysChange(WEEKDAY_ALL_SET) }
            )
        }
    }
}

@Composable
private fun QuickSelectButton(
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color(0xFF007AFF)
        ),
        border = BorderStroke(width = 1.dp, color = Color(0xFFB3D7FF)),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DayButton(
    day: NotificationWeekday,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) Color(0xFF007AFF) else Color.White,
            contentColor = if (isSelected) Color.White else Color(0xFF1C1C1E)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) Color(0xFF007AFF) else Color(0xFFE0E0E0)
        ),
        modifier = Modifier.size(44.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = stringResource(dayAbbreviationRes(day)),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (isSelected) Color.White else Color(0xFF1C1C1E),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

data class ThresholdCategory(
    val key: String,
    @StringRes val labelRes: Int,
    val alertThreshold: Double,
    val color: Color
)
