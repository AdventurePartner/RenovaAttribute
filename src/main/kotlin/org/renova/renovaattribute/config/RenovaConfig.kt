package org.renova.renovaattribute.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.event.entity.EntityDamageEvent

class RenovaConfig(config: FileConfiguration) {
    val debug = boolean(config, "debug")

    val namespace = string(config, "attributes.namespace")
    val customAttributeDirectory = string(config, "attributes.custom-directory")

    val equipmentLoreEnabled = boolean(config, "equipment-lore.enabled")
    val equipmentRefreshDelayTicks = positiveLong(config, "equipment-lore.refresh-delay-ticks")
    val equipmentLorePriority = integer(config, "equipment-lore.priority")

    val buffCleanupPeriodTicks = positiveLong(config, "buffs.cleanup-period-ticks")
    val buffPriority = integer(config, "buffs.priority")

    val damageEnabled = boolean(config, "damage.enabled")
    val useVanillaBaseDamage = boolean(config, "damage.use-vanilla-base-damage")
    val defenseConstant = positiveDouble(config, "damage.defense-constant")
    val criticalHits = boolean(config, "damage.critical-hits")
    val trueDamage = boolean(config, "damage.true-damage")
    val lifesteal = boolean(config, "damage.lifesteal")
    val physicalCauses = causes(config, "damage.physical-causes")
    val magicCauses = causes(config, "damage.magic-causes")

    val syncMaxHealth = boolean(config, "vanilla-sync.max-health")
    val syncMovementSpeed = boolean(config, "vanilla-sync.movement-speed")

    val maxLuaInstructions = positiveInt(config, "lua.max-instructions-per-execution")

    val mythicEnabled = boolean(config, "mythicmobs.enabled")
    val mythicConfigNode = string(config, "mythicmobs.config-node")
    val mythicSourcePriority = integer(config, "mythicmobs.source-priority")
    val mythicPlaceholders = boolean(config, "mythicmobs.register-placeholders")
    val mythicMechanics = boolean(config, "mythicmobs.register-mechanics")
    val mythicCondition = boolean(config, "mythicmobs.register-condition")
    val mythicDamage = boolean(config, "mythicmobs.process-damage")

    init {
        require(namespace.matches(Regex("[a-z0-9.-]+"))) {
            "attributes.namespace contains invalid characters"
        }
        require(namespace == "renova") {
            "attributes.namespace must remain 'renova'; custom attributes may declare their own namespace"
        }
        require(physicalCauses.intersect(magicCauses).isEmpty()) {
            "damage.physical-causes and damage.magic-causes cannot overlap"
        }
    }

    private fun boolean(config: FileConfiguration, path: String): Boolean {
        require(config.isBoolean(path)) { "$path must be a boolean" }
        return config.getBoolean(path)
    }

    private fun string(config: FileConfiguration, path: String): String {
        require(config.isString(path)) { "$path must be a string" }
        return requireNotNull(config.getString(path)).also {
            require(it.isNotBlank()) { "$path cannot be blank" }
        }
    }

    private fun integer(config: FileConfiguration, path: String): Int {
        require(config.isInt(path)) { "$path must be an integer" }
        return config.getInt(path)
    }

    private fun positiveInt(config: FileConfiguration, path: String): Int =
        integer(config, path).also { require(it > 0) { "$path must be positive" } }

    private fun positiveLong(config: FileConfiguration, path: String): Long {
        require(config.isInt(path) || config.isLong(path)) { "$path must be an integer" }
        return config.getLong(path).also { require(it > 0) { "$path must be positive" } }
    }

    private fun positiveDouble(config: FileConfiguration, path: String): Double {
        require(config.isDouble(path) || config.isInt(path) || config.isLong(path)) {
            "$path must be a number"
        }
        return config.getDouble(path).also {
            require(it.isFinite() && it > 0.0) { "$path must be finite and positive" }
        }
    }

    private fun causes(
        config: FileConfiguration,
        path: String,
    ): Set<EntityDamageEvent.DamageCause> {
        require(config.isList(path)) { "$path must be a list" }
        return config.getStringList(path).mapTo(linkedSetOf()) { name ->
            EntityDamageEvent.DamageCause.entries.firstOrNull { it.name.equals(name, true) }
                ?: throw IllegalArgumentException("Unknown damage cause '$name' in $path")
        }
    }
}
