package org.renova.renovaattribute.hooks.mythicmobs

import org.bukkit.entity.Entity
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.core.AttributeRegistry
import org.renova.renovaattribute.platform.Capabilities

object AttributePlaceholder {
    private const val GENERIC_REGISTRAR = "org.renova.renovaattribute.compat.mythic.GenericPlaceholderRegistrar"

    @Volatile
    private var enabled = true

    // MythicMobs 5.12 起提供按类注册的新占位符 API，MetaPlaceholder / TargetPlaceholder 随之弃用。
    // 新实现在 compat-mythic 里，探测到它的父类后才按类名加载，5.11 及以下不会链接到它。
    // lazy 的 lambda 没有期望类型，不写类型参数时 create 会被推断成 LegacyPlaceholderRegistrar 并强转失败。
    private val registrar: PlaceholderRegistrar by lazy {
        if (Capabilities.hasClass("io.lumine.mythic.core.skills.placeholders.types.EntityScopedPlaceholder")) {
            Capabilities.create<PlaceholderRegistrar>(GENERIC_REGISTRAR)
        } else {
            LegacyPlaceholderRegistrar
        }
    }

    /** The registration used on the running MythicMobs: `generic` (5.12+) or `legacy`. */
    val implementation: String
        get() = registrar.name

    fun configure(enabled: Boolean) {
        this.enabled = enabled
    }

    fun register() {
        registrar.register()
    }

    /** The attribute named by [argument] on [entity]; null while the placeholders are switched off. */
    fun lookup(entity: Entity, argument: String): Double? {
        if (!enabled) {
            return null
        }
        return MythicAttributeLookup.get(entity, parseKey(argument))
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
