package org.renova.renovaattribute.script

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.attribute.AttributeDefinition
import org.renova.renovaattribute.damage.CombatHandler
import org.renova.renovaattribute.damage.CombatHandlerSpec
import org.renova.renovaattribute.damage.CombatTrigger
import org.renova.renovaattribute.damage.DamageSession
import org.renova.renovaattribute.formula.LuaExecution
import org.renova.renovaattribute.formula.LuaFormulaException
import org.renova.renovaattribute.formula.LuaSandboxFactory
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

internal object LuaCombatScripts {
    fun compile(
        definitions: Collection<AttributeDefinition>,
        dataFolder: File,
        maxInstructions: Int,
    ): List<CombatHandler> {
        require(maxInstructions > 0) { "Lua instruction limit must be positive" }
        val entries = definitions.flatMap { definition ->
            definition.combat.mapIndexed { index, spec -> Triple(definition, index, spec) }
        }
        if (entries.isEmpty()) {
            return emptyList()
        }
        val globals = LuaSandboxFactory.create()
        val scriptRoot = File(dataFolder, CombatHandlerSpec.SCRIPT_DIRECTORY).canonicalFile
        return entries.map { (definition, index, spec) ->
            val id = "${definition.key}#$index"
            val (source, chunkName) = source(spec, id, definition.key, dataFolder, scriptRoot)
            val function = load(globals, source, chunkName, maxInstructions)
            LuaCombatHandler(
                id = id,
                attribute = definition.key,
                trigger = spec.trigger,
                priority = spec.priority,
                runWhenZero = spec.runWhenZero,
                globals = globals,
                function = function,
                maxInstructions = maxInstructions,
                chunkName = chunkName,
            )
        }
    }

    private fun source(
        spec: CombatHandlerSpec,
        id: String,
        key: AttributeKey,
        dataFolder: File,
        scriptRoot: File,
    ): Pair<String, String> {
        spec.script?.let { script ->
            // Same line as the wrapper so Lua error line numbers match the YAML block.
            return "return function(ctx, value) $script\nend" to id
        }
        val path = requireNotNull(spec.scriptFile)
        val file = File(dataFolder, path).canonicalFile
        require(file.path.startsWith(scriptRoot.path + File.separator)) {
            "script-file of $key must stay inside ${CombatHandlerSpec.SCRIPT_DIRECTORY}/: $path"
        }
        require(file.isFile) { "Combat script $path of $key does not exist" }
        return Files.readString(file.toPath(), StandardCharsets.UTF_8) to path
    }

    private fun load(globals: Globals, source: String, chunkName: String, maxInstructions: Int): LuaValue {
        val chunk = try {
            globals.load(source, chunkName, globals)
        } catch (error: LuaError) {
            throw LuaFormulaException("$chunkName: ${error.message}", error)
        }
        val function = LuaExecution.invoke(globals, chunk, LuaValue.NONE, maxInstructions, chunkName).arg1()
        require(function.isfunction()) { "$chunkName must return function(ctx, value)" }
        return function
    }
}

internal class LuaCombatHandler(
    override val id: String,
    override val attribute: AttributeKey,
    override val trigger: CombatTrigger,
    override val priority: Int,
    override val runWhenZero: Boolean,
    private val globals: Globals,
    private val function: LuaValue,
    private val maxInstructions: Int,
    private val chunkName: String,
) : CombatHandler {
    override fun handle(session: DamageSession, value: Double) {
        LuaExecution.invoke(
            globals,
            function,
            LuaValue.varargsOf(LuaDamageBinding.context(session), LuaValue.valueOf(value)),
            maxInstructions,
            chunkName,
        )
    }
}
