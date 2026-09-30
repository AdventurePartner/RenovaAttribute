package org.renova.renovaattribute.core

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.ModifierMode
import org.renova.renovaattribute.attribute.AttributeCategory
import org.renova.renovaattribute.attribute.AttributeDefinition
import org.renova.renovaattribute.attribute.AttributeFormat
import org.renova.renovaattribute.damage.CombatHandlerSpec
import org.renova.renovaattribute.damage.CombatTrigger
import java.io.File
import java.util.Collections
import java.util.LinkedHashMap

object AttributeRegistry {
    data class PreparedState internal constructor(
        val namespace: String,
        val configuredDefinitions: Map<AttributeKey, AttributeDefinition>,
        val definitions: Map<AttributeKey, AttributeDefinition>,
        val formulaOrder: List<AttributeDefinition>,
    )

    @Volatile
    private var runtimeDefinitions: Map<AttributeKey, AttributeDefinition> = emptyMap()

    @Volatile
    private var activeState = PreparedState("renova", emptyMap(), emptyMap(), emptyList())

    val defaultNamespace: String
        get() = activeState.namespace

    fun prepare(builtinFile: File, customDirectory: File, namespace: String): PreparedState {
        val configured = LinkedHashMap<AttributeKey, AttributeDefinition>()
        loadBuiltin(builtinFile, namespace, configured)
        if (!customDirectory.exists() && !customDirectory.mkdirs()) {
            throw IllegalStateException("Cannot create custom attribute directory: $customDirectory")
        }
        customDirectory.listFiles { file -> file.isFile && file.extension.equals("yml", true) }
            ?.sortedBy { it.name }
            ?.forEach { loadCustom(it, namespace, configured) }

        val combined = LinkedHashMap(configured)
        combined.putAll(runtimeDefinitions)
        val order = formulaOrder(combined)
        return PreparedState(
            namespace = namespace,
            configuredDefinitions = immutableMap(configured),
            definitions = immutableMap(combined),
            formulaOrder = Collections.unmodifiableList(order),
        )
    }

    @Synchronized
    fun commit(state: PreparedState) {
        activeState = state
    }

    @Synchronized
    fun load(builtinFile: File, customDirectory: File, namespace: String) {
        commit(prepare(builtinFile, customDirectory, namespace))
    }

    @Synchronized
    fun register(definition: AttributeDefinition) {
        require(definition.formula.isNullOrBlank()) {
            "Runtime formula attributes must be loaded from configuration"
        }
        require(definition.dependencies.isEmpty()) {
            "Runtime attributes without formulas cannot declare dependencies"
        }
        require(definition.combat.isEmpty()) {
            "Runtime attributes cannot declare combat scripts; register a CombatHandler instead"
        }
        val candidateRuntime = LinkedHashMap(runtimeDefinitions)
        candidateRuntime[definition.key] = definition
        val combined = LinkedHashMap(activeState.configuredDefinitions)
        combined.putAll(candidateRuntime)
        runtimeDefinitions = immutableMap(candidateRuntime)
        activeState = activeState.copy(
            definitions = immutableMap(combined),
            formulaOrder = Collections.unmodifiableList(formulaOrder(combined)),
        )
    }

    @Synchronized
    fun unregister(key: AttributeKey): Boolean {
        if (key !in runtimeDefinitions) {
            return false
        }
        val candidateRuntime = LinkedHashMap(runtimeDefinitions)
        candidateRuntime.remove(key)
        val combined = LinkedHashMap(activeState.configuredDefinitions)
        combined.putAll(candidateRuntime)
        val order = formulaOrder(combined)
        runtimeDefinitions = immutableMap(candidateRuntime)
        activeState = activeState.copy(
            definitions = immutableMap(combined),
            formulaOrder = Collections.unmodifiableList(order),
        )
        return true
    }

    operator fun get(key: AttributeKey): AttributeDefinition? = activeState.definitions[key]

    fun definitions(): List<AttributeDefinition> =
        Collections.unmodifiableList(activeState.definitions.values.toList())

    fun formulaOrder(): List<AttributeDefinition> = activeState.formulaOrder

    private fun loadBuiltin(
        file: File,
        namespace: String,
        destination: MutableMap<AttributeKey, AttributeDefinition>,
    ) {
        val yaml = YamlConfiguration.loadConfiguration(file)
        val attributes = requireNotNull(yaml.getConfigurationSection("attributes")) {
            "Missing attributes section in ${file.path}"
        }
        attributes.getKeys(false).forEach { name ->
            val section = requireNotNull(attributes.getConfigurationSection(name)) {
                "Attribute $name must be a section in ${file.path}"
            }
            addDefinition(
                destination,
                parseDefinition(section, AttributeKey.of(namespace, name), file.path, namespace),
                file.path,
            )
        }
    }

    private fun loadCustom(
        file: File,
        defaultNamespace: String,
        destination: MutableMap<AttributeKey, AttributeDefinition>,
    ) {
        val yaml = YamlConfiguration.loadConfiguration(file)
        val section = yaml.getConfigurationSection("attribute") ?: yaml
        val rawName = when {
            section.isString("internal") -> requireNotNull(section.getString("internal"))
            section.isString("name") -> requireNotNull(section.getString("name"))
            section.isSet("internal") || section.isSet("name") -> {
                throw IllegalArgumentException("internal/name must be a string in ${file.path}")
            }
            else -> file.nameWithoutExtension
        }
        val namespace = when {
            section.isString("namespace") -> requireNotNull(section.getString("namespace"))
            section.isSet("namespace") -> throw IllegalArgumentException(
                "namespace must be a string in ${file.path}",
            )
            else -> defaultNamespace
        }
        addDefinition(
            destination,
            parseDefinition(
                section,
                AttributeKey.of(namespace, rawName),
                file.path,
                defaultNamespace,
            ),
            file.path,
        )
    }

    private fun addDefinition(
        destination: MutableMap<AttributeKey, AttributeDefinition>,
        definition: AttributeDefinition,
        source: String,
    ) {
        require(destination.putIfAbsent(definition.key, definition) == null) {
            "Duplicate attribute ${definition.key} in $source"
        }
    }

    private fun parseDefinition(
        section: ConfigurationSection,
        key: AttributeKey,
        source: String,
        defaultNamespace: String,
    ): AttributeDefinition {
        require(section.isString("display-name")) { "Missing display-name for $key in $source" }
        val dependencies = if (section.isSet("depends-on")) {
            require(section.isList("depends-on")) { "depends-on must be a list for $key" }
            section.getStringList("depends-on")
                .mapTo(linkedSetOf()) { AttributeKey.parse(it, defaultNamespace) }
        } else {
            emptySet()
        }
        val formula = when {
            section.isString("formula") -> section.getString("formula")
            section.isSet("formula") -> throw IllegalArgumentException("formula must be a string for $key")
            else -> null
        }
        return AttributeDefinition(
            key = key,
            displayName = requireNotNull(section.getString("display-name")),
            category = enumValue(section, "category", AttributeCategory.UTILITY, key),
            defaultValue = number(section, "default-value", 0.0, key),
            minValue = optionalNumber(section, "min-value", key),
            maxValue = optionalNumber(section, "max-value", key),
            format = enumValue(section, "format", AttributeFormat.NUMBER, key),
            vanillaAttribute = optionalString(section, "vanilla-attribute", key),
            loreMode = enumValue(section, "lore-mode", ModifierMode.FLAT, key),
            lorePercentValue = optionalBoolean(section, "lore-percent-value", false, key),
            formula = formula,
            dependencies = dependencies,
            combat = if (section.isSet("combat")) parseCombat(section, key, source) else emptyList(),
        )
    }

    private fun parseCombat(
        section: ConfigurationSection,
        key: AttributeKey,
        source: String,
    ): List<CombatHandlerSpec> {
        require(section.isList("combat")) { "combat must be a list for $key in $source" }
        return section.getList("combat").orEmpty().mapIndexed { index, raw ->
            val location = "combat[$index] of $key in $source"
            val entry = raw as? Map<*, *> ?: throw IllegalArgumentException("$location must be a section")
            val unknown = entry.keys.map(Any?::toString).toSet() - COMBAT_KEYS
            require(unknown.isEmpty()) { "$location has unknown keys: ${unknown.joinToString()}" }
            val triggerName = entry["trigger"] as? String
                ?: throw IllegalArgumentException("$location requires trigger")
            val trigger = CombatTrigger.parse(triggerName) ?: throw IllegalArgumentException(
                "$location has unknown trigger '$triggerName' (${CombatTrigger.entries.joinToString()})",
            )
            val priority = entry["priority"] as? Int
                ?: throw IllegalArgumentException("$location requires an integer priority")
            val script = entry["script"]?.let {
                it as? String ?: throw IllegalArgumentException("$location script must be a string")
            }
            val scriptFile = entry["script-file"]?.let {
                it as? String ?: throw IllegalArgumentException("$location script-file must be a string")
            }
            val runWhenZero = entry["run-when-zero"]?.let {
                it as? Boolean ?: throw IllegalArgumentException("$location run-when-zero must be a boolean")
            } ?: false
            try {
                CombatHandlerSpec(trigger, priority, script, scriptFile, runWhenZero)
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("$location: ${error.message}", error)
            }
        }
    }

    private fun formulaOrder(
        definitions: Map<AttributeKey, AttributeDefinition>,
    ): List<AttributeDefinition> {
        definitions.values.filter { !it.formula.isNullOrBlank() }.forEach { definition ->
            definition.dependencies.forEach { dependency ->
                require(dependency in definitions) {
                    "Formula ${definition.key} depends on unknown attribute $dependency"
                }
            }
        }

        val result = mutableListOf<AttributeDefinition>()
        val visiting = mutableSetOf<AttributeKey>()
        val visited = mutableSetOf<AttributeKey>()

        fun visit(definition: AttributeDefinition) {
            if (definition.key in visited) {
                return
            }
            check(visiting.add(definition.key)) { "Circular attribute formula dependency: ${definition.key}" }
            definition.dependencies.forEach { dependency ->
                definitions.getValue(dependency)
                    .takeIf { !it.formula.isNullOrBlank() }
                    ?.let(::visit)
            }
            visiting.remove(definition.key)
            visited.add(definition.key)
            result += definition
        }

        definitions.values.filter { !it.formula.isNullOrBlank() }.forEach(::visit)
        return result
    }

    private inline fun <reified T : Enum<T>> enumValue(
        section: ConfigurationSection,
        path: String,
        default: T,
        key: AttributeKey,
    ): T {
        if (!section.isSet(path)) {
            return default
        }
        require(section.isString(path)) { "$path must be a string for $key" }
        val value = requireNotNull(section.getString(path))
        return enumValues<T>().firstOrNull { it.name.equals(value, true) }
            ?: throw IllegalArgumentException("Invalid $path '$value' for $key")
    }

    private fun number(
        section: ConfigurationSection,
        path: String,
        default: Double,
        key: AttributeKey,
    ): Double = optionalNumber(section, path, key) ?: default

    private fun optionalNumber(
        section: ConfigurationSection,
        path: String,
        key: AttributeKey,
    ): Double? {
        if (!section.isSet(path)) {
            return null
        }
        require(section.isInt(path) || section.isLong(path) || section.isDouble(path)) {
            "$path must be a number for $key"
        }
        return section.getDouble(path).also {
            require(it.isFinite()) { "$path must be finite for $key" }
        }
    }

    private fun optionalBoolean(
        section: ConfigurationSection,
        path: String,
        default: Boolean,
        key: AttributeKey,
    ): Boolean {
        if (!section.isSet(path)) {
            return default
        }
        require(section.isBoolean(path)) { "$path must be a boolean for $key" }
        return section.getBoolean(path)
    }

    private fun optionalString(
        section: ConfigurationSection,
        path: String,
        key: AttributeKey,
    ): String? {
        if (!section.isSet(path)) {
            return null
        }
        require(section.isString(path)) { "$path must be a string for $key" }
        return section.getString(path)
    }

    private fun immutableMap(
        source: Map<AttributeKey, AttributeDefinition>,
    ): Map<AttributeKey, AttributeDefinition> = Collections.unmodifiableMap(LinkedHashMap(source))

    private val COMBAT_KEYS = setOf("trigger", "priority", "script", "script-file", "run-when-zero")
}
