package org.renova.renovaattribute.formula

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.StatValue
import org.renova.renovaattribute.attribute.AttributeDefinition
import java.util.Collections
import java.util.LinkedHashMap

object LuaFormulaEvaluator : FormulaEvaluator {
    internal data class CompiledFormula(
        val globals: Globals,
        val function: LuaValue,
    )

    internal data class PreparedState internal constructor(
        private val compiled: Map<AttributeKey, CompiledFormula>,
        val maxInstructions: Int,
    ) {
        internal fun formula(key: AttributeKey): CompiledFormula? = compiled[key]
    }

    @Volatile
    private var activeState = PreparedState(emptyMap(), 50_000)

    fun configure(maxInstructions: Int) {
        require(maxInstructions > 0) { "Lua instruction limit must be positive" }
        activeState = activeState.copy(maxInstructions = maxInstructions)
    }

    internal fun prepare(
        definitions: Collection<AttributeDefinition>,
        formulaOrder: Collection<AttributeDefinition>,
        maxInstructions: Int,
        defaultNamespace: String,
    ): PreparedState {
        require(maxInstructions > 0) { "Lua instruction limit must be positive" }
        val formulas = LinkedHashMap<AttributeKey, CompiledFormula>()
        definitions.filter { !it.formula.isNullOrBlank() }.forEach { definition ->
            val globals = LuaSandboxFactory.create()
            val environment = LuaSandboxFactory.createEnvironment(globals)
            val script = buildString {
                append("return function(context) ")
                appendLine(definition.formula)
                appendLine("end")
            }
            val loader = globals.load(script, definition.key.toString(), environment)
            val result = LuaExecution.invoke(
                globals,
                loader,
                LuaValue.NONE,
                maxInstructions,
                definition.key.toString(),
            ).arg1()
            require(result.isfunction()) { "Formula ${definition.key} did not compile to a function" }
            formulas[definition.key] = CompiledFormula(globals, result)
        }
        val prepared = PreparedState(
            Collections.unmodifiableMap(formulas),
            maxInstructions,
        )

        val values = definitions.associateTo(LinkedHashMap()) { it.key to it.defaultValue }
        val formulaAttributes = formulaOrder.mapTo(HashSet()) { it.key }
        formulaOrder.forEach { definition ->
            values[definition.key] = definition.clamp(
                evaluate(
                    prepared,
                    definition,
                    FormulaContext(
                        contribution = StatValue.base(definition.defaultValue),
                        values = values,
                        defaultNamespace = defaultNamespace,
                        self = definition.key,
                        dependencies = definition.dependencies,
                        formulaAttributes = formulaAttributes,
                    ),
                ),
            )
        }
        return prepared
    }

    @Synchronized
    internal fun commit(state: PreparedState) {
        activeState = state
    }

    override fun compile(definitions: Collection<AttributeDefinition>) {
        val formulaDefinitions = definitions.filter { !it.formula.isNullOrBlank() }
        commit(prepare(definitions, formulaDefinitions, activeState.maxInstructions, "renova"))
    }

    override fun evaluate(definition: AttributeDefinition, context: FormulaContext): Double =
        evaluate(activeState, definition, context)

    override fun clear() {
        activeState = PreparedState(emptyMap(), activeState.maxInstructions)
    }

    private fun evaluate(
        state: PreparedState,
        definition: AttributeDefinition,
        context: FormulaContext,
    ): Double {
        val formula = requireNotNull(state.formula(definition.key)) {
            "Formula ${definition.key} has not been compiled"
        }
        val writable = LuaTable().apply {
            rawset("base", LuaValue.valueOf(context.contribution.base))
            rawset("flat", LuaValue.valueOf(context.contribution.flat))
            rawset("percent", LuaValue.valueOf(context.contribution.percent))
            rawset("multiplier", LuaValue.valueOf(context.contribution.multiplier))
            rawset("current", LuaValue.valueOf(context.contribution.calculate()))
            rawset("get", object : OneArgFunction() {
                override fun call(argument: LuaValue): LuaValue = LuaValue.valueOf(
                    context.get(argument.checkjstring()),
                )
            })
        }
        val result = LuaExecution.invoke(
            formula.globals,
            formula.function,
            ReadOnlyLuaTable(writable),
            state.maxInstructions,
            definition.key.toString(),
        ).arg1()
        check(result.isnumber()) { "Formula ${definition.key} must return a number" }
        return result.todouble().also {
            check(it.isFinite()) { "Formula ${definition.key} returned a non-finite number" }
        }
    }
}
