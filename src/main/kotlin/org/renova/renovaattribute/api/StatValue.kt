package org.renova.renovaattribute.api

data class StatValue(
    val base: Double = 0.0,
    val flat: Double = 0.0,
    val percent: Double = 0.0,
    val multiplier: Double = 1.0,
    val setValue: Double? = null,
) {
    init {
        require(base.isFinite()) { "base must be finite" }
        require(flat.isFinite()) { "flat must be finite" }
        require(percent.isFinite()) { "percent must be finite" }
        require(multiplier.isFinite() && multiplier >= 0.0) {
            "multiplier must be finite and non-negative"
        }
        require(setValue == null || setValue.isFinite()) { "setValue must be finite" }
    }

    fun merge(higherPriority: StatValue): StatValue {
        if (higherPriority.setValue != null) {
            return higherPriority
        }
        return StatValue(
            base = base + higherPriority.base,
            flat = flat + higherPriority.flat,
            percent = percent + higherPriority.percent,
            multiplier = multiplier * higherPriority.multiplier,
            setValue = setValue,
        )
    }

    fun calculate(): Double {
        val result = ((setValue ?: 0.0) + base + flat) * (1.0 + percent) * multiplier
        require(result.isFinite()) { "attribute calculation produced a non-finite value" }
        return result
    }

    companion object {
        @JvmStatic
        fun base(value: Double): StatValue = StatValue(base = value)

        @JvmStatic
        fun flat(value: Double): StatValue = StatValue(flat = value)

        @JvmStatic
        fun percent(value: Double): StatValue = StatValue(percent = value)

        @JvmStatic
        fun multiply(value: Double): StatValue = StatValue(multiplier = 1.0 + value)

        @JvmStatic
        fun set(value: Double): StatValue = StatValue(setValue = value)

        @JvmStatic
        fun of(mode: ModifierMode, value: Double): StatValue = when (mode) {
            ModifierMode.BASE -> base(value)
            ModifierMode.FLAT -> flat(value)
            ModifierMode.PERCENT -> percent(value)
            ModifierMode.MULTIPLY -> multiply(value)
        }
    }
}
