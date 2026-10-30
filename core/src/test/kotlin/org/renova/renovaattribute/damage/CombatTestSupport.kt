package org.renova.renovaattribute.damage

import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntityDamageEvent
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.AttributeSnapshot
import org.renova.renovaattribute.api.StatValue
import org.renova.renovaattribute.attribute.BuiltinAttributes
import java.util.ArrayDeque
import java.util.UUID
import java.util.random.RandomGenerator

internal class FakeParticipant(
    override val name: String,
    values: Map<AttributeKey, Double> = emptyMap(),
    override var health: Double = 20.0,
    override val maxHealth: Double = 20.0,
    override val isPlayer: Boolean = false,
    override val typeKey: String = "minecraft:zombie",
    private val tags: Set<String> = emptySet(),
) : CombatParticipant {
    override val uniqueId: UUID = UUID.randomUUID()
    override val attributes: AttributeSnapshot = snapshot(uniqueId, values)
    override val entity: LivingEntity? = null
    override val isDead: Boolean get() = health <= 0.0
    override val isValid: Boolean = true

    override fun hasTag(tag: String): Boolean = tag in tags

    companion object {
        fun snapshot(id: UUID, values: Map<AttributeKey, Double>): AttributeSnapshot {
            val all = LinkedHashMap<AttributeKey, Double>()
            BuiltinAttributes.ALL.forEach { all[it] = 0.0 }
            all[BuiltinAttributes.CRITICAL_DAMAGE] = 0.5
            all.putAll(values)
            return AttributeSnapshot(id, all)
        }
    }
}

/** Returns queued doubles in order, then 0.5 forever. */
internal class ScriptedRandom(vararg values: Double) : RandomGenerator {
    private val queue = ArrayDeque(values.toList())

    override fun nextLong(): Long = throw UnsupportedOperationException()

    override fun nextDouble(): Double = if (queue.isEmpty()) 0.5 else queue.removeFirst()
}

internal class RecordingActions : CombatActions {
    val calls = ArrayList<String>()
    var skillsAvailable = false

    override fun applyTrueDamage(target: CombatParticipant, amount: Double): Double {
        val fake = target as FakeParticipant
        val before = fake.health
        fake.health = (before - amount).coerceAtLeast(0.0)
        calls += "true_damage ${target.name} $amount"
        return before - fake.health
    }

    override fun heal(target: CombatParticipant, amount: Double) {
        calls += "heal ${target.name} $amount"
    }

    override fun dealDamage(source: CombatParticipant, target: CombatParticipant, amount: Double) {
        calls += "deal_damage ${source.name}->${target.name} $amount"
    }

    override fun addBuff(target: CombatParticipant, key: AttributeKey, value: StatValue, durationTicks: Long, tag: String) {
        calls += "add_buff ${target.name} $key $value $durationTicks $tag"
    }

    override fun sendMessage(target: CombatParticipant, text: String) {
        calls += "message ${target.name} $text"
    }

    override fun sendActionBar(target: CombatParticipant, text: String) {
        calls += "actionbar ${target.name} $text"
    }

    override fun castSkill(caster: CombatParticipant, skill: String, target: CombatParticipant?) {
        calls += "cast_skill ${caster.name} $skill ${target?.name}"
    }

    override fun canCastSkills(): Boolean = skillsAvailable
}

internal class CombatFixture(
    val attacker: FakeParticipant = FakeParticipant("attacker"),
    val defender: FakeParticipant = FakeParticipant("defender"),
    val random: RandomGenerator = ScriptedRandom(),
    val settings: DamageSettings = DamageSettings(),
    val handlers: List<CombatHandler> = emptyList(),
    var tick: Long = 100,
) {
    val actions = RecordingActions()
    val cooldowns = CombatCooldowns()
    val environment = CombatEnvironment(random, { tick }, cooldowns, actions)
    val chain = CombatChain(BuiltinCombatHandlers.create(BuiltinPriorities()) + handlers)

    fun session(
        originalDamage: Double = 5.0,
        type: DamageType = DamageType.PHYSICAL,
        cause: EntityDamageEvent.DamageCause = EntityDamageEvent.DamageCause.ENTITY_ATTACK,
        attackCooldown: Double = 1.0,
        element: String? = null,
        ignoresArmor: Boolean = false,
        traced: Boolean = false,
    ): DamageSession = DamagePipeline.createSession(
        attacker = attacker,
        defender = defender,
        type = type,
        cause = cause,
        origin = DamageOrigin.VANILLA,
        projectile = false,
        originalDamage = originalDamage,
        environment = environment,
        element = element,
        ignoresArmor = ignoresArmor,
        attackCooldown = attackCooldown,
        settings = settings,
        chain = chain,
        traced = traced,
    )
}

internal class RecordingHandler(
    override val id: String,
    override val trigger: CombatTrigger,
    override val priority: Int,
    override val attribute: AttributeKey? = null,
    override val runWhenZero: Boolean = false,
    private val log: MutableList<String> = ArrayList(),
    private val body: (DamageSession, Double) -> Unit = { _, _ -> },
) : CombatHandler {
    override fun handle(session: DamageSession, value: Double) {
        log += "$id=$value"
        body(session, value)
    }
}
