package org.renova.renovaattribute.sync

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeInstance
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.renova.renovaattribute.RenovaAttribute
import org.renova.renovaattribute.api.AttributeSnapshot
import org.renova.renovaattribute.api.event.AttributeSnapshotUpdateEvent
import org.renova.renovaattribute.attribute.BuiltinAttributes
import org.renova.renovaattribute.core.AttributeServiceImpl
import java.util.UUID

object VanillaAttributeSync : Listener {
    private lateinit var maxHealthKey: NamespacedKey
    private lateinit var movementSpeedKey: NamespacedKey
    private val modifiedEntities = mutableSetOf<UUID>()
    private var syncMaxHealth = true
    private var syncMovementSpeed = true
    private var initialized = false

    fun initialize(plugin: RenovaAttribute) {
        if (initialized) {
            return
        }
        initialized = true
        maxHealthKey = NamespacedKey(plugin, "max_health")
        movementSpeedKey = NamespacedKey(plugin, "movement_speed")
    }

    fun configure(syncMaxHealth: Boolean, syncMovementSpeed: Boolean) {
        check(initialized) { "VanillaAttributeSync has not been initialized" }
        if (this.syncMaxHealth && !syncMaxHealth) {
            loadedEntities().forEach { removeMaxHealth(it) }
        }
        if (this.syncMovementSpeed && !syncMovementSpeed) {
            loadedEntities().forEach { removeMovementSpeed(it) }
        }
        this.syncMaxHealth = syncMaxHealth
        this.syncMovementSpeed = syncMovementSpeed
    }

    fun syncAll() {
        loadedEntities().forEach { entity ->
            AttributeServiceImpl.cachedSnapshot(entity)?.let { sync(entity, it) }
        }
    }

    fun removeModifiers(entity: LivingEntity) {
        removeMaxHealth(entity)
        removeMovementSpeed(entity)
        modifiedEntities.remove(entity.uniqueId)
    }

    fun removeAll() {
        loadedEntities().forEach(::removeModifiers)
        modifiedEntities.clear()
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onSnapshotUpdate(event: AttributeSnapshotUpdateEvent) {
        val entity = event.entity as? LivingEntity ?: return
        sync(entity, event.current)
    }

    private fun sync(entity: LivingEntity, snapshot: AttributeSnapshot) {
        if (syncMaxHealth) {
            syncMaxHealth(entity, snapshot[BuiltinAttributes.MAX_HEALTH])
        }
        if (syncMovementSpeed) {
            syncAttribute(
                entity.getAttribute(Attribute.MOVEMENT_SPEED),
                movementSpeedKey,
                snapshot[BuiltinAttributes.MOVEMENT_SPEED],
            )
        }
        modifiedEntities += entity.uniqueId
    }

    private fun syncMaxHealth(entity: LivingEntity, desiredBase: Double) {
        if (!desiredBase.isFinite() || desiredBase <= 0.0 || entity.isDead) {
            return
        }
        val attribute = entity.getAttribute(Attribute.MAX_HEALTH) ?: return
        val previousMaximum = attribute.value
        val healthRatio = if (previousMaximum > 0.0) entity.health / previousMaximum else 1.0
        syncAttribute(attribute, maxHealthKey, desiredBase)
        val newMaximum = attribute.value
        entity.health = (newMaximum * healthRatio).coerceIn(0.0, newMaximum)
    }

    private fun syncAttribute(
        attribute: AttributeInstance?,
        key: NamespacedKey,
        desiredBase: Double,
    ) {
        if (attribute == null || !desiredBase.isFinite() || desiredBase < 0.0) {
            return
        }
        attribute.removeModifier(key)
        val amount = desiredBase - attribute.baseValue
        if (kotlin.math.abs(amount) <= 1.0E-9) {
            return
        }
        attribute.addTransientModifier(
            AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER),
        )
    }

    private fun removeMaxHealth(entity: LivingEntity) {
        val attribute = entity.getAttribute(Attribute.MAX_HEALTH) ?: return
        val previousMaximum = attribute.value
        val healthRatio = if (!entity.isDead && previousMaximum > 0.0) {
            entity.health / previousMaximum
        } else {
            1.0
        }
        attribute.removeModifier(maxHealthKey)
        if (!entity.isDead) {
            entity.health = (attribute.value * healthRatio).coerceIn(0.0, attribute.value)
        }
    }

    private fun removeMovementSpeed(entity: LivingEntity) {
        entity.getAttribute(Attribute.MOVEMENT_SPEED)?.removeModifier(movementSpeedKey)
    }

    private fun loadedEntities(): List<LivingEntity> = modifiedEntities.mapNotNull { uuid ->
        Bukkit.getEntity(uuid) as? LivingEntity
    }
}
