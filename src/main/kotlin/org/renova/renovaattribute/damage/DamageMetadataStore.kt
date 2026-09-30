package org.renova.renovaattribute.damage

import org.renova.renovaattribute.RenovaAttribute
import java.util.IdentityHashMap

/** Sessions of MythicMobs skill damage, keyed by the Mythic DamageMetadata instance. */
object DamageMetadataStore {
    private val pending = IdentityHashMap<Any, DamageSession>()

    internal fun record(token: Any, session: DamageSession) {
        pending.put(token, session)?.let(DamagePipeline::discard)
        RenovaAttribute.instance.server.scheduler.runTaskLater(
            RenovaAttribute.instance,
            Runnable {
                if (pending[token] === session) {
                    pending.remove(token)
                    DamagePipeline.discard(session)
                }
            },
            2L,
        )
    }

    internal fun peek(tokens: Collection<Any>): DamageSession? =
        tokens.firstNotNullOfOrNull { pending[it] }

    internal fun consume(tokens: Collection<Any>): DamageSession? {
        tokens.forEach { token ->
            pending.remove(token)?.let { return it }
        }
        return null
    }

    fun clear() {
        pending.clear()
    }
}
