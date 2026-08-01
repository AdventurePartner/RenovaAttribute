package org.renova.renovaattribute.damage

import org.bukkit.entity.LivingEntity
import org.renova.renovaattribute.RenovaAttribute
import java.util.IdentityHashMap

object DamageMetadataStore {
    private val pending = IdentityHashMap<Any, PendingDamage>()

    fun record(token: Any, attacker: LivingEntity, result: DamageResult) {
        pending[token] = PendingDamage(attacker.uniqueId, result)
        RenovaAttribute.instance.server.scheduler.runTaskLater(
            RenovaAttribute.instance,
            Runnable { pending.remove(token) },
            2L,
        )
    }

    fun consume(tokens: Collection<Any>): PendingDamage? {
        tokens.forEach { token ->
            pending.remove(token)?.let { return it }
        }
        return null
    }

    fun clear() {
        pending.clear()
    }
}
