package com.danidipp.sneakynpcs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import java.util.UUID

class PlayerDataPersistenceTest {
    @Test
    fun `player data round trips npc wallets`() {
        val uuid = UUID.randomUUID()
        val playerData = PlayerData(
            uuid = uuid,
            completedQuests = mutableSetOf("jacktimbers-learning_to_jack_1"),
            reputation = mutableMapOf("CopperGuild" to 12),
            npcWallets = mutableMapOf(
                "jacktimbers" to NpcWalletState(
                    nativeCurrencyId = "silver",
                    lastRestockAtEpochMillis = 1234L,
                    balances = mutableMapOf("silver" to 99L, "penny" to 10L)
                )
            ),
            shopItemStocks = mutableMapOf(
                "jacktimbers" to mutableMapOf(
                    "rootMenu/options/1/items/0" to ShopItemStockState(
                        remainingQuantity = 3,
                        lastRestockAtEpochMillis = 5678L
                    )
                )
            ),
        )

        val reloaded = loadPlayerDataFromConfig(uuid, playerData.toYaml())

        assertEquals(setOf("jacktimbers-learning_to_jack_1"), reloaded.getCompletedQuests(null))
        assertEquals(12, reloaded.getReputation("CopperGuild"))
        val wallet = assertNotNull(reloaded.getNpcWalletState("jacktimbers"))
        assertEquals("silver", wallet.nativeCurrencyId)
        assertEquals(1234L, wallet.lastRestockAtEpochMillis)
        assertEquals(mapOf("silver" to 99L, "penny" to 10L), wallet.balances)
        val stock = assertNotNull(reloaded.getShopItemStockState("jacktimbers", "rootMenu/options/1/items/0"))
        assertEquals(3, stock.remainingQuantity)
        assertEquals(5678L, stock.lastRestockAtEpochMillis)
    }
}
