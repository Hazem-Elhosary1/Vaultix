package com.vaultix.app.debug

import com.vaultix.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ── Debug Event Model ──────────────────────────────────────────────────────────

data class DebugEvent(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val category: DebugCategory,
    val eventType: String,
    val details: String,
    val severity: DebugSeverity = DebugSeverity.INFO,
    val source: String = ""
)

enum class DebugCategory(val label: String) {
    AUTH("AUTH"),
    NAVIGATION("NAV"),
    CRUD("DATA"),
    FILE("FILE"),
    SECURITY("SEC"),
    SYSTEM("SYS"),
    CRYPTO("KEY")
}

enum class DebugSeverity(val label: String) {
    INFO("INFO"),
    WARNING("WARN"),
    CRITICAL("CRIT")
}

// ── Singleton Event Bus ────────────────────────────────────────────────────────

object DebugEventBus {

    private const val MAX_EVENTS = 500

    private val _events = MutableStateFlow<List<DebugEvent>>(emptyList())
    val events: StateFlow<List<DebugEvent>> = _events.asStateFlow()

    /**
     * Emit a debug event. No-op in release builds.
     */
    fun log(
        category: DebugCategory,
        eventType: String,
        details: String,
        severity: DebugSeverity = DebugSeverity.INFO,
        source: String = ""
    ) {
        if (!BuildConfig.DEBUG) return

        val event = DebugEvent(
            category = category,
            eventType = eventType,
            details = details,
            severity = severity,
            source = source
        )

        synchronized(this) {
            val current = _events.value.toMutableList()
            current.add(event)
            if (current.size > MAX_EVENTS) {
                current.removeAt(0)
            }
            _events.value = current
        }
    }

    /** Clear all buffered events. No-op in release builds. */
    fun clear() {
        if (!BuildConfig.DEBUG) return
        _events.value = emptyList()
    }
}
