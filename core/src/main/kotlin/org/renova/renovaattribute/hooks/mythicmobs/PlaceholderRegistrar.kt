package org.renova.renovaattribute.hooks.mythicmobs

import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.core.skills.placeholders.PlaceholderMeta
import io.lumine.mythic.core.skills.placeholders.types.MetaPlaceholder
import io.lumine.mythic.core.skills.placeholders.types.TargetPlaceholder
import io.lumine.utils.interfaces.TriFunction

/** Registers the `attr` placeholders with MythicMobs; values come from [AttributePlaceholder.lookup]. */
interface PlaceholderRegistrar {
    val name: String

    fun register()
}

/** MythicMobs 5.6–5.11: placeholder instances registered by name, only `caster.attr` and `target.attr`. */
internal object LegacyPlaceholderRegistrar : PlaceholderRegistrar {
    override val name: String = "legacy"

    override fun register() {
        val manager = MythicBukkit.inst().placeholderManager
        // 直接实现 types.MetaPlaceholder / 构造 types.TargetPlaceholder，
        // 避开 Placeholder.meta/target 弃用工厂（其方法体内调用
        // ClassInfo.warnClassIsUsingDeprecatedAPI 触发 MM 启动横幅）。
        manager.register(
            "caster.attr",
            object : MetaPlaceholder {
                override fun apply(meta: PlaceholderMeta, argument: String): String =
                    AttributePlaceholder.lookup(meta.caster.entity.bukkitEntity, argument)?.toString() ?: "0"
            },
        )
        manager.register(
            "target.attr",
            TargetPlaceholder(
                TriFunction { _, target, argument ->
                    AttributePlaceholder.lookup(target.bukkitEntity, argument)?.toString() ?: "0"
                },
            ),
        )
    }
}
