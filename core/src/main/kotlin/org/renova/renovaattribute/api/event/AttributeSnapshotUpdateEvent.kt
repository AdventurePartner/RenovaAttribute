package org.renova.renovaattribute.api.event

import org.bukkit.entity.Entity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.renova.renovaattribute.api.AttributeSnapshot

class AttributeSnapshotUpdateEvent(
    val entity: Entity,
    val previous: AttributeSnapshot?,
    val current: AttributeSnapshot,
) : Event() {
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmField
        val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
