package com.danidipp.sneakynpcs.menus

import kotlin.test.Test
import kotlin.test.assertEquals

class ShopMenuBankDisplayTest {
    @Test
    fun `bank display amounts use comma thousands separators`() {
        assertEquals("0", formatBankDisplayAmount(0L))
        assertEquals("999", formatBankDisplayAmount(999L))
        assertEquals("1,000", formatBankDisplayAmount(1_000L))
        assertEquals("1,234,567", formatBankDisplayAmount(1_234_567L))
    }
}
