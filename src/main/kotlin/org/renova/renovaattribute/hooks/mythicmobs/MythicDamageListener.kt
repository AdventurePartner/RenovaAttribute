package org.renova.renovaattribute.hooks.mythicmobs

import io.lumine.mythic.bukkit.events.MythicDamageEvent
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.renova.renovaattribute.damage.DamageMetadataStore
import org.renova.renovaattribute.damage.DamagePipeline
import org.renova.renovaattribute.damage.DamageType
import org.renova.renovaattribute.damage.DamageTypeResolver

class MythicDamageListener : Listener {
    var enabled: Boolean = true

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onMythicDamage(event: MythicDamageEvent) {
        if (!enabled || !DamagePipeline.enabled) {
            return
        }
        val attacker = event.caster.entity.bukkitEntity as? LivingEntity ?: return
        val target = event.target.bukkitEntity as? LivingEntity ?: return
        val type = DamageTypeResolver.resolve(event.damageMetadata.damageCause) ?: DamageType.PHYSICAL
        val result = DamagePipeline.calculate(
            DamagePipeline.createContext(attacker, target, event.damage, type),
        )
        event.damage = result.normalDamage
        DamageMetadataStore.record(event.damageMetadata, attacker, result)
    }
}
