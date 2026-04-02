package com.danidipp.sneakynpcs.shop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShopSessionLedgerTest {
    @Test
    fun `aggregates spent and earned totals per currency without conversion`() {
        val ledger = ShopSessionLedger()

        ledger.recordSpent(
            listOf(
                CurrencyUnits("silver", 2L),
                CurrencyUnits("gold", 1L),
                CurrencyUnits("silver", 3L),
            )
        )
        ledger.recordEarned(
            listOf(
                CurrencyUnits("silver", 4L),
                CurrencyUnits("penny", 10L),
                CurrencyUnits("silver", 1L),
            )
        )

        assertEquals(mapOf("silver" to 5L, "gold" to 1L), ledger.spentTotals())
        assertEquals(mapOf("silver" to 5L, "penny" to 10L), ledger.earnedTotals())
        assertTrue(ledger.hasActivity())
    }

    @Test
    fun `ignores zero or negative entries`() {
        val ledger = ShopSessionLedger()

        ledger.recordSpent(listOf(CurrencyUnits("silver", 0L), CurrencyUnits("gold", -1L)))
        ledger.recordEarned(listOf(CurrencyUnits("penny", 0L)))

        assertEquals(emptyMap(), ledger.spentTotals())
        assertEquals(emptyMap(), ledger.earnedTotals())
        assertFalse(ledger.hasActivity())
    }
}
