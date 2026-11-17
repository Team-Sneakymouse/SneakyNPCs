package com.danidipp.sneakynpcs.shop

import com.sneakyitems.util.magicitems.MagicItem

data class CurrencyDefinition(
    val id: String,
    val itemId: String?,
    val itemMagicItem: MagicItem?,
    val variableId: String?,
    val exchangeToCurrency: String?,
    val exchangeRate: Int?,
    val sellable: Boolean,
)

data class ShopPrice(
    val currencyId: String,
    val amount: Int,
)

sealed interface ShopItemRequirement {
    val hideOffer: Boolean
}

data class VariableShopRequirement(
    val variableName: String,
    val min: Int,
    override val hideOffer: Boolean = false,
) : ShopItemRequirement

data class ReputationShopRequirement(
    val minimum: Double,
    override val hideOffer: Boolean = false,
) : ShopItemRequirement

data class ShopRequirementEvaluation(
    val player: org.bukkit.entity.Player,
    val owningNpcReputation: Double,
)

enum class PaymentSource {
    INVENTORY,
    BANK,
}
