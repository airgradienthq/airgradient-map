package com.airgradient.android.data.provisioning

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import com.airgradient.android.domain.models.provisioning.BleDeviceInfo
import com.airgradient.android.domain.models.provisioning.ProvisioningError
import com.airgradient.android.domain.models.provisioning.ProvisioningStatus
import com.airgradient.android.domain.models.provisioning.ProvisioningStatusCode
import com.airgradient.android.domain.models.provisioning.WifiNetwork
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TARGET_MTU = 512
private const val MIN_USABLE_MTU = 23
private const val BLUETOOTH_READY_TIMEOUT_MS = 5_000L
private const val SCAN_TIMEOUT_MS = 20_000L
private const val BLE_TAG = "AGBleProvisioning"

@Singleton
class BleProvisioningManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
    ) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)

    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    suspend fun ensureBluetoothReady(timeoutMs: Long = BLUETOOTH_READY_TIMEOUT_MS): Result<Unit> {
        val currentAdapter = adapter ?: return Result.failure(IllegalStateException("Bluetooth not supported"))
        return try {
            Log.d(BLE_TAG, "ensureBluetoothReady: state=${currentAdapter.state} enabled=${currentAdapter.isEnabled}")
            val start = SystemClock.elapsedRealtime()
            while (currentAdapter.state == BluetoothAdapter.STATE_TURNING_ON ||
                currentAdapter.state == BluetoothAdapter.STATE_TURNING_OFF
            ) {
                if (SystemClock.elapsedRealtime() - start > timeoutMs) {
                    return Result.failure(TimeoutException("Bluetooth not ready"))
                }
                delay(200)
            }
            if (currentAdapter.isEnabled) Result.success(Unit)
            else Result.failure(IllegalStateException("Bluetooth not enabled"))
        } catch (security: SecurityException) {
            Log.e(BLE_TAG, "ensureBluetoothReady: SecurityException", security)
            Result.failure(security)
        }
    }

    suspend fun scanForProvisioningDevice(timeoutMs: Long = SCAN_TIMEOUT_MS): Result<BluetoothDevice> {
        val scanner = adapter?.bluetoothLeScanner ?: return Result.failure(IllegalStateException("Bluetooth scanner unavailable"))
        Log.d(BLE_TAG, "scanForProvisioningDevice: starting scan (unfiltered), timeoutMs=$timeoutMs")
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()

        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine<Result<BluetoothDevice>> { cont ->
                var timeoutJob: Job? = null
                var bestCandidate: BluetoothDevice? = null
                var bestRssi: Int = Int.MIN_VALUE
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult?) {
                        val res = result ?: return
                        val device = res.device
                        val record = res.scanRecord
                        val serviceUuids = record?.serviceUuids
                        val name = device.name ?: record?.deviceName
                        val rssi = res.rssi
                        val hasProvisioningService = serviceUuids?.any { it?.uuid == PROVISIONING_SERVICE_UUID } == true
                        Log.d(
                            BLE_TAG,
                            "scanForProvisioningDevice: result addr=${device.address} name=$name rssi=$rssi hasProvSvc=$hasProvisioningService svcs=$serviceUuids"
                        )

                        val isNameMatch = name?.contains("airgradient", ignoreCase = true) == true ||
                            name?.startsWith("AG", ignoreCase = true) == true

                        val isMatch = hasProvisioningService || isNameMatch
                        if (isMatch && cont.isActive) {
                            Log.d(BLE_TAG, "scanForProvisioningDevice: selecting device address=${device.address} name=$name")
                            cont.resume(Result.success(device))
                            timeoutJob?.cancel()
                            scanner.stopScan(this)
                            return
                        }

                        // Track best RSSI as a fallback if we never see a clear match
                        if (rssi > bestRssi) {
                            bestRssi = rssi
                            bestCandidate = device
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        Log.e(BLE_TAG, "scanForProvisioningDevice: onScanFailed errorCode=$errorCode")
                        if (cont.isActive) {
                            cont.resume(Result.failure(IllegalStateException("Scan failed: $errorCode")))
                        }
                    }
                }

                try {
                    // Start an unfiltered scan and apply our matching logic in the callback.
                    scanner.startScan(null, settings, callback)
                } catch (security: SecurityException) {
                    Log.e(BLE_TAG, "scanForProvisioningDevice: SecurityException starting scan", security)
                    cont.resume(Result.failure(security))
                    return@suspendCancellableCoroutine
                }

                timeoutJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    delay(timeoutMs)
                    if (cont.isActive) {
                        Log.w(BLE_TAG, "scanForProvisioningDevice: scan timeout after $timeoutMs ms, bestCandidate=${bestCandidate?.address}")
                        scanner.stopScan(callback)
                        val candidate = bestCandidate
                        if (candidate != null) {
                            cont.resume(Result.success(candidate))
                        } else {
                            cont.resume(Result.failure(TimeoutException("Scan timeout")))
                        }
                    }
                }

                cont.invokeOnCancellation {
                    scanner.stopScan(callback)
                    timeoutJob?.cancel()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun openSession(device: BluetoothDevice): Result<BleProvisioningSession> {
        return withContext(Dispatchers.IO) {
            Log.d(BLE_TAG, "openSession: starting for device address=${device.address} name=${device.name} bondState=${device.bondState}")
            suspendCancellableCoroutine<Result<BleProvisioningSession>> { cont ->
                // Use a dedicated scope that outlives the openSession coroutine so that
                // callbacks can continue to emit events (e.g., Wi-Fi scan results)
                // after this suspend function has returned.
                val scope = CoroutineScope(Dispatchers.IO)
                val callback = ProvisioningGattCallback(gson, scope)
                val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                    ?: run {
                        cont.resume(Result.failure(IllegalStateException("Unable to connect GATT")))
                        return@suspendCancellableCoroutine
                    }

                scope.launch {
                    try {
                        withTimeout(15_000L) { callback.awaitConnected() }
                        Log.d(BLE_TAG, "openSession: GATT connected, bondState=${device.bondState}")
                        // Request a large MTU but continue even if the negotiated size is lower.
                        runCatching { withTimeout(5_000L) { callback.requestAndAwaitMtu(gatt, TARGET_MTU) } }
                            .onSuccess { mtu -> Log.d(BLE_TAG, "openSession: negotiated MTU=$mtu") }
                            .onFailure { Log.w(BLE_TAG, "openSession: MTU request failed", it) }

                        val servicesOk = withTimeout(10_000L) { callback.awaitServicesDiscovered(gatt) }
                        if (!servicesOk) {
                            Log.e(BLE_TAG, "openSession: service discovery failed")
                            cont.resume(Result.failure(IllegalStateException("Service discovery failed")))
                            gatt.disconnect()
                            return@launch
                        }

                        val characteristics = resolveCharacteristics(gatt)
                            ?: run {
                                Log.e(BLE_TAG, "openSession: provisioning characteristics missing")
                                cont.resume(Result.failure(IllegalStateException("Provisioning characteristics missing")))
                                gatt.disconnect()
                                return@launch
                            }

                        enableNotifications(gatt, callback, characteristics.statusCharacteristic)
                        characteristics.wifiScanCharacteristic?.let { wifiChar ->
                            enableNotifications(gatt, callback, wifiChar)
                        }

                        val deviceInfo = readDeviceInfo(gatt, callback, characteristics.deviceInfoService)
                        Log.d(BLE_TAG, "openSession: session ready, deviceInfo=$deviceInfo")
                        cont.resume(
                            Result.success(
                                BleProvisioningSession(
                                    gatt = gatt,
                                    callback = callback,
                                    characteristics = characteristics,
                                    deviceInfo = deviceInfo,
                                    scope = scope
                                )
                            )
                        )
                    } catch (t: Throwable) {
                        Log.e(BLE_TAG, "openSession: error during session setup", t)
                        cont.resume(Result.failure(t))
                        gatt.disconnect()
                    }
                }

                cont.invokeOnCancellation {
                    scope.cancel()
                    gatt.disconnect()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun enableNotifications(
        gatt: BluetoothGatt,
        callback: ProvisioningGattCallback,
        characteristic: BluetoothGattCharacteristic
    ) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
        descriptor?.let {
            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val deferred = callback.prepareDescriptor(it.uuid)
            if (gatt.writeDescriptor(it)) {
                deferred.await()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun readDeviceInfo(
        gatt: BluetoothGatt,
        callback: ProvisioningGattCallback,
        deviceInfoService: BluetoothGattService?
    ): BleDeviceInfo? {
        deviceInfoService ?: return null
        val manufacturer = deviceInfoService
            .getCharacteristic(MANUFACTURER_UUID)
            ?.let { callback.readAsString(gatt, it) }
        val model = deviceInfoService
            .getCharacteristic(MODEL_UUID)
            ?.let { callback.readAsString(gatt, it) }
        val serial = deviceInfoService
            .getCharacteristic(SERIAL_UUID)
            ?.let { callback.readAsString(gatt, it) }
        val firmware = deviceInfoService
            .getCharacteristic(FIRMWARE_UUID)
            ?.let { callback.readAsString(gatt, it) }
        if (manufacturer == null && model == null && serial == null && firmware == null) return null
        return BleDeviceInfo(
            manufacturer = manufacturer,
            model = model,
            serial = serial,
            firmware = firmware
        )
    }

    private fun resolveCharacteristics(gatt: BluetoothGatt): SessionCharacteristics? {
        val provisioningService = gatt.getService(PROVISIONING_SERVICE_UUID) ?: return null
        val credentialsCharacteristic = provisioningService.characteristics.firstOrNull {
            it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        } ?: return null

        val notifyCandidate = provisioningService.characteristics.firstOrNull {
            it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        }
        val statusCharacteristic = notifyCandidate ?: credentialsCharacteristic.takeIf {
            it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        } ?: return null

        val wifiScanCharacteristic = provisioningService.getCharacteristic(WIFI_SCAN_UUID)
        val deviceInfoService = gatt.getService(DEVICE_INFO_SERVICE_UUID)

        return SessionCharacteristics(
            credentialsCharacteristic = credentialsCharacteristic,
            statusCharacteristic = statusCharacteristic,
            wifiScanCharacteristic = wifiScanCharacteristic,
            deviceInfoService = deviceInfoService
        )
    }
}

@SuppressLint("MissingPermission")
class BleProvisioningSession internal constructor(
    private val gatt: BluetoothGatt,
    private val callback: ProvisioningGattCallback,
    val characteristics: SessionCharacteristics,
    val deviceInfo: BleDeviceInfo?,
    private val scope: CoroutineScope
) {
    val statusUpdates: SharedFlow<ProvisioningStatus> = callback.statusUpdates
    val wifiScanResults: SharedFlow<List<WifiNetwork>> = callback.wifiScanUpdates
    val disconnections: SharedFlow<Unit> = callback.disconnections

    suspend fun requestWifiScan(): Result<Unit> {
        val wifiChar = characteristics.wifiScanCharacteristic ?: return Result.success(Unit)
        Log.d(BLE_TAG, "BleProvisioningSession.requestWifiScan: uuid=${wifiChar.uuid} properties=${wifiChar.properties}")
        wifiChar.value = byteArrayOf(0x01)
        wifiChar.writeType = if (wifiChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        val writeDeferred = callback.prepareWrite(wifiChar.uuid)
        val success = gatt.writeCharacteristic(wifiChar)
        Log.d(BLE_TAG, "BleProvisioningSession.requestWifiScan: writeCharacteristic success=$success writeType=${wifiChar.writeType}")
        if (!success) return Result.failure(IllegalStateException("Wi-Fi scan write failed"))
        if (wifiChar.writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            return Result.success(Unit)
        }
        val acknowledged = writeDeferred.await()
        return if (acknowledged) Result.success(Unit) else Result.failure(IllegalStateException("Wi-Fi scan write not acknowledged"))
    }

    suspend fun sendCredentials(ssid: String, password: String): Result<Unit> {
        val trimmedSsid = ssid.trim()
        if (trimmedSsid.isEmpty()) {
            return Result.failure(IllegalArgumentException("SSID missing"))
        }
        val payload = kotlin.runCatching {
            val json = JsonObject().apply {
                addProperty("ssid", trimmedSsid)
                addProperty("password", password)
            }
            json.toString().toByteArray(Charsets.UTF_8)
        }.getOrElse { return Result.failure(it) }

        val writeChar = characteristics.credentialsCharacteristic
        val prefersResponse = writeChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        writeChar.writeType = if (prefersResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        writeChar.value = payload
        val writeDeferred = callback.prepareWrite(writeChar.uuid)
        val success = gatt.writeCharacteristic(writeChar)
        if (!success) return Result.failure(IllegalStateException("Credential write failed"))
        if (writeChar.writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            return Result.success(Unit)
        }
        val acknowledged = writeDeferred.await()
        return if (acknowledged) Result.success(Unit) else Result.failure(IllegalStateException("Credential write not acknowledged"))
    }

    suspend fun initialReads() {
        kotlin.runCatching { callback.read(gatt, characteristics.statusCharacteristic) }
        characteristics.wifiScanCharacteristic?.let { kotlin.runCatching { callback.read(gatt, it) } }
    }

    fun close() {
        scope.cancel()
        gatt.disconnect()
        gatt.close()
    }
}

data class SessionCharacteristics(
    val credentialsCharacteristic: BluetoothGattCharacteristic,
    val statusCharacteristic: BluetoothGattCharacteristic,
    val wifiScanCharacteristic: BluetoothGattCharacteristic?,
    val deviceInfoService: BluetoothGattService?
)

internal class ProvisioningGattCallback(
    private val gson: Gson,
    private val scope: CoroutineScope
) : BluetoothGattCallback() {

    private val connectionReady = CompletableDeferred<Unit>()
    private val servicesReady = CompletableDeferred<Boolean>()
    private val mtuReady = CompletableDeferred<Int>()
    private val pendingReads = ConcurrentHashMap<UUID, CompletableDeferred<ByteArray>>()
    private val pendingWrites = ConcurrentHashMap<UUID, CompletableDeferred<Boolean>>()
    private val pendingDescriptors = ConcurrentHashMap<UUID, CompletableDeferred<Boolean>>()

    private val _statusUpdates = MutableSharedFlow<ProvisioningStatus>(replay = 1, extraBufferCapacity = 4)
    val statusUpdates: SharedFlow<ProvisioningStatus> = _statusUpdates

    private val _wifiScanUpdates = MutableSharedFlow<List<WifiNetwork>>(replay = 1, extraBufferCapacity = 2)
    val wifiScanUpdates: SharedFlow<List<WifiNetwork>> = _wifiScanUpdates

    private val _disconnections = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val disconnections: SharedFlow<Unit> = _disconnections

    private val aggregatedNetworks = LinkedHashMap<String, WifiNetwork>()
    private var expectedPages: Int? = null
    private var lastPageSeen: Int? = null

    suspend fun awaitConnected() = connectionReady.await()
    suspend fun awaitServicesDiscovered(gatt: BluetoothGatt): Boolean {
        gatt.discoverServices()
        return servicesReady.await()
    }

    suspend fun requestAndAwaitMtu(gatt: BluetoothGatt, mtu: Int): Int {
        if (!gatt.requestMtu(mtu)) return MIN_USABLE_MTU
        return mtuReady.await().coerceAtLeast(MIN_USABLE_MTU)
    }

    fun prepareDescriptor(uuid: UUID): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        pendingDescriptors[uuid] = deferred
        Log.d(BLE_TAG, "prepareDescriptor: uuid=$uuid")
        return deferred
    }

    fun prepareWrite(uuid: UUID): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        pendingWrites[uuid] = deferred
        Log.d(BLE_TAG, "prepareWrite: uuid=$uuid")
        return deferred
    }

    suspend fun read(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): ByteArray? {
        val deferred = CompletableDeferred<ByteArray>()
        pendingReads[characteristic.uuid] = deferred
        if (!gatt.readCharacteristic(characteristic)) {
            pendingReads.remove(characteristic.uuid)
            return null
        }
        return deferred.await()
    }

    suspend fun readAsString(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): String? {
        val bytes = read(gatt, characteristic) ?: return null
        return bytes.toString(Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        Log.d(BLE_TAG, "onConnectionStateChange: address=${gatt.device.address} status=$status newState=$newState")
        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            if (!connectionReady.isCompleted && status != BluetoothGatt.GATT_SUCCESS) {
                connectionReady.completeExceptionally(IllegalStateException("Connection failed"))
            }
            scope.launch { _disconnections.emit(Unit) }
            return
        }
        if (status != BluetoothGatt.GATT_SUCCESS || newState != BluetoothProfile.STATE_CONNECTED) {
            if (!connectionReady.isCompleted) connectionReady.completeExceptionally(IllegalStateException("Connection failed"))
            scope.launch { _disconnections.emit(Unit) }
            return
        }
        connectionReady.complete(Unit)
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        Log.d(BLE_TAG, "onMtuChanged: address=${gatt.device.address} mtu=$mtu status=$status")
        if (!mtuReady.isCompleted) {
            mtuReady.complete(mtu)
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        Log.d(BLE_TAG, "onServicesDiscovered: address=${gatt.device.address} status=$status")
        if (!servicesReady.isCompleted) {
            servicesReady.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        pendingReads.remove(characteristic.uuid)?.let { deferred ->
            if (status == BluetoothGatt.GATT_SUCCESS) {
                deferred.complete(characteristic.value)
            } else {
                Log.e(BLE_TAG, "onCharacteristicRead: failed for uuid=${characteristic.uuid} status=$status")
                deferred.completeExceptionally(IllegalStateException("Read failed"))
            }
        }
        handlePayload(characteristic)
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        Log.d(BLE_TAG, "onCharacteristicWrite: uuid=${characteristic.uuid} status=$status")
        pendingWrites.remove(characteristic.uuid)?.complete(status == BluetoothGatt.GATT_SUCCESS)
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        Log.d(BLE_TAG, "onDescriptorWrite: uuid=${descriptor.uuid} status=$status")
        pendingDescriptors.remove(descriptor.uuid)?.complete(status == BluetoothGatt.GATT_SUCCESS)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        Log.d(BLE_TAG, "onCharacteristicChanged: uuid=${characteristic.uuid} length=${characteristic.value?.size ?: 0}")
        handlePayload(characteristic)
    }

    private fun handlePayload(characteristic: BluetoothGattCharacteristic) {
        val value = characteristic.value ?: return
        val preview = value.toString(Charsets.UTF_8).replace("\n", "\\n").take(128)
        Log.d(BLE_TAG, "handlePayload: uuid=${characteristic.uuid} raw=[$preview]")
        if (characteristic.uuid == WIFI_SCAN_UUID) {
            handleWifiPayload(value)
            return
        }
        val status = parseProvisioningStatus(value, gson)
        if (status != null) {
            scope.launch { _statusUpdates.emit(status) }
            return
        }
        // Some firmware versions report Wi-Fi scan results on the status characteristic instead of a dedicated one.
        handleWifiPayload(value)
    }

    private fun handleWifiPayload(raw: ByteArray) {
        val payload = parseWifiPayload(raw) ?: return

        Log.d(BLE_TAG, "handleWifiPayload: page=${payload.page} totalPages=${payload.totalPages} totalFound=${payload.totalFound} networksInPayload=${payload.networks.size}")

        if (payload.page == null || payload.page == 1) {
            aggregatedNetworks.clear()
            expectedPages = null
            lastPageSeen = null
        }

        payload.totalPages?.let { expectedPages = it }
        payload.page?.let { lastPageSeen = it }

        payload.networks.forEach { network ->
            val key = "${network.ssid}|${network.isOpen}"
            val existing = aggregatedNetworks[key]
            if (existing == null || (network.rssi ?: Int.MIN_VALUE) > (existing.rssi ?: Int.MIN_VALUE)) {
                aggregatedNetworks[key] = network
            }
        }

        val shouldEmit = expectedPages == null || (lastPageSeen != null && expectedPages != null && lastPageSeen!! >= expectedPages!!)
        if (shouldEmit || payload.networks.isNotEmpty()) {
            val sorted = aggregatedNetworks.values
                .sortedByDescending { it.rssi ?: Int.MIN_VALUE }
                .take(10)
            Log.d(BLE_TAG, "handleWifiPayload: preparing to emit ${sorted.size} networks: ${sorted.joinToString { it.ssid }}")
            scope.launch {
                try {
                    _wifiScanUpdates.emit(sorted)
                    Log.d(BLE_TAG, "handleWifiPayload: emit completed, size=${sorted.size}")
                } catch (t: Throwable) {
                    Log.e(BLE_TAG, "handleWifiPayload: emit failed", t)
                }
            }
        }
    }
}

private val CLIENT_CHARACTERISTIC_CONFIG: UUID =
    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

private val PROVISIONING_SERVICE_UUID: UUID =
    UUID.fromString("ACBCFEA8-E541-4C40-9BFD-17820F16C95C")
private val WIFI_SCAN_UUID: UUID =
    UUID.fromString("467A080F-E50F-42C9-B9B2-A2AB14D82725")
private val DEVICE_INFO_SERVICE_UUID: UUID =
    UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
private val MANUFACTURER_UUID: UUID =
    UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
private val MODEL_UUID: UUID =
    UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
private val SERIAL_UUID: UUID =
    UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb")
private val FIRMWARE_UUID: UUID =
    UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")

fun parseProvisioningStatus(bytes: ByteArray, gson: Gson): ProvisioningStatus? {
    if (bytes.isEmpty()) return null
    val utf8 = bytes.toString(Charsets.UTF_8)
        .substringBefore('\u0000')
        .trim()
    Log.d(BLE_TAG, "parseProvisioningStatus: raw=[$utf8]")
    // Try JSON parsing first
    val jsonElement = runCatching { JsonParser.parseString(utf8) }.getOrNull()
    val parsedStatus = when (jsonElement) {
        is JsonObject -> parseStatusValue(jsonElement.get("status"))
        is JsonArray -> null
        is JsonElement -> parseStatusValue(jsonElement)
        else -> null
    }
    if (parsedStatus != null) return parsedStatus

    utf8.toIntOrNull()?.let { code ->
        val mapped = ProvisioningStatusCode.fromInt(code)
        return ProvisioningStatus(mapped, code)
    }

    if (bytes.size in setOf(1, 2, 4)) {
        val little = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).intFromSize(bytes.size)
        val big = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).intFromSize(bytes.size)
        val candidate = little ?: big
        if (candidate != null) {
            val mapped = ProvisioningStatusCode.fromInt(candidate)
            return ProvisioningStatus(mapped, candidate)
        }
    }
    return null
}

private fun parseStatusValue(element: JsonElement?): ProvisioningStatus? {
    element ?: return null
    if (element.isJsonPrimitive) {
        val primitive = element.asJsonPrimitive
        if (primitive.isNumber) {
            val code = primitive.asInt
            return ProvisioningStatus(ProvisioningStatusCode.fromInt(code), code)
        }
        if (primitive.isString) {
            val code = primitive.asString.toIntOrNull() ?: return null
            return ProvisioningStatus(ProvisioningStatusCode.fromInt(code), code)
        }
    }
    return null
}

private fun ByteBuffer.intFromSize(size: Int): Int? {
    return when (size) {
        1 -> this.get(0).toInt() and 0xFF
        2 -> this.short.toInt()
        4 -> this.int
        else -> null
    }
}

data class WifiPayload(
    val networks: List<WifiNetwork>,
    val page: Int? = null,
    val totalPages: Int? = null,
    val totalFound: Int? = null
)

fun parseWifiPayload(bytes: ByteArray): WifiPayload? {
    val text = bytes.toString(Charsets.UTF_8)
        .substringBefore('\u0000')
        .trim()
    Log.d(BLE_TAG, "parseWifiPayload: raw=[$text]")
    if (text.isBlank()) return null
    val element = runCatching { JsonParser.parseString(text) }.getOrNull() ?: return null

    fun JsonObject.toWifiNetwork(): WifiNetwork? {
        val ssid = (get("s") ?: get("ssid"))?.takeIf { it.isJsonPrimitive }?.asString ?: ""
        val rssi = (get("r") ?: get("rssi"))?.takeIf { it.isJsonPrimitive }?.asInt
        val openElement = get("o") ?: get("open")
        val isOpen = when {
            openElement == null -> false
            openElement.isJsonPrimitive && openElement.asJsonPrimitive.isBoolean -> openElement.asBoolean
            openElement.isJsonPrimitive && openElement.asJsonPrimitive.isNumber -> openElement.asInt == 1
            else -> false
        }
        return WifiNetwork(ssid = ssid, rssi = rssi, isOpen = isOpen)
    }

    if (element.isJsonArray) {
        val array = element.asJsonArray
        val networks = array.mapNotNull { if (it.isJsonObject) it.asJsonObject.toWifiNetwork() else null }
            .sortedByDescending { it.rssi ?: Int.MIN_VALUE }
            .take(10)
        return WifiPayload(networks)
    }

    if (element.isJsonObject) {
        val obj = element.asJsonObject
        val wifiArray = obj.get("wifi")?.takeIf { it.isJsonArray }?.asJsonArray
        val networks = wifiArray?.mapNotNull { if (it.isJsonObject) it.asJsonObject.toWifiNetwork() else null }
            ?.sortedByDescending { it.rssi ?: Int.MIN_VALUE }
            ?.take(10)
            ?: emptyList()
        val page = obj.get("page")?.takeIf { it.isJsonPrimitive }?.asInt
        val totalPages = obj.get("tpage")?.takeIf { it.isJsonPrimitive }?.asInt
        val totalFound = obj.get("found")?.takeIf { it.isJsonPrimitive }?.asInt
        return WifiPayload(networks = networks, page = page, totalPages = totalPages, totalFound = totalFound)
    }

    return null
}
