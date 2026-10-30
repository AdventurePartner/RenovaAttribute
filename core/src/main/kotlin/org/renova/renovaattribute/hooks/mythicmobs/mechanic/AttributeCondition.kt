package org.renova.renovaattribute.hooks.mythicmobs.mechanic

import io.lumine.mythic.api.adapters.AbstractEntity
import io.lumine.mythic.api.config.MythicLineConfig
import io.lumine.mythic.api.skills.SkillMetadata
import io.lumine.mythic.api.skills.conditions.IEntityCondition
import io.lumine.mythic.api.skills.conditions.ISkillMetaComparisonCondition
import io.lumine.mythic.api.skills.placeholders.PlaceholderString
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.core.AttributeRegistry
import org.renova.renovaattribute.hooks.mythicmobs.MythicAttributeLookup
import org.renova.renovaattribute.hooks.mythicmobs.MythicHook

class AttributeCondition(
    config: MythicLineConfig,
    argument: String,
) : IEntityCondition, ISkillMetaComparisonCondition {
    private val attribute: PlaceholderString = config.getPlaceholderString(
        arrayOf("attribute", "attr", "key"),
        "",
    )
    private val expression: PlaceholderString = config.getPlaceholderString(
        arrayOf("value", "v", "amount", "a"),
        if (argument.isBlank()) "!=0" else argument,
    )

    override fun check(entity: AbstractEntity): Boolean {
        if (!MythicHook.conditionEnabled) {
            return false
        }
        return compare(
            entity,
            attribute.get(entity),
            expression.get(entity),
        )
    }

    override fun check(metadata: SkillMetadata, target: AbstractEntity): Boolean {
        if (!MythicHook.conditionEnabled) {
            return false
        }
        return compare(
            target,
            attribute.get(metadata, target),
            expression.get(metadata, target),
        )
    }

    private fun compare(entity: AbstractEntity, rawKey: String, expression: String): Boolean {
        if (rawKey.isBlank()) {
            return false
        }
        val key = AttributeKey.parse(rawKey, AttributeRegistry.defaultNamespace)
        val actual = MythicAttributeLookup.get(entity.bukkitEntity, key)
        return NumericComparison.matches(actual, expression)
    }
}

internal object NumericComparison {
    private val comparison = Regex("^\\s*(>=|<=|==|!=|>|<|=)?\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*$")
    private val range = Regex(
        "^\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*(?:to|-)\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*$",
        RegexOption.IGNORE_CASE,
    )

    fun matches(actual: Double, expression: String): Boolean {
        range.matchEntire(expression)?.let { match ->
            val first = match.groupValues[1].toDouble()
            val second = match.groupValues[2].toDouble()
            return actual in minOf(first, second)..maxOf(first, second)
        }
        val match = requireNotNull(comparison.matchEntire(expression)) {
            "Invalid attribute comparison: $expression"
        }
        val expected = match.groupValues[2].toDouble()
        return when (match.groupValues[1].ifEmpty { "=" }) {
            ">" -> actual > expected
            ">=" -> actual >= expected
            "<" -> actual < expected
            "<=" -> actual <= expected
            "!=" -> actual != expected
            "=", "==" -> actual == expected
            else -> false
        }
    }
}
