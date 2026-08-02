package org.renova.renovaattribute.config

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.entity.EntityDamageEvent
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigMigratorTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `migrates persisted indirect magic entry without deleting neighboring values`() {
        val file = temporaryDirectory.resolve("config.yml")
        Files.writeString(
            file,
            """
            damage:
              magic-causes:
                - MAGIC
                - INDIRECT_MAGIC
                - SONIC_BOOM
            """.trimIndent(),
        )

        assertTrue(ConfigMigrator.migrate(file.toFile()))
        val migrated = Files.readString(file)
        assertFalse("INDIRECT_MAGIC" in migrated)
        assertTrue("SONIC_BOOM" in migrated)
        assertEquals(1, Regex("(?m)^\\s*-\\s*MAGIC\\s*$").findAll(migrated).count())
    }

    @Test
    fun `packaged configuration only contains supported damage causes`() {
        val file = temporaryDirectory.resolve("config.yml")
        requireNotNull(javaClass.getResourceAsStream("/config.yml")).use { input ->
            Files.newOutputStream(file).use(input::copyTo)
        }

        assertFalse(ConfigMigrator.migrate(file.toFile()))
        val config = RenovaConfig(YamlConfiguration.loadConfiguration(file.toFile()))
        assertEquals(
            setOf(
                EntityDamageEvent.DamageCause.MAGIC,
                EntityDamageEvent.DamageCause.SONIC_BOOM,
                EntityDamageEvent.DamageCause.DRAGON_BREATH,
            ),
            config.magicCauses,
        )
    }
}
