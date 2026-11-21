package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.bank.BankFeature
import com.danidipp.sneakynpcs.bank.BankGate
import com.danidipp.sneakynpcs.bank.BankMenuConfig
import com.danidipp.sneakynpcs.bank.BankRegion
import com.danidipp.sneakynpcs.shop.CurrencyDefinition
import com.danidipp.sneakynpcs.shop.CurrencyLookup
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigManagerBankMenuValidationTest {
    private val serializer = PlainTextComponentSerializer.plainText()

    @Test
    fun `bank menu defaults gate every feature and deposit notes in the bank region`() {
        val (config, errors) = parse(mapOf("type" to "bank"))

        assertTrue(errors.isEmpty(), errors.joinToString())
        assertEquals(BankMenuConfig(), config)
        assertEquals(BankGate("skillTradingTier6A", "Bank Voucher"), config?.gates?.get(BankFeature.NOTES))
        assertEquals(BankRegion("world", "bank"), config?.noteDepositRegion)
    }

    @Test
    fun `gates can be renamed or turned off and the region changed`() {
        val (config, errors) = parse(mapOf(
            "type" to "bank",
            "gates" to mapOf("notes" to false, "loans" to "customLoanFlag"),
            "noteDepositRegion" to "world:royal_bank",
        ))

        assertTrue(errors.isEmpty(), errors.joinToString())
        assertNull(config?.gates?.get(BankFeature.NOTES))
        assertEquals(BankGate("customLoanFlag", null), config?.gates?.get(BankFeature.LOANS))
        assertEquals(BankFeature.EXCHANGES.defaultGate, config?.gates?.get(BankFeature.EXCHANGES))
        assertEquals(BankRegion("world", "royal_bank"), config?.noteDepositRegion)
    }

    @Test
    fun `region deposits can be turned off`() {
        val (config, errors) = parse(mapOf("type" to "bank", "noteDepositRegion" to false))

        assertTrue(errors.isEmpty(), errors.joinToString())
        assertNull(config?.noteDepositRegion)
    }

    @Test
    fun `bank menu rejects bad keys, gates and regions`() {
        val (config, errors) = parse(mapOf(
            "type" to "bank",
            "gui" to "main",
            "gates" to mapOf("withdraw" to "x", "loans" to 3),
            "noteDepositRegion" to "bank",
        ))

        assertNull(config)
        assertEquals(
            listOf(
                " - rootMenu: Unknown bank menu keys gui",
                " - rootMenu.gates: Unknown bank gate 'withdraw'. Expected one of notes, loans, exchanges",
                " - rootMenu.gates.loans: Invalid field. Expected a variable name or false, got Int(3)",
                " - rootMenu.noteDepositRegion: Invalid field. Expected '<world>:<region>' or false, got String('bank')",
            ),
            errors.map(serializer::serialize),
        )
    }

    @Test
    fun `bank menu reports missing variables, items and currencies`() {
        val (config, errors) = parseBankMenuSettings(
            menuYaml = mapOf("type" to "bank", "gates" to mapOf("notes" to "missingFlag")),
            path = "rootMenu",
            currencyLookup = FakeCurrencyLookup(withoutPennyItem = true),
            hasVariable = { it != "missingFlag" && it != "bankLoanGold" },
            hasItem = { it != "item-holyWrit" && it != "item-goldNote1m" },
        )
        val lines = errors.map(serializer::serialize)

        assertNull(config)
        assertEquals(
            listOf(
                " - rootMenu.gates.notes: Required variable 'missingFlag' is not configured",
                " - rootMenu: Currency 'penny' must define both an item and a bank variable",
                " - rootMenu: Required variable 'bankLoanGold' is not configured",
                " - rootMenu: Required MagicItem 'item-goldNote1m' is not configured",
                " - rootMenu: Required MagicItem 'item-holyWrit' is not configured",
            ),
            lines,
        )
    }

    private fun parse(menuYaml: Map<String, Any>) = parseBankMenuSettings(
        menuYaml = menuYaml,
        path = "rootMenu",
        currencyLookup = FakeCurrencyLookup(),
        hasVariable = { true },
        hasItem = { true },
    )

    private class FakeCurrencyLookup(withoutPennyItem: Boolean = false) : CurrencyLookup {
        private val currencies = listOf(
            CurrencyDefinition("penny", if (withoutPennyItem) null else "money-penny", null, "bankPenny", "silver", 50, true),
            CurrencyDefinition("silver", "money-silver", null, "bankSilver", "gold", 50, true),
            CurrencyDefinition("gold", "money-gold", null, "bankGold", "diamond", 50, true),
        ).associateBy { it.id }

        override fun getCurrency(currencyId: String): CurrencyDefinition? = currencies[currencyId]
        override fun hasCurrency(currencyId: String): Boolean = currencies.containsKey(currencyId)
        override fun getAtomicValue(currencyId: String): BigInteger? = null
        override fun getConvertibleCurrencies(currencyId: String): Set<String> = emptySet()
        override fun getWalletCurrencies(nativeCurrencyId: String): List<CurrencyDefinition> = emptyList()
        override fun isConvertible(fromCurrencyId: String, toCurrencyId: String): Boolean = false
    }
}
