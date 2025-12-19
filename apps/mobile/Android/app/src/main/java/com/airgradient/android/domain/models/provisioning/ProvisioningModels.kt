package com.airgradient.android.domain.models.provisioning

data class WifiNetwork(
    val ssid: String,
    val rssi: Int?,
    val isOpen: Boolean
)

data class BleDeviceInfo(
    val manufacturer: String? = null,
    val model: String? = null,
    val serial: String? = null,
    val firmware: String? = null
)

enum class ProvisioningStatusCode(
    val code: Int,
    val isTerminal: Boolean,
    val shouldDisconnect: Boolean
) {
    CONNECTING_WIFI(0, false, false),
    CONNECTING_SERVER(1, false, false),
    SERVER_REACHABLE(2, false, false),
    CONFIGURED(3, true, true),
    WIFI_FAILED(10, true, false),
    SERVER_UNREACHABLE(11, true, false),
    CONFIG_PENDING(12, true, true),
    NOT_REGISTERED(13, true, true),
    UNKNOWN(-1, false, false);

    companion object {
        fun fromInt(value: Int?): ProvisioningStatusCode {
            return when (value) {
                0 -> CONNECTING_WIFI
                1 -> CONNECTING_SERVER
                2 -> SERVER_REACHABLE
                3 -> CONFIGURED
                10 -> WIFI_FAILED
                11 -> SERVER_UNREACHABLE
                12 -> CONFIG_PENDING
                13 -> NOT_REGISTERED
                else -> UNKNOWN
            }
        }
    }
}

data class ProvisioningStatus(
    val code: ProvisioningStatusCode,
    val rawCode: Int
)

data class ProvisioningResult(
    val status: ProvisioningStatus,
    val deviceInfo: BleDeviceInfo? = null,
    val networks: List<WifiNetwork> = emptyList()
)

sealed class ProvisioningError {
    data class Message(val message: String) : ProvisioningError()
    object BluetoothUnavailable : ProvisioningError()
    object PermissionsMissing : ProvisioningError()
    object ScanTimeout : ProvisioningError()
    object MissingService : ProvisioningError()
    object MissingCharacteristics : ProvisioningError()
    object EncodingFailed : ProvisioningError()
    object WriteFailed : ProvisioningError()
    object Disconnected : ProvisioningError()
    object MtuTooSmall : ProvisioningError()
}
