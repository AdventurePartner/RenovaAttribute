package org.renova.renovaattribute.config

import org.bukkit.configuration.file.YamlConfiguration
import org.renova.renovaattribute.RenovaAttribute
import org.renova.renovaattribute.core.AttributeRegistry
import org.renova.renovaattribute.damage.CombatRegistry
import org.renova.renovaattribute.formula.LuaFormulaEvaluator
import org.renova.renovaattribute.lore.LoreParser
import java.io.File

object ResourceLoader {
    internal data class PreparedResources(
        val config: RenovaConfig,
        val messages: Messages.PreparedState,
        val registry: AttributeRegistry.PreparedState,
        val lua: LuaFormulaEvaluator.PreparedState,
        val lore: LoreParser.PreparedState,
        val combat: CombatRegistry.PreparedState,
    )

    private val EXAMPLES = listOf(
        "attributes/custom/example.yml.example",
        "attributes/custom/armor-penetration.yml.example",
        "attributes/custom/dodge.yml.example",
        "attributes/custom/critical-resistance.yml.example",
        "attributes/custom/execute.yml.example",
        "attributes/custom/thorns.yml.example",
        "attributes/custom/fire-resistance.yml.example",
        "attributes/custom/pvp-reduction.yml.example",
    )

    @Volatile
    private var active: PreparedResources? = null

    val config: RenovaConfig
        get() = requireNotNull(active).config

    fun load(plugin: RenovaAttribute): RenovaConfig {
        val prepared = prepare(plugin)
        commit(prepared)
        return prepared.config
    }

    internal fun prepare(plugin: RenovaAttribute): PreparedResources {
        lateinit var configFile: File
        lateinit var messageFile: File
        lateinit var builtinFile: File
        lateinit var lorePatternsFile: File
        plugin.saveResource("config.yml", "config.yml", false) { configFile = it }
        plugin.saveResource("message.yml", "message.yml", false) { messageFile = it }
        plugin.saveResource("attributes/builtin.yml", "attributes/builtin.yml", false) {
            builtinFile = it
        }
        EXAMPLES.forEach { path -> plugin.saveResource(path, path, false, null) }
        plugin.saveResource("lore_patterns.yml", "lore_patterns.yml", false) {
            lorePatternsFile = it
        }

        if (ConfigMigrator.migrate(configFile)) {
            plugin.logger.info("Migrated damage cause INDIRECT_MAGIC to MAGIC in config.yml")
        }
        val preparedConfig = RenovaConfig(YamlConfiguration.loadConfiguration(configFile))
        val preparedMessages = Messages.prepare(messageFile)
        val customDirectory = File(plugin.dataFolder, preparedConfig.customAttributeDirectory)
        val preparedRegistry = AttributeRegistry.prepare(
            builtinFile,
            customDirectory,
            preparedConfig.namespace,
        )
        val preparedLua = LuaFormulaEvaluator.prepare(
            preparedRegistry.definitions.values,
            preparedRegistry.formulaOrder,
            preparedConfig.maxLuaInstructions,
            preparedRegistry.namespace,
        )
        val preparedLore = LoreParser.prepare(lorePatternsFile, preparedRegistry.definitions.values)
        val preparedCombat = CombatRegistry.prepare(
            preparedRegistry.definitions.values,
            plugin.dataFolder,
            preparedConfig.maxCombatLuaInstructions,
            preparedConfig.builtinPriorities,
        )
        return PreparedResources(
            preparedConfig,
            preparedMessages,
            preparedRegistry,
            preparedLua,
            preparedLore,
            preparedCombat,
        )
    }

    @Synchronized
    internal fun commit(prepared: PreparedResources): PreparedResources? {
        val previous = active
        Messages.commit(prepared.messages)
        AttributeRegistry.commit(prepared.registry)
        LuaFormulaEvaluator.commit(prepared.lua)
        LoreParser.commit(prepared.lore)
        CombatRegistry.commit(prepared.combat)
        active = prepared
        return previous
    }
}
