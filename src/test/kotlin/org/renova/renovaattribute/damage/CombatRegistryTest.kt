package org.renova.renovaattribute.damage

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CombatRegistryTest {
    @TempDir
    lateinit var dataFolder: Path

    @AfterTest
    fun reset() {
        CombatRegistry.reset()
    }

    @Test
    fun `api handlers survive a reload and cannot reuse ids`() {
        val handler = RecordingHandler("other-plugin:bleed", CombatTrigger.ATTACK, 250)
        CombatRegistry.register(handler)

        val prepared = CombatRegistry.prepare(emptyList(), dataFolder.toFile(), 1_000, BuiltinPriorities(critical = 50))
        CombatRegistry.commit(prepared)

        val ids = CombatRegistry.chain().calculate.map { it.id }
        assertEquals(
            listOf(
                BuiltinCombatHandlers.CRITICAL,
                BuiltinCombatHandlers.DAMAGE_BOOST,
                "other-plugin:bleed",
                BuiltinCombatHandlers.DEFENSE,
            ),
            ids,
        )
        assertFailsWith<IllegalArgumentException> { CombatRegistry.register(handler) }
        assertFailsWith<IllegalArgumentException> {
            CombatRegistry.register(RecordingHandler(BuiltinCombatHandlers.DEFENSE, CombatTrigger.DEFENSE, 1))
        }
        assertTrue(CombatRegistry.unregister("other-plugin:bleed"))
        assertEquals(3, CombatRegistry.chain().calculate.size)
    }

    @Test
    fun `rejects unknown builtin priority names`() {
        assertFailsWith<IllegalArgumentException> { BuiltinPriorities.of(mapOf("crit" to 1)) }
    }
}
