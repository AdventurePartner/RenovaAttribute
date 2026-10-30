package org.renova.renovaattribute.listener

import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.renova.renovaattribute.RenovaAttribute
import org.renova.renovaattribute.core.AttributeServiceImpl
import org.renova.renovaattribute.damage.CombatCooldowns
import org.renova.renovaattribute.source.BuffSource
import org.renova.renovaattribute.sync.VanillaAttributeSync
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Entities entering and leaving the loaded world, as reported by the platform's world entity listener. */
object EntityLifecycle {
    private val addedHandlers = CopyOnWriteArrayList<(Entity) -> Unit>()

    fun onAdded(handler: (Entity) -> Unit) {
        addedHandlers += handler
    }

    fun removeAddedHandler(handler: (Entity) -> Unit) {
        addedHandlers -= handler
    }

    fun added(entity: Entity) {
        (entity as? LivingEntity)?.let(::scheduleRefresh)
        addedHandlers.forEach { it(entity) }
    }

    fun removed(entity: Entity) {
        (entity as? LivingEntity)?.let(::cleanup)
    }

    fun scheduleRefresh(entity: LivingEntity) {
        RenovaAttribute.instance.server.scheduler.runTask(
            RenovaAttribute.instance,
            Runnable {
                if (entity.isValid) {
                    AttributeServiceImpl.refresh(entity)
                }
            },
        )
    }

    fun cleanup(entity: LivingEntity) {
        BuffSource.discard(entity)
        CombatCooldowns.shared.discard(entity.uniqueId)
        VanillaAttributeSync.removeModifiers(entity)
        AttributeServiceImpl.invalidate(entity)
    }

    /** Drops the state of entities that left the world without an event announcing it. */
    fun sweep() {
        val tracked = AttributeServiceImpl.trackedIds() + BuffSource.trackedIds()
        tracked.filter { Bukkit.getEntity(it) == null }.forEach(::forget)
    }

    private fun forget(uniqueId: UUID) {
        BuffSource.discard(uniqueId)
        CombatCooldowns.shared.discard(uniqueId)
        VanillaAttributeSync.forget(uniqueId)
        AttributeServiceImpl.invalidate(uniqueId)
    }
}
