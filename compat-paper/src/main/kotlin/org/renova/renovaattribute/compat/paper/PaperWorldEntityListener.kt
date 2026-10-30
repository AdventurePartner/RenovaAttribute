package org.renova.renovaattribute.compat.paper

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.renova.renovaattribute.listener.EntityLifecycle
import org.renova.renovaattribute.platform.PlatformListener

class PaperWorldEntityListener : PlatformListener {
    override val name: String = "paper"

    @EventHandler(priority = EventPriority.MONITOR)
    fun onAddToWorld(event: EntityAddToWorldEvent) {
        EntityLifecycle.added(event.entity)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRemoveFromWorld(event: EntityRemoveFromWorldEvent) {
        EntityLifecycle.removed(event.entity)
    }
}
