package com.toolx.app.tools.units

import kotlin.math.roundToInt

/**
 * Shoe sizes, converted through foot length with the standard formulas (pure; unit-tested).
 *
 * UK and US sizes count in barleycorns (1/3 inch) of the shoe's *last*, which is the foot plus
 * an allowance (0.5 in here, matching common retail charts). EU sizes count Paris points
 * (2/3 cm) of the last, foot + 1.5 cm. Asian sizes are the foot length itself. Real brands
 * differ by about half a size.
 */
internal object ShoeSizes {

    private const val ALLOWANCE_IN = 0.5
    private const val EU_ALLOWANCE_CM = 1.5
    private const val CM_PER_IN = 2.54

    enum class System(val label: String, val unit: String, val step: Double, val group: String) {
        FOOT_CM("Foot length", "cm", 0.1, "Measure"),
        FOOT_IN("Foot length", "in", 0.05, "Measure"),
        // EU-style sizes are whole numbers on almost all charts.
        EU("EU / Europe", "", 1.0, "Adult"),
        UK("UK", "", 0.5, "Adult"),
        US_MEN("US men's", "", 0.5, "Adult"),
        US_WOMEN("US women's", "", 0.5, "Adult"),
        AUSTRALIA("Australia (UK sizing)", "", 0.5, "Adult"),
        JAPAN("Japan", "cm", 0.5, "Adult"),
        MONDOPOINT("Mondopoint / Korea / China", "mm", 5.0, "Adult"),
        BRAZIL("Brazil", "", 1.0, "Adult"),
        RUSSIA("Russia", "", 1.0, "Adult"),
        UK_KIDS("UK kids", "", 0.5, "Kids"),
        US_KIDS("US kids", "", 0.5, "Kids"),
    }

    /** Last length in inches → size, for the barleycorn systems. */
    private fun barleycorn(lastIn: Double, offset: Double) = 3 * lastIn - offset
    private val offsets = mapOf(
        System.UK to 25.0, System.AUSTRALIA to 25.0, System.US_MEN to 24.0, System.US_WOMEN to 22.5,
        System.UK_KIDS to 12.0, System.US_KIDS to 11.0 + 2.0 / 3.0,
    )

    /** Exact (unrounded) size in [system] for a foot of [footCm]. */
    fun fromFoot(system: System, footCm: Double): Double {
        val lastIn = footCm / CM_PER_IN + ALLOWANCE_IN
        val eu = 1.5 * (footCm + EU_ALLOWANCE_CM)
        return when (system) {
            System.FOOT_CM -> footCm
            System.FOOT_IN -> footCm / CM_PER_IN
            System.EU -> eu
            System.BRAZIL -> eu - 2
            System.RUSSIA -> eu - 1
            System.JAPAN -> footCm
            System.MONDOPOINT -> footCm * 10
            else -> barleycorn(lastIn, offsets.getValue(system))
        }
    }

    /** Foot length in cm for a size in [system] (the exact inverse of [fromFoot]). */
    fun toFoot(system: System, size: Double): Double = when (system) {
        System.FOOT_CM, System.JAPAN -> size
        System.FOOT_IN -> size * CM_PER_IN
        System.MONDOPOINT -> size / 10
        System.EU -> size / 1.5 - EU_ALLOWANCE_CM
        System.BRAZIL -> (size + 2) / 1.5 - EU_ALLOWANCE_CM
        System.RUSSIA -> (size + 1) / 1.5 - EU_ALLOWANCE_CM
        else -> ((size + offsets.getValue(system)) / 3 - ALLOWANCE_IN) * CM_PER_IN
    }

    /** Rounded to the system's usual steps (half sizes, 5 mm…), or null if outside its range. */
    fun display(system: System, footCm: Double): String? {
        val v = fromFoot(system, footCm)
        val r = (v / system.step).roundToInt() * system.step
        val inRange = when (system) {
            System.UK_KIDS -> r in 0.0..13.5
            System.US_KIDS -> r in 1.0..13.5
            System.UK, System.AUSTRALIA -> r in 1.0..16.0
            System.US_MEN -> r in 2.0..17.0
            System.US_WOMEN -> r in 3.5..18.5
            System.EU, System.BRAZIL, System.RUSSIA -> r > 15.0
            else -> r > 0
        }
        if (!inRange) return null
        return when (system) {
            System.FOOT_CM -> "%.1f cm".format(r)
            System.FOOT_IN -> "%.2f in".format(r)
            System.MONDOPOINT -> "${r.roundToInt()} mm"
            System.JAPAN -> trim(r) + " cm"
            else -> trim(r)
        }
    }

    private fun trim(x: Double): String = if (x % 1.0 == 0.0) x.toInt().toString() else x.toString()
}
