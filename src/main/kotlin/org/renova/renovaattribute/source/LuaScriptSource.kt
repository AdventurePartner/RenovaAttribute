package org.renova.renovaattribute.source

import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaClosure
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Prototype
import org.luaj.vm2.Varargs
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.AttributeSource
import org.renova.renovaattribute.api.StatValue
import org.renova.renovaattribute.formula.LuaExecution
import org.renova.renovaattribute.formula.LuaSandboxFactory
import org.renova.renovaattribute.formula.ReadOnlyLuaTable
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.io.StringReader

class LuaScriptSource(
    override val id: String,
    override val priority: Int,
    scriptFile: File,
    private val defaultNamespace: String = "renova",
    private val maxInstructions: Int = 50_000,
) : AttributeSource {
    private val globals: Globals = LuaSandboxFactory.create()
    private val prototype: Prototype
    private val chunkName = scriptFile.name

    init {
        require(scriptFile.isFile) { "Lua attribute source does not exist: $scriptFile" }
        require(maxInstructions > 0) { "Lua instruction limit must be positive" }
        val source = Files.readString(scriptFile.toPath(), StandardCharsets.UTF_8)
        prototype = globals.compilePrototype(StringReader(source), chunkName)
        createFunction()
    }

    override fun provide(entity: Entity): Map<AttributeKey, StatValue> {
        val writableEntity = LuaTable().apply {
            rawset("uuid", LuaValue.valueOf(entity.uniqueId.toString()))
            rawset("type", LuaValue.valueOf(entity.type.key.toString()))
            rawset("name", LuaValue.valueOf(entity.name))
            if (entity is LivingEntity) {
                rawset("health", LuaValue.valueOf(entity.health))
            }
        }
        val result = LuaExecution.invoke(
            globals,
            createFunction(),
            ReadOnlyLuaTable(writableEntity),
            maxInstructions,
            id,
        ).arg1()
        require(result.istable()) { "Lua attribute source '$id' must return a table" }
        return readContributions(result.checktable())
    }

    private fun createFunction(): LuaValue {
        val environment = LuaSandboxFactory.createEnvironment(globals)
        val result = LuaExecution.invoke(
            globals,
            LuaClosure(prototype, environment),
            LuaValue.NONE,
            maxInstructions,
            chunkName,
        ).arg1()
        require(result.isfunction()) { "$chunkName must return a function" }
        return result
    }

    private fun readContributions(table: LuaTable): Map<AttributeKey, StatValue> {
        val result = linkedMapOf<AttributeKey, StatValue>()
        var next: Varargs = table.next(LuaValue.NIL)
        while (!next.arg1().isnil()) {
            val key = AttributeKey.parse(next.arg1().checkjstring(), defaultNamespace)
            val value = next.arg(2)
            result[key] = if (value.isnumber()) {
                StatValue.flat(value.checkdouble())
            } else {
                readContribution(value.checktable())
            }
            next = table.next(next.arg1())
        }
        return result
    }

    private fun readContribution(table: LuaTable): StatValue {
        val set = table.get("set").takeUnless { it.isnil() }?.checkdouble()
        val multiply = table.get("multiply").optdouble(0.0)
        return StatValue(
            base = table.get("base").optdouble(0.0),
            flat = table.get("flat").optdouble(0.0),
            percent = table.get("percent").optdouble(0.0),
            multiplier = 1.0 + multiply,
            setValue = set,
        )
    }
}
