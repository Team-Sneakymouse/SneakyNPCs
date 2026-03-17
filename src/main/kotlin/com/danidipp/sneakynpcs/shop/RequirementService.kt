package com.danidipp.sneakynpcs.shop

import org.bukkit.entity.Player

class RequirementService {
    fun requirementsMet(player: Player, requirements: List<ShopRequirement>): Boolean {
        if (requirements.isEmpty()) return true
        for (requirement in requirements) {
            val value = requirement.variable.getValue(player).toInt()
            if (value < requirement.min) return false
        }
        return true
    }
}
