package org.renova.renovaattribute.util

import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

object RenovaLog {
    private const val THROTTLE_NANOS = 60_000_000_000L

    @Volatile
    var logger: Logger = Logger.getLogger("RenovaAttribute")

    private val lastReported = ConcurrentHashMap<String, Long>()
    private val suppressed = ConcurrentHashMap<String, Int>()

    /**
     * Logs at most once per [key] per minute; the next report states how many were suppressed.
     */
    fun throttled(key: String, message: String, error: Throwable? = null) {
        val now = System.nanoTime()
        val last = lastReported[key]
        if (last != null && now - last < THROTTLE_NANOS) {
            suppressed.merge(key, 1, Int::plus)
            return
        }
        lastReported[key] = now
        val skipped = suppressed.remove(key) ?: 0
        val suffix = if (skipped > 0) " ($skipped similar errors suppressed)" else ""
        if (error == null) {
            logger.warning(message + suffix)
        } else {
            logger.log(Level.WARNING, message + suffix, error)
        }
    }

    fun reset() {
        lastReported.clear()
        suppressed.clear()
    }
}
