package com.komugi.attacker

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.komugi.attacker.ble.PayloadFamily
import com.komugi.attacker.ble.Payloads
import com.komugi.attacker.ble.SpamConfig
import com.komugi.attacker.ble.SpamMode
import com.komugi.attacker.ble.SpamService
import com.komugi.attacker.ble.SpamState
import com.komugi.attacker.ble.TargetDevice
import com.komugi.attacker.ble.TargetScanner

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("komugi", Context.MODE_PRIVATE)

    var config by mutableStateOf(loadConfig())
        private set

    var targets by mutableStateOf<List<TargetDevice>>(emptyList())
        private set

    var scanning by mutableStateOf(false)
        private set

    var customHex by mutableStateOf("")
        private set

    var customHexError by mutableStateOf<String?>(null)
        private set

    private var scanner: TargetScanner? = null

    val snapshot = SpamState.snapshot

    fun setMode(mode: SpamMode) {
        config = config.copy(mode = mode)
        persist()
    }

    fun setFamily(family: PayloadFamily) {
        config = config.copy(family = family)
        persist()
    }

    fun setInterval(value: Int) {
        config = config.copy(intervalMs = value)
        persist()
    }

    fun setThreads(value: Int) {
        config = config.copy(threads = value)
        persist()
    }

    fun setCycleAll(value: Boolean) {
        config = config.copy(cycleAllFamilies = value)
        persist()
    }

    fun updateCustomHex(text: String) {
        customHex = text.uppercase()
        val parsed = Payloads.parseCustom(customHex, null)
        customHexError = when {
            customHex.isBlank() -> null
            parsed == null -> "Need even hex, at least 6 chars, max 24 payload bytes"
            else -> null
        }
        config = config.copy(customHex = if (parsed != null) customHex else config.customHex)
    }

    fun useCustomPayload(enabled: Boolean) {
        if (enabled) {
            val parsed = Payloads.parseCustom(customHex, null)
            if (parsed == null) {
                customHexError = "Paste a valid hex payload first"
                return
            }
            config = config.copy(family = PayloadFamily.CUSTOM, customHex = customHex)
        } else {
            config = config.copy(family = PayloadFamily.APPLE, customHex = "")
        }
        persist()
    }

    fun selectTarget(target: TargetDevice?) {
        config = config.copy(
            targetAddress = target?.address,
            targetName = target?.label,
        )
        persist()
    }

    fun toggleScan() {
        if (scanning) {
            scanner?.stop()
            scanning = false
            return
        }
        val newScanner = TargetScanner(getApplication()) { list ->
            targets = list
        }
        scanner = newScanner
        newScanner.start()
        scanning = true
    }

    fun stopScan() {
        scanner?.stop()
        scanning = false
    }

    fun start() {
        SpamService.start(getApplication(), config)
    }

    fun stop() {
        SpamService.stop(getApplication())
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }

    private fun persist() {
        prefs.edit()
            .putString(KEY_MODE, config.mode.name)
            .putString(KEY_FAMILY, config.family.name)
            .putInt(KEY_INTERVAL, config.intervalMs)
            .putInt(KEY_THREADS, config.threads)
            .putBoolean(KEY_CYCLE, config.cycleAllFamilies)
            .putString(KEY_TARGET_MAC, config.targetAddress)
            .putString(KEY_TARGET_NAME, config.targetName)
            .apply()
    }

    private fun loadConfig(): SpamConfig {
        val mode = prefs.getString(KEY_MODE, null)
            ?.let { runCatching { SpamMode.valueOf(it) }.getOrNull() }
            ?: SpamMode.ADVERTISE
        val family = prefs.getString(KEY_FAMILY, null)
            ?.let { runCatching { PayloadFamily.valueOf(it) }.getOrNull() }
            ?: PayloadFamily.APPLE
        return SpamConfig(
            mode = mode,
            family = family,
            intervalMs = prefs.getInt(KEY_INTERVAL, 100),
            threads = prefs.getInt(KEY_THREADS, 6),
            cycleAllFamilies = prefs.getBoolean(KEY_CYCLE, true),
            targetAddress = prefs.getString(KEY_TARGET_MAC, null),
            targetName = prefs.getString(KEY_TARGET_NAME, null),
        )
    }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_FAMILY = "family"
        const val KEY_INTERVAL = "interval"
        const val KEY_THREADS = "threads"
        const val KEY_CYCLE = "cycle"
        const val KEY_TARGET_MAC = "target_mac"
        const val KEY_TARGET_NAME = "target_name"
    }
}
