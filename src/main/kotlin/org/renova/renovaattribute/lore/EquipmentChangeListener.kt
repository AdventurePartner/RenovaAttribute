package org.renova.renovaattribute.lore

import io.papermc.paper.event.entity.EntityEquipmentChangedEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.renova.renovaattribute.RenovaAttribute
import org.renova.renovaattribute.core.AttributeServiceImpl
import java.util.UUID

class EquipmentChangeListener : Listener {
    private val scheduled = mutableSetOf<UUID>()
    private var refreshDelayTicks = 1L

    fun configure(refreshDelayTicks: Long) {
        require(refreshDelayTicks > 0) { "Equipment refresh delay must be positive" }
        this.refreshDelayTicks = refreshDelayTicks
    }

    fun shutdown() {
        scheduled.clear()
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onEquipmentChanged(event: EntityEquipmentChangedEvent) {
        val entity = event.entity
        if (!scheduled.add(entity.uniqueId)) {
            return
        }
        RenovaAttribute.instance.server.scheduler.runTaskLater(
            RenovaAttribute.instance,
            Runnable {
                scheduled.remove(entity.uniqueId)
                if (entity.isValid) {
                    AttributeServiceImpl.refresh(entity)
                }
            },
            refreshDelayTicks,
        )
    }
}
