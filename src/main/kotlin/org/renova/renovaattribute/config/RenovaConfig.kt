package org.renova.renovaattribute.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.event.entity.EntityDamageEvent
import org.renova.renovaattribute.damage.BuiltinPriorities
import org.renova.renovaattribute.damage.DamageSettings
import org.renova.renovaattribute.damage.ScriptErrorPolicy
import org.renova.renovaattribute.damage.VanillaReduction

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

    // Keys added after the first release are optional so existing config.yml files keep loading.
    val vanillaReduction = optionalEnum(config, "damage.vanilla-reduction", VanillaReduction.KEEP)
    val sweepRatio = optionalNonNegativeDouble(config, "damage.sweep-ratio", 1.0)
    val scaleByAttackCooldown = optionalBoolean(config, "damage.scale-by-attack-cooldown", false)
    val scriptErrorPolicy = optionalEnum(config, "damage.script-error-policy", ScriptErrorPolicy.SKIP_HANDLER)
    val builtinPriorities = builtinPriorities(config, "damage.builtin-priorities")

    val syncMaxHealth = boolean(config, "vanilla-sync.max-health")
    val syncMovementSpeed = boolean(config, "vanilla-sync.movement-speed")

    val maxLuaInstructions = positiveInt(config, "lua.max-instructions-per-execution")
    val maxCombatLuaInstructions = optionalPositiveInt(config, "lua.max-instructions-per-combat-script", 20_000)

    val mythicEnabled = boolean(config, "mythicmobs.enabled")
    val mythicConfigNode = string(config, "mythicmobs.config-node")
    val mythicSourcePriority = integer(config, "mythicmobs.source-priority")
    val mythicPlaceholders = boolean(config, "mythicmobs.register-placeholders")
    val mythicMechanics = boolean(config, "mythicmobs.register-mechanics")
    val mythicCondition = boolean(config, "mythicmobs.register-condition")
    val mythicDamage = boolean(config, "mythicmobs.process-damage")
    val mythicIgnoreArmorBypassesDefense = optionalBoolean(config, "mythicmobs.ignore-armor-bypasses-defense", false)

    val damageSettings = DamageSettings(
        enabled = damageEnabled,
        useVanillaBaseDamage = useVanillaBaseDamage,
        defenseConstant = defenseConstant,
        criticalHits = criticalHits,
        trueDamage = trueDamage,
        lifesteal = lifesteal,
        vanillaReduction = vanillaReduction,
        sweepRatio = sweepRatio,
        scaleByAttackCooldown = scaleByAttackCooldown,
        ignoreArmorBypassesDefense = mythicIgnoreArmorBypassesDefense,
        scriptErrorPolicy = scriptErrorPolicy,
    )

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

    private fun optionalBoolean(config: FileConfiguration, path: String, default: Boolean): Boolean =
        if (config.isSet(path)) boolean(config, path) else default

    private fun optionalPositiveInt(config: FileConfiguration, path: String, default: Int): Int =
        if (config.isSet(path)) positiveInt(config, path) else default

    private fun optionalNonNegativeDouble(config: FileConfiguration, path: String, default: Double): Double {
        if (!config.isSet(path)) {
            return default
        }
        require(config.isDouble(path) || config.isInt(path) || config.isLong(path)) { "$path must be a number" }
        return config.getDouble(path).also {
            require(it.isFinite() && it >= 0.0) { "$path must be finite and non-negative" }
        }
    }

    private inline fun <reified T : Enum<T>> optionalEnum(config: FileConfiguration, path: String, default: T): T {
        if (!config.isSet(path)) {
            return default
        }
        val value = string(config, path)
        return enumValues<T>().firstOrNull { it.name.equals(value.replace('-', '_'), true) }
            ?: throw IllegalArgumentException(
                "$path must be one of ${enumValues<T>().joinToString { it.name }}",
            )
    }

    private fun builtinPriorities(config: FileConfiguration, path: String): BuiltinPriorities {
        if (!config.isSet(path)) {
            return BuiltinPriorities()
        }
        val section = requireNotNull(config.getConfigurationSection(path)) { "$path must be a section" }
        val overrides = section.getKeys(false).associateWith { name -> integer(config, "$path.$name") }
        return BuiltinPriorities.of(overrides)
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
