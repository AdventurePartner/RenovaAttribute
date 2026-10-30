package org.renova.renovaattribute.compat.paper

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.ItemMeta
import org.renova.renovaattribute.platform.TextAccess

/** Paper keeps lore as components; reading them directly also flattens translatable parts. */
class AdventureTextAccess : TextAccess {
    override val name: String = "adventure"

    private val plainText = PlainTextComponentSerializer.plainText()
    private val ampersand = LegacyComponentSerializer.legacyAmpersand()
    private val section = LegacyComponentSerializer.builder()
        .character(LegacyComponentSerializer.SECTION_CHAR)
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build()

    override fun plainLore(meta: ItemMeta): List<String>? = meta.lore()?.map(plainText::serialize)

    override fun plainFromAmpersand(text: String): String = plainText.serialize(ampersand.deserialize(text))

    override fun sendActionBar(player: Player, text: String) {
        player.sendActionBar(section.deserialize(text))
    }
}
