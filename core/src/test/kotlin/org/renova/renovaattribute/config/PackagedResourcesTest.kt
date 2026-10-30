package org.renova.renovaattribute.config

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.io.TempDir
import org.renova.renovaattribute.core.AttributeRegistry
import org.renova.renovaattribute.damage.BuiltinPriorities
import org.renova.renovaattribute.damage.CombatRegistry
import org.renova.renovaattribute.damage.ScriptErrorPolicy
import org.renova.renovaattribute.damage.VanillaReduction
import org.renova.renovaattribute.formula.LuaFormulaEvaluator
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PackagedResourcesTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private val examples = listOf(
        "example",
        "armor-penetration",
        "dodge",
        "critical-resistance",
        "execute",
        "thorns",
        "fire-resistance",
        "pvp-reduction",
    )

    @AfterTest
    fun reset() {
        CombatRegistry.reset()
    }

    @Test
    fun `every packaged example attribute loads and compiles`() {
        val builtin = copy("/attributes/builtin.yml", temporaryDirectory.resolve("builtin.yml"))
        val custom = Files.createDirectory(temporaryDirectory.resolve("custom"))
        examples.forEach { name ->
            copy("/attributes/custom/$name.yml.example", custom.resolve("$name.yml"))
        }

        val registry = AttributeRegistry.prepare(builtin.toFile(), custom.toFile(), "renova")
        LuaFormulaEvaluator.prepare(registry.definitions.values, registry.formulaOrder, 50_000, "renova")
        val combat = CombatRegistry.prepare(registry.definitions.values, temporaryDirectory.toFile(), 20_000, BuiltinPriorities())
        CombatRegistry.commit(combat)

        assertEquals(12 + examples.size, registry.definitions.size)
        assertEquals(5 + examples.size - 1, CombatRegistry.chain().all().size)
    }

    @Test
    fun `configuration files from before the combat update still load`() {
        val file = copy("/config.yml", temporaryDirectory.resolve("config.yml"))
        val yaml = YamlConfiguration.loadConfiguration(file.toFile())
        listOf(
            "damage.vanilla-reduction",
            "damage.sweep-ratio",
            "damage.scale-by-attack-cooldown",
            "damage.script-error-policy",
            "damage.builtin-priorities",
            "lua.max-instructions-per-combat-script",
            "mythicmobs.ignore-armor-bypasses-defense",
        ).forEach { yaml.set(it, null) }
        assertFalse(yaml.isSet("damage.sweep-ratio"))

        val config = RenovaConfig(yaml)

        assertEquals(VanillaReduction.KEEP, config.vanillaReduction)
        assertEquals(1.0, config.sweepRatio)
        assertEquals(ScriptErrorPolicy.SKIP_HANDLER, config.scriptErrorPolicy)
        assertEquals(BuiltinPriorities(), config.builtinPriorities)
        assertEquals(20_000, config.maxCombatLuaInstructions)
    }

    private fun copy(resource: String, destination: Path): Path {
        requireNotNull(javaClass.getResourceAsStream(resource)) { "Missing $resource" }.use { input ->
            Files.newOutputStream(destination).use(input::copyTo)
        }
        return destination
    }
}
