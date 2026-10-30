package org.renova.renovaattribute.api

import java.util.Locale

data class AttributeKey private constructor(
    val namespace: String,
    val name: String,
) {
    init {
        require(PART_PATTERN.matches(namespace)) { "Invalid attribute namespace: $namespace" }
        require(PART_PATTERN.matches(name)) { "Invalid attribute name: $name" }
    }

    override fun toString(): String = "$namespace:$name"

    companion object {
        private val PART_PATTERN = Regex("[a-z0-9.-]+")

        @JvmStatic
        fun of(value: String): AttributeKey = parse(value, "renova")

        @JvmStatic
        fun parse(value: String, defaultNamespace: String): AttributeKey {
            val split = value.trim().split(':', limit = 2)
            return if (split.size == 2) {
                of(split[0], split[1])
            } else {
                of(defaultNamespace, split[0])
            }
        }

        @JvmStatic
        fun of(namespace: String, name: String): AttributeKey {
            val normalizedNamespace = normalize(namespace)
            val normalizedName = normalize(name)
            return AttributeKey(normalizedNamespace, normalizedName)
        }

        private fun normalize(value: String): String = value
            .trim()
            .lowercase(Locale.ROOT)
            .replace('_', '-')
    }
}
