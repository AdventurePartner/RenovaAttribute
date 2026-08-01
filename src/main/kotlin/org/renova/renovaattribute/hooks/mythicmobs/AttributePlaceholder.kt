package org.renova.renovaattribute.hooks.mythicmobs

import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.core.skills.placeholders.Placeholder
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.core.AttributeRegistry
import java.util.function.BiFunction

object AttributePlaceholder {
    @Volatile
    private var enabled = true

    fun configure(enabled: Boolean) {
        this.enabled = enabled
    }

    fun register() {
        val manager = MythicBukkit.inst().placeholderManager
        manager.register(
            "caster.attr",
            Placeholder.meta(BiFunction { meta, argument ->
                if (!enabled) {
                    return@BiFunction "0"
                }
                val entity = meta.caster.entity.bukkitEntity
                MythicAttributeLookup.get(entity, parseKey(argument)).toString()
            }),
        )
        manager.register(
            "target.attr",
            Placeholder.target { _, target, argument ->
                if (!enabled) {
                    "0"
                } else {
                    MythicAttributeLookup.get(target.bukkitEntity, parseKey(argument)).toString()
                }
            },
        )
    }

    private fun parseKey(argument: String): AttributeKey {
        val normalized = if (':' !in argument && '.' in argument) {
            val separator = argument.indexOf('.')
            argument.substring(0, separator) + ':' + argument.substring(separator + 1)
        } else {
            argument
        }
        return AttributeKey.parse(normalized, AttributeRegistry.defaultNamespace)
    }
}
