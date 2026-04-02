package com.danidipp.sneakynpcs.shop

data class CurrencyUnits(
    val currencyId: String,
    val units: Long,
)

class ShopSessionLedger {
    private val spent = linkedMapOf<String, Long>()
    private val earned = linkedMapOf<String, Long>()

    fun recordSpent(amounts: Iterable<CurrencyUnits>) {
        recordInto(spent, amounts)
    }

    fun recordEarned(amounts: Iterable<CurrencyUnits>) {
        recordInto(earned, amounts)
    }

    fun spentTotals(): Map<String, Long> = spent.toMap()

    fun earnedTotals(): Map<String, Long> = earned.toMap()

    fun hasActivity(): Boolean = spent.isNotEmpty() || earned.isNotEmpty()

    private fun recordInto(target: MutableMap<String, Long>, amounts: Iterable<CurrencyUnits>) {
        for (amount in amounts) {
            if (amount.units <= 0L) continue
            target.merge(amount.currencyId, amount.units, Long::plus)
        }
    }
}
