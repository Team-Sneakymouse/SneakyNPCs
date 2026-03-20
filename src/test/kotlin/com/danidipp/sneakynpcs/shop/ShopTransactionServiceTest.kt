package com.danidipp.sneakynpcs.shop

import com.danidipp.sneakynpcs.NoOpInventoryTransactionLogger
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ShopTransactionServiceTest {
    private val lookup = FakeCurrencyLookup()
    private val service = ShopTransactionService(
        currencyGraphService = lookup,
        balanceService = BalanceService(),
        requirementService = RequirementService(),
        npcWalletService = NpcWalletService(lookup),
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
