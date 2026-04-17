package com.danidipp.sneakynpcs.shop

import com.danidipp.sneakynpcs.PlayerData
import com.danidipp.sneakynpcs.ShopItemLimitConfig
import com.danidipp.sneakynpcs.ShopItemStockState

class ShopItemStockService {
    sealed class ConsumeResult {
        data class Success(val grantedQuantity: Int) : ConsumeResult()
        data class Failure(val message: String) : ConsumeResult()
    }

    fun getOrCreateRestockedStock(
        playerData: PlayerData,
        npcId: String,
        stockEntryId: String,
        limits: ShopItemLimitConfig,
        nowMillis: Long = System.currentTimeMillis(),
    ): ShopItemStockState {
        val existingState = playerData.getShopItemStockState(npcId, stockEntryId)
        val startingState = existingState ?: ShopItemStockState(
            remainingQuantity = limits.maxQuantity,
            lastRestockAtEpochMillis = nowMillis
        )

        val normalizedState = restockAndClamp(startingState, limits, nowMillis)
        if (existingState != normalizedState) {
            playerData.setShopItemStockState(npcId, stockEntryId, normalizedState)
        }
        return normalizedState
    }

    fun getRemainingQuantity(
        playerData: PlayerData,
        npcId: String,
        stockEntryId: String,
        limits: ShopItemLimitConfig?,
        nowMillis: Long = System.currentTimeMillis(),
    ): Int {
        if (limits == null) return Int.MAX_VALUE
        return getOrCreateRestockedStock(playerData, npcId, stockEntryId, limits, nowMillis).remainingQuantity
    }

    fun consumeForPurchase(
        playerData: PlayerData,
        npcId: String,
        stockEntryId: String,
        limits: ShopItemLimitConfig?,
        requestedQuantity: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): ConsumeResult {
        if (requestedQuantity <= 0) return ConsumeResult.Failure("Invalid quantity.")
        if (limits == null) return ConsumeResult.Success(requestedQuantity)

        val currentState = getOrCreateRestockedStock(playerData, npcId, stockEntryId, limits, nowMillis)
        if (currentState.remainingQuantity <= 0) {
            return ConsumeResult.Failure("This item is sold out.")
        }

        val grantedQuantity = minOf(requestedQuantity, currentState.remainingQuantity)
        val updatedState = currentState.copy(remainingQuantity = currentState.remainingQuantity - grantedQuantity)
        playerData.setShopItemStockState(npcId, stockEntryId, updatedState)
        return ConsumeResult.Success(grantedQuantity)
    }

    private fun restockAndClamp(
        state: ShopItemStockState,
        limits: ShopItemLimitConfig,
        nowMillis: Long,
    ): ShopItemStockState {
        var remainingQuantity = state.remainingQuantity.coerceIn(0, limits.maxQuantity)
        var lastRestockAt = state.lastRestockAtEpochMillis

        if (remainingQuantity >= limits.maxQuantity) {
            remainingQuantity = limits.maxQuantity
            lastRestockAt = nowMillis
        } else if (limits.restockAmount > 0 && limits.restockIntervalSeconds > 0L) {
            val intervalMillis = limits.restockIntervalSeconds * 1000L
            val elapsedMillis = (nowMillis - lastRestockAt).coerceAtLeast(0L)
            val elapsedIntervals = elapsedMillis / intervalMillis
            if (elapsedIntervals > 0L) {
                val addedQuantity = (elapsedIntervals * limits.restockAmount.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                remainingQuantity = (remainingQuantity + addedQuantity).coerceAtMost(limits.maxQuantity)
                lastRestockAt = if (remainingQuantity >= limits.maxQuantity) {
                    nowMillis
                } else {
                    lastRestockAt + elapsedIntervals * intervalMillis
                }
            }
        }

        return ShopItemStockState(
            remainingQuantity = remainingQuantity,
            lastRestockAtEpochMillis = lastRestockAt
        )
    }
}
