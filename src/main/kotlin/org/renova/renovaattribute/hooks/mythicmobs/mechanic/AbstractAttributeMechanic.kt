package org.renova.renovaattribute.hooks.mythicmobs.mechanic

import io.lumine.mythic.api.adapters.AbstractEntity
import io.lumine.mythic.api.config.MythicLineConfig
import io.lumine.mythic.api.skills.ISkillMechanic
import io.lumine.mythic.api.skills.ITargetedEntitySkill
import io.lumine.mythic.api.skills.SkillMetadata
import io.lumine.mythic.api.skills.SkillResult
import io.lumine.mythic.api.skills.ThreadSafetyLevel
import io.lumine.mythic.api.skills.placeholders.PlaceholderDouble
import io.lumine.mythic.api.skills.placeholders.PlaceholderInt
import io.lumine.mythic.api.skills.placeholders.PlaceholderString
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.StatValue
import org.renova.renovaattribute.core.AttributeRegistry
import org.renova.renovaattribute.hooks.mythicmobs.MythicHook

abstract class AbstractAttributeMechanic(config: MythicLineConfig) :
    ISkillMechanic,
    ITargetedEntitySkill {

    protected val attribute: PlaceholderString = config.getPlaceholderString(
        arrayOf("attribute", "attr", "key"),
        "",
    )
    protected val value: PlaceholderDouble = config.getPlaceholderDouble(
        arrayOf("value", "v", "amount", "a"),
        0.0,
    )
    protected val duration: PlaceholderInt = config.getPlaceholderInteger(
        arrayOf("duration", "d", "ticks", "t"),
        0,
    )
    protected val tag: PlaceholderString = config.getPlaceholderString(
        arrayOf("tag", "source", "id"),
        "renova-attribute",
    )

    override fun getThreadSafetyLevel(): ThreadSafetyLevel = ThreadSafetyLevel.SYNC_ONLY

    final override fun castAtEntity(data: SkillMetadata, target: AbstractEntity): SkillResult {
        if (!MythicHook.mechanicsEnabled) {
            return SkillResult.CONDITION_FAILED
        }
        val rawKey = attribute.get(data, target)
        if (rawKey.isBlank()) {
            return SkillResult.INVALID_CONFIG
        }
        val key = AttributeKey.parse(rawKey, AttributeRegistry.defaultNamespace)
        val amount = value.get(data, target)
        val ticks = duration.get(data, target).toLong()
        val sourceTag = tag.get(data, target)
        apply(target.bukkitEntity, key, amount, ticks, sourceTag)
        return SkillResult.SUCCESS
    }

    protected abstract fun apply(
        target: org.bukkit.entity.Entity,
        key: AttributeKey,
        value: Double,
        durationTicks: Long,
        tag: String,
    )
}
