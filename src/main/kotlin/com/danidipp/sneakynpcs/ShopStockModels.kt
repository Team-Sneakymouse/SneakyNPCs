package com.danidipp.sneakynpcs

data class ShopItemLimitConfig(
    val maxQuantity: Int,
    val restockIntervalSeconds: Long,
    val restockAmount: Int,
)

data class ShopItemStockState(
    val remainingQuantity: Int,
    val lastRestockAtEpochMillis: Long,
) {
    fun deepCopy(): ShopItemStockState = copy()
}
