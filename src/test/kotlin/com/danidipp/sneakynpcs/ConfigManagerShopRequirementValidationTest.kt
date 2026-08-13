package com.danidipp.sneakynpcs

import com.nisovin.magicspells.variables.Variable
import com.danidipp.sneakynpcs.shop.ReputationShopRequirement
import com.danidipp.sneakynpcs.shop.VariableShopRequirement
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigManagerShopRequirementValidationTest {
    private val serializer = PlainTextComponentSerializer.plainText()
    private val variable = TestVariable()

    @Test
    fun `shop reputation requirement parses fractional threshold`() {
        val (requirement, errors) = parseShopRequirementConfig(
            requirementYaml = mapOf(
                "reputation" to 12.5,
                "hideOffer" to true,
            ),
            path = "rootMenu.items[0].requirements[0]",
            variableLookup = { null },
        )

        val parsedRequirement = requirement as ReputationShopRequirement
        assertEquals(12.5, parsedRequirement.minimum)
        assertTrue(parsedRequirement.hideOffer)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `shop requirement rejects multiple requirement types`() {
        val (requirement, errors) = parseShopRequirementConfig(
            requirementYaml = mapOf(
                "variable" to "lumberjack_level",
                "min" to 3,
                "reputation" to 12.5,
            ),
            path = "rootMenu.items[0].requirements[0]",
            variableLookup = { variable },
        )

        assertNull(requirement)
        assertEquals(
            listOf(" - rootMenu.items[0].requirements[0]: Requirement must define exactly one of 'variable' or 'reputation'"),
            errors.map(serializer::serialize),
        )
    }

    @Test
    fun `shop reputation requirement rejects invalid thresholds`() {
        val invalidValues = listOf("12.5", -1, Double.NaN, Double.POSITIVE_INFINITY)

        for (invalidValue in invalidValues) {
            val (requirement, errors) = parseShopRequirementConfig(
                requirementYaml = mapOf("reputation" to invalidValue),
                path = "rootMenu.items[0].requirements[0]",
                variableLookup = { null },
            )

            assertNull(requirement, "Expected '$invalidValue' to be rejected")
            assertTrue(errors.isNotEmpty(), "Expected '$invalidValue' to produce a validation error")
        }
    }

    @Test
    fun `shop reputation requirement rejects variable minimum field`() {
        val (requirement, errors) = parseShopRequirementConfig(
            requirementYaml = mapOf(
                "reputation" to 12.5,
                "min" to 10,
            ),
            path = "rootMenu.items[0].requirements[0]",
            variableLookup = { null },
        )

        assertNull(requirement)
        assertEquals(
            listOf(" - rootMenu.items[0].requirements[0].min: Not allowed for a reputation requirement"),
            errors.map(serializer::serialize),
        )
    }

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

        val parsedRequirement = assertNotNull(requirement) as VariableShopRequirement
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

        val parsedRequirement = assertNotNull(requirement) as VariableShopRequirement
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

        val parsedRequirement = assertNotNull(requirement) as VariableShopRequirement
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
