package org.renova.renovaattribute.script

import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.attribute.AttributeCategory
import org.renova.renovaattribute.attribute.AttributeDefinition
import org.renova.renovaattribute.attribute.BuiltinAttributes
import org.renova.renovaattribute.damage.CalculationOutcome
import org.renova.renovaattribute.damage.CombatFixture
import org.renova.renovaattribute.damage.CombatHandler
import org.renova.renovaattribute.damage.CombatHandlerSpec
import org.renova.renovaattribute.damage.CombatTrigger
import org.renova.renovaattribute.damage.DamagePipeline
import org.renova.renovaattribute.damage.DamageType
import org.renova.renovaattribute.damage.FakeParticipant
import org.renova.renovaattribute.damage.ScriptedRandom
import org.bukkit.event.entity.EntityDamageEvent
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LuaCombatScriptTest {
    @TempDir
    lateinit var dataFolder: Path

    private fun key(name: String) = AttributeKey.of("renova", name)

    private fun compile(vararg handlers: Pair<AttributeKey, CombatHandlerSpec>): List<CombatHandler> {
        val definitions = handlers.groupBy({ it.first }, { it.second }).map { (key, specs) ->
            AttributeDefinition(key, key.name, AttributeCategory.UTILITY, 0.0, combat = specs)
        }
        return LuaCombatScripts.compile(definitions, dataFolder.toFile(), 10_000)
    }

    private fun spec(trigger: CombatTrigger, priority: Int, script: String) =
        CombatHandlerSpec(trigger, priority, script = script)

    @Test
    fun `script multiplies this hit only when its condition holds`() {
        val execute = key("execute")
        val handlers = compile(
            execute to spec(
                CombatTrigger.ATTACK,
                150,
                """
                if ctx.defender:health() / ctx.defender:max_health() < 0.3 then
                  ctx:mul_damage(1 + value)
                end
                """.trimIndent(),
            ),
        )
        val attacker = FakeParticipant("attacker", mapOf(execute to 0.5))
        val low = CombatFixture(attacker, FakeParticipant("low", health = 5.0), handlers = handlers).session()
        val high = CombatFixture(attacker, FakeParticipant("high", health = 20.0), handlers = handlers).session()

        DamagePipeline.calculate(low)
        DamagePipeline.calculate(high)

        assertEquals(7.5, low.normalDamage(), 1e-9)
        assertEquals(5.0, high.normalDamage(), 1e-9)
    }

    @Test
    fun `penetration only matters when it runs before the builtin defense`() {
        val penetration = key("armor-penetration")
        val script = "ctx:set_defense(ctx:defense() * (1 - value))"
        val attacker = FakeParticipant("attacker", mapOf(penetration to 1.0))
        val defender = FakeParticipant("defender", mapOf(BuiltinAttributes.PHYSICAL_DEFENSE to 100.0))
        val before = CombatFixture(
            attacker,
            defender,
            handlers = compile(penetration to spec(CombatTrigger.ATTACK, 350, script)),
        ).session(originalDamage = 10.0)
        val after = CombatFixture(
            attacker,
            defender,
            handlers = compile(penetration to spec(CombatTrigger.ATTACK, 450, script)),
        ).session(originalDamage = 10.0)

        DamagePipeline.calculate(before)
        DamagePipeline.calculate(after)

        assertEquals(10.0, before.normalDamage(), 1e-9)
        assertEquals(5.0, after.normalDamage(), 1e-9)
    }

    @Test
    fun `dodge cancels the hit and still delivers its message`() {
        val dodge = key("dodge")
        val fixture = CombatFixture(
            defender = FakeParticipant("defender", mapOf(dodge to 0.2)),
            random = ScriptedRandom(0.1),
            handlers = compile(
                dodge to spec(
                    CombatTrigger.DEFENSE,
                    50,
                    "if ctx:chance(value) then ctx:cancel(); ctx:actionbar(ctx.defender, '&a闪避') end",
                ),
            ),
        )
        val session = fixture.session()

        assertEquals(CalculationOutcome.CANCELLED, DamagePipeline.calculate(session))
        DamagePipeline.settle(session, landed = false)

        assertEquals(listOf("actionbar defender &a闪避"), fixture.actions.calls)
    }

    @Test
    fun `shares per-hit variables between handlers of both sides`() {
        val combo = key("combo")
        val guard = key("guard")
        val fixture = CombatFixture(
            attacker = FakeParticipant("attacker", mapOf(combo to 1.0)),
            defender = FakeParticipant("defender", mapOf(guard to 1.0)),
            handlers = compile(
                combo to spec(CombatTrigger.ATTACK, 10, "ctx:set('combo.stacks', 3)"),
                guard to spec(CombatTrigger.DEFENSE, 20, "ctx:add_damage(ctx:get('combo.stacks', 0))"),
            ),
        )
        val session = fixture.session()

        DamagePipeline.calculate(session)

        assertEquals(8.0, session.normalDamage(), 1e-9)
    }

    @Test
    fun `settle scripts see the dealt damage and respect cooldowns`() {
        val thorns = key("thorns")
        val fixture = CombatFixture(
            defender = FakeParticipant("defender", mapOf(thorns to 0.5)),
            handlers = compile(
                thorns to spec(
                    CombatTrigger.AFTER_DEFENSE,
                    300,
                    """
                    if ctx.final_damage > 0 and ctx:cooldown(ctx.defender, 'thorns', 10) then
                      ctx:deal_damage(ctx.defender, ctx.attacker, ctx.final_damage * value)
                    end
                    """.trimIndent(),
                ),
            ),
        )
        repeat(2) {
            val session = fixture.session()
            DamagePipeline.calculate(session)
            session.finalDamage = 4.0
            DamagePipeline.settle(session, landed = true)
        }

        assertEquals(listOf("deal_damage defender->attacker 2.0"), fixture.actions.calls)
    }

    @Test
    fun `settle scripts cannot change the damage any more`() {
        val marker = key("marker")
        val fixture = CombatFixture(
            defender = FakeParticipant("defender", mapOf(marker to 1.0)),
            handlers = compile(
                marker to spec(
                    CombatTrigger.AFTER_DEFENSE,
                    10,
                    "if not pcall(function() ctx:set_damage(1) end) then ctx:heal(ctx.defender, 2) end",
                ),
            ),
        )
        val session = fixture.session()
        DamagePipeline.calculate(session)

        DamagePipeline.settle(session, landed = true)

        assertEquals(listOf("heal defender 2.0"), fixture.actions.calls)
    }

    @Test
    fun `exposes hit facts as read-only fields`() {
        val probe = key("probe")
        val fixture = CombatFixture(
            attacker = FakeParticipant("attacker", mapOf(probe to 1.0)),
            handlers = compile(
                probe to spec(
                    CombatTrigger.ATTACK,
                    10,
                    """
                    if ctx.type == 'magic' and ctx.cause == 'MAGIC' and ctx.element == 'FIRE'
                        and ctx.source == 'vanilla' and ctx.stage == 'calculate' and ctx.final_damage == nil then
                      ctx:set_damage(42)
                    end
                    """.trimIndent(),
                ),
            ),
        )
        val session = fixture.session(
            type = DamageType.MAGIC,
            cause = EntityDamageEvent.DamageCause.MAGIC,
            element = "FIRE",
        )

        DamagePipeline.calculate(session)

        assertEquals(42.0, session.normalDamage(), 1e-9)
    }

    @Test
    fun `reads snapshot attributes and rejects unknown keys`() {
        val probe = key("probe")
        val attacker = FakeParticipant("attacker", mapOf(probe to 1.0, BuiltinAttributes.PHYSICAL_DAMAGE to 12.0))
        val known = CombatFixture(
            attacker,
            handlers = compile(probe to spec(CombatTrigger.ATTACK, 10, "ctx:set_damage(ctx.attacker:attr('physical-damage'))")),
        ).session()
        val unknown = CombatFixture(
            attacker,
            handlers = compile(probe to spec(CombatTrigger.ATTACK, 10, "ctx:set_damage(ctx.attacker:attr('renova:nope'))")),
        ).session(traced = true)

        DamagePipeline.calculate(known)
        DamagePipeline.calculate(unknown)

        assertEquals(12.0, known.normalDamage(), 1e-9)
        assertEquals(17.0, unknown.normalDamage(), 1e-9)
        assertTrue("Unknown attribute renova:nope" in unknown.trace!!.first().error.orEmpty())
    }

    @Test
    fun `rejects writes to ctx fields and non-finite numbers`() {
        val probe = key("probe")
        val fixture = CombatFixture(
            attacker = FakeParticipant("attacker", mapOf(probe to 1.0)),
            handlers = compile(
                probe to spec(CombatTrigger.ATTACK, 10, "ctx.damage = 100"),
                probe to spec(CombatTrigger.ATTACK, 20, "ctx:set_damage(0/0)"),
            ),
        )
        val session = fixture.session(traced = true)

        DamagePipeline.calculate(session)

        assertEquals(5.0, session.normalDamage(), 1e-9)
        val errors = session.trace!!.filter { it.handlerId.startsWith("renova:probe") }.map { it.error.orEmpty() }
        assertTrue("read-only" in errors[0], errors[0])
        assertTrue("finite" in errors[1], errors[1])
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `endless scripts are stopped and skipped`() {
        val probe = key("probe")
        val fixture = CombatFixture(
            attacker = FakeParticipant("attacker", mapOf(probe to 1.0)),
            handlers = compile(probe to spec(CombatTrigger.ATTACK, 10, "while true do end")),
        )
        val session = fixture.session(traced = true)

        assertEquals(CalculationOutcome.APPLIED, DamagePipeline.calculate(session))
        assertTrue("instruction limit" in session.trace!!.first().error.orEmpty())
    }

    @Test
    fun `reports inline script line numbers`() {
        val probe = key("probe")
        val fixture = CombatFixture(
            attacker = FakeParticipant("attacker", mapOf(probe to 1.0)),
            handlers = compile(probe to spec(CombatTrigger.ATTACK, 10, "local a = 1\nerror('boom')")),
        )
        val session = fixture.session(traced = true)

        DamagePipeline.calculate(session)

        val error = session.trace!!.first().error.orEmpty()
        assertTrue("renova:probe#0:2:" in error, error)
    }

    @Test
    fun `queues buffs with a default tag per handler`() {
        val burst = key("burst")
        val fixture = CombatFixture(
            attacker = FakeParticipant("attacker", mapOf(burst to 1.0)),
            handlers = compile(
                burst to spec(CombatTrigger.ATTACK, 10, "ctx:add_buff(ctx.attacker, 'damage-boost', 'percent', 0.2, 60)"),
            ),
        )
        val session = fixture.session()
        DamagePipeline.calculate(session)

        DamagePipeline.settle(session, landed = true)

        val call = fixture.actions.calls.single()
        assertTrue(call.startsWith("add_buff attacker renova:damage-boost"), call)
        assertTrue("percent=0.2" in call && call.endsWith("60 combat:renova:burst#0:renova:damage-boost"), call)
    }

    @Test
    fun `loads script files from the scripts directory only`() {
        val bonus = key("bonus")
        val scripts = Files.createDirectories(dataFolder.resolve("scripts"))
        Files.writeString(scripts.resolve("bonus.lua"), "return function(ctx, value)\n  ctx:add_damage(value)\nend\n")
        val fixture = CombatFixture(
            attacker = FakeParticipant("attacker", mapOf(bonus to 4.0)),
            handlers = compile(bonus to CombatHandlerSpec(CombatTrigger.ATTACK, 10, scriptFile = "scripts/bonus.lua")),
        )
        val session = fixture.session()

        DamagePipeline.calculate(session)

        assertEquals(9.0, session.normalDamage(), 1e-9)
        assertFailsWith<IllegalArgumentException> {
            CombatHandlerSpec(CombatTrigger.ATTACK, 10, scriptFile = "scripts/../config.lua")
        }
        assertFailsWith<IllegalArgumentException> {
            CombatHandlerSpec(CombatTrigger.ATTACK, 10, scriptFile = "other/bonus.lua")
        }
    }
}
