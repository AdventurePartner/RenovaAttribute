package org.renova.renovaattribute.damage

enum class VanillaReduction {
    /** Vanilla armor, resistance and protection still reduce the written-back damage. */
    KEEP,
    IGNORE_ARMOR,

    /** Ignores armor, resistance and protection; shield blocking and absorption still apply. */
    IGNORE_ALL,
}

enum class ScriptErrorPolicy {
    SKIP_HANDLER,

    /** A failing calculate-stage handler leaves the hit at its vanilla damage. */
    VANILLA,
}

data class DamageSettings(
    val enabled: Boolean = true,
    val useVanillaBaseDamage: Boolean = true,
    val defenseConstant: Double = 100.0,
    val criticalHits: Boolean = true,
    val trueDamage: Boolean = true,
    val lifesteal: Boolean = true,
    val vanillaReduction: VanillaReduction = VanillaReduction.KEEP,
    val sweepRatio: Double = 1.0,
    val scaleByAttackCooldown: Boolean = false,
    val ignoreArmorBypassesDefense: Boolean = false,
    val scriptErrorPolicy: ScriptErrorPolicy = ScriptErrorPolicy.SKIP_HANDLER,
) {
    init {
        require(defenseConstant > 0.0 && defenseConstant.isFinite()) {
            "Damage defense constant must be finite and positive"
        }
        require(sweepRatio >= 0.0 && sweepRatio.isFinite()) {
            "Sweep ratio must be finite and non-negative"
        }
    }
}

data class BuiltinPriorities(
    val damageBoost: Int = 100,
    val critical: Int = 200,
    val defense: Int = 400,
    val trueDamage: Int = 100,
    val lifesteal: Int = 200,
) {
    companion object {
        const val DAMAGE_BOOST = "damage-boost"
        const val CRITICAL = "critical"
        const val DEFENSE = "defense"
        const val TRUE_DAMAGE = "true-damage"
        const val LIFESTEAL = "lifesteal"

        @JvmField
        val NAMES = setOf(DAMAGE_BOOST, CRITICAL, DEFENSE, TRUE_DAMAGE, LIFESTEAL)

        @JvmStatic
        fun of(overrides: Map<String, Int>): BuiltinPriorities {
            val unknown = overrides.keys - NAMES
            require(unknown.isEmpty()) { "Unknown builtin combat handlers: ${unknown.joinToString()}" }
            val defaults = BuiltinPriorities()
            return BuiltinPriorities(
                damageBoost = overrides[DAMAGE_BOOST] ?: defaults.damageBoost,
                critical = overrides[CRITICAL] ?: defaults.critical,
                defense = overrides[DEFENSE] ?: defaults.defense,
                trueDamage = overrides[TRUE_DAMAGE] ?: defaults.trueDamage,
                lifesteal = overrides[LIFESTEAL] ?: defaults.lifesteal,
            )
        }
    }
}
