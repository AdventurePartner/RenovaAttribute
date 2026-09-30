package org.renova.renovaattribute.damage

import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.StatValue

/**
 * Side effects requested by handlers. They are queued on the session and applied after
 * every handler of the current stage has finished, so no handler runs while another hit
 * triggered by an effect is being processed.
 */
sealed class CombatEffect {
    internal abstract val label: String

    internal abstract fun apply(actions: CombatActions)

    class Heal(val target: CombatParticipant, val amount: Double) : CombatEffect() {
        init {
            requireNonNegative(amount, "heal amount")
        }

        override val label: String get() = "heal"

        override fun apply(actions: CombatActions) = actions.heal(target, amount)
    }

    /** Armor-ignoring damage that does not enter the damage pipeline again. */
    class DealDamage(
        val source: CombatParticipant,
        val target: CombatParticipant,
        val amount: Double,
    ) : CombatEffect() {
        init {
            requireNonNegative(amount, "damage amount")
        }

        override val label: String get() = "deal_damage"

        override fun apply(actions: CombatActions) = actions.dealDamage(source, target, amount)
    }

    /** Timed modifier; an existing buff with the same [tag] on the target is replaced. */
    class AddBuff(
        val target: CombatParticipant,
        val key: AttributeKey,
        val value: StatValue,
        val durationTicks: Long,
        val tag: String,
    ) : CombatEffect() {
        init {
            require(durationTicks in 1..Int.MAX_VALUE.toLong()) { "buff duration must be between 1 and ${Int.MAX_VALUE} ticks" }
            require(tag.isNotBlank()) { "buff tag cannot be blank" }
        }

        override val label: String get() = "add_buff"

        override fun apply(actions: CombatActions) = actions.addBuff(target, key, value, durationTicks, tag)
    }

    class Message(val target: CombatParticipant, val text: String) : CombatEffect() {
        override val label: String get() = "message"

        override fun apply(actions: CombatActions) = actions.sendMessage(target, text)
    }

    class ActionBar(val target: CombatParticipant, val text: String) : CombatEffect() {
        override val label: String get() = "actionbar"

        override fun apply(actions: CombatActions) = actions.sendActionBar(target, text)
    }

    class CastSkill(
        val caster: CombatParticipant,
        val skill: String,
        val target: CombatParticipant?,
    ) : CombatEffect() {
        init {
            require(skill.isNotBlank()) { "skill name cannot be blank" }
        }

        override val label: String get() = "cast_skill"

        override fun apply(actions: CombatActions) = actions.castSkill(caster, skill, target)
    }

    private companion object {
        fun requireNonNegative(value: Double, name: String) {
            require(value.isFinite() && value >= 0.0) { "$name must be finite and non-negative" }
        }
    }
}

internal interface CombatActions {
    /** Returns the health actually removed. */
    fun applyTrueDamage(target: CombatParticipant, amount: Double): Double

    fun heal(target: CombatParticipant, amount: Double)

    fun dealDamage(source: CombatParticipant, target: CombatParticipant, amount: Double)

    fun addBuff(target: CombatParticipant, key: AttributeKey, value: StatValue, durationTicks: Long, tag: String)

    fun sendMessage(target: CombatParticipant, text: String)

    fun sendActionBar(target: CombatParticipant, text: String)

    fun castSkill(caster: CombatParticipant, skill: String, target: CombatParticipant?)

    fun canCastSkills(): Boolean
}
