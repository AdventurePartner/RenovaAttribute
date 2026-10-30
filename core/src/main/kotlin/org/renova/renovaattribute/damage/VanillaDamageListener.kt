package org.renova.renovaattribute.damage

import org.bukkit.entity.HumanEntity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import java.util.IdentityHashMap

class VanillaDamageListener : Listener {
    private val sessions = IdentityHashMap<EntityDamageEvent, DamageSession>()

    fun configure(
        physicalCauses: Set<EntityDamageEvent.DamageCause>,
        magicCauses: Set<EntityDamageEvent.DamageCause>,
    ) {
        DamageTypeResolver.configure(physicalCauses, magicCauses)
    }

    fun shutdown() {
        sessions.values.forEach(DamagePipeline::discard)
        sessions.clear()
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        if (SecondaryDamage.active) {
            return
        }
        val target = event.entity as? LivingEntity ?: return
        if (target.hasMetadata(SKILL_DAMAGE_METADATA)) {
            DamageMetadataStore.peek(skillTokens(target))
                ?.takeIf { it.outcome == CalculationOutcome.APPLIED }
                ?.let { applyVanillaReduction(event, it.settings.vanillaReduction) }
            return
        }
        if (!DamagePipeline.enabled) {
            return
        }
        val attacker = resolveAttacker(event) ?: return
        val type = DamageTypeResolver.resolve(event.cause) ?: return
        if (!CombatRuntime.onPrimaryThread()) {
            return
        }
        val session = DamagePipeline.createSession(
            attacker = CombatRuntime.participant(attacker),
            defender = CombatRuntime.participant(target),
            type = type,
            cause = event.cause,
            origin = DamageOrigin.VANILLA,
            projectile = event.damager is Projectile,
            originalDamage = event.damage,
            environment = CombatRuntime.environment,
            attackCooldown = attackCooldown(event, attacker),
            traced = DamageDebug.isTraced(attacker, target),
        )
        when (DamagePipeline.calculate(session)) {
            CalculationOutcome.FALLBACK -> {
                DamagePipeline.discard(session)
                return
            }
            CalculationOutcome.CANCELLED -> event.isCancelled = true
            CalculationOutcome.APPLIED -> {
                event.damage = session.normalDamage()
                applyVanillaReduction(event, session.settings.vanillaReduction)
            }
        }
        sessions[event] = session
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDamageMonitor(event: EntityDamageEvent) {
        val target = event.entity as? LivingEntity ?: return
        val mythicSession = if (target.hasMetadata(SKILL_DAMAGE_METADATA)) {
            DamageMetadataStore.consume(skillTokens(target))
        } else {
            null
        }
        val session = sessions.remove(event) ?: mythicSession ?: return
        CombatRuntime.finish(session, event.isCancelled, event.finalDamage.coerceIn(0.0, target.health))
    }

    private fun resolveAttacker(event: EntityDamageByEntityEvent): LivingEntity? = when (val damager = event.damager) {
        is LivingEntity -> damager
        is Projectile -> damager.shooter as? LivingEntity
        else -> null
    }

    private fun attackCooldown(event: EntityDamageByEntityEvent, attacker: LivingEntity): Double {
        if (event.damager !== attacker) {
            return 1.0
        }
        if (event.cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK &&
            event.cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
        ) {
            return 1.0
        }
        return (attacker as? HumanEntity)?.attackCooldown?.toDouble() ?: 1.0
    }

    private fun skillTokens(target: LivingEntity): List<Any> =
        target.getMetadata(SKILL_DAMAGE_METADATA).mapNotNull { it.value() }

    companion object {
        private const val SKILL_DAMAGE_METADATA = "skill-damage"

        /** Must run after `event.damage` was set, because setting the base damage recomputes the modifiers. */
        @Suppress("DEPRECATION")
        internal fun applyVanillaReduction(event: EntityDamageEvent, mode: VanillaReduction) {
            val modifiers = when (mode) {
                VanillaReduction.KEEP -> return
                VanillaReduction.IGNORE_ARMOR -> listOf(EntityDamageEvent.DamageModifier.ARMOR)
                VanillaReduction.IGNORE_ALL -> listOf(
                    EntityDamageEvent.DamageModifier.ARMOR,
                    EntityDamageEvent.DamageModifier.RESISTANCE,
                    EntityDamageEvent.DamageModifier.MAGIC,
                )
            }
            modifiers.forEach { modifier ->
                if (event.isApplicable(modifier)) {
                    event.setDamage(modifier, 0.0)
                }
            }
        }
    }
}
