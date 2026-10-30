package org.renova.renovaattribute.damage

import org.bukkit.event.entity.EntityDamageEvent
import org.renova.renovaattribute.attribute.BuiltinAttributes
import org.renova.renovaattribute.formula.LuaFormulaException
import org.renova.renovaattribute.util.RenovaLog

object DamagePipeline {
    @Volatile
    var settings: DamageSettings = DamageSettings()
        private set

    val enabled: Boolean
        get() = settings.enabled

    /** Receives every traced session once it is closed. */
    @Volatile
    internal var traceSink: ((DamageSession) -> Unit)? = null

    fun configure(settings: DamageSettings) {
        this.settings = settings
    }

    internal fun createSession(
        attacker: CombatParticipant,
        defender: CombatParticipant,
        type: DamageType,
        cause: EntityDamageEvent.DamageCause?,
        origin: DamageOrigin,
        projectile: Boolean,
        originalDamage: Double,
        environment: CombatEnvironment,
        element: String? = null,
        damageTags: Set<String> = emptySet(),
        ignoresArmor: Boolean = false,
        attackCooldown: Double = 1.0,
        settings: DamageSettings = this.settings,
        chain: CombatChain = CombatRegistry.chain(),
        traced: Boolean = false,
    ): DamageSession {
        val session = DamageSession(
            attacker = attacker,
            defender = defender,
            type = type,
            cause = cause,
            origin = origin,
            projectile = projectile,
            originalDamage = originalDamage,
            element = element,
            damageTags = damageTags.mapTo(HashSet()) { it.uppercase() },
            ignoresArmor = ignoresArmor,
            attackCooldown = attackCooldown.coerceIn(0.0, 1.0),
            tick = environment.clock.asLong,
            settings = settings,
            chain = chain,
            environment = environment,
            traced = traced,
        )
        initialize(session)
        if (session.trace != null) {
            session.initialState = session.workingState()
        }
        return session
    }

    /** Runs the ATTACK/DEFENSE handlers and locks the working variables. */
    internal fun calculate(session: DamageSession): CalculationOutcome {
        check(session.state == DamageSession.State.CALCULATING) { "Damage session was already calculated" }
        var outcome = CalculationOutcome.APPLIED
        for (handler in session.chain.calculate) {
            if (session.cancelled) {
                break
            }
            if (!invoke(session, handler) && session.settings.scriptErrorPolicy == ScriptErrorPolicy.VANILLA) {
                outcome = CalculationOutcome.FALLBACK
                break
            }
        }
        if (outcome == CalculationOutcome.APPLIED && session.cancelled) {
            outcome = CalculationOutcome.CANCELLED
        }
        session.outcome = outcome
        session.lock()
        return outcome
    }

    /**
     * Applies the effects queued while calculating, then runs the AFTER_* handlers when the hit
     * really landed and applies their effects.
     */
    internal fun settle(session: DamageSession, landed: Boolean) {
        if (session.state == DamageSession.State.CLOSED) {
            return
        }
        session.lock()
        flush(session)
        if (landed) {
            session.chain.settle.forEach { invoke(session, it) }
            flush(session)
        }
        close(session)
    }

    /** Drops the session and its queued effects, e.g. when another plugin cancelled the hit. */
    internal fun discard(session: DamageSession) {
        if (session.state != DamageSession.State.CLOSED) {
            close(session)
        }
    }

    private fun close(session: DamageSession) {
        session.close()
        if (session.trace != null) {
            traceSink?.invoke(session)
        }
    }

    private fun initialize(session: DamageSession) {
        val settings = session.settings
        val attacker = session.attacker.attributes
        val defender = session.defender.attributes
        val damageKey = when (session.type) {
            DamageType.PHYSICAL -> BuiltinAttributes.PHYSICAL_DAMAGE
            DamageType.MAGIC -> BuiltinAttributes.MAGIC_DAMAGE
        }
        val defenseKey = when (session.type) {
            DamageType.PHYSICAL -> BuiltinAttributes.PHYSICAL_DEFENSE
            DamageType.MAGIC -> BuiltinAttributes.MAGIC_DEFENSE
        }
        var scale = 1.0
        if (session.cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            scale *= settings.sweepRatio
        }
        if (settings.scaleByAttackCooldown) {
            val charge = session.attackCooldown
            scale *= 0.2 + 0.8 * charge * charge
        }
        val attributeDamage = attacker[damageKey] * scale
        val base = if (settings.useVanillaBaseDamage) {
            session.originalDamage + attributeDamage
        } else {
            attributeDamage
        }
        session.damage = base.coerceAtLeast(0.0)
        session.damageBoost = attacker[BuiltinAttributes.DAMAGE_BOOST]
        session.criticalChance = if (settings.criticalHits) attacker[BuiltinAttributes.CRITICAL_CHANCE] else 0.0
        session.criticalDamage = attacker[BuiltinAttributes.CRITICAL_DAMAGE]
        session.defense = defender[defenseKey]
        session.trueDamage = if (settings.trueDamage) attacker[BuiltinAttributes.TRUE_DAMAGE] else 0.0
        session.lifesteal = if (settings.lifesteal) attacker[BuiltinAttributes.LIFESTEAL] else 0.0
    }

    /** Returns false when the handler failed. */
    private fun invoke(session: DamageSession, handler: CombatHandler): Boolean {
        val owner = when (handler.trigger.side) {
            CombatSide.ATTACKER -> session.attacker
            CombatSide.DEFENDER -> session.defender
        }
        val attribute = handler.attribute
        val value = if (attribute == null) 0.0 else owner.attributes[attribute]
        if (attribute != null && value == 0.0 && !handler.runWhenZero) {
            return true
        }
        val before = session.trace?.let { session.workingState() }
        val started = System.nanoTime()
        var failure: Exception? = null
        session.currentHandler = handler
        try {
            handler.handle(session, value)
        } catch (error: Exception) {
            failure = error
            val detail = if (error is LuaFormulaException) null else error
            RenovaLog.throttled(
                "combat:${handler.id}",
                "Combat handler ${handler.id} failed: ${error.message}",
                detail,
            )
        } finally {
            session.currentHandler = null
        }
        session.trace?.add(
            TraceEntry(
                handlerId = handler.id,
                priority = handler.priority,
                trigger = handler.trigger,
                value = value,
                before = requireNotNull(before),
                after = session.workingState(),
                nanos = System.nanoTime() - started,
                error = failure?.message,
            ),
        )
        return failure == null
    }

    private fun flush(session: DamageSession) {
        session.drainEffects().forEach { effect ->
            try {
                effect.apply(session.environment.actions)
            } catch (error: Exception) {
                RenovaLog.throttled(
                    "combat-effect:${effect.label}",
                    "Combat effect ${effect.label} failed: ${error.message}",
                    error,
                )
            }
        }
    }
}
