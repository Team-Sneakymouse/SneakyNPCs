package com.danidipp.sneakynpcs.shop

import com.sneakyhorses.api.PrincessDebtApi
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.math.BigInteger

/**
 * Pony credit cards at NPC shops (Rob, 2026-11-27). SneakyHorses owns the cards and the princess debt ledger; a shop
 * purchase priced in the gold family (penny, silver, gold) goes on a carried card first, the way an auction bid does,
 * and coins and bank only pay when no card has room. Debt is kept in whole gold, so a price that isn't whole gold
 * rounds up. Optional: without SneakyHorses (or its card module) nothing is charged and shops pay as before.
 */
interface CreditCardPayments {
    /** Charges [gold] to a card [player] carries; the card id, or null when none fits (nothing changed). */
    fun charge(player: Player, gold: Long): String?

    /** Takes a [charge] back. */
    fun refund(player: Player, cardId: String, gold: Long)

    /** The card's name for messages. */
    fun cardName(cardId: String): String

    companion object {
        /** The currency whose units the ledger counts. */
        const val GOLD = "gold"

        /**
         * What a price of [requiredAtomic] (atomic units of [priceCurrencyId]) comes to in whole gold, rounded up;
         * null when the price isn't in the gold family.
         */
        fun goldFor(currencies: CurrencyLookup, priceCurrencyId: String, requiredAtomic: BigInteger): Long? {
            if (!currencies.isConvertible(priceCurrencyId, GOLD)) return null
            val goldAtomic = currencies.getAtomicValue(GOLD)?.takeIf { it > BigInteger.ZERO } ?: return null
            if (requiredAtomic <= BigInteger.ZERO) return null
            val (q, r) = requiredAtomic.divideAndRemainder(goldAtomic)
            val gold = if (r.signum() == 0) q else q.add(BigInteger.ONE)
            return if (gold.bitLength() < 63) gold.toLong() else null
        }
    }
}

/** No cards (tests, or SneakyHorses missing). */
object NoCreditCards : CreditCardPayments {
    override fun charge(player: Player, gold: Long): String? = null
    override fun refund(player: Player, cardId: String, gold: Long) = Unit
    override fun cardName(cardId: String): String = cardId
}

/**
 * SneakyHorses' `PrincessDebtApi` (optional dependency, joined to the classpath in paper-plugin.yml). Its classes are
 * only touched while SneakyHorses is enabled, so a server without it never loads them.
 */
class SneakyHorsesCreditCards : CreditCardPayments {
    private fun up(): Boolean = Bukkit.getPluginManager().isPluginEnabled("SneakyHorses")

    override fun charge(player: Player, gold: Long): String? =
        if (up()) PrincessDebtApi.charge(player, gold) else null

    override fun refund(player: Player, cardId: String, gold: Long) {
        if (up()) PrincessDebtApi.refund(player, cardId, gold)
    }

    override fun cardName(cardId: String): String =
        (if (up()) PrincessDebtApi.cardName(cardId) else null) ?: cardId
}
