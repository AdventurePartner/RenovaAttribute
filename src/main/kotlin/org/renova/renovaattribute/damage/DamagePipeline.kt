package org.renova.renovaattribute.damage

import org.renova.renovaattribute.attribute.BuiltinAttributes
import org.renova.renovaattribute.core.AttributeServiceImpl
import java.util.concurrent.ThreadLocalRandom

object DamagePipeline {
    var enabled: Boolean = true
        private set
    var useVanillaBaseDamage: Boolean = true
        private set
    var defenseConstant: Double = 100.0
        private set
    var criticalHits: Boolean = true
        private set
    var trueDamage: Boolean = true
        private set
    var lifesteal: Boolean = true
        private set

    fun configure(
        enabled: Boolean,
        useVanillaBaseDamage: Boolean,
        defenseConstant: Double,
        criticalHits: Boolean,
        trueDamage: Boolean,
        lifesteal: Boolean,
    ) {
        require(defenseConstant > 0.0 && defenseConstant.isFinite()) {
            "Damage defense constant must be finite and positive"
        }
        this.enabled = enabled
        this.useVanillaBaseDamage = useVanillaBaseDamage
        this.defenseConstant = defenseConstant
        this.criticalHits = criticalHits
        this.trueDamage = trueDamage
        this.lifesteal = lifesteal
    }

    fun createContext(
        attacker: org.bukkit.entity.LivingEntity,
        defender: org.bukkit.entity.LivingEntity,
        originalDamage: Double,
        type: DamageType,
    ): DamageContext = DamageContext(
        attacker = attacker,
        defender = defender,
        attackerAttributes = AttributeServiceImpl.snapshot(attacker),
        defenderAttributes = AttributeServiceImpl.snapshot(defender),
        originalDamage = originalDamage,
        type = type,
    )

    @JvmOverloads
    fun calculate(
        context: DamageContext,
        criticalRoll: Double = ThreadLocalRandom.current().nextDouble(),
    ): DamageResult {
        if (!enabled) {
            return DamageResult(context.originalDamage, 0.0, false, 0.0)
        }
        val damageKey = when (context.type) {
            DamageType.PHYSICAL -> BuiltinAttributes.PHYSICAL_DAMAGE
            DamageType.MAGIC -> BuiltinAttributes.MAGIC_DAMAGE
        }
        val defenseKey = when (context.type) {
            DamageType.PHYSICAL -> BuiltinAttributes.PHYSICAL_DEFENSE
            DamageType.MAGIC -> BuiltinAttributes.MAGIC_DEFENSE
        }
        val attributeDamage = context.attackerAttributes[damageKey]
        var normalDamage = if (useVanillaBaseDamage) {
            context.originalDamage + attributeDamage
        } else {
            attributeDamage
        }
        normalDamage = normalDamage.coerceAtLeast(0.0)
        normalDamage *= 1.0 + context.attackerAttributes[BuiltinAttributes.DAMAGE_BOOST]

        val criticalChance = context.attackerAttributes[BuiltinAttributes.CRITICAL_CHANCE]
            .coerceIn(0.0, 1.0)
        val critical = criticalHits && criticalRoll < criticalChance
        if (critical) {
            normalDamage *= 1.0 + context.attackerAttributes[BuiltinAttributes.CRITICAL_DAMAGE]
                .coerceAtLeast(0.0)
        }

        val defense = context.defenderAttributes[defenseKey].coerceAtLeast(0.0)
        normalDamage = reduceByDefense(normalDamage.coerceAtLeast(0.0), defense)

        return DamageResult(
            normalDamage = normalDamage.coerceAtLeast(0.0),
            trueDamage = if (trueDamage) {
                context.attackerAttributes[BuiltinAttributes.TRUE_DAMAGE].coerceAtLeast(0.0)
            } else {
                0.0
            },
            critical = critical,
            lifestealRatio = if (lifesteal) {
                context.attackerAttributes[BuiltinAttributes.LIFESTEAL].coerceAtLeast(0.0)
            } else {
                0.0
            },
        )
    }

    fun reduceByDefense(damage: Double, defense: Double): Double =
        damage * defenseConstant / (defense.coerceAtLeast(0.0) + defenseConstant)
}
