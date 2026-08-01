package org.renova.renovaattribute.hooks.mythicmobs.mechanic

import io.lumine.mythic.api.config.MythicLineConfig
import org.bukkit.entity.Entity
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.ModifierMode
import org.renova.renovaattribute.api.StatValue
import org.renova.renovaattribute.source.BuffSource

class AddAttributeMechanic(config: MythicLineConfig) : AbstractAttributeMechanic(config) {
    private val mode = parseMode(config)

    override fun apply(
        target: Entity,
        key: AttributeKey,
        value: Double,
        durationTicks: Long,
        tag: String,
    ) {
        BuffSource.add(target, key, StatValue.of(mode, value), durationTicks, tag)
    }

    private fun parseMode(config: MythicLineConfig): ModifierMode {
        val value = config.getString(arrayOf("mode", "m", "operation", "op"), "FLAT")
        return ModifierMode.entries.firstOrNull { it.name.equals(value, true) }
            ?: throw IllegalArgumentException("Invalid attribute modifier mode: $value")
    }
}
