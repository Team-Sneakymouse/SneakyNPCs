package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.shop.CurrencyDefinition
import com.danidipp.sneakynpcs.shop.CurrencyLookup
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigManagerTailorMenuValidationTest {
    private val serializer = PlainTextComponentSerializer.plainText()
    private val currencyLookup = FakeCurrencyLookup()

    @Test
    fun `tailor menu accepts configured buttons`() {
        val (menu, errors) = parseTailorMenuConfig(
            menuYaml = validTailorMenuYaml(),
            path = "rootMenu",
            currencyLookup = currencyLookup,
        )

        assertNotNull(menu)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `tailor menu rejects unknown keys`() {
        val (menu, errors) = parseTailorMenuConfig(
            menuYaml = validTailorMenuYaml() + mapOf("gui" to "unused"),
            path = "rootMenu",
            currencyLookup = currencyLookup,
        )

        assertNull(menu)
        assertEquals(
            listOf(" - rootMenu: Unknown tailor menu keys gui"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `tailor menu rejects unknown button currency`() {
        val (menu, errors) = parseTailorMenuConfig(
            menuYaml = validTailorMenuYaml(
                shirt = validButtonYaml() + mapOf("priceCurrency" to "voucher")
            ),
            path = "rootMenu",
            currencyLookup = currencyLookup,
        )

        assertNull(menu)
        assertEquals(
            listOf(" - rootMenu.shirt.priceCurrency: Unknown currency 'voucher'"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `tailor menu rejects string price amount`() {
        val (menu, errors) = parseTailorMenuConfig(
            menuYaml = validTailorMenuYaml(
                shirt = validButtonYaml() + mapOf("priceAmount" to "50")
            ),
            path = "rootMenu",
            currencyLookup = currencyLookup,
        )

        assertNull(menu)
        assertEquals(
            listOf(" - rootMenu.shirt.priceAmount: Missing or invalid field"),
            errors.map(serializer::serialize)
        )
    }

    private fun validTailorMenuYaml(
        shirt: Map<String, Any> = validButtonYaml(),
        pants: Map<String, Any> = validButtonYaml(),
        both: Map<String, Any> = validButtonYaml(),
    ): Map<String, Any> = mapOf(
        "type" to "tailor",
        "shirt" to shirt,
        "pants" to pants,
        "both" to both,
    )

    private fun validButtonYaml(): Map<String, Any> = mapOf(
        "title" to "<green>Pack Shirt",
        "lore" to listOf(
            "<gray>Cost: <dark_purple>50 Gold",
            "<gray>Pack up an outfit you can use or",
            "<gray>trade to swap to this outfit",
        ),
        "priceCurrency" to "gold",
        "priceAmount" to 50,
    )

    private class FakeCurrencyLookup : CurrencyLookup {
        private val currencies = mapOf(
            "gold" to CurrencyDefinition("gold", null, null, "bankGold", null, null, true)
        )

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
