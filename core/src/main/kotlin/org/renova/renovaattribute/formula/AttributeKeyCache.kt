package org.renova.renovaattribute.formula

import org.renova.renovaattribute.api.AttributeKey
import java.util.concurrent.ConcurrentHashMap

internal object AttributeKeyCache {
    private const val MAX_ENTRIES = 4096
    private val entries = ConcurrentHashMap<String, AttributeKey>()

    fun parse(value: String, defaultNamespace: String): AttributeKey {
        val cacheKey = "$defaultNamespace\u0000$value"
        entries[cacheKey]?.let { return it }
        val parsed = AttributeKey.parse(value, defaultNamespace)
        if (entries.size >= MAX_ENTRIES) {
            entries.clear()
        }
        entries[cacheKey] = parsed
        return parsed
    }
}
