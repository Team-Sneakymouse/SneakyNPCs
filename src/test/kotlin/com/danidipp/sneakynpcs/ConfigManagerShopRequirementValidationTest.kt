package com.danidipp.sneakynpcs

import com.nisovin.magicspells.variables.Variable
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConfigManagerShopRequirementValidationTest {
    private val serializer = PlainTextComponentSerializer.plainText()
    private val variable = TestVariable()

    @Test
    fun `shop requirement parses hideOffer when configured`() {
        val (requirement, errors) = parseShopRequirementConfig(
            requirementYaml = mapOf(
                "variable" to "lumberjack_level",
                "min" to 5,
                "hideOffer" to true,
            ),
            path = "rootMenu.items[0].requirements[0]",
            variableLookup = { variableName -> if (variableName == "lumberjack_level") variable else null },
        )

        val parsedRequirement = assertNotNull(requirement)
        assertEquals(variable, parsedRequirement.variable)
        assertEquals(5, parsedRequirement.min)
        assertTrue(parsedRequirement.hideOffer)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `shop requirement defaults hideOffer to false`() {
        val (requirement, errors) = parseShopRequirementConfig(
            requirementYaml = mapOf(
                "variable" to "lumberjack_level",
                "min" to 1,
            ),
            path = "rootMenu.items[0].requirements[0]",
            variableLookup = { variableName -> if (variableName == "lumberjack_level") variable else null },
        )

        val parsedRequirement = assertNotNull(requirement)
        assertFalse(parsedRequirement.hideOffer)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `shop requirement rejects non boolean hideOffer`() {
        val (requirement, errors) = parseShopRequirementConfig(
            requirementYaml = mapOf(
                "variable" to "lumberjack_level",
                "min" to 1,
                "hideOffer" to "true",
            ),
            path = "rootMenu.items[0].requirements[0]",
            variableLookup = { variableName -> if (variableName == "lumberjack_level") variable else null },
        )

        val parsedRequirement = assertNotNull(requirement)
        assertFalse(parsedRequirement.hideOffer)
        assertEquals(
            listOf(" - rootMenu.items[0].requirements[0].hideOffer: Must be a boolean"),
            errors.map(serializer::serialize),
        )
    }

    private class TestVariable : Variable() {
        override fun set(playerName: String, value: Double) = Unit
        override fun getValue(playerName: String): Double = 0.0
        override fun reset(playerName: String) = Unit
    }
}
