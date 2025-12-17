package com.airgradient.android.ui.provisioning.wifible.Views

import android.Manifest
import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airgradient.android.R
import com.airgradient.android.domain.models.provisioning.ProvisioningStatusCode
import com.airgradient.android.domain.models.provisioning.WifiNetwork
import com.airgradient.android.ui.provisioning.wifible.ViewModels.WifiBleProvisioningUiState
import com.airgradient.android.ui.provisioning.wifible.ViewModels.WifiBleProvisioningViewModel
import com.airgradient.android.ui.provisioning.wifible.ViewModels.WizardStep
import com.airgradient.android.ui.shared.Views.AgTopBar
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun WifiBleProvisioningScreen(
    onClose: () -> Unit,
    onNavigateToMyMonitors: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WifiBleProvisioningViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        onDispose { viewModel.onDispose() }
    }
    val bluetoothPermissions = rememberMultiplePermissionsState(
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    )
    var pendingBluetoothAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(bluetoothPermissions.allPermissionsGranted) {
        if (bluetoothPermissions.allPermissionsGranted) {
            pendingBluetoothAction?.invoke()
            pendingBluetoothAction = null
        }
    }
    val runWithBluetoothPermission: (() -> Unit) -> Unit = { action ->
        if (bluetoothPermissions.allPermissionsGranted) {
            action()
        } else {
            pendingBluetoothAction = action
            bluetoothPermissions.launchMultiplePermissionRequest()
        }
    }
    val canGoBack = uiState.step == WizardStep.SCAN || uiState.step == WizardStep.CREDENTIALS
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AgTopBar(
                title = stringResourceSafe(R.string.settings_wifi_ble_title),
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = viewModel::navigateBackStep) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResourceSafe(R.string.cd_back_button)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.closeSession()
                        onClose()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResourceSafe(R.string.action_cancel)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StepIndicator(step = uiState.step)
            }
            item {
                DeviceStatusBanner(uiState)
            }
            item {
                when (uiState.step) {
                    WizardStep.PREPARE -> PrepareStep(onNext = viewModel::advanceFromPrepare)
                    WizardStep.SCAN -> ScanStep(
                        uiState = uiState,
                        onScan = { runWithBluetoothPermission { viewModel.beginNetworkScan() } },
                        onSkip = { runWithBluetoothPermission { viewModel.skipNetworkScan() } }
                    )
                    WizardStep.CREDENTIALS -> CredentialsStep(
                        uiState = uiState,
                        onSelectNetwork = viewModel::selectNetwork,
                        onSsidChanged = viewModel::updateSsid,
                        onPasswordChanged = viewModel::updatePassword,
                        onTogglePassword = viewModel::togglePasswordVisibility,
                        onRescan = { runWithBluetoothPermission { viewModel.refreshNetworks() } },
                        onSubmit = { runWithBluetoothPermission { viewModel.attemptProvision() } }
                    )
                    WizardStep.RESULT -> ResultStep(
                        uiState = uiState,
                        onRetry = viewModel::retryFromResult,
                        onRegister = viewModel::submitRegistration,
                        onGoToMonitors = onNavigateToMyMonitors,
                        onSelectPlace = viewModel::selectRegistrationPlace,
                        onUpdateModel = viewModel::updateRegistrationModel,
                        onUpdateSerial = viewModel::updateRegistrationSerial,
                        onUpdateLocation = viewModel::updateRegistrationLocationName
                    )
                }
            }
        }
    }

    if (uiState.showFailureAlert) {
        AlertDialog(
            onDismissRequest = viewModel::clearFailureAlert,
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearFailureAlert()
                    viewModel.resetToCredentials()
                }) {
                    Text(text = stringResourceSafe(R.string.settings_wifi_ble_wizard_alert_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::clearFailureAlert) {
                    Text(text = stringResourceSafe(R.string.action_cancel))
                }
            },
            title = { Text(text = stringResourceSafe(R.string.settings_wifi_ble_wizard_alert_title)) },
            text = {
                Text(
                    text = uiState.failureMessage ?: uiState.failureMessageRes?.let { stringResourceSafe(it) }
                    ?: stringResourceSafe(R.string.settings_wifi_ble_wizard_alert_title)
                )
            }
        )
    }
}

@Composable
private fun StepIndicator(step: WizardStep) {
    val current = when (step) {
        WizardStep.PREPARE -> 1
        WizardStep.SCAN -> 2
        WizardStep.CREDENTIALS -> 3
        WizardStep.RESULT -> 4
    }
    Text(
        text = stringResourceSafe(R.string.settings_wifi_ble_wizard_step_indicator, current, 4),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DeviceStatusBanner(uiState: WifiBleProvisioningUiState) {
    val resId = uiState.deviceStatusTextRes ?: return
    val status = uiState.status
    val text = if (status?.code == ProvisioningStatusCode.UNKNOWN) {
        status.rawCode.let { code -> stringResourceSafe(resId, code) }
    } else {
        stringResourceSafe(resId)
    }
    if (text.isBlank()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .height(4.dp)
                    .weight(1f)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PrepareStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResourceSafe(R.string.settings_wifi_ble_wizard_prepare_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        InstructionRow(text = stringResourceSafe(R.string.settings_wifi_ble_wizard_prepare_power_on))
        InstructionRow(text = stringResourceSafe(R.string.settings_wifi_ble_wizard_prepare_password))
        InstructionRow(text = stringResourceSafe(R.string.settings_wifi_ble_wizard_prepare_24ghz))
        InstructionRow(text = stringResourceSafe(R.string.settings_wifi_ble_wizard_prepare_indicator))
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResourceSafe(R.string.settings_wifi_ble_wizard_next))
        }
    }
}

@Composable
private fun ScanStep(
    uiState: WifiBleProvisioningUiState,
    onScan: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResourceSafe(R.string.settings_wifi_ble_scan_step_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResourceSafe(R.string.settings_wifi_ble_scan_step_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (uiState.isScanningNetworks || uiState.isScanningDevices) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LinearProgressIndicator(modifier = Modifier.weight(1f))
                Text(text = stringResourceSafe(R.string.settings_wifi_ble_scanning_in_progress))
            }
        }
        Button(
            onClick = onScan,
            enabled = !uiState.isScanningDevices && !uiState.isScanningNetworks,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResourceSafe(R.string.settings_wifi_ble_scan_networks_button))
        }
        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResourceSafe(R.string.settings_wifi_ble_skip_scan_button))
        }
    }
}

@Composable
private fun CredentialsStep(
    uiState: WifiBleProvisioningUiState,
    onSelectNetwork: (WifiNetwork) -> Unit,
    onSsidChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onRescan: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResourceSafe(R.string.settings_wifi_ble_credentials_step_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResourceSafe(
                if (uiState.scanSkipped) R.string.settings_wifi_ble_credentials_step_description_manual
                else R.string.settings_wifi_ble_credentials_step_description
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!uiState.scanSkipped) {
            NetworkList(
                networks = uiState.availableNetworks,
                isScanning = uiState.isScanningNetworks,
                selected = uiState.selectedNetwork,
                onSelect = onSelectNetwork
            )
            OutlinedButton(
                onClick = onRescan,
                enabled = !uiState.isScanningNetworks,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResourceSafe(R.string.settings_wifi_ble_networks_rescan_button))
            }
        }

        Text(
            text = stringResourceSafe(R.string.settings_wifi_ble_credentials_pairing_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = uiState.ssid,
            onValueChange = onSsidChanged,
            label = { Text(text = "SSID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChanged,
            enabled = uiState.passwordRequired,
            label = { Text(text = stringResourceSafe(R.string.auth_password_label)) },
            trailingIcon = {
                IconButton(onClick = onTogglePassword, enabled = uiState.passwordRequired) {
                    Icon(
                        imageVector = if (uiState.passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            },
            visualTransformation = if (uiState.passwordVisible || !uiState.passwordRequired) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = {
                if (!uiState.passwordRequired) {
                    Text(text = stringResourceSafe(R.string.settings_wifi_ble_password_not_required))
                }
            }
        )
        Button(
            onClick = onSubmit,
            enabled = uiState.ssid.trim().isNotEmpty() && !uiState.isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isProcessing) {
                LinearProgressIndicator(modifier = Modifier.weight(1f))
            } else {
                Text(text = stringResourceSafe(R.string.settings_wifi_ble_connect_button))
            }
        }
    }
}

@Composable
private fun NetworkList(
    networks: List<WifiNetwork>,
    isScanning: Boolean,
    selected: WifiNetwork?,
    onSelect: (WifiNetwork) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResourceSafe(R.string.settings_wifi_ble_networks_header),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (isScanning) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(modifier = Modifier.weight(1f))
                    Text(text = stringResourceSafe(R.string.settings_wifi_ble_scanning_in_progress))
                }
            } else if (networks.isEmpty()) {
                Text(
                    text = stringResourceSafe(R.string.settings_wifi_ble_networks_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    networks.forEach { network ->
                        NetworkRow(
                            network = network,
                            selected = selected?.ssid == network.ssid && selected.isOpen == network.isOpen,
                            onClick = { onSelect(network) }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkRow(
    network: WifiNetwork,
    selected: Boolean,
    onClick: () -> Unit
) {
    val label = networkLabel(network.rssi)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (network.ssid.isNotBlank()) network.ssid else stringResourceSafe(R.string.settings_wifi_ble_network_hidden),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            label?.let {
                Text(
                    text = stringResourceSafe(R.string.settings_wifi_ble_signal_label, it.first, it.second ?: 0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (network.isOpen) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = stringResourceSafe(R.string.settings_wifi_ble_network_open_accessibility)
                )
            }
            Icon(
                imageVector = if (network.rssi != null) Icons.Default.SignalWifi4Bar else Icons.Default.SignalWifiOff,
                contentDescription = null
            )
            Text(
                text = network.rssi?.let { stringResourceSafe(R.string.settings_wifi_ble_network_rssi, it) } ?: "--",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ResultStep(
    uiState: WifiBleProvisioningUiState,
    onRetry: () -> Unit,
    onRegister: () -> Unit,
    onGoToMonitors: () -> Unit,
    onSelectPlace: (Int) -> Unit,
    onUpdateModel: (String) -> Unit,
    onUpdateSerial: (String) -> Unit,
    onUpdateLocation: (String) -> Unit
) {
    val status = uiState.resultStatus
    val statusCode = status?.code
    val title = when (statusCode) {
        ProvisioningStatusCode.CONFIGURED -> R.string.settings_wifi_ble_wizard_result_success_title
        ProvisioningStatusCode.CONFIG_PENDING -> R.string.settings_wifi_ble_wizard_result_config_title
        ProvisioningStatusCode.NOT_REGISTERED -> R.string.settings_wifi_ble_wizard_result_registration_title
        ProvisioningStatusCode.WIFI_FAILED,
        ProvisioningStatusCode.SERVER_UNREACHABLE -> R.string.settings_wifi_ble_wizard_result_failure_title
        else -> R.string.settings_wifi_ble_wizard_result_success_title
    }
    val messageRes = when (statusCode) {
        ProvisioningStatusCode.CONFIGURED -> R.string.settings_wifi_ble_wizard_result_success_message
        ProvisioningStatusCode.CONFIG_PENDING -> R.string.settings_wifi_ble_wizard_result_config_message
        ProvisioningStatusCode.NOT_REGISTERED -> R.string.settings_wifi_ble_wizard_result_registration_message
        ProvisioningStatusCode.WIFI_FAILED,
        ProvisioningStatusCode.SERVER_UNREACHABLE -> R.string.settings_wifi_ble_error_connect_failed
        else -> R.string.settings_wifi_ble_wizard_result_success_message
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResourceSafe(title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResourceSafe(messageRes),
                    style = MaterialTheme.typography.bodyMedium
                )
                uiState.deviceInfo?.let { info ->
                    info.manufacturer?.takeIf { it.isNotBlank() }?.let {
                        Text(text = stringResourceSafe(R.string.settings_wifi_ble_registration_manufacturer_text, it))
                    }
                    info.model?.takeIf { it.isNotBlank() }?.let {
                        Text(text = stringResourceSafe(R.string.settings_wifi_ble_registration_model_text, it))
                    }
                    info.serial?.takeIf { it.isNotBlank() }?.let {
                        Text(text = stringResourceSafe(R.string.settings_wifi_ble_registration_serial_text, it))
                    }
                    info.firmware?.takeIf { it.isNotBlank() }?.let {
                        Text(text = stringResourceSafe(R.string.settings_wifi_ble_registration_firmware_text, it))
                    }
                }
            }
        }

        when (statusCode) {
            ProvisioningStatusCode.WIFI_FAILED,
            ProvisioningStatusCode.SERVER_UNREACHABLE -> {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResourceSafe(R.string.settings_wifi_ble_wizard_try_again))
                }
                OutlinedButton(
                    onClick = onGoToMonitors,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResourceSafe(R.string.settings_wifi_ble_wizard_exit_provisioning))
                }
            }
            ProvisioningStatusCode.NOT_REGISTERED -> {
                RegistrationCard(
                    uiState = uiState,
                    onRegister = onRegister,
                    onSelectPlace = onSelectPlace,
                    onUpdateModel = onUpdateModel,
                    onUpdateSerial = onUpdateSerial,
                    onUpdateLocation = onUpdateLocation
                )
            }
            else -> {
                Button(
                    onClick = onGoToMonitors,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResourceSafe(R.string.settings_wifi_ble_go_to_monitors_button))
                }
            }
        }
    }
}

@Composable
private fun RegistrationCard(
    uiState: WifiBleProvisioningUiState,
    onRegister: () -> Unit,
    onSelectPlace: (Int) -> Unit,
    onUpdateModel: (String) -> Unit,
    onUpdateSerial: (String) -> Unit,
    onUpdateLocation: (String) -> Unit
) {
    val reg = uiState.registration
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResourceSafe(R.string.settings_wifi_ble_registration_form_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (reg.requiresAuth) {
                Text(
                    text = stringResourceSafe(R.string.settings_wifi_ble_go_to_monitors_login_hint),
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (reg.places.isNotEmpty()) {
                Text(
                    text = stringResourceSafe(R.string.settings_wifi_ble_registration_location_label),
                    style = MaterialTheme.typography.bodySmall
                )
                reg.places.forEach { place ->
                    val selected = reg.selectedPlaceId == place.id
                    Text(
                        text = place.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPlace(place.id) }
                            .background(
                                if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    )
                }
            }
            OutlinedTextField(
                value = reg.model,
                onValueChange = onUpdateModel,
                label = { Text(text = stringResourceSafe(R.string.settings_wifi_ble_registration_model_label)) },
                enabled = reg.model.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = reg.serial,
                onValueChange = onUpdateSerial,
                label = { Text(text = stringResourceSafe(R.string.settings_wifi_ble_registration_serial_label)) },
                enabled = reg.serial.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = reg.locationName,
                onValueChange = onUpdateLocation,
                label = { Text(text = stringResourceSafe(R.string.settings_wifi_ble_registration_location_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            reg.errorMessageRes?.let { Text(text = stringResourceSafe(it), color = MaterialTheme.colorScheme.error) }
            reg.errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
            reg.resultMessageRes?.let { Text(text = stringResourceSafe(it)) }
            reg.resultMessage?.let { Text(text = it) }
            Button(
                onClick = onRegister,
                enabled = !reg.isSubmitting && !reg.requiresAuth,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResourceSafe(R.string.settings_wifi_ble_registration_submit))
            }
        }
    }
}

@Composable
private fun InstructionRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "•", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun stringResourceSafe(@androidx.annotation.StringRes id: Int, vararg args: Any): String {
    return androidx.compose.ui.res.stringResource(id = id, *args)
}

@Composable
private fun networkLabel(rssi: Int?): Pair<String, Int?>? {
    rssi ?: return null
    val labelRes = when {
        rssi >= -55 -> R.string.settings_wifi_ble_signal_level_excellent
        rssi >= -65 -> R.string.settings_wifi_ble_signal_level_good
        rssi >= -75 -> R.string.settings_wifi_ble_signal_level_fair
        else -> R.string.settings_wifi_ble_signal_level_weak
    }
    return stringResourceSafe(labelRes) to rssi
}
