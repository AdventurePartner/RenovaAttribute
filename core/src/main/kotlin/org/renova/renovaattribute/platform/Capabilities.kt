package org.renova.renovaattribute.platform

/**
 * Probes the running server's API by name, so nothing missing on older servers is ever linked.
 * Parameter types are class names; `"double"` and the other primitive names are accepted.
 */
internal object Capabilities {
    private val loader: ClassLoader = Capabilities::class.java.classLoader

    fun hasClass(name: String): Boolean = find(name) != null

    fun hasMethod(owner: String, name: String, vararg parameters: String): Boolean {
        val type = find(owner) ?: return false
        val parameterTypes = types(parameters) ?: return false
        return try {
            type.getMethod(name, *parameterTypes)
            true
        } catch (_: NoSuchMethodException) {
            false
        }
    }

    fun hasConstructor(owner: String, vararg parameters: String): Boolean {
        val type = find(owner) ?: return false
        val parameterTypes = types(parameters) ?: return false
        return try {
            type.getConstructor(*parameterTypes)
            true
        } catch (_: NoSuchMethodException) {
            false
        }
    }

    /**
     * Instantiates an implementation from a compat module; call only after its probe passed.
     * Where the call has no expected type (inside `lazy`, an untyped local), pass the interface
     * as [T] explicitly: otherwise it is inferred from the other branch's concrete type and the
     * unchecked result fails that cast.
     */
    fun <T> create(className: String): T {
        @Suppress("UNCHECKED_CAST")
        return Class.forName(className, true, loader).getDeclaredConstructor().newInstance() as T
    }

    private fun types(names: Array<out String>): Array<Class<*>>? {
        val result = arrayOfNulls<Class<*>>(names.size)
        names.forEachIndexed { index, name -> result[index] = find(name) ?: return null }
        @Suppress("UNCHECKED_CAST")
        return result as Array<Class<*>>
    }

    private fun find(name: String): Class<*>? = when (name) {
        "boolean" -> java.lang.Boolean.TYPE
        "int" -> java.lang.Integer.TYPE
        "long" -> java.lang.Long.TYPE
        "float" -> java.lang.Float.TYPE
        "double" -> java.lang.Double.TYPE
        else -> try {
            Class.forName(name, false, loader)
        } catch (_: ClassNotFoundException) {
            null
        } catch (_: LinkageError) {
            null
        }
    }
}
