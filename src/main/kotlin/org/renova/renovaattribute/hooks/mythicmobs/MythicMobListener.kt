package org.renova.renovaattribute.hooks.mythicmobs

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
import io.lumine.mythic.api.config.MythicConfig
import io.lumine.mythic.api.mobs.MythicMob
import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent
import io.lumine.mythic.bukkit.events.MythicMobDespawnEvent
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.renova.renovaattribute.RenovaAttribute
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.ModifierMode
import org.renova.renovaattribute.api.StatValue
import org.renova.renovaattribute.core.AttributeRegistry
import org.renova.renovaattribute.core.AttributeServiceImpl
import org.renova.renovaattribute.source.BuffSource
import org.renova.renovaattribute.sync.VanillaAttributeSync

class MythicMobListener : Listener {
    internal data class PreparedConfiguration(
        val enabled: Boolean,
        val configNode: String,
        val sourcePriority: Int,
        val replacements: Map<Entity, MythicMobConfigSource?>,
    )

    private var enabled = false
    private var configNode = "RenovaAttribute"
    private var sourcePriority = 50

    fun configure(enabled: Boolean, configNode: String, sourcePriority: Int) {
        commitConfiguration(prepareConfiguration(enabled, configNode, sourcePriority))
    }

    fun rebuildAll() {
        configure(enabled, configNode, sourcePriority)
    }

    internal fun prepareConfiguration(
        enabled: Boolean,
        configNode: String,
        sourcePriority: Int,
    ): PreparedConfiguration {
        val replacements = linkedMapOf<Entity, MythicMobConfigSource?>()
        MythicBukkit.inst().mobManager.activeMobs.forEach { activeMob ->
            val entity = activeMob.entity.bukkitEntity
            if (entity.isValid) {
                replacements[entity] = if (enabled) {
                    createSource(entity, activeMob.type, configNode, sourcePriority)
                } else null
            }
        }
        return PreparedConfiguration(enabled, configNode, sourcePriority, replacements)
    }

    internal fun commitConfiguration(configuration: PreparedConfiguration) {
        AttributeServiceImpl.replaceSources(
            MythicMobConfigSource.ID,
            configuration.replacements,
        )
        enabled = configuration.enabled
        configNode = configuration.configNode
        sourcePriority = configuration.sourcePriority
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSpawn(event: MythicMobSpawnEvent) {
        if (enabled) {
            scheduleAttach(event.entity, event.mobType)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onAddToWorld(event: EntityAddToWorldEvent) {
        if (!enabled) {
            return
        }
        val entity = event.entity
        RenovaAttribute.instance.server.scheduler.runTask(
            RenovaAttribute.instance,
            Runnable {
                if (!entity.isValid) {
                    return@Runnable
                }
                MythicBukkit.inst().mobManager.getActiveMob(entity.uniqueId)
                    .ifPresent { activeMob -> attach(entity, activeMob.type) }
            },
        )
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(event: MythicMobDeathEvent) {
        cleanup(event.entity)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDespawn(event: MythicMobDespawnEvent) {
        cleanup(event.entity)
    }

    private fun scheduleAttach(entity: Entity, mobType: MythicMob) {
        RenovaAttribute.instance.server.scheduler.runTask(
            RenovaAttribute.instance,
            Runnable {
                if (entity.isValid && enabled) {
                    attach(entity, mobType)
                }
            },
        )
    }

    private fun attach(entity: Entity, mobType: MythicMob) {
        val source = createSource(entity, mobType, configNode, sourcePriority)
        if (source == null) {
            removeConfigSource(entity)
            return
        }
        AttributeServiceImpl.updateSource(entity, source)
    }

    private fun createSource(
        entity: Entity,
        mobType: MythicMob,
        configNode: String,
        sourcePriority: Int,
    ): MythicMobConfigSource? {
        val config = mobType.config
        if (!config.isConfigurationSection(configNode)) {
            return null
        }
        val values = readAttributes(config.getNestedConfig(configNode))
        if (values.isEmpty()) {
            return null
        }
        return MythicMobConfigSource(entity.uniqueId, values, sourcePriority)
    }

    private fun removeConfigSource(entity: Entity) {
        if (MythicMobConfigSource.ID in AttributeServiceImpl.sourceIds(entity)) {
            AttributeServiceImpl.removeSource(entity, MythicMobConfigSource.ID)
        }
    }

    private fun readAttributes(config: MythicConfig): Map<AttributeKey, StatValue> = buildMap {
        config.keys.forEach { rawKey ->
            val key = AttributeKey.parse(rawKey, AttributeRegistry.defaultNamespace)
            val value = if (config.isConfigurationSection(rawKey)) {
                readContribution(config.getNestedConfig(rawKey))
            } else {
                StatValue.set(config.getDouble(rawKey))
            }
            put(key, value)
        }
    }

    private fun readContribution(config: MythicConfig): StatValue {
        val keys = config.keys
        val mode = config.getString("mode")
        if (mode != null) {
            val parsedMode = ModifierMode.entries.firstOrNull { it.name.equals(mode, true) }
                ?: throw IllegalArgumentException("Invalid MythicMobs attribute mode: $mode")
            return StatValue.of(parsedMode, config.getDouble("value"))
        }
        return StatValue(
            base = config.getDouble("base", 0.0),
            flat = config.getDouble("flat", 0.0),
            percent = config.getDouble("percent", 0.0),
            multiplier = 1.0 + config.getDouble("multiply", 0.0),
            setValue = if ("set" in keys) config.getDouble("set") else null,
        )
    }

    private fun cleanup(entity: Entity) {
        BuffSource.discard(entity)
        (entity as? LivingEntity)?.let(VanillaAttributeSync::removeModifiers)
        AttributeServiceImpl.invalidate(entity)
    }
}
