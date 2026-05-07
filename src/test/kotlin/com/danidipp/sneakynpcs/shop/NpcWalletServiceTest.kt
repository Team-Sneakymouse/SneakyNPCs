package com.danidipp.sneakynpcs.shop

import com.danidipp.sneakynpcs.NPC
import com.danidipp.sneakynpcs.NpcWalletConfig
import com.danidipp.sneakynpcs.NpcWalletState
import com.danidipp.sneakynpcs.PlayerData
import com.danidipp.sneakynpcs.menus.CustomMenu
import java.math.BigInteger
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class NpcWalletServiceTest {
    private val lookup = FakeCurrencyLookup()
    private val service = NpcWalletService(lookup)

    @Test
    fun `first access initializes full wallet`() {
        val playerData = emptyPlayerData()

        val state = service.getOrCreateRestockedWallet(playerData, npc())

        assertEquals("gold", state.nativeCurrencyId)
        assertEquals(mapOf("gold" to 10L), state.balances)
    }

    @Test
    fun `wallet catches up across offline intervals`() {
        val playerData = emptyPlayerData().apply {
            setNpcWalletState(
                "merchant",
                NpcWalletState(
                    nativeCurrencyId = "gold",
                    lastRestockAtEpochMillis = 0L,
                    balances = mutableMapOf("gold" to 4L)
                )
            )
        }

        val state = service.getOrCreateRestockedWallet(playerData, npc(), nowMillis = 180_000L)

        assertEquals(mapOf("gold" to 7L), state.balances)
        assertEquals(180_000L, state.lastRestockAtEpochMillis)
    }

    @Test
    fun `wallet normalization prefers native currency`() {
        val playerData = emptyPlayerData().apply {
            setNpcWalletState(
                "merchant",
                NpcWalletState(
                    nativeCurrencyId = "gold",
                    lastRestockAtEpochMillis = 0L,
                    balances = mutableMapOf("silver" to 120L)
                )
            )
        }

        val state = service.getOrCreateRestockedWallet(playerData, npc(walletMax = 20L), nowMillis = 0L)

        assertEquals(mapOf("gold" to 2L, "silver" to 20L), state.balances)
    }

    @Test
    fun `selling debits wallet and leaves lower denomination remainder`() {
        val playerData = emptyPlayerData()

        val result = service.spendForSale(playerData, npc(), saleCurrencyId = "silver", saleUnits = 30L, nowMillis = 0L)

        assertIs<NpcWalletService.SpendResult.Success>(result)
        val state = assertNotNull(playerData.getNpcWalletState("merchant"))
        assertEquals(mapOf("gold" to 9L, "silver" to 20L), state.balances)
    }

    @Test
    fun `native currency mismatch reinitializes wallet`() {
        val playerData = emptyPlayerData().apply {
            setNpcWalletState(
                "merchant",
                NpcWalletState(
                    nativeCurrencyId = "silver",
                    lastRestockAtEpochMillis = 0L,
                    balances = mutableMapOf("silver" to 200L)
                )
            )
        }

        val state = service.getOrCreateRestockedWallet(playerData, npc(), nowMillis = 0L)

        assertEquals("gold", state.nativeCurrencyId)
        assertEquals(mapOf("gold" to 10L), state.balances)
    }

    @Test
    fun `debug wallet set zeroes non native balances`() {
        val playerData = emptyPlayerData().apply {
            setNpcWalletState(
                "merchant",
                NpcWalletState(
                    nativeCurrencyId = "gold",
                    lastRestockAtEpochMillis = 0L,
                    balances = mutableMapOf("gold" to 2L, "silver" to 50L)
                )
            )
        }

        val state = service.setNativeWalletAmount(playerData, npc(), nativeAmount = 3L, nowMillis = 42L)

        assertEquals(42L, state.lastRestockAtEpochMillis)
        assertEquals(mapOf("gold" to 3L), state.balances)
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

    private fun npc(walletMax: Long = 10L): NPC {
        return NPC(
            id = "merchant",
            style = "merchant",
            friendship = false,
            wallet = NpcWalletConfig(
                currencyId = "gold",
                max = walletMax,
                restockIntervalSeconds = 60L,
                restockAmount = 1L
            ),
            rootMenu = CustomMenu("root")
        )
    }

    private class FakeCurrencyLookup : CurrencyLookup {
        private val currencies = mapOf(
            "gold" to CurrencyDefinition("gold", null, null, "bankGold", "silver", 50, true),
            "silver" to CurrencyDefinition("silver", null, null, "bankSilver", "penny", 50, true),
            "penny" to CurrencyDefinition("penny", null, null, "bankPenny", null, null, true)
        )

        private val atomic = mapOf(
            "gold" to BigInteger.valueOf(2500L),
            "silver" to BigInteger.valueOf(50L),
            "penny" to BigInteger.ONE
        )

        private val component = setOf("gold", "silver", "penny")

        override fun getCurrency(currencyId: String): CurrencyDefinition? = currencies[currencyId]

        override fun hasCurrency(currencyId: String): Boolean = currencies.containsKey(currencyId)

        override fun getAtomicValue(currencyId: String): BigInteger? = atomic[currencyId]

        override fun getConvertibleCurrencies(currencyId: String): Set<String> {
            return if (currencyId in component) component else emptySet()
        }

        override fun getWalletCurrencies(nativeCurrencyId: String): List<CurrencyDefinition> {
            val native = currencies[nativeCurrencyId] ?: return emptyList()
            val orderedOthers = component.filter { it != nativeCurrencyId }
                .sortedWith(compareByDescending<String> { atomic[it] ?: BigInteger.ZERO }.thenBy { it })
                .mapNotNull(currencies::get)
            return listOf(native) + orderedOthers
        }

        override fun isConvertible(fromCurrencyId: String, toCurrencyId: String): Boolean {
            return fromCurrencyId in component && toCurrencyId in component
        }
    }
}
