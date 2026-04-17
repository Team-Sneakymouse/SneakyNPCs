package com.danidipp.sneakynpcs.menus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TailorMenuCommandTest {
    @Test
    fun `tailor command maps shirt slot`() {
        assertEquals("sneakymannequins:mannequin item shirt", buildTailorCommand(14))
    }

    @Test
    fun `tailor command maps pants slot`() {
        assertEquals("sneakymannequins:mannequin item pants", buildTailorCommand(15))
    }

    @Test
    fun `tailor command maps shirt and pants slot`() {
        assertEquals("sneakymannequins:mannequin item shirt pants", buildTailorCommand(16))
    }

    @Test
    fun `tailor command returns null for unsupported slots`() {
        listOf(0, 13, 17, 53).forEach { slot ->
            assertNull(buildTailorCommand(slot))
        }
    }

    @Test
    fun `tailor permissions match mannequin item gates`() {
        assertEquals(
            setOf(
                "sneakymannequins.command.mannequin",
                "sneakymannequins.command.item",
            ),
            requiredTailorPermissions()
        )
    }
}
