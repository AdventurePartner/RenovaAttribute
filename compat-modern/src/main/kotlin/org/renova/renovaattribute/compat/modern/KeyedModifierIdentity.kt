package org.renova.renovaattribute.compat.modern

import org.bukkit.NamespacedKey
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlotGroup
import org.renova.renovaattribute.platform.ModifierIdentity

/** 1.21+: modifiers are identified by key; Spigot only offers the constructor with a slot group. */
class KeyedModifierIdentity : ModifierIdentity {
    override val name: String = "keyed"

    override fun create(key: NamespacedKey, amount: Double): AttributeModifier =
        AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY)

    override fun matches(modifier: AttributeModifier, key: NamespacedKey): Boolean = modifier.key == key
}
