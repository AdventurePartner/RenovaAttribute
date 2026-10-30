package org.renova.renovaattribute.damage

import org.bukkit.event.entity.EntityDamageEvent
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.attribute.BuiltinAttributes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DamagePipelineTest {
    private val physicalDamage = BuiltinAttributes.PHYSICAL_DAMAGE
    private val magicDamage = BuiltinAttributes.MAGIC_DAMAGE

    /** The fixed formula DamagePipeline used before handlers existed. */
    private data class Legacy(val normal: Double, val trueDamage: Double, val critical: Boolean, val lifesteal: Double)

    private fun legacy(
        original: Double,
        attacker: Map<AttributeKey, Double>,
        defender: Map<AttributeKey, Double>,
        type: DamageType,
        roll: Double,
        settings: DamageSettings,
    ): Legacy {
        fun a(key: AttributeKey) = attacker[key] ?: if (key == BuiltinAttributes.CRITICAL_DAMAGE) 0.5 else 0.0
        fun d(key: AttributeKey) = defender[key] ?: 0.0
        val damageKey = if (type == DamageType.PHYSICAL) physicalDamage else magicDamage
        val defenseKey = if (type == DamageType.PHYSICAL) BuiltinAttributes.PHYSICAL_DEFENSE else BuiltinAttributes.MAGIC_DEFENSE
        var normal = if (settings.useVanillaBaseDamage) original + a(damageKey) else a(damageKey)
        normal = normal.coerceAtLeast(0.0)
        normal *= 1.0 + a(BuiltinAttributes.DAMAGE_BOOST)
        val critical = settings.criticalHits && roll < a(BuiltinAttributes.CRITICAL_CHANCE).coerceIn(0.0, 1.0)
        if (critical) {
            normal *= 1.0 + a(BuiltinAttributes.CRITICAL_DAMAGE).coerceAtLeast(0.0)
        }
        val defense = d(defenseKey).coerceAtLeast(0.0)
        normal = normal.coerceAtLeast(0.0) * settings.defenseConstant / (defense + settings.defenseConstant)
        return Legacy(
            normal.coerceAtLeast(0.0),
            if (settings.trueDamage) a(BuiltinAttributes.TRUE_DAMAGE).coerceAtLeast(0.0) else 0.0,
            critical,
            if (settings.lifesteal) a(BuiltinAttributes.LIFESTEAL).coerceAtLeast(0.0) else 0.0,
        )
    }

    @Test
    fun `matches the previous fixed formula when no scripts are registered`() {
        var cases = 0
        for (settings in listOf(
            DamageSettings(),
            DamageSettings(useVanillaBaseDamage = false),
            DamageSettings(criticalHits = false, trueDamage = false, lifesteal = false, defenseConstant = 60.0),
        )) for (type in DamageType.entries) for (original in listOf(0.0, 5.0, 12.5))
            for (attributeDamage in listOf(0.0, 10.0)) for (boost in listOf(-0.5, 0.0, 0.3))
                for (chance in listOf(0.0, 0.5, 1.0)) for (roll in listOf(0.25, 0.75))
                    for (defense in listOf(0.0, 50.0, 200.0)) {
                        val attacker = mapOf(
                            physicalDamage to attributeDamage,
                            magicDamage to attributeDamage * 2,
                            BuiltinAttributes.DAMAGE_BOOST to boost,
                            BuiltinAttributes.CRITICAL_CHANCE to chance,
                            BuiltinAttributes.CRITICAL_DAMAGE to 1.2,
                            BuiltinAttributes.TRUE_DAMAGE to 3.0,
                            BuiltinAttributes.LIFESTEAL to 0.1,
                        )
                        val defender = mapOf(
                            BuiltinAttributes.PHYSICAL_DEFENSE to defense,
                            BuiltinAttributes.MAGIC_DEFENSE to defense / 2,
                        )
                        val fixture = CombatFixture(
                            attacker = FakeParticipant("attacker", attacker),
                            defender = FakeParticipant("defender", defender),
                            random = ScriptedRandom(roll),
                            settings = settings,
                        )
                        val session = fixture.session(originalDamage = original, type = type)
                        assertEquals(CalculationOutcome.APPLIED, DamagePipeline.calculate(session))

                        val expected = legacy(original, attacker, defender, type, roll, settings)
                        assertEquals(expected.normal, session.normalDamage(), 1e-9)
                        assertEquals(expected.critical, session.critical)
                        assertEquals(expected.trueDamage, session.trueDamage.coerceAtLeast(0.0), 1e-9)
                        assertEquals(expected.lifesteal, session.lifesteal.coerceAtLeast(0.0), 1e-9)
                        cases++
                    }
        assertEquals(3 * 2 * 3 * 2 * 3 * 3 * 2 * 3, cases)
    }

    @Test
    fun `orders by priority then attack before defense then id`() {
        val log = ArrayList<String>()
        val handlers = listOf(
            RecordingHandler("b", CombatTrigger.DEFENSE, 150, log = log),
            RecordingHandler("z", CombatTrigger.ATTACK, 150, log = log),
            RecordingHandler("a", CombatTrigger.ATTACK, 150, log = log),
            RecordingHandler("first", CombatTrigger.DEFENSE, -10, log = log),
            RecordingHandler("late", CombatTrigger.AFTER_ATTACK, 0, log = log),
        )
        val fixture = CombatFixture(handlers = handlers)
        val session = fixture.session()

        DamagePipeline.calculate(session)
        assertEquals(listOf("first=0.0", "a=0.0", "z=0.0", "b=0.0"), log)

        DamagePipeline.settle(session, landed = true)
        assertEquals("late=0.0", log.last())
    }

    @Test
    fun `passes the trigger side value and skips zero values unless requested`() {
        val key = AttributeKey.of("renova", "test-value")
        val log = ArrayList<String>()
        val fixture = CombatFixture(
            attacker = FakeParticipant("attacker", mapOf(key to 3.0)),
            defender = FakeParticipant("defender", mapOf(key to 0.0)),
            handlers = listOf(
                RecordingHandler("attack", CombatTrigger.ATTACK, 10, key, log = log),
                RecordingHandler("defense", CombatTrigger.DEFENSE, 20, key, log = log),
                RecordingHandler("defense-always", CombatTrigger.DEFENSE, 30, key, runWhenZero = true, log = log),
            ),
        )

        DamagePipeline.calculate(fixture.session())

        assertEquals(listOf("attack=3.0", "defense-always=0.0"), log)
    }

    @Test
    fun `handlers before a builtin change what the builtin consumes`() {
        val fixture = CombatFixture(
            attacker = FakeParticipant("attacker", mapOf(physicalDamage to 95.0)),
            defender = FakeParticipant("defender", mapOf(BuiltinAttributes.PHYSICAL_DEFENSE to 100.0)),
            handlers = listOf(
                RecordingHandler("penetration", CombatTrigger.ATTACK, 350) { session, _ ->
                    session.defense *= 0.0
                },
                RecordingHandler("boost", CombatTrigger.ATTACK, 90) { session, _ ->
                    session.damageBoost += 0.5
                },
            ),
        )
        val session = fixture.session(originalDamage = 5.0)

        DamagePipeline.calculate(session)

        assertEquals(150.0, session.normalDamage(), 1e-9)
    }

    @Test
    fun `cancel stops the remaining calculate handlers`() {
        val log = ArrayList<String>()
        val fixture = CombatFixture(
            handlers = listOf(
                RecordingHandler("dodge", CombatTrigger.DEFENSE, 50, log = log) { session, _ -> session.cancel() },
                RecordingHandler("later", CombatTrigger.ATTACK, 60, log = log),
            ),
        )
        val session = fixture.session()

        assertEquals(CalculationOutcome.CANCELLED, DamagePipeline.calculate(session))
        assertEquals(listOf("dodge=0.0"), log)
    }

    @Test
    fun `scales the attribute part by sweep ratio and attack cooldown`() {
        val attacker = FakeParticipant("attacker", mapOf(physicalDamage to 10.0))
        val sweep = CombatFixture(attacker = attacker, settings = DamageSettings(sweepRatio = 0.3))
            .session(originalDamage = 1.0, cause = EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)
        val charged = CombatFixture(attacker = attacker, settings = DamageSettings(scaleByAttackCooldown = true))
            .session(originalDamage = 1.0, attackCooldown = 0.5)

        assertEquals(1.0 + 10.0 * 0.3, sweep.damage, 1e-9)
        assertEquals(1.0 + 10.0 * (0.2 + 0.8 * 0.25), charged.damage, 1e-9)
    }

    @Test
    fun `mythic ignore-armor hits can bypass builtin defense`() {
        val defender = FakeParticipant("defender", mapOf(BuiltinAttributes.PHYSICAL_DEFENSE to 100.0))
        val bypass = CombatFixture(defender = defender, settings = DamageSettings(ignoreArmorBypassesDefense = true))
            .session(originalDamage = 10.0, ignoresArmor = true)
        val kept = CombatFixture(defender = defender).session(originalDamage = 10.0, ignoresArmor = true)

        DamagePipeline.calculate(bypass)
        DamagePipeline.calculate(kept)

        assertEquals(10.0, bypass.normalDamage(), 1e-9)
        assertEquals(5.0, kept.normalDamage(), 1e-9)
    }

    @Test
    fun `settle applies true damage and lifesteal from the health actually lost`() {
        val fixture = CombatFixture(
            attacker = FakeParticipant(
                "attacker",
                mapOf(BuiltinAttributes.TRUE_DAMAGE to 5.0, BuiltinAttributes.LIFESTEAL to 0.5),
            ),
            defender = FakeParticipant("defender", health = 3.0),
        )
        val session = fixture.session()
        DamagePipeline.calculate(session)
        session.finalDamage = 4.0

        DamagePipeline.settle(session, landed = true)

        assertEquals(listOf("true_damage defender 5.0", "heal attacker 3.5"), fixture.actions.calls)
        assertEquals(DamageSession.State.CLOSED, session.state)
    }

    @Test
    fun `a cancelled hit only applies the effects queued while calculating`() {
        val log = ArrayList<String>()
        val fixture = CombatFixture(
            attacker = FakeParticipant("attacker", mapOf(BuiltinAttributes.TRUE_DAMAGE to 5.0)),
            handlers = listOf(
                RecordingHandler("dodge", CombatTrigger.DEFENSE, 50, log = log) { session, _ ->
                    session.queue(CombatEffect.ActionBar(session.defender, "dodged"))
                    session.cancel()
                },
                RecordingHandler("after", CombatTrigger.AFTER_DEFENSE, 50, runWhenZero = true, log = log),
            ),
        )
        val session = fixture.session()
        DamagePipeline.calculate(session)

        DamagePipeline.settle(session, landed = false)

        assertEquals(listOf("actionbar defender dodged"), fixture.actions.calls)
        assertEquals(listOf("dodge=0.0"), log)
    }

    @Test
    fun `failing handlers are skipped or fall back to vanilla by policy`() {
        val failing = RecordingHandler("broken", CombatTrigger.ATTACK, 10) { _, _ -> error("boom") }
        val skipped = CombatFixture(handlers = listOf(failing)).session(originalDamage = 8.0)
        val fallback = CombatFixture(
            handlers = listOf(failing),
            settings = DamageSettings(scriptErrorPolicy = ScriptErrorPolicy.VANILLA),
        ).session(originalDamage = 8.0)

        assertEquals(CalculationOutcome.APPLIED, DamagePipeline.calculate(skipped))
        assertEquals(8.0, skipped.normalDamage(), 1e-9)
        assertEquals(CalculationOutcome.FALLBACK, DamagePipeline.calculate(fallback))
    }

    @Test
    fun `working variables are read-only after calculation and reject non-finite values`() {
        val session = CombatFixture().session()
        assertFailsWith<IllegalArgumentException> { session.damage = Double.NaN }

        DamagePipeline.calculate(session)

        assertFailsWith<IllegalStateException> { session.damage = 1.0 }
        assertFailsWith<IllegalStateException> { session.cancel() }
        DamagePipeline.discard(session)
        assertFailsWith<IllegalStateException> { session.queue(CombatEffect.Heal(session.attacker, 1.0)) }
    }

    @Test
    fun `cooldowns block until the given ticks passed`() {
        val fixture = CombatFixture()
        val first = fixture.session()
        assertTrue(first.cooldown(fixture.defender, "thorns", 10))
        assertFalse(first.cooldown(fixture.defender, "thorns", 10))
        assertTrue(first.cooldown(fixture.attacker, "thorns", 10))

        fixture.tick += 10
        assertTrue(fixture.session().cooldown(fixture.defender, "thorns", 10))
    }
}
