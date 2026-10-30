package org.renova.renovaattribute.formula

import org.luaj.vm2.Globals
import org.luaj.vm2.LoadState
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.DebugLib
import org.luaj.vm2.lib.PackageLib
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.jse.JseBaseLib
import org.luaj.vm2.lib.jse.JseMathLib

object LuaSandboxFactory {
    fun create(): Globals {
        val globals = LockedGlobals()
        globals.load(JseBaseLib())
        globals.load(PackageLib())
        globals.load(TableLib())
        globals.load(StringLib())
        globals.load(JseMathLib())
        globals.load(DebugLib())
        LoadState.install(globals)
        LuaC.install(globals)

        DISABLED_GLOBALS.forEach { globals.set(it, LuaValue.NIL) }
        globals.set("math", ReadOnlyLuaTable(globals.get("math")))
        globals.set("string", ReadOnlyLuaTable(globals.get("string")))
        globals.set("table", ReadOnlyLuaTable(globals.get("table")))
        globals.lock()
        return globals
    }

    fun createEnvironment(globals: Globals): LuaTable = globals

    private val DISABLED_GLOBALS = setOf(
        "collectgarbage",
        "coroutine",
        "debug",
        "dofile",
        "getmetatable",
        "io",
        "load",
        "loadfile",
        "luajava",
        "module",
        "os",
        "package",
        "print",
        "rawset",
        "require",
        "setmetatable",
        "_G",
    )
}

private class LockedGlobals : Globals() {
    private var locked = false

    fun lock() {
        locked = true
    }

    override fun set(key: Int, value: LuaValue) {
        ensureUnlocked()
        super.set(key, value)
    }

    override fun set(key: LuaValue, value: LuaValue) {
        ensureUnlocked()
        super.set(key, value)
    }

    override fun rawset(key: Int, value: LuaValue) {
        ensureUnlocked()
        super.rawset(key, value)
    }

    override fun rawset(key: LuaValue, value: LuaValue) {
        ensureUnlocked()
        super.rawset(key, value)
    }

    private fun ensureUnlocked() {
        if (locked) {
            error("global variables are read-only")
        }
    }
}

internal class ReadOnlyLuaTable(source: LuaValue) : LuaTable() {
    init {
        var next: Varargs = source.next(LuaValue.NIL)
        while (!next.arg1().isnil()) {
            val value = next.arg(2)
            super.rawset(next.arg1(), if (value.istable()) ReadOnlyLuaTable(value) else value)
            next = source.next(next.arg1())
        }
    }

    override fun setmetatable(metatable: LuaValue): LuaValue = error("table is read-only")

    override fun set(key: Int, value: LuaValue) {
        error("table is read-only")
    }

    override fun set(key: LuaValue, value: LuaValue) {
        error("table is read-only")
    }

    override fun rawset(key: Int, value: LuaValue) {
        error("table is read-only")
    }

    override fun rawset(key: LuaValue, value: LuaValue) {
        error("table is read-only")
    }

    override fun insert(position: Int, value: LuaValue) {
        error("table is read-only")
    }

    override fun remove(position: Int): LuaValue = error("table is read-only")
}
