package org.renova.renovaattribute.damage

import org.bukkit.event.entity.EntityDamageEvent
import java.util.function.LongSupplier
import java.util.random.RandomGenerator

internal class CombatEnvironment(
    val random: RandomGenerator,
    val clock: LongSupplier,
    val cooldowns: CombatCooldowns,
    val actions: CombatActions,
)

enum class CalculationOutcome {
    APPLIED,
    CANCELLED,

    /** A handler failed under [ScriptErrorPolicy.VANILLA]; the hit keeps its vanilla damage. */
    FALLBACK,
}

internal data class WorkingState(
    val damage: Double,
    val damageBoost: Double,
    val criticalChance: Double,
    val criticalDamage: Double,
    val critical: Boolean,
    val defense: Double,
    val trueDamage: Double,
    val lifesteal: Double,
    val cancelled: Boolean,
)

internal data class TraceEntry(
    val handlerId: String,
    val priority: Int,
    val trigger: CombatTrigger,
    val value: Double,
    val before: WorkingState,
    val after: WorkingState,
    val nanos: Long,
    val error: String?,
)

/**
 * The damage of one hit ("本轮伤害"). Working variables start from the attribute snapshots and
 * are mutable only while [state] is [State.CALCULATING]; built-in handlers consume them at their
 * priority, so a handler that runs earlier changes what the built-in logic sees.
 */
class DamageSession internal constructor(
    val attacker: CombatParticipant,
    val defender: CombatParticipant,
    val type: DamageType,
    val cause: EntityDamageEvent.DamageCause?,
    val origin: DamageOrigin,
    val projectile: Boolean,
    val originalDamage: Double,
    val element: String?,
    /** MythicMobs damage tags, upper-cased; empty for vanilla hits. */
    val damageTags: Set<String>,
    val ignoresArmor: Boolean,
    val attackCooldown: Double,
    val tick: Long,
    internal val settings: DamageSettings,
    internal val chain: CombatChain,
    internal val environment: CombatEnvironment,
    traced: Boolean,
) {
    enum class State {
        CALCULATING,
        SETTLING,
        CLOSED,
    }

    var state: State = State.CALCULATING
        private set

    var damage: Double = 0.0
        set(value) {
            field = writable("damage", value)
        }

    var damageBoost: Double = 0.0
        set(value) {
            field = writable("damage boost", value)
        }

    var criticalChance: Double = 0.0
        set(value) {
            field = writable("critical chance", value)
        }

    var criticalDamage: Double = 0.0
        set(value) {
            field = writable("critical damage", value)
        }

    var critical: Boolean = false
        set(value) {
            checkWritable()
            field = value
        }

    var defense: Double = 0.0
        set(value) {
            field = writable("defense", value)
        }

    var trueDamage: Double = 0.0
        set(value) {
            field = writable("true damage", value)
        }

    var lifesteal: Double = 0.0
        set(value) {
            field = writable("lifesteal", value)
        }

    var cancelled: Boolean = false
        private set

    /** Health the defender actually lost to the normal damage; known once the hit settles. */
    var finalDamage: Double = 0.0
        internal set

    var outcome: CalculationOutcome? = null
        internal set

    internal var trueHealthLoss: Double = 0.0
    internal var currentHandler: CombatHandler? = null
    internal var scriptHandle: Any? = null
    internal val trace: MutableList<TraceEntry>? = if (traced) ArrayList() else null
    internal var initialState: WorkingState? = null

    private val variables = HashMap<String, Any>()
    private val effects = ArrayList<CombatEffect>()

    fun cancel() {
        checkWritable()
        cancelled = true
    }

    fun normalDamage(): Double = damage.coerceAtLeast(0.0)

    fun hasDamageTag(tag: String): Boolean = tag.uppercase() in damageTags

    fun variable(name: String): Any? {
        checkOpen()
        return variables[name]
    }

    /** Stores a per-hit value shared between handlers; only numbers, booleans and strings are allowed. */
    fun setVariable(name: String, value: Any?) {
        checkOpen()
        require(name.isNotBlank()) { "variable name cannot be blank" }
        when (value) {
            null -> variables.remove(name)
            is Number -> variables[name] = value.toDouble().also {
                require(it.isFinite()) { "variable $name must be finite" }
            }
            is Boolean, is String -> variables[name] = value
            else -> throw IllegalArgumentException("variable $name must be a number, boolean or string")
        }
    }

    fun chance(probability: Double): Boolean {
        checkOpen()
        require(probability.isFinite()) { "probability must be finite" }
        return environment.random.nextDouble() < probability
    }

    fun random(min: Double, max: Double): Double {
        checkOpen()
        require(min.isFinite() && max.isFinite() && min <= max) { "random range must be finite and ordered" }
        return if (min == max) min else environment.random.nextDouble(min, max)
    }

    fun cooldown(owner: CombatParticipant, name: String, ticks: Long): Boolean {
        checkOpen()
        return environment.cooldowns.tryUse(owner.uniqueId, name, ticks, environment.clock.asLong)
    }

    fun queue(effect: CombatEffect) {
        checkOpen()
        effects += effect
    }

    internal fun drainEffects(): List<CombatEffect> {
        if (effects.isEmpty()) {
            return emptyList()
        }
        return ArrayList(effects).also { effects.clear() }
    }

    internal fun lock() {
        if (state == State.CALCULATING) {
            state = State.SETTLING
        }
    }

    internal fun close() {
        state = State.CLOSED
        effects.clear()
        variables.clear()
        scriptHandle = null
    }

    internal fun workingState(): WorkingState = WorkingState(
        damage = damage,
        damageBoost = damageBoost,
        criticalChance = criticalChance,
        criticalDamage = criticalDamage,
        critical = critical,
        defense = defense,
        trueDamage = trueDamage,
        lifesteal = lifesteal,
        cancelled = cancelled,
    )

    fun checkOpen() {
        check(state != State.CLOSED) { "this damage session has already finished" }
    }

    private fun checkWritable() {
        checkOpen()
        check(state == State.CALCULATING) { "damage can only be changed by ATTACK/DEFENSE handlers" }
    }

    private fun writable(name: String, value: Double): Double {
        checkWritable()
        require(value.isFinite()) { "$name must be a finite number" }
        return value
    }
}
