package org.renova.renovaattribute.source

import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.scheduler.BukkitTask
import org.renova.renovaattribute.RenovaAttribute
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.AttributeSource
import org.renova.renovaattribute.api.StatValue
import org.renova.renovaattribute.core.AttributeServiceImpl
import java.util.UUID

object BuffSource : AttributeSource {
    override val id: String = "renova:buffs"
    override var priority: Int = 200
        private set

    private var sequence = 0L
    private val modifications = linkedMapOf<UUID, LinkedHashMap<String, TimedModification>>()
    private var cleanupTask: BukkitTask? = null

    fun configure(priority: Int, cleanupPeriodTicks: Long) {
        ensureMainThread()
        require(cleanupPeriodTicks > 0) { "Buff cleanup period must be positive" }
        this.priority = priority
        cleanupTask?.cancel()
        cleanupTask = RenovaAttribute.instance.server.scheduler.runTaskTimer(
            RenovaAttribute.instance,
            Runnable(::cleanup),
            cleanupPeriodTicks,
            cleanupPeriodTicks,
        )
    }

    fun add(
        entity: Entity,
        key: AttributeKey,
        value: StatValue,
        durationTicks: Long,
        tag: String,
    ): String {
        ensureMainThread()
        require(tag.isNotBlank()) { "Buff tag cannot be blank" }
        val id = "$tag#${nextSequence()}"
        mutate(entity) { values ->
            values[id] = createModification(id, tag, key, value, durationTicks)
        }
        return id
    }

    fun upsert(
        entity: Entity,
        key: AttributeKey,
        value: StatValue,
        durationTicks: Long,
        tag: String,
    ) {
        ensureMainThread()
        require(tag.isNotBlank()) { "Buff tag cannot be blank" }
        mutate(entity) { values ->
            values.entries.removeIf { it.value.tag == tag }
            values[tag] = createModification(tag, tag, key, value, durationTicks)
        }
    }

    fun removeByTag(entity: Entity, tag: String): Boolean {
        ensureMainThread()
        var removed = false
        mutate(entity) { values ->
            removed = values.entries.removeIf { it.value.tag == tag }
        }
        return removed
    }

    fun clear(entity: Entity) {
        ensureMainThread()
        if (entity.uniqueId !in modifications) {
            return
        }
        mutate(entity, LinkedHashMap())
    }

    fun discard(entity: Entity) {
        ensureMainThread()
        modifications.remove(entity.uniqueId)
    }

    override fun provide(entity: Entity): Map<AttributeKey, StatValue> {
        val values = modifications[entity.uniqueId] ?: return emptyMap()
        val currentTick = currentTick()
        val result = linkedMapOf<AttributeKey, StatValue>()
        values.values.asSequence()
            .filterNot { it.expired(currentTick) }
            .sortedBy { it.sequence }
            .forEach { modification ->
                result[modification.key] = (result[modification.key] ?: StatValue())
                    .merge(modification.value)
            }
        return result
    }

    fun shutdown() {
        ensureMainThread()
        cleanupTask?.cancel()
        cleanupTask = null
        modifications.clear()
    }

    private fun mutate(
        entity: Entity,
        replacement: LinkedHashMap<String, TimedModification>? = null,
        operation: ((LinkedHashMap<String, TimedModification>) -> Unit)? = null,
    ) {
        val previous = modifications[entity.uniqueId]
        val candidate = replacement ?: LinkedHashMap(previous.orEmpty()).also { operation?.invoke(it) }
        if (candidate.isEmpty()) {
            modifications.remove(entity.uniqueId)
        } else {
            modifications[entity.uniqueId] = candidate
        }
        try {
            AttributeServiceImpl.refresh(entity)
        } catch (error: Throwable) {
            if (previous == null) {
                modifications.remove(entity.uniqueId)
            } else {
                modifications[entity.uniqueId] = previous
            }
            throw error
        }
    }

    private fun mutate(
        entity: Entity,
        operation: (LinkedHashMap<String, TimedModification>) -> Unit,
    ) = mutate(entity, null, operation)

    private fun createModification(
        id: String,
        tag: String,
        key: AttributeKey,
        value: StatValue,
        durationTicks: Long,
    ): TimedModification {
        require(durationTicks <= Int.MAX_VALUE.toLong()) { "Buff duration is too large" }
        val expiresAt = if (durationTicks <= 0) {
            Long.MAX_VALUE
        } else {
            currentTick() + durationTicks
        }
        return TimedModification(
            id = id,
            tag = tag,
            key = key,
            value = value,
            expiresAtTick = expiresAt,
            sequence = nextSequence(),
        )
    }

    private fun cleanup() {
        val currentTick = currentTick()
        modifications.entries.toList().forEach { (uuid, values) ->
            val changed = values.entries.removeIf { it.value.expired(currentTick) }
            if (!changed) {
                return@forEach
            }
            if (values.isEmpty()) {
                modifications.remove(uuid)
            }
            Bukkit.getEntity(uuid)?.let(AttributeServiceImpl::refresh)
        }
    }

    private fun currentTick(): Long = Bukkit.getCurrentTick().toLong()

    private fun nextSequence(): Long = ++sequence

    private fun ensureMainThread() {
        check(Bukkit.isPrimaryThread()) { "BuffSource must be modified from the server thread" }
    }
}
