package com.komugi.attacker.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

data class TargetDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val bondState: Int,
    val isBle: Boolean = true,
) {
    val isBonded: Boolean get() = bondState == BluetoothDevice.BOND_BONDED
    val label: String get() = when {
        name.isNotBlank() -> name
        isBonded -> "Paired ($address)"
        else -> "Unknown ($address)"
    }
}

/**
 * Scans for real nearby active devices:
 * 1. Reads bonded/paired devices directly from BluetoothAdapter (headphones, watches, cars, etc.).
 * 2. Starts Classic Bluetooth discovery (`startDiscovery`) to find active BR/EDR devices with friendly names.
 * 3. Starts BLE scan (`startScan`) to find advertising LE devices.
 * 4. Merges and deduplicates results by MAC address, prioritizing bonded and named devices.
 */
@SuppressLint("MissingPermission")
class TargetScanner(
    private val context: Context,
    private val onUpdate: (List<TargetDevice>) -> Unit,
) {

    private val devices = ConcurrentHashMap<String, TargetDevice>()
    private var running = false
    private var adapter: BluetoothAdapter? = null
    private var receiverRegistered = false

    private val classicReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                            ?: runCatching { device.name }.getOrNull().orEmpty()
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, -70).toInt()
                        ingestClassic(device, name, rssi)
                    }
                }
                BluetoothDevice.ACTION_NAME_CHANGED -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME).orEmpty()
                    if (device != null && name.isNotBlank()) {
                        val existing = devices[device.address]
                        if (existing != null) {
                            devices[device.address] = existing.copy(name = name)
                            notifyUpdate()
                        }
                    }
                }
            }
        }
    }

    private val bleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            ingestBle(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::ingestBle)
        }

        override fun onScanFailed(errorCode: Int) {
            SpamState.log("scan: BLE scan failed code=$errorCode")
        }
    }

    private fun ingestClassic(device: BluetoothDevice, discoveredName: String, rssi: Int) {
        val address = device.address ?: return
        val existing = devices[address]
        val name = when {
            discoveredName.isNotBlank() -> discoveredName
            existing?.name?.isNotBlank() == true -> existing.name
            else -> runCatching { device.name }.getOrNull().orEmpty()
        }
        val bond = runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
        devices[address] = TargetDevice(
            address = address,
            name = name,
            rssi = if (rssi != 0) rssi else (existing?.rssi ?: -70),
            bondState = bond,
            isBle = false,
        )
        notifyUpdate()
    }

    private fun ingestBle(result: ScanResult) {
        val device = result.device ?: return
        val address = device.address ?: return
        val existing = devices[address]
        val scanRecordName = runCatching { result.scanRecord?.deviceName }.getOrNull()
        val name = when {
            existing?.name?.isNotBlank() == true -> existing.name
            !scanRecordName.isNullOrBlank() -> scanRecordName
            else -> runCatching { device.name }.getOrNull().orEmpty()
        }
        val bond = runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
        devices[address] = TargetDevice(
            address = address,
            name = name,
            rssi = result.rssi,
            bondState = bond,
            isBle = true,
        )
        notifyUpdate()
    }

    private var lastNotifyTime = 0L
    private fun notifyUpdate() {
        val now = System.currentTimeMillis()
        if (now - lastNotifyTime > 300) {
            lastNotifyTime = now
            onUpdate(snapshot())
        }
    }

    fun snapshot(): List<TargetDevice> {
        return devices.values.sortedWith(
            compareByDescending<TargetDevice> { it.isBonded }
                .thenByDescending { it.name.isNotBlank() }
                .thenByDescending { it.rssi }
        )
    }

    fun devices(): List<BluetoothDevice> {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val ad = manager?.adapter ?: return emptyList()
        return devices.keys.mapNotNull { address ->
            runCatching { ad.getRemoteDevice(address) }.getOrNull()
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
        if (adapter?.isEnabled != true) {
            SpamState.log("scan: bluetooth is off")
            return
        }

        devices.clear()

        // 1. Load paired (bonded) devices immediately!
        runCatching {
            adapter?.bondedDevices?.forEach { device ->
                val name = runCatching { device.name }.getOrNull().orEmpty()
                devices[device.address] = TargetDevice(
                    address = device.address,
                    name = name,
                    rssi = -50,
                    bondState = BluetoothDevice.BOND_BONDED,
                    isBle = false,
                )
            }
        }
        onUpdate(snapshot())

        // 2. Register classic discovery receiver
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            }
            context.registerReceiver(classicReceiver, filter)
            receiverRegistered = true
            adapter?.startDiscovery()
        } catch (e: Exception) {
            SpamState.log("scan: classic discovery error: ${e.message}")
        }

        // 3. Start BLE scan
        val scanner = adapter?.bluetoothLeScanner
        if (scanner != null) {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()
            runCatching { scanner.startScan(null, settings, bleCallback) }
        }

        running = true
        SpamState.log("scan: listening for real devices (bonded=${devices.size})")
    }

    fun stop() {
        if (!running) return
        running = false

        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(classicReceiver) }
            receiverRegistered = false
        }
        runCatching { adapter?.cancelDiscovery() }
        runCatching { adapter?.bluetoothLeScanner?.stopScan(bleCallback) }

        onUpdate(snapshot())
        SpamState.log("scan: stopped, ${devices.size} devices found")
    }

    fun isRunning(): Boolean = running

    private fun hasScanPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED
    }
}
