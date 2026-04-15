package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.shop.CurrencyDefinition
import com.danidipp.sneakynpcs.shop.CurrencyLookup
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigManagerBankDisplayValidationTest {
    private val serializer = PlainTextComponentSerializer.plainText()
    private val lookup = FakeCurrencyLookup()

    @Test
    fun `bankDisplay preserves configured order exactly`() {
        val (bankDisplay, errors) = parseBankDisplayConfig(
            rawBankDisplay = listOf("silver", "gold", "silver"),
            path = "rootMenu",
            currencyLookup = lookup,
        )

        assertEquals(listOf("silver", "gold", "silver"), bankDisplay)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `bankDisplay rejects currencies without bank variables`() {
        val (bankDisplay, errors) = parseBankDisplayConfig(
            rawBankDisplay = listOf("voucher"),
            path = "rootMenu",
            currencyLookup = lookup,
        )

        assertTrue(bankDisplay.isEmpty())
        assertEquals(
            listOf(" - rootMenu.bankDisplay[0]: Currency 'voucher' does not define a bank variable"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `bankDisplay rejects non list values`() {
        val (bankDisplay, errors) = parseBankDisplayConfig(
            rawBankDisplay = "silver",
            path = "rootMenu",
            currencyLookup = lookup,
        )

        assertTrue(bankDisplay.isEmpty())
        assertEquals(
            listOf(" - rootMenu.bankDisplay: Invalid field. Expected list, got String('silver')"),
            errors.map(serializer::serialize)
        )
    }

    private class FakeCurrencyLookup : CurrencyLookup {
        private val currencies = mapOf(
            "gold" to CurrencyDefinition("gold", null, null, "bankGold", null, null, true),
            "silver" to CurrencyDefinition("silver", null, null, "bankSilver", "gold", 50, true),
            "voucher" to CurrencyDefinition("voucher", null, null, null, null, null, true),
        )

        override fun getCurrency(currencyId: String): CurrencyDefinition? = currencies[currencyId]

        override fun hasCurrency(currencyId: String): Boolean = currencies.containsKey(currencyId)

        override fun getAtomicValue(currencyId: String): BigInteger? = null

        override fun getConvertibleCurrencies(currencyId: String): Set<String> = emptySet()

        override fun getWalletCurrencies(nativeCurrencyId: String): List<CurrencyDefinition> = emptyList()

        override fun isConvertible(fromCurrencyId: String, toCurrencyId: String): Boolean = false
    }
}
