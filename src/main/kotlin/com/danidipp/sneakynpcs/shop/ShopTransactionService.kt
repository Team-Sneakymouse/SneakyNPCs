package com.danidipp.sneakynpcs.shop

import com.danidipp.sneakynpcs.InventoryAuditItems
import com.danidipp.sneakynpcs.InventoryTransactionLogger
import com.danidipp.sneakynpcs.NPC
import com.danidipp.sneakynpcs.PlayerData
import com.danidipp.sneakynpcs.menus.ShopMenuItem
import com.nisovin.magicspells.MagicSpells
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.persistence.PersistentDataType
import java.math.BigInteger

class ShopTransactionService(
    private val currencyGraphService: CurrencyLookup,
    private val balanceService: BalanceService,
    private val requirementService: RequirementService,
    private val npcWalletService: NpcWalletService,
    private val inventoryTransactionLogger: InventoryTransactionLogger,
) {
    private val storeCurrencyKey by lazy(LazyThreadSafetyMode.NONE) {
        NamespacedKey(MagicSpells.getInstance(), "magicspellpermanentdata_store_value_currency")
    }
    private val storeAmountKey by lazy(LazyThreadSafetyMode.NONE) {
        NamespacedKey(MagicSpells.getInstance(), "magicspellpermanentdata_store_value_amount")
    }
    private val expiresAtKey by lazy(LazyThreadSafetyMode.NONE) {
        NamespacedKey(MagicSpells.getInstance(), "expires_at")
    }

    sealed class PurchaseResult {
        object Success : PurchaseResult()
        data class Failure(val message: String) : PurchaseResult()
    }

    sealed class SellResult {
        data class Success(
            val currencyId: String,
            val payoutAmount: Long,
        ) : SellResult()
        data class Failure(val message: String) : SellResult()
    }

    fun purchase(player: Player, shopItem: ShopMenuItem, quantity: Int): PurchaseResult {
        if (quantity <= 0) return PurchaseResult.Failure("Invalid quantity.")
        if (!requirementService.requirementsMet(player, shopItem.requirements)) {
            return PurchaseResult.Failure("You do not meet the requirements.")
        }

        val priceCurrency = currencyGraphService.getCurrency(shopItem.price.currencyId)
            ?: return PurchaseResult.Failure("This item has an invalid currency.")
        val priceAtomic = currencyGraphService.getAtomicValue(shopItem.price.currencyId)
            ?: return PurchaseResult.Failure("This item has an invalid currency graph.")

        val totalAmount = shopItem.price.amount.toLong() * quantity.toLong()
        if (totalAmount <= 0L) return PurchaseResult.Failure("Invalid configured price.")
        val requiredAtomic = priceAtomic.multiply(BigInteger.valueOf(totalAmount))

        val purchasableStack = shopItem.magicItem.itemStack.clone().apply {
            amount = quantity
        }
        if (!balanceService.canFitItem(player.inventory, purchasableStack, quantity)) {
            return PurchaseResult.Failure("Not enough inventory space.")
        }

        val plan = buildPaymentPlan(player, priceCurrency, requiredAtomic, priceAtomic)
            ?: return PurchaseResult.Failure("You don't have enough money.")

        // Mutations begin here after all pre-checks succeeded.
        for (spend in plan.spends.filter { it.source == PaymentSource.INVENTORY }) {
            val currency = currencyGraphService.getCurrency(spend.currencyId)
                ?: return PurchaseResult.Failure("Currency disappeared during payment.")
            if (!balanceService.removeInventoryCurrencyUnits(player, currency, spend.units)) {
                return PurchaseResult.Failure("Failed to remove currency items from inventory.")
            }
        }

        for (spend in plan.spends.filter { it.source == PaymentSource.BANK }) {
            val currency = currencyGraphService.getCurrency(spend.currencyId)
                ?: return PurchaseResult.Failure("Currency disappeared during payment.")
            if (!balanceService.removeBankCurrencyUnits(player, currency, spend.units)) {
                return PurchaseResult.Failure("Failed to remove bank balance.")
            }
        }

        if (plan.changeUnits > 0L) {
            balanceService.addBankCurrencyUnits(player, priceCurrency, plan.changeUnits)
        }

        val leftovers = player.inventory.addItem(purchasableStack)
        if (leftovers.isNotEmpty()) {
            return PurchaseResult.Failure("Not enough inventory space.")
        }

        val removedInventoryItems = buildList {
            for (spend in plan.spends.filter { it.source == PaymentSource.INVENTORY }) {
                val currency = currencyGraphService.getCurrency(spend.currencyId) ?: continue
                val template = currency.itemMagicItem?.itemStack ?: continue
                addAll(InventoryAuditItems.split(template, spend.units))
            }
        }
        inventoryTransactionLogger.log(
            player = player,
            removedItems = removedInventoryItems,
            addedItems = listOf(purchasableStack.clone()),
        )

        return PurchaseResult.Success
    }

    fun sell(player: Player, playerData: PlayerData, npc: NPC, offeredStack: ItemStack): SellResult {
        if (offeredStack.type.isAir || offeredStack.amount <= 0) {
            return SellResult.Failure("There is nothing to sell.")
        }
        if (hasExpirySellBlock(offeredStack)) {
            return SellResult.Failure("This item cannot be sold.")
        }

        val storedValue = resolveStoredValue(offeredStack)
            ?: return SellResult.Failure("This item has no sell value.")
        val priceCurrency = currencyGraphService.getCurrency(storedValue.currencyId)
            ?: return SellResult.Failure("This item has an invalid currency.")
        if (!priceCurrency.sellable) {
            return SellResult.Failure("This item cannot be sold.")
        }
        if (!currencyGraphService.isConvertible(npc.wallet.currencyId, priceCurrency.id)) {
            return SellResult.Failure("This NPC cannot buy that item.")
        }

        val unitsPerItem = calculateSellUnitsPerItem(storedValue.amount.toLong(), offeredStack)
        val totalUnits = unitsPerItem * offeredStack.amount.toLong()
        if (totalUnits <= 0L) {
            return SellResult.Failure("This item has an invalid sell value.")
        }

        if (priceCurrency.variableId == null) {
            val payoutTemplate = priceCurrency.itemMagicItem?.itemStack ?: run {
                return SellResult.Failure("This currency cannot be paid out.")
            }
            if (totalUnits > Int.MAX_VALUE || !balanceService.canFitItem(player.inventory, payoutTemplate, totalUnits.toInt())) {
                return SellResult.Failure("Not enough inventory space for the payout.")
            }
        }

        when (val spendResult = npcWalletService.spendForSale(playerData, npc, priceCurrency.id, totalUnits)) {
            is NpcWalletService.SpendResult.Failure -> return SellResult.Failure(spendResult.message)
            NpcWalletService.SpendResult.Success -> Unit
        }

        if (priceCurrency.variableId != null) {
            balanceService.addBankCurrencyUnits(player, priceCurrency, totalUnits)
        } else if (!balanceService.addInventoryCurrencyUnits(player, priceCurrency, totalUnits)) {
            return SellResult.Failure("Failed to deliver the payout.")
        } else {
            val payoutTemplate = priceCurrency.itemMagicItem?.itemStack
            if (payoutTemplate != null) {
                inventoryTransactionLogger.log(
                    player = player,
                    addedItems = InventoryAuditItems.split(payoutTemplate, totalUnits),
                )
            }
        }

        return SellResult.Success(
            currencyId = priceCurrency.id,
            payoutAmount = totalUnits
        )
    }

    private fun buildPaymentPlan(
        player: Player,
        priceCurrency: CurrencyDefinition,
        requiredAtomic: BigInteger,
        priceAtomic: BigInteger,
    ): PaymentPlan? {
        val convertible = currencyGraphService.getConvertibleCurrencies(priceCurrency.id)
        if (convertible.isEmpty()) return null

        val buckets = buildBuckets(player, convertible)
        if (buckets.isEmpty()) return null
        return buildPaymentPlanFromBuckets(priceCurrency, requiredAtomic, priceAtomic, buckets)
    }

    internal fun buildPaymentPlanFromBuckets(
        priceCurrency: CurrencyDefinition,
        requiredAtomic: BigInteger,
        priceAtomic: BigInteger,
        buckets: List<FundBucket>,
    ): PaymentPlan? {
        val inventoryBuckets = buckets.filter { it.source == PaymentSource.INVENTORY }
        val bankBuckets = buckets.filter { it.source == PaymentSource.BANK }

        val hasBankChange = priceCurrency.variableId != null

        // Inventory-first with optional overpay completion if price currency supports bank change.
        if (inventoryBuckets.isNotEmpty() && hasBankChange) {
            val inventoryOnly = collectWithOptionalOverpay(inventoryBuckets, requiredAtomic)
            if (inventoryOnly != null) {
                val overpay = inventoryOnly.collected.subtract(requiredAtomic)
                if (overpay >= BigInteger.ZERO && overpay.mod(priceAtomic) == BigInteger.ZERO) {
                    val changeUnits = overpay.divide(priceAtomic).longValueExact()
                    return PaymentPlan(inventoryOnly.spends.toMutableList(), changeUnits)
                }
            }
        }

        val inventoryCandidates = buildInventoryCandidates(inventoryBuckets, requiredAtomic, priceAtomic, hasBankChange)
        for (inventoryPart in inventoryCandidates) {
            val collectedSpends = inventoryPart.spends.toMutableList()
            var collectedAtomic = inventoryPart.collected

            if (collectedAtomic >= requiredAtomic) {
                return PaymentPlan(collectedSpends, 0L)
            }

            val remaining = requiredAtomic.subtract(collectedAtomic)
            if (bankBuckets.isEmpty()) continue

            val bankResult = if (hasBankChange) {
                collectWithOptionalOverpayPreferred(bankBuckets, remaining, priceCurrency.id)
            } else {
                collectExactPreferred(bankBuckets, remaining, priceCurrency.id)
            } ?: continue

            collectedSpends.addAll(bankResult.spends)
            collectedAtomic = collectedAtomic.add(bankResult.collected)
            if (collectedAtomic < requiredAtomic) continue

            val overpay = collectedAtomic.subtract(requiredAtomic)
            if (overpay == BigInteger.ZERO) return PaymentPlan(collectedSpends, 0L)
            if (!hasBankChange) continue
            if (overpay.mod(priceAtomic) != BigInteger.ZERO) continue

            return PaymentPlan(
                spends = collectedSpends,
                changeUnits = overpay.divide(priceAtomic).longValueExact()
            )
        }

        return null
    }

    private fun buildBuckets(player: Player, currencies: Set<String>): List<FundBucket> {
        val buckets = mutableListOf<FundBucket>()
        for (currencyId in currencies.sorted()) {
            val currency = currencyGraphService.getCurrency(currencyId) ?: continue
            val atomic = currencyGraphService.getAtomicValue(currencyId) ?: continue
            if (atomic <= BigInteger.ZERO) continue

            val invUnits = balanceService.getInventoryCurrencyUnits(player, currency)
            if (invUnits > 0L) {
                buckets.add(
                    FundBucket(
                        currencyId = currencyId,
                        source = PaymentSource.INVENTORY,
                        availableUnits = invUnits,
                        atomicPerUnit = atomic
                    )
                )
            }

            val bankUnits = balanceService.getBankCurrencyUnits(player, currency)
            if (bankUnits > 0L) {
                buckets.add(
                    FundBucket(
                        currencyId = currencyId,
                        source = PaymentSource.BANK,
                        availableUnits = bankUnits,
                        atomicPerUnit = atomic
                    )
                )
            }
        }
        return buckets
    }

    private fun collectWithOptionalOverpay(buckets: List<FundBucket>, target: BigInteger): CollectResult? {
        val noOverpay = collectNoOverpayBestEffort(buckets, target)
        if (noOverpay.collected >= target) return noOverpay

        val remaining = target.subtract(noOverpay.collected)
        var chosen: Triple<FundBucket, Long, BigInteger>? = null

        val spentByKey = noOverpay.spends.associateBy { "${it.currencyId}:${it.source.name}" }

        for (bucket in buckets.sortedWith(compareByDescending<FundBucket> { it.atomicPerUnit }.thenBy { it.currencyId })) {
            val key = "${bucket.currencyId}:${bucket.source.name}"
            val alreadySpent = spentByKey[key]?.units ?: 0L
            val remainingUnits = bucket.availableUnits - alreadySpent
            if (remainingUnits <= 0L) continue

            val neededUnits = ceilDiv(remaining, bucket.atomicPerUnit)
            if (neededUnits > remainingUnits) continue

            val extraCollected = bucket.atomicPerUnit.multiply(BigInteger.valueOf(neededUnits))
            val overpay = extraCollected.subtract(remaining)
            val candidate = Triple(bucket, neededUnits, overpay)
            if (chosen == null || overpay < chosen!!.third ||
                (overpay == chosen!!.third && neededUnits < chosen!!.second)
            ) {
                chosen = candidate
            }
        }

        if (chosen == null) return null

        val spends = noOverpay.spends.toMutableList()
        spends.add(
            PaymentSpend(
                currencyId = chosen.first.currencyId,
                source = chosen.first.source,
                units = chosen.second
            )
        )
        val collected = noOverpay.collected.add(remaining).add(chosen!!.third)
        return CollectResult(spends = mergeSpends(spends), collected = collected)
    }

    private fun collectWithOptionalOverpayPreferred(
        buckets: List<FundBucket>,
        target: BigInteger,
        preferredCurrencyId: String,
    ): CollectResult? {
        val orderedBuckets = orderBucketsWithPreferredCurrency(buckets, preferredCurrencyId)
        val noOverpay = collectNoOverpayByOrder(orderedBuckets, target)
        if (noOverpay.collected >= target) return noOverpay

        val remaining = target.subtract(noOverpay.collected)
        var chosen: Triple<FundBucket, Long, BigInteger>? = null
        val spentByKey = noOverpay.spends.associateBy { "${it.currencyId}:${it.source.name}" }

        for (bucket in orderedBuckets) {
            val key = "${bucket.currencyId}:${bucket.source.name}"
            val alreadySpent = spentByKey[key]?.units ?: 0L
            val remainingUnits = bucket.availableUnits - alreadySpent
            if (remainingUnits <= 0L) continue

            val neededUnits = ceilDiv(remaining, bucket.atomicPerUnit)
            if (neededUnits > remainingUnits) continue

            val extraCollected = bucket.atomicPerUnit.multiply(BigInteger.valueOf(neededUnits))
            val overpay = extraCollected.subtract(remaining)
            val candidate = Triple(bucket, neededUnits, overpay)
            if (chosen == null || overpay < chosen!!.third ||
                (overpay == chosen!!.third && bucket.currencyId == preferredCurrencyId && chosen!!.first.currencyId != preferredCurrencyId) ||
                (overpay == chosen!!.third && neededUnits < chosen!!.second)
            ) {
                chosen = candidate
            }
        }

        if (chosen == null) return null

        val spends = noOverpay.spends.toMutableList()
        spends.add(
            PaymentSpend(
                currencyId = chosen!!.first.currencyId,
                source = chosen!!.first.source,
                units = chosen!!.second
            )
        )
        val collected = noOverpay.collected.add(remaining).add(chosen!!.third)
        return CollectResult(spends = mergeSpends(spends), collected = collected)
    }

    private fun collectExact(buckets: List<FundBucket>, target: BigInteger): CollectResult? {
        val result = collectNoOverpayBestEffort(buckets, target)
        if (result.collected == target) return result
        return null
    }

    private fun collectExactPreferred(
        buckets: List<FundBucket>,
        target: BigInteger,
        preferredCurrencyId: String,
    ): CollectResult? {
        val result = collectNoOverpayByOrder(orderBucketsWithPreferredCurrency(buckets, preferredCurrencyId), target)
        if (result.collected == target) return result
        return null
    }

    private fun collectNoOverpayBestEffort(buckets: List<FundBucket>, target: BigInteger): CollectResult {
        val descending = collectNoOverpayByOrder(
            buckets.sortedWith(compareByDescending<FundBucket> { it.atomicPerUnit }.thenBy { it.currencyId }),
            target
        )
        val ascending = collectNoOverpayByOrder(
            buckets.sortedWith(compareBy<FundBucket> { it.atomicPerUnit }.thenBy { it.currencyId }),
            target
        )

        if (descending.collected > ascending.collected) return descending
        if (ascending.collected > descending.collected) return ascending
        if (descending.totalUnitsSpent <= ascending.totalUnitsSpent) return descending
        return ascending
    }

    private fun collectNoOverpayByOrder(buckets: List<FundBucket>, target: BigInteger): CollectResult {
        var remaining = target
        val spends = mutableListOf<PaymentSpend>()
        var collected = BigInteger.ZERO
        var totalUnitsSpent = 0L

        for (bucket in buckets) {
            if (remaining <= BigInteger.ZERO) break
            val maxByTarget = remaining.divide(bucket.atomicPerUnit).toLongSafe()
            if (maxByTarget <= 0L) continue
            val take = minOf(bucket.availableUnits, maxByTarget)
            if (take <= 0L) continue

            val paid = bucket.atomicPerUnit.multiply(BigInteger.valueOf(take))
            remaining = remaining.subtract(paid)
            collected = collected.add(paid)
            totalUnitsSpent += take
            spends.add(PaymentSpend(bucket.currencyId, bucket.source, take))
        }

        return CollectResult(
            spends = mergeSpends(spends),
            collected = collected,
            totalUnitsSpent = totalUnitsSpent
        )
    }

    private fun buildInventoryCandidates(
        inventoryBuckets: List<FundBucket>,
        requiredAtomic: BigInteger,
        priceAtomic: BigInteger,
        hasBankChange: Boolean,
    ): List<CollectResult> {
        if (inventoryBuckets.isEmpty()) {
            return listOf(CollectResult(mutableListOf(), BigInteger.ZERO, 0L))
        }

        val baseline = collectNoOverpayBestEffort(inventoryBuckets, requiredAtomic)
        if (!hasBankChange || baseline.spends.isEmpty()) {
            return listOf(baseline)
        }

        val baselineUnits = baseline.spends.associate { it.currencyId to it.units }.toMutableMap()
        val candidates = linkedMapOf<String, CollectResult>()

        fun snapshot(currentUnits: Map<String, Long>, currentCollected: BigInteger) {
            val spends = inventoryBuckets.asSequence()
                .mapNotNull { bucket ->
                    val units = currentUnits[bucket.currencyId] ?: 0L
                    if (units <= 0L) null else PaymentSpend(bucket.currencyId, PaymentSource.INVENTORY, units)
                }
                .toMutableList()
            val result = CollectResult(
                spends = spends,
                collected = currentCollected,
                totalUnitsSpent = currentUnits.values.sum()
            )
            val key = spends.joinToString("|") { "${it.currencyId}:${it.units}" }
            candidates.putIfAbsent(key, result)
        }

        snapshot(baselineUnits, baseline.collected)

        // If the greedy inventory spend leaves an unusable remainder, progressively trim the
        // lowest-value inventory denominations until the bank can settle the rest cleanly.
        val currentUnits = baselineUnits.toMutableMap()
        var currentCollected = baseline.collected
        val removalOrder = inventoryBuckets
            .filter { (currentUnits[it.currencyId] ?: 0L) > 0L && it.atomicPerUnit.mod(priceAtomic) != BigInteger.ZERO }
            .sortedWith(compareBy<FundBucket> { it.atomicPerUnit }.thenBy { it.currencyId })

        for (bucket in removalOrder) {
            while ((currentUnits[bucket.currencyId] ?: 0L) > 0L) {
                val updatedUnits = (currentUnits[bucket.currencyId] ?: 0L) - 1L
                if (updatedUnits <= 0L) currentUnits.remove(bucket.currencyId)
                else currentUnits[bucket.currencyId] = updatedUnits
                currentCollected = currentCollected.subtract(bucket.atomicPerUnit)
                snapshot(currentUnits, currentCollected)
            }
        }

        return candidates.values.sortedWith(
            compareByDescending<CollectResult> { it.collected }
                .thenBy { it.totalUnitsSpent }
        )
    }

    private fun mergeSpends(spends: List<PaymentSpend>): MutableList<PaymentSpend> {
        val merged = linkedMapOf<String, PaymentSpend>()
        for (spend in spends) {
            val key = "${spend.currencyId}:${spend.source.name}"
            val existing = merged[key]
            if (existing == null) {
                merged[key] = spend
            } else {
                merged[key] = existing.copy(units = existing.units + spend.units)
            }
        }
        return merged.values.toMutableList()
    }

    private fun orderBucketsWithPreferredCurrency(
        buckets: List<FundBucket>,
        preferredCurrencyId: String,
    ): List<FundBucket> {
        return buckets.sortedWith(
            compareBy<FundBucket> { it.currencyId != preferredCurrencyId }
                .thenByDescending { it.atomicPerUnit }
                .thenBy { it.currencyId }
        )
    }

    private fun ceilDiv(a: BigInteger, b: BigInteger): Long {
        if (a <= BigInteger.ZERO) return 0L
        val div = a.divide(b)
        val rem = a.mod(b)
        val rounded = if (rem == BigInteger.ZERO) div else div.add(BigInteger.ONE)
        return rounded.toLongSafe()
    }

    private fun BigInteger.toLongSafe(): Long {
        return try {
            this.longValueExact()
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
    }

    private fun resolveStoredValue(stack: ItemStack): ShopPrice? {
        val itemMeta = stack.itemMeta ?: return null
        val pdc = itemMeta.persistentDataContainer
        val currencyId = pdc.get(storeCurrencyKey, PersistentDataType.STRING)?.takeIf { it.isNotBlank() } ?: return null
        val amount = pdc.get(storeAmountKey, PersistentDataType.STRING)?.toIntOrNull() ?: return null
        if (amount <= 0) return null
        return ShopPrice(currencyId = currencyId, amount = amount)
    }

    private fun hasExpirySellBlock(stack: ItemStack): Boolean {
        val pdc = stack.itemMeta?.persistentDataContainer ?: return false
        return pdc.has(expiresAtKey, PersistentDataType.LONG) ||
            pdc.has(expiresAtKey, PersistentDataType.INTEGER) ||
            pdc.has(expiresAtKey, PersistentDataType.STRING)
    }

    internal fun calculateSellUnitsPerItem(baseUnits: Long, stack: ItemStack): Long {
        if (baseUnits <= 0L) return 0L

        val maxDurability = stack.type.maxDurability.toLong()
        if (maxDurability <= 0L) return baseUnits

        val damageable = stack.itemMeta as? Damageable ?: return baseUnits
        return calculateDurabilityAdjustedUnits(baseUnits, maxDurability, damageable.damage.toLong())
    }

    internal fun calculateDurabilityAdjustedUnits(baseUnits: Long, maxDurability: Long, damage: Long): Long {
        if (baseUnits <= 0L) return 0L
        if (maxDurability <= 0L) return baseUnits

        val clampedDamage = damage.coerceIn(0L, maxDurability)
        val remainingDurability = maxDurability - clampedDamage
        return (baseUnits * remainingDurability) / maxDurability
    }

    internal data class FundBucket(
        val currencyId: String,
        val source: PaymentSource,
        val availableUnits: Long,
        val atomicPerUnit: BigInteger,
    )

    private data class CollectResult(
        val spends: MutableList<PaymentSpend>,
        val collected: BigInteger,
        val totalUnitsSpent: Long = spends.sumOf { it.units },
    )

    internal data class PaymentPlan(
        val spends: MutableList<PaymentSpend>,
        val changeUnits: Long,
    )

    internal data class PaymentSpend(
        val currencyId: String,
        val source: PaymentSource,
        val units: Long,
    )
}
