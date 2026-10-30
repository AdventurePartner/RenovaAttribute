package org.renova.renovaattribute.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent

class EntityLifecycleListener : Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        EntityLifecycle.scheduleRefresh(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRespawn(event: PlayerRespawnEvent) {
        EntityLifecycle.scheduleRefresh(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        EntityLifecycle.cleanup(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(event: EntityDeathEvent) {
        EntityLifecycle.cleanup(event.entity)
    }
}
