package org.renova.renovaattribute.hooks.placeholderapi

import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.attribute.AttributeCategory
import org.renova.renovaattribute.attribute.AttributeDefinition
import org.renova.renovaattribute.attribute.AttributeFormat
import org.renova.renovaattribute.core.AttributeRegistry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaceholderValuesTest {
    @BeforeTest
    fun registerDefinitions() {
        val definitions = listOf(
            definition(AttributeKey.of("renova", "physical-damage"), 12.0, AttributeFormat.NUMBER),
            definition(AttributeKey.of("renova", "critical-chance"), 0.15, AttributeFormat.PERCENT),
            definition(AttributeKey.of("mypack", "fire-damage"), 3.0, AttributeFormat.NUMBER),
        ).associateBy(AttributeDefinition::key)
        AttributeRegistry.commit(AttributeRegistry.PreparedState("renova", definitions, definitions, emptyList()))
    }

    @AfterTest
    fun reset() {
        AttributeRegistry.commit(AttributeRegistry.PreparedState("renova", emptyMap(), emptyMap(), emptyList()))
    }

    @Test
    fun `formats like the info command`() {
        assertEquals("12", PlaceholderValues.request(null, "physical-damage"))
        assertEquals("15%", PlaceholderValues.request(null, "critical-chance"))
    }

    @Test
    fun `value prefix prints the plain number`() {
        assertEquals("0.15", PlaceholderValues.request(null, "value_critical-chance"))
        assertEquals("12", PlaceholderValues.request(null, "VALUE_physical-damage"))
    }

    @Test
    fun `accepts underscores and namespaced keys`() {
        assertEquals("12", PlaceholderValues.request(null, "physical_damage"))
        assertEquals("15%", PlaceholderValues.request(null, "renova:critical_chance"))
        assertEquals("3", PlaceholderValues.request(null, "mypack:fire-damage"))
        assertEquals("3", PlaceholderValues.request(null, "value_mypack:fire_damage"))
    }

    @Test
    fun `unknown or malformed keys stay unresolved`() {
        assertNull(PlaceholderValues.request(null, "missing"))
        assertNull(PlaceholderValues.request(null, "fire-damage"))
        assertNull(PlaceholderValues.request(null, "bad key!"))
        assertNull(PlaceholderValues.request(null, "value_"))
    }

    private fun definition(key: AttributeKey, defaultValue: Double, format: AttributeFormat) = AttributeDefinition(
        key = key,
        displayName = key.name,
        category = AttributeCategory.UTILITY,
        defaultValue = defaultValue,
        format = format,
    )
}
