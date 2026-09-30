package org.renova.renovaattribute.formula

import org.junit.jupiter.api.Timeout
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.ZeroArgFunction
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LuaExecutionTest {
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `nested invocation keeps the outer instruction budget`() {
        val globals = LuaSandboxFactory.create()
        val inner = compile(globals, "return function() return 1 end", "inner")
        val outer = compile(globals, "return function(nested) nested(); while true do end end", "outer")
        val nested = object : ZeroArgFunction() {
            override fun call(): LuaValue =
                LuaExecution.invoke(globals, inner, LuaValue.NONE, 1_000, "inner").arg1()
        }

        val error = assertFailsWith<LuaFormulaException> {
            LuaExecution.invoke(globals, outer, nested, 10_000, "outer")
        }
        assertTrue("instruction limit exceeded" in error.message.orEmpty(), error.message)
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `converts deep recursion into a script error`() {
        val globals = LuaSandboxFactory.create()
        val recursive = compile(
            globals,
            "local function f(n) return f(n + 1) + 1 end return function() return f(1) end",
            "recursive",
        )

        assertFailsWith<LuaFormulaException> {
            LuaExecution.invoke(globals, recursive, LuaValue.NONE, Int.MAX_VALUE, "recursive")
        }
    }

    private fun compile(globals: org.luaj.vm2.Globals, source: String, name: String): LuaValue {
        val chunk = globals.load(source, name, globals)
        return LuaExecution.invoke(globals, chunk, LuaValue.NONE, 10_000, name).arg1()
    }
}
