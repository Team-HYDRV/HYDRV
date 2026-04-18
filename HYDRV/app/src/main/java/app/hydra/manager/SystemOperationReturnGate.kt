package app.hydra.manager

import android.os.SystemClock

object SystemOperationReturnGate {

    private val lock = Any()
    private var pendingReason: String? = null
    private var lastReason: String? = null
    private var lastMarkedAt = 0L

    fun mark(reason: String) {
        synchronized(lock) {
            val normalizedReason = reason.trim().ifBlank { "system_operation" }
            pendingReason = normalizedReason
            lastReason = normalizedReason
            lastMarkedAt = SystemClock.elapsedRealtime()
        }
    }

    fun consume(): String? {
        synchronized(lock) {
            val reason = pendingReason
            pendingReason = null
            return reason
        }
    }

    fun recentReason(windowMs: Long): String? {
        synchronized(lock) {
            if (SystemClock.elapsedRealtime() - lastMarkedAt > windowMs) return null
            return lastReason
        }
    }
}
