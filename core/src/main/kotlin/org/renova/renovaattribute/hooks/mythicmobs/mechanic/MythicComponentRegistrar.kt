package org.renova.renovaattribute.hooks.mythicmobs.mechanic

import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class MythicComponentRegistrar(
    private var mechanicsEnabled: Boolean,
    private var conditionEnabled: Boolean,
) : Listener {

    fun configure(mechanicsEnabled: Boolean, conditionEnabled: Boolean) {
        this.mechanicsEnabled = mechanicsEnabled
        this.conditionEnabled = conditionEnabled
    }

    @EventHandler
    fun onMechanicLoad(event: MythicMechanicLoadEvent) {
        if (!mechanicsEnabled) {
            return
        }
        when (event.mechanicName.lowercase()) {
            "setattribute", "rasetattribute" -> event.register(SetAttributeMechanic(event.config))
            "addattribute", "raaddattribute" -> event.register(AddAttributeMechanic(event.config))
            "modifyattribute", "ramodifyattribute" -> event.register(ModifyAttributeMechanic(event.config))
        }
    }

    @EventHandler
    fun onConditionLoad(event: MythicConditionLoadEvent) {
        if (conditionEnabled && (
                event.conditionName.equals("attribute", true) ||
                    event.conditionName.equals("raattribute", true)
                )
        ) {
            event.register(AttributeCondition(event.config, event.argument))
        }
    }
}
