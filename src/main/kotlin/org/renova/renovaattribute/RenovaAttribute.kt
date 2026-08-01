package org.renova.renovaattribute

import com.aystudio.core.bukkit.plugin.AyPlugin
import org.bukkit.entity.LivingEntity
import org.renova.renovaattribute.api.RenovaApi
import org.renova.renovaattribute.command.RenovaCommand
import org.renova.renovaattribute.config.RenovaConfig
import org.renova.renovaattribute.config.ResourceLoader
import org.renova.renovaattribute.core.AttributeServiceImpl
import org.renova.renovaattribute.damage.DamageMetadataStore
import org.renova.renovaattribute.damage.DamagePipeline
import org.renova.renovaattribute.damage.VanillaDamageListener
import org.renova.renovaattribute.formula.LuaFormulaEvaluator
import org.renova.renovaattribute.hooks.mythicmobs.MythicHook
import org.renova.renovaattribute.listener.EntityLifecycleListener
import org.renova.renovaattribute.lore.EquipmentChangeListener
import org.renova.renovaattribute.lore.EquipmentLoreSource
import org.renova.renovaattribute.source.BuffSource
import org.renova.renovaattribute.sync.VanillaAttributeSource
import org.renova.renovaattribute.sync.VanillaAttributeSync
import java.util.logging.Level

class RenovaAttribute : AyPlugin() {
    private val vanillaDamageListener = VanillaDamageListener()
    private val equipmentChangeListener = EquipmentChangeListener()
    private var initialized = false
    private lateinit var currentConfig: RenovaConfig

    companion object {
        lateinit var instance: RenovaAttribute
            private set

        @JvmStatic
        fun api(): RenovaApi = RenovaApi
    }

    override fun onEnable() {
        instance = this
        runCatching {
            currentConfig = ResourceLoader.load(this)
            initialize(currentConfig)
        }.onFailure { error ->
            logger.log(Level.SEVERE, "Failed to enable RenovaAttribute", error)
            server.pluginManager.disablePlugin(this)
            return
        }
        logger.info("RenovaAttribute has been enabled")
    }

    override fun onDisable() {
        if (server.pluginManager.getPlugin("MythicMobs") != null && MythicHook.isInitialized()) {
            MythicHook.shutdown()
        }
        BuffSource.shutdown()
        equipmentChangeListener.shutdown()
        vanillaDamageListener.shutdown()
        DamageMetadataStore.clear()
        VanillaAttributeSync.removeAll()
        AttributeServiceImpl.shutdown()
        LuaFormulaEvaluator.clear()
        initialized = false
        logger.info("RenovaAttribute has been disabled")
    }

    fun reloadPlugin() {
        val prepared = ResourceLoader.prepare(this)
        val previous = ResourceLoader.commit(prepared)
        try {
            currentConfig = prepared.config
            applyReloadedConfiguration(currentConfig)
        } catch (error: Throwable) {
            if (previous != null) {
                try {
                    ResourceLoader.commit(previous)
                    currentConfig = previous.config
                    applyReloadedConfiguration(currentConfig)
                } catch (rollbackError: Throwable) {
                    error.addSuppressed(rollbackError)
                    logger.log(
                        Level.SEVERE,
                        "RenovaAttribute reload rollback failed; disabling plugin to avoid mixed state",
                        rollbackError,
                    )
                    server.pluginManager.disablePlugin(this)
                }
            } else {
                server.pluginManager.disablePlugin(this)
            }
            throw error
        }
    }

    private fun initialize(config: RenovaConfig) {
        if (initialized) {
            return
        }
        VanillaAttributeSync.initialize(this)
        configure(config)
        server.pluginManager.registerEvents(VanillaAttributeSync, this)
        server.pluginManager.registerEvents(vanillaDamageListener, this)
        server.pluginManager.registerEvents(equipmentChangeListener, this)
        server.pluginManager.registerEvents(EntityLifecycleListener(), this)

        AttributeServiceImpl.registerSource(VanillaAttributeSource)
        AttributeServiceImpl.registerSource(EquipmentLoreSource)
        AttributeServiceImpl.registerSource(BuffSource)

        val command = requireNotNull(getCommand("renovaattribute")) {
            "renovaattribute command is missing from plugin.yml"
        }
        val executor = RenovaCommand()
        command.setExecutor(executor)
        command.tabCompleter = executor

        configureMythic(config)

        server.worlds.asSequence()
            .flatMap { it.livingEntities.asSequence() }
            .filter(LivingEntity::isValid)
            .forEach(AttributeServiceImpl::refresh)
        initialized = true
    }

    private fun configureMythic(config: RenovaConfig) {
        if (!server.pluginManager.isPluginEnabled("MythicMobs")) {
            if (config.mythicEnabled) {
                logger.warning("MythicMobs integration is enabled but MythicMobs is not installed")
            }
            return
        }
        val configureHook = {
            MythicHook.configure(
                enabled = config.mythicEnabled,
                configNode = config.mythicConfigNode,
                sourcePriority = config.mythicSourcePriority,
                placeholders = config.mythicPlaceholders,
                mechanics = config.mythicMechanics,
                condition = config.mythicCondition,
                damage = config.mythicDamage,
            )
        }
        if (MythicHook.isInitialized()) {
            configureHook()
        } else {
            MythicHook.enable(
                enabled = config.mythicEnabled,
                configNode = config.mythicConfigNode,
                sourcePriority = config.mythicSourcePriority,
                placeholders = config.mythicPlaceholders,
                mechanics = config.mythicMechanics,
                condition = config.mythicCondition,
                damage = config.mythicDamage,
            )
        }
    }

    private fun configure(config: RenovaConfig) {
        EquipmentLoreSource.configure(config.equipmentLoreEnabled, config.equipmentLorePriority)
        equipmentChangeListener.configure(config.equipmentRefreshDelayTicks)
        BuffSource.configure(config.buffPriority, config.buffCleanupPeriodTicks)
        VanillaAttributeSync.configure(config.syncMaxHealth, config.syncMovementSpeed)
        vanillaDamageListener.configure(config.physicalCauses, config.magicCauses)
        DamagePipeline.configure(
            enabled = config.damageEnabled,
            useVanillaBaseDamage = config.useVanillaBaseDamage,
            defenseConstant = config.defenseConstant,
            criticalHits = config.criticalHits,
            trueDamage = config.trueDamage,
            lifesteal = config.lifesteal,
        )
    }

    private fun applyReloadedConfiguration(config: RenovaConfig) {
        configure(config)
        configureMythic(config)
        AttributeServiceImpl.refreshAll()
        VanillaAttributeSync.syncAll()
    }
}
