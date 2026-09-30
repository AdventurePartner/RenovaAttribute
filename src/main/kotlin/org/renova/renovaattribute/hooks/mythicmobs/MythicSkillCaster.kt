package org.renova.renovaattribute.hooks.mythicmobs

import io.lumine.mythic.bukkit.MythicBukkit
import org.bukkit.entity.LivingEntity
import org.renova.renovaattribute.damage.SkillCaster

internal object MythicSkillCaster : SkillCaster {
    override fun cast(caster: LivingEntity, skill: String, target: LivingEntity?): Boolean {
        val api = MythicBukkit.inst().apiHelper
        if (target == null) {
            return api.castSkill(caster, skill)
        }
        return api.castSkill(caster, skill, target, caster.location, listOf(target), emptyList(), 1.0f)
    }
}
