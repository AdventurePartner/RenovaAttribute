package org.renova.renovaattribute.script

import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.ModifierMode
import org.renova.renovaattribute.api.StatValue
import org.renova.renovaattribute.core.AttributeRegistry
import org.renova.renovaattribute.damage.CombatEffect
import org.renova.renovaattribute.damage.CombatParticipant
import org.renova.renovaattribute.damage.DamageSession
import org.renova.renovaattribute.formula.AttributeKeyCache

/**
 * Exposes a [DamageSession] to Lua as `ctx`. One userdata is created per hit; the method tables
 * are shared and unreachable from scripts because the sandbox removes getmetatable/setmetatable.
 */
internal object LuaDamageBinding {
    private class SessionRef(val session: DamageSession) {
        var attacker: LuaValue? = null
        var defender: LuaValue? = null
    }

    private class ParticipantRef(val session: DamageSession, val participant: CombatParticipant)

    private val sessionMethods = LuaTable()
    private val sessionMeta = LuaTable()
    private val participantMethods = LuaTable()
    private val participantMeta = LuaTable()

    fun context(session: DamageSession): LuaValue {
        (session.scriptHandle as? LuaValue)?.let { return it }
        return LuaUserdata(SessionRef(session), sessionMeta).also { session.scriptHandle = it }
    }

    init {
        sessionMeta.rawset("__index", object : TwoArgFunction() {
            override fun call(self: LuaValue, key: LuaValue): LuaValue {
                val method = sessionMethods.rawget(key)
                if (!method.isnil()) {
                    return method
                }
                return guarded { field(sessionRef(self), key.tojstring()) }
            }
        })
        sessionMeta.rawset("__newindex", readOnly("ctx"))
        sessionMeta.rawset("__metatable", LuaValue.valueOf("locked"))
        participantMeta.rawset("__index", participantMethods)
        participantMeta.rawset("__newindex", readOnly("entity"))
        participantMeta.rawset("__metatable", LuaValue.valueOf("locked"))
        registerSessionMethods()
        registerParticipantMethods()
    }

    private fun field(ref: SessionRef, name: String): LuaValue {
        val session = ref.session
        session.checkOpen()
        return when (name) {
            "attacker" -> ref.attacker ?: participant(session, session.attacker).also { ref.attacker = it }
            "defender" -> ref.defender ?: participant(session, session.defender).also { ref.defender = it }
            "type" -> LuaValue.valueOf(session.type.name.lowercase())
            "cause" -> session.cause?.let { LuaValue.valueOf(it.name) } ?: LuaValue.NIL
            "source" -> LuaValue.valueOf(session.origin.name.lowercase())
            "projectile" -> LuaValue.valueOf(session.projectile)
            "original_damage" -> LuaValue.valueOf(session.originalDamage)
            "element" -> session.element?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            "ignores_armor" -> LuaValue.valueOf(session.ignoresArmor)
            "attack_cooldown" -> LuaValue.valueOf(session.attackCooldown)
            "tick" -> LuaValue.valueOf(session.tick.toDouble())
            "stage" -> LuaValue.valueOf(
                if (session.state == DamageSession.State.CALCULATING) "calculate" else "settle",
            )
            "final_damage" -> if (session.state == DamageSession.State.CALCULATING) {
                LuaValue.NIL
            } else {
                LuaValue.valueOf(session.finalDamage)
            }
            else -> LuaValue.NIL
        }
    }

    private fun registerSessionMethods() {
        number("damage", { it.damage }) { session, value -> session.damage = value }
        method("add_damage") { session, args -> session.damage += args.checkdouble(2); LuaValue.NONE }
        method("mul_damage") { session, args -> session.damage *= args.checkdouble(2); LuaValue.NONE }
        number("damage_boost", { it.damageBoost }) { session, value -> session.damageBoost = value }
        number("crit_chance", { it.criticalChance }) { session, value -> session.criticalChance = value }
        number("crit_damage", { it.criticalDamage }) { session, value -> session.criticalDamage = value }
        method("is_crit") { session, _ -> LuaValue.valueOf(session.critical) }
        method("set_crit") { session, args -> session.critical = args.checkboolean(2); LuaValue.NONE }
        number("defense", { it.defense }) { session, value -> session.defense = value }
        number("true_damage", { it.trueDamage }) { session, value -> session.trueDamage = value }
        method("add_true_damage") { session, args -> session.trueDamage += args.checkdouble(2); LuaValue.NONE }
        number("lifesteal", { it.lifesteal }) { session, value -> session.lifesteal = value }
        method("has_damage_tag") { session, args -> LuaValue.valueOf(session.hasDamageTag(args.checkjstring(2))) }
        method("cancel") { session, _ -> session.cancel(); LuaValue.NONE }
        method("is_cancelled") { session, _ -> LuaValue.valueOf(session.cancelled) }

        method("get") { session, args ->
            toLua(session.variable(args.checkjstring(2))) ?: args.arg(3)
        }
        method("set") { session, args ->
            session.setVariable(args.checkjstring(2), fromLua(args.arg(3)))
            LuaValue.NONE
        }
        method("chance") { session, args -> LuaValue.valueOf(session.chance(args.checkdouble(2))) }
        method("random") { session, args -> LuaValue.valueOf(session.random(args.checkdouble(2), args.checkdouble(3))) }
        method("cooldown") { session, args ->
            val owner = participantArg(session, args, 2)
            LuaValue.valueOf(session.cooldown(owner, args.checkjstring(3), args.checklong(4)))
        }

        method("heal") { session, args ->
            session.queue(CombatEffect.Heal(participantArg(session, args, 2), args.checkdouble(3)))
            LuaValue.NONE
        }
        method("deal_damage") { session, args ->
            session.queue(
                CombatEffect.DealDamage(
                    participantArg(session, args, 2),
                    participantArg(session, args, 3),
                    args.checkdouble(4),
                ),
            )
            LuaValue.NONE
        }
        method("add_buff") { session, args ->
            val target = participantArg(session, args, 2)
            val key = attributeKey(target, args.checkjstring(3))
            val mode = ModifierMode.entries.firstOrNull { it.name.equals(args.checkjstring(4), true) }
                ?: throw LuaError("unknown buff mode '${args.tojstring(4)}' (BASE, FLAT, PERCENT, MULTIPLY)")
            val tag = if (args.isnoneornil(7)) {
                "combat:${session.currentHandler?.id ?: "script"}:$key"
            } else {
                args.checkjstring(7)
            }
            session.queue(
                CombatEffect.AddBuff(target, key, StatValue.of(mode, args.checkdouble(5)), args.checklong(6), tag),
            )
            LuaValue.NONE
        }
        method("message") { session, args ->
            session.queue(CombatEffect.Message(participantArg(session, args, 2), args.checkjstring(3)))
            LuaValue.NONE
        }
        method("actionbar") { session, args ->
            session.queue(CombatEffect.ActionBar(participantArg(session, args, 2), args.checkjstring(3)))
            LuaValue.NONE
        }
        method("cast_skill") { session, args ->
            check(session.environment.actions.canCastSkills()) { "cast_skill requires the MythicMobs integration" }
            val target = if (args.isnoneornil(4)) null else participantArg(session, args, 4)
            session.queue(CombatEffect.CastSkill(participantArg(session, args, 2), args.checkjstring(3), target))
            LuaValue.NONE
        }
    }

    private fun registerParticipantMethods() {
        participantMethod("attr") { ref, args ->
            val key = attributeKey(ref.participant, args.checkjstring(2))
            LuaValue.valueOf(ref.participant.attributes[key])
        }
        participantMethod("health") { ref, _ -> LuaValue.valueOf(ref.participant.health) }
        participantMethod("max_health") { ref, _ -> LuaValue.valueOf(ref.participant.maxHealth) }
        participantMethod("is_player") { ref, _ -> LuaValue.valueOf(ref.participant.isPlayer) }
        participantMethod("type") { ref, _ -> LuaValue.valueOf(ref.participant.typeKey) }
        participantMethod("name") { ref, _ -> LuaValue.valueOf(ref.participant.name) }
        participantMethod("has_tag") { ref, args -> LuaValue.valueOf(ref.participant.hasTag(args.checkjstring(2))) }
        participantMethod("uuid") { ref, _ -> LuaValue.valueOf(ref.participant.uniqueId.toString()) }
        participantMethod("is_dead") { ref, _ -> LuaValue.valueOf(ref.participant.isDead) }
        participantMethod("is_valid") { ref, _ -> LuaValue.valueOf(ref.participant.isValid) }
    }

    /** Registers `name()` as getter and `set_name(value)` as setter. */
    private fun number(
        name: String,
        getter: (DamageSession) -> Double,
        setter: (DamageSession, Double) -> Unit,
    ) {
        method(name) { session, _ -> LuaValue.valueOf(getter(session)) }
        method("set_$name") { session, args ->
            setter(session, args.checkdouble(2))
            LuaValue.NONE
        }
    }

    private fun method(name: String, body: (DamageSession, Varargs) -> Varargs) {
        sessionMethods.rawset(name, object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val self = args.arg1()
                val ref = self.touserdata() as? SessionRef
                    ?: throw LuaError("call ctx:$name(...) with ':'")
                return guarded {
                    ref.session.checkOpen()
                    body(ref.session, args)
                }
            }
        })
    }

    private fun participantMethod(name: String, body: (ParticipantRef, Varargs) -> LuaValue) {
        participantMethods.rawset(name, object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val ref = args.arg1().touserdata() as? ParticipantRef
                    ?: throw LuaError("call entity:$name(...) with ':'")
                return guarded {
                    ref.session.checkOpen()
                    body(ref, args)
                }
            }
        })
    }

    private fun participant(session: DamageSession, participant: CombatParticipant): LuaValue =
        LuaUserdata(ParticipantRef(session, participant), participantMeta)

    private fun participantArg(session: DamageSession, args: Varargs, index: Int): CombatParticipant {
        val ref = args.arg(index).touserdata() as? ParticipantRef
        if (ref == null || ref.session !== session) {
            throw LuaError("bad argument #$index: expected ctx.attacker or ctx.defender")
        }
        return ref.participant
    }

    private fun attributeKey(participant: CombatParticipant, raw: String): AttributeKey {
        val key = AttributeKeyCache.parse(raw, AttributeRegistry.defaultNamespace)
        require(participant.attributes.has(key)) { "Unknown attribute $key" }
        return key
    }

    private fun sessionRef(value: LuaValue): SessionRef =
        value.touserdata() as? SessionRef ?: throw LuaError("ctx expected")

    private fun readOnly(name: String) = object : ThreeArgFunction() {
        override fun call(self: LuaValue, key: LuaValue, value: LuaValue): LuaValue =
            throw LuaError("$name is read-only; use its methods instead of assigning '${key.tojstring()}'")
    }

    private fun toLua(value: Any?): LuaValue? = when (value) {
        null -> null
        is Double -> LuaValue.valueOf(value)
        is Boolean -> LuaValue.valueOf(value)
        is String -> LuaValue.valueOf(value)
        else -> null
    }

    private fun fromLua(value: LuaValue): Any? = when {
        value.isnil() -> null
        value.isboolean() -> value.toboolean()
        value.type() == LuaValue.TNUMBER -> value.todouble()
        value.isstring() -> value.tojstring()
        else -> throw LuaError("ctx:set only accepts numbers, booleans, strings or nil")
    }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (error: IllegalStateException) {
        throw LuaError(error.message)
    } catch (error: IllegalArgumentException) {
        throw LuaError(error.message)
    }
}
