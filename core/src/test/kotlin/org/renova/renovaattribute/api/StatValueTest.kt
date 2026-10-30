package org.renova.renovaattribute.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StatValueTest {
    @Test
    fun `combines all modifier layers`() {
        val value = StatValue.base(100.0)
            .merge(StatValue.flat(20.0))
            .merge(StatValue.percent(0.5))
            .merge(StatValue.multiply(0.25))

        assertEquals(225.0, value.calculate(), 0.000001)
    }

    @Test
    fun `set contribution discards lower priority layers`() {
        val value = StatValue.base(100.0)
            .merge(StatValue.percent(1.0))
            .merge(StatValue.set(40.0))
            .merge(StatValue.flat(10.0))

        assertEquals(50.0, value.calculate(), 0.000001)
    }

    @Test
    fun `multiply mode accepts percentage delta`() {
        assertEquals(1.2, StatValue.of(ModifierMode.MULTIPLY, 0.2).multiplier, 0.000001)
    }

    @Test
    fun `rejects arithmetic overflow`() {
        assertFailsWith<IllegalArgumentException> {
            StatValue(base = Double.MAX_VALUE, multiplier = 2.0).calculate()
        }
    }
}
