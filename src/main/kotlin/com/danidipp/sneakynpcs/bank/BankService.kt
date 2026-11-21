package com.danidipp.sneakynpcs.bank

import com.danidipp.sneakynpcs.InventoryAuditItems
import com.danidipp.sneakynpcs.SneakyNPCs
import com.danidipp.sneakynpcs.Variables
import com.danidipp.sneakynpcs.menus.formatBankDisplayAmount
import com.danidipp.sneakynpcs.shop.CurrencyDefinition
import com.sneakyitems.util.magicitems.MagicItems
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** What a bank action did: [message] is shown to the player, green on success and red on failure. */
data class BankOutcome(val ok: Boolean, val message: String? = null) {
    companion object {
        fun done(message: String? = null) = BankOutcome(true, message)
        fun failed(message: String) = BankOutcome(false, message)
    }
}

/**
 * Moves money between a player's inventory and their bank balances (SneakyVariables), following [BankRules].
 * Every method runs on the main thread.
 */
class BankService(private val plugin: SneakyNPCs) {
    private val balances get() = plugin.balanceService

    fun currency(currencyId: String): CurrencyDefinition? = plugin.currencyGraphService.getCurrency(currencyId)

    fun balance(player: Player, currencyId: String): Long {
        val currency = currency(currencyId) ?: return 0L
        return balances.getBankCurrencyUnits(player, currency)
    }

    /** What [display] shows for [player], or null when its variable isn't defined and the slot stays empty. */
    fun displayBalance(player: Player, display: BankDisplayBalance): Long? {
        if (!Variables.defined(display.variable)) return null
        return Variables.getNumber(player, display.variable).toLong().coerceAtLeast(0L)
    }

    fun loan(player: Player, coin: BankCoin): Long =
        Variables.getNumber(player, coin.loanVariable).toLong().coerceAtLeast(0L)

    fun carried(player: Player, itemId: String): Long = balances.countItems(player, itemId)

    fun exchangeRate(exchange: BankExchange): Long? {
        val from = currency(exchange.from.currencyId) ?: return null
        if (from.exchangeToCurrency != exchange.to.currencyId) return null
        return from.exchangeRate?.toLong()?.takeIf { it > 0L }
    }

    fun passes(player: Player, gate: BankGate?): Boolean =
        gate == null || Variables.getNumber(player, gate.variable) != 0.0

    fun gateMessage(gate: BankGate): String =
        if (gate.skillName != null) "You need the ${gate.skillName} trading skill for that."
        else "You haven't unlocked that yet."

    fun depositCoins(player: Player, coin: BankCoin, limit: Long): BankOutcome {
        return when (val result = depositCoinStacks(player, coin, limit)) {
            is CoinDeposit.Refused -> result.outcome
            is CoinDeposit.Deposited -> when {
                result.split.repaid <= 0L -> BankOutcome.done()
                result.loanLeft <= 0L -> BankOutcome.done("Your ${coin.name} loan is paid off!")
                else -> BankOutcome.done(
                    "You paid ${amount(result.split.repaid)} ${coin.plural} towards your loan. ${amount(result.loanLeft)} left."
                )
            }
        }
    }

    fun withdrawCoins(player: Player, coin: BankCoin, limit: Long): BankOutcome {
        val currency = currency(coin.currencyId) ?: return misconfigured()
        val template = currency.itemMagicItem?.itemStack ?: return misconfigured()
        val balance = balances.getBankCurrencyUnits(player, currency)
        if (balance <= 0L) return BankOutcome.failed("You don't have any ${coin.plural} in the bank.")
        val amount = balances.fitCount(player.inventory, template, minOf(limit, balance))
        return giveFromBalance(player, currency, balance, template, amount, unitValue = 1L)
    }

    fun depositNotes(player: Player, coin: BankCoin, note: BankNote, limit: Long): BankOutcome {
        val count = minOf(limit, carried(player, note.itemId))
        if (count <= 0L) return BankOutcome.failed("You don't have a ${note.label} ${coin.name} note.")
        val credited = depositNoteStacks(player, coin, note, count) ?: return accountFull(coin)
        return BankOutcome.done("You deposited ${amount(credited)} ${coin.plural} into your bank account!")
    }

    /** Deposits every bank note the player carries; coins too when [includeCoins]. */
    fun depositEverything(player: Player, includeCoins: Boolean): BankOutcome {
        val credited = linkedMapOf<BankCoin, Long>()
        val repaid = linkedMapOf<BankCoin, Long>()
        val full = mutableListOf<BankCoin>()

        for (coin in BankRules.COINS) {
            if (includeCoins) {
                val itemId = currency(coin.currencyId)?.itemId ?: return misconfigured()
                if (carried(player, itemId) > 0L) {
                    when (val result = depositCoinStacks(player, coin, Long.MAX_VALUE)) {
                        is CoinDeposit.Deposited -> {
                            repaid.merge(coin, result.split.repaid, Long::plus)
                            credited.merge(coin, result.split.credited, Long::plus)
                        }
                        is CoinDeposit.Refused -> full += coin
                    }
                }
            }
            for (note in coin.notes) {
                val count = carried(player, note.itemId)
                if (count <= 0L) continue
                val value = depositNoteStacks(player, coin, note, count)
                if (value == null) full += coin else credited.merge(coin, value, Long::plus)
            }
        }

        val parts = credited.filterValues { it > 0L }.map { (coin, value) -> "${amount(value)} ${coin.plural}" } +
            repaid.filterValues { it > 0L }.map { (coin, value) -> "${amount(value)} ${coin.plural} to your loan" }
        val accountFullMessage = full.distinct().takeIf { it.isNotEmpty() }
            ?.let { coins -> " Your bank account couldn't hold all your ${coins.joinToString(", ") { it.plural }}." }
            .orEmpty()
        if (parts.isEmpty()) {
            if (accountFullMessage.isNotEmpty()) return BankOutcome.failed(accountFullMessage.trim())
            return BankOutcome.failed(if (includeCoins) "You have no coins or bank notes to deposit." else "You have no bank notes to deposit.")
        }
        return BankOutcome.done("You deposited ${parts.joinToString(", ")}.$accountFullMessage")
    }

    fun withdrawNotes(player: Player, coin: BankCoin, note: BankNote, limit: Long, gate: BankGate?): BankOutcome {
        gateFailure(player, gate)?.let { return it }
        val currency = currency(coin.currencyId) ?: return misconfigured()
        val template = MagicItems.getMagicItemByInternalName(note.itemId)?.itemStack ?: return misconfigured()
        val balance = balances.getBankCurrencyUnits(player, currency)
        if (balance < note.value) {
            return BankOutcome.failed("You need ${amount(note.value)} ${coin.plural} in the bank for that note.")
        }
        val space = balances.fitCount(player.inventory, template, limit)
        val count = BankRules.noteWithdrawCount(limit, balance, note.value, space)
        return giveFromBalance(player, currency, balance, template, count, unitValue = note.value)
    }

    fun takeLoan(player: Player, coin: BankCoin, gate: BankGate?): BankOutcome {
        gateFailure(player, gate)?.let { return it }
        val currency = currency(coin.currencyId) ?: return misconfigured()
        val variable = currency.variableId ?: return misconfigured()
        if (loan(player, coin) > 0L) {
            return BankOutcome.failed("You already have a ${coin.name} loan. Deposit ${coin.plural} to pay it off.")
        }
        if (carried(player, BankRules.LOAN_COST_ITEM) < BankRules.LOAN_COST_AMOUNT) {
            return BankOutcome.failed("A loan costs ${BankRules.LOAN_COST_AMOUNT} Holy Writs.")
        }
        val balance = balances.getBankCurrencyUnits(player, currency)
        if (!BankRules.fits(balance, coin.loanAmount, Variables.maxNumber(player, variable)) ||
            !BankRules.fits(0L, coin.loanAmount, Variables.maxNumber(player, coin.loanVariable))
        ) {
            return BankOutcome.failed("Your bank account can't hold that loan.")
        }
        if (!balances.removeItems(player, BankRules.LOAN_COST_ITEM, BankRules.LOAN_COST_AMOUNT.toLong())) return misconfigured()
        balances.setBankCurrencyUnits(player, currency, balance + coin.loanAmount)
        Variables.setNumber(player, coin.loanVariable, coin.loanAmount.toDouble())
        logRemoved(player, MagicItems.getMagicItemByInternalName(BankRules.LOAN_COST_ITEM)?.itemStack, BankRules.LOAN_COST_AMOUNT.toLong())
        return BankOutcome.done(
            "You took out a ${amount(coin.loanAmount)} ${coin.name} loan. Depositing ${coin.plural} pays it off first."
        )
    }

    /** Exchanges banked coins for [quantity] of the next coin up, or as many as possible when [quantity] is null. */
    fun exchange(player: Player, exchange: BankExchange, quantity: Long?, gate: BankGate?): BankOutcome {
        gateFailure(player, gate)?.let { return it }
        val from = currency(exchange.from.currencyId) ?: return misconfigured()
        val to = currency(exchange.to.currencyId) ?: return misconfigured()
        val toVariable = to.variableId ?: return misconfigured()
        val rate = exchangeRate(exchange) ?: return misconfigured()
        val fromBalance = balances.getBankCurrencyUnits(player, from)
        val toBalance = balances.getBankCurrencyUnits(player, to)
        val room = BankRules.room(toBalance, Variables.maxNumber(player, toVariable))

        val amount = quantity ?: BankRules.maxExchange(fromBalance, rate, room)
        val cost = amount * rate
        when {
            amount > room || room <= 0L -> return BankOutcome.failed("You would exceed the maximum ${exchange.to.name} value if you did that.")
            amount <= 0L || fromBalance < cost -> return BankOutcome.failed(
                "You need ${amount(maxOf(cost, rate))} ${exchange.from.plural} in the bank for that."
            )
        }
        balances.setBankCurrencyUnits(player, from, fromBalance - cost)
        balances.setBankCurrencyUnits(player, to, toBalance + amount)
        return BankOutcome.done()
    }

    fun feedback(player: Player, outcome: BankOutcome) {
        player.playSound(player.location, if (outcome.ok) "lom:buy" else "lom:fail_wrong", 1f, 1f)
        val message = outcome.message ?: return
        player.sendMessage(Component.text(message, if (outcome.ok) NamedTextColor.GREEN else NamedTextColor.RED))
    }

    private sealed interface CoinDeposit {
        data class Deposited(val split: DepositSplit, val loanLeft: Long) : CoinDeposit
        data class Refused(val outcome: BankOutcome) : CoinDeposit
    }

    /** Deposits up to [limit] carried coins, paying off the coin's loan first. */
    private fun depositCoinStacks(player: Player, coin: BankCoin, limit: Long): CoinDeposit {
        val currency = currency(coin.currencyId) ?: return CoinDeposit.Refused(misconfigured())
        val itemId = currency.itemId ?: return CoinDeposit.Refused(misconfigured())
        val variable = currency.variableId ?: return CoinDeposit.Refused(misconfigured())
        val amount = minOf(limit, carried(player, itemId))
        if (amount <= 0L) return CoinDeposit.Refused(BankOutcome.failed("You don't have any ${coin.plural} on you."))

        val loan = loan(player, coin)
        val split = BankRules.depositSplit(amount, loan)
        val balance = balances.getBankCurrencyUnits(player, currency)
        if (!BankRules.fits(balance, split.credited, Variables.maxNumber(player, variable))) {
            return CoinDeposit.Refused(accountFull(coin))
        }
        if (!balances.removeItems(player, itemId, amount)) return CoinDeposit.Refused(misconfigured())
        if (split.repaid > 0L) Variables.setNumber(player, coin.loanVariable, (loan - split.repaid).toDouble())
        balances.setBankCurrencyUnits(player, currency, balance + split.credited)
        logRemoved(player, currency.itemMagicItem?.itemStack, amount)
        return CoinDeposit.Deposited(split, loanLeft = loan - split.repaid)
    }

    /**
     * Gives up to [count] copies of [template] and takes [unitValue] from the balance for each one that arrived.
     * Items are given first, so a player is never charged for items that didn't fit.
     */
    private fun giveFromBalance(
        player: Player,
        currency: CurrencyDefinition,
        balance: Long,
        template: ItemStack,
        count: Long,
        unitValue: Long,
    ): BankOutcome {
        if (count <= 0L) return BankOutcome.failed("Your inventory is full.")
        val delivered = balances.giveItems(player, template, count)
        if (delivered <= 0L) return BankOutcome.failed("Your inventory is full.")
        balances.setBankCurrencyUnits(player, currency, balance - delivered * unitValue)
        plugin.inventoryTransactionLogger.log(player, addedItems = InventoryAuditItems.split(template, delivered))
        return BankOutcome.done()
    }

    private fun gateFailure(player: Player, gate: BankGate?): BankOutcome? =
        if (gate == null || passes(player, gate)) null else BankOutcome.failed(gateMessage(gate))

    private fun accountFull(coin: BankCoin) = BankOutcome.failed("Your bank account can't hold that many ${coin.plural}.")

    /** Deposits [count] notes and returns the value credited, or null if the balance can't hold it. */
    private fun depositNoteStacks(player: Player, coin: BankCoin, note: BankNote, count: Long): Long? {
        val currency = currency(coin.currencyId) ?: return null
        val variable = currency.variableId ?: return null
        val credited = count * note.value
        val balance = balances.getBankCurrencyUnits(player, currency)
        if (!BankRules.fits(balance, credited, Variables.maxNumber(player, variable))) return null
        if (!balances.removeItems(player, note.itemId, count)) return null
        balances.setBankCurrencyUnits(player, currency, balance + credited)
        logRemoved(player, MagicItems.getMagicItemByInternalName(note.itemId)?.itemStack, count)
        return credited
    }

    private fun logRemoved(player: Player, template: ItemStack?, amount: Long) {
        if (template == null) return
        plugin.inventoryTransactionLogger.log(player, removedItems = InventoryAuditItems.split(template, amount))
    }

    private fun misconfigured() = BankOutcome.failed("The bank is not configured correctly. Please tell Dani.")

    private fun amount(value: Long) = formatBankDisplayAmount(value)
}
