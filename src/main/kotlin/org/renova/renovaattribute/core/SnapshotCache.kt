package org.renova.renovaattribute.core

import org.bukkit.entity.Entity
import org.renova.renovaattribute.api.AttributeSnapshot
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SnapshotCache {
    private data class Entry(
        val entity: WeakReference<Entity>,
        var snapshot: AttributeSnapshot,
        var dirty: Boolean,
    )

    private val entries = ConcurrentHashMap<UUID, Entry>()

    fun get(entity: Entity): AttributeSnapshot? {
        val entry = entries[entity.uniqueId] ?: return null
        return entry.snapshot.takeUnless { entry.dirty }
    }

    fun previous(entity: Entity): AttributeSnapshot? = entries[entity.uniqueId]?.snapshot

    fun put(entity: Entity, snapshot: AttributeSnapshot) {
        entries[entity.uniqueId] = Entry(WeakReference(entity), snapshot, false)
    }

    fun markDirty(entity: Entity) {
        entries[entity.uniqueId]?.dirty = true
    }

    fun remove(entity: Entity) {
        entries.remove(entity.uniqueId)
    }

    fun entities(): List<Entity> {
        entries.entries.removeIf { it.value.entity.get() == null }
        return entries.values.mapNotNull { it.entity.get() }
    }

    fun clear() {
        entries.clear()
    }
}
