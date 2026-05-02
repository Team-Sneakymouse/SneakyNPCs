package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.shop.CurrencyDefinition
import com.danidipp.sneakynpcs.shop.CurrencyLookup
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigManagerTaxesMenuValidationTest {
    private val serializer = PlainTextComponentSerializer.plainText()

    @Test
    fun `taxes menu accepts required runtime dependencies`() {
        val errors = validateTaxesMenuConfig(
            menuYaml = validTaxesMenuYaml(),
            path = "rootMenu",
            currencyLookup = FakeCurrencyLookup(),
            hasVariable = { it == "bankTax" },
            hasRewardItem = { it == "item-holywrit" },
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `taxes menu accepts configured currencies and reward`() {
        val (config, errors) = parseTaxesMenuSettings(
            menuYaml = validTaxesMenuYaml(
                slot14 = mapOf("currency" to "copper", "taxValue" to 5),
                slot16 = mapOf("currency" to "taxnote", "taxValue" to 250),
                reward = mapOf("item" to "item-taxreceipt", "taxValue" to 250),
            ),
            path = "rootMenu",
            currencyLookup = FakeCurrencyLookup(extraCurrencies = setOf("copper", "taxnote")),
            hasVariable = { it == "bankTax" },
            hasRewardItem = { it == "item-taxreceipt" },
        )

        assertTrue(errors.isEmpty())
        assertEquals("copper", config?.payments?.get(14)?.currencyId)
        assertEquals(5L, config?.payments?.get(14)?.taxValuePerUnit)
        assertEquals("taxnote", config?.payments?.get(16)?.currencyId)
        assertEquals(250L, config?.payments?.get(16)?.taxValuePerUnit)
        assertEquals("item-taxreceipt", config?.reward?.itemId)
        assertEquals(250L, config?.reward?.taxValue)
    }

    @Test
    fun `taxes menu rejects unknown keys`() {
        val errors = validateTaxesMenuConfig(
            menuYaml = validTaxesMenuYaml() + mapOf("gui" to "unused"),
            path = "rootMenu",
            currencyLookup = FakeCurrencyLookup(),
            hasVariable = { it == "bankTax" },
            hasRewardItem = { it == "item-holywrit" },
        )

        assertEquals(
            listOf(" - rootMenu: Unknown taxes menu keys gui"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `taxes menu rejects missing gold`() {
        val errors = validateTaxesMenuConfig(
            menuYaml = validTaxesMenuYaml(),
            path = "rootMenu",
            currencyLookup = FakeCurrencyLookup(includeGold = false),
            hasVariable = { it == "bankTax" },
            hasRewardItem = { it == "item-holywrit" },
        )

        assertEquals(
            listOf(" - rootMenu.payments.slot14.currency: Required currency 'gold' is not configured"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `taxes menu rejects missing goldbar`() {
        val errors = validateTaxesMenuConfig(
            menuYaml = validTaxesMenuYaml(),
            path = "rootMenu",
            currencyLookup = FakeCurrencyLookup(includeGoldbar = false),
            hasVariable = { it == "bankTax" },
            hasRewardItem = { it == "item-holywrit" },
        )

        assertEquals(
            listOf(" - rootMenu.payments.slot16.currency: Required currency 'goldbar' is not configured"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `taxes menu rejects missing bank tax variable`() {
        val errors = validateTaxesMenuConfig(
            menuYaml = validTaxesMenuYaml(),
            path = "rootMenu",
            currencyLookup = FakeCurrencyLookup(),
            hasVariable = { false },
            hasRewardItem = { it == "item-holywrit" },
        )

        assertEquals(
            listOf(" - rootMenu.bankTax: Required MagicSpells variable 'bankTax' is not configured"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `taxes menu rejects missing holy writ`() {
        val errors = validateTaxesMenuConfig(
            menuYaml = validTaxesMenuYaml(),
            path = "rootMenu",
            currencyLookup = FakeCurrencyLookup(),
            hasVariable = { it == "bankTax" },
            hasRewardItem = { false },
        )

        assertEquals(
            listOf(" - rootMenu.reward.item: Required MagicItem 'item-holywrit' is not configured"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `taxes menu rejects reward tax value that does not divide into currency tax value`() {
        val errors = validateTaxesMenuConfig(
            menuYaml = validTaxesMenuYaml(
                slot14 = mapOf("currency" to "gold", "taxValue" to 30),
                reward = mapOf("item" to "item-holywrit", "taxValue" to 100),
            ),
            path = "rootMenu",
            currencyLookup = FakeCurrencyLookup(),
            hasVariable = { it == "bankTax" },
            hasRewardItem = { it == "item-holywrit" },
        )

        assertEquals(
            listOf(" - rootMenu.payments.slot14.taxValue: Reward tax value 100 must be divisible by currency tax value 30"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `taxes menu rejects missing required sections`() {
        val errors = validateTaxesMenuConfig(
            menuYaml = mapOf("type" to "taxes"),
            path = "rootMenu",
            currencyLookup = FakeCurrencyLookup(),
            hasVariable = { it == "bankTax" },
            hasRewardItem = { it == "item-holywrit" },
        )

        assertEquals(
            listOf(
                " - rootMenu.payments: Missing or invalid section",
                " - rootMenu.reward: Missing or invalid section",
            ),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `taxes menu rejects missing required payment fields`() {
        val errors = validateTaxesMenuConfig(
            menuYaml = validTaxesMenuYaml(slot14 = emptyMap()),
            path = "rootMenu",
            currencyLookup = FakeCurrencyLookup(),
            hasVariable = { it == "bankTax" },
            hasRewardItem = { it == "item-holywrit" },
        )

        assertEquals(
            listOf(
                " - rootMenu.payments.slot14.currency: Missing or invalid field",
                " - rootMenu.payments.slot14.taxValue: Missing or invalid field",
            ),
            errors.map(serializer::serialize)
        )
    }

    private fun validTaxesMenuYaml(
        slot14: Map<String, Any> = mapOf("currency" to "gold", "taxValue" to 1),
        slot16: Map<String, Any> = mapOf("currency" to "goldbar", "taxValue" to 100),
        reward: Map<String, Any> = mapOf("item" to "item-holywrit", "taxValue" to 100),
    ): Map<String, Any> = mapOf(
        "type" to "taxes",
        "payments" to mapOf(
            "slot14" to slot14,
            "slot16" to slot16,
        ),
        "reward" to reward,
    )

    private class FakeCurrencyLookup(
        includeGold: Boolean = true,
        includeGoldbar: Boolean = true,
        extraCurrencies: Set<String> = emptySet(),
    ) : CurrencyLookup {
        private val currencies = buildMap {
            if (includeGold) {
                put("gold", CurrencyDefinition("gold", null, null, "bankGold", null, null, true))
            }
            if (includeGoldbar) {
                put("goldbar", CurrencyDefinition("goldbar", null, null, "bankGoldbar", null, null, true))
            }
            for (currencyId in extraCurrencies) {
                put(currencyId, CurrencyDefinition(currencyId, null, null, "bank_$currencyId", null, null, true))
            }
        }

        override fun getCurrency(currencyId: String): CurrencyDefinition? = currencies[currencyId]

        override fun hasCurrency(currencyId: String): Boolean = currencies.containsKey(currencyId)

        override fun getAtomicValue(currencyId: String): BigInteger? = BigInteger.ONE

        override fun getConvertibleCurrencies(currencyId: String): Set<String> = currencies.keys

        override fun getWalletCurrencies(nativeCurrencyId: String): List<CurrencyDefinition> = emptyList()

        override fun isConvertible(fromCurrencyId: String, toCurrencyId: String): Boolean {
            return currencies.containsKey(fromCurrencyId) && currencies.containsKey(toCurrencyId)
        }
    }
}
