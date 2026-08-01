package org.renova.renovaattribute.damage

data class DamageResult(
    val normalDamage: Double,
    val trueDamage: Double,
    val critical: Boolean,
    val lifestealRatio: Double,
)
