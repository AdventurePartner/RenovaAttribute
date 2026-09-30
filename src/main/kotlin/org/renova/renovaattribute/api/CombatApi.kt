package org.renova.renovaattribute.api

import org.renova.renovaattribute.damage.CombatHandler
import org.renova.renovaattribute.damage.CombatRegistry

/**
 * Registers damage handlers from other plugins. They run in the same priority order as the
 * built-in handlers and attribute scripts, and stay registered across `/ra reload`.
 */
object CombatApi {
    @JvmStatic
    fun registerHandler(handler: CombatHandler) = CombatRegistry.register(handler)

    @JvmStatic
    fun unregisterHandler(id: String): Boolean = CombatRegistry.unregister(id)

    /** All handlers in execution order: the calculate stage first, then the settle stage. */
    @JvmStatic
    fun handlers(): List<CombatHandler> = CombatRegistry.chain().all()
}
