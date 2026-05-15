package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.menus.questItemDisplaySlots
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigManagerQuestItemValidationTest {
    private val serializer = PlainTextComponentSerializer.plainText()

    @Test
    fun `quest item config max matches display slot count`() {
        assertEquals(questItemDisplaySlots.size, maxQuestItems)
    }

    @Test
    fun `quest items allow ten configured items`() {
        val errors = validateQuestItemCount(
            items = List(maxQuestItems) { mapOf("item" to "item-$it", "amount" to 1) },
            path = "rootMenu.quests[0].items",
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `quest items reject more than ten configured items`() {
        val errors = validateQuestItemCount(
            items = List(maxQuestItems + 1) { mapOf("item" to "item-$it", "amount" to 1) },
            path = "rootMenu.quests[0].items",
        )

        assertEquals(
            listOf(" - rootMenu.quests[0].items: May contain at most $maxQuestItems items"),
            errors.map(serializer::serialize),
        )
    }
}
