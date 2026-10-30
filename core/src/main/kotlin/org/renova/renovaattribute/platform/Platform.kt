package org.renova.renovaattribute.platform

import org.renova.renovaattribute.listener.BukkitWorldEntityListener
import org.renova.renovaattribute.lore.PollingEquipmentWatcher

/**
 * Server-dependent implementations. The defaults work on every supported server (Spigot 1.20.1
 * and later); [detect] swaps in the newer implementation of each capability the running server
 * provides. Newer implementations live in the compat modules and are loaded by name only after
 * their probe passed, so their classes never link against API the server lacks.
 */
object Platform {
    private const val MODERN = "org.renova.renovaattribute.compat.modern"
    private const val PAPER = "org.renova.renovaattribute.compat.paper"

    var modifiers: AttributeModifiers = AttributeModifiers(UuidModifierIdentity, PersistentModifierWriter)
        private set
    var damage: DamageDealer = LegacyDamageDealer
        private set
    var text: TextAccess = LegacyTextAccess
        private set
    var equipment: EquipmentWatcher = PollingEquipmentWatcher()
        private set
    var worldEntities: PlatformListener = BukkitWorldEntityListener()
        private set

    /** Returns a one-line summary of the selected implementations. */
    fun detect(): String {
        val identity: ModifierIdentity = if (keyedModifiers()) {
            Capabilities.create("$MODERN.KeyedModifierIdentity")
        } else {
            UuidModifierIdentity
        }
        val writer: ModifierWriter = if (transientModifiers()) {
            Capabilities.create("$PAPER.TransientModifierWriter")
        } else {
            PersistentModifierWriter
        }
        modifiers = AttributeModifiers(identity, writer)
        if (damageSources()) {
            damage = Capabilities.create("$MODERN.DamageSourceDealer")
        }
        if (adventureText()) {
            text = Capabilities.create("$PAPER.AdventureTextAccess")
        }
        if (Capabilities.hasClass("io.papermc.paper.event.entity.EntityEquipmentChangedEvent")) {
            equipment = Capabilities.create("$PAPER.PaperEquipmentWatcher")
        }
        if (
            Capabilities.hasClass("com.destroystokyo.paper.event.entity.EntityAddToWorldEvent") &&
            Capabilities.hasClass("com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent")
        ) {
            worldEntities = Capabilities.create("$PAPER.PaperWorldEntityListener")
        }
        return "modifiers=${modifiers.name}, damage=${damage.name}, equipment=${equipment.name}, " +
            "entities=${worldEntities.name}, text=${text.name}"
    }

    private fun keyedModifiers(): Boolean =
        Capabilities.hasConstructor(
            "org.bukkit.attribute.AttributeModifier",
            "org.bukkit.NamespacedKey",
            "double",
            "org.bukkit.attribute.AttributeModifier\$Operation",
            "org.bukkit.inventory.EquipmentSlotGroup",
        ) && Capabilities.hasMethod("org.bukkit.attribute.AttributeModifier", "getKey")

    private fun transientModifiers(): Boolean =
        Capabilities.hasMethod(
            "org.bukkit.attribute.AttributeInstance",
            "addTransientModifier",
            "org.bukkit.attribute.AttributeModifier",
        )

    private fun damageSources(): Boolean =
        Capabilities.hasMethod("org.bukkit.entity.Damageable", "damage", "double", "org.bukkit.damage.DamageSource") &&
            Capabilities.hasMethod(
                "org.bukkit.damage.DamageSource\$Builder",
                "withCausingEntity",
                "org.bukkit.entity.Entity",
            ) &&
            Capabilities.hasMethod(
                "org.bukkit.damage.DamageSource\$Builder",
                "withDirectEntity",
                "org.bukkit.entity.Entity",
            )

    private fun adventureText(): Boolean =
        Capabilities.hasMethod("org.bukkit.inventory.meta.ItemMeta", "lore") &&
            Capabilities.hasClass("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer") &&
            Capabilities.hasClass("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer") &&
            Capabilities.hasMethod("org.bukkit.entity.Player", "sendActionBar", "net.kyori.adventure.text.Component")
}
