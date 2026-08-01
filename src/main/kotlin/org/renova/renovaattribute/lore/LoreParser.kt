package org.renova.renovaattribute.lore

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.ModifierMode
import org.renova.renovaattribute.api.StatValue
import org.renova.renovaattribute.attribute.AttributeDefinition
import org.renova.renovaattribute.core.AttributeRegistry
import java.io.File
import java.util.Collections
import java.util.regex.Pattern

object LoreParser {
    data class PreparedState internal constructor(val patterns: List<LorePattern>)

    private val plainText = PlainTextComponentSerializer.plainText()
    private val legacyText = LegacyComponentSerializer.legacyAmpersand()

    @Volatile
    private var patterns = emptyList<LorePattern>()

    fun prepare(file: File, definitions: Collection<AttributeDefinition>): PreparedState {
        val yaml = YamlConfiguration.loadConfiguration(file)
        val section = requireNotNull(yaml.getConfigurationSection("patterns")) {
            "Missing patterns section in ${file.path}"
        }
        val templates = ModifierMode.entries.associateWith { mode ->
            val path = mode.name.lowercase()
            require(section.isString(path)) { "Missing lore pattern '$path' in ${file.path}" }
            requireNotNull(section.getString(path))
        }
        require(section.isString("default")) { "Missing lore pattern 'default' in ${file.path}" }
        val defaultTemplate = requireNotNull(section.getString("default"))

        val prepared = buildList {
            definitions.forEach { definition ->
                val displayName = plainText.serialize(legacyText.deserialize(definition.displayName))
                ModifierMode.entries.forEach { mode ->
                    add(
                        LorePattern(
                            key = definition.key,
                            pattern = compile(templates.getValue(mode), displayName),
                            mode = mode,
                            scale = if (mode == ModifierMode.PERCENT || mode == ModifierMode.MULTIPLY) {
                                0.01
                            } else if (definition.lorePercentValue) {
                                0.01
                            } else {
                                1.0
                            },
                        ),
                    )
                }
                add(
                    LorePattern(
                        key = definition.key,
                        pattern = compile(defaultTemplate, displayName),
                        mode = definition.loreMode,
                        scale = if (definition.lorePercentValue) 0.01 else 1.0,
                    ),
                )
            }
        }
        return PreparedState(Collections.unmodifiableList(prepared))
    }

    @Synchronized
    fun commit(state: PreparedState) {
        patterns = state.patterns
    }

    fun load(file: File) {
        commit(prepare(file, AttributeRegistry.definitions()))
    }

    fun parse(item: ItemStack?): Map<AttributeKey, StatValue> {
        if (item == null || item.type.isAir || !item.hasItemMeta()) {
            return emptyMap()
        }
        val lore = item.itemMeta.lore() ?: return emptyMap()
        val result = linkedMapOf<AttributeKey, StatValue>()
        lore.asSequence().map(plainText::serialize).forEach { line ->
            for (rule in patterns) {
                val match = rule.pattern.matcher(line)
                if (!match.matches()) {
                    continue
                }
                val parsed = match.group(1).toDoubleOrNull()
                if (parsed == null || !parsed.isFinite()) {
                    break
                }
                val number = parsed * rule.scale
                if (!number.isFinite()) {
                    break
                }
                val contribution = StatValue.of(rule.mode, number)
                result[rule.key] = (result[rule.key] ?: StatValue()).merge(contribution)
                break
            }
        }
        return result
    }

    private fun compile(template: String, displayName: String): Pattern = Pattern.compile(
        template.replace("{name}", Pattern.quote(displayName)),
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE,
    )
}
