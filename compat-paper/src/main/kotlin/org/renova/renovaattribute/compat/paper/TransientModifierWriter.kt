package org.renova.renovaattribute.compat.paper

import org.bukkit.attribute.AttributeInstance
import org.bukkit.attribute.AttributeModifier
import org.renova.renovaattribute.platform.ModifierWriter

/** Transient modifiers are never saved with the entity, so nothing is left behind if the plugin goes. */
class TransientModifierWriter : ModifierWriter {
    override val name: String = "transient"
    override val transient: Boolean = true

    override fun add(attribute: AttributeInstance, modifier: AttributeModifier) {
        attribute.addTransientModifier(modifier)
    }
}
