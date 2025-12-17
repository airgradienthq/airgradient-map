package com.airgradient.android.ui.provisioning.wifible.ViewModels

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airgradient.android.R
import com.airgradient.android.data.provisioning.BleProvisioningManager
import com.airgradient.android.data.provisioning.BleProvisioningSession
import com.airgradient.android.domain.models.monitors.MonitorsPlace
import com.airgradient.android.domain.models.provisioning.BleDeviceInfo
import com.airgradient.android.domain.models.provisioning.ProvisioningError
import com.airgradient.android.domain.models.provisioning.ProvisioningStatus
import com.airgradient.android.domain.models.provisioning.ProvisioningStatusCode
import com.airgradient.android.domain.models.provisioning.WifiNetwork
import com.airgradient.android.domain.repositories.AuthenticationRepository
import com.airgradient.android.domain.repositories.MyMonitorsRepository
import com.airgradient.android.domain.models.auth.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class WizardStep { PREPARE, SCAN, CREDENTIALS, RESULT }

private const val WIFI_SCAN_TIMEOUT_MS = 15_000L
private const val VM_TAG = "AGBleProvVM"

data class RegistrationUiState(
    val places: List<MonitorsPlace> = emptyList(),
    val selectedPlaceId: Int? = null,
    val locationName: String = "",
    val model: String = "",
    val serial: String = "",
    val isSubmitting: Boolean = false,
    val resultMessage: String? = null,
    @StringRes val resultMessageRes: Int? = null,
    val errorMessage: String? = null,
    @StringRes val errorMessageRes: Int? = null,
    val requiresAuth: Boolean = false
)

data class WifiBleProvisioningUiState(
    val step: WizardStep = WizardStep.PREPARE,
    @StringRes val statusTextRes: Int? = null,
    @StringRes val deviceStatusTextRes: Int? = null,
    val status: ProvisioningStatus? = null,
    val isBluetoothPreparing: Boolean = false,
    val isScanningDevices: Boolean = false,
    val isConnecting: Boolean = false,
    val isDiscovering: Boolean = false,
    val scanSkipped: Boolean = false,
    val isProcessing: Boolean = false,
    val isScanningNetworks: Boolean = false,
    val availableNetworks: List<WifiNetwork> = emptyList(),
    val selectedNetwork: WifiNetwork? = null,
    val ssid: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val passwordRequired: Boolean = true,
    val resultStatus: ProvisioningStatus? = null,
    val deviceInfo: BleDeviceInfo? = null,
    val showFailureAlert: Boolean = false,
    @StringRes val failureMessageRes: Int? = null,
    val failureMessage: String? = null,
    val registration: RegistrationUiState = RegistrationUiState()
)

@HiltViewModel
class WifiBleProvisioningViewModel @Inject constructor(
    private val bleManager: BleProvisioningManager,
    private val myMonitorsRepository: MyMonitorsRepository,
    private val authenticationRepository: AuthenticationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WifiBleProvisioningUiState())
    val uiState: StateFlow<WifiBleProvisioningUiState> = _uiState.asStateFlow()

    private var session: BleProvisioningSession? = null
    private var sessionJobs: List<Job> = emptyList()
    private val sessionMutex = Mutex()
    private var terminalReceived = false
    private var wifiScanTimeoutJob: Job? = null

    fun advanceFromPrepare() {
        _uiState.update { it.copy(step = WizardStep.SCAN) }
    }

    fun skipNetworkScan() {
        viewModelScope.launch {
            Log.d(VM_TAG, "skipNetworkScan: requested")
            ensureSession(requestWifiScan = false)
            _uiState.update { it.copy(step = WizardStep.CREDENTIALS, scanSkipped = true) }
        }
    }

    fun beginNetworkScan() {
        viewModelScope.launch {
            Log.d(VM_TAG, "beginNetworkScan: starting")
            _uiState.update {
                it.copy(
                    statusTextRes = R.string.settings_wifi_ble_status_scanning,
                    isBluetoothPreparing = true,
                    isScanningDevices = true,
                    showFailureAlert = false,
                    failureMessage = null,
                    failureMessageRes = null
                )
            }
            val connected = ensureSession(requestWifiScan = true)
            Log.d(VM_TAG, "beginNetworkScan: ensureSession returned session=${connected != null}")
            if (connected != null) {
                _uiState.update { state ->
                    state.copy(
                        step = WizardStep.CREDENTIALS,
                        isBluetoothPreparing = false,
                        isScanningDevices = false,
                        scanSkipped = false
                    )
                }
            }
        }
    }

    fun refreshNetworks() {
        viewModelScope.launch {
            Log.d(VM_TAG, "refreshNetworks: invoked")
            val activeSession = session ?: ensureSession(requestWifiScan = true)
            if (activeSession == null) return@launch
            _uiState.update { it.copy(isScanningNetworks = true, statusTextRes = R.string.settings_wifi_ble_status_wifi_scanning, scanSkipped = false) }
            scheduleWifiScanTimeout()
            val result = activeSession.requestWifiScan()
            Log.d(VM_TAG, "refreshNetworks: requestWifiScan result isFailure=${result.isFailure}")
            if (result.isFailure) {
                _uiState.update { state ->
                    state.copy(
                        isScanningNetworks = false,
                        statusTextRes = null
                    )
                }
            }
        }
    }

    fun selectNetwork(network: WifiNetwork) {
        val passwordRequired = !network.isOpen
        _uiState.update {
            it.copy(
                selectedNetwork = network,
                ssid = if (network.ssid.isNotBlank()) network.ssid else it.ssid,
                password = if (passwordRequired) it.password else "",
                passwordRequired = passwordRequired
            )
        }
    }

    fun updateSsid(value: String) {
        _uiState.update { it.copy(ssid = value) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun attemptProvision() {
        val state = _uiState.value
        if (state.ssid.trim().isEmpty() || state.isProcessing) {
            return
        }
        viewModelScope.launch {
            Log.d(VM_TAG, "attemptProvision: ssid='${state.ssid}' passwordRequired=${state.passwordRequired}")
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusTextRes = R.string.settings_wifi_ble_status_sending,
                    showFailureAlert = false,
                    failureMessageRes = null,
                    failureMessage = null
                )
            }
            val activeSession = session ?: ensureSession(requestWifiScan = false)
            if (activeSession == null) {
                _uiState.update { it.copy(isProcessing = false) }
                return@launch
            }
            val password = if (state.passwordRequired) state.password else ""
            val result = activeSession.sendCredentials(state.ssid, password)
            Log.d(VM_TAG, "attemptProvision: sendCredentials result isFailure=${result.isFailure}")
            if (result.isFailure) {
                handleFailure(ProvisioningError.WriteFailed, result.exceptionOrNull()?.message)
                _uiState.update { it.copy(isProcessing = false) }
                return@launch
            }
            _uiState.update { it.copy(statusTextRes = R.string.settings_wifi_ble_device_status_waiting) }
        }
    }

    fun clearFailureAlert() {
        _uiState.update { it.copy(showFailureAlert = false, failureMessage = null, failureMessageRes = null) }
    }

    fun navigateBackStep() {
        _uiState.update { state ->
            when (state.step) {
                WizardStep.SCAN -> state.copy(step = WizardStep.PREPARE)
                WizardStep.CREDENTIALS -> state.copy(step = WizardStep.SCAN)
                else -> state
            }
        }
    }

    fun closeSession() {
        sessionJobs.forEach { it.cancel() }
        sessionJobs = emptyList()
        wifiScanTimeoutJob?.cancel()
        wifiScanTimeoutJob = null
        session?.close()
        session = null
    }

    /**
     * Retry from the result screen without tearing down the BLE session.
     * This is used when provisioning failed but the device is still connected,
     * so the user can adjust credentials and try again without re-pairing.
     */
    fun retryFromResult() {
        wifiScanTimeoutJob?.cancel()
        wifiScanTimeoutJob = null
        terminalReceived = false
        _uiState.update {
            it.copy(
                step = WizardStep.CREDENTIALS,
                status = null,
                resultStatus = null,
                statusTextRes = null,
                deviceStatusTextRes = null,
                showFailureAlert = false,
                failureMessage = null,
                failureMessageRes = null,
                isProcessing = false,
                isScanningNetworks = false,
                isScanningDevices = false
            )
        }
    }

    fun resetToCredentials() {
        closeSession()
        terminalReceived = false
        _uiState.update {
            it.copy(
                step = WizardStep.CREDENTIALS,
                status = null,
                resultStatus = null,
                statusTextRes = null,
                deviceStatusTextRes = null,
                showFailureAlert = false,
                failureMessage = null,
                failureMessageRes = null,
                isProcessing = false
            )
        }
    }

    fun onDispose() {
        closeSession()
    }

    private suspend fun ensureSession(requestWifiScan: Boolean): BleProvisioningSession? {
        Log.d(VM_TAG, "ensureSession: requestWifiScan=$requestWifiScan existingSession=${session != null}")
        return sessionMutex.withLock {
            session?.let {
                if (requestWifiScan && it.characteristics.wifiScanCharacteristic != null) {
                    Log.d(VM_TAG, "ensureSession: reusing existing session for Wi-Fi scan")
                    _uiState.update { state ->
                        state.copy(
                            isScanningNetworks = true,
                            statusTextRes = R.string.settings_wifi_ble_status_wifi_scanning
                        )
                    }
                    scheduleWifiScanTimeout()
                    it.requestWifiScan()
                } else if (requestWifiScan) {
                    _uiState.update { state ->
                        state.copy(
                            isScanningNetworks = false,
                            statusTextRes = null
                        )
                    }
                }
                return@withLock it
            }
            val bluetoothReady = bleManager.ensureBluetoothReady().onFailure { error ->
                Log.e(VM_TAG, "ensureSession: Bluetooth not ready", error)
                val failure = if (error is SecurityException) ProvisioningError.PermissionsMissing else ProvisioningError.BluetoothUnavailable
                handleFailure(failure, error.message)
                return@withLock null
            }
            if (bluetoothReady.isFailure) return@withLock null

            _uiState.update { it.copy(statusTextRes = R.string.settings_wifi_ble_status_scanning, isBluetoothPreparing = false, isScanningDevices = true) }
            val device = bleManager.scanForProvisioningDevice().getOrElse { throwable ->
                val failure = if (throwable is SecurityException) ProvisioningError.PermissionsMissing else ProvisioningError.ScanTimeout
                Log.e(VM_TAG, "ensureSession: scanForProvisioningDevice failed", throwable)
                handleFailure(failure, throwable.message)
                return@withLock null
            }
            _uiState.update { it.copy(isScanningDevices = false, isConnecting = true, statusTextRes = R.string.settings_wifi_ble_status_pairing) }
            val openedSession = bleManager.openSession(device).getOrElse { throwable ->
                val message = throwable.message
                val error = when {
                    throwable is SecurityException -> ProvisioningError.PermissionsMissing
                    message?.contains("permission", ignoreCase = true) == true -> ProvisioningError.PermissionsMissing
                    message?.contains("pairing", ignoreCase = true) == true -> ProvisioningError.PermissionsMissing
                    message?.contains("bond", ignoreCase = true) == true -> ProvisioningError.PermissionsMissing
                    message?.contains("MTU", ignoreCase = true) == true -> ProvisioningError.MtuTooSmall
                    message?.contains("characteristic", ignoreCase = true) == true -> ProvisioningError.MissingCharacteristics
                    message?.contains("service", ignoreCase = true) == true -> ProvisioningError.MissingService
                    else -> ProvisioningError.Message(message ?: "Connection failed")
                }
                Log.e(VM_TAG, "ensureSession: openSession failed", throwable)
                handleFailure(error, message)
                return@withLock null
            }

            session = openedSession
            terminalReceived = false
            Log.d(VM_TAG, "ensureSession: session established, hasWifiScanChar=${openedSession.characteristics.wifiScanCharacteristic != null}")
            startSessionListeners(openedSession)
            openedSession.deviceInfo?.let { info ->
                _uiState.update { it.copy(deviceInfo = info) }
            }
            _uiState.update {
                it.copy(
                    isConnecting = false,
                    isDiscovering = false,
                    statusTextRes = R.string.settings_wifi_ble_status_characteristics
                )
            }

            openedSession.initialReads()
            if (requestWifiScan && openedSession.characteristics.wifiScanCharacteristic != null) {
                _uiState.update { it.copy(isScanningNetworks = true, statusTextRes = R.string.settings_wifi_ble_status_wifi_scanning) }
                scheduleWifiScanTimeout()
                val scanResult = openedSession.requestWifiScan()
                Log.d(VM_TAG, "ensureSession: initial requestWifiScan result isFailure=${scanResult.isFailure}")
                if (scanResult.isFailure) {
                    _uiState.update { state ->
                        state.copy(
                            isScanningNetworks = false,
                            statusTextRes = null
                        )
                    }
                }
            } else if (requestWifiScan) {
                _uiState.update { it.copy(isScanningNetworks = false, statusTextRes = null) }
            }
            openedSession
        }
    }

    private fun startSessionListeners(newSession: BleProvisioningSession) {
        sessionJobs.forEach { it.cancel() }
        sessionJobs = listOf(
            viewModelScope.launch {
                newSession.statusUpdates.collect { status ->
                    Log.d(VM_TAG, "statusUpdates: code=${status.code} raw=${status.rawCode}")
                    handleStatus(status)
                }
            },
            viewModelScope.launch {
                newSession.wifiScanResults.collect { networks ->
                    Log.d(VM_TAG, "wifiScanResults: count=${networks.size} ssids=${networks.joinToString { it.ssid }}")
                    wifiScanTimeoutJob?.cancel()
                    wifiScanTimeoutJob = null
                    _uiState.update {
                        it.copy(
                            availableNetworks = networks,
                            isScanningNetworks = false,
                            statusTextRes = null
                        )
                    }
                }
            },
            viewModelScope.launch {
                newSession.disconnections.collect {
                    Log.w(VM_TAG, "disconnections: session disconnected before terminalReceived=$terminalReceived")
                    if (!terminalReceived) {
                        handleFailure(ProvisioningError.Disconnected, null)
                    }
                }
            }
        )
    }

    private fun handleStatus(status: ProvisioningStatus) {
        val deviceStatusRes = statusLabel(status.code)
        _uiState.update {
            it.copy(
                status = status,
                deviceStatusTextRes = deviceStatusRes
            )
        }
        if (status.code.isTerminal) {
            terminalReceived = true
            _uiState.update { it.copy(resultStatus = status, step = WizardStep.RESULT, isProcessing = false, isScanningNetworks = false) }
            if (status.code.shouldDisconnect) {
                closeSession()
            }
            when (status.code) {
                ProvisioningStatusCode.NOT_REGISTERED -> prepareRegistration()
                ProvisioningStatusCode.CONFIG_PENDING -> { /* no-op */ }
                else -> { /* nothing */ }
            }
        }
    }

    private fun prepareRegistration() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentAuth = authenticationRepository.authState.value
            if (currentAuth is AuthState.SignedOut) {
                _uiState.update { state ->
                    state.copy(
                        registration = state.registration.copy(
                            requiresAuth = true
                        )
                    )
                }
                return@launch
            }
            val existing = _uiState.value.registration
            val deviceInfo = _uiState.value.deviceInfo
            val currentSerial = deviceInfo?.serial?.takeIf { it.isNotBlank() } ?: existing.serial
            val currentModel = deviceInfo?.model?.takeIf { it.isNotBlank() } ?: existing.model
            _uiState.update {
                it.copy(
                    registration = it.registration.copy(
                        serial = currentSerial,
                        model = currentModel
                    )
                )
            }
            val places = myMonitorsRepository.fetchPlaces().getOrElse { return@launch }
            val selected = places.firstOrNull()?.id
            _uiState.update {
                it.copy(
                    registration = it.registration.copy(
                        places = places,
                        selectedPlaceId = selected,
                        requiresAuth = false
                    )
                )
            }
        }
    }

    fun selectRegistrationPlace(placeId: Int) {
        _uiState.update { state ->
            state.copy(registration = state.registration.copy(selectedPlaceId = placeId))
        }
    }

    fun updateRegistrationLocationName(value: String) {
        _uiState.update { state ->
            state.copy(registration = state.registration.copy(locationName = value))
        }
    }

    fun updateRegistrationModel(value: String) {
        _uiState.update { state ->
            state.copy(registration = state.registration.copy(model = value))
        }
    }

    fun updateRegistrationSerial(value: String) {
        _uiState.update { state ->
            state.copy(registration = state.registration.copy(serial = value))
        }
    }

    fun submitRegistration() {
        val reg = _uiState.value.registration
        val placeId = reg.selectedPlaceId ?: run {
            _uiState.update {
                it.copy(
                    registration = reg.copy(
                        errorMessageRes = R.string.settings_wifi_ble_registration_error_missing_place,
                        errorMessage = null
                    )
                )
            }
            return
        }
        val serial = reg.serial.trim()
        if (serial.isEmpty()) {
            _uiState.update {
                it.copy(
                    registration = reg.copy(
                        errorMessageRes = R.string.settings_wifi_ble_registration_error_missing_serial,
                        errorMessage = null
                    )
                )
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { state ->
                state.copy(
                    registration = state.registration.copy(
                        isSubmitting = true,
                        errorMessage = null,
                        resultMessage = null
                    )
                )
            }
            myMonitorsRepository.registerMonitor(
                placeId = placeId,
                serial = serial,
                model = reg.model.ifBlank { "Unknown" },
                locationName = reg.locationName.ifBlank { "Monitor" }
            ).onSuccess {
                _uiState.update { state ->
                    state.copy(
                        registration = state.registration.copy(
                            isSubmitting = false,
                            resultMessage = null,
                            resultMessageRes = R.string.settings_wifi_ble_registration_success,
                            errorMessage = null,
                            errorMessageRes = null
                        )
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        registration = state.registration.copy(
                            isSubmitting = false,
                            errorMessage = throwable.message,
                            errorMessageRes = throwable.message?.let { null } ?: R.string.settings_wifi_ble_registration_error_generic
                        )
                    )
                }
            }
        }
    }

    private fun statusLabel(code: ProvisioningStatusCode): Int {
        return when (code) {
            ProvisioningStatusCode.CONNECTING_WIFI -> R.string.settings_wifi_ble_device_status_wifi_connect
            ProvisioningStatusCode.CONNECTING_SERVER -> R.string.settings_wifi_ble_device_status_connecting_server
            ProvisioningStatusCode.SERVER_REACHABLE -> R.string.settings_wifi_ble_device_status_server_reachable
            ProvisioningStatusCode.CONFIGURED -> R.string.settings_wifi_ble_device_status_configured
            ProvisioningStatusCode.WIFI_FAILED -> R.string.settings_wifi_ble_device_status_failed_wifi
            ProvisioningStatusCode.SERVER_UNREACHABLE -> R.string.settings_wifi_ble_device_status_server_unreachable
            ProvisioningStatusCode.CONFIG_PENDING -> R.string.settings_wifi_ble_device_status_failed_configuration
            ProvisioningStatusCode.NOT_REGISTERED -> R.string.settings_wifi_ble_device_status_not_registered
            ProvisioningStatusCode.UNKNOWN -> R.string.settings_wifi_ble_device_status_unknown
        }
    }

    private fun scheduleWifiScanTimeout() {
        wifiScanTimeoutJob?.cancel()
        wifiScanTimeoutJob = viewModelScope.launch {
            delay(WIFI_SCAN_TIMEOUT_MS)
            Log.w(VM_TAG, "wifi scan timeout after ${WIFI_SCAN_TIMEOUT_MS}ms; availableNetworks=${_uiState.value.availableNetworks.size}")
            _uiState.update {
                it.copy(
                    isScanningNetworks = false,
                    availableNetworks = it.availableNetworks,
                    statusTextRes = if (it.availableNetworks.isEmpty()) null else it.statusTextRes
                )
            }
        }
    }

    private fun handleFailure(error: ProvisioningError, rawMessage: String?) {
        val messageRes = when (error) {
            ProvisioningError.BluetoothUnavailable -> R.string.settings_wifi_ble_error_bluetooth_unavailable
            ProvisioningError.PermissionsMissing -> R.string.settings_wifi_ble_error_unauthorized
            ProvisioningError.ScanTimeout -> R.string.settings_wifi_ble_error_scan_timeout
            ProvisioningError.MissingService -> R.string.settings_wifi_ble_error_service_missing
            ProvisioningError.MissingCharacteristics -> R.string.settings_wifi_ble_error_characteristics
            ProvisioningError.EncodingFailed -> R.string.settings_wifi_ble_error_encoding
            ProvisioningError.WriteFailed -> R.string.settings_wifi_ble_error_write_failed
            ProvisioningError.Disconnected -> R.string.settings_wifi_ble_error_disconnected
            ProvisioningError.MtuTooSmall -> R.string.settings_wifi_ble_error_mtu_too_small
            is ProvisioningError.Message -> null
        }
        val messageText = if (error is ProvisioningError.Message) error.message else rawMessage
        closeSession()
        _uiState.update {
            it.copy(
                isProcessing = false,
                isScanningDevices = false,
                isConnecting = false,
                isDiscovering = false,
                isScanningNetworks = false,
                showFailureAlert = true,
                failureMessageRes = messageRes,
                failureMessage = messageText
            )
        }
    }
}
