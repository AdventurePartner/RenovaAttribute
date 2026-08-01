package org.renova.renovaattribute.hooks.mythicmobs

import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.core.AttributeRegistry
import org.renova.renovaattribute.core.AttributeServiceImpl

internal object MythicAttributeLookup {
    fun get(entity: Entity, key: AttributeKey): Double {
        if (Bukkit.isPrimaryThread()) {
            return AttributeServiceImpl.get(entity, key)
        }
        return AttributeServiceImpl.cachedSnapshot(entity)?.get(key)
            ?: AttributeRegistry[key]?.defaultValue
            ?: 0.0
    }
}
