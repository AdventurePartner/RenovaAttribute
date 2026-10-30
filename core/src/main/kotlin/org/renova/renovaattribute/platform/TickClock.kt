package org.renova.renovaattribute.platform

import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask

/** Server ticks counted since the plugin was enabled; Spigot exposes no tick counter. */
object TickClock {
    @Volatile
    private var ticks = 0L
    private var task: BukkitTask? = null

    fun now(): Long = ticks

    fun start(plugin: Plugin) {
        if (task == null) {
            task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { ticks++ }, 1L, 1L)
        }
    }

    fun stop() {
        task?.cancel()
        task = null
    }
}
