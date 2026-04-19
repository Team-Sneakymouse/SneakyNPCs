package com.danidipp.sneakynpcs.shop

import com.danidipp.sneakynpcs.PlayerData
import com.danidipp.sneakynpcs.ShopItemLimitConfig
import com.danidipp.sneakynpcs.ShopItemStockState
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ShopItemStockServiceTest {
    private val service = ShopItemStockService()

    @Test
    fun `missing state initializes at full stock`() {
        val state = service.getOrCreateRestockedStock(
            playerData = emptyPlayerData(),
            npcId = "merchant",
            stockEntryId = "rootMenu/options/1/items/item-brick",
            limits = limits(),
            nowMillis = 0L,
        )

        assertEquals(5, state.remainingQuantity)
        assertEquals(0L, state.lastRestockAtEpochMillis)
    }

    @Test
    fun `partial stock catches up across offline intervals`() {
        val playerData = emptyPlayerData().apply {
            setShopItemStockState(
                "merchant",
                "rootMenu/options/1/items/item-brick",
                ShopItemStockState(remainingQuantity = 2, lastRestockAtEpochMillis = 0L)
            )
        }

        val state = service.getOrCreateRestockedStock(
            playerData = playerData,
            npcId = "merchant",
            stockEntryId = "rootMenu/options/1/items/item-brick",
            limits = limits(),
            nowMillis = 180_000L,
        )

        assertEquals(5, state.remainingQuantity)
        assertEquals(180_000L, state.lastRestockAtEpochMillis)
    }

    @Test
    fun `full stock clamps and resets last restock time`() {
        val playerData = emptyPlayerData().apply {
            setShopItemStockState(
                "merchant",
                "rootMenu/options/1/items/item-brick",
                ShopItemStockState(remainingQuantity = 9, lastRestockAtEpochMillis = 0L)
            )
        }

        val state = service.getOrCreateRestockedStock(
            playerData = playerData,
            npcId = "merchant",
            stockEntryId = "rootMenu/options/1/items/item-brick",
            limits = limits(maxQuantity = 5),
            nowMillis = 42L,
        )

        assertEquals(5, state.remainingQuantity)
        assertEquals(42L, state.lastRestockAtEpochMillis)
    }

    @Test
    fun `consume fails when stock is sold out`() {
        val playerData = emptyPlayerData().apply {
            setShopItemStockState(
                "merchant",
                "rootMenu/options/1/items/item-brick",
                ShopItemStockState(remainingQuantity = 0, lastRestockAtEpochMillis = 0L)
            )
        }

        val result = service.consumeForPurchase(
            playerData = playerData,
            npcId = "merchant",
            stockEntryId = "rootMenu/options/1/items/item-brick",
            limits = limits(),
            requestedQuantity = 1,
            nowMillis = 0L,
        )

        val failure = assertIs<ShopItemStockService.ConsumeResult.Failure>(result)
        assertEquals("This item is sold out.", failure.message)
    }

    @Test
    fun `config max reduction clamps old larger saved values`() {
        val playerData = emptyPlayerData().apply {
            setShopItemStockState(
                "merchant",
                "rootMenu/options/1/items/item-brick",
                ShopItemStockState(remainingQuantity = 8, lastRestockAtEpochMillis = 0L)
            )
        }

        val state = service.getOrCreateRestockedStock(
            playerData = playerData,
            npcId = "merchant",
            stockEntryId = "rootMenu/options/1/items/item-brick",
            limits = limits(maxQuantity = 3),
            nowMillis = 100L,
        )

        assertEquals(3, state.remainingQuantity)
        assertEquals(100L, state.lastRestockAtEpochMillis)
    }

    @Test
    fun `zero restock amount never replenishes`() {
        val playerData = emptyPlayerData().apply {
            setShopItemStockState(
                "merchant",
                "rootMenu/options/1/items/item-brick",
                ShopItemStockState(remainingQuantity = 2, lastRestockAtEpochMillis = 0L)
            )
        }

        val state = service.getOrCreateRestockedStock(
            playerData = playerData,
            npcId = "merchant",
            stockEntryId = "rootMenu/options/1/items/item-brick",
            limits = limits(restockAmount = 0),
            nowMillis = 180_000L,
        )

        assertEquals(2, state.remainingQuantity)
        assertEquals(0L, state.lastRestockAtEpochMillis)
    }

    @Test
    fun `consume grants only as much as remains and persists new quantity`() {
        val playerData = emptyPlayerData().apply {
            setShopItemStockState(
                "merchant",
                "rootMenu/options/1/items/item-brick",
                ShopItemStockState(remainingQuantity = 3, lastRestockAtEpochMillis = 0L)
            )
        }

        val result = service.consumeForPurchase(
            playerData = playerData,
            npcId = "merchant",
            stockEntryId = "rootMenu/options/1/items/item-brick",
            limits = limits(),
            requestedQuantity = 64,
            nowMillis = 0L,
        )

        val success = assertIs<ShopItemStockService.ConsumeResult.Success>(result)
        assertEquals(3, success.grantedQuantity)
        val updated = assertNotNull(playerData.getShopItemStockState("merchant", "rootMenu/options/1/items/item-brick"))
        assertEquals(0, updated.remainingQuantity)
    }

    private fun emptyPlayerData(): PlayerData {
        return PlayerData(
            uuid = UUID.randomUUID(),
            completedQuests = mutableSetOf(),
            reputation = mutableMapOf(),
            npcWallets = mutableMapOf(),
            shopItemStocks = mutableMapOf(),
        )
    }

    private fun limits(
        maxQuantity: Int = 5,
        restockIntervalSeconds: Long = 60L,
        restockAmount: Int = 1,
    ): ShopItemLimitConfig {
        return ShopItemLimitConfig(
            maxQuantity = maxQuantity,
            restockIntervalSeconds = restockIntervalSeconds,
            restockAmount = restockAmount,
        )
    }
}
