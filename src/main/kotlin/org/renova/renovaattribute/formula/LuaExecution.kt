package org.renova.renovaattribute.formula

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.ZeroArgFunction

internal object LuaExecution {
    fun invoke(
        globals: Globals,
        function: LuaValue,
        arguments: Varargs,
        maxInstructions: Int,
        chunkName: String,
    ): Varargs {
        val state = globals.running.state
        val budgetHook = object : ZeroArgFunction() {
            override fun call(): LuaValue {
                throw ScriptAbortError("instruction limit exceeded")
            }
        }
        state.bytecodes = 0
        state.hookfunc = budgetHook
        state.hookcount = maxInstructions
        state.hookcall = false
        state.hookline = false
        state.hookrtrn = false
        try {
            return function.invoke(arguments)
        } catch (error: ScriptAbortError) {
            throw LuaFormulaException("$chunkName: ${error.message}", error)
        } catch (error: LuaError) {
            throw LuaFormulaException("$chunkName: ${error.message}", error)
        } finally {
            state.hookfunc = null
            state.hookcount = 0
            state.hookcall = false
            state.hookline = false
            state.hookrtrn = false
            state.inhook = false
        }
    }

    private class ScriptAbortError(message: String) : Error(message)
}

class LuaFormulaException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
