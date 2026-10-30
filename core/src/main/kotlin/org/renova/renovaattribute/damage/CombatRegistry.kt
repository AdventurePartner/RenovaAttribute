package org.renova.renovaattribute.damage

import org.renova.renovaattribute.attribute.AttributeDefinition
import org.renova.renovaattribute.script.LuaCombatScripts
import java.io.File
import java.util.LinkedHashMap

/**
 * Holds the configured handlers (built-ins + attribute scripts, replaced on reload) and the
 * handlers registered through [org.renova.renovaattribute.api.CombatApi], which survive reloads.
 */
object CombatRegistry {
    class PreparedState internal constructor(internal val handlers: List<CombatHandler>)

    private var configured: List<CombatHandler> = BuiltinCombatHandlers.create(BuiltinPriorities())
    private val runtime = LinkedHashMap<String, CombatHandler>()

    @Volatile
    private var chain = CombatChain(configured)

    fun chain(): CombatChain = chain

    internal fun prepare(
        definitions: Collection<AttributeDefinition>,
        dataFolder: File,
        maxInstructions: Int,
        priorities: BuiltinPriorities,
    ): PreparedState {
        val handlers = BuiltinCombatHandlers.create(priorities) +
            LuaCombatScripts.compile(definitions, dataFolder, maxInstructions)
        val runtimeIds = synchronized(this) { runtime.keys.toSet() }
        handlers.forEach { handler ->
            require(handler.id !in runtimeIds) {
                "Combat handler ${handler.id} conflicts with a handler registered through the API"
            }
        }
        CombatChain(handlers)
        return PreparedState(handlers)
    }

    @Synchronized
    internal fun commit(state: PreparedState) {
        configured = state.handlers
        rebuild()
    }

    @Synchronized
    fun register(handler: CombatHandler) {
        require(configured.none { it.id == handler.id } && handler.id !in runtime) {
            "Combat handler ${handler.id} is already registered"
        }
        runtime[handler.id] = handler
        rebuild()
    }

    @Synchronized
    fun unregister(id: String): Boolean {
        if (runtime.remove(id) == null) {
            return false
        }
        rebuild()
        return true
    }

    @Synchronized
    internal fun reset() {
        configured = BuiltinCombatHandlers.create(BuiltinPriorities())
        runtime.clear()
        rebuild()
    }

    private fun rebuild() {
        chain = CombatChain(configured + runtime.values)
    }
}
