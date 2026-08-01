package org.renova.renovaattribute.api

import kotlin.test.Test
import kotlin.test.assertEquals

class AttributeKeyTest {
    @Test
    fun `normalizes and interns keys`() {
        val first = AttributeKey.parse("Renova:PHYSICAL_DAMAGE", "other")
        val second = AttributeKey.of("renova", "physical-damage")

        assertEquals("renova:physical-damage", first.toString())
        assertEquals(first, second)
    }

    @Test
    fun `uses supplied default namespace`() {
        assertEquals(
            "custom:power",
            AttributeKey.parse("POWER", "custom").toString(),
        )
    }
}
