package org.renova.renovaattribute.lore

import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.ModifierMode
import java.util.regex.Pattern

data class LorePattern(
    val key: AttributeKey,
    val pattern: Pattern,
    val mode: ModifierMode,
    val scale: Double,
)
