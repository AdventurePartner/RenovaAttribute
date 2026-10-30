package org.renova.renovaattribute.platform

import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.ItemMeta

interface TextAccess {
    val name: String

    /** The lore lines without any formatting, or null when the item has no lore. */
    fun plainLore(meta: ItemMeta): List<String>?

    /** Strips the '&' formatting codes used in attribute display names. */
    fun plainFromAmpersand(text: String): String

    /** [text] already uses '§' codes, with hex colours in the '§x§r§r§g§g§b§b' form. */
    fun sendActionBar(player: Player, text: String)
}

/** Spigot only exposes lore and chat as legacy strings and BungeeCord components. */
object LegacyTextAccess : TextAccess {
    override val name: String = "legacy"

    override fun plainLore(meta: ItemMeta): List<String>? = meta.lore?.map { ChatColor.stripColor(it).orEmpty() }

    override fun plainFromAmpersand(text: String): String =
        ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text)).orEmpty()

    override fun sendActionBar(player: Player, text: String) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, *TextComponent.fromLegacyText(text))
    }
}
