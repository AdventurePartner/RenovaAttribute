package org.renova.renovaattribute.damage

import com.aystudio.core.bukkit.util.common.TextUtil
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.attribute.Attribute
import org.bukkit.damage.DamageSource
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.StatValue
import org.renova.renovaattribute.source.BuffSource
import org.renova.renovaattribute.util.RenovaLog
import org.bukkit.damage.DamageType as VanillaDamageType

/** Casts a MythicMobs skill; set by the MythicMobs hook while the integration is enabled. */
fun interface SkillCaster {
    fun cast(caster: LivingEntity, skill: String, target: LivingEntity?): Boolean
}

object CombatSkills {
    @Volatile
    var caster: SkillCaster? = null
}

/** Marks damage dealt by combat effects so the pipeline does not process it a second time. */
internal object SecondaryDamage {
    private var depth = 0

    val active: Boolean
        get() = depth > 0

    fun run(block: () -> Unit) {
        depth++
        try {
            block()
        } finally {
            depth--
        }
    }
}

internal object BukkitCombatActions : CombatActions {
    private val legacy = LegacyComponentSerializer.builder()
        .character(LegacyComponentSerializer.SECTION_CHAR)
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build()

    override fun applyTrueDamage(target: CombatParticipant, amount: Double): Double {
        val entity = alive(target) ?: return 0.0
        val before = entity.health
        entity.health = (before - amount).coerceAtLeast(0.0)
        return before - entity.health
    }

    override fun heal(target: CombatParticipant, amount: Double) {
        val entity = alive(target) ?: return
        if (amount <= 0.0) {
            return
        }
        val maxHealth = entity.getAttribute(Attribute.MAX_HEALTH)?.value ?: entity.health
        entity.health = (entity.health + amount).coerceAtMost(maxHealth)
    }

    override fun dealDamage(source: CombatParticipant, target: CombatParticipant, amount: Double) {
        val victim = alive(target) ?: return
        if (amount <= 0.0) {
            return
        }
        val attacker = source.entity?.takeIf { it.isValid }
        val damageSource = DamageSource.builder(VanillaDamageType.GENERIC).apply {
            if (attacker != null) {
                withCausingEntity(attacker)
                withDirectEntity(attacker)
            }
        }.build()
        SecondaryDamage.run { victim.damage(amount, damageSource) }
    }

    override fun addBuff(
        target: CombatParticipant,
        key: AttributeKey,
        value: StatValue,
        durationTicks: Long,
        tag: String,
    ) {
        val entity = alive(target) ?: return
        BuffSource.upsert(entity, key, value, durationTicks, tag)
    }

    override fun sendMessage(target: CombatParticipant, text: String) {
        target.entity?.takeIf { it.isValid }?.sendMessage(TextUtil.formatHexColor(text))
    }

    override fun sendActionBar(target: CombatParticipant, text: String) {
        val player = target.entity as? Player ?: return
        if (player.isOnline) {
            player.sendActionBar(legacy.deserialize(TextUtil.formatHexColor(text)))
        }
    }

    override fun castSkill(caster: CombatParticipant, skill: String, target: CombatParticipant?) {
        val skillCaster = CombatSkills.caster ?: return
        val entity = alive(caster) ?: return
        if (!skillCaster.cast(entity, skill, target?.entity?.takeIf { it.isValid })) {
            RenovaLog.throttled("combat-skill:$skill", "MythicMobs skill '$skill' could not be cast")
        }
    }

    override fun canCastSkills(): Boolean = CombatSkills.caster != null

    private fun alive(participant: CombatParticipant): LivingEntity? =
        participant.entity?.takeIf { it.isValid && !it.isDead }
}
