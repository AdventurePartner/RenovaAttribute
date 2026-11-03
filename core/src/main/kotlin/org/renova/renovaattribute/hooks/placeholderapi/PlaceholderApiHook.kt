package org.renova.renovaattribute.hooks.placeholderapi

import org.renova.renovaattribute.RenovaAttribute

/** Registers [RenovaExpansion]. Call only while PlaceholderAPI is enabled, or its classes fail to link. */
object PlaceholderApiHook {
    private var expansion: RenovaExpansion? = null

    fun configure(enabled: Boolean) {
        if (!enabled) {
            shutdown()
            return
        }
        // `/papi unregister ra` 之后，下一次 /ra reload 会重新注册。
        if (expansion?.isRegistered == true) {
            return
        }
        val created = RenovaExpansion(RenovaAttribute.instance)
        if (created.register()) {
            expansion = created
        } else {
            expansion = null
            RenovaAttribute.instance.logger.warning(
                "PlaceholderAPI refused the 'ra' expansion; another expansion may already use that identifier",
            )
        }
    }

    fun shutdown() {
        expansion?.takeIf { it.isRegistered }?.unregister()
        expansion = null
    }
}
