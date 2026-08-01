package org.renova.renovaattribute.listener

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.renova.renovaattribute.RenovaAttribute
import org.renova.renovaattribute.core.AttributeServiceImpl
import org.renova.renovaattribute.source.BuffSource
import org.renova.renovaattribute.sync.VanillaAttributeSync

class EntityLifecycleListener : Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onAddToWorld(event: EntityAddToWorldEvent) {
        val entity = event.entity as? LivingEntity ?: return
        scheduleRefresh(entity)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRemoveFromWorld(event: EntityRemoveFromWorldEvent) {
        (event.entity as? LivingEntity)?.let(::cleanup)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        scheduleRefresh(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRespawn(event: PlayerRespawnEvent) {
        scheduleRefresh(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        cleanup(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(event: EntityDeathEvent) {
        cleanup(event.entity)
    }

    private fun scheduleRefresh(entity: LivingEntity) {
        RenovaAttribute.instance.server.scheduler.runTask(
            RenovaAttribute.instance,
            Runnable {
                if (entity.isValid) {
                    AttributeServiceImpl.refresh(entity)
                }
            },
        )
    }

    private fun cleanup(entity: LivingEntity) {
        BuffSource.discard(entity)
        VanillaAttributeSync.removeModifiers(entity)
        AttributeServiceImpl.invalidate(entity)
    }
}
