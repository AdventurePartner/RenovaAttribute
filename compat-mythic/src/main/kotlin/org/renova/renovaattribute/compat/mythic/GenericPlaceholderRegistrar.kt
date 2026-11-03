package org.renova.renovaattribute.compat.mythic

import io.lumine.mythic.bukkit.MythicBukkit
import org.renova.renovaattribute.hooks.mythicmobs.PlaceholderRegistrar

/**
 * MythicMobs 5.12+: registers [AttributeScopePlaceholder] by class. Placeholder classes stay
 * registered across `/mm reload`, so this happens once. CustomComponentRegistry would end in
 * the same call, but scans this jar through MythicMobs' class loader to find the class.
 */
class GenericPlaceholderRegistrar : PlaceholderRegistrar {
    override val name: String = "generic"

    private var registered = false

    override fun register() {
        if (registered) {
            return
        }
        MythicBukkit.inst().placeholderManager.register(AttributeScopePlaceholder::class.java)
        registered = true
    }
}
