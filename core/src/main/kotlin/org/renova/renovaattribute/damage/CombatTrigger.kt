package org.renova.renovaattribute.damage

enum class CombatStage {
    /** Before the damage is written back; handlers may change the working variables. */
    CALCULATE,

    /** One tick after the damage was dealt; working variables are read-only. */
    SETTLE,
}

enum class CombatSide {
    ATTACKER,
    DEFENDER,
}

enum class CombatTrigger(val stage: CombatStage, val side: CombatSide) {
    ATTACK(CombatStage.CALCULATE, CombatSide.ATTACKER),
    DEFENSE(CombatStage.CALCULATE, CombatSide.DEFENDER),
    AFTER_ATTACK(CombatStage.SETTLE, CombatSide.ATTACKER),
    AFTER_DEFENSE(CombatStage.SETTLE, CombatSide.DEFENDER),
    ;

    companion object {
        @JvmStatic
        fun parse(value: String): CombatTrigger? {
            val normalized = value.trim().replace('-', '_')
            return entries.firstOrNull { it.name.equals(normalized, true) }
        }
    }
}

enum class DamageOrigin {
    VANILLA,
    MYTHIC,
}
