package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.menus.NPCQuestItemReward
import com.danidipp.sneakynpcs.menus.NPCQuestVariableOperation
import com.danidipp.sneakynpcs.menus.NPCQuestVariableReward
import com.danidipp.sneakynpcs.menus.applyQuestVariableOperation
import com.nisovin.magicspells.util.magicitems.MagicItem
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigManagerQuestRewardValidationTest {
    private val serializer = PlainTextComponentSerializer.plainText()
    private val magicItem = MagicItem(null, null)

    @Test
    fun `quest rewards parse item reward successfully`() {
        val (rewards, errors) = parseRewards(
            listOf(mapOf("item" to "money-silver", "amount" to 100)),
        )

        assertTrue(errors.isEmpty())
        val reward = assertIs<NPCQuestItemReward>(rewards.single())
        assertEquals(magicItem, reward.magicItem)
        assertEquals(100, reward.amount)
    }

    @Test
    fun `quest rewards parse variable set reward successfully`() {
        val reward = parseSingleVariableReward("set")

        assertEquals("shop_lumberjack_unlocked", reward.variableName)
        assertEquals(NPCQuestVariableOperation.SET, reward.operation)
        assertEquals(1.0, reward.amount)
    }

    @Test
    fun `quest rewards parse variable add reward successfully`() {
        val reward = parseSingleVariableReward("add")

        assertEquals(NPCQuestVariableOperation.ADD, reward.operation)
    }

    @Test
    fun `quest rewards parse variable subtract reward successfully`() {
        val reward = parseSingleVariableReward("subtract")

        assertEquals(NPCQuestVariableOperation.SUBTRACT, reward.operation)
    }

    @Test
    fun `quest rewards reject omitted reward`() {
        val (rewards, errors) = parseRewards(null)

        assertTrue(rewards.isEmpty())
        assertEquals(listOf(" - rootMenu.quests[0].reward: Missing or invalid field"), errors.map(serializer::serialize))
    }

    @Test
    fun `quest rewards reject empty reward`() {
        val (rewards, errors) = parseRewards(emptyList<Any>())

        assertTrue(rewards.isEmpty())
        assertEquals(listOf(" - rootMenu.quests[0].reward: List is empty"), errors.map(serializer::serialize))
    }

    @Test
    fun `quest rewards reject non-list reward`() {
        val (rewards, errors) = parseRewards("money-silver")

        assertTrue(rewards.isEmpty())
        assertEquals(listOf(" - rootMenu.quests[0].reward: Missing or invalid field"), errors.map(serializer::serialize))
    }

    @Test
    fun `quest rewards reject reward entry that is not an object`() {
        val (rewards, errors) = parseRewards(listOf("money-silver"))

        assertTrue(rewards.isEmpty())
        assertEquals(
            listOf(" - rootMenu.quests[0].reward[0]: Reward must be an object, got String('money-silver')"),
            errors.map(serializer::serialize),
        )
    }

    @Test
    fun `quest rewards reject entries with both item and variable`() {
        val (reward, errors) = parseReward(mapOf("item" to "money-silver", "variable" to "shop_lumberjack_unlocked", "amount" to 1))

        assertNull(reward)
        assertEquals(
            listOf(" - rootMenu.quests[0].reward[0]: Reward must define exactly one of 'item' or 'variable'"),
            errors.map(serializer::serialize),
        )
    }

    @Test
    fun `quest rewards reject entries with neither item nor variable`() {
        val (reward, errors) = parseReward(mapOf("amount" to 1))

        assertNull(reward)
        assertEquals(
            listOf(" - rootMenu.quests[0].reward[0]: Reward must define exactly one of 'item' or 'variable'"),
            errors.map(serializer::serialize),
        )
    }

    @Test
    fun `quest rewards reject unknown item reward keys`() {
        val (reward, errors) = parseReward(mapOf("item" to "money-silver", "amount" to 1, "extra" to true))

        assertNull(reward)
        assertEquals(
            listOf(" - rootMenu.quests[0].reward[0]: Unknown quest reward item keys extra"),
            errors.map(serializer::serialize),
        )
    }

    @Test
    fun `quest rewards reject missing item amount`() {
        val (reward, errors) = parseReward(mapOf("item" to "money-silver"))

        assertNull(reward)
        assertEquals(
            listOf(" - rootMenu.quests[0].reward[0].amount: Missing or invalid field"),
            errors.map(serializer::serialize),
        )
    }

    @Test
    fun `quest rewards reject item amount less than one`() {
        val (reward, errors) = parseReward(mapOf("item" to "money-silver", "amount" to 0))

        assertNull(reward)
        assertEquals(
            listOf(" - rootMenu.quests[0].reward[0].amount: Must be > 0"),
            errors.map(serializer::serialize),
        )
    }

    @Test
    fun `quest rewards reject unknown magic item`() {
        val (reward, errors) = parseReward(mapOf("item" to "missing-item", "amount" to 1))

        assertNull(reward)
        assertEquals(
            listOf(" - rootMenu.quests[0].reward[0].item: Magic item 'missing-item' not found"),
            errors.map(serializer::serialize),
        )
    }

    @Test
    fun `quest rewards reject blank variable`() {
        val (reward, errors) = parseReward(mapOf("variable" to "", "operation" to "set", "amount" to 1))

        assertNull(reward)
        assertEquals(
            listOf(" - rootMenu.quests[0].reward[0].variable: Missing or invalid field"),
            errors.map(serializer::serialize),
        )
    }

    @Test
    fun `quest rewards reject unknown magic variable`() {
        val (reward, errors) = parseReward(mapOf("variable" to "missing_variable", "operation" to "set", "amount" to 1))

        assertNull(reward)
        assertEquals(
            listOf(" - rootMenu.quests[0].reward[0].variable: Unknown MagicVariable 'missing_variable'"),
            errors.map(serializer::serialize),
        )
    }

    @Test
    fun `quest rewards reject unknown operation`() {
        val (reward, errors) = parseReward(mapOf("variable" to "shop_lumberjack_unlocked", "operation" to "multiply", "amount" to 1))

        assertNull(reward)
        assertEquals(
            listOf(" - rootMenu.quests[0].reward[0].operation: Unknown variable operation 'multiply'. Expected one of: set, add, subtract"),
            errors.map(serializer::serialize),
        )
    }

    @Test
    fun `quest rewards reject substract as normal unknown operation`() {
        val (reward, errors) = parseReward(mapOf("variable" to "shop_lumberjack_unlocked", "operation" to "substract", "amount" to 1))

        assertNull(reward)
        assertEquals(
            listOf(" - rootMenu.quests[0].reward[0].operation: Unknown variable operation 'substract'. Expected one of: set, add, subtract"),
            errors.map(serializer::serialize),
        )
    }

    @Test
    fun `quest variable operation math matches expected behavior`() {
        assertEquals(4.0, applyQuestVariableOperation(10.0, NPCQuestVariableOperation.SET, 4.0))
        assertEquals(14.0, applyQuestVariableOperation(10.0, NPCQuestVariableOperation.ADD, 4.0))
        assertEquals(6.0, applyQuestVariableOperation(10.0, NPCQuestVariableOperation.SUBTRACT, 4.0))
    }

    private fun parseSingleVariableReward(operation: String): NPCQuestVariableReward {
        val (rewards, errors) = parseRewards(
            listOf(mapOf("variable" to "shop_lumberjack_unlocked", "operation" to operation, "amount" to 1)),
        )

        assertTrue(errors.isEmpty())
        return assertIs<NPCQuestVariableReward>(rewards.single())
    }

    private fun parseRewards(rawRewards: Any?) = parseQuestMenuQuestRewardsConfig(
        rawRewards = rawRewards,
        path = "rootMenu.quests[0].reward",
        itemLookup = { itemId -> if (itemId == "money-silver") magicItem else null },
        hasVariable = { variableName -> variableName == "shop_lumberjack_unlocked" },
    )

    private fun parseReward(rewardYaml: Map<*, *>) = parseQuestMenuQuestRewardConfig(
        rewardYaml = rewardYaml,
        path = "rootMenu.quests[0].reward[0]",
        itemLookup = { itemId -> if (itemId == "money-silver") magicItem else null },
        hasVariable = { variableName -> variableName == "shop_lumberjack_unlocked" },
    )
}
