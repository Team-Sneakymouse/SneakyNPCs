package com.danidipp.sneakynpcs.bank

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BankRulesTest {
    @Test
    fun `deposits pay off a loan before reaching the balance`() {
        assertEquals(DepositSplit(repaid = 0, credited = 99), BankRules.depositSplit(99, 0))
        assertEquals(DepositSplit(repaid = 40, credited = 59), BankRules.depositSplit(99, 40))
        assertEquals(DepositSplit(repaid = 99, credited = 0), BankRules.depositSplit(99, 100_000))
    }

    @Test
    fun `balance limit uses the variable maximum`() {
        assertTrue(BankRules.fits(900, 100, 1_000.0))
        assertFalse(BankRules.fits(901, 100, 1_000.0))
        assertTrue(BankRules.fits(Long.MAX_VALUE / 2, 1, null))
        assertEquals(100, BankRules.room(900, 1_000.0))
        assertEquals(0, BankRules.room(1_200, 1_000.0))
        assertEquals(Long.MAX_VALUE, BankRules.room(5, null))
    }

    @Test
    fun `exchange quantity cycles through the old steps and wraps`() {
        assertEquals(32, BankRules.cycleQuantity(1, 1))
        assertEquals(100_000, BankRules.cycleQuantity(10_000, 1))
        assertEquals(1, BankRules.cycleQuantity(100_000, 1))
        assertEquals(100_000, BankRules.cycleQuantity(1, -1))
        assertEquals(1, BankRules.cycleQuantity(7, 1))
    }

    @Test
    fun `exchanging as much as possible respects balance and room`() {
        assertEquals(20, BankRules.maxExchange(sourceBalance = 1_049, rate = 50, targetRoom = 1_000))
        assertEquals(3, BankRules.maxExchange(sourceBalance = 1_049, rate = 50, targetRoom = 3))
        assertEquals(0, BankRules.maxExchange(sourceBalance = 49, rate = 50, targetRoom = 1_000))
        assertEquals(0, BankRules.maxExchange(sourceBalance = 1_000, rate = 0, targetRoom = 1_000))
    }

    @Test
    fun `note withdrawals are limited by request, balance and space`() {
        assertEquals(1, BankRules.noteWithdrawCount(requested = 1, balance = 5_000, value = 1_000, space = 64))
        assertEquals(5, BankRules.noteWithdrawCount(requested = 99, balance = 5_999, value = 1_000, space = 64))
        assertEquals(2, BankRules.noteWithdrawCount(requested = 99, balance = 5_999, value = 1_000, space = 2))
        assertEquals(0, BankRules.noteWithdrawCount(requested = 99, balance = 999, value = 1_000, space = 64))
    }

    @Test
    fun `note item ids map back to their coin and value`() {
        val (coin, note) = BankRules.noteByItemId("item-silverNote100k")!!
        assertEquals("silver", coin.currencyId)
        assertEquals(100_000, note.value)
        assertEquals(1_000_000, BankRules.noteByItemId("item-goldNote1m")!!.second.value)
        assertNull(BankRules.noteByItemId("item-curlyCheck"))
    }
}
