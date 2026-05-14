package com.danidipp.sneakynpcs

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
