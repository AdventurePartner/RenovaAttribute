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
        val previousHook = state.hookfunc
        val previousCount = state.hookcount
        val previousBytecodes = state.bytecodes
        val previousCall = state.hookcall
        val previousLine = state.hookline
        val previousReturn = state.hookrtrn
        val previousInHook = state.inhook
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
        state.inhook = false
        try {
            return function.invoke(arguments)
        } catch (error: ScriptAbortError) {
            throw LuaFormulaException("$chunkName: ${error.message}", error)
        } catch (error: LuaError) {
            throw LuaFormulaException("$chunkName: ${error.message}", error)
        } catch (error: StackOverflowError) {
            throw LuaFormulaException("$chunkName: stack overflow", error)
        } catch (error: LuaFormulaException) {
            throw error
        } catch (error: RuntimeException) {
            // Tail calls into Kotlin callbacks run outside LuaClosure's own error wrapping.
            throw LuaFormulaException("$chunkName: ${error.message}", error)
        } finally {
            state.hookfunc = previousHook
            state.hookcount = previousCount
            state.bytecodes = previousBytecodes
            state.hookcall = previousCall
            state.hookline = previousLine
            state.hookrtrn = previousReturn
            state.inhook = previousInHook
        }
    }

    private class ScriptAbortError(message: String) : Error(message)
}

class LuaFormulaException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
