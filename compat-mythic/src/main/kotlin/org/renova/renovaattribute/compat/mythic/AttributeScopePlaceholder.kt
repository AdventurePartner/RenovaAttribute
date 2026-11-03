package org.renova.renovaattribute.compat.mythic

import io.lumine.mythic.api.skills.placeholders.PlaceholderString
import io.lumine.mythic.core.skills.placeholders.PlaceholderContext
import io.lumine.mythic.core.skills.placeholders.types.EntityScopedPlaceholder
import io.lumine.mythic.core.skills.placeholders.types.NumericEntityScopedPlaceholder
import io.lumine.mythic.core.utils.annotations.MythicPlaceholder
import org.renova.renovaattribute.hooks.mythicmobs.AttributePlaceholder

/**
 * `<caster.attr.KEY>`, `<target.attr.KEY>` and the other entity scopes MythicMobs adds for
 * [EntityScopedPlaceholder] (`c`, `trigger`, `parent`, `owner`).
 *
 * MythicMobs splits the placeholder on '.', so a namespaced key like `renova.physical_damage`
 * arrives as two extra segments; they are joined back here and all of them are consumed, so
 * none is taken for a meta keyword. Rounding -1 prints the plain double like the legacy
 * placeholders did; `{r=N}` on the placeholder still rounds.
 */
@MythicPlaceholder(placeholder = "attr", usedPlaceholderArguments = -1)
class AttributeScopePlaceholder(
    arguments: EntityScopedPlaceholder.EntityScopedPlaceholderArguments,
) : NumericEntityScopedPlaceholder(arguments, -1) {
    private val key: PlaceholderString

    init {
        val extra = placeholderSegmentsManager.extraArgs
        key = PlaceholderString.of(extra.joinToString(".") { it.key() })
        usedPlaceholderArguments.set(extra.size)
        initializeMetaKeywords()
    }

    override fun applyToScopeWithNumericFormatting(context: PlaceholderContext): Number? {
        val entity = getEntity.get(context)?.bukkitEntity ?: return null
        return AttributePlaceholder.lookup(entity, key.get(context)) ?: 0.0
    }
}
