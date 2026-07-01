package com.danidipp.sneakynpcs

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigManagerShopAvailabilityValidationTest {
    private val serializer = PlainTextComponentSerializer.plainText()

    @Test
    fun `shop offer availability parses supported conditions`() {
        val (availability, errors) = parseShopOfferAvailabilityConfig(
            rawAvailability = mapOf(
                "weekdays" to listOf("monday", "FRIDAY"),
                "daysOfMonth" to listOf(5, "31"),
                "everyNthDayOfYear" to "10",
                "randomGroup" to "special",
                "chance" to "0.01",
            ),
            path = "rootMenu.items[0].availability",
        )

        assertNotNull(availability)
        assertEquals(setOf(ShopWeekday.MONDAY, ShopWeekday.FRIDAY), availability.weekdays)
        assertEquals(setOf(5, 31), availability.daysOfMonth)
        assertEquals(10, availability.everyNthDayOfYear)
        assertEquals("special", availability.randomGroup)
        assertEquals(0.01, availability.chance)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `missing shop offer availability means always available and empty means never available`() {
        val (missingAvailability, missingErrors) = parseShopOfferAvailabilityConfig(
            rawAvailability = null,
            path = "rootMenu.items[0].availability",
        )
        val (emptyAvailability, emptyErrors) = parseShopOfferAvailabilityConfig(
            rawAvailability = emptyMap<String, Any>(),
            path = "rootMenu.items[1].availability",
        )

        assertNull(missingAvailability)
        assertTrue(missingErrors.isEmpty())
        assertNotNull(emptyAvailability)
        assertTrue(emptyErrors.isEmpty())
    }

    @Test
    fun `shop offer availability rejects malformed conditions`() {
        val (availability, errors) = parseShopOfferAvailabilityConfig(
            rawAvailability = mapOf(
                "weekDays" to listOf("monday"),
                "weekdays" to listOf("monday", "MONDAY", "mon"),
                "daysOfMonth" to listOf(0, 32, "5", "5"),
                "everyNthDayOfYear" to 0,
                "randomGroup" to " ",
                "chance" to 1.5,
            ),
            path = "rootMenu.items[0].availability",
        )

        assertNull(availability)
        assertEquals(
            listOf(
                " - rootMenu.items[0].availability: Unknown availability keys weekDays",
                " - rootMenu.items[0].availability.weekdays[1]: Duplicate weekday 'MONDAY'",
                " - rootMenu.items[0].availability.weekdays[2]: Invalid weekday 'mon'",
                " - rootMenu.items[0].availability.daysOfMonth[0]: Must be between 1 and 31",
                " - rootMenu.items[0].availability.daysOfMonth[1]: Must be between 1 and 31",
                " - rootMenu.items[0].availability.daysOfMonth[3]: Duplicate day '5'",
                " - rootMenu.items[0].availability.everyNthDayOfYear: Must be >= 1",
                " - rootMenu.items[0].availability.randomGroup: Cannot be blank",
                " - rootMenu.items[0].availability.chance: Must be between 0.0 and 1.0",
            ),
            errors.map(serializer::serialize),
        )
    }

    @Test
    fun `shop offer availability rejects scalar shorthand for list conditions`() {
        val (availability, errors) = parseShopOfferAvailabilityConfig(
            rawAvailability = mapOf(
                "weekdays" to "monday",
                "daysOfMonth" to 5,
            ),
            path = "rootMenu.items[0].availability",
        )

        assertNull(availability)
        assertEquals(
            listOf(
                " - rootMenu.items[0].availability.weekdays: Must be a list",
                " - rootMenu.items[0].availability.daysOfMonth: Must be a list",
            ),
            errors.map(serializer::serialize),
        )
    }
}
