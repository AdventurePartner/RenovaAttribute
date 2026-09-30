package org.renova.renovaattribute.config

import com.aystudio.core.bukkit.util.common.TextUtil
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object Messages {
    data class PreparedState internal constructor(val config: YamlConfiguration)

    @Volatile
    private lateinit var config: YamlConfiguration

    fun prepare(file: File): PreparedState {
        val prepared = YamlConfiguration.loadConfiguration(file)
        bundledDefaults()?.let(prepared::setDefaults)
        require(prepared.isString("prefix")) { "message.yml prefix must be a string" }
        return PreparedState(prepared)
    }

    fun colorize(text: String): String = color(text)

    /** Keys added in newer versions fall back to the bundled file instead of failing. */
    private fun bundledDefaults(): YamlConfiguration? =
        Messages::class.java.getResourceAsStream("/message.yml")?.use { input ->
            YamlConfiguration.loadConfiguration(InputStreamReader(input, StandardCharsets.UTF_8))
        }

    @Synchronized
    fun commit(state: PreparedState) {
        config = state.config
    }

    fun load(file: File) {
        commit(prepare(file))
    }

    fun raw(key: String): String = requireNotNull(config.getString(key)) {
        "Missing message key: $key"
    }

    fun format(key: String, values: Map<String, Any?> = emptyMap()): String =
        color(replace(raw(key), values))

    fun prefixed(key: String, values: Map<String, Any?> = emptyMap()): String =
        color(raw("prefix") + replace(raw(key), values))

    private fun replace(text: String, values: Map<String, Any?>): String {
        var result = text
        values.forEach { (key, value) -> result = result.replace("{$key}", value.toString()) }
        return result
    }

    private fun color(text: String): String = TextUtil.formatHexColor(text)
}
