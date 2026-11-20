package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.shop.ShopPrice
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigManagerShopPriceValidationTest {
    private val serializer = PlainTextComponentSerializer.plainText()

    @Test
    fun `shop stored price amount allows zero`() {
        val (amount, errors) = parseShopStoredPriceAmount(
            amountRaw = "0",
            path = "rootMenu.items[0]",
            itemId = "item-free-sample",
            pdcKeyName = "store_value_amount",
        )

        assertEquals(0, amount)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `shop stored price amount rejects negative values`() {
        val (amount, errors) = parseShopStoredPriceAmount(
            amountRaw = "-1",
            path = "rootMenu.items[0]",
            itemId = "item-free-sample",
            pdcKeyName = "store_value_amount",
        )

        assertNull(amount)
        assertEquals(
            listOf(" - rootMenu.items[0].price.amount: Must be >= 0 (got -1)"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `configured shop price is used as given`() {
        val (price, errors) = parseShopConfiguredPrice(
            rawPrice = mapOf("currency" to "gold", "amount" to 555),
            path = "rootMenu.items[0].price",
            hasCurrency = { it == "gold" },
        )

        assertEquals(ShopPrice(currencyId = "gold", amount = 555), price)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `configured shop price rejects unknown currency, bad amount and extra keys`() {
        val (price, errors) = parseShopConfiguredPrice(
            rawPrice = mapOf("currency" to "doubloons", "amount" to "555", "cost" to 1),
            path = "rootMenu.items[0].price",
            hasCurrency = { it == "gold" },
        )

        assertNull(price)
        assertEquals(
            listOf(
                " - rootMenu.items[0].price: Unknown price keys cost",
                " - rootMenu.items[0].price.currency: Unknown currency 'doubloons'",
                " - rootMenu.items[0].price.amount: Missing or invalid field (expected a whole number)",
            ),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `configured shop price rejects negative amounts and non-objects`() {
        val (negative, negativeErrors) = parseShopConfiguredPrice(
            rawPrice = mapOf("currency" to "gold", "amount" to -1),
            path = "p",
            hasCurrency = { true },
        )
        assertNull(negative)
        assertEquals(listOf(" - p.amount: Must be >= 0 (got -1)"), negativeErrors.map(serializer::serialize))

        val (scalar, scalarErrors) = parseShopConfiguredPrice(rawPrice = 555, path = "p", hasCurrency = { true })
        assertNull(scalar)
        assertEquals(listOf(" - p: Must be an object with currency and amount"), scalarErrors.map(serializer::serialize))
    }

    @Test
    fun `shop stored price amount still rejects malformed values`() {
        val (amount, errors) = parseShopStoredPriceAmount(
            amountRaw = "free",
            path = "rootMenu.items[0]",
            itemId = "item-free-sample",
            pdcKeyName = "store_value_amount",
        )

        assertNull(amount)
        assertEquals(
            listOf(" - rootMenu.items[0].price.amount: Invalid price amount 'free' on 'item-free-sample' (expected integer string)"),
            errors.map(serializer::serialize)
        )
    }
}
