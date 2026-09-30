package org.renova.renovaattribute.damage

import java.util.UUID

/** Per-entity named cooldowns for combat handlers, measured in server ticks. */
class CombatCooldowns {
    private val entries = HashMap<UUID, HashMap<String, Long>>()
    private var lastPurgeTick = Long.MIN_VALUE

    /** Returns true and restarts the cooldown when it is ready; returns false while it is still running. */
    fun tryUse(owner: UUID, name: String, ticks: Long, now: Long): Boolean {
        require(name.isNotBlank()) { "cooldown name cannot be blank" }
        require(ticks >= 0) { "cooldown ticks cannot be negative" }
        purgeExpired(now)
        val cooldowns = entries.getOrPut(owner) { HashMap() }
        val readyAt = cooldowns[name]
        if (readyAt != null && now < readyAt) {
            return false
        }
        if (ticks == 0L) {
            cooldowns.remove(name)
        } else {
            cooldowns[name] = now + ticks
        }
        if (cooldowns.isEmpty()) {
            entries.remove(owner)
        }
        return true
    }

    fun discard(owner: UUID) {
        entries.remove(owner)
    }

    fun clear() {
        entries.clear()
    }

    private fun purgeExpired(now: Long) {
        if (lastPurgeTick != Long.MIN_VALUE && now - lastPurgeTick < PURGE_INTERVAL_TICKS) {
            return
        }
        lastPurgeTick = now
        entries.values.forEach { cooldowns -> cooldowns.values.removeIf { it <= now } }
        entries.values.removeIf { it.isEmpty() }
    }

    companion object {
        private const val PURGE_INTERVAL_TICKS = 1_200L

        @JvmStatic
        val shared = CombatCooldowns()
    }
}
