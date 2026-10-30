package org.renova.renovaattribute.compat.paper

import io.papermc.paper.event.entity.EntityEquipmentChangedEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.renova.renovaattribute.lore.EquipmentRefreshScheduler
import org.renova.renovaattribute.platform.EquipmentWatcher

/** Paper 1.21.4+ reports every equipment change of any living entity. */
class PaperEquipmentWatcher : EquipmentWatcher {
    override val name: String = "paper-event"

    @EventHandler(priority = EventPriority.MONITOR)
    fun onEquipmentChanged(event: EntityEquipmentChangedEvent) {
        EquipmentRefreshScheduler.schedule(event.entity)
    }
}
