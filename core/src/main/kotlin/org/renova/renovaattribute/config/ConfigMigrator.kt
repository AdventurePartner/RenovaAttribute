package org.renova.renovaattribute.config

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

object ConfigMigrator {
    private val magicEntry = Regex(
        pattern = "^\\s*-\\s*MAGIC\\s*(?:#.*)?$",
        option = RegexOption.MULTILINE,
    )
    private val indirectMagicEntry = Regex(
        pattern = "^(\\s*-\\s*)INDIRECT_MAGIC(\\s*(?:#.*)?)$",
        option = RegexOption.MULTILINE,
    )

    fun migrate(file: File): Boolean {
        val path = file.toPath()
        val source = Files.readString(path, StandardCharsets.UTF_8)
        val magicAlreadyPresent = magicEntry.containsMatchIn(source)
        val migrated = indirectMagicEntry.replace(source) { match ->
            if (magicAlreadyPresent) {
                ""
            } else {
                match.groupValues[1] + "MAGIC" + match.groupValues[2]
            }
        }
        if (migrated == source) {
            return false
        }
        Files.writeString(path, migrated, StandardCharsets.UTF_8)
        return true
    }
}
