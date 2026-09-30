package org.renova.renovaattribute.core

import org.junit.jupiter.api.io.TempDir
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.attribute.BuiltinAttributes
import org.renova.renovaattribute.damage.CombatTrigger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AttributeRegistryTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `loads all configurable builtin display names`() {
        val builtin = copyBuiltinResource()
        val custom = Files.createDirectory(temporaryDirectory.resolve("custom"))

        val state = AttributeRegistry.prepare(builtin.toFile(), custom.toFile(), "renova")

        assertEquals(12, state.definitions.size)
        assertEquals("生命值", state.definitions.getValue(BuiltinAttributes.MAX_HEALTH).displayName)
    }

    @Test
    fun `rejects unknown formula dependencies before commit`() {
        val builtin = copyBuiltinResource()
        val custom = Files.createDirectory(temporaryDirectory.resolve("custom"))
        Files.writeString(
            custom.resolve("broken.yml"),
            """
            internal: broken
            display-name: Broken
            category: UTILITY
            default-value: 0.0
            formula: 'return context.get("renova:missing")'
            depends-on:
              - renova:missing
            """.trimIndent(),
        )

        assertFailsWith<IllegalArgumentException> {
            AttributeRegistry.prepare(builtin.toFile(), custom.toFile(), "renova")
        }
    }

    @Test
    fun `parses combat handler entries`() {
        val builtin = copyBuiltinResource()
        val custom = Files.createDirectory(temporaryDirectory.resolve("custom"))
        Files.writeString(
            custom.resolve("penetration.yml"),
            """
            internal: armor-penetration
            display-name: 护甲穿透
            combat:
              - trigger: attack
                priority: 350
                script: |
                  ctx:set_defense(ctx:defense() * (1 - value))
              - trigger: after-defense
                priority: 10
                script-file: scripts/log.lua
                run-when-zero: true
            """.trimIndent(),
        )

        val state = AttributeRegistry.prepare(builtin.toFile(), custom.toFile(), "renova")

        val combat = state.definitions.getValue(AttributeKey.of("renova", "armor-penetration")).combat
        assertEquals(2, combat.size)
        assertEquals(CombatTrigger.ATTACK, combat[0].trigger)
        assertEquals(350, combat[0].priority)
        assertEquals(CombatTrigger.AFTER_DEFENSE, combat[1].trigger)
        assertEquals("scripts/log.lua", combat[1].scriptFile)
        assertEquals(true, combat[1].runWhenZero)
    }

    @Test
    fun `rejects invalid combat entries`() {
        val invalidEntries = listOf(
            "- trigger: ATTACK\n  script: return",
            "- trigger: SOMETIMES\n  priority: 1\n  script: return",
            "- trigger: ATTACK\n  priority: 1",
            "- trigger: ATTACK\n  priority: 1\n  script: return\n  script-file: scripts/a.lua",
            "- trigger: ATTACK\n  priority: 1\n  script-file: scripts/../../evil.lua",
            "- trigger: ATTACK\n  priority: 1\n  script: return\n  prority: 2",
        )
        invalidEntries.forEachIndexed { index, entry ->
            val builtin = copyBuiltinResource()
            val custom = Files.createDirectories(temporaryDirectory.resolve("invalid-$index"))
            Files.writeString(
                custom.resolve("broken.yml"),
                "internal: broken\ndisplay-name: Broken\ncombat:\n" + entry.prependIndent("  "),
            )
            assertFailsWith<IllegalArgumentException>(entry) {
                AttributeRegistry.prepare(builtin.toFile(), custom.toFile(), "renova")
            }
        }
    }

    private fun copyBuiltinResource(): Path {
        val destination = temporaryDirectory.resolve("builtin.yml")
        requireNotNull(javaClass.getResourceAsStream("/attributes/builtin.yml")).use { input ->
            destination.outputStream().use(input::copyTo)
        }
        return destination
    }
}
