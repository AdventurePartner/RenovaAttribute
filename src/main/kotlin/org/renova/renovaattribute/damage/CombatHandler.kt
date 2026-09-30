package org.renova.renovaattribute.damage

import org.renova.renovaattribute.api.AttributeKey

interface CombatHandler {
    /** Unique across all handlers; also the last ordering tie-breaker. */
    val id: String
    val trigger: CombatTrigger
    val priority: Int

    /**
     * Attribute read from the trigger side and passed to [handle] as `value`.
     * Handlers without an attribute always run with a value of 0.
     */
    val attribute: AttributeKey?

    /** When false, an attribute handler is skipped while its owner's value is 0. */
    val runWhenZero: Boolean

    fun handle(session: DamageSession, value: Double)
}

class CombatChain(handlers: Collection<CombatHandler>) {
    val calculate: List<CombatHandler>
    val settle: List<CombatHandler>

    init {
        val ids = HashSet<String>()
        handlers.forEach { handler ->
            require(ids.add(handler.id)) { "Duplicate combat handler id ${handler.id}" }
        }
        val sorted = handlers.sortedWith(ORDER)
        calculate = sorted.filter { it.trigger.stage == CombatStage.CALCULATE }
        settle = sorted.filter { it.trigger.stage == CombatStage.SETTLE }
    }

    fun all(): List<CombatHandler> = calculate + settle

    companion object {
        @JvmField
        val ORDER: Comparator<CombatHandler> = compareBy<CombatHandler>(
            { it.priority },
            { it.trigger.ordinal },
            { it.id },
        )
    }
}
