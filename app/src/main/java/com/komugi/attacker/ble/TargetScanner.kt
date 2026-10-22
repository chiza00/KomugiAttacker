package com.komugi.attacker.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

data class TargetDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val bondState: Int,
) {
    val label: String get() = if (name.isBlank()) "Unknown device" else name
}

/**
 * Passive LE scan used to build the target list and to feed the connect flood when no
 * explicit target is chosen. Results are kept in memory only.
 */
@SuppressLint("MissingPermission")
class TargetScanner(
    private val context: Context,
    private val onUpdate: (List<TargetDevice>) -> Unit,
) {

    private val devices = LinkedHashMap<String, TargetDevice>()
    private var running = false
    private var adapter: BluetoothAdapter? = null

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            ingest(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::ingest)
            onUpdate(snapshot())
        }

        override fun onScanFailed(errorCode: Int) {
            SpamState.log("scan: failed code=$errorCode")
        }
    }

    private fun ingest(result: ScanResult) {
        val device = result.device ?: return
        val previous = devices[device.address]
        val name = runCatching { device.name }.getOrNull()
            ?: previous?.name
            ?: result.scanRecord?.deviceName
            ?: ""
        devices[device.address] = TargetDevice(
            address = device.address,
            name = name,
            rssi = result.rssi,
            bondState = runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE),
        )
        onUpdate(snapshot())
    }

    fun snapshot(): List<TargetDevice> = devices.values.sortedByDescending { it.rssi }

    fun devices(): List<BluetoothDevice> {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: return emptyList()
        return devices.keys.mapNotNull { address ->
            runCatching { adapter.getRemoteDevice(address) }.getOrNull()
        }
    }

    fun start() {
        if (running) return
        if (!hasScanPermission()) {
            SpamState.log("scan: missing BLUETOOTH_SCAN permission")
            return
        }
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        adapter = manager?.adapter
        val scanner = adapter?.bluetoothLeScanner
        if (adapter?.isEnabled != true || scanner == null) {
            SpamState.log("scan: bluetooth is off")
            return
        }
        devices.clear()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        running = runCatching { scanner.startScan(null, settings, callback) }.isSuccess
        SpamState.log(if (running) "scan: listening" else "scan: start rejected")
    }

    fun stop() {
        if (!running) return
        running = false
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        runCatching { manager?.adapter?.bluetoothLeScanner?.stopScan(callback) }
        SpamState.log("scan: stopped, ${devices.size} devices cached")
    }

    fun isRunning(): Boolean = running

    private fun hasScanPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED
    }
}
