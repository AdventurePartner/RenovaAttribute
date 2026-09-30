package org.renova.renovaattribute.i18n

import org.bukkit.configuration.file.YamlConfiguration
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class I18nTest {
    private val colorize: (String) -> String = { it.replace('&', '§') }

    private fun yaml(text: String) = YamlConfiguration().apply { loadFromString(text.trimIndent()) }

    private fun commit(user: String, defaults: String? = null) {
        I18n.commit(I18n.prepare(defaults?.let(::yaml), yaml(user), "language/test.yml", colorize))
    }

    @Test
    fun `strings are one line, lists are many lines and empty values send nothing`() {
        commit(
            """
            single: '&aone'
            multi:
              - '&afirst'
              - 'second'
            blank: ''
            none: []
            """,
        )

        assertEquals(listOf("§aone"), I18n.lines("single"))
        assertEquals(listOf("§afirst", "second"), I18n.lines("multi"))
        assertEquals(emptyList(), I18n.lines("blank"))
        assertEquals(emptyList(), I18n.lines("none"))
        assertEquals("§afirst\nsecond", I18n.text("multi"))
    }

    @Test
    fun `user entries override bundled defaults and missing keys fall back to them`() {
        commit(
            user = """
            command:
              reloaded: 'custom'
            """,
            defaults = """
            prefix: 'P '
            command:
              reloaded: 'default'
              reload-failed: 'failed'
            """,
        )

        assertEquals(listOf("custom"), I18n.lines("command.reloaded"))
        assertEquals(listOf("failed"), I18n.lines("command.reload-failed"))
    }

    @Test
    fun `placeholders are filled after coloring so values keep their ampersands`() {
        commit(
            """
            prefix: '&6[R] '
            greet: '{prefix}&a{player} {unknown}'
            """,
        )

        assertEquals(listOf("§6[R] §a&cBob {unknown}"), I18n.lines("greet", mapOf("player" to "&cBob")))
    }

    @Test
    fun `an empty prefix removes it from every message`() {
        commit(
            """
            prefix: ''
            greet: '{prefix}hi'
            """,
        )

        assertEquals(listOf("hi"), I18n.lines("greet"))
    }

    @Test
    fun `unknown keys yield the key itself`() {
        commit("prefix: ''")

        assertEquals(listOf("command.missing"), I18n.lines("command.missing"))
    }

    @Test
    fun `rejects entries that are neither strings nor lists`() {
        val error = assertFailsWith<IllegalArgumentException> { commit("reloaded: 12") }
        assertTrue("reloaded" in error.message.orEmpty())
        assertFailsWith<IllegalArgumentException> { commit("prefix: ['a', 'b']") }
    }

    @Test
    fun `bundled language file defines every key the plugin sends`() {
        val bundled = I18nTest::class.java.getResourceAsStream("/language/${I18n.DEFAULT_LANGUAGE}.yml")!!.use {
            YamlConfiguration().apply { load(InputStreamReader(it, StandardCharsets.UTF_8)) }
        }
        I18n.commit(I18n.prepare(null, bundled, "language/zh_CN.yml", colorize))

        val labels = listOf(
            "damage", "damage-boost", "crit-chance", "crit-damage", "critical",
            "defense", "true-damage", "lifesteal", "cancelled",
        ).map { "debug.labels.$it" }
        val keys = listOf(
            "command.no-permission", "command.player-only", "command.reloaded", "command.reload-failed",
            "command.help.player", "command.help.admin", "command.unknown-attribute",
            "command.info-header", "command.info-line",
            "command.player-not-found", "command.debug-enabled", "command.debug-disabled",
            "debug.header", "debug.entry", "debug.entry-error", "debug.result", "debug.no-change",
        ) + labels
        keys.forEach { key -> assertTrue(key !in I18n.lines(key), "missing $key") }
    }
}
