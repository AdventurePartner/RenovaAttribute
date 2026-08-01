package org.renova.renovaattribute.core

import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.AttributeService
import org.renova.renovaattribute.api.AttributeSnapshot
import org.renova.renovaattribute.api.AttributeSource
import org.renova.renovaattribute.api.event.AttributeSnapshotUpdateEvent
import java.util.LinkedHashMap
import java.util.UUID

object AttributeServiceImpl : AttributeService {
    private val globalSources = LinkedHashMap<String, AttributeSource>()
    private val entitySources = LinkedHashMap<UUID, Map<String, AttributeSource>>()
    private val dispatchingEntities = mutableSetOf<UUID>()

    override fun snapshot(entity: Entity): AttributeSnapshot {
        ensureMainThread()
        return SnapshotCache.get(entity) ?: refresh(entity)
    }

    override fun cachedSnapshot(entity: Entity): AttributeSnapshot? = SnapshotCache.get(entity)

    override fun get(entity: Entity, key: AttributeKey): Double = snapshot(entity)[key]

    override fun registerSource(source: AttributeSource) {
        ensureMainThread()
        ensureNotDispatching()
        val candidate = LinkedHashMap(globalSources)
        candidate[source.id] = source
        val entities = SnapshotCache.entities()
        val snapshots = entities.associateWith { compute(it, candidate, entitySources[it.uniqueId]) }
        globalSources.clear()
        globalSources.putAll(candidate)
        commitBatch(snapshots)
    }

    override fun unregisterSource(sourceId: String): Boolean {
        ensureMainThread()
        ensureNotDispatching()
        if (sourceId !in globalSources) {
            return false
        }
        val candidate = LinkedHashMap(globalSources)
        candidate.remove(sourceId)
        val entities = SnapshotCache.entities()
        val snapshots = entities.associateWith { compute(it, candidate, entitySources[it.uniqueId]) }
        globalSources.clear()
        globalSources.putAll(candidate)
        commitBatch(snapshots)
        return true
    }

    override fun addSource(entity: Entity, source: AttributeSource) {
        ensureMainThread()
        ensureNotDispatching()
        val candidate = LinkedHashMap(entitySources[entity.uniqueId].orEmpty())
        require(source.id !in candidate) {
            "Attribute source '${source.id}' is already registered for ${entity.uniqueId}"
        }
        candidate[source.id] = source
        val snapshot = compute(entity, globalSources, candidate)
        entitySources[entity.uniqueId] = candidate.toMap()
        commitSnapshot(entity, snapshot)
    }

    override fun updateSource(entity: Entity, source: AttributeSource) {
        ensureMainThread()
        ensureNotDispatching()
        val candidate = LinkedHashMap(entitySources[entity.uniqueId].orEmpty())
        candidate[source.id] = source
        val snapshot = compute(entity, globalSources, candidate)
        entitySources[entity.uniqueId] = candidate.toMap()
        commitSnapshot(entity, snapshot)
    }

    override fun removeSource(entity: Entity, sourceId: String): Boolean {
        ensureMainThread()
        ensureNotDispatching()
        val current = entitySources[entity.uniqueId] ?: return false
        if (sourceId !in current) {
            return false
        }
        val candidate = LinkedHashMap(current)
        candidate.remove(sourceId)
        val snapshot = compute(entity, globalSources, candidate)
        if (candidate.isEmpty()) {
            entitySources.remove(entity.uniqueId)
        } else {
            entitySources[entity.uniqueId] = candidate.toMap()
        }
        commitSnapshot(entity, snapshot)
        return true
    }

    override fun sourceIds(entity: Entity): Set<String> {
        ensureMainThread()
        val result = linkedSetOf<String>()
        result += globalSources.keys
        entitySources[entity.uniqueId]?.keys?.let(result::addAll)
        return result
    }

    override fun refresh(entity: Entity): AttributeSnapshot {
        ensureMainThread()
        ensureNotDispatching()
        val current = compute(entity, globalSources, entitySources[entity.uniqueId])
        commitSnapshot(entity, current)
        return current
    }

    override fun refreshAll() {
        ensureMainThread()
        ensureNotDispatching()
        val snapshots = SnapshotCache.entities().associateWith { entity ->
            compute(entity, globalSources, entitySources[entity.uniqueId])
        }
        commitBatch(snapshots)
    }

    override fun invalidate(entity: Entity) {
        ensureMainThread()
        ensureNotDispatching()
        entitySources.remove(entity.uniqueId)
        SnapshotCache.remove(entity)
    }

    internal fun replaceSources(
        sourceId: String,
        replacements: Map<Entity, AttributeSource?>,
    ) {
        ensureMainThread()
        ensureNotDispatching()
        val candidates = LinkedHashMap<Entity, Map<String, AttributeSource>>()
        replacements.forEach { (entity, replacement) ->
            require(replacement == null || replacement.id == sourceId) {
                "Replacement source id must be '$sourceId'"
            }
            val candidate = LinkedHashMap(entitySources[entity.uniqueId].orEmpty())
            if (replacement == null) {
                candidate.remove(sourceId)
            } else {
                candidate[sourceId] = replacement
            }
            candidates[entity] = candidate.toMap()
        }
        val snapshots = candidates.mapValues { (entity, localSources) ->
            compute(entity, globalSources, localSources)
        }
        candidates.forEach { (entity, sources) ->
            if (sources.isEmpty()) {
                entitySources.remove(entity.uniqueId)
            } else {
                entitySources[entity.uniqueId] = sources
            }
        }
        commitBatch(snapshots)
    }

    fun shutdown() {
        ensureMainThread()
        globalSources.clear()
        entitySources.clear()
        dispatchingEntities.clear()
        SnapshotCache.clear()
    }

    private fun compute(
        entity: Entity,
        global: Map<String, AttributeSource>,
        local: Map<String, AttributeSource>?,
    ): AttributeSnapshot {
        val sources = ArrayList<AttributeSource>(global.size + (local?.size ?: 0))
        sources += global.values
        local?.values?.let(sources::addAll)
        return AttributeMerger.compute(entity, sources)
    }

    private fun commitBatch(snapshots: Map<Entity, AttributeSnapshot>) {
        val previous = snapshots.keys.associateWith(SnapshotCache::previous)
        snapshots.forEach(SnapshotCache::put)
        snapshots.forEach { (entity, current) ->
            publish(entity, previous[entity], current)
        }
    }

    private fun commitSnapshot(entity: Entity, current: AttributeSnapshot) {
        val previous = SnapshotCache.previous(entity)
        SnapshotCache.put(entity, current)
        publish(entity, previous, current)
    }

    private fun publish(
        entity: Entity,
        previous: AttributeSnapshot?,
        current: AttributeSnapshot,
    ) {
        if (previous?.values == current.values) {
            return
        }
        check(dispatchingEntities.add(entity.uniqueId)) {
            "Recursive attribute snapshot event for ${entity.uniqueId}"
        }
        try {
            Bukkit.getPluginManager().callEvent(
                AttributeSnapshotUpdateEvent(entity, previous, current),
            )
        } finally {
            dispatchingEntities.remove(entity.uniqueId)
        }
    }

    private fun ensureNotDispatching() {
        check(dispatchingEntities.isEmpty()) {
            "Attribute sources cannot be modified from AttributeSnapshotUpdateEvent"
        }
    }

    private fun ensureMainThread() {
        check(Bukkit.isPrimaryThread()) {
            "RenovaAttribute mutations and live snapshots require the server thread"
        }
    }
}
