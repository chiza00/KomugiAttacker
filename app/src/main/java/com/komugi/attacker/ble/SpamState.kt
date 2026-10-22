package com.komugi.attacker.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SpamMode(val title: String, val subtitle: String) {
    ADVERTISE("Broadcast", "Pairing popups to every device in range"),
    CONNECT("Connect flood", "Repeated link attempts against one target"),
}

data class SpamConfig(
    val mode: SpamMode = SpamMode.ADVERTISE,
    val family: PayloadFamily = PayloadFamily.APPLE,
    val customHex: String = "",
    val intervalMs: Int = 100,
    val threads: Int = 6,
    val targetAddress: String? = null,
    val targetName: String? = null,
    val cycleAllFamilies: Boolean = true,
) {
    val effectiveInterval: Long get() = intervalMs.coerceIn(MIN_INTERVAL, MAX_INTERVAL).toLong()
    val effectiveThreads: Int get() = threads.coerceIn(MIN_THREADS, MAX_THREADS)

    companion object {
        const val MIN_INTERVAL = 20
        const val MAX_INTERVAL = 2000
        const val MIN_THREADS = 1
        const val MAX_THREADS = 16
    }
}

data class SpamSnapshot(
    val running: Boolean = false,
    val mode: SpamMode = SpamMode.ADVERTISE,
    val packets: Long = 0,
    val failures: Long = 0,
    val targetLabel: String = "Broadcast",
    val currentPayload: String = "",
    val startedAtMillis: Long = 0L,
    val log: List<String> = emptyList(),
)

/**
 * Process wide view of the spam service. The service writes, the UI reads, nothing else
 * touches it. Log is bounded so a long session cannot grow the heap.
 */
object SpamState {

    private const val MAX_LOG_LINES = 250

    private val _snapshot = MutableStateFlow(SpamSnapshot())
    val snapshot: StateFlow<SpamSnapshot> = _snapshot.asStateFlow()

    fun start(mode: SpamMode, targetLabel: String) = _snapshot.update {
        it.copy(
            running = true,
            mode = mode,
            targetLabel = targetLabel,
            packets = 0,
            failures = 0,
            startedAtMillis = System.currentTimeMillis(),
            currentPayload = "",
            log = emptyList(),
        )
    }

    fun stop() = _snapshot.update { it.copy(running = false, currentPayload = "") }

    fun packet(label: String) = _snapshot.update {
        it.copy(packets = it.packets + 1, currentPayload = label)
    }

    fun failure(label: String) = _snapshot.update {
        it.copy(failures = it.failures + 1, currentPayload = label)
    }

    fun log(line: String) = _snapshot.update {
        val next = it.log.toMutableList()
        next.add(line)
        while (next.size > MAX_LOG_LINES) next.removeAt(0)
        it.copy(log = next)
    }

    fun setTargetLabel(label: String) = _snapshot.update { it.copy(targetLabel = label) }
}
