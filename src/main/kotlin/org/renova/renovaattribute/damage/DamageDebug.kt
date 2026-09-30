package org.renova.renovaattribute.damage

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.renova.renovaattribute.config.Messages
import java.text.DecimalFormat
import java.util.UUID

/** `/ra debug damage`: prints every handler step of hits involving a watched entity. */
object DamageDebug {
    private val console = UUID(0L, 0L)
    private val watchers = HashMap<UUID, MutableSet<UUID>>()
    private val number = DecimalFormat("0.##")

    /** Returns true when tracing is now enabled for [target]. */
    fun toggle(watcher: CommandSender, target: UUID): Boolean {
        val watcherId = (watcher as? Player)?.uniqueId ?: console
        val targetWatchers = watchers.getOrPut(target) { LinkedHashSet() }
        val enabled = targetWatchers.add(watcherId)
        if (!enabled) {
            targetWatchers.remove(watcherId)
        }
        if (targetWatchers.isEmpty()) {
            watchers.remove(target)
        }
        return enabled
    }

    fun isTraced(attacker: Entity, defender: Entity): Boolean =
        watchers.isNotEmpty() && (attacker.uniqueId in watchers || defender.uniqueId in watchers)

    fun clear() {
        watchers.clear()
    }

    internal fun publish(session: DamageSession) {
        val trace = session.trace ?: return
        val recipients = (watchers[session.attacker.uniqueId].orEmpty() + watchers[session.defender.uniqueId].orEmpty())
            .mapNotNull { id -> if (id == console) Bukkit.getConsoleSender() else Bukkit.getPlayer(id) }
        if (recipients.isEmpty()) {
            return
        }
        val lines = format(session, trace)
        recipients.forEach { recipient -> lines.forEach(recipient::sendMessage) }
    }

    private fun format(session: DamageSession, trace: List<TraceEntry>): List<String> {
        val lines = ArrayList<String>(trace.size + 2)
        lines += Messages.format(
            "debug.header",
            mapOf(
                "attacker" to session.attacker.name,
                "defender" to session.defender.name,
                "type" to session.type.name.lowercase(),
                "cause" to (session.cause?.name ?: "-"),
                "source" to session.origin.name.lowercase(),
                "original" to number.format(session.originalDamage),
                "initial" to number.format(session.initialState?.damage ?: 0.0),
            ),
        )
        trace.forEach { entry ->
            val values = mapOf(
                "priority" to entry.priority,
                "handler" to entry.handlerId,
                "trigger" to entry.trigger.name,
                "value" to number.format(entry.value),
                "changes" to changes(entry.before, entry.after),
                "micros" to entry.nanos / 1_000,
                "error" to entry.error,
            )
            lines += Messages.format(if (entry.error == null) "debug.entry" else "debug.entry-error", values)
        }
        lines += Messages.format(
            "debug.result",
            mapOf(
                "outcome" to (session.outcome?.name ?: "-"),
                "damage" to number.format(session.normalDamage()),
                "final" to number.format(session.finalDamage),
                "true" to number.format(session.trueHealthLoss),
            ),
        )
        return lines
    }

    private fun changes(before: WorkingState, after: WorkingState): String {
        val parts = ArrayList<String>()
        fun numeric(label: String, from: Double, to: Double) {
            if (from != to) {
                parts += "${Messages.raw("debug.labels.$label")} ${number.format(from)}→${number.format(to)}"
            }
        }
        fun flag(label: String, from: Boolean, to: Boolean) {
            if (from != to) {
                parts += "${Messages.raw("debug.labels.$label")} $from→$to"
            }
        }
        numeric("damage", before.damage, after.damage)
        numeric("damage-boost", before.damageBoost, after.damageBoost)
        numeric("crit-chance", before.criticalChance, after.criticalChance)
        numeric("crit-damage", before.criticalDamage, after.criticalDamage)
        flag("critical", before.critical, after.critical)
        numeric("defense", before.defense, after.defense)
        numeric("true-damage", before.trueDamage, after.trueDamage)
        numeric("lifesteal", before.lifesteal, after.lifesteal)
        flag("cancelled", before.cancelled, after.cancelled)
        return parts.joinToString(", ").ifEmpty { Messages.raw("debug.no-change") }
    }
}
