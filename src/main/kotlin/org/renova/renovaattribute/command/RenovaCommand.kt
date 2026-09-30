package org.renova.renovaattribute.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.renova.renovaattribute.RenovaAttribute
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.config.Messages
import org.renova.renovaattribute.core.AttributeRegistry
import org.renova.renovaattribute.core.AttributeServiceImpl
import org.renova.renovaattribute.damage.DamageDebug

class RenovaCommand : CommandExecutor, TabCompleter {
    private enum class SubCommand(val id: String, val permission: String) {
        INFO("info", "renovaattribute.info"),
        RELOAD("reload", "renovaattribute.admin"),
        DEBUG("debug", "renovaattribute.admin"),
        ;

        fun allows(sender: CommandSender): Boolean = sender.hasPermission(permission)

        companion object {
            fun of(id: String?): SubCommand? = entries.firstOrNull { it.id.equals(id, true) }
        }
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        val subCommand = SubCommand.of(args.firstOrNull())
        if (subCommand == null) {
            help(sender, label)
            return true
        }
        if (!subCommand.allows(sender)) {
            sender.sendMessage(Messages.prefixed("command.no-permission"))
            return true
        }
        when (subCommand) {
            SubCommand.INFO -> info(sender, args.drop(1))
            SubCommand.RELOAD -> reload(sender)
            SubCommand.DEBUG -> debug(sender, label, args.drop(1))
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        if (args.size == 1) {
            return (SubCommand.entries.filter { it.allows(sender) }.map { it.id } + "help")
                .filter { it.startsWith(args[0], true) }
        }
        val subCommand = SubCommand.of(args[0])?.takeIf { it.allows(sender) } ?: return emptyList()
        return when {
            args.size == 2 && subCommand == SubCommand.INFO -> AttributeRegistry.definitions()
                .map { it.key.toString() }
                .filter { it.startsWith(args[1], true) }
            args.size == 2 && subCommand == SubCommand.DEBUG -> listOf("damage").filter { it.startsWith(args[1], true) }
            args.size == 3 && subCommand == SubCommand.DEBUG && args[1].equals("damage", true) ->
                Bukkit.getOnlinePlayers().map(Player::getName).filter { it.startsWith(args[2], true) }
            else -> emptyList()
        }
    }

    private fun help(sender: CommandSender, label: String) {
        val allowed = SubCommand.entries.filter { it.allows(sender) }
        if (allowed.isEmpty()) {
            sender.sendMessage(Messages.prefixed("command.no-permission"))
            return
        }
        sender.sendMessage(Messages.format("command.help.header"))
        allowed.forEach { sender.sendMessage(Messages.format("command.help.${it.id}", mapOf("label" to label))) }
    }

    private fun reload(sender: CommandSender) {
        runCatching(RenovaAttribute.instance::reloadPlugin)
            .onSuccess { sender.sendMessage(Messages.prefixed("command.reloaded")) }
            .onFailure { error ->
                RenovaAttribute.instance.logger.log(
                    java.util.logging.Level.SEVERE,
                    "Failed to reload RenovaAttribute",
                    error,
                )
                sender.sendMessage(Messages.prefixed("command.reload-failed"))
            }
    }

    private fun debug(sender: CommandSender, label: String, args: List<String>) {
        if (!args.firstOrNull().equals("damage", true)) {
            help(sender, label)
            return
        }
        val target = when (val name = args.getOrNull(1)) {
            null -> sender as? Player ?: run {
                sender.sendMessage(Messages.prefixed("command.player-only"))
                return
            }
            else -> Bukkit.getPlayerExact(name) ?: run {
                sender.sendMessage(Messages.prefixed("command.player-not-found", mapOf("player" to name)))
                return
            }
        }
        val key = if (DamageDebug.toggle(sender, target.uniqueId)) "command.debug-enabled" else "command.debug-disabled"
        sender.sendMessage(Messages.prefixed(key, mapOf("player" to target.name)))
    }

    private fun info(sender: CommandSender, args: List<String>) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage(Messages.prefixed("command.player-only"))
            return
        }
        val snapshot = AttributeServiceImpl.snapshot(player)
        if (args.isNotEmpty()) {
            val key = AttributeKey.parse(args[0], AttributeRegistry.defaultNamespace)
            val definition = AttributeRegistry[key]
            if (definition == null) {
                sender.sendMessage(
                    Messages.prefixed("command.unknown-attribute", mapOf("attribute" to args[0])),
                )
                return
            }
            sender.sendMessage(
                Messages.format(
                    "command.info-line",
                    mapOf("name" to definition.displayName, "value" to definition.format(snapshot[key])),
                ),
            )
            return
        }

        sender.sendMessage(Messages.format("command.info-header", mapOf("entity" to player.name)))
        AttributeRegistry.definitions().forEach { definition ->
            sender.sendMessage(
                Messages.format(
                    "command.info-line",
                    mapOf("name" to definition.displayName, "value" to definition.format(snapshot[definition.key])),
                ),
            )
        }
    }
}
