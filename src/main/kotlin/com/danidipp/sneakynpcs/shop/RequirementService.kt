package com.danidipp.sneakynpcs.shop

import com.danidipp.sneakynpcs.Variables
import org.bukkit.entity.Player

class RequirementService(
    private val readVariable: (Player, String) -> Double = { player, name -> Variables.getNumber(player, name) },
) {
    fun requirementsMet(requirements: List<ShopItemRequirement>, evaluation: ShopRequirementEvaluation): Boolean {
        if (requirements.isEmpty()) return true
        for (requirement in requirements) {
            if (!requirementMet(requirement, evaluation)) return false
        }
        return true
    }

    fun offerVisible(requirements: List<ShopItemRequirement>, evaluation: ShopRequirementEvaluation): Boolean {
        for (requirement in requirements) {
            if (requirement.hideOffer && !requirementMet(requirement, evaluation)) return false
        }
        return true
    }

    private fun requirementMet(requirement: ShopItemRequirement, evaluation: ShopRequirementEvaluation): Boolean {
        return when (requirement) {
            is VariableShopRequirement ->
                readVariable(evaluation.player, requirement.variableName).toInt() >= requirement.min
            is ReputationShopRequirement -> evaluation.owningNpcReputation >= requirement.minimum
        }
    }
}
