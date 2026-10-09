package io.github.mfren022.nearbyphones

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat

/**
 * Finds nearby Bluetooth devices with BLE scanning and classic discovery, and advertises
 * [PhoneClassifier.APP_SERVICE_UUID] so other phones running this app can find this one.
 *
 * Callers must hold the Bluetooth scan/advertise permissions before calling [start].
 * All callbacks are delivered on the main thread.
 */
@SuppressLint("MissingPermission")
class NearbyScanner(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    private val onDevicesChanged: (List<NearbyDevice>) -> Unit,
    private val onError: (Int) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val devices = LinkedHashMap<String, NearbyDevice>()
    private var changed = false

    var isScanning = false
        private set

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handler.post { onBleResult(result) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            handler.post { results.forEach(::onBleResult) }
        }

        override fun onScanFailed(errorCode: Int) {
            handler.post {
                Log.w(TAG, "BLE scan failed: $errorCode")
                onError(errorCode)
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            // Not fatal: we can still see others, they just won't confirm us.
            Log.w(TAG, "Advertising failed: $errorCode")
        }
    }

    private val classicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> onClassicResult(intent)
                // Discovery runs for ~12 s; keep it going while we're scanning.
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED ->
                    if (isScanning) handler.postDelayed({ if (isScanning) adapter.startDiscovery() }, 1_000)
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            pruneStale()
            if (changed) {
                changed = false
                onDevicesChanged(devices.values.toList())
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    fun start() {
        if (isScanning) return
        isScanning = true
        devices.clear()
        changed = true

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        adapter.bluetoothLeScanner?.startScan(null, settings, scanCallback)

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(context, classicReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        adapter.startDiscovery()

        startAdvertising()
        handler.post(ticker)
    }

    fun stop() {
        if (!isScanning) return
        isScanning = false
        handler.removeCallbacksAndMessages(null)
        runCatching { adapter.bluetoothLeScanner?.stopScan(scanCallback) }
        runCatching { adapter.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) }
        runCatching { adapter.cancelDiscovery() }
        runCatching { context.unregisterReceiver(classicReceiver) }
        onDevicesChanged(devices.values.toList())
    }

    private fun startAdvertising() {
        val advertiser = adapter.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(PhoneClassifier.APP_SERVICE_UUID)
            .setIncludeTxPowerLevel(true)
            .setIncludeDeviceName(false)
            .build()
        runCatching { advertiser.startAdvertising(settings, data, advertiseCallback) }
            .onFailure { Log.w(TAG, "Could not start advertising", it) }
    }

    private fun onBleResult(result: ScanResult) {
        if (!isScanning) return
        val record = result.scanRecord
        val name = record?.deviceName
        val txPower = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }
            ?: result.txPower.takeIf { it != ScanResult.TX_POWER_NOT_PRESENT }
        update(
            address = result.device.address,
            name = name,
            rssi = result.rssi,
            measuredPower = NearbyDevice.measuredPowerFromTxPower(txPower),
            source = Source.BLE,
            classification = PhoneClassifier.classifyBle(record, name),
        )
    }

    private fun onClassicResult(intent: Intent) {
        if (!isScanning) return
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            ?: return
        val btClass = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_CLASS, BluetoothClass::class.java)
        val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
        if (rssi == Short.MIN_VALUE.toInt()) return
        update(
            address = device.address,
            name = name,
            rssi = rssi,
            measuredPower = NearbyDevice.DEFAULT_MEASURED_POWER,
            source = Source.CLASSIC,
            classification = PhoneClassifier.classifyClassic(btClass, name),
        )
    }

    private fun update(
        address: String,
        name: String?,
        rssi: Int,
        measuredPower: Int,
        source: Source,
        classification: Classification,
    ) {
        val now = SystemClock.elapsedRealtime()
        val old = devices[address]
        devices[address] = if (old == null) {
            NearbyDevice(address, name, rssi, measuredPower, setOf(source), classification, now)
        } else {
            old.copy(
                name = name ?: old.name,
                // Exponential moving average to calm down noisy RSSI readings.
                rssi = Math.round(old.rssi * (1 - RSSI_SMOOTHING) + rssi * RSSI_SMOOTHING).toInt(),
                measuredPowerAt1m = if (measuredPower != NearbyDevice.DEFAULT_MEASURED_POWER) measuredPower else old.measuredPowerAt1m,
                sources = old.sources + source,
                classification = Classification.best(old.classification, classification),
                lastSeenMillis = now,
            )
        }
        changed = true
    }

    private fun pruneStale() {
        val now = SystemClock.elapsedRealtime()
        if (devices.values.removeAll { now - it.lastSeenMillis > STALE_AFTER_MS }) changed = true
    }

    companion object {
        private const val TAG = "NearbyScanner"
        private const val TICK_MS = 1_000L

        /** Classic discovery only reports each device about once per ~13 s cycle. */
        private const val STALE_AFTER_MS = 45_000L
        private const val RSSI_SMOOTHING = 0.3
    }
}
