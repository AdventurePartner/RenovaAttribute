package org.renova.renovaattribute.damage

import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.renova.renovaattribute.RenovaAttribute
import org.renova.renovaattribute.core.AttributeServiceImpl
import org.renova.renovaattribute.util.RenovaLog
import java.util.concurrent.ThreadLocalRandom
import java.util.random.RandomGenerator

/** Server-side glue for the listeners: live snapshots, scheduling and the Bukkit effect executor. */
internal object CombatRuntime {
    private var warnedOffThread = false

    val environment = CombatEnvironment(
        random = RandomGenerator { ThreadLocalRandom.current().nextLong() },
        clock = { Bukkit.getCurrentTick().toLong() },
        cooldowns = CombatCooldowns.shared,
        actions = BukkitCombatActions,
    )

    fun participant(entity: LivingEntity): CombatParticipant =
        EntityParticipant(entity, AttributeServiceImpl.snapshot(entity))

    /** Attribute snapshots and Lua are main-thread only; asynchronous hits keep their vanilla damage. */
    fun onPrimaryThread(): Boolean {
        if (Bukkit.isPrimaryThread()) {
            return true
        }
        if (!warnedOffThread) {
            warnedOffThread = true
            RenovaLog.logger.warning("Skipping RenovaAttribute damage processing for a hit fired off the server thread")
        }
        return false
    }

    /**
     * Called once the vanilla damage event finished. Queued effects of a hit that we cancelled
     * (e.g. a dodge message) still run; a hit cancelled by someone else drops them.
     */
    fun finish(session: DamageSession, eventCancelled: Boolean, finalDamage: Double) {
        if (eventCancelled && !session.cancelled) {
            DamagePipeline.discard(session)
            return
        }
        val landed = !eventCancelled
        if (landed) {
            session.finalDamage = finalDamage
        }
        RenovaAttribute.instance.server.scheduler.runTask(
            RenovaAttribute.instance,
            Runnable { DamagePipeline.settle(session, landed) },
        )
    }
}
