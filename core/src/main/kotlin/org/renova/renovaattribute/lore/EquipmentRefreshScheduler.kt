package org.renova.renovaattribute.lore

import org.bukkit.entity.LivingEntity
import org.renova.renovaattribute.RenovaAttribute
import org.renova.renovaattribute.core.AttributeServiceImpl
import java.util.UUID

/** Refreshes an entity shortly after its equipment changed; changes within the delay share one refresh. */
object EquipmentRefreshScheduler {
    private val scheduled = mutableSetOf<UUID>()
    private var refreshDelayTicks = 1L

    fun configure(refreshDelayTicks: Long) {
        require(refreshDelayTicks > 0) { "Equipment refresh delay must be positive" }
        this.refreshDelayTicks = refreshDelayTicks
    }

    fun shutdown() {
        scheduled.clear()
    }

    fun schedule(entity: LivingEntity) {
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
