package com.danidipp.sneakynpcs.shop

class RequirementService {
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
            is VariableShopRequirement -> requirement.variable.getValue(evaluation.player).toInt() >= requirement.min
            is ReputationShopRequirement -> evaluation.owningNpcReputation >= requirement.minimum
        }
    }
}
