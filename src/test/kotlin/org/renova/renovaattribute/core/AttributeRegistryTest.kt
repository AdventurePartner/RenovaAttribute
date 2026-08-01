package org.renova.renovaattribute.core

import org.junit.jupiter.api.io.TempDir
import org.renova.renovaattribute.attribute.BuiltinAttributes
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

    private fun copyBuiltinResource(): Path {
        val destination = temporaryDirectory.resolve("builtin.yml")
        requireNotNull(javaClass.getResourceAsStream("/attributes/builtin.yml")).use { input ->
            destination.outputStream().use(input::copyTo)
        }
        return destination
    }
}
