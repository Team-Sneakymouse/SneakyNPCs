package com.danidipp.sneakynpcs

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ShopOfferAvailabilityTest {
    @Test
    fun `shop offer availability filters visible offers for the shop date`() {
        val result = evaluateShopOfferAvailability(
            offers = listOf(
                ConfiguredShopOfferAvailability(
                    offerId = "jacktimbers:rootMenu/options/1:item-always",
                    itemId = "item-always",
                    availability = null,
                ),
                ConfiguredShopOfferAvailability(
                    offerId = "jacktimbers:rootMenu/options/1:item-disabled",
                    itemId = "item-disabled",
                    availability = ShopOfferAvailabilityConfig(),
                ),
                ConfiguredShopOfferAvailability(
                    offerId = "jacktimbers:rootMenu/options/1:item-monday",
                    itemId = "item-monday",
                    availability = ShopOfferAvailabilityConfig(weekdays = setOf(ShopWeekday.MONDAY)),
                ),
                ConfiguredShopOfferAvailability(
                    offerId = "jacktimbers:rootMenu/options/1:item-friday",
                    itemId = "item-friday",
                    availability = ShopOfferAvailabilityConfig(weekdays = setOf(ShopWeekday.FRIDAY)),
                ),
            ),
            shopDate = LocalDate.of(2026, 6, 29),
            seedContext = ShopOfferAvailabilitySeedContext(
                npcId = "jacktimbers",
                shopMenuPath = "rootMenu/options/1",
            ),
        )

        assertEquals(
            listOf(
                "jacktimbers:rootMenu/options/1:item-always",
                "jacktimbers:rootMenu/options/1:item-monday",
            ),
            result.visibleOfferIds,
        )
        assertEquals(
            listOf(
                "jacktimbers:rootMenu/options/1:item-disabled",
                "jacktimbers:rootMenu/options/1:item-friday",
            ),
            result.hiddenOfferIds,
        )
    }

    @Test
    fun `shop offer availability matches calendar day conditions with OR semantics`() {
        val result = evaluateShopOfferAvailability(
            offers = listOf(
                ConfiguredShopOfferAvailability(
                    offerId = "jacktimbers:rootMenu/options/1:item-fifth",
                    itemId = "item-tenth-of-month",
                    availability = ShopOfferAvailabilityConfig(daysOfMonth = setOf(10)),
                ),
                ConfiguredShopOfferAvailability(
                    offerId = "jacktimbers:rootMenu/options/1:item-tenth-day",
                    itemId = "item-tenth-day",
                    availability = ShopOfferAvailabilityConfig(everyNthDayOfYear = 10),
                ),
                ConfiguredShopOfferAvailability(
                    offerId = "jacktimbers:rootMenu/options/1:item-no-match",
                    itemId = "item-no-match",
                    availability = ShopOfferAvailabilityConfig(
                        daysOfMonth = setOf(6),
                        everyNthDayOfYear = 11,
                    ),
                ),
            ),
            shopDate = LocalDate.of(2026, 1, 10),
            seedContext = ShopOfferAvailabilitySeedContext(
                npcId = "jacktimbers",
                shopMenuPath = "rootMenu/options/1",
            ),
        )

        assertEquals(
            listOf(
                "jacktimbers:rootMenu/options/1:item-fifth",
                "jacktimbers:rootMenu/options/1:item-tenth-day",
            ),
            result.visibleOfferIds,
        )
        assertEquals(
            listOf("jacktimbers:rootMenu/options/1:item-no-match"),
            result.hiddenOfferIds,
        )
    }

    @Test
    fun `shop offer availability evaluates deterministic random conditions for the shop date`() {
        val offers = listOf(
            ConfiguredShopOfferAvailability(
                offerId = "jacktimbers:rootMenu/options/1:item-random-a",
                itemId = "item-random-a",
                availability = ShopOfferAvailabilityConfig(randomGroup = "special"),
            ),
            ConfiguredShopOfferAvailability(
                offerId = "jacktimbers:rootMenu/options/1:item-random-b",
                itemId = "item-random-b",
                availability = ShopOfferAvailabilityConfig(randomGroup = "special"),
            ),
            ConfiguredShopOfferAvailability(
                offerId = "jacktimbers:rootMenu/options/1:item-never",
                itemId = "item-never",
                availability = ShopOfferAvailabilityConfig(chance = 0.0),
            ),
            ConfiguredShopOfferAvailability(
                offerId = "jacktimbers:rootMenu/options/1:item-always",
                itemId = "item-always",
                availability = ShopOfferAvailabilityConfig(chance = 1.0),
            ),
        )
        val seedContext = ShopOfferAvailabilitySeedContext(
            npcId = "jacktimbers",
            shopMenuPath = "rootMenu/options/1",
        )

        val first = evaluateShopOfferAvailability(
            offers = offers,
            shopDate = LocalDate.of(2026, 6, 30),
            seedContext = seedContext,
        )
        val second = evaluateShopOfferAvailability(
            offers = offers,
            shopDate = LocalDate.of(2026, 6, 30),
            seedContext = seedContext,
        )

        assertEquals(first, second)
        assertEquals(2, first.visibleOfferIds.size)
        assertEquals(2, first.hiddenOfferIds.size)
        assertEquals(
            1,
            first.visibleOfferIds.count {
                it == "jacktimbers:rootMenu/options/1:item-random-a" ||
                    it == "jacktimbers:rootMenu/options/1:item-random-b"
            },
        )
        assertEquals(
            listOf(
                ShopOfferRandomGroupSelection(
                    groupId = "jacktimbers:rootMenu/options/1:special",
                    groupName = "special",
                    selectedOfferId = first.visibleOfferIds.first {
                        it == "jacktimbers:rootMenu/options/1:item-random-a" ||
                            it == "jacktimbers:rootMenu/options/1:item-random-b"
                    },
                    selectedItemId = first.visibleOfferIds.first {
                        it == "jacktimbers:rootMenu/options/1:item-random-a" ||
                            it == "jacktimbers:rootMenu/options/1:item-random-b"
                    }.substringAfterLast(":"),
                ),
            ),
            first.randomGroupSelections,
        )
    }
}
