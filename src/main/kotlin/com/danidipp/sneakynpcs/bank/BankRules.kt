package com.danidipp.sneakynpcs.bank

/** A bank note: a MagicItem worth [value] of its coin in the bank. */
data class BankNote(
    val itemId: String,
    val value: Long,
    val label: String,
)

/**
 * A coin players can deposit and withdraw at the bank, take a loan in, and withdraw as bank notes.
 * [currencyId] names the currency in currencies.yml, which supplies the coin item and the balance variable.
 */
data class BankCoin(
    val currencyId: String,
    val name: String,
    val plural: String,
    val model: String,
    val noteModel: String,
    val loanModel: String,
    val loanVariable: String,
    val loanAmount: Long,
    val notes: List<BankNote>,
)

/** A balance the bank shows but doesn't let players move. */
data class BankDisplayBalance(
    val currencyId: String,
    val plural: String,
    val model: String,
    val lore: List<String> = emptyList(),
)

/** Turns banked [from] coins into the next coin up, at the exchange rate from currencies.yml. */
data class BankExchange(
    val from: BankCoin,
    val to: BankCoin,
)

/** How a deposit is split between paying off a loan and the bank balance. */
data class DepositSplit(
    val repaid: Long,
    val credited: Long,
)

/**
 * The bank's fixed rules, carried over from the MagicSpells bank (spells-city-bank.yml in MagicSpells-Data).
 */
object BankRules {
    const val STACK = 99L
    const val LOAN_COST_ITEM = "item-holyWrit"
    const val LOAN_COST_AMOUNT = 5
    val EXCHANGE_QUANTITIES = listOf(1L, 32L, 64L, 100L, 1_000L, 10_000L, 100_000L)

    val PENNY = coin("penny", "Penny", "Pennies", "copper", "penny", "bankLoanPenny", 100_000)
    val SILVER = coin("silver", "Silver", "Silver", "silver", "silver", "bankLoanSilver", 10_000)
    val GOLD = coin("gold", "Gold", "Gold", "gold", "gold", "bankLoanGold", 1_000)
    val COINS = listOf(PENNY, SILVER, GOLD)

    val DISPLAY_BALANCES = listOf(
        BankDisplayBalance("diamond", "Diamonds", "lom:coin_diamond"),
        BankDisplayBalance("mythril", "Mythril", "lom:coin_mithril"),
        BankDisplayBalance(
            "dragoncoins", "Dragon Coins", "lom:items/dragoncoin_3d",
            listOf("<gray>You cannot withdraw <gold>Dragon Coins", "<gray>as you are too weak to hold them."),
        ),
    )

    val EXCHANGES = listOf(BankExchange(PENNY, SILVER), BankExchange(SILVER, GOLD))

    private fun coin(
        currencyId: String,
        name: String,
        plural: String,
        coinModel: String,
        noteName: String,
        loanVariable: String,
        loanAmount: Long,
    ) = BankCoin(
        currencyId = currencyId,
        name = name,
        plural = plural,
        model = "lom:coin_$coinModel",
        noteModel = "lom:items/note_$noteName",
        loanModel = "lom:items/loan_$noteName",
        loanVariable = loanVariable,
        loanAmount = loanAmount,
        notes = listOf(
            BankNote("item-${noteName}Note1k", 1_000, "1k"),
            BankNote("item-${noteName}Note10k", 10_000, "10k"),
            BankNote("item-${noteName}Note100k", 100_000, "100k"),
            BankNote("item-${noteName}Note1m", 1_000_000, "1M"),
        ),
    )

    fun noteByItemId(itemId: String): Pair<BankCoin, BankNote>? {
        for (coin in COINS) {
            val note = coin.notes.firstOrNull { it.itemId == itemId } ?: continue
            return coin to note
        }
        return null
    }

    /** Coin deposits pay off an outstanding loan in the same coin before they reach the balance. */
    fun depositSplit(amount: Long, loan: Long): DepositSplit {
        val repaid = minOf(amount, loan.coerceAtLeast(0L)).coerceAtLeast(0L)
        return DepositSplit(repaid = repaid, credited = amount - repaid)
    }

    /** Whether [balance] + [added] stays within [max]; a null max means unbounded. */
    fun fits(balance: Long, added: Long, max: Double?): Boolean =
        max == null || balance.toDouble() + added.toDouble() <= max

    /** How much can still be added to [balance] before reaching [max]. */
    fun room(balance: Long, max: Double?): Long {
        if (max == null || max >= Long.MAX_VALUE.toDouble()) return Long.MAX_VALUE
        return (max.toLong() - balance).coerceAtLeast(0L)
    }

    /** The next (or, with [step] -1, previous) exchange quantity, wrapping around at the ends. */
    fun cycleQuantity(current: Long, step: Int): Long {
        val list = EXCHANGE_QUANTITIES
        val index = list.indexOf(current).takeIf { it >= 0 } ?: return list.first()
        return list[Math.floorMod(index + step, list.size)]
    }

    /** The most coins a exchange at [rate]-to-1 can produce from [sourceBalance] without passing [targetRoom]. */
    fun maxExchange(sourceBalance: Long, rate: Long, targetRoom: Long): Long {
        if (rate <= 0L) return 0L
        return minOf(sourceBalance.coerceAtLeast(0L) / rate, targetRoom.coerceAtLeast(0L))
    }

    /** How many notes worth [value] each can be withdrawn: at most [requested], paid from [balance], fitting in [space]. */
    fun noteWithdrawCount(requested: Long, balance: Long, value: Long, space: Long): Long {
        if (value <= 0L) return 0L
        return minOf(requested, balance.coerceAtLeast(0L) / value, space.coerceAtLeast(0L)).coerceAtLeast(0L)
    }
}
