package org.renova.renovaattribute.platform

import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeInstance
import org.bukkit.entity.LivingEntity

/**
 * Vanilla attributes resolved through the registry. The `GENERIC_*` constants were renamed in
 * 1.21.3, when [Attribute] also turned from an enum into an interface, so no constant or method of
 * [Attribute] may be referenced here: the value is only ever passed back to the server.
 */
object VanillaAttributes {
    private val maxHealth: Attribute? by lazy { find("max_health", "generic.max_health") }
    private val movementSpeed: Attribute? by lazy { find("movement_speed", "generic.movement_speed") }

    fun maxHealth(entity: LivingEntity): AttributeInstance? = maxHealth?.let(entity::getAttribute)

    fun movementSpeed(entity: LivingEntity): AttributeInstance? = movementSpeed?.let(entity::getAttribute)

    private fun find(vararg keys: String): Attribute? = keys.firstNotNullOfOrNull { key ->
        Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key))
    }
}
