package org.renova.renovaattribute.damage

import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.renova.renovaattribute.RenovaAttribute
import java.util.IdentityHashMap

class VanillaDamageListener : Listener {
    private data class ProcessedDamage(
        val attacker: LivingEntity,
        val target: LivingEntity,
        val result: DamageResult,
    )

    private val results = IdentityHashMap<EntityDamageEvent, ProcessedDamage>()
    fun configure(
        physicalCauses: Set<org.bukkit.event.entity.EntityDamageEvent.DamageCause>,
        magicCauses: Set<org.bukkit.event.entity.EntityDamageEvent.DamageCause>,
    ) {
        DamageTypeResolver.configure(physicalCauses, magicCauses)
    }

    fun shutdown() {
        results.clear()
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        if (!DamagePipeline.enabled) {
            return
        }
        val target = event.entity as? LivingEntity ?: return
        if (target.hasMetadata("skill-damage")) {
            return
        }
        val attacker = resolveAttacker(event) ?: return
        val type = DamageTypeResolver.resolve(event.cause) ?: return
        val result = DamagePipeline.calculate(
            DamagePipeline.createContext(attacker, target, event.damage, type),
        )
        event.damage = result.normalDamage
        results[event] = ProcessedDamage(attacker, target, result)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDamageMonitor(event: EntityDamageEvent) {
        val target = event.entity as? LivingEntity ?: return
        val processed = results.remove(event)
        val pending = if (processed == null && target.hasMetadata("skill-damage")) {
            val tokens = buildList<Any> {
                target.getMetadata("skill-damage").forEach { metadata ->
                    metadata.value()?.let(::add)
                }
            }
            DamageMetadataStore.consume(
                tokens,
            )
        } else {
            null
        }
        if (event.isCancelled) {
            return
        }
        when {
            processed != null -> applyPostDamage(
                processed.attacker,
                processed.target,
                processed.result,
                event.finalDamage.coerceIn(0.0, target.health),
            )
            pending != null -> {
                val attacker = Bukkit.getEntity(pending.attackerId) as? LivingEntity ?: return
                applyPostDamage(
                    attacker,
                    target,
                    pending.result,
                    event.finalDamage.coerceIn(0.0, target.health),
                )
            }
        }
    }

    private fun resolveAttacker(event: EntityDamageByEntityEvent): LivingEntity? = when (val damager = event.damager) {
        is LivingEntity -> damager
        is Projectile -> damager.shooter as? LivingEntity
        else -> null
    }

    private fun applyPostDamage(
        attacker: LivingEntity,
        target: LivingEntity,
        result: DamageResult,
        normalHealthLoss: Double,
    ) {
        RenovaAttribute.instance.server.scheduler.runTask(
            RenovaAttribute.instance,
            Runnable {
                var trueHealthLoss = 0.0
                if (result.trueDamage > 0.0 && target.isValid && !target.isDead) {
                    val beforeTrueDamage = target.health
                    target.health = (beforeTrueDamage - result.trueDamage).coerceAtLeast(0.0)
                    trueHealthLoss = beforeTrueDamage - target.health
                }
                val healAmount = (normalHealthLoss + trueHealthLoss) * result.lifestealRatio
                if (healAmount > 0.0 && attacker.isValid && !attacker.isDead) {
                    val maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH)?.value ?: attacker.health
                    attacker.health = (attacker.health + healAmount).coerceAtMost(maxHealth)
                }
            },
        )
    }
}
