package io.github.mfren022.nearbyphones

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var emptyText: TextView
    private lateinit var scanButton: MaterialButton
    private lateinit var phonesOnlySwitch: MaterialSwitch
    private val deviceAdapter = DeviceAdapter()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanner: NearbyScanner? = null
    private var lastDevices: List<NearbyDevice> = emptyList()

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                // Classic discovery and some BLE results still need location on Android 12+.
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) startScan() else showMessage(R.string.permissions_denied)
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (bluetoothAdapter?.isEnabled == true) startScan() else showMessage(R.string.bt_off)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        emptyText = findViewById(R.id.emptyText)
        scanButton = findViewById(R.id.scanButton)
        phonesOnlySwitch = findViewById(R.id.phonesOnlySwitch)

        findViewById<RecyclerView>(R.id.deviceList).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceAdapter
            itemAnimator = null // Rows update every second; animations just flicker.
        }

        bluetoothAdapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (bluetoothAdapter == null || !packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            statusText.setText(R.string.bt_unsupported)
            scanButton.isEnabled = false
            return
        }

        scanButton.setOnClickListener {
            if (scanner?.isScanning == true) stopScan() else startScan()
        }
        phonesOnlySwitch.setOnCheckedChangeListener { _, _ -> render() }
    }

    override fun onStop() {
        super.onStop()
        // Scanning is battery-hungry and BLE scans are throttled in the background anyway.
        stopScan()
    }

    @SuppressLint("MissingPermission") // Checked against requiredPermissions below.
    private fun startScan() {
        val adapter = bluetoothAdapter ?: return

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
            return
        }

        if (!adapter.isEnabled) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        val locationManager = getSystemService(LocationManager::class.java)
        if (locationManager != null && !LocationManagerCompat.isLocationEnabled(locationManager)) {
            showMessage(R.string.location_off)
        }

        val newScanner = scanner ?: NearbyScanner(
            context = this,
            adapter = adapter,
            onDevicesChanged = { devices ->
                lastDevices = devices
                render()
            },
            onError = { code ->
                Snackbar.make(statusText, getString(R.string.scan_failed, code), Snackbar.LENGTH_LONG).show()
                stopScan()
            },
        ).also { scanner = it }

        newScanner.start()
        scanButton.setText(R.string.stop_scan)
        render()
    }

    private fun stopScan() {
        scanner?.stop()
        if (::scanButton.isInitialized) scanButton.setText(R.string.start_scan)
        render()
    }

    private fun render() {
        if (!::statusText.isInitialized) return
        val phones = lastDevices.count { it.classification.confidence.isPhone }
        val visible = lastDevices
            .filter { !phonesOnlySwitch.isChecked || it.classification.confidence.isPhone }
            .sortedWith(
                compareByDescending<NearbyDevice> { it.classification.confidence }
                    .thenByDescending { it.rssi },
            )
        deviceAdapter.submitList(visible)
        emptyText.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE

        statusText.text = when {
            scanner?.isScanning == true -> getString(R.string.status_scanning, phones, lastDevices.size)
            scanner != null -> getString(R.string.status_stopped, phones, lastDevices.size)
            else -> getString(R.string.status_idle)
        }
    }

    private fun showMessage(resId: Int) {
        Snackbar.make(statusText, resId, Snackbar.LENGTH_LONG).show()
    }
}
