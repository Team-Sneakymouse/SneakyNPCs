package com.danidipp.sneakynpcs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import java.util.UUID
import org.bukkit.configuration.file.YamlConfiguration

class PlayerDataPersistenceTest {
    @Test
    fun `player data round trips npc wallets`() {
        val uuid = UUID.randomUUID()
        val playerData = PlayerData(
            uuid = uuid,
            completedQuests = mutableSetOf("jacktimbers-learning_to_jack_1"),
            reputation = mutableMapOf("jacktimbers" to 12.5),
            npcWallets = mutableMapOf(
                "jacktimbers" to NpcWalletState(
                    nativeCurrencyId = "silver",
                    lastRestockAtEpochMillis = 1234L,
                    balances = mutableMapOf("silver" to 99L, "penny" to 10L)
                )
            ),
            shopItemStocks = mutableMapOf(
                "jacktimbers" to mutableMapOf(
                    "rootMenu/options/1/items/item-brick" to ShopItemStockState(
                        remainingQuantity = 3,
                        lastRestockAtEpochMillis = 5678L
                    )
                )
            ),
        )

        val reloaded = loadPlayerDataFromConfig(uuid, playerData.toYaml())

        assertEquals(setOf("jacktimbers-learning_to_jack_1"), reloaded.getCompletedQuests(null))
        assertEquals(12.5, reloaded.getReputation("jacktimbers"))
        val wallet = assertNotNull(reloaded.getNpcWalletState("jacktimbers"))
        assertEquals("silver", wallet.nativeCurrencyId)
        assertEquals(1234L, wallet.lastRestockAtEpochMillis)
        assertEquals(mapOf("silver" to 99L, "penny" to 10L), wallet.balances)
        val stock = assertNotNull(reloaded.getShopItemStockState("jacktimbers", "rootMenu/options/1/items/item-brick"))
        assertEquals(3, stock.remainingQuantity)
        assertEquals(5678L, stock.lastRestockAtEpochMillis)
    }

    @Test
    fun `zero reputation is not stored`() {
        val playerData = PlayerData(
            uuid = UUID.randomUUID(),
            completedQuests = mutableSetOf(),
            reputation = mutableMapOf("jacktimbers" to 12.5),
            npcWallets = mutableMapOf(),
            shopItemStocks = mutableMapOf(),
        )

        playerData.setReputation("jacktimbers", 0.0)

        assertEquals(0.0, playerData.getReputation("jacktimbers"))
        assertEquals(emptyMap(), playerData.getReputationEntries())
    }

    @Test
    fun `player data drops legacy reputation keys when known npc ids are provided`() {
        val uuid = UUID.randomUUID()
        val config = YamlConfiguration()
        config.set("reputation.CopperGuild", 12)
        config.set("reputation.jacktimbers", 4.5)

        val reloaded = loadPlayerDataFromConfig(uuid, config, knownNpcIds = setOf("jacktimbers"))

        assertEquals(4.5, reloaded.getReputation("jacktimbers"))
        assertEquals(0.0, reloaded.getReputation("CopperGuild"))
        assertEquals(mapOf("jacktimbers" to 4.5), reloaded.getReputationEntries())
    }
}
