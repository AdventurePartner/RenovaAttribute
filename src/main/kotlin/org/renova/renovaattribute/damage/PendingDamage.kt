package org.renova.renovaattribute.damage

import java.util.UUID

data class PendingDamage(
    val attackerId: UUID,
    val result: DamageResult,
)
