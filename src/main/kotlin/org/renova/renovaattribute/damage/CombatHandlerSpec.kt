package org.renova.renovaattribute.damage

/** A `combat:` entry of an attribute definition, compiled into a Lua combat handler on load. */
data class CombatHandlerSpec(
    val trigger: CombatTrigger,
    val priority: Int,
    val script: String? = null,
    val scriptFile: String? = null,
    val runWhenZero: Boolean = false,
) {
    init {
        require((script == null) != (scriptFile == null)) {
            "Exactly one of script and script-file must be set"
        }
        require(script == null || script.isNotBlank()) { "script cannot be blank" }
        scriptFile?.let(::validateScriptPath)
    }

    companion object {
        const val SCRIPT_DIRECTORY = "scripts"

        /** Script files must live under the plugin's `scripts/` directory. */
        @JvmStatic
        fun validateScriptPath(path: String) {
            require(path.startsWith("$SCRIPT_DIRECTORY/")) { "script-file must start with $SCRIPT_DIRECTORY/: $path" }
            require(path.endsWith(".lua")) { "script-file must end with .lua: $path" }
            require('\\' !in path && ':' !in path) { "script-file must use '/' separators and a relative path: $path" }
            require(path.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
                "script-file cannot contain empty, '.' or '..' segments: $path"
            }
        }
    }
}
