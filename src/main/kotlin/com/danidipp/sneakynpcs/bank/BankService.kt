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
        val currency = currency(coin.currencyId) ?: return misconfigured()
        val itemId = currency.itemId ?: return misconfigured()
        val variable = currency.variableId ?: return misconfigured()
        val amount = minOf(limit, carried(player, itemId))
        if (amount <= 0L) return BankOutcome.failed("You don't have any ${coin.plural} on you.")

        val loan = loan(player, coin)
        val split = BankRules.depositSplit(amount, loan)
        val balance = balances.getBankCurrencyUnits(player, currency)
        if (!BankRules.fits(balance, split.credited, Variables.maxNumber(player, variable))) {
            return BankOutcome.failed("Your bank account can't hold that many ${coin.plural}.")
        }
        if (!balances.removeItems(player, itemId, amount)) return misconfigured()
        if (split.repaid > 0L) Variables.setNumber(player, coin.loanVariable, (loan - split.repaid).toDouble())
        balances.setBankCurrencyUnits(player, currency, balance + split.credited)
        logRemoved(player, currency.itemMagicItem?.itemStack, amount)

        if (split.repaid <= 0L) return BankOutcome.done()
        val left = loan - split.repaid
        return BankOutcome.done(
            if (left <= 0L) "Your ${coin.name} loan is paid off!"
            else "You paid ${amount(split.repaid)} ${coin.plural} towards your loan. ${amount(left)} left."
        )
    }

    fun withdrawCoins(player: Player, coin: BankCoin, limit: Long): BankOutcome {
        val currency = currency(coin.currencyId) ?: return misconfigured()
        val template = currency.itemMagicItem?.itemStack ?: return misconfigured()
        val balance = balances.getBankCurrencyUnits(player, currency)
        if (balance <= 0L) return BankOutcome.failed("You don't have any ${coin.plural} in the bank.")
        val amount = balances.fitCount(player.inventory, template, minOf(limit, balance))
        if (amount <= 0L) return BankOutcome.failed("Your inventory is full.")

        balances.setBankCurrencyUnits(player, currency, balance - amount)
        if (!balances.addItems(player, template, amount)) {
            balances.setBankCurrencyUnits(player, currency, balance)
            return misconfigured()
        }
        plugin.inventoryTransactionLogger.log(player, addedItems = InventoryAuditItems.split(template, amount))
        return BankOutcome.done()
    }

    fun depositNotes(player: Player, coin: BankCoin, note: BankNote, limit: Long): BankOutcome {
        val count = minOf(limit, carried(player, note.itemId))
        if (count <= 0L) return BankOutcome.failed("You don't have a ${note.label} ${coin.name} note.")
        val credited = depositNoteStacks(player, coin, note, count)
            ?: return BankOutcome.failed("Your bank account can't hold that many ${coin.plural}.")
        return BankOutcome.done("You deposited ${amount(credited)} ${coin.plural} into your bank account!")
    }

    /** Deposits every bank note the player carries; coins too when [includeCoins]. */
    fun depositEverything(player: Player, includeCoins: Boolean): BankOutcome {
        val credited = linkedMapOf<BankCoin, Long>()
        val repaid = linkedMapOf<BankCoin, Long>()
        val full = mutableListOf<BankCoin>()

        for (coin in BankRules.COINS) {
            if (includeCoins) {
                val currency = currency(coin.currencyId) ?: return misconfigured()
                val itemId = currency.itemId ?: return misconfigured()
                val carried = carried(player, itemId)
                if (carried > 0L) {
                    val loanBefore = loan(player, coin)
                    val balanceBefore = balances.getBankCurrencyUnits(player, currency)
                    val outcome = depositCoins(player, coin, carried)
                    if (outcome.ok) {
                        repaid.merge(coin, loanBefore - loan(player, coin), Long::plus)
                        credited.merge(coin, balances.getBankCurrencyUnits(player, currency) - balanceBefore, Long::plus)
                    } else {
                        full += coin
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
        val fullNote = full.distinct().takeIf { it.isNotEmpty() }
            ?.let { coins -> " Your bank account couldn't hold all your ${coins.joinToString(", ") { it.plural }}." }
            .orEmpty()
        if (parts.isEmpty()) {
            if (fullNote.isNotEmpty()) return BankOutcome.failed(fullNote.trim())
            return BankOutcome.failed(if (includeCoins) "You have no coins or bank notes to deposit." else "You have no bank notes to deposit.")
        }
        return BankOutcome.done("You deposited ${parts.joinToString(", ")}.$fullNote")
    }

    fun withdrawNotes(player: Player, coin: BankCoin, note: BankNote, limit: Long, gate: BankGate?): BankOutcome {
        if (!passes(player, gate)) return BankOutcome.failed(gateMessage(gate!!))
        val currency = currency(coin.currencyId) ?: return misconfigured()
        val template = MagicItems.getMagicItemByInternalName(note.itemId)?.itemStack ?: return misconfigured()
        val balance = balances.getBankCurrencyUnits(player, currency)
        if (balance < note.value) {
            return BankOutcome.failed("You need ${amount(note.value)} ${coin.plural} in the bank for that note.")
        }
        val space = balances.fitCount(player.inventory, template, limit)
        val count = BankRules.noteWithdrawCount(limit, balance, note.value, space)
        if (count <= 0L) return BankOutcome.failed("Your inventory is full.")

        balances.setBankCurrencyUnits(player, currency, balance - count * note.value)
        if (!balances.addItems(player, template, count)) {
            balances.setBankCurrencyUnits(player, currency, balance)
            return misconfigured()
        }
        plugin.inventoryTransactionLogger.log(player, addedItems = InventoryAuditItems.split(template, count))
        return BankOutcome.done()
    }

    fun takeLoan(player: Player, coin: BankCoin, gate: BankGate?): BankOutcome {
        if (!passes(player, gate)) return BankOutcome.failed(gateMessage(gate!!))
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
        if (!passes(player, gate)) return BankOutcome.failed(gateMessage(gate!!))
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
