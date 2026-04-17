package com.danidipp.sneakynpcs

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigManagerShopLimitValidationTest {
    private val serializer = PlainTextComponentSerializer.plainText()

    @Test
    fun `shop item limits parse successfully`() {
        val (limits, errors) = parseShopItemLimitConfig(
            rawLimits = mapOf(
                "maxQuantity" to 5,
                "restockInterval" to 1800,
                "restockAmount" to 1,
            ),
            path = "rootMenu.items[0].limits",
            maxStackSize = 64,
        )

        assertNotNull(limits)
        assertEquals(5, limits.maxQuantity)
        assertEquals(1800L, limits.restockIntervalSeconds)
        assertEquals(1, limits.restockAmount)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `shop item limits reject maxQuantity above stack size`() {
        val (limits, errors) = parseShopItemLimitConfig(
            rawLimits = mapOf(
                "maxQuantity" to 65,
                "restockInterval" to 1800,
                "restockAmount" to 1,
            ),
            path = "rootMenu.items[0].limits",
            maxStackSize = 64,
        )

        assertNull(limits)
        assertEquals(
            listOf(" - rootMenu.items[0].limits.maxQuantity: Must be <= item max stack size 64"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `shop item limits reject negative values`() {
        val (limits, errors) = parseShopItemLimitConfig(
            rawLimits = mapOf(
                "maxQuantity" to -1,
                "restockInterval" to -10,
                "restockAmount" to -1,
            ),
            path = "rootMenu.items[0].limits",
            maxStackSize = 64,
        )

        assertNull(limits)
        assertEquals(
            listOf(
                " - rootMenu.items[0].limits.maxQuantity: Must be > 0",
                " - rootMenu.items[0].limits.restockInterval: Must be >= 0",
                " - rootMenu.items[0].limits.restockAmount: Must be >= 0",
            ),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `shop item limits require positive interval when restock amount is positive`() {
        val (limits, errors) = parseShopItemLimitConfig(
            rawLimits = mapOf(
                "maxQuantity" to 5,
                "restockInterval" to 0,
                "restockAmount" to 1,
            ),
            path = "rootMenu.items[0].limits",
            maxStackSize = 64,
        )

        assertNull(limits)
        assertEquals(
            listOf(" - rootMenu.items[0].limits.restockInterval: Must be > 0 when 'restockAmount' is > 0"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `shop item limits reject unknown keys`() {
        val (limits, errors) = parseShopItemLimitConfig(
            rawLimits = mapOf(
                "maxQuantity" to 5,
                "restockInterval" to 1800,
                "restockAmount" to 1,
                "mystery" to true,
            ),
            path = "rootMenu.items[0].limits",
            maxStackSize = 64,
        )

        assertNull(limits)
        assertEquals(
            listOf(" - rootMenu.items[0].limits: Unknown limits keys mystery"),
            errors.map(serializer::serialize)
        )
    }
}
