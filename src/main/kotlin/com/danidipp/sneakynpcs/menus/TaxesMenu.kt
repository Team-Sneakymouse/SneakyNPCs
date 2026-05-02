package com.danidipp.sneakynpcs.menus

import com.danidipp.sneakynpcs.InventoryAuditItems
import com.danidipp.sneakynpcs.NPCGui
import com.danidipp.sneakynpcs.PlayerData
import com.danidipp.sneakynpcs.shop.CurrencyDefinition
import com.nisovin.magicspells.MagicSpells
import com.nisovin.magicspells.util.magicitems.MagicItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import java.util.UUID

private const val BANK_TAX_VARIABLE = "bankTax"
private const val OUTSTANDING_TAX_PLACEHOLDER = "<outstanding_taxes>"

data class TaxesButtonConfig(
    val title: String,
    val lore: List<String>,
    val currencyId: String,
    val currencyAmount: Long,
    val taxAmount: Long,
    val writAmount: Int,
)

data class TaxesPaymentConfig(
    val currencyId: String,
    val taxValuePerUnit: Long,
)

data class TaxesRewardConfig(
    val itemId: String,
    val taxValue: Long,
)

data class TaxesMenuConfig(
    val payments: Map<Int, TaxesPaymentConfig>,
    val reward: TaxesRewardConfig,
)

internal data class ExactCurrencySpend(
    val inventoryUnits: Long,
    val bankUnits: Long,
)

internal fun taxesButtonConfig(
    slot: Int,
    payments: Map<Int, TaxesPaymentConfig>,
    reward: TaxesRewardConfig,
): TaxesButtonConfig? {
    val payment = payments[slot] ?: return null
    val currencyAmount = calculateTaxesCurrencyUnits(
        rewardTaxValue = reward.taxValue,
        currencyTaxValue = payment.taxValuePerUnit,
    ) ?: return null

    return TaxesButtonConfig(
        title = "<green>Pay off <gold>${reward.taxValue}g</gold> worth of taxes",
        lore = listOf(
            "<gray>Cost: <gold>$currencyAmount ${payment.currencyId}",
            "<gray>Outstanding taxes: <yellow>$OUTSTANDING_TAX_PLACEHOLDER",
        ),
        currencyId = payment.currencyId,
        currencyAmount = currencyAmount,
        taxAmount = reward.taxValue,
        writAmount = 1,
    )
}

internal fun calculateExactCurrencySpend(inventoryAvailable: Long, bankAvailable: Long, required: Long): ExactCurrencySpend? {
    if (required <= 0L) return ExactCurrencySpend(0L, 0L)
    if (inventoryAvailable < 0L || bankAvailable < 0L) return null
    if (inventoryAvailable + bankAvailable < required) return null

    val inventorySpend = minOf(inventoryAvailable, required)
    return ExactCurrencySpend(
        inventoryUnits = inventorySpend,
        bankUnits = required - inventorySpend,
    )
}

internal fun canPayTaxes(bankTax: Long): Boolean = bankTax > 0L

internal fun bankTaxAfterPayment(bankTax: Long, taxAmount: Long): Long = bankTax - taxAmount

internal fun calculateTaxesCurrencyUnits(rewardTaxValue: Long, currencyTaxValue: Long): Long? {
    if (rewardTaxValue <= 0L || currencyTaxValue <= 0L) return null
    if (rewardTaxValue % currencyTaxValue != 0L) return null
    return rewardTaxValue / currencyTaxValue
}

class TaxesMenu(
    private val rewardItem: MagicItem,
    private val config: TaxesMenuConfig,
) : NPCMenu(MenuType.TAXES) {
    private val miniMessage = MiniMessage.miniMessage()
    private val sessionTotals = mutableMapOf<UUID, TaxesSessionTotals>()

    override fun open(gui: NPCGui, player: Player, playerData: PlayerData?) {
        sessionTotals[player.uniqueId] = TaxesSessionTotals()
        render(gui, player)
    }

    override fun onClick(gui: NPCGui, event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val buttonConfig = taxesButtonConfig(event.slot, config.payments, config.reward) ?: return
        val plugin = gui.plugin
        val currentTax = getBankTax(player)

        if (!canPayTaxes(currentTax)) {
            sendFailure(gui, player, "You don't have any outstanding taxes.")
            return
        }

        val currency = plugin.currencyGraphService.getCurrency(buttonConfig.currencyId) ?: run {
            sendFailure(gui, player, "Taxes are not configured correctly. Please tell Dani.")
            return
        }

        val writStack = rewardItem.itemStack.clone().apply { amount = buttonConfig.writAmount }
        if (!plugin.balanceService.canFitItem(player.inventory, writStack, buttonConfig.writAmount)) {
            sendFailure(gui, player, "Not enough inventory space for the holy writ.")
            return
        }

        val spend = resolveExactSpend(gui, player, currency, buttonConfig.currencyAmount) ?: run {
            sendFailure(gui, player, "You don't have enough ${buttonConfig.currencyId}.")
            return
        }

        val removedItems = buildRemovedCurrencyItems(currency, spend)
        if (!plugin.balanceService.removeInventoryCurrencyUnits(player, currency, spend.inventoryUnits)) {
            sendFailure(gui, player, "Taxes are not configured correctly. Please tell Dani.")
            return
        }
        if (!plugin.balanceService.removeBankCurrencyUnits(player, currency, spend.bankUnits)) {
            sendFailure(gui, player, "Taxes are not configured correctly. Please tell Dani.")
            return
        }

        MagicSpells.getVariableManager().set(BANK_TAX_VARIABLE, player, bankTaxAfterPayment(currentTax, buttonConfig.taxAmount).toDouble())
        val leftovers = player.inventory.addItem(writStack)
        if (leftovers.isNotEmpty()) {
            plugin.logger.warning("Failed to deliver tax reward payment for player '${player.name}'")
            sendFailure(gui, player, "Taxes are not configured correctly. Please tell Dani.")
            return
        }

        plugin.inventoryTransactionLogger.log(
            player = player,
            removedItems = removedItems,
            addedItems = InventoryAuditItems.split(rewardItem.itemStack, buttonConfig.writAmount.toLong()),
        )

        val totals = sessionTotals.getOrPut(player.uniqueId) { TaxesSessionTotals() }
        totals.taxPaid += buttonConfig.taxAmount
        totals.writs += buttonConfig.writAmount
        player.playSound(player.location, "lom:buy", 1f, 1f)
        render(gui, player)
    }

    override fun onClose(gui: NPCGui, player: Player) {
        val totals = sessionTotals.remove(player.uniqueId) ?: return
        if (totals.taxPaid <= 0L) return
        player.sendMessage(
            Component.join(JoinConfiguration.spaces(),
                Component.text("You paid off",NamedTextColor.GREEN),
                Component.text("${totals.taxPaid} gold", NamedTextColor.GOLD),
                Component.text("worth of taxes and received ${totals.writs}x", NamedTextColor.GREEN),
                rewardItem.itemStack.displayName(),
            )
        )
    }

    private fun render(gui: NPCGui, player: Player) {
        val inv = gui.inventory
        val npc = gui.npc
        val hideTooltip = shouldHideTooltip(player)
        val outstandingTax = getBankTax(player)

        inv.clear()
        inv.setItem(0, makeItem(npc.guiModelKey, "alt", hideTooltip))
        inv.setItem(1, makeItem(npc.guiModelKey, "taxes", hideTooltip))
        taxesButtonConfig(14, config.payments, config.reward)?.let { inv.setItem(14, buildButtonItem(it, outstandingTax)) }
        taxesButtonConfig(16, config.payments, config.reward)?.let { inv.setItem(16, buildButtonItem(it, outstandingTax)) }
    }

    private fun buildButtonItem(buttonConfig: TaxesButtonConfig, outstandingTax: Long) = makeItem("lom:invisible").apply {
        val replacements = mapOf(
            OUTSTANDING_TAX_PLACEHOLDER to if (outstandingTax > 0L) outstandingTax.toString() else "<newline> <yellow>No outstanding taxes"
        )
        editMeta { meta ->
            meta.displayName(formatTooltipText(applyReplacements(buttonConfig.title, replacements)))
            meta.lore(buttonConfig.lore
                .joinToString("<newline>")
                .let { applyReplacements(it, replacements) }
                .split("<newline>")
                .map { formatTooltipText(it) })
        }
    }

    private fun resolveExactSpend(gui: NPCGui, player: Player, currency: CurrencyDefinition, required: Long): ExactCurrencySpend? {
        val inventoryAvailable = gui.plugin.balanceService.getInventoryCurrencyUnits(player, currency)
        val bankAvailable = gui.plugin.balanceService.getBankCurrencyUnits(player, currency)
        return calculateExactCurrencySpend(inventoryAvailable, bankAvailable, required)
    }

    private fun buildRemovedCurrencyItems(currency: CurrencyDefinition, spend: ExactCurrencySpend): List<org.bukkit.inventory.ItemStack> {
        val template = currency.itemMagicItem?.itemStack ?: return emptyList()
        return InventoryAuditItems.split(template, spend.inventoryUnits)
    }

    private fun sendFailure(gui: NPCGui, player: Player, message: String) {
        player.playSound(player.location, "lom:fail_wrong", 1f, 1f)
        player.sendMessage(Component.text(message, NamedTextColor.RED))
    }

    private fun getBankTax(player: Player): Long {
        return MagicSpells.getVariableManager().getValue(BANK_TAX_VARIABLE, player).toLong()
    }

    private fun applyReplacements(input: String, replacements: Map<String, String>): String {
        var output = input
        for ((key, value) in replacements) {
            output = output.replace(key, value)
        }
        return output
    }

    private fun formatTooltipText(input: String): Component {
        return Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(miniMessage.deserialize(input))
    }

    private data class TaxesSessionTotals(
        var taxPaid: Long = 0L,
        var writs: Int = 0,
    )
}
