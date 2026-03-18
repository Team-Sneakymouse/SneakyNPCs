package com.danidipp.sneakynpcs.shop

import com.danidipp.sneakynpcs.NPC
import com.danidipp.sneakynpcs.NpcWalletState
import com.danidipp.sneakynpcs.PlayerData
import java.math.BigInteger

class NpcWalletService(
    private val currencyLookup: CurrencyLookup,
) {
    sealed class SpendResult {
        object Success : SpendResult()
        data class Failure(val message: String) : SpendResult()
    }

    fun spendForSale(
        playerData: PlayerData,
        npc: NPC,
        saleCurrencyId: String,
        saleUnits: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): SpendResult {
        if (saleUnits <= 0L) return SpendResult.Failure("Invalid sale value.")

        val nativeCurrencyId = npc.wallet.currencyId
        if (!currencyLookup.isConvertible(nativeCurrencyId, saleCurrencyId)) {
            return SpendResult.Failure("This NPC cannot pay in that currency.")
        }

        val saleAtomic = currencyLookup.getAtomicValue(saleCurrencyId)
            ?.multiply(BigInteger.valueOf(saleUnits))
            ?: return SpendResult.Failure("This item has an invalid currency graph.")

        val currentState = getOrCreateRestockedWallet(playerData, npc, nowMillis)
        val component = currencyLookup.getConvertibleCurrencies(nativeCurrencyId)
        val totalAtomic = calculateTotalAtomic(currentState, component)
        if (totalAtomic < saleAtomic) {
            return SpendResult.Failure("This NPC does not have enough money.")
        }

        val debitedState = currentState.copy(
            balances = normalizeBalances(
                nativeCurrencyId = nativeCurrencyId,
                totalAtomic = totalAtomic.subtract(saleAtomic)
            ).toMutableMap()
        )
        playerData.setNpcWalletState(npc.id, debitedState)
        return SpendResult.Success
    }

    fun getOrCreateRestockedWallet(
        playerData: PlayerData,
        npc: NPC,
        nowMillis: Long = System.currentTimeMillis(),
    ): NpcWalletState {
        val nativeCurrencyId = npc.wallet.currencyId
        val maxAtomic = nativeAtomicValue(nativeCurrencyId).multiply(BigInteger.valueOf(npc.wallet.max))
        val existingState = playerData.getNpcWalletState(npc.id)
        val startingState = when {
            existingState == null || existingState.nativeCurrencyId != nativeCurrencyId -> NpcWalletState(
                nativeCurrencyId = nativeCurrencyId,
                lastRestockAtEpochMillis = nowMillis,
                balances = mutableMapOf(nativeCurrencyId to npc.wallet.max)
            )
            else -> existingState
        }

        val normalizedState = restockAndNormalize(startingState, npc, nowMillis, maxAtomic)
        if (existingState != normalizedState) {
            playerData.setNpcWalletState(npc.id, normalizedState)
        }
        return normalizedState
    }

    fun setNativeWalletAmount(
        playerData: PlayerData,
        npc: NPC,
        nativeAmount: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): NpcWalletState {
        val clampedAmount = nativeAmount.coerceIn(0L, npc.wallet.max)
        val state = NpcWalletState(
            nativeCurrencyId = npc.wallet.currencyId,
            lastRestockAtEpochMillis = nowMillis,
            balances = if (clampedAmount > 0L) {
                mutableMapOf(npc.wallet.currencyId to clampedAmount)
            } else {
                mutableMapOf()
            }
        )
        playerData.setNpcWalletState(npc.id, state)
        return state
    }

    private fun restockAndNormalize(
        state: NpcWalletState,
        npc: NPC,
        nowMillis: Long,
        maxAtomic: BigInteger,
    ): NpcWalletState {
        val nativeCurrencyId = npc.wallet.currencyId
        var totalAtomic = calculateTotalAtomic(state, currencyLookup.getConvertibleCurrencies(nativeCurrencyId))
        var lastRestockAt = state.lastRestockAtEpochMillis

        if (totalAtomic >= maxAtomic) {
            totalAtomic = maxAtomic
            lastRestockAt = nowMillis
        } else if (npc.wallet.restockAmount > 0L && npc.wallet.restockIntervalSeconds > 0L) {
            val intervalMillis = npc.wallet.restockIntervalSeconds * 1000L
            val elapsedMillis = (nowMillis - lastRestockAt).coerceAtLeast(0L)
            val elapsedIntervals = elapsedMillis / intervalMillis
            if (elapsedIntervals > 0L) {
                val addedAtomic = nativeAtomicValue(nativeCurrencyId)
                    .multiply(BigInteger.valueOf(npc.wallet.restockAmount))
                    .multiply(BigInteger.valueOf(elapsedIntervals))
                totalAtomic = totalAtomic.add(addedAtomic).min(maxAtomic)
                lastRestockAt = if (totalAtomic >= maxAtomic) {
                    nowMillis
                } else {
                    lastRestockAt + elapsedIntervals * intervalMillis
                }
            }
        }

        return NpcWalletState(
            nativeCurrencyId = nativeCurrencyId,
            lastRestockAtEpochMillis = lastRestockAt,
            balances = normalizeBalances(nativeCurrencyId, totalAtomic).toMutableMap()
        )
    }

    private fun calculateTotalAtomic(state: NpcWalletState, component: Set<String>): BigInteger {
        var total = BigInteger.ZERO
        for ((currencyId, units) in state.balances) {
            if (units <= 0L || currencyId !in component) continue
            val atomicValue = currencyLookup.getAtomicValue(currencyId) ?: continue
            total = total.add(atomicValue.multiply(BigInteger.valueOf(units)))
        }
        return total
    }

    private fun normalizeBalances(nativeCurrencyId: String, totalAtomic: BigInteger): Map<String, Long> {
        if (totalAtomic <= BigInteger.ZERO) return emptyMap()

        var remaining = totalAtomic
        val normalized = linkedMapOf<String, Long>()
        for (currency in currencyLookup.getWalletCurrencies(nativeCurrencyId)) {
            val atomicValue = currencyLookup.getAtomicValue(currency.id) ?: continue
            if (atomicValue <= BigInteger.ZERO) continue
            val units = remaining.divide(atomicValue)
            if (units > BigInteger.ZERO) {
                normalized[currency.id] = units.toLongSafe()
                remaining = remaining.mod(atomicValue)
            }
        }
        return normalized
    }

    private fun nativeAtomicValue(nativeCurrencyId: String): BigInteger {
        return currencyLookup.getAtomicValue(nativeCurrencyId)
            ?: error("Validated wallet currency '$nativeCurrencyId' is missing an atomic value")
    }

    private fun BigInteger.toLongSafe(): Long {
        return try {
            longValueExact()
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
    }
}
