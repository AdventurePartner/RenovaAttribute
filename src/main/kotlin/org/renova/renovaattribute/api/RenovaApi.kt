package org.renova.renovaattribute.api

import org.renova.renovaattribute.core.AttributeServiceImpl

object RenovaApi {
    @JvmStatic
    val attributeService: AttributeService
        get() = AttributeServiceImpl
}
