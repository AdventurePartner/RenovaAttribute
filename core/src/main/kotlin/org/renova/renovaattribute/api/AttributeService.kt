package org.renova.renovaattribute.api

import org.bukkit.entity.Entity

interface AttributeService {
    fun snapshot(entity: Entity): AttributeSnapshot

    fun cachedSnapshot(entity: Entity): AttributeSnapshot?

    fun get(entity: Entity, key: AttributeKey): Double = snapshot(entity)[key]

    fun registerSource(source: AttributeSource)

    fun unregisterSource(sourceId: String): Boolean

    fun addSource(entity: Entity, source: AttributeSource)

    fun updateSource(entity: Entity, source: AttributeSource)

    fun removeSource(entity: Entity, sourceId: String): Boolean

    fun sourceIds(entity: Entity): Set<String>

    fun refresh(entity: Entity): AttributeSnapshot

    fun refreshAll()

    fun invalidate(entity: Entity)
}
