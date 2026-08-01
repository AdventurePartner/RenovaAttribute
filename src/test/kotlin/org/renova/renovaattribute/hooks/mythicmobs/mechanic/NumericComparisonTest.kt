package org.renova.renovaattribute.hooks.mythicmobs.mechanic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NumericComparisonTest {
    @Test
    fun `supports operators`() {
        assertTrue(NumericComparison.matches(10.0, ">=10"))
        assertTrue(NumericComparison.matches(10.0, "!=9"))
        assertFalse(NumericComparison.matches(10.0, "<10"))
    }

    @Test
    fun `supports unordered ranges`() {
        assertTrue(NumericComparison.matches(10.0, "20to5"))
        assertFalse(NumericComparison.matches(30.0, "20to5"))
    }
}
