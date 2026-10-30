package org.renova.renovaattribute.core

import org.bukkit.entity.Entity
import org.junit.jupiter.api.io.TempDir
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.AttributeSource
import org.renova.renovaattribute.api.StatValue
import org.renova.renovaattribute.attribute.BuiltinAttributes
import org.renova.renovaattribute.formula.LuaFormulaEvaluator
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.outputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AttributeMergerTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private val guarded = AttributeKey.of("renova", "guarded")

    @AfterTest
    fun reset() {
        AttributeRegistry.commit(AttributeRegistry.PreparedState("renova", emptyMap(), emptyMap(), emptyList()))
        LuaFormulaEvaluator.clear()
    }

    @Test
    fun `keeps the source value when a formula fails for one entity`() {
        loadRegistry(
            """
            internal: guarded
            display-name: Guarded
            default-value: 1.0
            depends-on:
              - renova:max-mana
            formula: |
              if context.get('renova:max-mana') > 500 then error('too much mana') end
              return context.current * 2
            """.trimIndent(),
        )

        val healthy = AttributeMerger.compute(entity(), emptyList())
        val broken = AttributeMerger.compute(entity(), listOf(source(BuiltinAttributes.MAX_MANA, StatValue.set(1000.0))))

        assertEquals(2.0, healthy[guarded])
        assertEquals(1.0, broken[guarded])
        assertEquals(1000.0, broken[BuiltinAttributes.MAX_MANA])
    }

    private fun loadRegistry(customDefinition: String) {
        val builtin = temporaryDirectory.resolve("builtin.yml")
        requireNotNull(javaClass.getResourceAsStream("/attributes/builtin.yml")).use { input ->
            builtin.outputStream().use(input::copyTo)
        }
        val custom = Files.createDirectory(temporaryDirectory.resolve("custom"))
        Files.writeString(custom.resolve("guarded.yml"), customDefinition)
        val registry = AttributeRegistry.prepare(builtin.toFile(), custom.toFile(), "renova")
        AttributeRegistry.commit(registry)
        LuaFormulaEvaluator.commit(
            LuaFormulaEvaluator.prepare(registry.definitions.values, registry.formulaOrder, 10_000, "renova"),
        )
    }

    private fun source(key: AttributeKey, value: StatValue) = object : AttributeSource {
        override val id: String = "test:source"
        override val priority: Int = 0
        override fun provide(entity: Entity): Map<AttributeKey, StatValue> = mapOf(key to value)
    }

    private fun entity(): Entity {
        val id = UUID.randomUUID()
        return Proxy.newProxyInstance(Entity::class.java.classLoader, arrayOf(Entity::class.java)) { _, method, _ ->
            when (method.name) {
                "getUniqueId" -> id
                "hashCode" -> id.hashCode()
                "toString" -> "TestEntity($id)"
                else -> throw UnsupportedOperationException(method.name)
            }
        } as Entity
    }
}
