package com.danidipp.sneakynpcs.shop

import com.danidipp.sneakynpcs.NoOpInventoryTransactionLogger
import com.danidipp.sneakynpcs.PlayerData
import com.danidipp.sneakynpcs.ShopItemLimitConfig
import java.math.BigInteger
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ShopTransactionServiceTest {
    private val lookup = FakeCurrencyLookup()
    private val service = ShopTransactionService(
        currencyGraphService = lookup,
        balanceService = BalanceService(),
        requirementService = RequirementService(),
        npcWalletService = NpcWalletService(lookup),
        shopItemStockService = ShopItemStockService(),
        inventoryTransactionLogger = NoOpInventoryTransactionLogger,
    )

    @Test
    fun `bank can cover remainder after compatible inventory spend is preserved`() {
        val plan = service.buildPaymentPlanFromBuckets(
            priceCurrency = lookup.getCurrency("silver")!!,
            requiredAtomic = BigInteger.valueOf(100L),
            priceAtomic = BigInteger.valueOf(50L),
            buckets = listOf(
                ShopTransactionService.FundBucket("silver", PaymentSource.INVENTORY, 1L, BigInteger.valueOf(50L)),
                ShopTransactionService.FundBucket("penny", PaymentSource.INVENTORY, 1L, BigInteger.ONE),
                ShopTransactionService.FundBucket("silver", PaymentSource.BANK, 1L, BigInteger.valueOf(50L)),
            )
        )

        val resolvedPlan = assertNotNull(plan)
        assertEquals(
            mapOf(
                "silver:${PaymentSource.INVENTORY}" to 1L,
                "silver:${PaymentSource.BANK}" to 1L,
            ),
            resolvedPlan.spends.associate { "${it.currencyId}:${it.source}" to it.units }
        )
        assertEquals(0L, resolvedPlan.changeUnits)
    }

    @Test
    fun `bank can fully cover purchase when lower inventory residue would block settlement`() {
        val plan = service.buildPaymentPlanFromBuckets(
            priceCurrency = lookup.getCurrency("silver")!!,
            requiredAtomic = BigInteger.valueOf(100L),
            priceAtomic = BigInteger.valueOf(50L),
            buckets = listOf(
                ShopTransactionService.FundBucket("penny", PaymentSource.INVENTORY, 1L, BigInteger.ONE),
                ShopTransactionService.FundBucket("silver", PaymentSource.BANK, 2L, BigInteger.valueOf(50L)),
            )
        )

        val resolvedPlan = assertNotNull(plan)
        assertEquals(
            mapOf("silver:${PaymentSource.BANK}" to 2L),
            resolvedPlan.spends.associate { "${it.currencyId}:${it.source}" to it.units }
        )
        assertEquals(0L, resolvedPlan.changeUnits)
    }

    @Test
    fun `sell value keeps full amount for non-durable items`() {
        assertEquals(25L, service.calculateDurabilityAdjustedUnits(25L, 0L, 0L))
    }

    @Test
    fun `sell value is reduced by remaining durability percentage`() {
        assertEquals(75L, service.calculateDurabilityAdjustedUnits(100L, 100L, 25L))
    }

    @Test
    fun `fully broken durable items sell for zero`() {
        assertEquals(0L, service.calculateDurabilityAdjustedUnits(100L, 100L, 100L))
    }

    @Test
    fun `resolve purchase quantity clamps to remaining limited stock`() {
        val playerData = emptyPlayerData()

        val resolvedQuantity = service.resolveEffectivePurchaseQuantity(
            playerData = playerData,
            npcId = "merchant",
            stockEntryId = "rootMenu/options/1/items/item-brick",
            limits = limits(maxQuantity = 5),
            requestedQuantity = 64,
            nowMillis = 0L,
        )

        assertEquals(5, resolvedQuantity)
    }

    @Test
    fun `resolve purchase quantity returns null when stock is sold out`() {
        val playerData = emptyPlayerData().apply {
            setShopItemStockState(
                "merchant",
                "rootMenu/options/1/items/item-brick",
                com.danidipp.sneakynpcs.ShopItemStockState(
                    remainingQuantity = 0,
                    lastRestockAtEpochMillis = 0L
                )
            )
        }

        val resolvedQuantity = service.resolveEffectivePurchaseQuantity(
            playerData = playerData,
            npcId = "merchant",
            stockEntryId = "rootMenu/options/1/items/item-brick",
            limits = limits(maxQuantity = 5),
            requestedQuantity = 1,
            nowMillis = 0L,
        )

        assertNull(resolvedQuantity)
    }

    @Test
    fun `resolve purchase quantity uses requested quantity for unlimited items`() {
        val resolvedQuantity = service.resolveEffectivePurchaseQuantity(
            playerData = emptyPlayerData(),
            npcId = "merchant",
            stockEntryId = "rootMenu/options/1/items/item-plank",
            limits = null,
            requestedQuantity = 1,
            nowMillis = 0L,
        )

        assertEquals(1, resolvedQuantity)
    }

    @Test
    fun `resolve purchase quantity uses restocked remaining stock for fit and pricing`() {
        val playerData = emptyPlayerData().apply {
            setShopItemStockState(
                "merchant",
                "rootMenu/options/1/items/item-brick",
                com.danidipp.sneakynpcs.ShopItemStockState(
                    remainingQuantity = 2,
                    lastRestockAtEpochMillis = 0L
                )
            )
        }

        val resolvedQuantity = service.resolveEffectivePurchaseQuantity(
            playerData = playerData,
            npcId = "merchant",
            stockEntryId = "rootMenu/options/1/items/item-brick",
            limits = limits(maxQuantity = 5, restockIntervalSeconds = 60L, restockAmount = 1),
            requestedQuantity = 64,
            nowMillis = 180_000L,
        )

        assertEquals(5, resolvedQuantity)
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
        maxQuantity: Int,
        restockIntervalSeconds: Long = 0L,
        restockAmount: Int = 0,
    ): ShopItemLimitConfig {
        return ShopItemLimitConfig(
            maxQuantity = maxQuantity,
            restockIntervalSeconds = restockIntervalSeconds,
            restockAmount = restockAmount,
        )
    }

    private class FakeCurrencyLookup : CurrencyLookup {
        private val currencies = mapOf(
            "silver" to CurrencyDefinition("silver", null, null, "bankSilver", "penny", 50, true),
            "penny" to CurrencyDefinition("penny", null, null, "bankPenny", null, null, true)
        )

        private val atomic = mapOf(
            "silver" to BigInteger.valueOf(50L),
            "penny" to BigInteger.ONE
        )
        override fun getCurrency(currencyId: String): CurrencyDefinition? = currencies[currencyId]

        override fun hasCurrency(currencyId: String): Boolean = currencies.containsKey(currencyId)

        override fun getAtomicValue(currencyId: String): BigInteger? = atomic[currencyId]

        override fun getConvertibleCurrencies(currencyId: String): Set<String> = currencies.keys

        override fun getWalletCurrencies(nativeCurrencyId: String): List<CurrencyDefinition> = emptyList()

        override fun isConvertible(fromCurrencyId: String, toCurrencyId: String): Boolean {
            return currencies.containsKey(fromCurrencyId) && currencies.containsKey(toCurrencyId)
        }
    }
}
