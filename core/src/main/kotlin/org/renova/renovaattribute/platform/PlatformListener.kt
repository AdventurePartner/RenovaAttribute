package org.renova.renovaattribute.platform

import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin

/** A listener whose events only exist on some servers; [Platform] picks one implementation. */
interface PlatformListener : Listener {
    val name: String

    fun start(plugin: Plugin) {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun shutdown() {}
}

/** Detects equipment changes and passes the entity to the equipment refresh scheduler. */
interface EquipmentWatcher : PlatformListener {
    /** Applied on enable and on every reload. */
    fun configure(pollIntervalTicks: Long) {}
}
