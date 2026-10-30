package org.renova.renovaattribute.platform

import org.bukkit.NamespacedKey
import org.bukkit.attribute.AttributeInstance
import org.bukkit.attribute.AttributeModifier
import java.nio.charset.StandardCharsets
import java.util.UUID

/** How a modifier owned by this plugin is built and recognised again. */
interface ModifierIdentity {
    val name: String

    fun create(key: NamespacedKey, amount: Double): AttributeModifier

    fun matches(modifier: AttributeModifier, key: NamespacedKey): Boolean
}

interface ModifierWriter {
    val name: String

    /** False when the modifier is saved with the entity and therefore outlives this plugin. */
    val transient: Boolean

    fun add(attribute: AttributeInstance, modifier: AttributeModifier)
}

/** Flat modifiers owned by this plugin, identified by a [NamespacedKey] on every server version. */
class AttributeModifiers(
    private val identity: ModifierIdentity,
    private val writer: ModifierWriter,
) {
    val name: String
        get() = "${identity.name}+${writer.name}"

    val persistent: Boolean
        get() = !writer.transient

    fun add(attribute: AttributeInstance, key: NamespacedKey, amount: Double) {
        writer.add(attribute, identity.create(key, amount))
    }

    /** Returns whether a modifier was removed. */
    fun remove(attribute: AttributeInstance, key: NamespacedKey): Boolean {
        val owned = attribute.modifiers.filter { identity.matches(it, key) }
        owned.forEach(attribute::removeModifier)
        return owned.isNotEmpty()
    }
}

/** Before 1.21 modifiers are identified by UUID; it is derived from the key so it stays stable. */
object UuidModifierIdentity : ModifierIdentity {
    override val name: String = "uuid"

    override fun create(key: NamespacedKey, amount: Double): AttributeModifier =
        AttributeModifier(uuidOf(key), key.toString(), amount, AttributeModifier.Operation.ADD_NUMBER)

    override fun matches(modifier: AttributeModifier, key: NamespacedKey): Boolean =
        modifier.uniqueId == uuidOf(key)

    private fun uuidOf(key: NamespacedKey): UUID =
        UUID.nameUUIDFromBytes(key.toString().toByteArray(StandardCharsets.UTF_8))
}

/** Spigot has no transient modifiers: they are written to the entity's saved data. */
object PersistentModifierWriter : ModifierWriter {
    override val name: String = "persistent"
    override val transient: Boolean = false

    override fun add(attribute: AttributeInstance, modifier: AttributeModifier) {
        attribute.addModifier(modifier)
    }
}
