package com.danidipp.sneakynpcs.shop

import com.nisovin.magicspells.variables.Variable
import org.bukkit.entity.Player
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequirementServiceTest {
    private val service = RequirementService()

    @Test
    fun `requirements must all be met for purchase`() {
        val player = playerNamed("Dani")
        val variable = TestVariable(mapOf("Dani" to 3.0))

        assertTrue(service.requirementsMet(listOf(VariableShopRequirement(variable, min = 3)), evaluation(player, 0.0)))
        assertFalse(service.requirementsMet(listOf(VariableShopRequirement(variable, min = 4)), evaluation(player, 0.0)))
    }

    @Test
    fun `reputation requirement uses inclusive fractional threshold`() {
        val player = playerNamed("Dani")
        val requirement = ReputationShopRequirement(minimum = 12.5)

        assertTrue(service.requirementsMet(listOf(requirement), evaluation(player, 12.5)))
        assertFalse(service.requirementsMet(listOf(requirement), evaluation(player, 12.49)))
    }

    @Test
    fun `zero reputation satisfies zero threshold`() {
        val player = playerNamed("Dani")

        assertTrue(
            service.requirementsMet(
                listOf(ReputationShopRequirement(minimum = 0.0)),
                evaluation(player, 0.0),
            )
        )
    }

    @Test
    fun `variable and reputation requirements must both be met`() {
        val player = playerNamed("Dani")
        val variable = TestVariable(mapOf("Dani" to 3.0))
        val requirements = listOf(
            VariableShopRequirement(variable, min = 3),
            ReputationShopRequirement(minimum = 12.5),
        )

        assertTrue(service.requirementsMet(requirements, evaluation(player, 12.5)))
        assertFalse(service.requirementsMet(requirements, evaluation(player, 12.49)))
    }

    @Test
    fun `unmet reputation requirement only hides offer when configured`() {
        val player = playerNamed("Dani")

        assertTrue(
            service.offerVisible(
                listOf(ReputationShopRequirement(minimum = 12.5, hideOffer = false)),
                evaluation(player, 10.0),
            )
        )
        assertFalse(
            service.offerVisible(
                listOf(ReputationShopRequirement(minimum = 12.5, hideOffer = true)),
                evaluation(player, 10.0),
            )
        )
    }

    @Test
    fun `offer stays visible when unmet requirement does not hide offer`() {
        val player = playerNamed("Dani")
        val variable = TestVariable(mapOf("Dani" to 3.0))

        assertTrue(service.offerVisible(listOf(VariableShopRequirement(variable, min = 4, hideOffer = false)), evaluation(player, 0.0)))
    }

    @Test
    fun `offer is hidden when any hiding requirement is unmet`() {
        val player = playerNamed("Dani")
        val level = TestVariable(mapOf("Dani" to 3.0))
        val quest = TestVariable(mapOf("Dani" to 1.0))

        assertFalse(
            service.offerVisible(
                listOf(
                    VariableShopRequirement(level, min = 4, hideOffer = true),
                    VariableShopRequirement(quest, min = 1, hideOffer = true),
                ),
                evaluation(player, 0.0),
            )
        )
    }

    @Test
    fun `offer is visible when all hiding requirements are met`() {
        val player = playerNamed("Dani")
        val variable = TestVariable(mapOf("Dani" to 4.0))

        assertTrue(service.offerVisible(listOf(VariableShopRequirement(variable, min = 4, hideOffer = true)), evaluation(player, 0.0)))
    }

    private fun playerNamed(name: String): Player {
        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getName" -> name
                "toString" -> "Player($name)"
                "hashCode" -> name.hashCode()
                "equals" -> proxy === args?.get(0)
                else -> error("Unexpected Player method call: ${method.name}")
            }
        } as Player
    }

    private fun evaluation(player: Player, reputation: Double) =
        ShopRequirementEvaluation(player, owningNpcReputation = reputation)

    private class TestVariable(private val values: Map<String, Double>) : Variable() {
        override fun set(playerName: String, value: Double) = Unit
        override fun getValue(playerName: String): Double = values[playerName] ?: 0.0
        override fun reset(playerName: String) = Unit
    }
}
