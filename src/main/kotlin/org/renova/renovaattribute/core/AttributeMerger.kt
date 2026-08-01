package org.renova.renovaattribute.core

import org.bukkit.entity.Entity
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.AttributeSnapshot
import org.renova.renovaattribute.api.AttributeSource
import org.renova.renovaattribute.api.StatValue
import org.renova.renovaattribute.formula.FormulaContext
import org.renova.renovaattribute.formula.LuaFormulaEvaluator
import java.util.LinkedHashMap
import java.util.Collections

object AttributeMerger {
    fun compute(entity: Entity, sources: Collection<AttributeSource>): AttributeSnapshot {
        val accumulators = LinkedHashMap<AttributeKey, StatValue>()
        AttributeRegistry.definitions().forEach { definition ->
            accumulators[definition.key] = StatValue.base(definition.defaultValue)
        }

        sources.sortedWith(compareBy<AttributeSource> { it.priority }.thenBy { it.id })
            .forEach { source ->
                source.provide(entity).forEach { (key, contribution) ->
                    val current = accumulators[key] ?: StatValue()
                    accumulators[key] = current.merge(contribution)
                }
            }

        val values = LinkedHashMap<AttributeKey, Double>()
        accumulators.forEach { (key, value) ->
            val calculated = value.calculate()
            values[key] = AttributeRegistry[key]?.clamp(calculated) ?: calculated
        }

        AttributeRegistry.formulaOrder().forEach { definition ->
            val contribution = accumulators[definition.key]
                ?: StatValue.base(definition.defaultValue)
            val calculated = LuaFormulaEvaluator.evaluate(
                definition,
                FormulaContext(
                    contribution = contribution,
                    values = values,
                    defaultNamespace = AttributeRegistry.defaultNamespace,
                ),
            )
            values[definition.key] = definition.clamp(calculated)
        }
        return AttributeSnapshot(
            entity.uniqueId,
            Collections.unmodifiableMap(LinkedHashMap(values)),
        )
    }
}
