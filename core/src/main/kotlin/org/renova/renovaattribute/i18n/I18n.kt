package org.renova.renovaattribute.i18n

import com.aystudio.core.bukkit.util.common.TextUtil
import org.bukkit.command.CommandSender
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/** Messages from `language/<language>.yml`; every entry is pre-colored and may span zero or more lines. */
object I18n {
    const val DEFAULT_LANGUAGE = "zh_CN"
    val BUNDLED_LANGUAGES = listOf(DEFAULT_LANGUAGE)

    private val PLACEHOLDER = Regex("\\{([A-Za-z0-9_-]+)}")

    class PreparedState internal constructor(
        internal val messages: Map<String, List<String>>,
        internal val prefix: String,
    )

    @Volatile
    private var state = PreparedState(emptyMap(), "")

    fun prepare(file: File): PreparedState {
        val user = YamlConfiguration().apply { load(file) }
        val defaults = bundled(file.nameWithoutExtension) ?: bundled(DEFAULT_LANGUAGE)
        return prepare(defaults, user, "language/${file.name}", TextUtil::formatHexColor)
    }

    /** Keys missing from [user] fall back to [defaults], so older language files keep working. */
    internal fun prepare(
        defaults: ConfigurationSection?,
        user: ConfigurationSection,
        source: String,
        colorize: (String) -> String,
    ): PreparedState {
        require(!user.isSet("prefix") || user.isString("prefix")) { "$source: prefix must be a string" }
        val messages = defaults?.let { parse(it, "bundled language file", colorize) }.orEmpty() +
            parse(user, source, colorize)
        return PreparedState(messages, messages["prefix"]?.joinToString("").orEmpty())
    }

    fun commit(prepared: PreparedState) {
        state = prepared
    }

    /** An unknown key yields the key itself rather than failing inside a command or damage event. */
    fun lines(key: String, values: Map<String, Any?> = emptyMap()): List<String> {
        val current = state
        val template = current.messages[key] ?: return listOf(key)
        return template.map { line ->
            PLACEHOLDER.replace(line) { match ->
                val name = match.groupValues[1]
                when {
                    name == "prefix" -> current.prefix
                    name in values -> values[name].toString()
                    else -> match.value
                }
            }
        }
    }

    fun text(key: String, values: Map<String, Any?> = emptyMap()): String = lines(key, values).joinToString("\n")

    fun send(receiver: CommandSender, key: String, values: Map<String, Any?> = emptyMap()) {
        lines(key, values).forEach(receiver::sendMessage)
    }

    private fun parse(
        section: ConfigurationSection,
        source: String,
        colorize: (String) -> String,
    ): Map<String, List<String>> {
        val result = LinkedHashMap<String, List<String>>()
        section.getKeys(true).forEach { key ->
            when {
                section.isConfigurationSection(key) -> Unit
                section.isString(key) -> result[key] = section.getString(key).orEmpty()
                    .let { if (it.isEmpty()) emptyList() else listOf(colorize(it)) }
                section.isList(key) -> result[key] = section.getStringList(key).map(colorize)
                else -> throw IllegalArgumentException("$source: $key must be a string or a list")
            }
        }
        return result
    }

    private fun bundled(language: String): YamlConfiguration? =
        I18n::class.java.getResourceAsStream("/language/$language.yml")?.use { input ->
            YamlConfiguration().apply { load(InputStreamReader(input, StandardCharsets.UTF_8)) }
        }
}
