package org.renova.renovaattribute.damage

import org.renova.renovaattribute.api.AttributeKey

internal object BuiltinCombatHandlers {
    const val DAMAGE_BOOST = "renova:damage-boost"
    const val CRITICAL = "renova:critical"
    const val DEFENSE = "renova:defense"
    const val TRUE_DAMAGE = "renova:true-damage"
    const val LIFESTEAL = "renova:lifesteal"

    fun create(priorities: BuiltinPriorities): List<CombatHandler> = listOf(
        DamageBoostHandler(priorities.damageBoost),
        CriticalHandler(priorities.critical),
        DefenseHandler(priorities.defense),
        TrueDamageHandler(priorities.trueDamage),
        LifestealHandler(priorities.lifesteal),
    )

    fun reduceByDefense(damage: Double, defense: Double, constant: Double): Double =
        damage * constant / (defense.coerceAtLeast(0.0) + constant)

    private abstract class Builtin(
        override val id: String,
        override val trigger: CombatTrigger,
        override val priority: Int,
    ) : CombatHandler {
        override val attribute: AttributeKey? get() = null
        override val runWhenZero: Boolean get() = true
    }

    private class DamageBoostHandler(priority: Int) : Builtin(DAMAGE_BOOST, CombatTrigger.ATTACK, priority) {
        override fun handle(session: DamageSession, value: Double) {
            session.damage = session.damage.coerceAtLeast(0.0) *
                (1.0 + session.damageBoost).coerceAtLeast(0.0)
        }
    }

    private class CriticalHandler(priority: Int) : Builtin(CRITICAL, CombatTrigger.ATTACK, priority) {
        override fun handle(session: DamageSession, value: Double) {
            if (!session.critical) {
                session.critical = session.environment.random.nextDouble() <
                    session.criticalChance.coerceIn(0.0, 1.0)
            }
            if (session.critical) {
                session.damage *= 1.0 + session.criticalDamage.coerceAtLeast(0.0)
            }
        }
    }

    private class DefenseHandler(priority: Int) : Builtin(DEFENSE, CombatTrigger.DEFENSE, priority) {
        override fun handle(session: DamageSession, value: Double) {
            if (session.ignoresArmor && session.settings.ignoreArmorBypassesDefense) {
                return
            }
            session.damage = reduceByDefense(
                session.damage.coerceAtLeast(0.0),
                session.defense,
                session.settings.defenseConstant,
            )
        }
    }

    private class TrueDamageHandler(priority: Int) : Builtin(TRUE_DAMAGE, CombatTrigger.AFTER_ATTACK, priority) {
        override fun handle(session: DamageSession, value: Double) {
            val amount = session.trueDamage.coerceAtLeast(0.0)
            if (session.cancelled || amount <= 0.0) {
                return
            }
            session.trueHealthLoss += session.environment.actions.applyTrueDamage(session.defender, amount)
        }
    }

    private class LifestealHandler(priority: Int) : Builtin(LIFESTEAL, CombatTrigger.AFTER_ATTACK, priority) {
        override fun handle(session: DamageSession, value: Double) {
            if (session.cancelled) {
                return
            }
            val amount = (session.finalDamage + session.trueHealthLoss) * session.lifesteal.coerceAtLeast(0.0)
            if (amount > 0.0) {
                session.environment.actions.heal(session.attacker, amount)
            }
        }
    }
}
