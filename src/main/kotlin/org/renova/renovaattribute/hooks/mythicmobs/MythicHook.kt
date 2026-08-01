package org.renova.renovaattribute.hooks.mythicmobs

import io.lumine.mythic.bukkit.events.MythicReloadedEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.renova.renovaattribute.RenovaAttribute
import org.renova.renovaattribute.hooks.mythicmobs.mechanic.MythicComponentRegistrar

object MythicHook : Listener {
    private val mobListener = MythicMobListener()
    private val componentRegistrar = MythicComponentRegistrar(false, false)
    private val damageListener = MythicDamageListener()
    private var initialized = false
    private var placeholdersEnabled = false

    @Volatile
    internal var mechanicsEnabled = false
        private set

    @Volatile
    internal var conditionEnabled = false
        private set

    fun isInitialized(): Boolean = initialized

    fun shutdown() {
        if (!initialized) {
            return
        }
        AttributePlaceholder.configure(false)
        mechanicsEnabled = false
        conditionEnabled = false
        componentRegistrar.configure(false, false)
        damageListener.enabled = false
        mobListener.configure(false, "RenovaAttribute", 50)
        HandlerList.unregisterAll(RenovaAttribute.instance)
        placeholdersEnabled = false
        initialized = false
    }

    fun enable(
        enabled: Boolean,
        configNode: String,
        sourcePriority: Int,
        placeholders: Boolean,
        mechanics: Boolean,
        condition: Boolean,
        damage: Boolean,
    ) {
        if (initialized) {
            configure(enabled, configNode, sourcePriority, placeholders, mechanics, condition, damage)
            return
        }
        val pluginManager = RenovaAttribute.instance.server.pluginManager
        val mythicMobs = pluginManager.getPlugin("MythicMobs")
        require(mythicMobs?.isEnabled == true) { "MythicMobs is not enabled" }

        pluginManager.registerEvents(this, RenovaAttribute.instance)
        pluginManager.registerEvents(mobListener, RenovaAttribute.instance)
        pluginManager.registerEvents(componentRegistrar, RenovaAttribute.instance)
        pluginManager.registerEvents(damageListener, RenovaAttribute.instance)
        initialized = true
        configure(enabled, configNode, sourcePriority, placeholders, mechanics, condition, damage)
    }

    fun configure(
        enabled: Boolean,
        configNode: String,
        sourcePriority: Int,
        placeholders: Boolean,
        mechanics: Boolean,
        condition: Boolean,
        damage: Boolean,
    ) {
        check(initialized) { "MythicHook has not been initialized" }
        val mobConfiguration = mobListener.prepareConfiguration(
            enabled,
            configNode,
            sourcePriority,
        )
        mobListener.commitConfiguration(mobConfiguration)
        placeholdersEnabled = enabled && placeholders
        mechanicsEnabled = enabled && mechanics
        conditionEnabled = enabled && condition
        AttributePlaceholder.configure(placeholdersEnabled)
        componentRegistrar.configure(mechanicsEnabled, conditionEnabled)
        damageListener.enabled = enabled && damage
        if (placeholdersEnabled) {
            AttributePlaceholder.register()
        }
        scheduleRebuilds()
    }

    @EventHandler
    fun onMythicReloaded(event: MythicReloadedEvent) {
        if (placeholdersEnabled) {
            AttributePlaceholder.register()
        }
        scheduleRebuilds()
    }

    private fun scheduleRebuilds() {
        RenovaAttribute.instance.server.scheduler.runTask(
            RenovaAttribute.instance,
            Runnable(mobListener::rebuildAll),
        )
        RenovaAttribute.instance.server.scheduler.runTaskLater(
            RenovaAttribute.instance,
            Runnable(mobListener::rebuildAll),
            20L,
        )
    }
}
