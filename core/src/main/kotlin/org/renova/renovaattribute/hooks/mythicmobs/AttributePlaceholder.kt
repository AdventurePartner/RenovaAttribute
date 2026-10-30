package org.renova.renovaattribute.hooks.mythicmobs

import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.core.skills.placeholders.PlaceholderMeta
import io.lumine.mythic.core.skills.placeholders.types.MetaPlaceholder
import io.lumine.mythic.core.skills.placeholders.types.TargetPlaceholder
import io.lumine.utils.interfaces.TriFunction
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.core.AttributeRegistry

object AttributePlaceholder {
    @Volatile
    private var enabled = true

    fun configure(enabled: Boolean) {
        this.enabled = enabled
    }

    fun register() {
        val manager = MythicBukkit.inst().placeholderManager
        // 直接实现 types.MetaPlaceholder / 构造 types.TargetPlaceholder，
        // 避开 Placeholder.meta/target 弃用工厂（其方法体内调用
        // ClassInfo.warnClassIsUsingDeprecatedAPI 触发 MM 启动横幅）。
        // MetaPlaceholder 与 TargetPlaceholder 的签名在 5.11.2 与 5.13.0 间一致。
        manager.register(
            "caster.attr",
            object : MetaPlaceholder {
                override fun apply(meta: PlaceholderMeta, argument: String): String {
                    if (!enabled) {
                        return "0"
                    }
                    val entity = meta.caster.entity.bukkitEntity
                    return MythicAttributeLookup.get(entity, parseKey(argument)).toString()
                }
            },
        )
        manager.register(
            "target.attr",
            TargetPlaceholder(
                TriFunction { _, target, argument ->
                    if (!enabled) {
                        "0"
                    } else {
                        MythicAttributeLookup.get(target.bukkitEntity, parseKey(argument)).toString()
                    }
                },
            ),
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
