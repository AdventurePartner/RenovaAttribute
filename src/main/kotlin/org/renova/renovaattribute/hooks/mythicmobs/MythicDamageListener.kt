package org.renova.renovaattribute.hooks.mythicmobs

import io.lumine.mythic.api.skills.damage.DamageMetadata
import io.lumine.mythic.bukkit.events.MythicDamageEvent
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.renova.renovaattribute.damage.CalculationOutcome
import org.renova.renovaattribute.damage.CombatRuntime
import org.renova.renovaattribute.damage.DamageDebug
import org.renova.renovaattribute.damage.DamageMetadataStore
import org.renova.renovaattribute.damage.DamageOrigin
import org.renova.renovaattribute.damage.DamagePipeline
import org.renova.renovaattribute.damage.DamageType
import org.renova.renovaattribute.damage.DamageTypeResolver

/**
 * Mythic applies skill damage from its own HIGH listener, which skips cancelled events, so the
 * session is computed at NORMAL and the vanilla damage event it fires is matched by its metadata.
 */
class MythicDamageListener : Listener {
    var enabled: Boolean = true

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onMythicDamage(event: MythicDamageEvent) {
        if (!enabled || !DamagePipeline.enabled) {
            return
        }
        val attacker = event.caster.entity.bukkitEntity as? LivingEntity ?: return
        val target = event.target.bukkitEntity as? LivingEntity ?: return
        if (!CombatRuntime.onPrimaryThread()) {
            return
        }
        val metadata = event.damageMetadata
        val cause = metadata.damageCause
        val session = DamagePipeline.createSession(
            attacker = CombatRuntime.participant(attacker),
            defender = CombatRuntime.participant(target),
            type = cause?.let(DamageTypeResolver::resolve) ?: DamageType.PHYSICAL,
            cause = cause,
            origin = DamageOrigin.MYTHIC,
            projectile = false,
            originalDamage = event.damage,
            environment = CombatRuntime.environment,
            element = element(metadata),
            damageTags = metadata.tags?.toSet().orEmpty(),
            ignoresArmor = metadata.ignoresArmor == true,
            traced = DamageDebug.isTraced(attacker, target),
        )
        when (DamagePipeline.calculate(session)) {
            CalculationOutcome.FALLBACK -> {
                DamagePipeline.discard(session)
                return
            }
            CalculationOutcome.CANCELLED -> event.isCancelled = true
            CalculationOutcome.APPLIED -> event.damage = session.normalDamage()
        }
        DamageMetadataStore.record(metadata, session)
    }

    // Deprecated since Mythic 5.9 without a replacement; damage tags are exposed alongside it.
    @Suppress("DEPRECATION")
    private fun element(metadata: DamageMetadata): String? = metadata.element?.takeIf { it.isNotBlank() }

    /** A cancelled skill hit never reaches the vanilla damage event, so it is finished here. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onMythicDamageMonitor(event: MythicDamageEvent) {
        if (!event.isCancelled) {
            return
        }
        val session = DamageMetadataStore.consume(listOf(event.damageMetadata)) ?: return
        CombatRuntime.finish(session, eventCancelled = true, finalDamage = 0.0)
    }
}
