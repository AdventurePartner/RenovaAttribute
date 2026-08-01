package org.renova.renovaattribute.command

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
import java.util.Locale

class RenovaCommand : CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        when (args.firstOrNull()?.lowercase(Locale.ROOT)) {
            "reload" -> reload(sender)
            "info" -> info(sender, args.drop(1))
            else -> Messages.list("command.usage")
                .map { it.replace("{label}", label) }
                .forEach(sender::sendMessage)
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> = when (args.size) {
        1 -> listOf("info", "reload").filter { it.startsWith(args[0], true) }
        2 -> if (args[0].equals("info", true)) {
            AttributeRegistry.definitions().map { it.key.toString() }
                .filter { it.startsWith(args[1], true) }
        } else {
            emptyList()
        }
        else -> emptyList()
    }

    private fun reload(sender: CommandSender) {
        if (!sender.hasPermission("renovaattribute.admin")) {
            sender.sendMessage(Messages.prefixed("command.no-permission"))
            return
        }
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

    private fun info(sender: CommandSender, args: List<String>) {
        if (!sender.hasPermission("renovaattribute.info")) {
            sender.sendMessage(Messages.prefixed("command.no-permission"))
            return
        }
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
