package com.danidipp.sneakynpcs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfigManagerShopStockKeyValidationTest {
    @Test
    fun `build shop stock entry id replaces item position with item id`() {
        assertEquals(
            "rootMenu/options/1/items/item-brick",
            buildShopStockEntryId("rootMenu.options[1].items[0]", "item-brick")
        )
    }

    @Test
    fun `build shop stock entry id preserves distinct shop paths for the same item id`() {
        val firstShopKey = buildShopStockEntryId("rootMenu.options[1].items[0]", "item-brick")
        val secondShopKey = buildShopStockEntryId("rootMenu.options[2].items[0]", "item-brick")

        assertEquals("rootMenu/options/1/items/item-brick", firstShopKey)
        assertEquals("rootMenu/options/2/items/item-brick", secondShopKey)
    }

    @Test
    fun `shop stock persistence rejects dotted item ids`() {
        assertEquals(
            "Magic item id may not contain '.'",
            validateShopItemIdForStockPersistence("item.brick")
        )
    }

    @Test
    fun `shop stock persistence accepts hyphenated item ids`() {
        assertNull(validateShopItemIdForStockPersistence("item-brick"))
    }
}
