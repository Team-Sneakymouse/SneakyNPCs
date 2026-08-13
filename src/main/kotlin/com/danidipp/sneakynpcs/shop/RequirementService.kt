package com.danidipp.sneakynpcs.shop

import org.bukkit.entity.Player

class RequirementService {
    fun requirementsMet(player: Player, requirements: List<ShopRequirement>): Boolean {
        if (requirements.isEmpty()) return true
        for (requirement in requirements) {
            if (!requirementMet(player, requirement)) return false
        }
        return true
    }

    fun offerVisible(player: Player, requirements: List<ShopRequirement>): Boolean {
        for (requirement in requirements) {
            if (requirement.hideOffer && !requirementMet(player, requirement)) return false
        }
        return true
    }

    private fun requirementMet(player: Player, requirement: ShopRequirement): Boolean {
        val value = requirement.variable.getValue(player).toInt()
        return value >= requirement.min
    }
}
