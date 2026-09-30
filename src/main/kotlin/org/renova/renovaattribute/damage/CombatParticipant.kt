package org.renova.renovaattribute.damage

import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.renova.renovaattribute.api.AttributeSnapshot
import java.util.UUID

/** One side of a hit. [attributes] is the snapshot captured when the hit started. */
interface CombatParticipant {
    val uniqueId: UUID
    val attributes: AttributeSnapshot
    val entity: LivingEntity?
    val health: Double
    val maxHealth: Double
    val isPlayer: Boolean
    val typeKey: String
    val name: String
    val isDead: Boolean
    val isValid: Boolean

    fun hasTag(tag: String): Boolean
}

class EntityParticipant(
    override val entity: LivingEntity,
    override val attributes: AttributeSnapshot,
) : CombatParticipant {
    override val uniqueId: UUID
        get() = entity.uniqueId
    override val health: Double
        get() = entity.health
    override val maxHealth: Double
        get() = entity.getAttribute(Attribute.MAX_HEALTH)?.value ?: entity.health
    override val isPlayer: Boolean
        get() = entity is Player
    override val typeKey: String
        get() = entity.type.key.toString()
    override val name: String
        get() = entity.name
    override val isDead: Boolean
        get() = entity.isDead
    override val isValid: Boolean
        get() = entity.isValid

    override fun hasTag(tag: String): Boolean = tag in entity.scoreboardTags
}
