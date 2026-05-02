package com.danidipp.sneakynpcs.menus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaxesMenuPaymentTest {
    @Test
    fun `taxes button maps gold slot`() {
        val button = taxesButtonConfig(14, validPayments(), validReward())

        assertEquals("gold", button?.currencyId)
        assertEquals(100L, button?.currencyAmount)
        assertEquals(100L, button?.taxAmount)
        assertEquals(1, button?.writAmount)
    }

    @Test
    fun `taxes button maps goldbar slot`() {
        val button = taxesButtonConfig(16, validPayments(), validReward())

        assertEquals("goldbar", button?.currencyId)
        assertEquals(1L, button?.currencyAmount)
        assertEquals(100L, button?.taxAmount)
        assertEquals(1, button?.writAmount)
    }

    @Test
    fun `taxes button uses configured currency values`() {
        val button = taxesButtonConfig(
            slot = 14,
            payments = mapOf(14 to TaxesPaymentConfig(currencyId = "copper", taxValuePerUnit = 5L)),
            reward = TaxesRewardConfig(itemId = "item-taxreceipt", taxValue = 250L),
        )

        assertEquals("copper", button?.currencyId)
        assertEquals(50L, button?.currencyAmount)
        assertEquals(250L, button?.taxAmount)
        assertEquals(1, button?.writAmount)
    }

    @Test
    fun `taxes button returns null for unsupported slots`() {
        listOf(0, 13, 15, 17, 53).forEach { slot ->
            assertNull(taxesButtonConfig(slot, validPayments(), validReward()))
        }
    }

    @Test
    fun `exact currency spend prefers inventory before bank`() {
        assertEquals(
            ExactCurrencySpend(inventoryUnits = 70L, bankUnits = 30L),
            calculateExactCurrencySpend(inventoryAvailable = 70L, bankAvailable = 100L, required = 100L)
        )
    }

    @Test
    fun `exact currency spend uses only provided exact currency buckets`() {
        assertNull(calculateExactCurrencySpend(inventoryAvailable = 0L, bankAvailable = 0L, required = 1L))
    }

    @Test
    fun `tax can overpay below zero`() {
        assertTrue(canPayTaxes(60L))
        assertEquals(-40L, bankTaxAfterPayment(bankTax = 60L, taxAmount = 100L))
    }

    @Test
    fun `currency units must divide reward tax value exactly`() {
        assertEquals(50L, calculateTaxesCurrencyUnits(rewardTaxValue = 250L, currencyTaxValue = 5L))
        assertNull(calculateTaxesCurrencyUnits(rewardTaxValue = 100L, currencyTaxValue = 30L))
    }

    @Test
    fun `tax at zero or negative cannot be paid`() {
        assertFalse(canPayTaxes(0L))
        assertFalse(canPayTaxes(-1L))
    }

    private fun validPayments(): Map<Int, TaxesPaymentConfig> = mapOf(
        14 to TaxesPaymentConfig(currencyId = "gold", taxValuePerUnit = 1L),
        16 to TaxesPaymentConfig(currencyId = "goldbar", taxValuePerUnit = 100L),
    )

    private fun validReward(): TaxesRewardConfig = TaxesRewardConfig(
        itemId = "item-holywrit",
        taxValue = 100L,
    )
}
