package org.renova.renovaattribute.hooks.mythicmobs.mechanic

import io.lumine.mythic.api.config.MythicLineConfig
import org.bukkit.entity.Entity
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.StatValue
import org.renova.renovaattribute.source.BuffSource

class SetAttributeMechanic(config: MythicLineConfig) : AbstractAttributeMechanic(config) {
    override fun apply(
        target: Entity,
        key: AttributeKey,
        value: Double,
        durationTicks: Long,
        tag: String,
    ) {
        BuffSource.upsert(target, key, StatValue.set(value), durationTicks, tag)
    }
}
