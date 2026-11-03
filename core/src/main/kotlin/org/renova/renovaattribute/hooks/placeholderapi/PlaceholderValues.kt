package org.renova.renovaattribute.hooks.placeholderapi

import org.bukkit.entity.Player
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.core.AttributeRegistry
import org.renova.renovaattribute.hooks.AttributeLookup
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * `%ra_<key>%` formats the attribute like `/ra info` does; `%ra_value_<key>%` prints the plain
 * number with a '.' separator for plugins that compare it. Free of PlaceholderAPI types.
 */
internal object PlaceholderValues {
    private const val VALUE_PREFIX = "value_"

    // 占位符常在异步线程上解析，DecimalFormat 不是线程安全的。
    private val plainFormat = ThreadLocal.withInitial {
        DecimalFormat("0.####", DecimalFormatSymbols.getInstance(Locale.ROOT))
    }

    /** Null for an unknown attribute, so PlaceholderAPI leaves the placeholder as typed. */
    fun request(player: Player?, params: String): String? {
        val plain = params.startsWith(VALUE_PREFIX, ignoreCase = true)
        val rawKey = if (plain) params.substring(VALUE_PREFIX.length) else params
        val key = try {
            AttributeKey.parse(rawKey, AttributeRegistry.defaultNamespace)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val definition = AttributeRegistry[key] ?: return null
        val value = if (player == null) definition.defaultValue else AttributeLookup.get(player, key)
        return if (plain) plainFormat.get().format(value) else definition.format(value)
    }
}
