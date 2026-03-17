package com.danidipp.sneakynpcs.shop

import com.nisovin.magicspells.util.magicitems.MagicItem
import com.nisovin.magicspells.variables.Variable

data class CurrencyDefinition(
    val id: String,
    val itemId: String?,
    val itemMagicItem: MagicItem?,
    val variableId: String?,
    val exchangeToCurrency: String?,
    val exchangeRate: Int?,
)

data class ShopPrice(
    val currencyId: String,
    val amount: Int,
)

data class ShopRequirement(
    val variable: Variable,
    val min: Int,
)

enum class PaymentSource {
    INVENTORY,
    BANK,
}
