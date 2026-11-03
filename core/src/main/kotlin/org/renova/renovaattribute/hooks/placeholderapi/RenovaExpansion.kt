package org.renova.renovaattribute.hooks.placeholderapi

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.Plugin

/** The `%ra_...%` expansion; values come from [PlaceholderValues]. */
internal class RenovaExpansion(private val plugin: Plugin) : PlaceholderExpansion() {
    override fun getIdentifier(): String = "ra"

    override fun getAuthor(): String = plugin.description.authors.joinToString()

    override fun getVersion(): String = plugin.description.version

    // 由本插件自己注册和注销，/papi reload 时保留。
    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? =
        PlaceholderValues.request(player?.player, params)
}
