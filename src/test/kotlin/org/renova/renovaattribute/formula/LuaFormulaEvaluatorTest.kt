package org.renova.renovaattribute.formula

import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.StatValue
import org.renova.renovaattribute.attribute.AttributeCategory
import org.renova.renovaattribute.attribute.AttributeDefinition
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LuaFormulaEvaluatorTest {
    @AfterTest
    fun clear() {
        LuaFormulaEvaluator.clear()
    }

    @Test
    fun `evaluates compiled formula with dependent attributes`() {
        val mana = AttributeKey.of("renova", "max-mana")
        val manaDefinition = AttributeDefinition(
            key = mana,
            displayName = "魔力值",
            category = AttributeCategory.VITAL,
            defaultValue = 100.0,
        )
        val definition = AttributeDefinition(
            key = AttributeKey.of("renova", "cooldown-reduction"),
            displayName = "冷却缩减",
            category = AttributeCategory.UTILITY,
            defaultValue = 0.0,
            formula = "return context.current + context.get('renova:max-mana') * 0.001",
            dependencies = setOf(mana),
        )
        LuaFormulaEvaluator.configure(10_000)
        LuaFormulaEvaluator.compile(listOf(manaDefinition, definition))

        val result = LuaFormulaEvaluator.evaluate(
            definition,
            FormulaContext(
                contribution = StatValue.flat(0.1),
                values = mapOf(mana to 100.0),
                defaultNamespace = "renova",
            ),
        )

        assertEquals(0.2, result, 0.000001)
    }

    @Test
    fun `stops formulas that exceed instruction budget`() {
        val definition = AttributeDefinition(
            key = AttributeKey.of("renova", "blocked"),
            displayName = "阻断测试",
            category = AttributeCategory.UTILITY,
            defaultValue = 0.0,
            formula = "while true do end",
        )
        LuaFormulaEvaluator.configure(1_000)
        assertFailsWith<LuaFormulaException> {
            LuaFormulaEvaluator.compile(listOf(definition))
        }
    }

    @Test
    fun `blocks writes to global environment`() {
        val definition = AttributeDefinition(
            key = AttributeKey.of("renova", "global-write"),
            displayName = "全局写入测试",
            category = AttributeCategory.UTILITY,
            defaultValue = 0.0,
            formula = "state = 1; return state",
        )
        LuaFormulaEvaluator.configure(10_000)

        assertFailsWith<LuaFormulaException> {
            LuaFormulaEvaluator.compile(listOf(definition))
        }
    }

    @Test
    fun `blocks writes to formula context`() {
        val definition = AttributeDefinition(
            key = AttributeKey.of("renova", "context-write"),
            displayName = "上下文写入测试",
            category = AttributeCategory.UTILITY,
            defaultValue = 0.0,
            formula = "context.flat = 10; return context.flat",
        )
        LuaFormulaEvaluator.configure(10_000)

        assertFailsWith<LuaFormulaException> {
            LuaFormulaEvaluator.compile(listOf(definition))
        }
    }

    @Test
    fun `rejects unknown attribute keys instead of reading zero`() {
        val definition = AttributeDefinition(
            key = AttributeKey.of("renova", "typo"),
            displayName = "拼写错误",
            category = AttributeCategory.UTILITY,
            defaultValue = 0.0,
            formula = "return context.get('renova:max-mnaa')",
        )
        LuaFormulaEvaluator.configure(10_000)

        val error = assertFailsWith<LuaFormulaException> {
            LuaFormulaEvaluator.compile(listOf(definition))
        }
        assertTrue("Unknown attribute renova:max-mnaa" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `rejects reading formula attributes that are not declared dependencies`() {
        val first = AttributeDefinition(
            key = AttributeKey.of("renova", "first"),
            displayName = "第一",
            category = AttributeCategory.UTILITY,
            defaultValue = 1.0,
            formula = "return context.current * 2",
        )
        val second = AttributeDefinition(
            key = AttributeKey.of("renova", "second"),
            displayName = "第二",
            category = AttributeCategory.UTILITY,
            defaultValue = 0.0,
            formula = "return context.get('renova:first')",
        )
        LuaFormulaEvaluator.configure(10_000)

        val error = assertFailsWith<LuaFormulaException> {
            LuaFormulaEvaluator.compile(listOf(first, second))
        }
        assertTrue("must be declared in depends-on" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `reports formula line numbers without wrapper offset`() {
        val definition = AttributeDefinition(
            key = AttributeKey.of("renova", "line-test"),
            displayName = "行号测试",
            category = AttributeCategory.UTILITY,
            defaultValue = 0.0,
            formula = "local value = 1\nerror('boom')",
        )
        LuaFormulaEvaluator.configure(10_000)

        val error = assertFailsWith<LuaFormulaException> {
            LuaFormulaEvaluator.compile(listOf(definition))
        }
        assertTrue(":2:" in error.message.orEmpty(), error.message)
    }
}
