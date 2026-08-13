package com.danidipp.sneakynpcs.menus

import kotlin.test.Test
import kotlin.test.assertEquals

class ShopMenuPaginationTest {
    @Test
    fun `visible offers compact into pages without hidden gaps`() {
        val visibleOffers = (1..30).filterNot { it in setOf(2, 4, 6) }

        val page = shopOfferPage(visibleOffers, requestedPage = 0, pageSize = 24)

        assertEquals(0, page.index)
        assertEquals(24, page.offers.size)
        assertEquals(27, page.offers.last())
    }

    @Test
    fun `requested page clamps when visible offer count shrinks`() {
        val page = shopOfferPage((1..20).toList(), requestedPage = 1, pageSize = 24)

        assertEquals(0, page.index)
        assertEquals((1..20).toList(), page.offers)
    }
}
