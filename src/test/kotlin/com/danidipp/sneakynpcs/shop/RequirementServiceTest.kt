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

        assertTrue(service.requirementsMet(player, listOf(ShopRequirement(variable, min = 3))))
        assertFalse(service.requirementsMet(player, listOf(ShopRequirement(variable, min = 4))))
    }

    @Test
    fun `offer stays visible when unmet requirement does not hide offer`() {
        val player = playerNamed("Dani")
        val variable = TestVariable(mapOf("Dani" to 3.0))

        assertTrue(service.offerVisible(player, listOf(ShopRequirement(variable, min = 4, hideOffer = false))))
    }

    @Test
    fun `offer is hidden when any hiding requirement is unmet`() {
        val player = playerNamed("Dani")
        val level = TestVariable(mapOf("Dani" to 3.0))
        val quest = TestVariable(mapOf("Dani" to 1.0))

        assertFalse(
            service.offerVisible(
                player,
                listOf(
                    ShopRequirement(level, min = 4, hideOffer = true),
                    ShopRequirement(quest, min = 1, hideOffer = true),
                ),
            )
        )
    }

    @Test
    fun `offer is visible when all hiding requirements are met`() {
        val player = playerNamed("Dani")
        val variable = TestVariable(mapOf("Dani" to 4.0))

        assertTrue(service.offerVisible(player, listOf(ShopRequirement(variable, min = 4, hideOffer = true))))
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

    private class TestVariable(private val values: Map<String, Double>) : Variable() {
        override fun set(playerName: String, value: Double) = Unit
        override fun getValue(playerName: String): Double = values[playerName] ?: 0.0
        override fun reset(playerName: String) = Unit
    }
}
