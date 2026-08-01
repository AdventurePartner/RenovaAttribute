package org.renova.renovaattribute.formula

import org.renova.renovaattribute.attribute.AttributeDefinition

interface FormulaEvaluator {
    fun compile(definitions: Collection<AttributeDefinition>)

    fun evaluate(definition: AttributeDefinition, context: FormulaContext): Double

    fun clear()
}
