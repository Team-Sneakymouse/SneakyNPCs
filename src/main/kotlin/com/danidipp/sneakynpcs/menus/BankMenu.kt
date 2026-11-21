package com.danidipp.sneakynpcs.menus

import com.danidipp.sneakynpcs.NPCGui
import com.danidipp.sneakynpcs.PlayerData
import com.danidipp.sneakynpcs.bank.BankCoin
import com.danidipp.sneakynpcs.bank.BankFeature
import com.danidipp.sneakynpcs.bank.BankGate
import com.danidipp.sneakynpcs.bank.BankMenuConfig
import com.danidipp.sneakynpcs.bank.BankOutcome
import com.danidipp.sneakynpcs.bank.BankRules
import com.danidipp.sneakynpcs.bank.BankService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID

private const val BACKGROUND_SLOT = 8
private const val BACK_SLOT = 45
private const val DEPOSIT_ALL_SLOT = 53
private val COIN_SLOTS = listOf(11, 13, 15)
private val DISPLAY_SLOTS = listOf(20, 22, 24)
private const val NOTES_SLOT = 30
private const val LOANS_SLOT = 32
private val LOAN_SLOTS = listOf(11, 13, 15)
private val EXCHANGE_SLOTS = listOf(21, 23)

/** One page of the bank, drawn over the Royal Bank art. Esc and Back return to the previous menu. */
abstract class BankPage(
    protected val config: BankMenuConfig,
    private val background: String,
) : NPCMenu(MenuType.BANK) {
    private val miniMessage = MiniMessage.miniMessage()

    protected fun gate(feature: BankFeature): BankGate? = config.gates[feature]

    protected fun service(gui: NPCGui): BankService = gui.plugin.bankService

    override fun open(gui: NPCGui, player: Player, playerData: PlayerData?) = render(gui, player)

    protected fun render(gui: NPCGui, player: Player) {
        val inv = gui.inventory
        inv.clear()
        inv.setItem(BACKGROUND_SLOT, makeItem(background, hideTooltip = shouldHideTooltip(player)))
        inv.setItem(BACK_SLOT, button("lom:icons/back", "<red>Back"))
        draw(gui, player, service(gui))
    }

    protected abstract fun draw(gui: NPCGui, player: Player, bank: BankService)

    final override fun onClick(gui: NPCGui, event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (event.slot == BACK_SLOT) {
            if (!gui.goBackOneLevel()) gui.close()
            return
        }
        val outcome = click(gui, player, event.slot, event.click) ?: return
        service(gui).feedback(player, outcome)
        if (!gui.isDisposed()) render(gui, player)
    }

    /** Handles a click on [slot]; null when the slot does nothing. */
    protected abstract fun click(gui: NPCGui, player: Player, slot: Int, click: ClickType): BankOutcome?

    protected fun button(model: String, name: String, lore: List<String> = emptyList()): ItemStack =
        makeItem(model).apply {
            editMeta { meta ->
                meta.displayName(text(name))
                meta.lore(lore.map(::text))
            }
        }

    protected fun gateLine(player: Player, bank: BankService, gate: BankGate?, action: String): String? {
        if (gate == null || bank.passes(player, gate)) return null
        val skill = gate.skillName?.let { "the $it trading skill" } ?: "an unlock"
        return "<red>$action needs $skill."
    }

    protected fun amount(value: Long) = formatBankDisplayAmount(value)

    private fun text(input: String): Component =
        Component.empty().decoration(TextDecoration.ITALIC, false).append(miniMessage.deserialize(input))
}

/** The bank's main page: coin balances, other balances, and the way to notes and loans. */
class BankMenu(config: BankMenuConfig) : BankPage(config, "lom:gui/bankui1") {
    private val notesMenu = BankNotesMenu(config)
    private val loansMenu = BankLoansMenu(config)

    /** Where right-clicking a bank note deposits it (see BankNoteListener), or null to turn that off. */
    val noteDepositRegion get() = config.noteDepositRegion

    override fun childMenus(): List<NPCMenu> = listOf(notesMenu, loansMenu)

    override fun draw(gui: NPCGui, player: Player, bank: BankService) {
        val inv = gui.inventory
        for ((slot, coin) in COIN_SLOTS.zip(BankRules.COINS)) inv.setItem(slot, coinItem(player, bank, coin))
        for ((slot, display) in DISPLAY_SLOTS.zip(BankRules.DISPLAY_BALANCES)) {
            val balance = bank.displayBalance(player, display) ?: continue
            inv.setItem(slot, button(
                display.model,
                "<gold>${display.plural}",
                listOf("<gold>${display.plural}: <aqua>${amount(balance)}") + display.lore,
            ))
        }
        inv.setItem(NOTES_SLOT, button("lom:gui/icons/banknote", "<gold>Bank Notes", listOfNotNull(
            "<gray>Withdraw and deposit large amounts",
            "<gray>of money as <gold>Bank Notes<gray>.",
            gateLine(player, bank, gate(BankFeature.NOTES), "Withdrawing notes"),
        )))
        inv.setItem(LOANS_SLOT, button("lom:items/loan_gold", "<gold>Loans and Conversions", listOfNotNull(
            "<gray>Take out and check on your loans",
            "<gray>and convert large amounts of money.",
            gateLine(player, bank, gate(BankFeature.LOANS), "Loans"),
            gateLine(player, bank, gate(BankFeature.EXCHANGES), "Conversions"),
        )))
        inv.setItem(DEPOSIT_ALL_SLOT, button("lom:items/note_gold", "<gold>Deposit Everything", listOf(
            "<gray>Deposits every coin and bank note",
            "<gray>you are carrying.",
        )))
    }

    private fun coinItem(player: Player, bank: BankService, coin: BankCoin): ItemStack {
        val currency = bank.currency(coin.currencyId)
        val loan = bank.loan(player, coin)
        val carried = currency?.itemId?.let { bank.carried(player, it) } ?: 0L
        return button(coin.model, "<gold>${coin.plural}", listOfNotNull(
            "<gold>${coin.plural}: <aqua>${amount(bank.balance(player, coin.currencyId))}",
            if (loan > 0L) "<gold>Loan: <aqua>${amount(loan)}" else null,
            "<gray>On you: <aqua>${amount(carried)}",
            "",
            "<yellow>L-Click<gray>: Deposit <aqua>1",
            "<yellow>R-Click<gray>: Withdraw <aqua>1",
            "<yellow>Shift<gray>: Deposit/withdraw a stack",
            "<yellow>Q<gray>: Deposit all",
            if (loan > 0L) "<gray>Deposits pay off your loan first." else null,
        ))
    }

    override fun click(gui: NPCGui, player: Player, slot: Int, click: ClickType): BankOutcome? {
        val bank = service(gui)
        COIN_SLOTS.indexOf(slot).takeIf { it >= 0 }?.let { index ->
            val coin = BankRules.COINS[index]
            return when (click) {
                ClickType.LEFT -> bank.depositCoins(player, coin, 1)
                ClickType.SHIFT_LEFT -> bank.depositCoins(player, coin, BankRules.STACK)
                ClickType.RIGHT -> bank.withdrawCoins(player, coin, 1)
                ClickType.SHIFT_RIGHT -> bank.withdrawCoins(player, coin, BankRules.STACK)
                ClickType.DROP, ClickType.CONTROL_DROP -> bank.depositCoins(player, coin, Long.MAX_VALUE)
                else -> null
            }
        }
        when (slot) {
            NOTES_SLOT -> gui.pushMenu(notesMenu)
            LOANS_SLOT -> gui.pushMenu(loansMenu)
            DEPOSIT_ALL_SLOT -> return bank.depositEverything(player, includeCoins = true)
        }
        return null
    }
}

/** Withdraw and deposit bank notes: a column per coin, a row per denomination. */
class BankNotesMenu(config: BankMenuConfig) : BankPage(config, "lom:gui/bankui") {
    private val columns = listOf(listOf(11, 20, 29, 38), listOf(13, 22, 31, 40), listOf(15, 24, 33, 42))

    override fun draw(gui: NPCGui, player: Player, bank: BankService) {
        val inv = gui.inventory
        val gateLine = gateLine(player, bank, gate(BankFeature.NOTES), "Withdrawing notes")
        for ((coin, slots) in BankRules.COINS.zip(columns)) {
            val balance = bank.balance(player, coin.currencyId)
            for ((note, slot) in coin.notes.zip(slots)) {
                inv.setItem(slot, button(coin.noteModel, "<green>${coin.name} Bank Note ${note.label}", listOfNotNull(
                    "<gold>${coin.plural}: <aqua>${amount(balance)}",
                    "<gray>On you: <aqua>${amount(bank.carried(player, note.itemId))}",
                    "",
                    "<yellow>L-Click<gray>: Deposit a note",
                    "<yellow>Shift+L-Click<gray>: Deposit all of these notes",
                    "<yellow>R-Click<gray>: Withdraw a <aqua>${amount(note.value)}<gray> ${coin.name} note",
                    "<yellow>Shift+R-Click<gray>: Withdraw up to a stack",
                    gateLine,
                )))
            }
        }
        inv.setItem(DEPOSIT_ALL_SLOT, button("lom:items/note_gold", "<gold>Deposit All Notes", listOf(
            "<gray>Deposits every bank note",
            "<gray>you are carrying.",
        )))
    }

    override fun click(gui: NPCGui, player: Player, slot: Int, click: ClickType): BankOutcome? {
        val bank = service(gui)
        if (slot == DEPOSIT_ALL_SLOT) return bank.depositEverything(player, includeCoins = false)
        for ((coin, slots) in BankRules.COINS.zip(columns)) {
            val note = coin.notes.getOrNull(slots.indexOf(slot)) ?: continue
            val gate = gate(BankFeature.NOTES)
            return when (click) {
                ClickType.LEFT -> bank.depositNotes(player, coin, note, 1)
                ClickType.SHIFT_LEFT -> bank.depositNotes(player, coin, note, Long.MAX_VALUE)
                ClickType.RIGHT -> bank.withdrawNotes(player, coin, note, 1, gate)
                ClickType.SHIFT_RIGHT -> bank.withdrawNotes(player, coin, note, BankRules.STACK, gate)
                else -> null
            }
        }
        return null
    }
}

/** Take out loans, and exchange banked coins for the next coin up ("Convert" in the menu, as before). */
class BankLoansMenu(config: BankMenuConfig) : BankPage(config, "lom:gui/bankui") {
    /** The exchange quantity each player has picked while this page is open. */
    private val quantities = mutableMapOf<UUID, Long>()

    override fun onClose(gui: NPCGui, player: Player) {
        quantities.remove(player.uniqueId)
    }

    override fun draw(gui: NPCGui, player: Player, bank: BankService) {
        val inv = gui.inventory
        val loanGate = gateLine(player, bank, gate(BankFeature.LOANS), "Loans")
        for ((slot, coin) in LOAN_SLOTS.zip(BankRules.COINS)) {
            val loan = bank.loan(player, coin)
            inv.setItem(slot, button(coin.loanModel, "<gold>Loan: ${coin.name}", listOfNotNull(
                "<gold>Loan: <aqua>${amount(loan)}",
                if (loan > 0L) "<gray>Deposit ${coin.plural} to pay it off."
                else "<yellow>L-Click<gray>: Take out a <aqua>${amount(coin.loanAmount)}<gold> ${coin.name}<gray> loan.",
                "<gray>Loans cost <aqua>${BankRules.LOAN_COST_AMOUNT} <green>Holy Writs<gray>.",
                loanGate,
            )))
        }

        val quantity = quantities[player.uniqueId] ?: BankRules.EXCHANGE_QUANTITIES.first()
        val exchangeGate = gateLine(player, bank, gate(BankFeature.EXCHANGES), "Converting")
        for ((slot, exchange) in EXCHANGE_SLOTS.zip(BankRules.EXCHANGES)) {
            val rate = bank.exchangeRate(exchange) ?: continue
            inv.setItem(slot, button(exchange.to.model, "<gold>Convert to ${exchange.to.name}", listOfNotNull(
                "<gold>${exchange.from.plural}: <aqua>${amount(bank.balance(player, exchange.from.currencyId))}",
                "<gold>${exchange.to.plural}: <aqua>${amount(bank.balance(player, exchange.to.currencyId))}",
                "",
                "<yellow>L-Click<gray>: Convert <aqua>${amount(quantity * rate)} <gold>${exchange.from.plural}",
                "          <gray>to <aqua>${amount(quantity)} <gold>${exchange.to.plural}<gray>.",
                "<yellow>R-Click<gray>: Convert as many as you can",
                "<yellow>Shift+L/R-Click<gray>: Change the amount",
                exchangeGate,
            )))
        }
    }

    override fun click(gui: NPCGui, player: Player, slot: Int, click: ClickType): BankOutcome? {
        val bank = service(gui)
        LOAN_SLOTS.indexOf(slot).takeIf { it >= 0 }?.let { index ->
            if (click != ClickType.LEFT) return null
            return bank.takeLoan(player, BankRules.COINS[index], gate(BankFeature.LOANS))
        }
        val exchange = BankRules.EXCHANGES.getOrNull(EXCHANGE_SLOTS.indexOf(slot)) ?: return null
        val quantity = quantities[player.uniqueId] ?: BankRules.EXCHANGE_QUANTITIES.first()
        return when (click) {
            ClickType.LEFT -> bank.exchange(player, exchange, quantity, gate(BankFeature.EXCHANGES))
            ClickType.RIGHT -> bank.exchange(player, exchange, null, gate(BankFeature.EXCHANGES))
            ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT -> {
                quantities[player.uniqueId] = BankRules.cycleQuantity(quantity, if (click == ClickType.SHIFT_LEFT) 1 else -1)
                gui.playNavigationSound()
                render(gui, player)
                null
            }
            else -> null
        }
    }
}
