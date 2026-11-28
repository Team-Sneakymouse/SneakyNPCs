package com.danidipp.sneakynpcs.shop

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CreditCardPaymentsTest {
    /** penny → silver ×50 → gold ×50 (gold = 2500 pennies), plus streetcred outside the gold family. */
    private val lookup = object : CurrencyLookup {
        private val atomic = mapOf("penny" to 1L, "silver" to 50L, "gold" to 2500L, "streetcred" to 1L)
        private val gold = setOf("penny", "silver", "gold")
        override fun getCurrency(currencyId: String): CurrencyDefinition? = null
        override fun hasCurrency(currencyId: String) = currencyId in atomic
        override fun getAtomicValue(currencyId: String) = atomic[currencyId]?.let(BigInteger::valueOf)
        override fun getConvertibleCurrencies(currencyId: String) = if (currencyId in gold) gold else setOf(currencyId)
        override fun getWalletCurrencies(nativeCurrencyId: String): List<CurrencyDefinition> = emptyList()
        override fun isConvertible(fromCurrencyId: String, toCurrencyId: String) =
            toCurrencyId in getConvertibleCurrencies(fromCurrencyId)
    }

    private fun gold(currency: String, atomic: Long) =
        CreditCardPayments.goldFor(lookup, currency, BigInteger.valueOf(atomic))

    @Test
    fun `whole gold prices charge exactly`() {
        assertEquals(40L, gold("gold", 40 * 2500L))
    }

    @Test
    fun `prices below whole gold round up`() {
        assertEquals(1L, gold("penny", 1))
        assertEquals(1L, gold("silver", 50 * 50L))
        assertEquals(2L, gold("silver", 51 * 50L))
    }

    @Test
    fun `currencies outside the gold family never go on a card`() {
        assertNull(gold("streetcred", 10))
        assertNull(gold("gold", 0))
    }
}
