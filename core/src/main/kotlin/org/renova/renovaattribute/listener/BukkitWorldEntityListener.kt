package org.renova.renovaattribute.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.event.world.EntitiesUnloadEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.renova.renovaattribute.platform.PlatformListener

/**
 * Spigot has no add/remove-from-world events: spawns and chunk entity loads stand in for them,
 * and a periodic sweep drops entities that were removed without any event (despawns, plugins).
 */
class BukkitWorldEntityListener : PlatformListener {
    override val name: String = "bukkit"
    private var sweepTask: BukkitTask? = null

    override fun start(plugin: Plugin) {
        super.start(plugin)
        sweepTask?.cancel()
        sweepTask = plugin.server.scheduler.runTaskTimer(
            plugin,
            Runnable(EntityLifecycle::sweep),
            SWEEP_PERIOD_TICKS,
            SWEEP_PERIOD_TICKS,
        )
    }

    override fun shutdown() {
        sweepTask?.cancel()
        sweepTask = null
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSpawn(event: EntitySpawnEvent) {
        EntityLifecycle.added(event.entity)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onEntitiesLoad(event: EntitiesLoadEvent) {
        event.entities.forEach(EntityLifecycle::added)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onEntitiesUnload(event: EntitiesUnloadEvent) {
        event.entities.forEach(EntityLifecycle::removed)
    }

    private companion object {
        const val SWEEP_PERIOD_TICKS = 600L
    }
}
