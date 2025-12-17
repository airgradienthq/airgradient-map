package com.airgradient.android.ui.settings.Views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.airgradient.android.FeatureFlags
import com.airgradient.android.R
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.auth.AuthState
import com.airgradient.android.ui.location.Views.NotificationsBottomSheet
import com.airgradient.android.ui.shared.Views.AgTopBar
import com.airgradient.android.ui.shared.Views.AirgradientOutlinedCard
import com.airgradient.android.ui.shared.Views.LabeledSwitchRow
import com.airgradient.android.ui.settings.ViewModels.SettingsViewModel
import com.airgradient.android.ui.settings.ViewModels.ProfileFlag
import com.airgradient.android.ui.settings.ViewModels.NotificationLocationItem
import com.airgradient.android.ui.settings.ViewModels.SettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onRequestSignIn: () -> Unit = {},
    onOpenBleProvisioning: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDisplayUnitDropdown by remember { mutableStateOf(false) }
    var showWidgetLocationDropdown by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    var selectedNotificationLocation by remember { mutableStateOf<NotificationLocationItem?>(null) }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AgTopBar(
                title = stringResource(id = R.string.nav_settings),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_to_locations)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DisplayUnitSection(
                uiState = uiState,
                showDropdown = showDisplayUnitDropdown,
                onToggleDropdown = { showDisplayUnitDropdown = it },
                onUpdateUnit = viewModel::updateDisplayUnit
            )

            WidgetSection(
                uiState = uiState,
                showDropdown = showWidgetLocationDropdown,
                onToggleDropdown = { showWidgetLocationDropdown = it },
                onUpdateWidgetLocation = viewModel::updateWidgetLocation
            )

            NotificationsSection(
                notificationItems = uiState.notificationLocations,
                isLoading = uiState.notificationsLoading,
                errorMessage = uiState.notificationsError,
                onItemClick = { selectedNotificationLocation = it }
            )

            if (FeatureFlags.WIFI_PROVISIONING_VIA_BLE_ENABLED) {
                BleProvisioningSection(onOpen = onOpenBleProvisioning)
            }

            ProfileSection(
                uiState = uiState,
                onUpdateProfile = viewModel::updateProfileFlag
            )
            AccountSection(
                authState = uiState.authState,
                onRequestSignIn = onRequestSignIn,
                onSignOut = viewModel::signOut
            )
            LicenseSection()
        }
    }

    selectedNotificationLocation?.let { activeLocation ->
        NotificationsBottomSheet(
            onDismiss = { changed ->
                selectedNotificationLocation = null
                if (changed) {
                    viewModel.refreshNotificationLocations()
                }
            },
            locationId = activeLocation.locationId,
            locationName = activeLocation.displayName
        )
    }
}

@Composable
private fun displayUnitLabel(unit: AQIDisplayUnit): String {
    return when (unit) {
        AQIDisplayUnit.UGM3 -> stringResource(R.string.settings_display_unit_ugm3)
        AQIDisplayUnit.USAQI -> stringResource(R.string.settings_display_unit_us_aqi)
    }
}

@Composable
private fun AccountSection(
    authState: AuthState,
    onRequestSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    AirgradientOutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        cardModifier = Modifier.fillMaxWidth(),
        header = {
            Text(
                text = stringResource(R.string.settings_section_account),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (authState) {
                is AuthState.Authenticated -> {
                    val rawName = authState.user.name?.trim().orEmpty()
                    val email = authState.user.email
                    val displayName = if (rawName.isNotEmpty()) rawName else email
                    Text(
                        text = stringResource(R.string.settings_account_signed_in_as, displayName),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (rawName.isNotEmpty()) {
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onSignOut,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(text = stringResource(R.string.auth_sign_out))
                    }
                }
                AuthState.SignedOut -> {
                    Text(
                        text = stringResource(R.string.settings_account_signed_out_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onRequestSignIn,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(text = stringResource(R.string.auth_prompt_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun BleProvisioningSection(onOpen: () -> Unit) {
    AirgradientOutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        cardModifier = Modifier.fillMaxWidth(),
        header = {
            Text(
                text = stringResource(R.string.settings_wifi_ble_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_wifi_ble_scan_step_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.settings_wifi_ble_button))
            }
        }
    }
}

@Composable
private fun DisplayUnitSection(
    uiState: SettingsUiState,
    showDropdown: Boolean,
    onToggleDropdown: (Boolean) -> Unit,
    onUpdateUnit: (AQIDisplayUnit) -> Unit
) {
    val displayUnitOptions = remember { listOf(AQIDisplayUnit.USAQI, AQIDisplayUnit.UGM3) }
    val selectedDisplayUnitLabel = displayUnitLabel(uiState.displayUnit)

    AirgradientOutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        cardModifier = Modifier.fillMaxWidth(),
        header = {
            Text(
                text = stringResource(R.string.settings_section_air_quality_display),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_display_unit_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_display_unit_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box {
                SelectorRow(
                    value = selectedDisplayUnitLabel,
                    onClick = { onToggleDropdown(true) }
                )

                if (showDropdown) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { onToggleDropdown(false) },
                        modifier = Modifier
                            .fillMaxWidth(0.8f),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        displayUnitOptions.forEach { unit ->
                            val optionLabel = displayUnitLabel(unit)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = optionLabel,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    onUpdateUnit(unit)
                                    onToggleDropdown(false)
                                },
                                trailingIcon = {
                                    if (unit == uiState.displayUnit) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetSection(
    uiState: SettingsUiState,
    showDropdown: Boolean,
    onToggleDropdown: (Boolean) -> Unit,
    onUpdateWidgetLocation: (String) -> Unit
) {
    val noneDisplayLabel = stringResource(R.string.settings_widget_location_none)
    val widgetValueDisplay = if (uiState.widgetLocation == "None") noneDisplayLabel else uiState.widgetLocation

    AirgradientOutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        cardModifier = Modifier.fillMaxWidth(),
        header = {
            Text(
                text = stringResource(R.string.settings_section_widget),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_widget_location_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_widget_location_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box {
                SelectorRow(
                    value = widgetValueDisplay,
                    onClick = { onToggleDropdown(true) }
                )

                if (showDropdown) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { onToggleDropdown(false) },
                        modifier = Modifier
                            .fillMaxWidth(0.9f),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = noneDisplayLabel,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                onUpdateWidgetLocation("None")
                                onToggleDropdown(false)
                            },
                            trailingIcon = {
                                if (uiState.widgetLocation == "None") {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )

                        uiState.availableLocations.forEach { location ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = location,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2
                                    )
                                },
                                onClick = {
                                    onUpdateWidgetLocation(location)
                                    onToggleDropdown(false)
                                },
                                trailingIcon = {
                                    if (location == uiState.widgetLocation) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsSection(
    notificationItems: List<NotificationLocationItem>,
    isLoading: Boolean,
    errorMessage: String?,
    onItemClick: (NotificationLocationItem) -> Unit
) {
    AirgradientOutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        cardModifier = Modifier.fillMaxWidth(),
        header = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_section_notifications),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (notificationItems.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.settings_section_notifications_count,
                            notificationItems.size
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            when {
                isLoading -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                errorMessage != null -> {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                notificationItems.isEmpty() -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.settings_notifications_empty_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(id = R.string.settings_notifications_empty_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    var showAll by rememberSaveable(notificationItems) { mutableStateOf(false) }
                    val visibleItems = if (!showAll && notificationItems.size > 2) {
                        notificationItems.take(2)
                    } else {
                        notificationItems
                    }

                    visibleItems.forEach { location ->
                        NotificationLocationCard(
                            item = location,
                            onClick = { onItemClick(location) }
                        )
                    }

                    if (notificationItems.size > 2) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { showAll = !showAll }) {
                                Text(
                                    text = stringResource(
                                        if (showAll) R.string.settings_notifications_show_less
                                        else R.string.settings_notifications_show_more
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.settings_notifications_helper_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSection(
    uiState: SettingsUiState,
    onUpdateProfile: (ProfileFlag, Boolean) -> Unit
) {
    AirgradientOutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        cardModifier = Modifier.fillMaxWidth(),
        header = {
            Text(
                text = stringResource(R.string.settings_section_profile),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            ProfileToggleRow(
                title = stringResource(id = R.string.profile_flag_vulnerable_groups),
                checked = uiState.userProfile.hasVulnerableGroups,
                onCheckedChange = { onUpdateProfile(ProfileFlag.VULNERABLE_GROUPS, it) }
            )

            ProfileToggleRow(
                title = stringResource(id = R.string.profile_flag_preexisting_conditions),
                checked = uiState.userProfile.hasPreexistingConditions,
                onCheckedChange = { onUpdateProfile(ProfileFlag.PREEXISTING_CONDITIONS, it) }
            )

            ProfileToggleRow(
                title = stringResource(id = R.string.profile_flag_exercises_outdoors),
                checked = uiState.userProfile.exercisesOutdoors,
                onCheckedChange = { onUpdateProfile(ProfileFlag.EXERCISES_OUTDOORS, it) }
            )

            ProfileToggleRow(
                title = stringResource(id = R.string.profile_flag_owns_equipment),
                checked = uiState.userProfile.ownsProtectiveEquipment,
                onCheckedChange = { onUpdateProfile(ProfileFlag.OWNS_EQUIPMENT, it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(id = R.string.profile_flags_section_footer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 17.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        if (subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun NotificationLocationCard(
    item: NotificationLocationItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AirgradientOutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.scheduledCount > 0) {
                    NotificationStatusChip(
                        label = pluralStringResource(
                            id = R.plurals.settings_notifications_scheduled_chip,
                            count = item.scheduledCount,
                            item.scheduledCount
                        ),
                        highlight = true
                    )
                }

                if (item.hasThresholdAlert) {
                    NotificationStatusChip(
                        label = stringResource(id = R.string.settings_notifications_threshold_chip),
                        highlight = true
                    )
                }

                if (item.scheduledCount == 0 && !item.hasThresholdAlert) {
                    NotificationStatusChip(
                        label = stringResource(id = R.string.settings_notifications_inactive_chip),
                        highlight = false
                    )
                }
            }
        }
    }
}


@Composable
private fun LicenseSection() {
    val uriHandler = LocalUriHandler.current

    AirgradientOutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        cardModifier = Modifier.fillMaxWidth(),
        header = {
            Text(
                text = stringResource(R.string.settings_section_license),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val requiredAttribution = stringResource(R.string.settings_license_ui_attribution_required)
            val localizedAttribution = stringResource(R.string.settings_license_ui_attribution)

            Text(
                text = requiredAttribution,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (localizedAttribution != requiredAttribution) {
                Text(
                    text = localizedAttribution,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.settings_license_open_source_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.settings_license_community_thanks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = { uriHandler.openUri("https://www.airgradient.com/open-source-initiative/") },
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_license_learn_more),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun NotificationStatusChip(
    label: String,
    highlight: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (highlight) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SelectorRow(
    value: String,
    onClick: () -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfileToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    LabeledSwitchRow(
        checked = checked,
        onCheckedChange = onCheckedChange
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
