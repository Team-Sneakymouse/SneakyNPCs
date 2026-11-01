package com.danidipp.sneakynpcs.screens2

import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Npc2LayoutTest {
    @Test
    fun `widths follow the vanilla font`() {
        assertEquals(6 + 2 + 3, Npc2Text.width("Hil"))
        assertEquals(4, Npc2Text.width(" "))
    }

    @Test
    fun `wrap keeps lines inside the width and marks cut text`() {
        val lines = Npc2Text.wrap("the quick brown fox jumps over the lazy dog again and again", 60, 2)
        assertEquals(2, lines.size)
        assertTrue(lines.all { Npc2Text.width(it) <= 60 })
        assertTrue(lines.last().endsWith("..."))
    }

    @Test
    fun `spaces add up to the distance`() {
        for (px in listOf(-300, -189, -1, 0, 1, 37, 255)) {
            val total = Npc2Text.spaces(px).sumOf { c -> if (c.code >= 0xE120) -(1 shl (c.code - 0xE120)) else 1 shl (c.code - 0xE100) }
            assertEquals(px, total)
        }
    }

    @Test
    fun `config falls back and warns`() {
        val yaml = YamlConfiguration().apply {
            set("district", "atlantis")
            set("options", listOf("shop", "dance"))
        }
        val config = Npc2Config.parse("Carl", yaml)
        assertEquals("Carl", config.npcId)
        assertEquals(Npc2District.GOATTOWN, config.district)
        assertEquals(listOf(Npc2Option.SHOP), config.options)
        assertEquals(2, config.warnings.size)
    }

    @Test
    fun `mood follows the weather and the clock`() {
        assertEquals(Npc2Mood.GLOOMY, Npc2Mood.of(1000, true))
        assertEquals(Npc2Mood.SLEEPY, Npc2Mood.of(18000, false))
        assertEquals(Npc2Mood.CHEERFUL, Npc2Mood.of(6000, false))
    }
}
